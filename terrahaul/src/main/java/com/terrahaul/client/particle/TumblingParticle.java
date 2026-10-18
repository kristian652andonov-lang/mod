package com.terrahaul.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.terrahaul.client.wind.WindSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A leaf, needle or petal that tumbles down through the air.
 * <p>
 * Unlike vanilla particles, which are flat sprites always facing the camera, this is a real
 * two-sided quad oriented in the world: it rocks like a pendulum while it falls, spins, drifts with
 * the {@link WindSystem}, flutters sideways, comes to rest lying flat on the ground (or floating on
 * water) and fades out there.
 */
public class TumblingParticle extends TextureSheetParticle {
    public enum Kind {
        /** Broad leaves: slow fall, strong pendulum rocking, gentle spin. */
        LEAF(0.035F, 0.17F, 0.85F, 0.07F, 0.75F),
        /** Conifer needles: fall faster and spin like a propeller. */
        NEEDLE(0.06F, 0.13F, 0.25F, 0.35F, 0.45F),
        /** Cherry petals: very light, flutter widely and ride the wind. */
        PETAL(0.022F, 0.11F, 1.1F, 0.12F, 1.0F);

        final float fallSpeed;
        final float size;
        final float rock;
        final float spin;
        final float windResponse;

        Kind(float fallSpeed, float size, float rock, float spin, float windResponse) {
            this.fallSpeed = fallSpeed;
            this.size = size;
            this.rock = rock;
            this.spin = spin;
            this.windResponse = windResponse;
        }
    }

    private static final int FADE_TICKS = 50;
    private static final int FLOAT_TICKS = 220;

    private final Kind kind;
    private final float rockFreq;
    private final float rockPhase;
    private final float flutterAmp;
    private final float spinSpeed;
    private final float yaw;
    private float spin;
    private float prevSpin;
    private float rockAngle;
    private float prevRockAngle;
    private int restAge = -1;
    private boolean floating;
    private float baseAlpha = 1.0F;

    public TumblingParticle(ClientLevel level, double x, double y, double z, Kind kind) {
        super(level, x, y, z);
        this.kind = kind;
        this.hasPhysics = true;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.quadSize = kind.size * (0.8F + random.nextFloat() * 0.45F);
        this.lifetime = 360 + random.nextInt(240);
        this.rockFreq = 0.12F + random.nextFloat() * 0.1F;
        this.rockPhase = random.nextFloat() * Mth.TWO_PI;
        this.flutterAmp = 0.012F + random.nextFloat() * 0.018F;
        this.spinSpeed = kind.spin * (0.6F + random.nextFloat() * 0.8F) * (random.nextBoolean() ? 1 : -1);
        this.yaw = random.nextFloat() * Mth.TWO_PI;
        this.spin = random.nextFloat() * Mth.TWO_PI;
        this.prevSpin = spin;
        this.xd = 0.0;
        this.yd = -kind.fallSpeed * 0.3;
        this.zd = 0.0;
        setSize(0.08F, 0.04F);
    }

