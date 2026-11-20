package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind;
import cn.yumis.fight.modules.dungeons.api.DarkCityEnemyKind;
import cn.yumis.fight.modules.dungeons.api.ForbiddenEnemyKind;
import cn.yumis.fight.modules.dungeons.api.GblEnemyKind;
import cn.yumis.fight.modules.dungeons.api.PalaceEnemyKind;
import cn.yumis.fight.modules.dungeons.api.SkasaEnemyKind;

import java.util.*;

/** Ordinary small mobs borrowed from every story dungeon for the plain practice room. */
public final class PracticeMobs {
    public record Entry(String enemy, String dungeon) {}

    private static final Map<String, List<String>> DUNGEONS = createDungeons();
    private static final List<Map.Entry<String, List<String>>> POOLS = List.copyOf(DUNGEONS.entrySet());

    private PracticeMobs() {}

    /** One random dungeon, then one random ordinary kind from that dungeon. */
    public static Entry pick(Random random) {
        var pool = POOLS.get(random.nextInt(POOLS.size()));
        return new Entry(pool.getValue().get(random.nextInt(pool.getValue().size())), pool.getKey());
    }

    /** The dungeon a plain practice mob was borrowed from; only registered pool kinds are accepted. */
    public static String origin(String enemy) {
        for (var pool : POOLS) if (pool.getValue().contains(enemy)) return pool.getKey();
        throw new IllegalArgumentException("练功房未登记该小怪：" + enemy);
    }

    public static Map<String, List<String>> pools() { return DUNGEONS; }

    private static Map<String, List<String>> createDungeons() {
        var dungeons = new LinkedHashMap<String, List<String>>();
        dungeons.put("goblin_camp", List.of("goblin", "goblin_thrower", "goblin_captain"));
        dungeons.put("dragon_tower", List.of("dragonman", "blue_dragonman"));
        dungeons.put("castle_palace", List.of(PalaceEnemyKind.EXPELLER.id, PalaceEnemyKind.AXE_EXPELLER.id,
            PalaceEnemyKind.COMMANDER.id, PalaceEnemyKind.GOLEM.id));
        dungeons.put("gbl_church", List.of(GblEnemyKind.CULTIST.id, GblEnemyKind.FEMALE_CULTIST.id, GblEnemyKind.YASHA.id));
        dungeons.put("behemoth_forbidden", List.of(ForbiddenEnemyKind.CULTIST.id, ForbiddenEnemyKind.FEMALE_CULTIST.id,
            ForbiddenEnemyKind.DEACON.id, ForbiddenEnemyKind.PRIEST.id));
        dungeons.put("dark_city", List.of(DarkCityEnemyKind.HEADLESS_GRAVEROBBER.id, DarkCityEnemyKind.HEADLESS_SKELETON.id,
            DarkCityEnemyKind.GUARD.id, DarkCityEnemyKind.GOLIATH.id));
        dungeons.put("skasa", List.of(SkasaEnemyKind.MALE_WARRIOR.id, SkasaEnemyKind.FEMALE_WARRIOR.id,
            SkasaEnemyKind.ICE_SPIDER.id, SkasaEnemyKind.HATCHLING.id));
        dungeons.put(MoonlightTavern.ID, ordinary(MoonlightTavern.WAVES));
        dungeons.put(Hamelin.ID, ordinary(Hamelin.WAVES));
        dungeons.put(PursuitAnnihilation.ID, ordinary(PursuitAnnihilation.WAVES));
        dungeons.put(SeaTrain.ID, ordinary(SeaTrain.WAVES));
        dungeons.put(TimeGate.ID, ordinary(TimeGate.WAVES));
        dungeons.put(MysteriousAwareness.ID, ordinary(MysteriousAwareness.WAVES));
        dungeons.put(Grandine.ID, ordinary(Grandine.WAVES));
        dungeons.put(WitchForest.ID, ordinary(WitchForest.WAVES));
        return Collections.unmodifiableMap(dungeons);
    }

    /** Ordinary pack members only: wave leads, bosses and stationary mechanisms stay out. */
    private static List<String> ordinary(ClassicEnemyKind[][] waves) {
        var kinds = new LinkedHashSet<String>();
        for (var pack : waves)
            for (int i = 1; i < pack.length; i++)
                if (!pack[i].stationary) kinds.add(pack[i].id);
        return List.copyOf(kinds);
    }
}
