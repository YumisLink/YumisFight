package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.Locale;

/** Battle Mage HUD: charge/channel bars, 5-color Chaser queue & buffs, Battle Groove stage, and Teana timers. */
final class BattleMageHud {
    private static final String[] BUFF_LABELS = {"无", "光", "火", "冰", "暗"};
    private static final int[] CHASER_ARGB = {
        0xff000000 | BattleMageRules.COLOR_NEUTRAL,
        0xff000000 | BattleMageRules.COLOR_LIGHT,
        0xff000000 | BattleMageRules.COLOR_FIRE,
        0xff000000 | BattleMageRules.COLOR_ICE,
        0xff000000 | BattleMageRules.COLOR_DARK
    };

    private BattleMageHud() {}

    static void renderCastBars(GuiGraphicsExtractor g, int width, int height) {
        long now = ClientUi.now();
        if (BattleMageChargeClient.active()) {
            var cast = BattleMageChargeClient.cast();
            boolean teana = CareerClient.INSTANCE.view.battleMage.transformed(SkillsClient.current().serverNow());
            String title = BattleMageRules.skillTitle(cast.skill, cast.parameters, teana);
            if (!BattleMageChargeClient.isFull(now)) {
                double progress = BattleMageChargeClient.chargeProgress(now);
                String label = title + " · 蓄力 " + String.format(Locale.ROOT, "%.1f", BattleMageChargeClient.chargeRemainingSeconds(now)) + "秒";
                int col = teana ? (0xff000000 | BattleMageRules.COLOR_TEANA) : (0xff000000 | BattleMageRules.COLOR_FIRE);
                SkillCastBar.render(g, width, height, label, progress, col);
            } else {
                double hold = BattleMageChargeClient.holdRemainingRatio(now);
                String label = title + " · 满蓄力 " + String.format(Locale.ROOT, "%.1f", BattleMageChargeClient.holdRemainingSeconds(now)) + "秒";
                SkillCastBar.render(g, width, height, label, hold, 0xffffd700);
            }
            return;
        }
        var punto = BattleMageClient.activePunto();
        if (punto != null) {
            long total = Math.max(1L, punto.endAt - punto.start);
            long left = Math.max(0L, punto.endAt - now);
            String label = "流星闪影击 · 连刺 " + punto.delivered + "/" + punto.totalHits + "（跳跃/Esc 中断）";
            SkillCastBar.render(g, width, height, label, left / (double) total, 0xff000000 | BattleMageRules.COLOR_MARTIAL);
            return;
        }
        var spear = BattleMageClient.activeSpear();
        if (spear != null && !spear.finished) {
            long total = Math.max(1L, spear.finishAt - spear.start);
            long left = Math.max(0L, spear.finishAt - now);
            String label = "煌龙偃月 · 龙炫纹 " + spear.delivered + "/" + spear.totalBalls;
            SkillCastBar.render(g, width, height, label, left / (double) total, 0xffffb833);
            return;
        }
        var fin = BattleMageClient.activeTeanaFinisher();
        if (fin != null) {
            long total = Math.max(1L, fin.endAt - fin.start);
            long left = Math.max(0L, fin.endAt - now);
            String label = "贝亚娜终结 · 使徒穿刺 " + String.format(Locale.ROOT, "%.1f", left / 1000.0) + "秒";
            SkillCastBar.render(g, width, height, label, left / (double) total, 0xff000000 | BattleMageRules.COLOR_TEANA);
        }
    }

