package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.TreasureMapItem;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.platform.ItemData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

public final class TreasureMapClient {
    private static final List<String> DIRECTIONS = List.of("北", "东北", "东", "东南", "南", "西南", "西", "西北");
    private static final double SECTOR_ANGLE = Math.PI / 4;
    private static final DustParticleOptions GUIDE_SPARKLE = new DustParticleOptions(0xFFD858, 1.15f);

    public record ActiveTarget(int x, int z, double surfaceY, double distance, String direction) {}

    private static ActiveTarget currentTarget;
    private static int guideTick;
    private static int pulseCountdown;

    private TreasureMapClient() {}

    public static ActiveTarget activeTarget() {
        return currentTarget;
    }

    public static void clear() {
        currentTarget = null;
        guideTick = 0;
        pulseCountdown = 0;
    }

    static void receiveClue(JsonObject clue) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.level().dimension() != Level.OVERWORLD) return;

        double dx = clue.get("x").getAsInt() + .5 - player.getX();
        double dz = clue.get("z").getAsInt() + .5 - player.getZ();
        ClientUi.current().message(clueText(dx, dz, clue.get("newlyOpened").getAsBoolean()));
    }

    /** Client-only Overworld guidance: reads held map ItemData directly (zero extra server packets). */
    public static void tick() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null || mc.isPaused() || !ClientUi.current().ready()
            || level.dimension() != Level.OVERWORLD) {
            currentTarget = null;
            return;
        }
        JsonObject map = heldOpenedMap(player.getMainHandItem());
        if (map == null) map = heldOpenedMap(player.getOffhandItem());
        if (map == null || !map.has("x") || !map.has("z")) {
            currentTarget = null;
            pulseCountdown = 0;
            return;
        }
        int tx = map.get("x").getAsInt();
        int tz = map.get("z").getAsInt();
        double dx = tx + .5 - player.getX();
        double dz = tz + .5 - player.getZ();
        double dist = Math.hypot(dx, dz);
        String dir = directionOf(dx, dz);
        double surfaceY = dist <= 64
            ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz)
            : player.getY();
        currentTarget = new ActiveTarget(tx, tz, surfaceY, dist, dir);

        guideTick++;
        // 金色流光粒子：每隔 6 tick 沿水平指向目标撒出一串引导光尘
        if (guideTick % 6 == 0) {
            var rng = level.getRandom();
            if (dist > 1.5) {
                double nx = dx / dist, nz = dz / dist;
                for (int i = 1; i <= 3; i++) {
                    double step = 1.4 + i * 1.35;
                    double px = player.getX() + nx * step + (rng.nextDouble() - 0.5) * 0.25;
                    double py = player.getY() + 1.15 + i * 0.14 + (rng.nextDouble() - 0.5) * 0.15;
                    double pz = player.getZ() + nz * step + (rng.nextDouble() - 0.5) * 0.25;
                    level.addParticle(GUIDE_SPARKLE, px, py, pz, nx * 0.04, 0.02, nz * 0.04);
                }
            } else {
                for (int i = 0; i < 4; i++) {
                    double a = guideTick * 0.3 + i * Math.PI * 0.5;
                    level.addParticle(GUIDE_SPARKLE,
                        tx + .5 + Math.cos(a) * 0.85, player.getY() + 0.5 + i * 0.3, tz + .5 + Math.sin(a) * 0.85,
                        0, 0.05, 0);
                }
            }
        }

        // 近距冷热脉冲音效与底部轻提示（ActionBar）
        if (--pulseCountdown <= 0) {
            int interval;
            float pitch;
            int color;
            String bar;
            if (dist <= TreasureMapItem.DIG_RADIUS) {
                interval = 12;
                pitch = 1.65f;
                color = 0xFFE66D;
                bar = "✦ 已抵达宝藏点（" + Math.round(dist) + " 格）· 右键藏宝图即可挖掘！";
            } else if (dist <= 32) {
                interval = 18;
                pitch = 1.42f;
                color = 0xFFAE52;
                bar = "🔥 炽热 · 宝藏就在" + dir + "方向近处（约 " + Math.round(dist) + " 格）";
            } else if (dist <= 100) {
                interval = 30;
                pitch = 1.16f;
                color = 0xFFD97D;
                bar = "☀ 温热 · 宝藏在" + dir + "方向不远处";
            } else if (dist <= 300) {
                interval = 48;
                pitch = 0.95f;
                color = 0xBFE8FF;
                bar = "✦ 微温 · 朝" + dir + "方向继续寻宝";
            } else {
                interval = 70;
                pitch = 0.78f;
                color = 0x99C7F5;
                bar = "❄ 遥远 · 朝" + dir + "方向行进";
            }
            pulseCountdown = interval;
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, dist <= 32 ? 0.42f : 0.26f, pitch, false);
            player.sendOverlayMessage(Component.literal(bar).withColor(color));
        }
    }

    private static JsonObject heldOpenedMap(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof TreasureMapItem)) return null;
        String raw = ItemData.string(stack, TreasureMapItem.DATA);
        if (raw.isEmpty()) return null;
        try {
            return JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String directionOf(double dx, double dz) {
        if (dx * dx + dz * dz < 1e-4) return "脚下";
        int sector = (int) Math.floor(Math.atan2(dx, -dz) / SECTOR_ANGLE + .5);
        return DIRECTIONS.get(Math.floorMod(sector, DIRECTIONS.size()));
    }

    static String clueText(double dx, double dz, boolean newlyOpened) {
        double distance = Math.hypot(dx, dz);
        if (distance == 0) return "藏宝地点很近，就在脚下。再次右键可挖掘。";

        String direction = directionOf(dx, dz);
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
