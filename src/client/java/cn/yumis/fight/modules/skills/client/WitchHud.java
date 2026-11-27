package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.Locale;

/** Witch HUD: riding machine channel bars (with Bitter Lollipop prompt), broom glide badge, and Lucky Candy timer. */
final class WitchHud {
    private WitchHud() {}

    static void renderCastBars(GuiGraphicsExtractor g, int width, int height) {
        long now = ClientUi.now();
        var m = WitchClient.activeMachine();
        if (m == null || m.finished) return;
        long total = Math.max(1L, m.endAt - m.start);
        long left = Math.max(0L, m.endAt - now);
        String label = m.cast.skill.title + " " + String.format(Locale.ROOT, "%.1f", left / 1000.0) + "秒 · 再按引爆（苦涩炸炉）";
        int color = 0xff000000 | WitchRules.elementColor(m.element);
        SkillCastBar.render(g, width, height, label, left / (double) total, color);
    }

    static int render(GuiGraphicsExtractor g, int x, int y, long serverNow) {
        var state = SkillsClient.current();
        if (!state.progress().hasJob(JobCatalog.WITCH)) return y;
        var font = UiFonts.font();

        if (WitchClient.isBroomGliding()) {
            String text = "骑扫把滑翔 · 跳跃/潜行解除";
            int w = font.width(text) + 14;
            UiTheme.hud(g, x - w / 2, y, w, 16);
            g.text(font, text, x - font.width(text) / 2, y + 4, 0xff000000 | WitchRules.COLOR_CANDY, true);
            y += 22;
        }

        var wState = CareerClient.INSTANCE.view.witch;
        long candyLeft = wState.candyBuffUntil - serverNow;
        if (candyLeft > 0L) {
            CareerHud.timer(g, SkillCatalog.require(WitchSkills.GIANT_FLYSWATTER), x, y, candyLeft, 15000L, 0xff000000 | WitchRules.COLOR_CANDY);
            y += 68;
        }
        return y;
    }
}
