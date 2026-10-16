package com.fantasyweapons.client.gui;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.ability.AbilityDefinition;
import com.fantasyweapons.client.ClientState;
import com.fantasyweapons.client.hud.UiDraw;
import com.fantasyweapons.client.vfx.Colors;
import com.fantasyweapons.network.C2SPayloads;
import com.fantasyweapons.progression.WeaponData;
import com.fantasyweapons.weapon.FantasyWeaponItem;
import com.fantasyweapons.weapon.WeaponDefinition;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * DOMINION SIGHT: the targeting map of the remote abilities. A top-down map of the land within the ability's reach
 * (32 chunks), drawn from the chunks the client has loaded; land beyond sight is uncharted dark but can still be
 * targeted. The reticle shows the spell's real size on the map; a click casts it there.
 */
public class TargetMapScreen extends Screen {
    /** Blocks per map texel. */
    private static final int RES = 2;
    private static final int BLOOD = 0xE0213A;

    private final WeaponDefinition def;
    private final List<AbilityDefinition> remotes;
    private AbilityDefinition ability;
    /** Map centre (the player's block when opened) and reach in blocks. */
    private final int cx, cz, reach;
    private final int texSize;
    private DynamicTexture texture;
    private ResourceLocation textureId;
    private short[] heights;
    private float zoom = 1;
    private double viewX, viewZ;
    private boolean panning;
    private double panFromX, panFromY, panViewX, panViewZ;
    private float time;
    private long openedAt;

    public TargetMapScreen(WeaponDefinition def, AbilityDefinition ability) {
        super(Component.literal("Dominion Sight"));
        this.def = def;
        this.ability = ability;
        this.remotes = def.castables().stream().filter(AbilityDefinition::remote).toList();
        Player p = Minecraft.getInstance().player;
        this.cx = p == null ? 0 : p.getBlockX();
        this.cz = p == null ? 0 : p.getBlockZ();
        this.reach = (int) (ability.param("remote_range") * 16);
        this.texSize = Math.max(16, reach * 2 / RES);
        this.viewX = cx;
        this.viewZ = cz;
    }

    /** Opens the map for the held weapon's selected remote ability; false if it is not one. */
    public static boolean openFor(ItemStack held) {
        if (!(held.getItem() instanceof FantasyWeaponItem item)) return false;
        WeaponData data = FantasyWeaponItem.data(held);
        AbilityDefinition a = com.fantasyweapons.ability.AbilityService.selected(item.definition(), data);
        if (a == null || !a.remote() || !data.isUnlocked(a)) return false;
        Minecraft.getInstance().setScreen(new TargetMapScreen(item.definition(), a));
        return true;
    }

