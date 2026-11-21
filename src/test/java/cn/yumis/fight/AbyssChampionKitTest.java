package cn.yumis.fight;

import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.dungeons.api.AbyssAttack;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits.Condition;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits.Cue;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits.Form;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits.Kit;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits.Motion;
import cn.yumis.fight.modules.dungeons.content.AbyssCareers;
import cn.yumis.fight.modules.equipment.api.EquipmentVisualItems;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.skills.api.SkillBalance;
import cn.yumis.fight.modules.skills.api.SkillCatalog;
import cn.yumis.fight.modules.skills.api.SkillDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Champion kits must stay a faithful transcription of the player catalog, not a parallel skill tree. */
class AbyssChampionKitTest {
    private static final String NS = "yumis_dungeon_and_fight:";

    static { SkillCatalog.all(); }

    @Test
    void kitsCoverExactlyTheActivePlayerSkillsAndSignatureWeaponOfEveryAbyssCareer() {
        for (AbyssCareers.Career career : AbyssCareers.ALL) {
            String weapon = AbyssChampionKits.weapon(career.job());
            assertTrue(Weapon.standard().stream().map(Enum::name).anyMatch(weapon::equals), career.job());
            if (career.job().equals(JobCatalog.SWORDMASTER)) assertEquals("LIGHTSABER", weapon);
            else assertEquals(SkillBalance.defaultWeapon(career.job()), weapon, career.job());
            String model = "common_" + weapon.toLowerCase(java.util.Locale.ROOT);
            assertTrue(EquipmentVisualItems.weaponModels().stream().anyMatch(entry -> entry.id().equals(model)),
                career.job() + " 缺少冠军武器外观：" + model);
            String base = JobCatalog.base(career.job());
            Set<String> expected = SkillCatalog.all().stream()
                .filter(def -> !def.passive() && !def.equipmentGranted && !def.bindingProxy && !def.automaticByJob)
                .filter(def -> def.advanced() ? def.requiredJob.equals(career.job()) : def.baseJob.equals(base))
                .map(def -> def.icon()).collect(Collectors.toSet());
            Set<String> actual = AbyssChampionKits.kits(career.job()).stream()
                .map(Kit::id).collect(Collectors.toSet());
            assertEquals(expected, actual, career.job());
        }
    }

    @Test
    void kitsStaySyncedWithTheCatalogAndReleaseInstantly() {
        for (AbyssCareers.Career career : AbyssCareers.ALL) {
            for (Kit kit : AbyssChampionKits.kits(career.job())) {
                SkillDefinition def = SkillCatalog.require(NS + kit.id());
                assertEquals(def.title, kit.title(), kit.id());
                assertEquals(def.id, kit.kind(), kit.id());
                assertEquals(def.coefficient, kit.coefficient(), 0, kit.id());
                assertEquals(0, kit.color() & 0xFF000000, kit.id());
                assertEquals(kit.id().equals("paradox") ? 90000 : Math.round(def.cooldown * 1000), kit.cooldown(), kit.id());
                assertTrue(kit.reach() > 0 && kit.enhance() >= 1.5 && kit.enhance() <= 2.5, kit.id());
                assertEquals(0, kit.cues().getFirst().delay(), kit.id());
                assertEquals(1, kit.cues().stream().mapToDouble(Cue::share).sum(), 1e-9, kit.id());
                assertTrue(kit.cues().stream().allMatch(cue -> cue.range() > 0), kit.id());
                if (kit.form() != Form.NONE) assertTrue(kit.formTime() > 0, kit.id());
            }
        }
    }

    @Test
    void releaseConditionsMatchCatalogAwakeningAndHeartSemantics() {
        for (AbyssCareers.Career career : AbyssCareers.ALL) {
            List<Kit> kits = AbyssChampionKits.kits(career.job());
            String awakening = JobCatalog.style(career.job()).awakeningSkill();
            List<String> gated = kits.stream().filter(k -> k.condition() == Condition.AWAKENING).map(Kit::id).toList();
            assertEquals(List.of(awakening.substring(awakening.indexOf(':') + 1)), gated, career.job());
            if (career.job().equals(JobCatalog.SUMMONER)) {
                assertEquals(List.of("summon_frenzy"),
                    kits.stream().filter(k -> k.condition() == Condition.SUMMON_ACTIVE).map(Kit::id).toList());
                assertTrue(kits.stream().anyMatch(k -> k.id().equals("kasijas") && k.condition() == Condition.AWAKENING));
            } else assertTrue(kits.stream().noneMatch(k -> k.condition() == Condition.SUMMON_ACTIVE), career.job());
            if (career.job().equals(JobCatalog.SUN_MOON)) {
                assertEquals(List.of("corona"), kits.stream().filter(k -> k.condition() == Condition.SUN_HEART).map(Kit::id).toList());
                assertEquals(List.of("moon_starfall"), kits.stream().filter(k -> k.condition() == Condition.MOON_HEART).map(Kit::id).toList());
            } else assertTrue(kits.stream().noneMatch(k -> k.condition() == Condition.SUN_HEART || k.condition() == Condition.MOON_HEART), career.job());
            for (Kit kit : kits)
                if (kit.motion() == Motion.DASH)
                    assertTrue(kit.cues().stream().anyMatch(cue -> cue.shape() == AbyssAttack.Shape.LINE), kit.id());
        }
    }
}
