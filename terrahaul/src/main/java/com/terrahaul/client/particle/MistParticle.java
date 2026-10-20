package com.terrahaul.client.particle;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.terrahaul.client.wind.WindSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * A soft puff of mist rolling off the foot of a fall.
 * <p>
 * A puff starts small where the water lands, swells as it rolls outwards, rises slowly, turns and
 * drifts off with the wind, and thins out until it is gone. Many overlapping puffs make the cloud.
 * Billows, the large slow puffs of tall falls, also breathe in and out a little as they climb.
 * <p>
 * Mist is drawn in a render type of its own:
 * <ul>
 *   <li>with its own shader ({@link MistShaders}), which keeps the faint edges that Minecraft's
 *       particle shader cuts off below an opacity of 0.1;</li>
 *   <li>without depth writes: overlapping translucent puffs then blend into one soft cloud instead
 *       of cutting holes into each other (vanilla's translucent particles write depth, so whichever
 *       puff happens to be drawn first hides the parts of later ones behind it). Rock and water
 *       still hide the mist, and it is drawn after them;</li>
 *   <li>with Fabulous graphics, where water and particles are drawn into separate layers that are
 *       sorted by depth afterwards, a second pass writes only the depth of the visible mist in front
 *       of the water, so the mist over a pool or in front of a curtain is not put behind the water
 *       ({@link #drawDepth}).</li>
 * </ul>
 * A puff fades out as the camera comes within about its own size of it: the camera inside a large
 * puff would otherwise see it fill the whole screen, many layers deep.
 */
public class MistParticle extends TextureSheetParticle {
    /** Translucent, depth-tested, without depth writes, with the mist shader. */
    public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            DRAWN.clear();
            // the particle engine has just set Minecraft's particle shader; replace it
            ShaderInstance shader = MistShaders.colour();
            if (shader != null) {
                RenderSystem.setShader(() -> shader);
            }
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "TERRAHAUL_MIST";
        }
    };

    /** The puffs drawn this frame, for the depth pass of Fabulous graphics. */
    private static final List<MistParticle> DRAWN = new ArrayList<>();
    private static Camera drawnCamera;
    private static float drawnPartialTicks;
    private static boolean replaying;

    private final ParticleGroup group;
    private final float startSize;
    private final float endSize;
    private final float maxAlpha;
    private final float rise;
    private final float spin;
    private final float breath;
    private final float phase;
    private float dissolve = 1.0F;
    private boolean dissolving;
    private final BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();

    /**
     * @param size      half width of the puff when it is born and when it has fully spread
     * @param alpha     peak opacity
     * @param rise      upward speed it settles to, in blocks per tick
     * @param billowing whether it swells and shrinks slowly while it lives (the big clouds of tall falls)
     */
    public MistParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                        float startSize, float endSize, int lifetime, float alpha, float rise, boolean billowing, ParticleGroup group) {
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
        this.spin = (random.nextFloat() - 0.5F) * (billowing ? 0.012F : 0.03F);
        this.breath = billowing ? 0.07F : 0.0F;
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        float shade = 0.95F + random.nextFloat() * 0.05F;
        setColor(0.93F * shade, 0.96F * shade, shade);
    }

    /** Uses a specific sprite of the set. */
    public MistParticle withSprite(TextureAtlasSprite sprite) {
        setSprite(sprite);
        return this;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return RENDER_TYPE;
    }

    @Override
    public Optional<ParticleGroup> getParticleGroup() {
        return Optional.ofNullable(group);
    }

    @Override
    public AABB getRenderBoundingBox(float partialTicks) {
        // a big puff is still on screen when its centre is not
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
        if (!replaying && Minecraft.useShaderTransparency()) {
            DRAWN.add(this);
            drawnCamera = camera;
            drawnPartialTicks = partialTicks;
        }
    }

    /** After the particles have been drawn: the depth pass of the mist, with Fabulous graphics only. */
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            drawDepth();
        }
    }

    /**
     * Fabulous graphics only: draws the mist of this frame again with colour writes off, writing the
     * depth of every puff where it can be seen and lies in front of the water layer. The particle
     * layer is then sorted in front of the water there when the layers are put together, as it is
     * drawn in Fast and Fancy graphics, while the puffs themselves still blended without depth.
     */
    static void drawDepth() {
        if (DRAWN.isEmpty()) {
            return;
        }
        try {
            ShaderInstance shader = MistShaders.depth();
            RenderTarget water = Minecraft.getInstance().levelRenderer.getTranslucentTarget();
            if (shader == null || water == null || !Minecraft.useShaderTransparency()) {
                return;
            }
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.setShaderTexture(3, water.getDepthTextureId());
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(false, false, false, false);
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            replaying = true;
            try {
                for (MistParticle particle : DRAWN) {
                    particle.render(buffer, drawnCamera, drawnPartialTicks);
                }
            } finally {
                replaying = false;
            }
            MeshData mesh = buffer.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShaderTexture(3, 0);
        } finally {
            DRAWN.clear();
        }
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
        // swells quickly at first, then ever more slowly; billows breathe on top of that
        float grow = 1.0F - (1.0F - t) * (1.0F - t);
        quadSize = Mth.lerp(grow, startSize, endSize) * (1.0F + breath * Mth.sin(age * 0.045F + phase));
        if (dissolving) {
            dissolve -= 0.1F;
            if (dissolve <= 0.0F) {
                remove();
                return;
            }
        }
        alpha = maxAlpha * dissolve * Math.min(1.0F, t / 0.12F) * Mth.clamp((1.0F - t) / 0.55F, 0.0F, 1.0F);
        roll += spin;

        // the outward roll from the impact dies away and the wind takes over
        float wind = WindSystem.total(1.0F);
        xd += (WindSystem.dirX(1.0F) * wind * 0.035 - xd) * 0.025;
        zd += (WindSystem.dirZ(1.0F) * wind * 0.035 - zd) * 0.025;
        yd += (rise - yd) * 0.03;
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
