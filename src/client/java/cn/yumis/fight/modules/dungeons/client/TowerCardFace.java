package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.TowerCard;
import cn.yumis.fight.modules.presentation.api.client.RichTextDrawing;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

final class TowerCardFace {
    static final int DESCRIPTION_TOP = 48;
    private static final int TEXT_INSET = 12;
    private static final int CORNER_RADIUS = 5;
    private static final int WHITE = 0xffffffff;

    private record Colors(String label, int edge, int accent, int fill) {}

    private final TowerCard.Rarity rarity;
    private final String title;
    private final int width;
    private final Font font;
    private final List<FormattedCharSequence> lines;
    private final Colors colors;

    TowerCardFace(TowerCard card, int width) {
        this.width = width;
        rarity = card.rarity();
        font = UiFonts.font();
        int textWidth = Math.max(1, width - TEXT_INSET * 2);
        title = font.width(card.name()) <= textWidth ? card.name()
            : font.plainSubstrByWidth(card.name(), Math.max(1, textWidth - font.width("…"))) + "…";
        lines = font.split(card.description().component(), textWidth);
        colors = switch (rarity) {
            case COMMON -> new Colors(card.milk() ? "保底" : "普通", 0xffeaf1f8, 0xff9cadbd, 0xff232c39);
            case RARE -> new Colors("稀有", 0xffb78cff, 0xff7255a1, 0xff292038);
            case EPIC -> new Colors("史诗", 0xffffa24e, 0xffa66534, 0xff33241d);
            case MYTHIC -> new Colors(card.careerMythic() ? "职业神话" : "神话", 0xfff5b2c9, 0xfff3d79c, 0xff37252d);
        };
    }

    int lineStep() { return font.lineHeight + 3; }

    int descriptionHeight() { return lines.size() * lineStep(); }

    int maximumScroll(int height, int footer) {
        return Math.max(0, descriptionHeight() - (height - DESCRIPTION_TOP - footer));
    }

    void draw(GuiGraphicsExtractor graphics, int x, int y, int height, int footer, int scroll) {
        int right = x + width;
        int bottom = y + height;
        int textLeft = x + TEXT_INSET;
        boolean mythic = rarity == TowerCard.Rarity.MYTHIC;
        UiTheme.surface(graphics, x, y, width, height, CORNER_RADIUS, colors.fill(), colors.edge());
        if (mythic) {
            int corner = UiTheme.pixelStyle() ? 1 : CORNER_RADIUS;
            graphics.fillGradient(x, y + corner, x + 1, bottom - corner, colors.edge(), colors.accent());
            graphics.fillGradient(right - 1, y + corner, right, bottom - corner, colors.edge(), colors.accent());
            graphics.enableScissor(x, bottom - corner, right, bottom);
            try {
                UiTheme.surface(graphics, x, y, width, height, CORNER_RADIUS, colors.fill(), colors.accent());
            } finally {
                graphics.disableScissor();
            }
        }

        graphics.text(font, colors.label(), textLeft, y + 10, colors.edge(), false);
        graphics.text(font, title, textLeft, y + 24, mythic ? colors.accent() : WHITE, false);
        graphics.fill(textLeft, y + 40, right - TEXT_INSET, y + 41, colors.accent());

        int bodyTop = y + DESCRIPTION_TOP;
        int bodyBottom = bottom - footer;
        if (bodyBottom <= bodyTop) return;
        int maxScroll = maximumScroll(height, footer);
        int offset = Math.min(scroll, maxScroll);
        graphics.enableScissor(textLeft - 1, bodyTop - 1, right - TEXT_INSET + 1, bodyBottom);
        try {
            int lineY = bodyTop - offset;
            for (var line : lines) {
                RichTextDrawing.line(graphics, font, line, textLeft, lineY, WHITE);
                lineY += lineStep();
            }
        } finally {
            graphics.disableScissor();
        }
        if (maxScroll > 0) {
            int visible = bodyBottom - bodyTop;
            int thumbHeight = Math.min(visible, Math.max(6, visible * visible / descriptionHeight()));
            int thumbTop = bodyTop + (visible - thumbHeight) * offset / maxScroll;
            graphics.fill(right - 8, bodyTop, right - 7, bodyBottom, colors.accent());
            graphics.fill(right - 8, thumbTop, right - 7, thumbTop + thumbHeight, colors.edge());
        }
    }

    void flash(GuiGraphicsExtractor graphics, int x, int y, int height, int alpha) {
        graphics.fill(x + 6, y + 6, x + width - 6, y + height - 6, alpha << 24 | colors.edge() & 0xffffff);
    }
}
