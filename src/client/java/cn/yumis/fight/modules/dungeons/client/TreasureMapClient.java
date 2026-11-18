package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.TreasureMapItem;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

final class TreasureMapClient {
    private static final List<String> DIRECTIONS = List.of("北", "东北", "东", "东南", "南", "西南", "西", "西北");
    private static final double SECTOR_ANGLE = Math.PI / 4;

    private TreasureMapClient() {}

    static void receiveClue(JsonObject clue) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.level().dimension() != Level.OVERWORLD) return;

        double dx = clue.get("x").getAsInt() + .5 - player.getX();
        double dz = clue.get("z").getAsInt() + .5 - player.getZ();
        ClientUi.current().message(clueText(dx, dz, clue.get("newlyOpened").getAsBoolean()));
    }

    static String clueText(double dx, double dz, boolean newlyOpened) {
        double distance = Math.hypot(dx, dz);
        if (distance == 0) return "藏宝地点很近，就在脚下。再次右键可挖掘。";

        int sector = (int) Math.floor(Math.atan2(dx, -dz) / SECTOR_ANGLE + .5);
        String direction = DIRECTIONS.get(Math.floorMod(sector, DIRECTIONS.size()));
        String text = "藏宝地点在" + direction + "方向" + distanceText(distance) + "的地方。";
        if (distance <= TreasureMapItem.DIG_RADIUS) return text + "再次右键可挖掘。";
        if (newlyOpened) return text + "到水平" + TreasureMapItem.DIG_RADIUS + "格内再次右键可挖掘。";
        return text;
    }

    private static String distanceText(double distance) {
        if (distance > 500) return "很远";
        if (distance >= 100) return "稍远";
        if (distance >= 30) return "稍近";
        return "很近";
    }
}
