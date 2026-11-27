package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.dungeons.api.TowerCard;
import cn.yumis.fight.modules.dungeons.api.TowerCardCatalog;
import cn.yumis.fight.modules.dungeons.api.TowerCareerDeck;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.ThemeDomain;
import cn.yumis.fight.modules.presentation.api.client.ThemedButton;
import cn.yumis.fight.modules.presentation.api.client.ThemedScreen;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creative-mode admin full card library with category filters, multi-keyword search, and direct add/remove. */
final class TowerCardLibraryScreen extends ThemedScreen {
    private static final int CARD_GAP = 8;
    private static final int MIN_CARD_WIDTH = 166;
    private static final int CARD_FOOTER = 34;
    private static final int SIDEBAR_ROW_HEIGHT = 22;
    private static final int SIDEBAR_ROW_STEP = 24;
    private static final int SIDEBAR_DIVIDER_HEIGHT = 16;
    private static final int MODAL_ROW_STEP = 26;
    private static final int HELD_BADGE_COLOR = 0xff8ef5ad;
    private static final int OTHER_JOB_BADGE_COLOR = 0xfff3d79c;
    private static final int JOB_LABEL_COLOR = 0xffd8c7a8;
    private static final int BIND_LABEL_COLOR = 0xff77bdff;
    private static final long CLEAR_CONFIRM_WINDOW_MS = 3000;

    private record SidebarItem(TowerCardCatalog.Category category, int y, boolean sectionBefore) {}

    private record CardCell(
        TowerCardCatalog.Entry entry,
        TowerCard displayCard,
        TowerCardFace face,
        int column,
        int heldCount,
        boolean activeOnCurrentJob,
        boolean requiresBinding
    ) {}

    private record GridRow(List<CardCell> cells, int y, int height) {}

    private record BindOption(String variant, String variantTitle, String badge, boolean recommended) {}

    private static final class BindingModal {
        final TowerCard card;
        final String currentVariant;
        final String subtitle;
        final List<BindOption> options;
        int scroll;

        BindingModal(TowerCard card, String currentVariant, String subtitle, List<BindOption> options) {
            this.card = card;
            this.currentVariant = currentVariant == null ? "" : currentVariant;
            this.subtitle = subtitle;
            this.options = List.copyOf(options);
        }
    }

    private EditBox searchBox;
    private ThemedButton clearSearchButton;
    private ThemedButton clearAllButton;
    private String query = "";
    private String selectedCategoryId = "all";
    private String currentJob = "";
    private long observedDeckRevision = -1;
    private long observedThemeRevision = UiTheme.revision;
    private long clearConfirmUntil;

    private List<TowerCardCatalog.Category> categories = List.of();
    private List<SidebarItem> sidebarItems = List.of();
    private List<GridRow> rows = List.of();
    private Set<String> heldIds = Set.of();
    private Map<String, TowerCard> heldCards = Map.of();
    private Map<String, Integer> heldCounts = Map.of();
    private int totalHeldCards;
    private int activeHeldCards;
    private int filteredCount;

    private int panelLeft, panelTop, panelWidth, panelHeight;
    private int sidebarLeft, sidebarTop, sidebarWidth, sidebarHeight, sidebarContentHeight, sidebarScroll;
    private int contentLeft, contentWidth;
    private int gridLeft, gridTop, gridWidth, gridHeight, gridContentHeight, gridScroll;
    private int columns = 2, cardWidth = 170;

    private boolean draggingGridScroll;
    private double gridScrollGrab;
    private BindingModal bindingModal;

    TowerCardLibraryScreen() {
        super(Component.literal("全卡牌库"), ThemeDomain.MENU);
    }

