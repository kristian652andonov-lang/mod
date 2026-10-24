package com.fantasyweapons.armor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a player's full-set ability is doing right now: stored souls, the storm counter, a primed rift, totality, the
 * grove... The first fields are synced to the wearer for the HUD; the rest only matter on the server. Not saved.
 */
public final class SetState {
    /** Soulreaper: souls stored by kills, spent by the next ability. */
    public int souls;
    /** Stormbreaker: hits landed towards the next lightning bolt. */
    public int stormHits;
    /** Voidfang: game time until which the next strike tears a rift. */
    public long riftUntil;
    /** Eclipse Reaper: game time until which both modes' bonuses apply, and when the next wave is ready. */
    public long totalityUntil, totalityReadyAt;
    /** Bloomfall: standing still long enough for the grove to take root. */
    public boolean grove;
    /** Doomcleaver: below half health. */
    public boolean frenzy;
    /** Gravebite: spectral allies currently fighting. */
    public int allies;
    /** Pyromancer: spell hits landed towards the next nova. */
    public int pyreHits;

    // ---- server only ----
    /** Soulreaper: souls being spent by the current cast, and until when its damage is empowered. */
    transient int empowered;
    transient long empoweredUntil;
    transient int stillTicks;
    transient double lastX = Double.NaN, lastZ = Double.NaN;
    transient long trailReadyAt;
    /** Tidemancer and Luminarch: when the next wave and the next ray are ready. */
    transient long tideReadyAt, judgementReadyAt;
    /** Cryomancer and Archdruid: per creature (entity id), when it can next be locked in ice or rooted. */
    transient java.util.Map<Integer, Long> freezeReadyAt = new java.util.HashMap<>(), rootReadyAt = new java.util.HashMap<>();

    public static final StreamCodec<RegistryFriendlyByteBuf, SetState> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SetState decode(RegistryFriendlyByteBuf buf) {
            SetState s = new SetState();
            s.souls = buf.readVarInt();
            s.stormHits = buf.readVarInt();
            s.riftUntil = buf.readVarLong();
            s.totalityUntil = buf.readVarLong();
            s.totalityReadyAt = buf.readVarLong();
            s.grove = buf.readBoolean();
            s.frenzy = buf.readBoolean();
            s.allies = buf.readVarInt();
            s.pyreHits = buf.readVarInt();
            return s;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SetState s) {
            buf.writeVarInt(s.souls);
            buf.writeVarInt(s.stormHits);
            buf.writeVarLong(s.riftUntil);
            buf.writeVarLong(s.totalityUntil);
            buf.writeVarLong(s.totalityReadyAt);
            buf.writeBoolean(s.grove);
            buf.writeBoolean(s.frenzy);
            buf.writeVarInt(s.allies);
            buf.writeVarInt(s.pyreHits);
        }
    };
}
