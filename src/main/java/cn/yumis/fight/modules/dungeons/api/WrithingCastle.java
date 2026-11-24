package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Public contract for the Writhing Castle special-entry dungeon (200-layer sky-sea megastructure). */
public final class WrithingCastle {
    public static final String ID = "writhing_castle";
    public static final String NAME = "蠕动之城";
    public static final int LEVEL = 80;
    public static final int MIN_X = -192, MAX_X = 191;
    public static final int MIN_Y = -16, MAX_Y = 317;
    public static final int MIN_Z = -192, MAX_Z = 191;
    /** Shared dungeon dimension cell baseline Y; the castle maps 1:1 to absolute Y (-16..317). */
    public static final int CELL_ORIGIN_Y = 80;
    public static final double SPAWN_X = 0.5, SPAWN_Y = 45.0, SPAWN_Z = 168.5;
    public static final float SPAWN_YAW = 180f, SPAWN_PITCH = -18f;
    public static final String SPECIAL_ENTRY_MESSAGE = "蠕动之城为特殊进入副本，暂不可从副本列表进入";

    /** Non-catalog dungeon metadata; available=false prevents standard menu and waystone entry. */
    public static final DungeonCatalog.DungeonDef DUNGEON =
        new DungeonCatalog.DungeonDef(ID, NAME, LEVEL, false, -1, List.of());

    public record Sanctuary(int layer, String name, String district, int x, int y, int z) {
        public Vec3 worldPoint(int cell) {
            return WrithingCastle.worldPoint(cell, x + 0.5, y, z + 0.5);
        }
    }

    public static final List<Sanctuary> SANCTUARIES = List.of(
        new Sanctuary(35, "卡米纳伊处刑圣庭", "龙人处刑聚落", 0, 89, 28),
        new Sanctuary(70, "四色共生魔偶大戏台", "四色魔偶工坊町", 0, 133, 28),
        new Sanctuary(105, "赤瞳真身镜像雷池殿", "赤瞳幻影水渠窟", 0, 177, 28),
        new Sanctuary(140, "十秒同生复苏圣堂", "三圣誓约卫戍区", 0, 221, 28),
        new Sanctuary(175, "莫格尼斯双极电磁中枢殿", "双极失重观测区", 0, 265, 28),
        new Sanctuary(200, "贝里斯克石化魔眼天宫", "魔眼石化天宫", 0, 297, 28)
    );

    private WrithingCastle() {}

    public static boolean handles(Dungeon dungeon) {
        return dungeon != null && dungeon.practice == null && dungeon.treasure == null
            && dungeon.waystone == null && ID.equals(dungeon.dungeon);
    }

    public static Vec3 worldPoint(int cell, double x, double y, double z) {
        return new Vec3(cell * 512 + x, y, z);
    }

    public static Vec3 entry(int cell) {
        return worldPoint(cell, SPAWN_X, SPAWN_Y, SPAWN_Z);
    }

    public static boolean contains(int cell, Vec3 point) {
        double x = point.x - cell * 512;
        return x >= MIN_X && x < MAX_X + 1
            && point.y >= MIN_Y && point.y <= MAX_Y + 2
            && point.z >= MIN_Z && point.z < MAX_Z + 1;
    }

    /** Human-readable district label from cell-local coordinates for HUD and menu display. */
    public static String area(double localX, double y, double localZ) {
        if (y < 44) return "次元深渊裂谷";
        if (y < 48 && Math.abs(localX) <= 24 && localZ >= 134) return "格兰裂谷入口";
        if (y <= 88) return "龙人处刑聚落（1～35层）";
        if (y <= 132) return "四色魔偶工坊町（36～70层）";
        if (y <= 176) return "赤瞳幻影水渠窟（71～105层）";
        if (y <= 220) return "三圣誓约卫戍区（106～140层）";
        if (y <= 264) return "双极失重观测区（141～175层）";
        return "魔眼石化天宫（176～200层）";
    }
}
