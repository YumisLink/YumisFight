package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.AwakeningTower;
import cn.yumis.fight.modules.dungeons.api.ForbiddenEnemyKind;
import cn.yumis.fight.modules.dungeons.api.GblEnemyKind;
import cn.yumis.fight.modules.dungeons.api.SkasaEnemyKind;
import cn.yumis.fight.modules.dungeons.api.TowerClimbRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;

/** Formations and portable bosses used by the tower encounters. */
public final class TowerClimbEncounters {
    private static final List<String> BOSSES = List.of("lucas", "goblin_shaman",
        MOONLIGHT_TAVERN_CHANG_SANLANG.id, TIME_RIFT_YOUNG_SKASA.id, SEA_TRAIN_BODIR.id,
        GRANDI_FURMAN.id, HAMELIN_PETER.id, MYSTERIOUS_AWARENESS_IRIS.id);

    private record Group(String enemy, int count) {}
    private record Formation(List<Group> base, List<String> reinforcements) {}

    private static final Map<AwakeningTower, List<Formation>> FORMATIONS = Map.of(
        AwakeningTower.ROOT, List.of(
            formation(List.of(group(CENTRAL_PARK_LUKA_GUARD.id, 10), group(CENTRAL_PARK_GIZA_GUARD.id, 8),
                group(CENTRAL_PARK_ROTTING_WOODCUTTER.id, 4), group(CENTRAL_PARK_CHAMCHAM.id, 2)),
                CENTRAL_PARK_CHAMCHAM.id, CENTRAL_PARK_LALOF_BLUE.id, CENTRAL_PARK_ROTTING_WOODCUTTER.id),
            formation(List.of(group(CENTRAL_PARK_LUKA_GUARD.id, 8), group(CENTRAL_PARK_GIZA_GUARD.id, 6),
                group(CENTRAL_PARK_ROTTING_WOODCUTTER.id, 6), group(CENTRAL_PARK_CHAMCHAM.id, 4)),
                CENTRAL_PARK_LUKA_GUARD.id, CENTRAL_PARK_CHAMCHAM.id, CENTRAL_PARK_LALOF_BLUE.id)
        ),
        AwakeningTower.FORGE, List.of(
            formation(List.of(group(GRANDI_ASSAULT_GRANITE.id, 12), group(GRANDI_MAGMA_OCTOPUS.id, 6),
                group(GRANDI_LAVA_SPIDER.id, 4), group(GRANDI_FLYING_GRANITE.id, 2)),
                GRANDI_LAVA_SPIDER.id, GRANDI_MAGMA_OCTOPUS.id, GRANDI_MAGMA_SLIME.id),
            formation(List.of(group(GRANDI_ASSAULT_GRANITE.id, 8), group(GRANDI_LAVA_SPIDER.id, 8),
                group(GRANDI_MAGMA_OCTOPUS.id, 6), group(GRANDI_MAGMA_SLIME.id, 2)),
                GRANDI_FLYING_GRANITE.id, GRANDI_ASSAULT_GRANITE.id, GRANDI_MAGMA_SLIME.id)
        ),
        AwakeningTower.SLUICE, List.of(
            formation(List.of(group(SEA_TRAIN_CROC_BITER.id, 12), group(SEA_TRAIN_CROC_SPEARMAN.id, 6),
                group(SEA_TRAIN_TURTLE.id, 4), group(SEA_TRAIN_TURTLE_GUNNER.id, 2)),
                SEA_TRAIN_CROC_SPEARMAN.id, SEA_TRAIN_TURTLE_SPINNER.id, SEA_TRAIN_MERMAID_MAGE.id),
            formation(List.of(group(SEA_TRAIN_CROC_BITER.id, 10), group(SEA_TRAIN_TURTLE.id, 8),
                group(SEA_TRAIN_TURTLE_SPINNER.id, 4), group(SEA_TRAIN_TURTLE_GUNNER.id, 2)),
                SEA_TRAIN_CROC_SPEARMAN.id, SEA_TRAIN_MERMAID_MAGE.id, SEA_TRAIN_MERMAN_SHAMAN.id)
        ),
        AwakeningTower.SAND_MARKET, List.of(
            formation(List.of(group(MOONLIGHT_TAVERN_CITY_THUG.id, 10), group(MOONLIGHT_TAVERN_STREET_HOOLIGAN.id, 8),
                group(MOONLIGHT_TAVERN_DRUNKARD.id, 4), group(HAMELIN_MAD_FEMALE_THIEF.id, 2)),
                MOONLIGHT_TAVERN_PICKPOCKET.id, HAMELIN_MAD_FEMALE_THIEF.id, MOONLIGHT_TAVERN_POISON_NIGHTBLADE.id),
            formation(List.of(group(MOONLIGHT_TAVERN_CITY_THUG.id, 8), group(MOONLIGHT_TAVERN_STREET_HOOLIGAN.id, 6),
                group(MOONLIGHT_TAVERN_DRUNKARD.id, 6), group(MOONLIGHT_TAVERN_PICKPOCKET.id, 4)),
                HAMELIN_MAD_FEMALE_THIEF.id, MOONLIGHT_TAVERN_POISON_NIGHTBLADE.id, MOONLIGHT_TAVERN_PICKPOCKET.id)
        ),
        AwakeningTower.FROST, List.of(
            formation(List.of(group(SkasaEnemyKind.MALE_WARRIOR.id, 10), group(SkasaEnemyKind.FEMALE_WARRIOR.id, 8),
                group(SkasaEnemyKind.ICE_SPIDER.id, 4), group(SkasaEnemyKind.HATCHLING.id, 2)),
                SkasaEnemyKind.FEMALE_WARRIOR.id, SkasaEnemyKind.ICE_SPIDER.id, SkasaEnemyKind.HATCHLING.id),
            formation(List.of(group(SkasaEnemyKind.MALE_WARRIOR.id, 8), group(SkasaEnemyKind.FEMALE_WARRIOR.id, 10),
                group(SkasaEnemyKind.ICE_SPIDER.id, 2), group(SkasaEnemyKind.HATCHLING.id, 4)),
                SkasaEnemyKind.MALE_WARRIOR.id, SkasaEnemyKind.HATCHLING.id, SkasaEnemyKind.ICE_SPIDER.id)
        ),
        AwakeningTower.ORRERY, List.of(
            formation(List.of(group(MYSTERIOUS_AWARENESS_TIME_SOLDIER.id, 14), group(MYSTERIOUS_AWARENESS_SPACE_SOLDIER.id, 6),
                group(PURSUIT_MODIFIED_SOLDIER.id, 4)),
                MYSTERIOUS_AWARENESS_TIME_SOLDIER.id, MYSTERIOUS_AWARENESS_SPACE_SOLDIER.id, PURSUIT_SPARK_BETA.id),
            formation(List.of(group(MYSTERIOUS_AWARENESS_TIME_SOLDIER.id, 12), group(MYSTERIOUS_AWARENESS_SPACE_SOLDIER.id, 8),
                group(PURSUIT_SPARK_BETA.id, 4)),
                MYSTERIOUS_AWARENESS_TIME_SOLDIER.id, PURSUIT_MODIFIED_SOLDIER.id, PURSUIT_SPARK_BETA.id)
        ),
        AwakeningTower.SEAL, List.of(
            formation(List.of(group(GblEnemyKind.CULTIST.id, 12), group(ForbiddenEnemyKind.DEACON.id, 8),
                group(ForbiddenEnemyKind.FEMALE_CULTIST.id, 4)),
                ForbiddenEnemyKind.DEACON.id, GblEnemyKind.YASHA.id, ForbiddenEnemyKind.FEMALE_CULTIST.id),
            formation(List.of(group(GblEnemyKind.CULTIST.id, 10), group(ForbiddenEnemyKind.DEACON.id, 6),
                group(ForbiddenEnemyKind.FEMALE_CULTIST.id, 4), group(GblEnemyKind.YASHA.id, 4)),
                ForbiddenEnemyKind.DEACON.id, GblEnemyKind.YASHA.id, ForbiddenEnemyKind.FEMALE_CULTIST.id)
        ),
        AwakeningTower.THEATER, List.of(
            formation(List.of(group(MYSTERIOUS_AWARENESS_SWORD_PUPPET.id, 10), group(MYSTERIOUS_AWARENESS_AXE_PUPPET.id, 8),
                group(MYSTERIOUS_AWARENESS_WHIP_PUPPET.id, 4), group(MYSTERIOUS_AWARENESS_FIGHTER_PUPPET.id, 2)),
                MYSTERIOUS_AWARENESS_FIGHTER_PUPPET.id, MYSTERIOUS_AWARENESS_WHIP_PUPPET.id, MYSTERIOUS_AWARENESS_AXE_PUPPET.id),
            formation(List.of(group(MYSTERIOUS_AWARENESS_SWORD_PUPPET.id, 8), group(MYSTERIOUS_AWARENESS_AXE_PUPPET.id, 6),
                group(MYSTERIOUS_AWARENESS_WHIP_PUPPET.id, 4), group(MYSTERIOUS_AWARENESS_FIGHTER_PUPPET.id, 6)),
                MYSTERIOUS_AWARENESS_SWORD_PUPPET.id, MYSTERIOUS_AWARENESS_WHIP_PUPPET.id, MYSTERIOUS_AWARENESS_AXE_PUPPET.id)
        )
    );

