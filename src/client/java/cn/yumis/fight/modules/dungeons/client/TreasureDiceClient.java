package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.TreasureDiceEntity;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.WorldInteractionMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Registers Treasure Hunt Twin Dice into the shared crosshair WorldInteractionMenu
 * (aim die -> right HUD list -> scroll to pick -> right-click to confirm).
 */
public final class TreasureDiceClient {
    private TreasureDiceClient() {}

    public static void registerWorldMenu() {
        WorldInteractionMenu.register("treasure_dice", 15, TreasureDiceClient::queryMenu);
    }

    private static WorldInteractionMenu.Target queryMenu() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || mc.gui.screen() != null || !ClientUi.current().ready()) {
            return null;
        }
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof TreasureDiceEntity die)) {
            return null;
        }
        if (mc.player.distanceToSqr(die) > 36.0) {
            return null;
        }

        int slot = die.slot();
        String phase = die.phase();
        int mult = die.multiplier();
        int rounds = die.rounds();
        int v1 = die.firstValue();
        boolean isOwner = mc.player.getStringUUID().equals(die.ownerId());
        boolean busy = ClientUi.current().serverNow() < die.rollUntil()
            || "ROLLING_1".equals(phase) || "ROLLING_2".equals(phase) || "REVEAL".equals(phase);

        String title = "命运双骰 · 骰子 " + slot;
        String subtitle = "当前倍率 ×" + mult + " · 第 " + rounds + "/6 轮";
        var options = new ArrayList<WorldInteractionMenu.Option>();

        if (!isOwner) {
            options.add(new WorldInteractionMenu.Option("not_owner", "仅图主可操作命运双骰", "等待图主决策", null));
            return new WorldInteractionMenu.Target("dice:" + die.getId() + ":" + phase + ":viewer", title, subtitle, options);
        }
        if (busy) {
            options.add(new WorldInteractionMenu.Option("rolling", "骰子旋转开奖中……", "请稍候", null));
            return new WorldInteractionMenu.Target("dice:" + die.getId() + ":" + phase + ":busy", title, subtitle, options);
        }

        switch (phase) {
            case "CHOOSE_START" -> {
                if (slot == 1) {
                    options.add(new WorldInteractionMenu.Option(
                        "start_64x", "连庄豪赌 · 64× 起步（最高 4096×）", "需赢首轮 · 输则归零",
                        () -> sendAction("start_64x", 1)
                    ));
                    options.add(new WorldInteractionMenu.Option(
                        "start_1x", "常规开赌 · 从 1× 起步（最高 64×）", WorldInteractionMenu.DEFAULT_HINT,
                        () -> sendAction("start_1x", 1)
                    ));
                    options.add(new WorldInteractionMenu.Option(
                        "cashout_1x", "稳健收手 · 领取 1× 通关宝箱", "安全落袋",
                        () -> sendAction("cashout_1x", 1)
                    ));
                } else {
                    options.add(new WorldInteractionMenu.Option("go_die_1", "请先对准左侧【骰子 1】选择开局模式", "移向骰子 1", null));
                }
            }
            case "READY_DIE_1" -> {
                if (slot == 1) {
                    int nextMult = Math.min(4096, mult * 2);
                    options.add(new WorldInteractionMenu.Option(
                        "roll_1", "掷出第一颗骰子（冲刺 ×" + nextMult + "）", WorldInteractionMenu.DEFAULT_HINT,
                        () -> sendAction("roll_1", 1)
                    ));
                    if (!die.mustWinOne()) {
                        options.add(new WorldInteractionMenu.Option(
                            "cashout", "见好就收 · 领取 ×" + mult + " 宝箱", "立即结算",
                            () -> sendAction("cashout", 1)
                        ));
                    }
                } else {
                    options.add(new WorldInteractionMenu.Option("go_die_1", "请先对准左侧【骰子 1】开掷", "移向骰子 1", null));
                }
            }
            case "READY_DIE_2" -> {
                if (slot == 2) {
                    int nextMult = Math.min(4096, mult * 2);
                    int higher = Math.max(0, 9 - v1);
                    int lower = Math.max(0, v1 - 1);
                    if (higher > 0) {
                        String odds = higher == 8 ? "必中" : higher + "/8";
                        options.add(new WorldInteractionMenu.Option(
                            "guess_higher", "猜第二颗更大 (> " + v1 + " · " + odds + ")",
                            "猜中 ×" + nextMult + " · 猜错归零",
                            () -> sendAction("guess_higher", 2)
                        ));
                    }
                    if (lower > 0) {
                        String odds = lower == 8 ? "必中" : lower + "/8";
                        options.add(new WorldInteractionMenu.Option(
                            "guess_lower", "猜第二颗更小 (< " + v1 + " · " + odds + ")",
                            "猜中 ×" + nextMult + " · 猜错归零",
                            () -> sendAction("guess_lower", 2)
                        ));
                    }
                } else {
                    options.add(new WorldInteractionMenu.Option(
                        "go_die_2", "骰子 1 为【" + v1 + "】，请对准右侧【骰子 2】猜大小", "移向骰子 2", null
                    ));
                }
            }
            default -> {
                return null;
            }
        }
        return new WorldInteractionMenu.Target("dice:" + die.getId() + ":" + phase + ":" + v1 + ":" + mult, title, subtitle, List.copyOf(options));
    }

    private static void sendAction(String action, int slot) {
        var req = ClientUi.current().operation("treasureDice");
        req.addProperty("action", action);
        req.addProperty("slot", slot);
        ClientUi.current().send("treasureDice", req);
    }
}