    @Override
    protected void init() {
        UiTheme.check();
        observedThemeRevision = UiTheme.revision;
        panelWidth = Math.clamp(width - 20, Math.min(width - 8, 420), 960);
        panelHeight = Math.clamp(height - 20, Math.min(height - 8, 280), 620);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        sidebarWidth = panelWidth >= 580 ? 132 : 108;
        sidebarLeft = panelLeft + 8;
        sidebarTop = panelTop + 36;
        sidebarHeight = Math.max(40, panelHeight - 46);

        contentLeft = sidebarLeft + sidebarWidth + 10;
        contentWidth = Math.max(160, panelLeft + panelWidth - 8 - contentLeft);

        gridLeft = contentLeft;
        gridTop = panelTop + 62;
        gridWidth = Math.max(120, contentWidth - 14);
        gridHeight = Math.max(40, panelTop + panelHeight - 10 - gridTop);

        columns = Math.clamp((gridWidth + CARD_GAP) / (MIN_CARD_WIDTH + CARD_GAP), 1, 4);
        cardWidth = Math.max(100, (gridWidth - (columns - 1) * CARD_GAP) / columns);

        int closeW = 44;
        int clearW = 74;
        addRenderableWidget(Button.builder(Component.literal("关闭"), ignored -> onClose())
            .bounds(panelLeft + panelWidth - 8 - closeW, panelTop + 8, closeW, 19)
            .build(ThemedButton::new));
        clearAllButton = addRenderableWidget(new ThemedButton(Button.builder(Component.literal("清空全部"), ignored -> onClearAllClick())
            .bounds(panelLeft + panelWidth - 8 - closeW - 6 - clearW, panelTop + 8, clearW, 19)));

        int clearSearchW = 20;
        int countReserve = 72;
        int searchW = Math.max(80, contentWidth - clearSearchW - countReserve - 10);
        searchBox = addRenderableWidget(new EditBox(UiFonts.font(), contentLeft, panelTop + 36, searchW, 18, Component.literal("搜索卡牌")));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("搜索卡名 / 效果 / 职业 / 技能 / ID…"));
        searchBox.setValue(query);
        searchBox.setResponder(text -> {
            if (!text.equals(query)) {
                query = text;
                gridScroll = 0;
                rebuildGrid();
                updateChromeButtons();
            }
        });
        clearSearchButton = addRenderableWidget(new ThemedButton(Button.builder(Component.literal("×"), ignored -> {
            searchBox.setValue("");
            query = "";
            gridScroll = 0;
            rebuildGrid();
            updateChromeButtons();
        }).bounds(contentLeft + searchW + 4, panelTop + 36, clearSearchW, 18)));

