package com.fantasyweapons.gametest;

import com.fantasyweapons.FantasyWeapons;
import com.fantasyweapons.network.FxIds;
import com.fantasyweapons.network.FxPayload;
import com.fantasyweapons.network.WreckPayload;
import com.fantasyweapons.tool.OreSightPayload;
import com.fantasyweapons.world.wreck.WreckSender;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * What the mod sends over the network reads back exactly as it was written, at its biggest: a packet read short of
 * what was written, or bigger than a packet may be, makes the game drop the player ("Internal Exception"). The single
 * player game never writes its packets out, so only a server shows it; these write and read them as a server would.
 */
@GameTestHolder(FantasyWeapons.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkGameTests {
    private static final String ARENA = "arena";
    /** The most a custom packet to a player may hold (a few servers and proxies refuse bigger ones outright). */
    private static final int PACKET_LIMIT = 1 << 20;

    private NetworkGameTests() {
    }

    /** An effect asked to carry far more entities and points than one may is sent with as many as it may, and reads back whole. */
    @GameTest(template = ARENA)
    public static void effectsReadBackWhole(GameTestHelper h) {
        int[] ids = new int[5000];
        for (int i = 0; i < ids.length; i++) ids[i] = i * 977;
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i < 5000; i++) points.add(new Vec3(i * 1.5, 64 + i % 30, -i * 0.25));
        FxPayload sent = FxPayload.of(FxIds.DIMENSION_BREAKAGE).caster(42).pos(new Vec3(1, 2, 3)).dir(new Vec3(0, 1, 0)).power(3).scale(512)
                .level(9).seed(1234567L).entities(ids).points(points).build();
        FxPayload got = roundTrip(h, FxPayload.CODEC, sent);
        if (got.entities().length != FxPayload.MOST_ENTITIES) h.fail("read back " + got.entities().length + " entities");
        if (got.points().size() != FxPayload.MOST_POINTS) h.fail("read back " + got.points().size() + " points");
        if (got.entities()[17] != ids[17] || !got.points().get(33).equals(points.get(33)) || got.seed() != sent.seed()) h.fail("read back wrong");
        // and a small one exactly as it was
        FxPayload small = FxPayload.of(FxIds.SIX_GODS).caster(7).pos(new Vec3(5, 6, 7)).entities(1, 2, 3).point(new Vec3(9, 9, 9)).build();
        FxPayload back = roundTrip(h, FxPayload.CODEC, small);
        if (back.entities().length != 3 || back.points().size() != 1 || back.caster() != 7) h.fail("a small effect read back wrong");
        h.succeed();
    }

    /** Destruction of the most varied land there can be (every section a different block in every place) goes in packets small enough, each read back whole. */
    @GameTest(template = ARENA)
    public static void destructionReadsBackWhole(GameTestHelper h) {
        List<BlockState> states = new ArrayList<>();
        for (BlockState s : Block.BLOCK_STATE_REGISTRY) {
            states.add(s);
            if (states.size() == 4096) break;
        }
        // the tallest world the game allows: 254 sections, all of them changed
        int n = 254;
        int[] ys = new int[n];
        byte[][] data = new byte[n][];
        for (int k = 0; k < n; k++) {
            PalettedContainer<BlockState> c = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
            for (int i = 0; i < 4096; i++) c.set(i & 15, (i >> 8) & 15, (i >> 4) & 15, states.get((i + k * 31) % states.size()));
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            c.write(buf);
            data[k] = new byte[buf.readableBytes()];
            buf.readBytes(data[k]);
            ys[k] = k - 64;
        }
        List<WreckPayload> packets = WreckSender.packets(new ChunkPos(12, -7), ys, data);
        int sections = 0;
        for (WreckPayload p : packets) {
            WreckPayload got = roundTrip(h, WreckPayload.CODEC, p);
            if (got.sectionYs().length != p.sectionYs().length) h.fail("a destruction packet read back " + got.sectionYs().length + " sections of " + p.sectionYs().length);
            for (int k = 0; k < got.sectionYs().length; k++) {
                if (got.sectionYs()[k] != p.sectionYs()[k] || !java.util.Arrays.equals(got.sections()[k], p.sections()[k])) h.fail("a section read back wrong");
            }
            sections += got.sectionYs().length;
        }
        if (sections != n) h.fail("sent " + sections + " sections of " + n);
        if (packets.size() < 2) h.fail("all of it in one packet");
        h.succeed();
    }

    /** Ore Sight with more ores than it sends reads back as many as it sends. */
    @GameTest(template = ARENA)
    public static void oreSightReadsBackWhole(GameTestHelper h) {
        int n = OreSightPayload.MOST + 500;
        long[] at = new long[n];
        byte[] mats = new byte[n];
        for (int i = 0; i < n; i++) {
            at[i] = i * 31L;
            mats[i] = (byte) (i % 13);
        }
        OreSightPayload got = roundTrip(h, OreSightPayload.CODEC, new OreSightPayload(99L, at, mats, 200));
        if (got.positions().length != OreSightPayload.MOST || got.ticks() != 200) h.fail("ore sight read back wrong");
        h.succeed();
    }

    /** Writes {@code payload} as a server would, checks its size, reads it back and checks nothing is left over. */
    private static <T> T roundTrip(GameTestHelper h, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T payload) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        codec.encode(buf, payload);
        int size = buf.readableBytes();
        if (size >= PACKET_LIMIT) h.fail(payload.getClass().getSimpleName() + " of " + size + " bytes, more than a packet may hold");
        T got = codec.decode(buf);
        if (buf.readableBytes() != 0) h.fail(payload.getClass().getSimpleName() + " left " + buf.readableBytes() + " bytes unread");
        return got;
    }
}
