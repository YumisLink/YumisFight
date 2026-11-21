package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.equipment.api.Weapon;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Champion kits are application content installed by the skills module; the encounter only consumes this contract. */
public final class AbyssChampionKits {
    public enum Form { NONE, OVERDRIVE, GHOST_FIELD, KALLA, WAVE_EYE, FRENZY, BLOOD_SWORD, METAMORPHOSIS,
        SUMMON, SUMMON_FRENZY, MOON, SUN, MOON_REFLUX }
    public enum Motion { STATIONARY, DASH, LEAP }
    /** Release conditions designed per skill; the encounter keeps the matching state. */
    public enum Condition { ALWAYS, AWAKENING, SUMMON_ACTIVE, SUN_HEART, MOON_HEART }

    /** One contact. Release is instant: delay only staggers later hits of the same cast. */
    public record Cue(AbyssAttack.Shape shape, AbyssAttack.Effect effect, boolean atTarget, double forward,
                      double side, double turn, double range, double width, long delay, double share) {
        public Cue {
            if (range <= 0 || width < 0 || delay < 0 || share < 0 || share > 1
                || !Double.isFinite(range + width + forward + side + turn + share))
                throw new IllegalArgumentException("无效深渊冠军技能接触：" + effect);
        }
    }

    public record Kit(String id, String kind, String title, boolean magic, int element, double coefficient,
                      long cooldown, double reach, double enhance, int color, Condition condition, Form form,
                      long formTime, Motion motion, List<Cue> cues) {
        public Kit {
            if (cooldown < 0 || reach <= 0 || enhance < 1 || formTime < 0
                || !Double.isFinite(coefficient + reach + enhance + formTime) || form == null || condition == null)
                throw new IllegalArgumentException("无效深渊冠军技能：" + id);
            cues = List.copyOf(cues);
            if (cues.isEmpty() || cues.getFirst().delay() != 0)
                throw new IllegalArgumentException("深渊冠军技能必须携带瞬发接触：" + id);
            double total = cues.stream().mapToDouble(Cue::share).sum();
            if (Math.abs(total - 1) > 1e-9) throw new IllegalArgumentException("接触份额必须合计为 1：" + id);
        }
    }

    private static final Map<String, List<Kit>> KITS = new LinkedHashMap<>();
    private static final Map<String, String> WEAPONS = new LinkedHashMap<>();

    /** The weapon type matches the career's own weapon so champions visibly wield the same armament. */
    public static synchronized void install(String job, List<Kit> kits, String weapon) {
        if (Weapon.standard().stream().map(Enum::name).noneMatch(weapon::equals))
            throw new IllegalArgumentException("无效深渊冠军武器：" + weapon);
        if (KITS.putIfAbsent(job, List.copyOf(kits)) != null || WEAPONS.putIfAbsent(job, weapon) != null)
            throw new IllegalArgumentException("重复登记深渊冠军技能组：" + job);
    }

    public static synchronized List<Kit> kits(String job) {
        List<Kit> kits = KITS.get(job);
        if (kits == null) throw new IllegalArgumentException("该职业没有深渊冠军技能组：" + job);
        return kits;
    }

    public static synchronized String weapon(String job) {
        String weapon = WEAPONS.get(job);
        if (weapon == null) throw new IllegalArgumentException("该职业没有深渊冠军技能组：" + job);
        return weapon;
    }

    private AbyssChampionKits() {}
}