        refreshDeckData(true);
    }

    @Override
    public void tick() {
        super.tick();
        UiTheme.check();
        if (!TowerClient.canOpenCreativeLibrary()) {
            ClientUi.current().message("已退出创造模式或失去管理员权限，全卡牌库已关闭");
            onClose();
            return;
        }
        if (observedThemeRevision != UiTheme.revision) {
            observedThemeRevision = UiTheme.revision;
            rebuildGrid();
        }
        if (clearConfirmUntil > 0 && ClientUi.now() > clearConfirmUntil) {
            clearConfirmUntil = 0;
            updateChromeButtons();
        }
        if (observedDeckRevision != TowerClient.deckRevision() || !currentJob.equals(TowerClient.currentJob())) {
            refreshDeckData(false);
        }
    }

    private void onClearAllClick() {
        if (totalHeldCards <= 0) return;
        long now = ClientUi.now();
        if (clearConfirmUntil > now) {
            clearConfirmUntil = 0;
            TowerClient.creativeClear();
            updateChromeButtons();
        } else {
            clearConfirmUntil = now + CLEAR_CONFIRM_WINDOW_MS;
            updateChromeButtons();
        }
    }

    private void refreshDeckData(boolean resetScroll) {
        observedDeckRevision = TowerClient.deckRevision();
        currentJob = TowerClient.currentJob();
        categories = TowerCardCatalog.categories(currentJob);
        if (categories.stream().noneMatch(c -> c.id().equals(selectedCategoryId))) {
            selectedCategoryId = "all";
        }
        rebuildSidebarLayout();

        TowerCareerDeck.DeckSnapshot deck = TowerClient.retainedDeck();
        List<TowerCard> activeCards = TowerClient.activeChosenCards();
        var ids = new java.util.LinkedHashSet<String>();
        var cardsById = new LinkedHashMap<String, TowerCard>();
        var deckCounts = new HashMap<String, Integer>();
        var activeCounts = new HashMap<String, Integer>();
        if (deck != null && deck.hasCards()) {
            for (var ref : deck.chosen()) {
                ids.add(ref.id());
                deckCounts.merge(ref.id(), 1, Integer::sum);
                cardsById.putIfAbsent(ref.id(), restoreSafe(ref));
            }
            for (var list : deck.careerCards().values()) {
                for (var ref : list) {
                    ids.add(ref.id());
                    deckCounts.merge(ref.id(), 1, Integer::sum);
                    cardsById.putIfAbsent(ref.id(), restoreSafe(ref));
                }
            }
        }
        for (TowerCard active : activeCards) {
            ids.add(active.id());
            activeCounts.merge(active.id(), 1, Integer::sum);
            cardsById.put(active.id(), active);
        }
        var counts = new HashMap<String, Integer>();
        int total = 0;
        for (String cardId : ids) {
            int c = Math.max(deckCounts.getOrDefault(cardId, 0), activeCounts.getOrDefault(cardId, 0));
            counts.put(cardId, c);
            total += c;
        }
        heldIds = Set.copyOf(ids);
        heldCards = Map.copyOf(cardsById);
        heldCounts = Map.copyOf(counts);
        totalHeldCards = total;
        activeHeldCards = activeCards.size();
        if (resetScroll) gridScroll = 0;
        rebuildGrid();
        updateChromeButtons();
    }

    private static TowerCard restoreSafe(TowerCard.Reference ref) {
        try {
            return TowerCard.restore(ref);
        } catch (RuntimeException ignored) {
            var entry = TowerCardCatalog.find(ref.id());
            return entry == null ? TowerCard.milkCard() : entry.card();
        }
    }

    private void rebuildSidebarLayout() {
        var items = new ArrayList<SidebarItem>(categories.size());
        int y = 0;
        boolean addedJobDivider = false;
        for (var category : categories) {
            boolean divider = false;
            if (category.isJob() && !addedJobDivider) {
                addedJobDivider = true;
                divider = true;
                y += SIDEBAR_DIVIDER_HEIGHT;
            }
            items.add(new SidebarItem(category, y, divider));
            y += SIDEBAR_ROW_STEP;
        }
        sidebarItems = List.copyOf(items);
        sidebarContentHeight = Math.max(0, y - 2);
        sidebarScroll = Math.clamp(sidebarScroll, 0, Math.max(0, sidebarContentHeight - sidebarHeight));
    }

    private TowerCardCatalog.Category activeCategory() {
        for (var category : categories) {
            if (category.id().equals(selectedCategoryId)) return category;
        }
        return categories.isEmpty() ? null : categories.getFirst();
    }

    private void rebuildGrid() {
        var filtered = TowerCardCatalog.filter(activeCategory(), query, currentJob, heldIds, heldCards);
        filteredCount = filtered.size();
        var nextRows = new ArrayList<GridRow>();
        int y = 0;
        for (int first = 0; first < filtered.size(); first += columns) {
            int end = Math.min(filtered.size(), first + columns);
            var cells = new ArrayList<CardCell>(end - first);
            int maxDesc = 36;
            for (int i = first; i < end; i++) {
                var entry = filtered.get(i);
                int count = heldCounts.getOrDefault(entry.card().id(), 0);
                TowerCard display = count > 0 ? heldCards.getOrDefault(entry.card().id(), entry.card()) : entry.card();
                TowerCardFace face = new TowerCardFace(display, cardWidth);
                maxDesc = Math.max(maxDesc, face.descriptionHeight());
                boolean active = count > 0 && (!display.careerMythic() || display.job().equals(currentJob));
                boolean requiresBinding = entry.card().requiresWeaponBinding()
                    || entry.card().skillBinding() != TowerBattleCard.Binding.NONE;
                cells.add(new CardCell(entry, display, face, i - first, count, active, requiresBinding));
            }
            int rowHeight = TowerCardFace.DESCRIPTION_TOP + CARD_FOOTER + maxDesc;
            nextRows.add(new GridRow(List.copyOf(cells), y, rowHeight));
            y += rowHeight + CARD_GAP;
        }
        rows = List.copyOf(nextRows);
        gridContentHeight = Math.max(0, y - CARD_GAP);
        gridScroll = Math.clamp(gridScroll, 0, Math.max(0, gridContentHeight - gridHeight));
    }

    private void updateChromeButtons() {
        if (clearSearchButton != null) {
            clearSearchButton.visible = !query.isEmpty();
            clearSearchButton.active = !query.isEmpty();
        }
        if (clearAllButton != null) {
            clearAllButton.active = totalHeldCards > 0;
            boolean confirming = clearConfirmUntil > ClientUi.now() && totalHeldCards > 0;
            clearAllButton.setMessage(Component.literal(confirming ? "确认清空?" : "清空全部"));
            clearAllButton.textColor = confirming ? UiTheme.ERROR : -1;
        }
    }

    @Override
    protected void drawBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, UiTheme.OVERLAY);
        UiTheme.panel(graphics, panelLeft, panelTop, panelWidth, panelHeight);
        if (UiTheme.pixelStyle()) {
            UiTheme.titleBar(graphics, panelLeft + 4, panelTop + 4, panelWidth - 8, 26);
        }
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var font = UiFonts.font();
        int effMouseX = bindingModal == null ? mouseX : -1;
        int effMouseY = bindingModal == null ? mouseY : -1;

        String title = "创造模式 · 全卡牌库";
        graphics.text(font, title, panelLeft + 12, panelTop + 13, UiTheme.GOLD, false);
        String summary = activeHeldCards == totalHeldCards
            ? "已持有 " + totalHeldCards + " 张"
            : "已持有 " + totalHeldCards + " 张（当前职业生效 " + activeHeldCards + " 张）";
        int summaryX = panelLeft + 12 + font.width(title) + 12;
        int summaryMax = clearAllButton.getX() - summaryX - 8;
        if (summaryMax > 30) {
            graphics.text(font, truncate(font, summary, summaryMax), summaryX, panelTop + 13, UiTheme.MUTED, false);
        }

        String resultLabel = "共 " + filteredCount + " 张";
        graphics.text(font, resultLabel, contentLeft + contentWidth - font.width(resultLabel), panelTop + 41, UiTheme.MUTED, false);

        drawSidebar(graphics, font, effMouseX, effMouseY);
        drawCardGrid(graphics, font, effMouseX, effMouseY);
        drawGridScrollbar(graphics);

        super.drawContents(graphics, effMouseX, effMouseY, delta);

        if (bindingModal != null) {
            graphics.nextStratum();
            drawBindingModal(graphics, font, mouseX, mouseY);
        }
    }

    private void drawSidebar(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        int barW = sidebarContentHeight > sidebarHeight ? 6 : 0;
        int btnW = sidebarWidth - barW;
        graphics.enableScissor(sidebarLeft, sidebarTop, sidebarLeft + sidebarWidth, sidebarTop + sidebarHeight);
        try {
            for (var item : sidebarItems) {
                int y = sidebarTop + item.y() - sidebarScroll;
                if (item.sectionBefore()) {
                    int divY = y - SIDEBAR_DIVIDER_HEIGHT + 4;
                    if (divY + font.lineHeight >= sidebarTop && divY <= sidebarTop + sidebarHeight) {
                        graphics.text(font, "按职业筛选", sidebarLeft + 2, divY, UiTheme.MUTED, false);
                    }
                }
                if (y + SIDEBAR_ROW_HEIGHT < sidebarTop || y > sidebarTop + sidebarHeight) continue;
                var cat = item.category();
                boolean selected = cat.id().equals(selectedCategoryId);
                boolean hovered = mouseX >= sidebarLeft && mouseX < sidebarLeft + btnW
                    && mouseY >= Math.max(sidebarTop, y) && mouseY < Math.min(sidebarTop + sidebarHeight, y + SIDEBAR_ROW_HEIGHT);
                UiTheme.button(graphics, sidebarLeft, y, btnW, SIDEBAR_ROW_HEIGHT, true, hovered, selected);

                int count = TowerCardCatalog.count(cat, heldIds);
                String countText = String.valueOf(count);
                int countWidth = font.width(countText);
                int countColor = cat.kind() == TowerCardCatalog.Kind.OWNED && count > 0 ? HELD_BADGE_COLOR : UiTheme.MUTED;
                int textY = y + (SIDEBAR_ROW_HEIGHT - font.lineHeight) / 2;
                graphics.text(font, countText, sidebarLeft + btnW - 6 - countWidth, textY, countColor, false);

                String rawLabel = cat.isJob() && cat.jobId().equals(currentJob) ? "★ " + cat.label() : cat.label();
                int labelMax = Math.max(10, btnW - 14 - countWidth);
                int labelColor = selected ? UiTheme.GOLD
                    : cat.isJob() && cat.jobId().equals(currentJob) ? HELD_BADGE_COLOR : UiTheme.TEXT;
                graphics.text(font, truncate(font, rawLabel, labelMax), sidebarLeft + 6, textY, labelColor, false);
            }
        } finally {
            graphics.disableScissor();
        }
        if (sidebarContentHeight > sidebarHeight) {
            int max = sidebarContentHeight - sidebarHeight;
            int thumb = Math.max(14, sidebarHeight * sidebarHeight / sidebarContentHeight);
            int thumbY = sidebarTop + (sidebarHeight - thumb) * sidebarScroll / Math.max(1, max);
            graphics.fill(sidebarLeft + sidebarWidth - 3, sidebarTop, sidebarLeft + sidebarWidth - 1, sidebarTop + sidebarHeight, UiTheme.SCROLL_TRACK);
            graphics.fill(sidebarLeft + sidebarWidth - 3, thumbY, sidebarLeft + sidebarWidth - 1, thumbY + thumb, UiTheme.SCROLL_THUMB);
        }
    }

    private void drawCardGrid(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        graphics.enableScissor(gridLeft, gridTop, gridLeft + gridWidth, gridTop + gridHeight);
        try {
            if (rows.isEmpty()) {
                String emptyText = "owned".equals(selectedCategoryId) && query.isBlank()
                    ? "当前尚未持有任何卡牌，切换到其他分类即可直接获取"
                    : "没有找到符合条件的卡牌";
                graphics.text(font, emptyText, gridLeft + 8, gridTop + 14, UiTheme.MUTED, false);
                return;
            }
            for (var row : rows) {
                int rowTop = gridTop + row.y() - gridScroll;
                if (rowTop + row.height() < gridTop || rowTop > gridTop + gridHeight) continue;
                for (var cell : row.cells()) {
                    int cx = gridLeft + cell.column() * (cardWidth + CARD_GAP);
                    cell.face().draw(graphics, cx, rowTop, row.height(), CARD_FOOTER, 0);
                    drawCardBadge(graphics, font, cell, cx, rowTop);
                    drawCardActions(graphics, font, cell, cx, rowTop, row.height(), mouseX, mouseY);
                }
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawCardBadge(GuiGraphicsExtractor graphics, Font font, CardCell cell, int x, int y) {
        String badge;
        int color;
        if (cell.heldCount() > 0) {
            if (cell.entry().card().milk()) {
                badge = "已持有 ×" + cell.heldCount();
                color = HELD_BADGE_COLOR;
            } else if (cell.activeOnCurrentJob()) {
                badge = "已持有";
                color = HELD_BADGE_COLOR;
            } else {
                badge = cell.entry().jobTitle() + " · 已持有";
                color = OTHER_JOB_BADGE_COLOR;
            }
        } else if (cell.entry().card().careerMythic()) {
            badge = cell.entry().jobTitle();
            color = JOB_LABEL_COLOR;
        } else if (cell.requiresBinding()) {
            badge = cell.entry().card().requiresWeaponBinding() ? "选武器" : "选技能";
            color = BIND_LABEL_COLOR;
        } else {
            return;
        }
        int maxW = Math.max(20, cardWidth - 68);
        String text = truncate(font, badge, maxW);
        graphics.text(font, text, x + cardWidth - 12 - font.width(text), y + 10, color, false);
    }

    private void drawCardActions(GuiGraphicsExtractor graphics, Font font, CardCell cell,
                                 int cx, int cy, int cardHeight, int mouseX, int mouseY) {
        int btnX = cx + 8;
        int btnY = cy + cardHeight - 26;
        int btnW = cardWidth - 16;
        int btnH = 19;
        boolean insideGrid = mouseX >= gridLeft && mouseX < gridLeft + gridWidth
            && mouseY >= gridTop && mouseY < gridTop + gridHeight;

        if (cell.entry().card().milk() && cell.heldCount() > 0) {
            int half = (btnW - 4) / 2;
            drawInlineButton(graphics, font, btnX, btnY, half, btnH, "获取 +1", UiTheme.TEXT,
                insideGrid && inside(mouseX, mouseY, btnX, btnY, half, btnH));
            drawInlineButton(graphics, font, btnX + half + 4, btnY, btnW - half - 4, btnH, "删除 -1", UiTheme.ERROR,
                insideGrid && inside(mouseX, mouseY, btnX + half + 4, btnY, btnW - half - 4, btnH));
            return;
        }
        if (cell.requiresBinding() && cell.heldCount() > 0) {
            int half = (btnW - 4) / 2;
            drawInlineButton(graphics, font, btnX, btnY, half, btnH, "改绑", UiTheme.TEXT,
                insideGrid && inside(mouseX, mouseY, btnX, btnY, half, btnH));
            drawInlineButton(graphics, font, btnX + half + 4, btnY, btnW - half - 4, btnH, "删除", UiTheme.ERROR,
                insideGrid && inside(mouseX, mouseY, btnX + half + 4, btnY, btnW - half - 4, btnH));
            return;
        }
        String label = cell.heldCount() > 0 ? "删除"
            : cell.requiresBinding() ? "选择并获取" : "获取";
        int color = cell.heldCount() > 0 ? UiTheme.ERROR : UiTheme.TEXT;
        drawInlineButton(graphics, font, btnX, btnY, btnW, btnH, label, color,
            insideGrid && inside(mouseX, mouseY, btnX, btnY, btnW, btnH));
    }

    private void drawInlineButton(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
                                  String label, int color, boolean hovered) {
        UiTheme.button(graphics, x, y, w, h, true, hovered, false);
        String text = truncate(font, label, Math.max(8, w - 8));
        graphics.text(font, text, x + (w - font.width(text)) / 2, y + (h - font.lineHeight) / 2, color, false);
    }

    private void drawGridScrollbar(GuiGraphicsExtractor graphics) {
        if (gridContentHeight <= gridHeight) return;
        int thumb = Math.max(16, gridHeight * gridHeight / gridContentHeight);
        int max = Math.max(1, gridContentHeight - gridHeight);
        int offset = (gridHeight - thumb) * gridScroll / max;
        UiTheme.scrollbar(graphics, gridLeft + gridWidth + 4, gridTop, gridHeight, thumb, offset);
    }

    private int modalWidth() { return Math.min(360, width - 32); }
    private int modalHeight() { return Math.min(300, height - 32); }
    private int modalLeft() { return (width - modalWidth()) / 2; }
    private int modalTop() { return (height - modalHeight()) / 2; }
    private int modalListTop() { return modalTop() + 44; }
    private int modalListHeight() { return Math.max(32, modalHeight() - 80); }

    private void drawBindingModal(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, 0xaa000000);
        int mx = modalLeft(), my = modalTop(), mw = modalWidth(), mh = modalHeight();
        UiTheme.panel(graphics, mx, my, mw, mh);

        graphics.text(font, "选择绑定 · " + bindingModal.card.name(), mx + 12, my + 11, UiTheme.GOLD, false);
        graphics.text(font, truncate(font, bindingModal.subtitle, mw - 24), mx + 12, my + 26, UiTheme.MUTED, false);

        int listX = mx + 12;
        int listY = modalListTop();
        int listW = mw - 24;
        int listH = modalListHeight();
        int totalH = bindingModal.options.size() * MODAL_ROW_STEP;
        bindingModal.scroll = Math.clamp(bindingModal.scroll, 0, Math.max(0, totalH - listH));

        graphics.enableScissor(listX, listY, listX + listW, listY + listH);
        try {
            if (bindingModal.options.isEmpty()) {
                graphics.text(font, "当前尚未习得任何主动技能，", listX + 4, listY + 10, UiTheme.MUTED, false);
                graphics.text(font, "请先选择职业并学习技能后再绑定。", listX + 4, listY + 24, UiTheme.MUTED, false);
            } else {
                int optW = totalH > listH ? listW - 8 : listW;
                for (int i = 0; i < bindingModal.options.size(); i++) {
                    var option = bindingModal.options.get(i);
                    int oy = listY + i * MODAL_ROW_STEP - bindingModal.scroll;
                    if (oy + 22 < listY || oy > listY + listH) continue;
                    boolean selected = option.variant().equals(bindingModal.currentVariant);
                    boolean hovered = mouseX >= listX && mouseX < listX + optW
                        && mouseY >= Math.max(listY, oy) && mouseY < Math.min(listY + listH, oy + 22);
                    UiTheme.button(graphics, listX, oy, optW, 22, true, hovered, selected);
                    int textY = oy + (22 - font.lineHeight) / 2;
                    int badgeW = font.width(option.badge());
                    int badgeColor = option.recommended() ? BIND_LABEL_COLOR : UiTheme.MUTED;
                    graphics.text(font, option.badge(), listX + optW - 8 - badgeW, textY, badgeColor, false);
                    String label = selected ? option.variantTitle() + "（当前）" : option.variantTitle();
                    graphics.text(font, truncate(font, label, Math.max(20, optW - 18 - badgeW)),
                        listX + 8, textY, selected ? UiTheme.GOLD : UiTheme.TEXT, false);
                }
            }
        } finally {
            graphics.disableScissor();
        }
        if (totalH > listH) {
            int thumb = Math.max(14, listH * listH / totalH);
            int thumbY = listY + (listH - thumb) * bindingModal.scroll / Math.max(1, totalH - listH);
            graphics.fill(listX + listW - 4, listY, listX + listW - 1, listY + listH, UiTheme.SCROLL_TRACK);
            graphics.fill(listX + listW - 4, thumbY, listX + listW - 1, thumbY + thumb, UiTheme.SCROLL_THUMB);
        }

        int cancelW = 76;
        int cancelX = mx + (mw - cancelW) / 2;
        int cancelY = my + mh - 28;
        drawInlineButton(graphics, font, cancelX, cancelY, cancelW, 20, "取消", UiTheme.TEXT,
            inside(mouseX, mouseY, cancelX, cancelY, cancelW, 20));
    }

    private void openBindingModal(CardCell cell) {
        TowerCard card = cell.entry().card();
        String currentVariant = cell.heldCount() > 0 ? cell.displayCard().variant() : "";
        var options = new ArrayList<BindOption>();
        String subtitle;
        if (card.requiresWeaponBinding()) {
            subtitle = "选择要绑定的武器类型（优先列出本职武器）";
            Set<String> jobWeapons = currentJob.isEmpty() ? Set.of() : JobCatalog.weapons(currentJob);
            for (Weapon weapon : Weapon.standard()) {
                if (jobWeapons.contains(weapon.name())) {
                    options.add(new BindOption(weapon.name(), weapon.title(), "本职武器", true));
                }
            }
            for (Weapon weapon : Weapon.standard()) {
                if (!jobWeapons.contains(weapon.name())) {
                    options.add(new BindOption(weapon.name(), weapon.title(), "其他武器", false));
                }
            }
        } else {
            TowerBattleCard battle = TowerBattleCard.find(card.id());
            String req = battle == null ? "主动技能" : battle.binding().label();
            subtitle = "推荐条件：" + req + "（列出当前已学主动技能）";
            var skills = TowerClient.availableSkills();
            for (var skill : skills) {
                if (battle != null && battle.eligible(skill)) {
                    options.add(new BindOption(skill.id(), skill.title(), req, true));
                }
            }
            for (var skill : skills) {
                if (battle == null || !battle.eligible(skill)) {
                    options.add(new BindOption(skill.id(), skill.title(), "其他主动", false));
                }
            }
        }
        bindingModal = new BindingModal(card, currentVariant, subtitle, options);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (bindingModal != null) {
            return handleModalClick(event);
        }
        if (event.button() == 0) {
            if (handleSidebarClick(event.x(), event.y())) { unfocusSearch(); return true; }
            if (handleScrollbarClick(event.x(), event.y())) { unfocusSearch(); return true; }
            if (handleGridClick(event.x(), event.y())) { unfocusSearch(); return true; }
        }
        return super.mouseClicked(event, twice);
    }

    private void unfocusSearch() {
        if (searchBox != null && searchBox.isFocused()) {
            searchBox.setFocused(false);
            if (getFocused() == searchBox) setFocused(null);
        }
    }

    private boolean handleModalClick(MouseButtonEvent event) {
        if (event.button() != 0) return true;
        double x = event.x(), y = event.y();
        int mx = modalLeft(), my = modalTop(), mw = modalWidth(), mh = modalHeight();
        if (!inside(x, y, mx, my, mw, mh)) {
            bindingModal = null;
            playClick();
            return true;
        }
        int cancelW = 76;
        int cancelX = mx + (mw - cancelW) / 2;
        int cancelY = my + mh - 28;
        if (inside(x, y, cancelX, cancelY, cancelW, 20)) {
            bindingModal = null;
            playClick();
            return true;
        }
        int listX = mx + 12, listY = modalListTop(), listW = mw - 24, listH = modalListHeight();
        int totalH = bindingModal.options.size() * MODAL_ROW_STEP;
        int optW = totalH > listH ? listW - 8 : listW;
        if (inside(x, y, listX, listY, optW, listH)) {
            int localY = (int) y - listY + bindingModal.scroll;
            int idx = localY / MODAL_ROW_STEP;
            if (idx >= 0 && idx < bindingModal.options.size() && (localY % MODAL_ROW_STEP) < 22) {
                var option = bindingModal.options.get(idx);
                TowerClient.creativeAdd(new TowerCard.Reference(bindingModal.card.id(), option.variant(), option.variantTitle()));
                bindingModal = null;
                playClick();
                return true;
            }
        }
        return true;
    }

    private boolean handleSidebarClick(double x, double y) {
        if (!inside(x, y, sidebarLeft, sidebarTop, sidebarWidth, sidebarHeight)) return false;
        int barW = sidebarContentHeight > sidebarHeight ? 6 : 0;
        if (x >= sidebarLeft + sidebarWidth - barW) return true;
        for (var item : sidebarItems) {
            int rowY = sidebarTop + item.y() - sidebarScroll;
            if (y >= rowY && y < rowY + SIDEBAR_ROW_HEIGHT) {
                if (!selectedCategoryId.equals(item.category().id())) {
                    selectedCategoryId = item.category().id();
                    gridScroll = 0;
                    rebuildGrid();
                }
                playClick();
                return true;
            }
        }
        return true;
    }

    private boolean handleScrollbarClick(double x, double y) {
        if (gridContentHeight <= gridHeight) return false;
        int barX = gridLeft + gridWidth + 4;
        if (!inside(x, y, barX, gridTop, 10, gridHeight)) return false;
        int thumb = Math.max(16, gridHeight * gridHeight / gridContentHeight);
        int max = Math.max(1, gridContentHeight - gridHeight);
        int thumbTop = gridTop + (gridHeight - thumb) * gridScroll / max;
        gridScrollGrab = y >= thumbTop && y < thumbTop + thumb ? y - thumbTop : thumb / 2d;
        draggingGridScroll = true;
        dragGridScroll(y);
        return true;
    }

    private boolean handleGridClick(double x, double y) {
        if (!inside(x, y, gridLeft, gridTop, gridWidth, gridHeight)) return false;
        for (var row : rows) {
            int rowTop = gridTop + row.y() - gridScroll;
            if (y < rowTop || y >= rowTop + row.height()) continue;
            for (var cell : row.cells()) {
                int cx = gridLeft + cell.column() * (cardWidth + CARD_GAP);
                if (x < cx || x >= cx + cardWidth) continue;
                int btnX = cx + 8;
                int btnY = rowTop + row.height() - 26;
                int btnW = cardWidth - 16;
                int btnH = 19;
                if (!inside(x, y, btnX, btnY, btnW, btnH)) return false;

                if (cell.entry().card().milk() && cell.heldCount() > 0) {
                    int half = (btnW - 4) / 2;
                    if (x < btnX + half) {
                        TowerClient.creativeAdd(TowerCard.milkCard().reference());
                        playClick();
                        return true;
                    }
                    if (x >= btnX + half + 4) {
                        TowerClient.creativeRemove(TowerCard.MILK_ID);
                        playClick();
                        return true;
                    }
                    return false;
                }
                if (cell.requiresBinding() && cell.heldCount() > 0) {
                    int half = (btnW - 4) / 2;
                    if (x < btnX + half) {
                        openBindingModal(cell);
                        playClick();
                        return true;
                    }
                    if (x >= btnX + half + 4) {
                        TowerClient.creativeRemove(cell.entry().card().id());
                        playClick();
                        return true;
                    }
                    return false;
                }
                if (cell.heldCount() > 0) {
                    TowerClient.creativeRemove(cell.entry().card().id());
                } else if (cell.requiresBinding()) {
                    openBindingModal(cell);
                } else {
                    TowerClient.creativeAdd(cell.entry().card().reference());
                }
                playClick();
                return true;
            }
        }
        return false;
    }

    private void dragGridScroll(double y) {
        int thumb = Math.max(16, gridHeight * gridHeight / Math.max(1, gridContentHeight));
        int max = Math.max(0, gridContentHeight - gridHeight);
        gridScroll = Math.clamp((int) Math.round((y - gridTop - gridScrollGrab) * max / Math.max(1, gridHeight - thumb)), 0, max);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (bindingModal != null) return true;
        if (draggingGridScroll) {
            dragGridScroll(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean wasDragging = draggingGridScroll;
        draggingGridScroll = false;
        if (bindingModal != null) return true;
        return wasDragging || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (bindingModal != null) {
            int max = Math.max(0, bindingModal.options.size() * MODAL_ROW_STEP - modalListHeight());
            bindingModal.scroll = Math.clamp(bindingModal.scroll - (int) (vertical * MODAL_ROW_STEP), 0, max);
            return true;
        }
        if (inside(x, y, sidebarLeft, sidebarTop, sidebarWidth, sidebarHeight)) {
            int max = Math.max(0, sidebarContentHeight - sidebarHeight);
            sidebarScroll = Math.clamp(sidebarScroll - (int) (vertical * SIDEBAR_ROW_STEP), 0, max);
            return true;
        }
        if (inside(x, y, gridLeft, gridTop, gridWidth + 14, gridHeight)) {
            int max = Math.max(0, gridContentHeight - gridHeight);
            gridScroll = Math.clamp(gridScroll - (int) (vertical * 32), 0, max);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            if (bindingModal != null) {
                bindingModal = null;
                playClick();
                return true;
            }
            onClose();
            return true;
        }
        if (bindingModal != null) return true;
        if (event.key() == GLFW.GLFW_KEY_F && minecraft != null) {
            var window = minecraft.getWindow();
            boolean control = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
            if (control && searchBox != null) {
                setFocused(searchBox);
                searchBox.setFocused(true);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        int ellipsis = font.width("…");
        if (ellipsis >= maxWidth) return "";
        return font.plainSubstrByWidth(text, maxWidth - ellipsis) + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