    static int render(GuiGraphicsExtractor g, int x, int y, long serverNow) {
        var state = SkillsClient.current();
        if (!state.progress().hasJob(JobCatalog.BATTLE_MAGE)) return y;
        var font = UiFonts.font();
        var bm = CareerClient.INSTANCE.view.battleMage;
        boolean teana = bm.transformed(serverNow);

        // 1. Chaser Queue Box (up to 7 visible slots, head highlighted) + 5-color Buff row
        var lp = BattleMageRules.launcherParams(state.stats(), state.progress());
        int maxCap = (int) Math.max(1, lp.get("maxChasers", 7.0));
        int count = bm.chaserCount(serverNow);
        int boxW = 114, boxH = 54;
        int left = x - boxW / 2;
        if (teana) {
            g.fill(left - 2, y - 2, left + boxW + 2, y + boxH + 2, 0xffffd700);
        }
        UiTheme.hud(g, left, y, boxW, boxH);

        // Draw queue pearls (up to 7 slots in top row)
        int shownSlots = Math.min(7, maxCap);
        int slotStep = Math.min(15, (boxW - 12) / Math.max(1, shownSlots));
        int startX = x - (shownSlots - 1) * slotStep / 2;
        int orbY = y + 11;
        int drawn = 0;
        if (bm.chasers != null) {
            for (var ch : bm.chasers) {
                if (ch == null || ch.expiresAt <= serverNow) continue;
                if (drawn >= shownSlots) break;
                int cx = startX + drawn * slotStep;
                int col = (teana || ch.teana) ? (0xff000000 | BattleMageRules.COLOR_TEANA) : CHASER_ARGB[Math.clamp(ch.type, 0, 4)];
                if (drawn == 0) {
                    g.fill(cx - 6, orbY - 6, cx + 6, orbY + 6, 0xffffffff);
                }
                g.fill(cx - 5, orbY - 5, cx + 5, orbY + 5, col);
                g.fill(cx - 2, orbY - 2, cx + 2, orbY + 2, 0xe0ffffff);
                drawn++;
            }
        }
        for (int i = drawn; i < shownSlots; i++) {
            int cx = startX + i * slotStep;
            g.fill(cx - 4, orbY - 4, cx + 4, orbY + 4, 0xff222836);
        }

        // Caption: Chaser count
        String chaserCaption = (teana ? "贝亚娜炫纹 " : "炫纹 ") + count + "/" + maxCap;
        g.text(font, chaserCaption, x - font.width(chaserCaption) / 2, y + 21, teana ? UiTheme.HUD_GOLD : UiTheme.HUD_TEXT, true);

        // 5-Color Buff micro badges
        int buffY = y + 39;
        for (int i = 0; i < 5; i++) {
            int bx = left + 13 + i * 22;
            boolean on = bm.buffUntil != null && i < bm.buffUntil.length && bm.buffUntil[i] > serverNow;
            g.fill(bx - 8, buffY - 7, bx + 8, buffY + 7, on ? CHASER_ARGB[i] : 0xff1e2430);
            g.fill(bx - 6, buffY - 5, bx + 6, buffY + 5, on ? 0xd0141824 : 0xff151922);
            g.text(font, BUFF_LABELS[i], bx - font.width(BUFF_LABELS[i]) / 2, buffY - 4, on ? CHASER_ARGB[i] : 0xff5a6478, true);
        }
        y += boxH + 10;

        // 2. Battle Groove (斗神意志) stage bar
        int grooveW = 114, grooveH = 26;
        int gLeft = x - grooveW / 2;
        UiTheme.hud(g, gLeft, y, grooveW, grooveH);
        String gText = bm.grooveStage > 0
            ? "斗神意志 " + bm.grooveStage + "阶 · " + bm.comboCount + "连击"
            : "斗神意志 0阶";
        int gCol = bm.grooveStage >= 6 ? UiTheme.HUD_GOLD : (bm.grooveStage > 0 ? 0xffffa756 : UiTheme.HUD_MUTED);
        g.text(font, gText, x - font.width(gText) / 2, y + 4, gCol, true);
        double fill = bm.grooveStage / 6.0;
        g.fill(gLeft + 6, y + 17, gLeft + grooveW - 6, y + 21, 0xff1a1f2c);
        g.fill(gLeft + 6, y + 17, gLeft + 6 + (int) ((grooveW - 12) * Math.clamp(fill, 0.0, 1.0)), y + 21, gCol);
        y += grooveH + 12;

        // 3. Teana Transformation active timer
        if (teana) {
            long leftMs = bm.teanaUntil - serverNow;
            var tp = SkillParameters.resolve(SkillCatalog.require(BattleMageSkills.TEANA_TRANSFORMATION),
                Math.max(1, SkillLearning.effective(state.stats(), state.progress(), SkillCatalog.require(BattleMageSkills.TEANA_TRANSFORMATION))),
                state.stats().parameters);
            long totalMs = Math.max(1000L, Math.round(tp.get("transformSeconds", 30.0) * 1000.0));
            CareerHud.timer(g, SkillCatalog.require(BattleMageSkills.TEANA_TRANSFORMATION), x, y, leftMs, totalMs, 0xff000000 | BattleMageRules.COLOR_TEANA);
            if (!bm.teanaFinisherUsed) {
                String hint = "再按终结穿刺";
                g.text(font, hint, x - font.width(hint) / 2, y + 54, UiTheme.HUD_GOLD, true);
                y += 12;
            }
            y += 68;
        }

        // 4. Fusion Resonance Buff timer
        if (bm.fusionUntil > serverNow) {
            long leftMs = bm.fusionUntil - serverNow;
            CareerHud.timer(g, SkillCatalog.require(BattleMageSkills.CHASER_FUSION), x, y, leftMs, 15000L, 0xff000000 | BattleMageRules.COLOR_DARK);
            y += 68;
        }

        // 5. Multi-charge indicators (if any skill has maxCharges > 1)
        var player = Minecraft.getInstance().player;
        if (player != null) {
            long now = ClientUi.now();
            for (String cid : new String[]{BattleMageSkills.PALM_BLAST, BattleMageSkills.CHASER_FUSION, BattleMageSkills.DRAGON_SPEAR}) {
                var sk = SkillCatalog.require(cid);
                int r = SkillLearning.effective(state.stats(), state.progress(), sk);
                if (r <= 0) continue;
                var sp = SkillParameters.forCast(player.getUUID(), sk, r, state.stats().parameters);
                int maxC = (int) Math.round(sp.get("maxCharges", 1.0));
                if (maxC > 1) {
                    int rem = BattleMageClient.remainingCharges(cid, maxC, now);
                    long cdRem = BattleMageClient.nextRechargeRemaining(cid, now);
                    String line = sk.title + " 充能 " + rem + "/" + maxC + (cdRem > 0 ? " (" + seconds(cdRem) + "s)" : "");
                    g.text(font, line, x - font.width(line) / 2, y, UiTheme.HUD_GOLD, true);
                    y += 14;
                }
            }
        }
        return y;
    }

    private static long seconds(long millis) {
        return Math.max(0L, (millis + 999L) / 1000L);
    }
}
