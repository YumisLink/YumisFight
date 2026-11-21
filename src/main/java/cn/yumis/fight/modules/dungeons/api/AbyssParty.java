package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.dungeons.content.CastlePalace;
import cn.yumis.fight.modules.dungeons.content.DragonTower;
import cn.yumis.fight.platform.ItemData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shared encounter parameters, including the boundary used by enemy AI and local contacts. */
public final class AbyssParty {
    public static final String INVITATION = "abyss_invitation";
    private static final Identifier INVITATION_ID = Identifier.fromNamespaceAndPath(ItemData.NS, INVITATION);
    public static final double CHANCE = .01;
    public static final double FIRST_FLOOR = .67, SECOND_FLOOR = .34;
    public static final List<Double> SKELETON_THRESHOLDS = List.of(.95, .90, .85, .80, .75, .70);
    public static final int SECOND_WAVE = 8, CHAMPIONS = 5;
    public static final double RADIUS = 24;
    private static final Set<String> DUNGEONS = Set.of("goblin_camp", "dragon_tower", "castle_palace", "gbl_church");

    public enum Enemy {
        PILLAR("abyss_pillar", 3, 1.15f, 3.2f),
        SKELETON("abyss_skeleton", .6, .65f, 1.95f),
        GIANT("abyss_steel_pipe", 1.8, 1.35f, 3.6f),
        CHAMPION("abyss_champion", .6, .7f, 1.95f);

        public final String id;
        public final double healthMultiplier;
        public final float width, height;

        Enemy(String id, double healthMultiplier, float width, float height) {
            this.id = id;
            this.healthMultiplier = healthMultiplier;
            this.width = width;
            this.height = height;
        }

        public static Enemy find(String id) {
            for (Enemy kind : values()) if (kind.id.equals(id)) return kind;
            return null;
        }
    }

    public record Arena(String dungeon, int cell, Vec3 anchor, int sector) {
        public static Arena at(Dungeon dungeon, Vec3 anchor) {
            int sector = switch (dungeon.dungeon) {
                case "castle_palace" -> CastlePalace.roomAt(dungeon.cell, anchor);
                case "dragon_tower" -> (int) Math.round((anchor.y - 80) / DragonTower.FLOOR_HEIGHT);
                case "gbl_church" -> churchSector(anchor.z);
                default -> 0;
            };
            return new Arena(dungeon.dungeon, dungeon.cell, anchor, sector);
        }

        public boolean contains(Vec3 point) {
            if (Math.abs(point.y - anchor.y) > 5 || point.subtract(anchor).horizontalDistanceSqr() > RADIUS * RADIUS) return false;
            return switch (dungeon) {
                case "castle_palace" -> CastlePalace.roomAt(cell, point) == sector;
                case "gbl_church" -> churchSector(point.z) == sector;
                default -> true;
            };
        }

        private static int churchSector(double z) {
            if (z < 35) return 0;
            if (z < 80) return 1;
            if (z < 125) return 2;
            return z < 179 ? 3 : 4;
        }
    }

    public record Activation(Vec3 replacement, Arena arena, UUID replacedEnemy) {}

    public static boolean invitation(ItemStack stack) {
        return !stack.isEmpty() && INVITATION_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean supports(String dungeonId) {
        return DUNGEONS.contains(dungeonId);
    }

    public static boolean eligible(Dungeon dungeon) {
        return dungeon.treasure == null && dungeon.practice == null && supports(dungeon.dungeon);
    }

    private AbyssParty() {}
}
