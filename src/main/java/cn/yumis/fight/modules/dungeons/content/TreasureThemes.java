package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.*;
import java.util.*;

/**
 * Central catalog of the 6 Lv.10-35 dungeon themes for Treasure Hunt Regions 0-4 plus Region 5 Golden Palace.
 */
public final class TreasureThemes {
    public static final String PALACE_THEME = "palace";
    public static final int OVERWORLD_GUARD_COUNT = 6;
    public static final int SECRET_WAVE_SIZE = 12;

    public record Theme(
        String id,
        String dungeonId,
        String displayName,
        int topColor,
        int bottomColor,
        int accentColor,
        List<String> overworldGuards,
        List<String> wavePool,
        List<String> eliteWave,
        String bossId
    ) {}

    public static final Theme FOREST = new Theme(
        "forest", "goblin_camp", "格兰之森 · 哥布林营地",
        0xe6ffc9, 0x8ec467, 0x76c74a,
        List.of("goblin", "goblin", "goblin", "goblin_thrower", "goblin_thrower", "goblin_captain"),
        List.of("goblin", "goblin", "goblin_thrower", "goblin_captain"),
        List.of("goblin_captain", "goblin_captain", "goblin_thrower", "goblin_thrower", "goblin", "goblin"),
        "goblin_shaman"
    );

    public static final Theme TOWER = new Theme(
        "tower", "dragon_tower", "天空之城 · 龙人之塔",
        0xffeac8, 0xb488e6, 0xc792f2,
        List.of("dragonman", "dragonman", "wyvern", "wyvern", "blue_dragonman", "blue_wyvern"),
        List.of("dragonman", "wyvern", "blue_dragonman", "blue_wyvern"),
        List.of("blue_dragonman", "blue_wyvern", "blue_dragonman", "dragonman", "wyvern", "wyvern"),
        "lucas"
    );

    public static final Theme PALACE_SKY = new Theme(
        "palace_sky", "castle_palace", "天空之城 · 城主宫殿",
        0xd5f9ff, 0x5ca7d9, 0x6cd0ff,
        List.of(
            PalaceEnemyKind.EXPELLER.id, PalaceEnemyKind.EXPELLER.id,
            PalaceEnemyKind.AXE_EXPELLER.id, PalaceEnemyKind.COMMANDER.id,
            PalaceEnemyKind.GOLEM.id, PalaceEnemyKind.AXION.id
        ),
        List.of(
            PalaceEnemyKind.EXPELLER.id, PalaceEnemyKind.AXE_EXPELLER.id,
            PalaceEnemyKind.COMMANDER.id, PalaceEnemyKind.GOLEM.id
        ),
        List.of(
            PalaceEnemyKind.AXION.id, PalaceEnemyKind.IAE.id,
            PalaceEnemyKind.AXE_EXPELLER.id, PalaceEnemyKind.GOLEM.id,
            PalaceEnemyKind.EXPELLER.id, PalaceEnemyKind.COMMANDER.id
        ),
        PalaceEnemyKind.SIEGHART.id
    );

    public static final Theme GBL = new Theme(
        "gbl", "gbl_church", "天帷巨兽 · GBL教会",
        0xf1e4ff, 0xad8bd7, 0xb588f0,
        List.of(
            GblEnemyKind.CULTIST.id, GblEnemyKind.CULTIST.id,
            GblEnemyKind.FEMALE_CULTIST.id, GblEnemyKind.YASHA.id,
            GblEnemyKind.YASHA.id, GblEnemyKind.FALLEN_PRIEST.id
        ),
        List.of(
            GblEnemyKind.CULTIST.id, GblEnemyKind.FEMALE_CULTIST.id,
            GblEnemyKind.YASHA.id, GblEnemyKind.DRAGON_CANNON.id,
            GblEnemyKind.FALLEN_PRIEST.id
        ),
        List.of(
            GblEnemyKind.FALLEN_PRIEST.id, GblEnemyKind.FALLEN_PRIEST.id,
            GblEnemyKind.YASHA.id, GblEnemyKind.YASHA.id,
            GblEnemyKind.DRAGON_CANNON.id, GblEnemyKind.FEMALE_CULTIST.id
        ),
        GblEnemyKind.ASOR.id
    );

    public static final Theme FORBIDDEN = new Theme(
        "forbidden", "behemoth_forbidden", "天帷巨兽 · 天帷禁地",
        0xfcd6e1, 0xbf6f8a, 0xe06c96,
        List.of(
            ForbiddenEnemyKind.CULTIST.id, ForbiddenEnemyKind.FEMALE_CULTIST.id,
            ForbiddenEnemyKind.DEACON.id, ForbiddenEnemyKind.DEACON.id,
            ForbiddenEnemyKind.PRIEST.id, ForbiddenEnemyKind.BISHOP.id
        ),
        List.of(
            ForbiddenEnemyKind.CULTIST.id, ForbiddenEnemyKind.FEMALE_CULTIST.id,
            ForbiddenEnemyKind.DEACON.id, ForbiddenEnemyKind.PRIEST.id,
            ForbiddenEnemyKind.BISHOP.id
        ),
        List.of(
            ForbiddenEnemyKind.BISHOP.id, ForbiddenEnemyKind.BISHOP.id,
            ForbiddenEnemyKind.PRIEST.id, ForbiddenEnemyKind.PRIEST.id,
            ForbiddenEnemyKind.DEACON.id, ForbiddenEnemyKind.FEMALE_CULTIST.id
        ),
        ForbiddenEnemyKind.MARCELLUS.id
    );

