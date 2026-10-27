package com.fantasyweapons.progression;

import com.fantasyweapons.ability.AbilityDefinition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent progression of ONE weapon item, stored as a data component on the ItemStack. Because it lives on the
 * stack it survives logout, restarts, inventory moves, death drops, chests and dimension changes, and it is
 * synchronised to the client automatically for the HUD and menu.
 * <p>
 * Only the server ever writes it (see {@link ExpService} and {@code AbilityService}).
 *
 * @param weaponId      unique id of this weapon instance (assigned on first server tick)
 * @param level         weapon level, 1..max
 * @param exp           EXP towards the next level
 * @param totalExp      lifetime EXP
 * @param upgrades      purchased ability upgrades (ability id → extra levels beyond 1)
 * @param masteryPoints unspent mastery points
 * @param form          index of the current form/mode
 * @param selected      id of the currently selected castable ability ("" = first available)
 * @param kills         lifetime kills
 * @param bossKills     lifetime boss kills
 */
public record WeaponData(Optional<UUID> weaponId, int level, long exp, long totalExp, Map<String, Integer> upgrades,
                         int masteryPoints, int form, String selected, int kills, int bossKills) {

    public static final WeaponData EMPTY = new WeaponData(Optional.empty(), 1, 0, 0, Map.of(), 0, 0, "", 0, 0);

    public static final Codec<WeaponData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("id").forGetter(WeaponData::weaponId),
            Codec.INT.optionalFieldOf("level", 1).forGetter(WeaponData::level),
            Codec.LONG.optionalFieldOf("exp", 0L).forGetter(WeaponData::exp),
            Codec.LONG.optionalFieldOf("total_exp", 0L).forGetter(WeaponData::totalExp),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("upgrades", Map.of()).forGetter(WeaponData::upgrades),
            Codec.INT.optionalFieldOf("mastery_points", 0).forGetter(WeaponData::masteryPoints),
            Codec.INT.optionalFieldOf("form", 0).forGetter(WeaponData::form),
            Codec.STRING.optionalFieldOf("selected", "").forGetter(WeaponData::selected),
            Codec.INT.optionalFieldOf("kills", 0).forGetter(WeaponData::kills),
            Codec.INT.optionalFieldOf("boss_kills", 0).forGetter(WeaponData::bossKills)
    ).apply(i, WeaponData::new));

    public static final StreamCodec<ByteBuf, WeaponData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WeaponData decode(ByteBuf buf) {
            Optional<UUID> id = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buf);
            int level = ByteBufCodecs.VAR_INT.decode(buf);
            long exp = ByteBufCodecs.VAR_LONG.decode(buf);
            long total = ByteBufCodecs.VAR_LONG.decode(buf);
            int n = ByteBufCodecs.VAR_INT.decode(buf);
            Map<String, Integer> up = new HashMap<>();
            for (int k = 0; k < n; k++) up.put(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf));
            int pts = ByteBufCodecs.VAR_INT.decode(buf);
            int form = ByteBufCodecs.VAR_INT.decode(buf);
            String sel = ByteBufCodecs.STRING_UTF8.decode(buf);
            int kills = ByteBufCodecs.VAR_INT.decode(buf);
            int boss = ByteBufCodecs.VAR_INT.decode(buf);
            return new WeaponData(id, level, exp, total, Map.copyOf(up), pts, form, sel, kills, boss);
        }

        @Override
        public void encode(ByteBuf buf, WeaponData d) {
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buf, d.weaponId());
            ByteBufCodecs.VAR_INT.encode(buf, d.level());
            ByteBufCodecs.VAR_LONG.encode(buf, d.exp());
            ByteBufCodecs.VAR_LONG.encode(buf, d.totalExp());
            ByteBufCodecs.VAR_INT.encode(buf, d.upgrades().size());
            d.upgrades().forEach((k, v) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, k);
                ByteBufCodecs.VAR_INT.encode(buf, v);
            });
            ByteBufCodecs.VAR_INT.encode(buf, d.masteryPoints());
            ByteBufCodecs.VAR_INT.encode(buf, d.form());
            ByteBufCodecs.STRING_UTF8.encode(buf, d.selected());
            ByteBufCodecs.VAR_INT.encode(buf, d.kills());
            ByteBufCodecs.VAR_INT.encode(buf, d.bossKills());
        }
    };

    public WeaponData {
        upgrades = Map.copyOf(upgrades);
    }

    /**
     * Effective ability level: 0 while locked, otherwise 1 + purchased upgrades, never above the max level nor above
     * what the current weapon level allows (keeps data valid if unlock levels are reconfigured).
     */
    public int abilityLevel(AbilityDefinition ability) {
        if (level < ability.unlockLevel()) return 0;
        int lvl = 1 + upgrades.getOrDefault(ability.id(), 0);
        lvl = Math.min(lvl, ability.maxLevel());
        while (lvl > 1 && ability.weaponLevelFor(lvl) > level) lvl--;
        return lvl;
    }

    public boolean isUnlocked(AbilityDefinition ability) {
        return abilityLevel(ability) > 0;
    }

    public int totalUpgrades() {
        int t = 0;
        for (int v : upgrades.values()) t += v;
        return t;
    }

    public UUID idOrNil() {
        return weaponId.orElse(new UUID(0, 0));
    }

    public WeaponData withId(UUID id) {
        return new WeaponData(Optional.of(id), level, exp, totalExp, upgrades, masteryPoints, form, selected, kills, bossKills);
    }

    public WeaponData withProgress(int newLevel, long newExp, long newTotal, int newPoints) {
        return new WeaponData(weaponId, newLevel, newExp, newTotal, upgrades, newPoints, form, selected, kills, bossKills);
    }

    public WeaponData withUpgrade(String abilityId, int extraLevels, int newPoints) {
        Map<String, Integer> up = new HashMap<>(upgrades);
        up.put(abilityId, extraLevels);
        return new WeaponData(weaponId, level, exp, totalExp, up, newPoints, form, selected, kills, bossKills);
    }

    public WeaponData withForm(int newForm) {
        return new WeaponData(weaponId, level, exp, totalExp, upgrades, masteryPoints, newForm, selected, kills, bossKills);
    }

    public WeaponData withSelected(String abilityId) {
        return new WeaponData(weaponId, level, exp, totalExp, upgrades, masteryPoints, form, abilityId, kills, bossKills);
    }

    public WeaponData withKills(int newKills, int newBossKills, int newPoints) {
        return new WeaponData(weaponId, level, exp, totalExp, upgrades, newPoints, form, selected, newKills, newBossKills);
    }
}