    private TowerClimbEncounters() {}

    public static List<String> ordinary(AwakeningTower tower, int floor, RandomGenerator random) {
        var choices = FORMATIONS.get(tower);
        var formation = choices.get(random.nextInt(choices.size()));
        int stage = Math.min(formation.reinforcements().size(), Math.max(0, (floor - 1) / 5));
        var result = new ArrayList<String>(24 + stage * 2);
        for (Group group : formation.base()) {
            for (int index = 0; index < group.count(); index++) result.add(group.enemy());
        }
        for (String reinforcement : formation.reinforcements().subList(0, stage)) {
            result.add(reinforcement);
            result.add(reinforcement);
        }
        return result;
    }

    public static String boss(AwakeningTower tower, int floor, Set<String> clearedRandomBosses, RandomGenerator random) {
        if (tower == AwakeningTower.SEAL && floor == TowerClimbRules.FLOOR_COUNT) {
            return SealTowerElaina.ID;
        }
        if (tower == AwakeningTower.THEATER && floor == TowerClimbRules.FLOOR_COUNT) {
            return TheaterTowerLiangyue.ID;
        }
        if (tower == AwakeningTower.FROST && floor == TowerClimbRules.FLOOR_COUNT) {
            return FrostTowerFrostnova.ID;
        }
        if (tower == AwakeningTower.ORRERY && floor == TowerClimbRules.FLOOR_COUNT) {
            return OrreryTowerGilgamesh.ID;
        }
        if (tower == AwakeningTower.FORGE && floor == TowerClimbRules.FLOOR_COUNT) {
            return ForgeTowerSwordMasters.LEAD_ID;
        }
        if (tower == AwakeningTower.SLUICE && floor == TowerClimbRules.FLOOR_COUNT) {
            return SluiceTowerRoxy.ID;
        }
        var pool = TowerClimbRules.specialBossFloor(floor) ? BOSSES :
            BOSSES.stream().filter(id -> !clearedRandomBosses.contains(id)).toList();
        if (pool.isEmpty()) throw new IllegalStateException("觉醒塔随机首领池已用尽");
        return pool.get(random.nextInt(pool.size()));
    }

    private static Group group(String id, int count) {
        return new Group(id, count);
    }

    private static Formation formation(List<Group> base, String... reinforcements) {
        return new Formation(base, List.of(reinforcements));
    }
}
