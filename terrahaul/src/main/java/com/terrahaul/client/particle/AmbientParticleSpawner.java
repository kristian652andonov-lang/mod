package com.terrahaul.client.particle;

import com.terrahaul.client.wind.WindSystem;
import com.terrahaul.config.TerrahaulConfig;
import com.terrahaul.config.TerrahaulConfig.AnimationQuality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns falling leaves, needles, petals and wind-blown dust around the camera.
 * <p>
 * Every client tick a handful of random columns around the camera are examined through the
 * client's heightmaps. A column whose top block is leaves may shed a leaf from the underside of
 * its canopy, so leaves appear where there is room to fall, never inside foliage. The species of
 * the leaves decides the particle (needles from spruce, petals from cherry) and its colour comes
 * from the block colour handlers, so biome tints and resource packs are respected. Dry biomes
 * shed dust when the wind is strong. Rates scale with wind, the quality preset and the density
 * options; nothing here runs on a server.
 */
public final class AmbientParticleSpawner {
    private static final int[] AUTUMN = {0xD9A43A, 0xC8642A, 0xA8392B, 0xB8862E, 0x8C5A2B, 0xCF8A2E};
    private static final int[] AUTUMN_BIRCH = {0xE3C34A, 0xD7A936, 0xC99A2E};
    private static final RandomSource RANDOM = RandomSource.create();

    private AmbientParticleSpawner() {
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || mc.player == null || TerrahaulConfig.animationQuality() == AnimationQuality.OFF) {
            return;
        }
        if (TerrahaulParticleProviders.leafSprites == null || !mc.gameRenderer.getMainCamera().isInitialized()) {
            return;
        }
        AnimationQuality quality = TerrahaulConfig.animationQuality();
        int radius = switch (quality) {
            case LOW -> 14;
            case MEDIUM -> 20;
            default -> 28;
        };
        int columns = switch (quality) {
            case LOW -> 6;
            case MEDIUM -> 12;
            default -> 20;
        };
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float wind = WindSystem.total(1.0F);

