package com.terrahaul.client.sodium;

import com.terrahaul.Terrahaul;
import com.terrahaul.client.sway.SwayShader;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Brings Terrahaul's tall-world fog and vegetation sway to Sodium's terrain shaders.
 * <p>
 * Sodium draws terrain with its own shaders instead of Minecraft's core shaders, so the fog and
 * sway that Terrahaul adds to {@code fog.glsl} and {@code rendertype_cutout.vsh} never reach it.
 * Sodium reads its shader sources from its own jar, not from resource packs; a mixin on its
 * shader loader hands each source to {@link #patch} instead, which edits two of them:
 * <ul>
 * <li>{@code sodium:include/fog.glsl} learns fog shape 2, which Terrahaul reports in its worlds
 * (see {@code FogShapeMixin}): a cylinder that counts vertical distance at 0.15, as in
 * Terrahaul's own {@code fog.glsl}. Without it Sodium treats the unknown shape as a sphere and the
 * valleys below a high peak disappear in fog.</li>
 * <li>{@code sodium:blocks/block_layer_opaque.vsh} gets the sway: the uniforms and wave function
 * are copied from Terrahaul's {@code rendertype_cutout.vsh} (between its {@code terrahaul:sway}
 * markers), and plant vertices, which carry a sway height in their colour alpha
 * ({@code SwayBakedModel}), bend with the wind exactly as with the vanilla renderer.</li>
 * </ul>
 * Each edit looks for a line of Sodium's source; if a Sodium version has changed that line the
 * edit is skipped and the source left as it is. {@link #swaySupported()} checks the vertex shader
 * before models are wrapped, so plants are only marked when their shader will understand them.
 * The uniforms are uploaded by a mixin on Sodium's shader interface whenever Sodium sets up its
 * terrain program.
 */
public final class SodiumShaders {
    private static final ResourceLocation FOG = ResourceLocation.fromNamespaceAndPath("sodium", "include/fog.glsl");
    private static final ResourceLocation TERRAIN = ResourceLocation.fromNamespaceAndPath("sodium", "blocks/block_layer_opaque.vsh");
    private static final String SODIUM_TERRAIN_SOURCE = "assets/sodium/shaders/blocks/block_layer_opaque.vsh";
    private static final String CUTOUT_SOURCE = "assets/minecraft/shaders/core/rendertype_cutout.vsh";
    private static final String SWAY_BEGIN = "// terrahaul:sway begin";
    private static final String SWAY_END = "// terrahaul:sway end";

    /** In Sodium's fog distance switch: the case to add goes before its fallback. */
    private static final String FOG_ANCHOR = "default:";
    private static final String FOG_CASE = "case 2: return max(length(position.xz), abs(position.y) * 0.15); // Terrahaul worlds\n        ";
    /** In Sodium's vertex shader: the declarations go after this line, the sway after the next one. */
    private static final String UNIFORM_ANCHOR = "uniform sampler2D u_LightTex;";
    private static final String POSITION_ANCHOR = "vec3 position = _vert_position + translation;";
    private static final String SWAY_MAIN = """

    // Terrahaul vegetation sway, as in Terrahaul's rendertype_cutout.vsh
    float terrahaulMarker = floor((1.0 - _vert_color.a) * 255.0 + 0.5);
    if (terrahaulMarker > 0.5) {
        _vert_color.a = 1.0;
        // sky light 7..13 -> 0..1: plants in caves and under overhangs stay still
        float terrahaulSky = clamp((_vert_tex_light_coord.y * 16.0 - 7.0) / 6.0, 0.0, 1.0);
        float terrahaulFade = 1.0 - smoothstep(40.0, 64.0, length(position));
        if (TerrahaulWind.z > 0.0 && terrahaulSky * terrahaulFade > 0.0) {
            position += terrahaulSway(position + TerrahaulCamera, terrahaulMarker / 48.0) * (terrahaulSky * terrahaulFade);
        }
    }""";

    /** Uniform locations (wind, camera) per program; programs are rebuilt when Sodium reloads its shaders. */
    private static final Int2ObjectOpenHashMap<int[]> LOCATIONS = new Int2ObjectOpenHashMap<>();
    private static String swayCode;
    private static boolean swayRead;

    private SodiumShaders() {
    }

    /** Returns Sodium's shader source with Terrahaul's changes, or the source unchanged. */
    public static String patch(ResourceLocation name, String source) {
        if (source == null) {
            return null;
        }
        if (FOG.equals(name)) {
            return patchFog(source);
        }
        if (TERRAIN.equals(name)) {
            LOCATIONS.clear();
            return patchTerrain(source);
        }
        return source;
    }

    static String patchFog(String source) {
        int at = source.indexOf(FOG_ANCHOR);
        if (at < 0 || source.contains("Terrahaul worlds")) {
            if (at < 0) {
                Terrahaul.LOGGER.warn("Sodium's fog shader has changed; valleys far below high peaks may be fogged with Sodium");
            }
            return source;
        }
        Terrahaul.LOGGER.info("Added Terrahaul's tall-world fog to Sodium's fog shader");
        return source.substring(0, at) + FOG_CASE + source.substring(at);
    }

    static String patchTerrain(String source) {
        String sway = swayCode();
        if (sway == null || !canPatchTerrain(source) || source.contains("terrahaulSway")) {
            if (sway != null && !canPatchTerrain(source)) {
                Terrahaul.LOGGER.warn("Sodium's terrain shader has changed; vegetation sway is not applied with Sodium");
            }
            return source;
        }
        int uniforms = source.indexOf(UNIFORM_ANCHOR) + UNIFORM_ANCHOR.length();
        String withDeclarations = source.substring(0, uniforms) + "\n\n" + sway + source.substring(uniforms);
        int position = withDeclarations.indexOf(POSITION_ANCHOR) + POSITION_ANCHOR.length();
        Terrahaul.LOGGER.info("Added Terrahaul's vegetation sway to Sodium's terrain shader");
        return withDeclarations.substring(0, position) + SWAY_MAIN + withDeclarations.substring(position);
    }

    private static boolean canPatchTerrain(String source) {
        return source.contains(UNIFORM_ANCHOR) && source.contains(POSITION_ANCHOR) && source.contains("_vert_color")
            && source.contains("_vert_tex_light_coord");
    }

    /**
     * True when Sodium's terrain shader can take the sway: Terrahaul's sway code is readable and
     * the lines it is added at are present in the installed Sodium's shader.
     */
    public static boolean swaySupported() {
        if (swayCode() == null) {
            return false;
        }
        try {
            // read the way Sodium reads it: through its own class loader
            Class<?> loader = Class.forName("net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader");
            String source = read(loader.getClassLoader().getResourceAsStream(SODIUM_TERRAIN_SOURCE));
            boolean ok = source != null && canPatchTerrain(source);
            if (!ok) {
                Terrahaul.LOGGER.info("Vegetation sway disabled: this Sodium version's terrain shader is not one Terrahaul can extend");
            }
            return ok;
        } catch (ClassNotFoundException | LinkageError e) {
            Terrahaul.LOGGER.info("Vegetation sway disabled: Sodium's shader loader was not found ({})", e.toString());
            return false;
        }
    }

    /** The sway declarations and wave function from Terrahaul's cutout shader, read once from the mod jar. */
    private static synchronized String swayCode() {
        if (!swayRead) {
            swayRead = true;
            // through the class, not its loader: a mod's resources are only readable from its own module
            String cutout = read(SodiumShaders.class.getResourceAsStream("/" + CUTOUT_SOURCE));
            int begin = cutout == null ? -1 : cutout.indexOf(SWAY_BEGIN);
            int end = cutout == null ? -1 : cutout.indexOf(SWAY_END);
            if (begin >= 0 && end > begin) {
                swayCode = cutout.substring(begin, end + SWAY_END.length()) + "\n";
            } else {
                Terrahaul.LOGGER.warn("Terrahaul's sway shader code was not found; vegetation sway is not applied with Sodium");
            }
        }
        return swayCode;
    }

    private static String read(InputStream stream) {
        try (InputStream in = stream) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Uploads this frame's wind and camera to the terrain program Sodium has just bound. */
    public static void uploadUniforms() {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program == 0) {
            return;
        }
        int[] location = LOCATIONS.computeIfAbsent(program,
            p -> new int[] {GL20.glGetUniformLocation(p, "TerrahaulWind"), GL20.glGetUniformLocation(p, "TerrahaulCamera")});
        if (location[0] >= 0) {
            float[] wind = SwayShader.wind();
            GL20.glUniform4f(location[0], wind[0], wind[1], wind[2], wind[3]);
        }
        if (location[1] >= 0) {
            float[] camera = SwayShader.camera();
            GL20.glUniform3f(location[1], camera[0], camera[1], camera[2]);
        }
    }
}
