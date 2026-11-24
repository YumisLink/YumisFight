package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.presentation.api.client.PlayerPageSurface;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;

/** Shows the player's selected cards and their saved bindings on the skill page. */
public final class TowerCardGallery {
    private static final int GAP = 8;
    private static final int MIN_CARD_WIDTH = 132;
    private static final int BOTTOM_PADDING = 10;

    private TowerCardGallery() {}

    public static void render(PlayerPageSurface page) {
        var chosen = TowerClient.chosenCards();
        page.gap();
        page.line("塔牌", UiTheme.GOLD);
        if (chosen.isEmpty()) {
            page.line("还没有选到塔牌", UiTheme.MUTED);
            return;
        }
        page.line("已持有 " + chosen.size() + " 张", UiTheme.MUTED);
        page.gap();

        int width = Math.max(1, page.contentWidth() - 24);
        int columns = Math.max(1, (width + GAP) / (MIN_CARD_WIDTH + GAP));
        int cardWidth = (width - (columns - 1) * GAP) / columns;
        var cards = chosen.stream().map(card -> new TowerCardFace(card, cardWidth)).toList();
        int y = page.cursor();
        for (int first = 0; first < cards.size(); first += columns) {
            var row = cards.subList(first, Math.min(first + columns, cards.size()));
            int height = TowerCardFace.DESCRIPTION_TOP + BOTTOM_PADDING
                + Math.max(24, row.stream().mapToInt(TowerCardFace::descriptionHeight).max().orElseThrow());
            for (int column = 0; column < row.size(); column++) {
                var face = row.get(column);
                page.drawing(column * (cardWidth + GAP), y, cardWidth, height, (graphics, x, top) ->
                    face.draw(graphics, x, top, height, BOTTOM_PADDING, 0));
            }
            y += height + GAP;
        }
        page.cursor(y - GAP);
    }
}