        if (TerrahaulConfig.fallingLeaves() || TerrahaulConfig.cherryPetals()) {
            double density = TerrahaulConfig.fallingLeafDensity();
            for (int i = 0; i < columns; i++) {
                int x = (int) Math.floor(cam.x) + RANDOM.nextInt(radius * 2 + 1) - radius;
                int z = (int) Math.floor(cam.z) + RANDOM.nextInt(radius * 2 + 1) - radius;
                trySpawnLeaf(mc, level, x, z, cam, wind, density);
            }
        }
        if (TerrahaulConfig.dust() && wind > 0.75F) {
            int attempts = columns / 3;
            for (int i = 0; i < attempts; i++) {
                int x = (int) Math.floor(cam.x) + RANDOM.nextInt(radius * 2 + 1) - radius;
                int z = (int) Math.floor(cam.z) + RANDOM.nextInt(radius * 2 + 1) - radius;
                trySpawnDust(mc, level, x, z, cam, wind);
            }
        }
    }

    private static void trySpawnLeaf(Minecraft mc, ClientLevel level, int x, int z, Vec3 cam, float wind, double density) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        if (top < cam.y - 32 || top > cam.y + 48) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        BlockState leaves = level.getBlockState(pos);
        if (!(leaves.getBlock() instanceof LeavesBlock) && !leaves.is(BlockTags.LEAVES)) {
            return;
        }
        boolean petals = leaves.is(Blocks.CHERRY_LEAVES);
        if (petals ? !TerrahaulConfig.cherryPetals() : !TerrahaulConfig.fallingLeaves()) {
            return;
        }

        // shedding rate: windier weather strips more leaves; autumn mode sheds heavily
        boolean deciduous = isDeciduous(leaves.getBlock());
        boolean autumn = deciduous && TerrahaulConfig.autumnLeafFall();
        double chance = 0.06 * density * (0.5 + Math.min(wind, 2.5) * 0.6);
        if (petals) {
            chance *= 1.6;
        } else if (leaves.is(Blocks.SPRUCE_LEAVES)) {
            chance *= 0.45;
        } else if (autumn) {
            chance *= 3.5;
        }
        if (RANDOM.nextDouble() >= chance) {
            return;
        }

        // walk down through the canopy to its underside
        BlockState below = leaves;
        int y = top;
        for (int i = 0; i < 14; i++) {
            pos.setY(y - 1);
            below = level.getBlockState(pos);
            if (!below.is(BlockTags.LEAVES) && !(below.getBlock() instanceof LeavesBlock)) {
                break;
            }
            y--;
        }
        if (!below.isAir()) {
            return;
        }
        pos.setY(y);
        BlockState source = level.getBlockState(pos);

        double px = x + RANDOM.nextDouble();
        double py = y - 0.15;
        double pz = z + RANDOM.nextDouble();
        TumblingParticle particle;
        if (petals) {
            particle = new TumblingParticle(level, px, py, pz, TumblingParticle.Kind.PETAL)
                .withSprite(TerrahaulParticleProviders.petalSprites.get(RANDOM));
        } else if (source.is(Blocks.SPRUCE_LEAVES)) {
            particle = new TumblingParticle(level, px, py, pz, TumblingParticle.Kind.NEEDLE)
                .withSprite(TerrahaulParticleProviders.needleSprites.get(RANDOM));
            tint(particle, mc, level, source, pos);
        } else {
            particle = new TumblingParticle(level, px, py, pz, TumblingParticle.Kind.LEAF)
                .withSprite(leafSprite(TerrahaulParticleProviders.leafSprites, source.getBlock()));
            if (autumn && RANDOM.nextFloat() < 0.8F) {
                int[] palette = source.is(Blocks.BIRCH_LEAVES) ? AUTUMN_BIRCH : AUTUMN;
                setColor(particle, palette[RANDOM.nextInt(palette.length)]);
            } else {
                tint(particle, mc, level, source, pos);
            }
        }
        mc.particleEngine.add(particle);
    }

    private static boolean isDeciduous(Block block) {
        return block == Blocks.OAK_LEAVES || block == Blocks.BIRCH_LEAVES || block == Blocks.DARK_OAK_LEAVES;
    }

    private static TextureAtlasSprite leafSprite(SpriteSet sprites, Block block) {
        // sprites: 0 oval, 1 slender, 2 broad, 3 lobed
        int index;
        if (block == Blocks.OAK_LEAVES || block == Blocks.DARK_OAK_LEAVES) {
            index = RANDOM.nextFloat() < 0.65F ? 3 : 0;
        } else if (block == Blocks.JUNGLE_LEAVES) {
            index = 2;
        } else if (block == Blocks.ACACIA_LEAVES || block == Blocks.MANGROVE_LEAVES) {
            index = RANDOM.nextFloat() < 0.7F ? 1 : 0;
        } else if (block == Blocks.BIRCH_LEAVES) {
            index = RANDOM.nextFloat() < 0.6F ? 0 : 2;
        } else {
            return sprites.get(RANDOM);
        }
        return sprites.get(index, 3);
    }

    private static void tint(TumblingParticle particle, Minecraft mc, ClientLevel level, BlockState state, BlockPos pos) {
        int colour = mc.getBlockColors().getColor(state, level, pos, 0);
        if (colour == -1) {
            // untinted leaves (azalea and many modded leaves): use the block's map colour
            if (state.is(Blocks.AZALEA_LEAVES) || state.is(Blocks.FLOWERING_AZALEA_LEAVES)) {
                colour = 0x5E8A2C;
            } else {
                colour = state.getMapColor(level, pos).col;
            }
        }
        // vary the shade a little leaf to leaf
        float shade = 0.85F + RANDOM.nextFloat() * 0.25F;
        particle.setColor(Math.min(1.0F, (colour >> 16 & 255) / 255.0F * shade),
            Math.min(1.0F, (colour >> 8 & 255) / 255.0F * shade),
            Math.min(1.0F, (colour & 255) / 255.0F * shade));
    }

    private static void setColor(TumblingParticle particle, int colour) {
        particle.setColor((colour >> 16 & 255) / 255.0F, (colour >> 8 & 255) / 255.0F, (colour & 255) / 255.0F);
    }

    private static void trySpawnDust(Minecraft mc, ClientLevel level, int x, int z, Vec3 cam, float wind) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (Math.abs(y - cam.y) > 24) {
            return;
        }
        BlockPos ground = new BlockPos(x, y - 1, z);
        BlockState state = level.getBlockState(ground);
        if (!(state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.GRAVEL)
            || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE))) {
            return;
        }
        Holder<Biome> biome = level.getBiome(ground);
        if (biome.value().hasPrecipitation()) {
            return;
        }
        if (RANDOM.nextFloat() >= (wind - 0.75F) * 0.5F) {
            return;
        }
        DustMoteParticle particle = new DustMoteParticle(level, x + RANDOM.nextDouble(), y + 0.1 + RANDOM.nextDouble() * 1.4, z + RANDOM.nextDouble());
        particle.pickSprite(TerrahaulParticleProviders.dustSprites);
        int colour = state.getMapColor(level, ground).col;
        particle.setColor(Math.min(1.0F, (colour >> 16 & 255) / 255.0F * 1.15F), Math.min(1.0F, (colour >> 8 & 255) / 255.0F * 1.15F),
            Math.min(1.0F, (colour & 255) / 255.0F * 1.15F));
        mc.particleEngine.add(particle);
    }
}
