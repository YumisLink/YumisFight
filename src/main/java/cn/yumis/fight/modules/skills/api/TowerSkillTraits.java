package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Skill eligibility for tower bindings, supplied to the draft by the client. */
public final class TowerSkillTraits {
    private static final Set<String> ROTATION = Set.of(CareerSkills.AGNI, DemonHunterSkills.AURA,
        DemonHunterSkills.BEAM, OracleSkills.SMILE);
    private static final Set<String> BUFFS = Set.of(SkillCatalog.OVERDRIVE.id, CareerSkills.EYE,
        CareerSkills.KALLA, CareerSkills.SWORD, SummonerSkills.FRENZY, DemonHunterSkills.META);
    private static final Set<String> MOVEMENT = Set.of(SkillCatalog.BLINK.id, SkillCatalog.TRIPLE.id,
        SkillCatalog.RISING.id, SkillCatalog.DRAGON.id, CareerSkills.PHANTOM, CareerSkills.CRASH,
        CareerSkills.ERUPTION, DemonHunterSkills.RUSH, DemonHunterSkills.HUNT, DemonHunterSkills.DANCE,
        DemonHunterSkills.BEAM, DemonHunterSkills.META);

    private TowerSkillTraits() {}

    public static boolean rotating(String skill) { return ROTATION.contains(skill); }
    public static boolean displaces(String skill) { return MOVEMENT.contains(skill); }

    public static List<TowerBattleCard.SkillChoice> choices(Progress progress, Stats stats) {
        var skills = new ArrayList<TowerBattleCard.SkillChoice>();
        for (SkillDefinition skill : SkillCatalog.all()) {
            if (skill.passive()) continue;
            int rank = SkillLearning.effective(stats, progress, skill);
            if (rank <= 0) continue;
            boolean damage = SkillBalance.weight(skill) > 0 && (!skill.id.equals(CareerSkills.FIELD) || rank >= 8);
            boolean single = skill.id.equals(DemonHunterSkills.HUNT);
            if (skill.projectile != null && !skill.projectile.explodes()) {
                double pierces = skill.parameters(rank).getOrDefault("pierces", (double) skill.projectile.pierces());
                single = pierces == 1;
            }
            skills.add(new TowerBattleCard.SkillChoice(skill.id, skill.title, damage, single, BUFFS.contains(skill.id)));
        }
        return List.copyOf(skills);
    }
}