    @Override
    protected void init() {
        if (texture == null) buildMap();
        openedAt = System.currentTimeMillis();
        if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundEvents.BEACON_ACTIVATE, 0.5f, 0.6f);
    }

    // ------------------------------------------------------------------------------------------------------------
    // the map texture
    // ------------------------------------------------------------------------------------------------------------

    private void buildMap() {
        ClientLevel level = Minecraft.getInstance().level;
        NativeImage img = new NativeImage(texSize, texSize, false);
        heights = new short[texSize * texSize];
        int x0 = cx - reach, z0 = cz - reach;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int j = 0; j < texSize; j++) {
            int prevH = Integer.MIN_VALUE;
            for (int i = 0; i < texSize; i++) {
                int x = x0 + i * RES + RES / 2, z = z0 + j * RES + RES / 2;
                LevelChunk chunk = level == null ? null : (LevelChunk) level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
                if (chunk == null || chunk.isEmpty()) {
                    heights[j * texSize + i] = Short.MIN_VALUE;
                    img.setPixelRGBA(i, j, uncharted(i, j));
                    continue;
                }
                int h = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) - 1;
                pos.set(x, h, z);
                BlockState s = chunk.getBlockState(pos);
                int depth = 0;
                while (!s.getFluidState().isEmpty() && depth < 12 && h - depth > level.getMinBuildHeight()) {
                    depth++;
                    pos.setY(h - depth);
                    s = chunk.getBlockState(pos);
                }
                if (depth > 0) s = chunk.getBlockState(pos.setY(h));
                int northH = j > 0 ? heights[(j - 1) * texSize + i] : h;
                if (northH == Short.MIN_VALUE) northH = h;
                MapColor mc = s.getMapColor(level, pos);
                MapColor.Brightness b;
                if (depth > 0) {
                    b = depth > 6 ? MapColor.Brightness.LOW : depth > 2 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH;
                } else {
                    b = h > northH ? MapColor.Brightness.HIGH : h < northH ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                }
                int abgr = mc == MapColor.NONE ? 0xFF101010 : mc.calculateRGBColor(b);
                img.setPixelRGBA(i, j, grade(abgr));
                heights[j * texSize + i] = (short) h;
                prevH = h;
            }
        }
        texture = new DynamicTexture(img);
        textureId = FantasyWeapons.id("dynamic/target_map");
        Minecraft.getInstance().getTextureManager().register(textureId, texture);
    }

    /** Land out of sight: dark, with a faint weave so it reads as unknown rather than void. */
    private static int uncharted(int i, int j) {
        int v = ((i * 7 + j * 13) ^ (i * j)) & 7;
        int g = 10 + v;
        return 0xFF000000 | (g + 2) << 16 | g << 8 | (g + 6);
    }

    /** Colour grading: a little desaturated and darkened, warm in the highlights, so the map reads as a scrying image. */
    private static int grade(int abgr) {
        int r = abgr & 255, g = abgr >> 8 & 255, b = abgr >> 16 & 255;
        int lum = (r * 3 + g * 6 + b) / 10;
        r = (int) ((r * 0.78f + lum * 0.22f) * 0.86f);
        g = (int) ((g * 0.78f + lum * 0.22f) * 0.82f);
        b = (int) ((b * 0.78f + lum * 0.22f) * 0.80f);
        return 0xFF000000 | Math.min(255, b) << 16 | Math.min(255, g) << 8 | Math.min(255, r);
    }

    @Override
    public void removed() {
        if (textureId != null) Minecraft.getInstance().getTextureManager().release(textureId);
        texture = null;
    }

    // ------------------------------------------------------------------------------------------------------------
    // layout
    // ------------------------------------------------------------------------------------------------------------

    private float mapSize() {
        return Math.min(width - 170, height - 92);
    }

    private float mapX() {
        return (width - mapSize()) / 2f + 40;
    }

    private float mapY() {
        return 50;
    }

    /** Half the width of the view, in blocks. */
    private double half() {
        return reach / zoom;
    }

    private double worldX(double sx) {
        return viewX + ((sx - mapX()) / mapSize() - 0.5) * 2 * half();
    }

    private double worldZ(double sy) {
        return viewZ + ((sy - mapY()) / mapSize() - 0.5) * 2 * half();
    }

    private float screenX(double wx) {
        return (float) (mapX() + ((wx - viewX) / (2 * half()) + 0.5) * mapSize());
    }

    private float screenY(double wz) {
        return (float) (mapY() + ((wz - viewZ) / (2 * half()) + 0.5) * mapSize());
    }

    private float scale() {
        return (float) (mapSize() / (2 * half()));
    }

    private boolean overMap(double mx, double my) {
        return mx >= mapX() && mx <= mapX() + mapSize() && my >= mapY() && my <= mapY() + mapSize();
    }

    private boolean inReach(double wx, double wz) {
        double dx = wx - cx, dz = wz - cz;
        return dx * dx + dz * dz <= (double) reach * reach;
    }

    /** Ground height at a world column if it was charted, else null. */
    @Nullable
    private Integer heightAt(double wx, double wz) {
        int i = (int) Math.floor((wx - (cx - reach)) / RES), j = (int) Math.floor((wz - (cz - reach)) / RES);
        if (i < 0 || j < 0 || i >= texSize || j >= texSize) return null;
        short h = heights[j * texSize + i];
        return h == Short.MIN_VALUE ? null : (int) h;
    }

    private long cooldownLeft() {
        ItemStack held = ClientState.heldWeapon(Minecraft.getInstance().player);
        if (held.isEmpty()) return 0;
        return ClientState.cooldownRemaining(FantasyWeaponItem.data(held), ability);
    }

    /** The size the spell covers on the ground, in blocks (its radius, or half a canyon's length). */
    private double footprint() {
        if (ability.hasParam("radius")) return ability.param("radius");
        if (ability.hasParam("length")) return ability.param("length") / 2;
        return 32;
    }

    // ------------------------------------------------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // no blur: the map is the whole screen
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        time += minecraft.getTimer().getRealtimeDeltaTicks();
        float open = Math.min(1, (System.currentTimeMillis() - openedAt) / 350f);
        int theme = def.themePrimary();
        int accent = elementColor(ability);
        // the void around the map: black, bleeding red at the edges
        UiDraw.rect(g, 0, 0, width, height, Colors.alpha(0.985f * open, 0x050206));
        UiDraw.vGradient(g, 0, 0, width, height * 0.3f, Colors.alpha(0.35f * open, 0x3A0008), 0);
        UiDraw.vGradient(g, 0, height * 0.7f, width, height, 0, Colors.alpha(0.35f * open, 0x3A0008));

        float mx = mapX(), my = mapY(), ms = mapSize();
        // the map itself
        g.enableScissor((int) mx, (int) my, (int) (mx + ms), (int) (my + ms));
        double u0 = (viewX - half() - (cx - reach)) / (2.0 * reach), v0 = (viewZ - half() - (cz - reach)) / (2.0 * reach);
        double u1 = u0 + 1 / zoom, v1 = v0 + 1 / zoom;
        if (textureId != null) UiDraw.textureUv(g, textureId, mx, my, ms, ms, (float) u0, (float) v0, (float) u1, (float) v1, Colors.alpha(open, 0xFFFFFF));
        // chunk grid when zoomed in
        if (zoom >= 2) {
            int grid = Colors.alpha(0.12f * open, 0x000000);
            double gx0 = Math.floor((viewX - half()) / 16) * 16, gz0 = Math.floor((viewZ - half()) / 16) * 16;
            for (double gx = gx0; gx <= viewX + half(); gx += 16) UiDraw.rect(g, screenX(gx), my, screenX(gx) + 0.5f, my + ms, grid);
            for (double gz = gz0; gz <= viewZ + half(); gz += 16) UiDraw.rect(g, mx, screenY(gz), mx + ms, screenY(gz) + 0.5f, grid);
        }
        // beyond reach: shadowed
        float pcx = screenX(cx), pcy = screenY(cz), rr = (float) (reach * scale());
        UiDraw.arc(g, pcx, pcy, rr, rr + ms * 2, 0, (float) (Math.PI * 2), Colors.alpha(0.62f * open, 0x050206), 96);
        drawPlayers(g, open);
        drawReticle(g, mouseX, mouseY, accent, open);
        g.disableScissor();

        // the reach: a turning rune ring
        drawReachRing(g, pcx, pcy, rr, accent, open, mx, my, ms);
        // frame
        UiDraw.border(g, mx - 2, my - 2, mx + ms + 2, my + ms + 2, 1, Colors.alpha(0.9f * open, 0x2A0A0E));
        UiDraw.border(g, mx - 1, my - 1, mx + ms + 1, my + ms + 1, 1, Colors.alpha(0.8f * open, Colors.lerpRgb(BLOOD, theme, 0.3f)));
        corners(g, mx - 6, my - 6, mx + ms + 6, my + ms + 6, Colors.alpha(open, accent));
        drawCompass(g, mx, my, ms, open);

        drawHeader(g, open, accent);
        drawSide(g, mouseX, mouseY, open, accent);
        drawFooter(g, mouseX, mouseY, open, accent);
    }

    private void drawPlayers(GuiGraphics g, float open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            float sx = screenX(p.getX()), sy = screenY(p.getZ());
            UiDraw.glow(g, sx, sy, 10, Colors.alpha(0.6f * open, BLOOD));
            UiDraw.disc(g, sx, sy, 2.2f, Colors.alpha(open, 0xFF5A6A));
            UiDraw.textCentered(g, p.getName().getString(), sx, sy - 9, Colors.alpha(0.85f * open, 0xFFD0D0), 0.5f, true);
        }
        // the caster: an arrow pointing where they face
        float sx = screenX(mc.player.getX()), sy = screenY(mc.player.getZ());
        double yaw = Math.toRadians(mc.player.getYRot());
        float fx = (float) -Math.sin(yaw), fz = (float) Math.cos(yaw);
        float s = 6;
        float tipX = sx + fx * s, tipY = sy + fz * s;
        float lx = sx - fx * s * 0.6f + fz * s * 0.6f, ly = sy - fz * s * 0.6f - fx * s * 0.6f;
        float rx = sx - fx * s * 0.6f - fz * s * 0.6f, ry = sy - fz * s * 0.6f + fx * s * 0.6f;
        UiDraw.glow(g, sx, sy, 16, Colors.alpha(0.5f * open, 0xFFFFFF));
        int c = Colors.alpha(open, 0xFFF4D6);
        UiDraw.line(g, tipX, tipY, lx, ly, 1.6f, c, c);
        UiDraw.line(g, tipX, tipY, rx, ry, 1.6f, c, c);
        UiDraw.line(g, lx, ly, sx - fx * s * 0.2f, sy - fz * s * 0.2f, 1.6f, c, c);
        UiDraw.line(g, rx, ry, sx - fx * s * 0.2f, sy - fz * s * 0.2f, 1.6f, c, c);
    }

    private void drawReticle(GuiGraphics g, int mouseX, int mouseY, int accent, float open) {
        if (!overMap(mouseX, mouseY) || panning) return;
        double wx = worldX(mouseX), wz = worldZ(mouseY);
        boolean ok = inReach(wx, wz) && cooldownLeft() <= 0;
        int col = ok ? accent : 0xFF4040;
        float r = (float) (footprint() * scale());
        float full = (float) (Math.PI * 2);
        // the area the spell will cover, to scale
        UiDraw.arc(g, mouseX, mouseY, 0, r, 0, full, Colors.alpha(0.16f * open, col), 64);
        UiDraw.arc(g, mouseX, mouseY, r - 1.2f, r, 0, full, Colors.alpha(0.95f * open, col), 64);
        UiDraw.arc(g, mouseX, mouseY, r * 0.62f - 0.6f, r * 0.62f, 0, full, Colors.alpha(0.5f * open, col), 48);
        float spin = time * 0.03f;
        for (int i = 0; i < 12; i++) {
            float a = spin + i * full / 12;
            UiDraw.arc(g, mouseX, mouseY, r * 0.66f, r - 2.5f, a - 0.05f, a + 0.05f, Colors.alpha(0.7f * open, col), 1);
        }
        // a canyon is a line across the land, square to the line from the caster
        if (!ability.hasParam("radius") && ability.hasParam("length")) {
            double dx = wx - cx, dz = wz - cz, len = Math.max(1e-3, Math.hypot(dx, dz));
            float px = (float) (-dz / len), pz = (float) (dx / len);
            float w = (float) (ability.param("width") * 0.5 * scale());
            UiDraw.line(g, mouseX - px * r, mouseY - pz * r, mouseX + px * r, mouseY + pz * r, Math.max(1.5f, w), Colors.alpha(0.45f * open, col),
                    Colors.alpha(0.45f * open, col));
        }
        // crosshair
        int ch = Colors.alpha(open, 0xFFFFFF);
        UiDraw.rect(g, mouseX - 8, mouseY - 0.5f, mouseX - 3, mouseY + 0.5f, ch);
        UiDraw.rect(g, mouseX + 3, mouseY - 0.5f, mouseX + 8, mouseY + 0.5f, ch);
        UiDraw.rect(g, mouseX - 0.5f, mouseY - 8, mouseX + 0.5f, mouseY - 3, ch);
        UiDraw.rect(g, mouseX - 0.5f, mouseY + 3, mouseX + 0.5f, mouseY + 8, ch);
        UiDraw.glow(g, mouseX, mouseY, 8, Colors.alpha(0.8f * open, col));
    }

    private void drawReachRing(GuiGraphics g, float x, float y, float r, int accent, float open, float mx, float my, float ms) {
        g.enableScissor((int) mx - 8, (int) my - 8, (int) (mx + ms + 8), (int) (my + ms + 8));
        float full = (float) (Math.PI * 2);
        UiDraw.arc(g, x, y, r - 0.8f, r + 0.8f, 0, full, Colors.alpha(0.9f * open, BLOOD), 128);
        UiDraw.arc(g, x, y, r + 3, r + 3.6f, 0, full, Colors.alpha(0.5f * open, accent), 128);
        float spin = time * 0.006f;
        for (int i = 0; i < 64; i++) {
            float a = spin + i * full / 64, len = i % 8 == 0 ? 0.02f : 0.006f;
            UiDraw.arc(g, x, y, r + 1.2f, r + (i % 8 == 0 ? 7 : 4), a - len, a + len, Colors.alpha((i % 8 == 0 ? 0.9f : 0.5f) * open, accent), 1);
        }
        g.disableScissor();
    }

    private void drawCompass(GuiGraphics g, float mx, float my, float ms, float open) {
        int c = Colors.alpha(0.95f * open, UiDraw.GOLD_LIGHT), plate = Colors.alpha(open, 0x0A0306);
        float[][] at = {{mx + ms / 2, my}, {mx + ms / 2, my + ms}, {mx, my + ms / 2}, {mx + ms, my + ms / 2}};
        String[] letters = {"N", "S", "W", "E"};
        for (int i = 0; i < 4; i++) {
            UiDraw.diamond(g, at[i][0], at[i][1], 7, plate);
            UiDraw.textCentered(g, letters[i], at[i][0] + 0.3f, at[i][1] - 2.6f, c, 0.62f, false);
        }
    }

    private static void corners(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
        float l = 14, t = 1.5f;
        UiDraw.rect(g, x0, y0, x0 + l, y0 + t, argb);
        UiDraw.rect(g, x0, y0, x0 + t, y0 + l, argb);
        UiDraw.rect(g, x1 - l, y0, x1, y0 + t, argb);
        UiDraw.rect(g, x1 - t, y0, x1, y0 + l, argb);
        UiDraw.rect(g, x0, y1 - t, x0 + l, y1, argb);
        UiDraw.rect(g, x0, y1 - l, x0 + t, y1, argb);
        UiDraw.rect(g, x1 - l, y1 - t, x1, y1, argb);
        UiDraw.rect(g, x1 - t, y1 - l, x1, y1, argb);
    }

    private void drawHeader(GuiGraphics g, float open, int accent) {
        UiDraw.text(g, "DOMINION SIGHT  //  " + def.displayName().toUpperCase(Locale.ROOT), 10, 8, Colors.alpha(0.75f * open, 0xC8A0A0), 0.55f, false);
        UiDraw.textRight(g, "REACH " + reach / 16 + " CHUNKS", width - 10, 8, Colors.alpha(0.75f * open, 0xC8A0A0), 0.55f, false);
    }

    /** The remote spells of the weapon, to pick from; the chosen one is cast. */
    private void drawSide(GuiGraphics g, int mouseX, int mouseY, float open, int accent) {
        float x = 12, y = mapY();
        UiDraw.text(g, "CHOOSE THE SPELL", x, y - 12, Colors.alpha(0.7f * open, 0xC8A0A0), 0.5f, false);
        for (AbilityDefinition a : remotes) {
            boolean sel = a == ability;
            boolean hover = mouseX >= x && mouseX <= x + 22 && mouseY >= y && mouseY <= y + 22;
            int c = elementColor(a);
            if (sel) UiDraw.glow(g, x + 11, y + 11, 34, Colors.alpha(0.55f * open, c));
            UiDraw.texture(g, a.icon(), x, y, 22, 22, Colors.alpha(open * (sel || hover ? 1f : 0.55f), 0xFFFFFF));
            if (sel) UiDraw.border(g, x - 1, y - 1, x + 23, y + 23, 1, Colors.alpha(open, c));
            if (hover && !sel) UiDraw.text(g, a.name(), x + 27, y + 7, Colors.alpha(open, 0xFFFFFF), 0.55f, true);
            y += 27;
        }
    }

    private void drawFooter(GuiGraphics g, int mouseX, int mouseY, float open, int accent) {
        float mx = mapX(), ms = mapSize();
        // the spell
        UiDraw.title(g, ability.name().toUpperCase(Locale.ROOT), mx + ms / 2, mapY() - 36, Colors.alpha(open, accent), 0.95f);
        long cd = cooldownLeft();
        String sub = cd > 0 ? String.format("SEALED  ·  %d:%02d", cd / 20 / 60, cd / 20 % 60) : "CHOOSE WHERE IT FALLS";
        UiDraw.textCentered(g, sub, mx + ms / 2, mapY() - 22, Colors.alpha(0.85f * open, cd > 0 ? 0xFF6A6A : 0xE8D8C8), 0.55f, false);
        // readout at the cursor
        float ry = height - 14;
        if (overMap(mouseX, mouseY)) {
            double wx = worldX(mouseX), wz = worldZ(mouseY);
            double dist = Math.hypot(wx - cx, wz - cz);
            Integer h = heightAt(wx, wz);
            boolean ok = inReach(wx, wz);
            String s = String.format("X %d   Z %d   %s   %,.0f m  ·  %.1f chunks", (int) Math.floor(wx), (int) Math.floor(wz),
                    h == null ? "UNCHARTED" : "Y " + h, dist, dist / 16);
            UiDraw.textCentered(g, s, mx + ms / 2, mapY() + ms + 11, Colors.alpha(open, ok ? 0xF4E8D8 : 0xFF6060), 0.6f, true);
            if (!ok) UiDraw.textCentered(g, "[ OUT OF REACH ]", mx + ms / 2, mapY() + ms - 14, Colors.alpha(open, 0xFF4040), 0.6f, true);
        }
        UiDraw.textCentered(g, "LEFT CLICK  CAST     SCROLL  ZOOM     RIGHT DRAG  PAN     ESC  CANCEL", width / 2f, ry,
                Colors.alpha(0.55f * open, 0xB8A898), 0.5f, false);
    }

    private static int elementColor(AbilityDefinition a) {
        // the six powers, by the ability's icon colour
        String id = a.id();
        if (id.contains("execution") || id.contains("slash")) return 0xFFE58A;
        if (id.contains("blackhole") || id.contains("dimension")) return 0xB070FF;
        if (id.contains("eruption") || id.contains("dragon")) return 0xFF6A2A;
        if (id.contains("tectonic") || id.contains("earthquake")) return 0xD8A868;
        if (id.contains("tree") || id.contains("thorn")) return 0x6BE873;
        if (id.contains("ice")) return 0x8FE4FF;
        return 0xFFFFFF;
    }

    // ------------------------------------------------------------------------------------------------------------
    // input
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // the spell list
        float x = 12, y = mapY();
        for (AbilityDefinition a : remotes) {
            if (mouseX >= x && mouseX <= x + 22 && mouseY >= y && mouseY <= y + 22) {
                ability = a;
                minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.4f);
                return true;
            }
            y += 27;
        }
        if (!overMap(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 1) {
            panning = true;
            panFromX = mouseX;
            panFromY = mouseY;
            panViewX = viewX;
            panViewZ = viewZ;
            return true;
        }
        if (button == 0) {
            double wx = worldX(mouseX), wz = worldZ(mouseY);
            if (!inReach(wx, wz) || cooldownLeft() > 0) {
                minecraft.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.5f);
                return true;
            }
            Integer h = heightAt(wx, wz);
            double wy = h != null ? h + 1 : minecraft.player.getY();
            PacketDistributor.sendToServer(new C2SPayloads.CastAt(ability.id(), wx, wy, wz));
            minecraft.player.playSound(SoundEvents.WITHER_SPAWN, 0.35f, 1.6f);
            onClose();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1) panning = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panning) {
            double per = 2 * half() / mapSize();
            viewX = panViewX - (mouseX - panFromX) * per;
            viewZ = panViewZ - (mouseY - panFromY) * per;
            clampView();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!overMap(mouseX, mouseY) || scrollY == 0) return false;
        double wx = worldX(mouseX), wz = worldZ(mouseY);
        float next = Math.max(1, Math.min(8, scrollY > 0 ? zoom * 2 : zoom / 2));
        if (next == zoom) return true;
        zoom = next;
        // keep the point under the cursor where it is
        viewX = wx - ((mouseX - mapX()) / mapSize() - 0.5) * 2 * half();
        viewZ = wz - ((mouseY - mapY()) / mapSize() - 0.5) * 2 * half();
        clampView();
        return true;
    }

    private void clampView() {
        double h = half();
        viewX = Math.max(cx - reach + h, Math.min(cx + reach - h, viewX));
        viewZ = Math.max(cz - reach + h, Math.min(cz + reach - h, viewZ));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
