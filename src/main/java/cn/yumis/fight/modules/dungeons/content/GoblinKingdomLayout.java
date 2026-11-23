package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.platform.ItemData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** The authored Goblin Kingdom terrain, aligned to the dungeon entrance road. */
final class GoblinKingdomLayout {
    static final String ID = "goblin_kingdom";
    private static final String VERSION = "GOBLIN_KINGDOM_LAYOUT_1";
    private static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(ItemData.NS, "dungeons/" + ID + "/map");
    private static final BlockPos OFFSET = new BlockPos(-104, -5, -20);
    private static final Vec3i SIZE = new Vec3i(210, 52, 230);
    private static final DungeonLayouts.Bounds BOUNDS = new DungeonLayouts.Bounds(
        OFFSET.getX(), OFFSET.getY(), OFFSET.getZ(),
        OFFSET.getX() + SIZE.getX() - 1, OFFSET.getY() + SIZE.getY() - 1, OFFSET.getZ() + SIZE.getZ() - 1
    );

    private GoblinKingdomLayout() {}

    static DungeonLayouts.Layout layout() {
        return new DungeonLayouts.Layout(VERSION, BOUNDS, 0, 1, GoblinKingdomLayout::build, (world, origin, gate, open) -> {});
    }

    private static void build(ServerLevel world, BlockPos origin) {
        var template = world.getStructureManager().get(TEMPLATE)
            .orElseThrow(() -> new IllegalStateException("缺少哥布林王国地图结构：" + TEMPLATE));
        if (!template.getSize().equals(SIZE)) {
            throw new IllegalStateException("哥布林王国地图结构尺寸应为 " + SIZE + "，实际为 " + template.getSize());
        }
        var corner = origin.offset(OFFSET);
        var settings = new StructurePlaceSettings().setKnownShape(true).setIgnoreEntities(true);
        if (!template.placeInWorld(world, corner, corner, settings, world.getRandom(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS)) {
            throw new IllegalStateException("无法放置哥布林王国地图结构：" + TEMPLATE);
        }
    }
}
