package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.Runtime;
import cn.yumis.fight.modules.equipment.api.EquipmentEffects;

import java.util.*;

/**
 * 封印塔第50层首领伊蕾娜的客户端权威技能冷却与软狂暴封印纯规则：
 * 1) 80% 转阶段全主动技能进入 100 秒冷却（不乘封印塔 0.5x）；
 * 2) 技能 10 回溯后随机 1 主动技能进冷却（0 秒基础冷却技能固定兜底 15 秒，不乘 0.5x）；
 * 3) 技能 11 时之封印优先永封快捷栏未封主动技能，冷却设为 999999 秒；
 * 4) 击败 / 全灭 / 退塔 / 换场次时清除所有永封冷却，绝不残留进永久存档。
 */
public final class SealBossSkillPenalties {
    private static final long CLEANUP_MIN_REMAINING_MS = 120_000L;

    private SealBossSkillPenalties() {}

    /** 返回当前玩家所有已习得（含装备授予）及已绑定快捷栏的非被动主动技能 ID（保持去重有序）。 */
    public static List<String> learnedActiveSkills(Progress progress, Stats stats) {
        var result = new LinkedHashSet<String>();
        for (String binding : progress.slots) {
            if (isUsableActiveSkill(binding, progress, stats)) result.add(binding);
        }
        for (SkillDefinition skill : SkillCatalog.all()) {
            if (!skill.passive() && SkillLearning.effective(stats, progress, skill) > 0) {
                result.add(skill.id);
            }
        }
        return List.copyOf(result);
    }

    /** 返回当前快捷栏已装备的非被动主动技能 ID（去重有序）。 */
    public static List<String> equippedActiveSkills(Progress progress, Stats stats) {
        var result = new LinkedHashSet<String>();
        for (String binding : progress.slots) {
            if (isUsableActiveSkill(binding, progress, stats)) result.add(binding);
        }
        return List.copyOf(result);
    }

    private static boolean isUsableActiveSkill(String skillId, Progress progress, Stats stats) {
        if (skillId == null || skillId.isEmpty() || !SkillCatalog.contains(skillId)) return false;
        SkillDefinition def = SkillCatalog.require(skillId);
        return !def.passive() && SkillLearning.effective(stats, progress, def) > 0;
    }

    /** 80% 转阶段 10 秒冰冻结束：所有主动技能至少进入 cooldownMs（100 秒）冷却，不乘封印塔 0.5x。 */
    public static int applyAllActiveCooldowns(Progress progress, Stats stats, Runtime runtime, long now, long cooldownMs) {
        List<String> active = learnedActiveSkills(progress, stats);
        long target = now + cooldownMs;
        for (String skillId : active) {
            runtime.cooldowns.merge(skillId, target, Math::max);
        }
        return active.size();
    }

    /** 计算技能 10 回溯应施加的冷却毫秒数：取当前面板实际冷却，基础/结算冷却为 0 时固定兜底 15000ms。 */
    public static long resolveRewindCooldownMs(String skillId, Progress progress, Stats stats,
                                               EquipmentEffects.Snapshot effects, long zeroCdFallbackMs) {
        if (!SkillCatalog.contains(skillId)) return zeroCdFallbackMs;
        SkillDefinition def = SkillCatalog.require(skillId);
        int rank = Math.max(1, SkillLearning.effective(stats, progress, def));
        if (def.cooldown(rank) <= 0) return zeroCdFallbackMs;
        boolean magic = CareerRules.magic(def, progress);
        double resolvedSeconds = SkillCosts.resolve(def, rank, stats,
            effects == null ? new EquipmentEffects.Snapshot() : effects, magic).cooldown();
        if (!(resolvedSeconds > 0) || !Double.isFinite(resolvedSeconds)) return zeroCdFallbackMs;
        return Math.max(1_000L, Math.round(resolvedSeconds * 1000.0));
    }

    /** 技能 10：随机 1 个未冷却（若全在冷却则任选未永封）主动技能进入冷却。返回被选中的技能 ID 或空串。 */
    public static String applyRewindRandomCooldown(Progress progress, Stats stats, EquipmentEffects.Snapshot effects,
                                                   Runtime runtime, Set<String> permaSealed, long now,
                                                   long zeroCdFallbackMs, Random random) {
        List<String> unsealed = learnedActiveSkills(progress, stats).stream()
            .filter(id -> !permaSealed.contains(id)).toList();
        if (unsealed.isEmpty()) return "";
        List<String> ready = unsealed.stream()
            .filter(id -> runtime.cooldowns.getOrDefault(id, 0L) <= now).toList();
        List<String> pool = !ready.isEmpty() ? ready : unsealed;
        String chosen = pool.get(random.nextInt(pool.size()));
        long cdMs = resolveRewindCooldownMs(chosen, progress, stats, effects, zeroCdFallbackMs);
        runtime.cooldowns.merge(chosen, now + cdMs, Math::max);
        return chosen;
    }

    /** 技能 11：优先永封快捷栏尚未被本技能封印的主动技能，全部封完后从其余已习得主动技能中抽取。 */
    public static String applyPermasealSkill(Progress progress, Stats stats, Runtime runtime,
                                             Set<String> permaSealed, long now, long sealDurationMs, Random random) {
        List<String> equippedUnsealed = equippedActiveSkills(progress, stats).stream()
            .filter(id -> !permaSealed.contains(id)).toList();
        List<String> candidates = !equippedUnsealed.isEmpty() ? equippedUnsealed
            : learnedActiveSkills(progress, stats).stream().filter(id -> !permaSealed.contains(id)).toList();
        if (candidates.isEmpty()) return "";
        String chosen = candidates.get(random.nextInt(candidates.size()));
        permaSealed.add(chosen);
        runtime.cooldowns.put(chosen, now + sealDurationMs);
        return chosen;
    }

    /** 击败、全灭回档、退塔、换场次时清除所有 999999s 软狂暴冷却。 */
    public static boolean clearPermasealedCooldowns(Runtime runtime, Set<String> permaSealed, long now) {
        boolean changed = false;
        for (String skillId : permaSealed) {
            Long until = runtime.cooldowns.get(skillId);
            if (until != null && until - now > CLEANUP_MIN_REMAINING_MS) {
                runtime.cooldowns.remove(skillId);
                changed = true;
            }
        }
        // 兜底：若因重连丢失本地 permaSealed 集合，同步清掉任何异常超长封印冷却（>300s）
        boolean extraRemoved = runtime.cooldowns.entrySet().removeIf(entry ->
            entry.getValue() != null && entry.getValue() - now > 300_000L);
        permaSealed.clear();
        return changed || extraRemoved;
    }
}
