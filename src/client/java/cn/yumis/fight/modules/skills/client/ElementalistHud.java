package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.Locale;

/** Elementalist HUD: forward charge / full-hold / channel bars, 4-lamp rune row, and active skill timers. */
final class ElementalistHud {
    private static final String[] LAMP_LABELS = {"光", "火", "冰", "暗"};
    private static final int[] LAMP_COLORS = {
        0xff000000 | ElementalistRules.COLOR_LIGHT,
        0xff000000 | ElementalistRules.COLOR_FIRE,
        0xff000000 | ElementalistRules.COLOR_ICE,
        0xff000000 | ElementalistRules.COLOR_DARK
    };

    private ElementalistHud() {}

    static void renderCastBars(GuiGraphicsExtractor g, int width, int height) {
        long now = ClientUi.now();
        if (ElementalistChargeClient.active()) {
            var cast = ElementalistChargeClient.cast();
            String title = ElementalistRules.skillTitle(cast.skill, cast.parameters);
            if (!ElementalistChargeClient.isFull(now)) {
                double progress = ElementalistChargeClient.chargeProgress(now);
                String label = title + " · 蓄力 " + String.format(Locale.ROOT, "%.1f", ElementalistChargeClient.chargeRemainingSeconds(now)) + "秒";
                int color = 0xff000000 | ElementalistRules.elementColor(ElementalistChargeClient.effectiveElement());
                SkillCastBar.render(g, width, height, label, progress, color);
            } else {
                double hold = ElementalistChargeClient.holdRemainingRatio(now);
                String label = title + " · 满蓄力 " + String.format(Locale.ROOT, "%.1f", ElementalistChargeClient.holdRemainingSeconds(now)) + "秒";
                SkillCastBar.render(g, width, height, label, hold, 0xfff5d061);
            }
            return;
        }
        var lightning = ElementalistClient.activeLightningChannel();
        if (lightning != null) {
            long total = Math.max(1L, lightning.endAt - lightning.start);
            long left = Math.max(0L, lightning.endAt - now);
            String label = "天雷 · 引导 " + String.format(Locale.ROOT, "%.1f", left / 1000.0) + "秒（Esc 取消）";
            SkillCastBar.render(g, width, height, label, left / (double) total, LAMP_COLORS[0]);
            return;
        }
        var storm = ElementalistClient.activeAstralStorm();
        if (storm != null && storm.channeling && !storm.finished) {
            long total = Math.max(1L, storm.endAt - storm.start);
            long left = Math.max(0L, storm.endAt - now);
            String label = "陨星幻灭 · 无敌浮空 " + String.format(Locale.ROOT, "%.1f", left / 1000.0) + "秒";
            SkillCastBar.render(g, width, height, label, left / (double) total, 0xff000000 | ElementalistRules.COLOR_PRISM);
        }
    }

    static int render(GuiGraphicsExtractor g, int x, int y, long serverNow) {
        var state = SkillsClient.current();
        if (!state.progress().hasJob(JobCatalog.ELEMENTALIST)) return y;
        var font = UiFonts.font();
        var lamps = CareerClient.INSTANCE.view.elementalist;
        int lit = lamps.litCount(serverNow);
        boolean resonance = lit >= 4;

        // Single-row 4-lamp gauge (光/火/冰/暗)
        int boxW = 104, boxH = 38;
        int left = x - boxW / 2;
        if (resonance) {
            g.fill(left - 2, y - 2, left + boxW + 2, y + boxH + 2, 0xffd8b459);
        }
        UiTheme.hud(g, left, y, boxW, boxH);
        for (int i = 0; i < 4; i++) {
            int cx = left + 14 + i * 25;
            int cy = y + 13;
            boolean on = lamps.lampUntil[i] > serverNow;
            g.fill(cx - 9, cy - 9, cx + 9, cy + 9, on ? LAMP_COLORS[i] : 0xff222836);
            g.fill(cx - 7, cy - 7, cx + 7, cy + 7, on ? 0xd0141824 : 0xff161b26);
            int textColor = on ? LAMP_COLORS[i] : 0xff5a6478;
            String glyph = LAMP_LABELS[i];
            g.text(font, glyph, cx - font.width(glyph) / 2, cy - 4, textColor, true);
        }
        long shortest = lamps.shortestRemaining(serverNow);
        String caption = resonance
            ? "四灯共鸣 · " + seconds(shortest) + "秒"
            : lit > 0 ? "圣灵符文 " + lit + "/4 · " + seconds(shortest) + "秒" : "圣灵符文 0/4";
        g.text(font, caption, x - font.width(caption) / 2, y + 26, resonance ? UiTheme.HUD_GOLD : UiTheme.HUD_MUTED, true);
        y += boxH + 14;

        long now = ClientUi.now();
        var wall = ElementalistClient.activeIceWall();
        if (wall != null && wall.endAt > now) {
            long total = Math.max(1L, wall.endAt - wall.start);
            CareerHud.timer(g, SkillCatalog.require(ElementalistSkills.ICE_WALL), x, y, wall.endAt - now, total, LAMP_COLORS[2]);
            if (wall.shieldMax > 0.0) {
                String blk = "抵伤 " + NumberText.damage(wall.shieldRemaining) + "/" + NumberText.damage(wall.shieldMax);
                g.text(font, blk, x - font.width(blk) / 2, y + 54, UiTheme.HUD_MUTED, true);
                y += 12;
            }
            y += 68;
        }
        var bh = ElementalistClient.activeBlackHole();
        if (bh != null && bh.endAt > now) {
            long total = Math.max(1L, bh.endAt - bh.start);
            CareerHud.timer(g, SkillCatalog.require(ElementalistSkills.BLACK_HOLE), x, y, bh.endAt - now, total, LAMP_COLORS[3]);
            if (bh.manualDetonate) {
                String hint = "再按引爆";
                g.text(font, hint, x - font.width(hint) / 2, y + 54, UiTheme.HUD_GOLD, true);
                y += 12;
            }
            y += 68;
        }
        var player = Minecraft.getInstance().player;
        if (player != null) {
            var voidSkill = SkillCatalog.require(ElementalistSkills.VOID_SPHERE);
            int rank = SkillLearning.effective(state.stats(), state.progress(), voidSkill);
            if (rank > 0) {
                var vp = SkillParameters.forCast(player.getUUID(), voidSkill, rank, state.stats().parameters);
                int maxCharges = (int) Math.round(vp.get("maxCharges", 1.0));
                if (maxCharges > 1) {
                    int rem = ElementalistClient.remainingCharges(voidSkill.id, maxCharges, now);
                    long cdRem = ElementalistClient.nextRechargeRemaining(voidSkill.id, now);
                    String line = "双生暗星 " + rem + "/" + maxCharges + (cdRem > 0 ? " (" + seconds(cdRem) + "s)" : "");
                    g.text(font, line, x - font.width(line) / 2, y, LAMP_COLORS[3], true);
                    y += 16;
                }
            }
        }
        return y;
    }

    private static long seconds(long millis) {
        return Math.max(0L, (millis + 999L) / 1000L);
    }
}
