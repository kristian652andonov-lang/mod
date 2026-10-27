package com.terrahaul.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.terrahaul.client.wind.WindSystem;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A puff of smoke rising from lava.
 * <p>
 * A puff is born small and dark just over the lava, tinted by the lava's glow, rises fast while it
 * is hot and slows as it cools, swells and greys as it thins out, and leans over with the wind,
 * which takes hold of it more strongly the higher it has risen, until it fades away. Many puffs
 * make a plume.
 * <p>
 * Smoke is drawn in the mist's render type ({@link MistParticle#RENDER_TYPE}): translucent,
 * without depth writes, with the mist's shader that keeps faint edges, so overlapping puffs blend
 * into one soft plume. It needs no render type of its own, and so nothing new for renderers such as
 * Sodium, which draw it as they draw the mist. Like the mist, a puff fades out as the camera comes
 * within about its own size of it.
 */
public class LavaSmokeParticle extends TextureSheetParticle {
    private final ParticleGroup group;
    private final float startSize;
    private final float endSize;
    private final float maxAlpha;
    private final float rise;
    private final float spin;
    private final float grey;
    private final double bornY;
    private float dissolve = 1.0F;
    private boolean dissolving;
    private final BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();

    /**
     * @param startSize half width of the puff when it is born
     * @param endSize   half width when it has fully spread
     * @param alpha     peak opacity
     * @param rise      upward speed it slows to once it has cooled, in blocks per tick
     * @param grey      brightness of the smoke once it has cooled (0 black, 1 white)
     */
    public LavaSmokeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                             float startSize, float endSize, int lifetime, float alpha, float rise, float grey, ParticleGroup group) {
        super(level, x, y, z);
        this.group = group;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.startSize = startSize;
        this.endSize = endSize;
        this.quadSize = startSize;
        this.lifetime = lifetime;
        this.maxAlpha = alpha;
        this.alpha = 0.0F;
        this.rise = rise;
        this.grey = grey;
        this.bornY = y;
        this.spin = (random.nextFloat() - 0.5F) * 0.02F;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        tint(0.0F);
    }

    /** Uses a specific sprite of the set. */
    public LavaSmokeParticle withSprite(TextureAtlasSprite sprite) {
        setSprite(sprite);
        return this;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return MistParticle.RENDER_TYPE;
    }

    @Override
    public Optional<ParticleGroup> getParticleGroup() {
        return Optional.ofNullable(group);
    }

    @Override
    public AABB getRenderBoundingBox(float partialTicks) {
        double r = quadSize * 1.1 + 0.5;
        return new AABB(x - r, y - r, z - r, x + r, y + r, z + r);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        double dx = Mth.lerp(partialTicks, xo, x) - cam.x;
        double dy = Mth.lerp(partialTicks, yo, y) - cam.y;
        double dz = Mth.lerp(partialTicks, zo, z) - cam.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float size = getQuadSize(partialTicks);
        // gone within half its size of the camera, whole again at one and a half times its size
        float near = (float) Mth.clamp((d - size * 0.5) / size, 0.0, 1.0);
        if (near <= 0.0F) {
            return;
        }
        float a = alpha;
        alpha = a * near;
        super.render(buffer, camera, partialTicks);
        alpha = a;
    }

    /**
     * The puff's colour at {@code t} of its life: lit warm by the lava at first, cooling to grey,
     * and a little lighter as it thins out.
     */
    private void tint(float t) {
        float warm = Math.max(0.0F, 1.0F - t / 0.18F);
        float g = grey * (0.8F + 0.45F * t);
        setColor(Math.min(1.0F, g + 0.32F * warm), Math.min(1.0F, g + 0.1F * warm), g * (1.0F - 0.15F * warm));
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float t = (float) age / lifetime;
        // swells quickly at first, then ever more slowly
        float grow = 1.0F - (1.0F - t) * (1.0F - t);
        quadSize = Mth.lerp(grow, startSize, endSize);
        if (dissolving) {
            dissolve -= 0.1F;
            if (dissolve <= 0.0F) {
                remove();
                return;
            }
        }
        // it thickens as it leaves the lava rather than lying on it as a dark smudge
        alpha = maxAlpha * dissolve * Mth.clamp(t / 0.16F, 0.0F, 1.0F) * Mth.clamp((1.0F - t) / 0.6F, 0.0F, 1.0F);
        tint(t);
        roll += spin;

        // the hot gas rises fast and slows as it cools; the wind takes hold of it the higher it is
        float wind = WindSystem.total(1.0F);
        double above = Math.max(0.0, y - bornY);
        double hold = 0.008 + 0.03 * Math.min(1.0, above / 16.0);
        double speed = wind * (0.03 + 0.03 * Math.min(1.0, above / 30.0));
        xd += (WindSystem.dirX(1.0F) * speed - xd) * hold;
        zd += (WindSystem.dirZ(1.0F) * speed - zd) * hold;
        yd += (rise - yd) * 0.035;
        x += xd;
        y += yd;
        z += zd;
        setPos(x, y, z);

        if (!dissolving && (age & 3) == 0 && level.getBlockState(cell.set(Mth.floor(x), Mth.floor(y), Mth.floor(z))).blocksMotion()) {
            // drifted into the rock: dissolve within half a second
            dissolving = true;
        }
    }
}
