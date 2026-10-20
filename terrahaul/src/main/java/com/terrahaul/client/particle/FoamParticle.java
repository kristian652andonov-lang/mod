package com.terrahaul.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.terrahaul.client.water.WaterSurface;
import com.terrahaul.client.wind.WindSystem;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A patch of white foam floating on the water below a fall or on rapids.
 * <p>
 * It lies flat on the surface (a quad in the water plane, not a sprite turned to the camera) and
 * rides it: on running water it follows the current, so foam from a step travels down the stream;
 * on a still pool it keeps the push it was born with, slows down, turns a little and drifts slowly
 * with the wind. It spreads at first, then slowly breaks up and fades. Foam carried over the edge
 * of a drop or onto the shore is gone within a few ticks.
 * <p>
 * It floats just above the surface as Minecraft draws it ({@link WaterSurface}), which rises to the
 * full block next to falling water, and above the highest point of that surface under the patch,
 * so the water never cuts through it.
 */
public class FoamParticle extends TextureSheetParticle {
    private final ParticleGroup group;
    private final float size;
    private final float maxAlpha;
    private final float yaw;
    private final float spinRate;
    private float spin;
    private float prevSpin;
    private float dissolve = 1.0F;
    private boolean dissolving;
    private final BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();

    /**
     * @param y    height of the foam: {@link WaterSurface#LIFT} above the drawn water surface
     * @param size half width of the patch when it has spread
     */
    public FoamParticle(ClientLevel level, double x, double y, double z, double xd, double zd, float size, int lifetime, ParticleGroup group) {
        super(level, x, y, z);
        this.group = group;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.size = size;
        this.quadSize = size * 0.6F;
        this.lifetime = lifetime;
        this.maxAlpha = 0.75F + random.nextFloat() * 0.2F;
        this.alpha = 0.0F;
        this.yaw = random.nextFloat() * Mth.TWO_PI;
        this.spinRate = (random.nextFloat() - 0.5F) * 0.6F;
        this.xd = xd;
        this.yd = 0.0;
        this.zd = zd;
        float shade = 0.96F + random.nextFloat() * 0.04F;
        setColor(0.94F * shade, 0.97F * shade, shade);
    }

    /** Uses a specific sprite of the set. */
    public FoamParticle withSprite(TextureAtlasSprite sprite) {
        setSprite(sprite);
        return this;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public Optional<ParticleGroup> getParticleGroup() {
        return Optional.ofNullable(group);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        prevSpin = spin;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        if (dissolving) {
            dissolve -= 0.15F;
            if (dissolve <= 0.0F) {
                remove();
                return;
            }
        }
        float t = (float) age / lifetime;
        // spreads out quickly, then breaks up into smaller and fainter bits
        quadSize = size * (t < 0.25F ? 0.6F + 1.6F * t : 1.0F - 0.25F * (t - 0.25F));
        alpha = maxAlpha * dissolve * Math.min(1.0F, age / 4.0F) * Mth.clamp((1.0F - t) / 0.45F, 0.0F, 1.0F);

        // the water it floats on: the block at the surface, or one below after floating off a step
        double surface = Double.NaN;
        cell.set(Mth.floor(x), Mth.floor(y - 0.05), Mth.floor(z));
        FluidState fluid = level.getFluidState(cell);
        if (!fluid.is(FluidTags.WATER)) {
            cell.move(0, -1, 0);
            fluid = level.getFluidState(cell);
        }
        if (fluid.is(FluidTags.WATER) && !level.getFluidState(cell.above()).is(FluidTags.WATER)) {
            surface = WaterSurface.highest(level, x, cell.getY(), z, quadSize * 0.8);
            if (Double.isNaN(surface)) {
                // something lies on the water here
                dissolving = true;
            } else if (!fluid.isSource() && fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING)) {
                // carried over the edge of a drop: churned under
                dissolving = true;
            } else if (!fluid.isSource()) {
                // ride the current
                Vec3 flow = fluid.getFlow(level, cell);
                xd += (flow.x * 0.07 - xd) * 0.08;
                zd += (flow.z * 0.07 - zd) * 0.08;
            }
        } else {
            // washed ashore, or under falling water
            dissolving = true;
        }
        // the wind barely moves foam on the water (a few percent of what it does to mist in the
        // air), so the push from the fall and the current carry it
        float wind = WindSystem.total(1.0F) * 0.0002F;
        xd = xd * 0.985 + WindSystem.dirX(1.0F) * wind;
        zd = zd * 0.985 + WindSystem.dirZ(1.0F) * wind;

        double nx = x + xd;
        double nz = z + zd;
        // a bank or a rock stops it
        if (level.getBlockState(cell.set(Mth.floor(nx), Mth.floor(y + 0.05), Mth.floor(nz))).blocksMotion()) {
            xd *= -0.2;
            zd *= -0.2;
            nx = x;
            nz = z;
        }
        // up at once where the surface rises (towards falling water), gently down off a step
        double ny = y;
        if (!Double.isNaN(surface)) {
            double target = surface + WaterSurface.LIFT;
            ny = target > y ? target : y + (target - y) * 0.5;
        }
        spin += spinRate * (float) Math.min(1.0, Math.sqrt(xd * xd + zd * zd) * 20.0 + 0.1);
        setPos(nx, ny, nz);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, xo, x) - cam.x());
        float py = (float) (Mth.lerp(partialTicks, yo, y) - cam.y());
        float pz = (float) (Mth.lerp(partialTicks, zo, z) - cam.z());
        Quaternionf q = new Quaternionf().rotateY(yaw + Mth.lerp(partialTicks, prevSpin, spin)).rotateX(Mth.HALF_PI);
        float s = getQuadSize(partialTicks);
        float u0 = getU0();
        float u1 = getU1();
        float v0 = getV0();
        float v1 = getV1();
        int light = getLightColor(partialTicks);
        Vector3f a = corner(q, 1.0F, -1.0F, s, px, py, pz);
        Vector3f b = corner(q, 1.0F, 1.0F, s, px, py, pz);
        Vector3f c = corner(q, -1.0F, 1.0F, s, px, py, pz);
        Vector3f d = corner(q, -1.0F, -1.0F, s, px, py, pz);
        // seen from above, and from below by a swimmer
        vertex(buffer, a, u1, v1, light);
        vertex(buffer, b, u1, v0, light);
        vertex(buffer, c, u0, v0, light);
        vertex(buffer, d, u0, v1, light);
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
