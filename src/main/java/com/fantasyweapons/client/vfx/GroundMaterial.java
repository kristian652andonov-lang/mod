package com.fantasyweapons.client.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The real block under an impact, so broken ground looks like the terrain it came from: the block's top and side
 * textures (from its baked model, with biome tint such as grass green), its map colour for dust, and the light level
 * there (VFX shaders are unlit, so modelled debris is darkened by hand at night and in caves).
 */
public record GroundMaterial(BlockState state, TextureAtlasSprite top, int topTint, TextureAtlasSprite side, int sideTint, TextureAtlasSprite particle,
                             int dust, float light) {
    public static final net.minecraft.resources.ResourceLocation ATLAS = InventoryMenu.BLOCK_ATLAS;

    /** The first solid block at or below {@code at} (searching 6 blocks down); stone if there is none. */
    public static GroundMaterial at(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        BlockPos.MutableBlockPos m = BlockPos.containing(at.x, at.y + 0.3, at.z).mutable();
        BlockState state = Blocks.STONE.defaultBlockState();
        if (level != null) {
            for (int i = 0; i < 7; i++, m.move(Direction.DOWN)) {
                BlockState s = level.getBlockState(m);
                if (!s.isAir() && s.isCollisionShapeFullBlock(level, m) && s.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL) {
                    state = s;
                    break;
                }
            }
        }
        BlockPos pos = m.immutable();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        RandomSource r = RandomSource.create(42);
        TextureAtlasSprite particle = model.getParticleIcon(net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
        TextureAtlasSprite top = particle, side = particle;
        int topTint = 0xFFFFFF, sideTint = 0xFFFFFF;
        List<BakedQuad> up = model.getQuads(state, Direction.UP, r);
        if (!up.isEmpty()) {
            top = up.get(0).getSprite();
            topTint = tint(level, state, pos, up.get(0));
        }
        List<BakedQuad> north = model.getQuads(state, Direction.NORTH, r);
        if (!north.isEmpty()) {
            side = north.get(0).getSprite();
            sideTint = tint(level, state, pos, north.get(0));
        }
        // grass, podzol and the like throw up earth, not green clouds
        int dust = level == null ? 0x8C7458 : state.is(net.minecraft.tags.BlockTags.DIRT) ? 0x8A6A4C : state.getMapColor(level, pos).col;
        float light = 1f;
        if (level != null) {
            int packed = LevelRenderer.getLightColor(level, pos.above());
            int block = (packed >> 4) & 15, sky = (packed >> 20) & 15;
            float daylight = level instanceof net.minecraft.client.multiplayer.ClientLevel cl ? cl.getSkyDarken(1f) : 1f; // 0.2 night .. 1 day
            float skyK = sky / 15f * daylight;
            light = 0.22f + 0.78f * Math.max(block / 15f, skyK);
        }
        return new GroundMaterial(state, top, topTint, side, sideTint, particle, dust, light);
    }

    private static int tint(Level level, BlockState state, BlockPos pos, BakedQuad q) {
        if (!q.isTinted() || level == null) return 0xFFFFFF;
        return Minecraft.getInstance().getBlockColors().getColor(state, level, pos, q.getTintIndex()) & 0xFFFFFF;
    }

    /** {@code rgb} darkened by the light level and a facet shade. */
    public int lit(int rgb, float shade) {
        return Colors.scale(rgb, light * shade);
    }
}