    public static final Theme DARK_CITY = new Theme(
        "dark_city", "dark_city", "暗黑城 · 暗黑城入口",
        0xe4deff, 0x745bc8, 0x8d72e8,
        List.of(
            DarkCityEnemyKind.HEADLESS_GRAVEROBBER.id, DarkCityEnemyKind.HEADLESS_GRAVEROBBER.id,
            DarkCityEnemyKind.HEADLESS_SKELETON.id, DarkCityEnemyKind.GUARD.id,
            DarkCityEnemyKind.GOLIATH.id, DarkCityEnemyKind.TINGO.id
        ),
        List.of(
            DarkCityEnemyKind.HEADLESS_GRAVEROBBER.id, DarkCityEnemyKind.HEADLESS_SKELETON.id,
            DarkCityEnemyKind.GUARD.id, DarkCityEnemyKind.GOLIATH.id
        ),
        List.of(
            DarkCityEnemyKind.TINGO.id, DarkCityEnemyKind.HUMPHREY.id,
            DarkCityEnemyKind.GOLIATH.id, DarkCityEnemyKind.GUARD.id,
            DarkCityEnemyKind.HEADLESS_SKELETON.id, DarkCityEnemyKind.HEADLESS_GRAVEROBBER.id
        ),
        DarkCityEnemyKind.HEADLESS_KNIGHT.id
    );

    public static final List<Theme> ROTATION = List.of(FOREST, TOWER, PALACE_SKY, GBL, FORBIDDEN, DARK_CITY);
    private static final Map<String, Theme> BY_ID;
    static {
        var map = new LinkedHashMap<String, Theme>();
        for (Theme t : ROTATION) map.put(t.id(), t);
        BY_ID = Map.copyOf(map);
    }

    private TreasureThemes() {}

    public static Theme byId(String id) {
        return BY_ID.getOrDefault(id, FOREST);
    }

    public static boolean isRotationTheme(String id) {
        return BY_ID.containsKey(id);
    }

    public static String displayName(String themeId) {
        if (PALACE_THEME.equals(themeId)) return "黄金王宫";
        return byId(themeId).displayName();
    }

    public static int accentColor(String themeId) {
        if (PALACE_THEME.equals(themeId)) return 0xf5bd5e;
        return byId(themeId).accentColor();
    }

    /** Pick a random theme, strongly preferring themes not yet visited in this run. */
    public static Theme pickRandom(Collection<String> visited, String exclude, Random random) {
        var fresh = new ArrayList<Theme>();
        var fallback = new ArrayList<Theme>();
        for (Theme t : ROTATION) {
            if (t.id().equals(exclude)) continue;
            fallback.add(t);
            if (visited == null || !visited.contains(t.id())) fresh.add(t);
        }
        var pool = fresh.isEmpty() ? fallback : fresh;
        return pool.get(random.nextInt(pool.size()));
    }

    /** Pick two distinct portal preview themes for West & East doors. */
    public static String[] pickPortalPair(int nextRegion, Collection<String> visited, String currentTheme, Random random) {
        if (nextRegion >= 5) return new String[]{PALACE_THEME, PALACE_THEME};
        Theme west = pickRandom(visited, currentTheme, random);
        var exclude = new HashSet<String>(visited == null ? Set.of() : visited);
        exclude.add(west.id());
        Theme east = pickRandom(exclude, west.id(), random);
        if (east.id().equals(west.id())) {
            for (Theme t : ROTATION) if (!t.id().equals(west.id())) { east = t; break; }
        }
        return new String[]{west.id(), east.id()};
    }

    /** Region difficulty ramp: R0=WARRIOR, R1=WARRIOR, R2=KING, R3=HELL, R4=HELL+1, R5=HELL+2. */
    public static Difficulty difficultyForRegion(int region) {
        return switch (region) {
            case 0, 1 -> Difficulty.WARRIOR;
            case 2 -> Difficulty.KING;
            default -> Difficulty.HELL;
        };
    }

    /** Waystone bonus tier for Hell+1 (region 4) and Hell+2 (region 5). */
    public static int hellPlusTier(int region) {
        return region >= 4 ? region - 3 : 0;
    }

    public static WaystoneRun.MonsterModifiers hellPlusModifiers(int region) {
        int tier = hellPlusTier(region);
        if (tier <= 0) return null;
        return new WaystoneRun.MonsterModifiers(tier, WaystoneRules.current().statGrowth, List.of());
    }

    /** Portal pass probability by current cleared region (1->2: 75%, 2->3: 65%, 3->4: 55%, 4->5: 50%). */
    public static double portalPassChance(int clearedRegion) {
        return switch (clearedRegion) {
            case 1 -> 0.75;
            case 2 -> 0.65;
            case 3 -> 0.55;
            default -> 0.50;
        };
    }

    /** Twin Dice base trigger probability on region clear (R3: 20%, R4: 15%, R5: 10%). */
    public static double diceTriggerChance(int clearedRegion) {
        return switch (clearedRegion) {
            case 3 -> 0.20;
            case 4 -> 0.15;
            case 5 -> 0.10;
            default -> 0.0;
        };
    }

    /** Build a 12-mob wave from the theme's ordinary enemy pool (2 elites mixed into the 12). */
    public static List<String> ordinaryWave(Theme theme, int waveIndex, Random random) {
        var list = new ArrayList<String>(SECRET_WAVE_SIZE);
        var pool = theme.wavePool();
        int size = pool.size();
        for (int i = 0; i < SECRET_WAVE_SIZE; i++) {
            int idx = (i + waveIndex + random.nextInt(size)) % size;
            list.add(pool.get(idx));
        }
        return list;
    }
}