    /** Uses a specific sprite, for example a lobed leaf for oak. */
    public TumblingParticle withSprite(TextureAtlasSprite sprite) {
        setSprite(sprite);
        return this;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        prevSpin = spin;
        prevRockAngle = rockAngle;
        if (age++ >= lifetime) {
            remove();
            return;
        }

        if (restAge >= 0) {
            int fade = floating ? FLOAT_TICKS : FADE_TICKS;
            int resting = age - restAge;
            alpha = baseAlpha * Mth.clamp(1.0F - (resting - fade * 0.4F) / (fade * 0.6F), 0.0F, 1.0F);
            if (resting >= fade) {
                remove();
                return;
            }
            if (floating) {
                // drift slowly with the wind on the water surface
                float wind = WindSystem.total(1.0F) * 0.006F;
                xd = xd * 0.9 + WindSystem.dirX(1.0F) * wind;
                zd = zd * 0.9 + WindSystem.dirZ(1.0F) * wind;
                move(xd, 0.0, zd);
                FluidState fluid = level.getFluidState(BlockPos.containing(x, y - 0.05, z));
                if (!fluid.is(FluidTags.WATER)) {
                    floating = false;
                }
            }
            return;
        }

        float wind = WindSystem.total(1.0F) * kind.windResponse;
        double windX = WindSystem.dirX(1.0F) * wind * 0.045;
        double windZ = WindSystem.dirZ(1.0F) * wind * 0.045;
        float phase = age * rockFreq + rockPhase;
        rockAngle = Mth.sin(phase) * kind.rock;
        // the pendulum swing pushes the leaf sideways and briefly slows its fall at the ends of each arc
        double flutter = Mth.cos(phase) * flutterAmp;
        double flutterX = -Mth.sin(yaw) * flutter;
        double flutterZ = Mth.cos(yaw) * flutter;
        float lift = kind == Kind.NEEDLE ? 0.0F : Math.abs(Mth.sin(phase)) * kind.fallSpeed * 0.55F;

        xd += (windX + flutterX - xd) * 0.12;
        zd += (windZ + flutterZ - zd) * 0.12;
        yd += (-(kind.fallSpeed - lift) - yd) * 0.15;
        spin += spinSpeed * (0.6F + wind * 0.5F);

        double beforeY = yd;
        move(xd, yd, zd);

        FluidState fluid = level.getFluidState(BlockPos.containing(x, y, z));
        if (fluid.is(FluidTags.WATER)) {
            BlockPos pos = BlockPos.containing(x, y, z);
            float surface = pos.getY() + fluid.getHeight(level, pos);
            if (y <= surface) {
                setPos(x, surface + 0.01, z);
                rest(true);
                return;
            }
        }
        if (onGround || (beforeY < 0.0 && yd == 0.0)) {
            setPos(x, y + 0.015, z);
            rest(false);
        }
    }

    private void rest(boolean onWater) {
        restAge = age;
        floating = onWater;
        baseAlpha = alpha;
        xd = 0.0;
        yd = 0.0;
        zd = 0.0;
        rockAngle = 0.0F;
        prevRockAngle = 0.0F;
        prevSpin = spin;
        hasPhysics = !onWater;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, xo, x) - cam.x());
        float py = (float) (Mth.lerp(partialTicks, yo, y) - cam.y());
        float pz = (float) (Mth.lerp(partialTicks, zo, z) - cam.z());

        Quaternionf q = new Quaternionf().rotateY(yaw);
        if (restAge >= 0) {
            q.rotateX(Mth.HALF_PI).rotateZ(spin);
        } else {
            q.rotateX(Mth.HALF_PI + Mth.lerp(partialTicks, prevRockAngle, rockAngle));
            if (kind == Kind.NEEDLE) {
                q.rotateY(Mth.lerp(partialTicks, prevSpin, spin));
            } else {
                q.rotateZ(Mth.lerp(partialTicks, prevSpin, spin));
            }
        }

        float size = getQuadSize(partialTicks);
        float u0 = getU0();
        float u1 = getU1();
        float v0 = getV0();
        float v1 = getV1();
        int light = getLightColor(partialTicks);

        Vector3f a = corner(q, 1.0F, -1.0F, size, px, py, pz);
        Vector3f b = corner(q, 1.0F, 1.0F, size, px, py, pz);
        Vector3f c = corner(q, -1.0F, 1.0F, size, px, py, pz);
        Vector3f d = corner(q, -1.0F, -1.0F, size, px, py, pz);
        // front face
        vertex(buffer, a, u1, v1, light);
        vertex(buffer, b, u1, v0, light);
        vertex(buffer, c, u0, v0, light);
        vertex(buffer, d, u0, v1, light);
        // back face (reverse winding) so the quad is visible from both sides
        vertex(buffer, d, u0, v1, light);
        vertex(buffer, c, u0, v0, light);
        vertex(buffer, b, u1, v0, light);
        vertex(buffer, a, u1, v1, light);
    }

    private static Vector3f corner(Quaternionf q, float dx, float dy, float size, float x, float y, float z) {
        return new Vector3f(dx, dy, 0.0F).rotate(q).mul(size).add(x, y, z);
    }

    private void vertex(VertexConsumer buffer, Vector3f p, float u, float v, int light) {
        buffer.addVertex(p.x(), p.y(), p.z()).setUv(u, v).setColor(rCol, gCol, bCol, alpha).setLight(light);
    }
}
