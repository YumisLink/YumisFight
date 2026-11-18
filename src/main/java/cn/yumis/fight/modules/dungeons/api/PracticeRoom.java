package cn.yumis.fight.modules.dungeons.api;

import java.util.List;

/** The reward and entry definition captured when a public practice room opens. */
public record PracticeRoom(String id, String name, Kind kind, int level, List<String> materials) {
    public enum Kind { GOLD, CURRENCY, MATERIAL }

    public static final int WAVE_SIZE = 12;
    public static final PracticeRoom GOLD = new PracticeRoom("practice_gold", "金币练功房", Kind.GOLD, 1, List.of());
    public static final PracticeRoom CURRENCY = new PracticeRoom("practice_currency", "通货练功房", Kind.CURRENCY, 1, List.of());
    private static final String MATERIAL_PREFIX = "practice_material_";

    public PracticeRoom {
        materials = List.copyOf(materials);
    }

    public static String materialId(String dungeon) {
        return MATERIAL_PREFIX + dungeon;
    }

    public static boolean isRoomId(String id) {
        return GOLD.id.equals(id) || CURRENCY.id.equals(id) || id.startsWith(MATERIAL_PREFIX);
    }

    public static PracticeRoom require(String id) {
        if (GOLD.id.equals(id)) return GOLD;
        if (CURRENCY.id.equals(id)) return CURRENCY;
        if (!id.startsWith(MATERIAL_PREFIX)) throw new IllegalArgumentException("未知练功房");
        var dungeon = DungeonCatalog.current().dungeon(id.substring(MATERIAL_PREFIX.length()));
        if (!dungeon.available()) throw new IllegalArgumentException("该副本尚未开放");
        return material(dungeon);
    }

    public static List<PracticeRoom> availableMaterials(int playerLevel) {
        return DungeonCatalog.current().dungeons.stream()
            .filter(dungeon -> dungeon.available() && playerLevel >= dungeon.level())
            .map(PracticeRoom::material)
            .toList();
    }

    private static PracticeRoom material(DungeonCatalog.DungeonDef dungeon) {
        return new PracticeRoom(materialId(dungeon.id()), dungeon.name(), Kind.MATERIAL, dungeon.level(), dungeon.materials());
    }
}
