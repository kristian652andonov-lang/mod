package com.fantasyweapons.network;

import com.fantasyweapons.FantasyWeapons;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: "play this custom visual/audio effect". Gameplay already happened on the server; this only tells
 * clients what to draw. One generic payload keeps the protocol small while every effect interprets the fields it needs.
 *
 * @param fx       effect id (see {@link FxIds})
 * @param caster   entity id of the caster (-1 if none)
 * @param pos      primary world position
 * @param dir      primary direction (normalised) or secondary position, depending on effect
 * @param power    effect strength (usually charge multiplier or damage)
 * @param scale    size multiplier (radius / range)
 * @param level    ability level or flags
 * @param seed     random seed so every client renders the same shapes (e.g. lightning branches)
 * @param entities related entity ids (targets hit, chain order...)
 * @param points   extra positions (path points, chain hops...)
 */
public record FxPayload(ResourceLocation fx, int caster, Vec3 pos, Vec3 dir, float power, float scale, int level, long seed,
                        int[] entities, List<Vec3> points) implements CustomPacketPayload {
    public static final Type<FxPayload> TYPE = new Type<>(FantasyWeapons.id("fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FxPayload> CODEC = new StreamCodec<>() {
        @Override
        public FxPayload decode(RegistryFriendlyByteBuf buf) {
            ResourceLocation fx = buf.readResourceLocation();
            int caster = buf.readVarInt();
            Vec3 pos = readVec(buf);
            Vec3 dir = readVec(buf);
            float power = buf.readFloat();
            float scale = buf.readFloat();
            int level = buf.readVarInt();
            long seed = buf.readLong();
            int[] ents = buf.readVarIntArray(256);
            int n = Math.min(buf.readVarInt(), 512);
            List<Vec3> pts = new ArrayList<>(n);
            for (int i = 0; i < n; i++) pts.add(readVec(buf));
            return new FxPayload(fx, caster, pos, dir, power, scale, level, seed, ents, pts);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, FxPayload p) {
            buf.writeResourceLocation(p.fx());
            buf.writeVarInt(p.caster());
            writeVec(buf, p.pos());
            writeVec(buf, p.dir());
            buf.writeFloat(p.power());
            buf.writeFloat(p.scale());
            buf.writeVarInt(p.level());
            buf.writeLong(p.seed());
            buf.writeVarIntArray(p.entities());
            buf.writeVarInt(p.points().size());
            for (Vec3 v : p.points()) writeVec(buf, v);
        }
    };

    private static Vec3 readVec(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void writeVec(RegistryFriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    @Override
    public Type<FxPayload> type() {
        return TYPE;
    }

    public static Builder of(ResourceLocation fx) {
        return new Builder(fx);
    }

    public static final class Builder {
        private final ResourceLocation fx;
        private int caster = -1;
        private Vec3 pos = Vec3.ZERO;
        private Vec3 dir = Vec3.ZERO;
        private float power = 1;
        private float scale = 1;
        private int level;
        private long seed;
        private int[] entities = new int[0];
        private final List<Vec3> points = new ArrayList<>();

        private Builder(ResourceLocation fx) {
            this.fx = fx;
        }

        public Builder caster(int id) {
            this.caster = id;
            return this;
        }

        public Builder pos(Vec3 pos) {
            this.pos = pos;
            return this;
        }

        public Builder dir(Vec3 dir) {
            this.dir = dir;
            return this;
        }

        public Builder power(float power) {
            this.power = power;
            return this;
        }

        public Builder scale(float scale) {
            this.scale = scale;
            return this;
        }

        public Builder level(int level) {
            this.level = level;
            return this;
        }

        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        public Builder entities(int... ids) {
            this.entities = ids;
            return this;
        }

        public Builder entities(List<Integer> ids) {
            this.entities = ids.stream().mapToInt(Integer::intValue).toArray();
            return this;
        }

        public Builder point(Vec3 p) {
            this.points.add(p);
            return this;
        }

        public Builder points(List<Vec3> ps) {
            this.points.addAll(ps);
            return this;
        }

        public FxPayload build() {
            return new FxPayload(fx, caster, pos, dir, power, scale, level, seed, entities, List.copyOf(points));
        }
    }
}
