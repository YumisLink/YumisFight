package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.TowerCardDraft;
import cn.yumis.fight.modules.dungeons.api.TowerClimbRules;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.ThemeDomain;
import cn.yumis.fight.modules.presentation.api.client.ThemedButton;
import cn.yumis.fight.modules.presentation.api.client.ThemedScreen;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

final class TowerCardScreen extends ThemedScreen {
    private static final int GAP = 10;
    private static final int COUNT = TowerCardDraft.CARDS_PER_OFFER;
    private static final int CHOICE_FOOTER = 32;
    private static final int WHITE = 0xffffffff;
    private final boolean steleMode;
    private final Button[] choices = new Button[COUNT], refreshes = new Button[COUNT];
    private final TowerCardFace[] faces = new TowerCardFace[COUNT];
    private final long[] refreshed = new long[COUNT];
    private final int[] scroll = new int[COUNT];
    private List<TowerCardDraft.Slot> shown = List.of();
    private boolean shownCareerOffer;
    private int shownRemaining;
    private int left, top, cardWidth, cardHeight, version;

    TowerCardScreen() { this(false); }

    TowerCardScreen(boolean steleMode) {
        super(Component.literal(steleMode ? "补选职业神话卡" : "觉醒塔选牌"), ThemeDomain.MENU);
        this.steleMode = steleMode;
    }

    boolean steleMode() { return steleMode; }

    @Override protected void init() {
        cardWidth = Math.clamp((width - (COUNT + 1) * GAP) / COUNT, 40, 174);
        cardHeight = Math.clamp(height - 100, 91, 166);
        left = (width - COUNT * cardWidth - (COUNT - 1) * GAP) / 2;
        top = (height - cardHeight - 40) / 2;
        for (int index = 0; index < COUNT; index++) {
            int slot = index, x = cardX(index);
            faces[index] = null;
            choices[index] = addRenderableWidget(Button.builder(Component.literal("选择"), b -> {
                if (steleMode) TowerClient.chooseCareer(slot, version);
                else TowerClient.choose(slot, version);
            }).bounds(x + 8, top + cardHeight - 27, cardWidth - 16, 19).build(ThemedButton::new));
            refreshes[index] = addRenderableWidget(Button.builder(Component.literal("刷新"), b -> {
                if (steleMode) TowerClient.refreshCareer(slot, version);
                else TowerClient.refresh(slot, version);
            }).bounds(x + 8, top + cardHeight + 6, cardWidth - 16, 19).build(ThemedButton::new));
        }
        if (steleMode) {
            addRenderableWidget(Button.builder(Component.literal("稍后再选"), b -> TowerClient.closeCareerStele())
                .bounds(width / 2 - 106, top + cardHeight + 39, 100, 18).build(ThemedButton::new));
            addRenderableWidget(Button.builder(Component.literal("直接进入觉醒塔"), b -> TowerClient.enterTowerFromCareerStele())
                .bounds(width / 2 + 6, top + cardHeight + 39, 100, 18).build(ThemedButton::new));
        } else {
            addRenderableWidget(Button.builder(Component.literal("退出爬塔"), b -> TowerClient.leave())
                .bounds(width / 2 - 50, top + cardHeight + 39, 100, 18).build(ThemedButton::new));
        }
        updateSlots();
    }

    @Override public void tick() {
        super.tick();
        if (steleMode || TowerClient.inRun()) updateSlots();
    }

    private void updateSlots() {
        var next = steleMode ? TowerClient.steleSlots() : TowerClient.slots();
        boolean careerOffer = steleMode || TowerClient.careerOffer();
        int remaining = steleMode ? TowerClient.steleRemaining() : 0;
        boolean switchedPhase = !shown.isEmpty() && !next.isEmpty()
            && (steleMode ? shownRemaining != remaining : shownCareerOffer != careerOffer);
        version = steleMode ? TowerClient.steleRevision() : TowerClient.revision();
        for (int index = 0; index < COUNT; index++) {
            boolean present = index < next.size();
            choices[index].visible = refreshes[index].visible = present;
            if (!present) continue;
            var slot = next.get(index);
            int left = slot.refreshesLeft();
            if (index >= shown.size() || !slot.equals(shown.get(index))) {
                faces[index] = null;
                scroll[index] = 0;
            }
            if (faces[index] == null) {
                faces[index] = new TowerCardFace(slot.card(), cardWidth);
                scroll[index] = Math.min(scroll[index], faces[index].maximumScroll(cardHeight, CHOICE_FOOTER));
            }
            if (switchedPhase || (index < shown.size() && left < shown.get(index).refreshesLeft())) refreshed[index] = ClientUi.now();
            refreshes[index].active = steleMode ? TowerClient.canRefreshCareer(index) : TowerClient.canRefresh(index);
            refreshes[index].setMessage(Component.literal("刷新 " + left + "/" + TowerCardDraft.REFRESHES_PER_CARD));
        }
        shown = next;
        shownCareerOffer = careerOffer;
        shownRemaining = remaining;
    }

    private int cardX(int index) { return left + index * (cardWidth + GAP); }

    @Override protected void drawBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xc20d111b);
    }

    @Override protected void drawContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!steleMode && !TowerClient.inRun()) return;
        if (steleMode) {
            int remaining = TowerClient.steleRemaining();
            String title = remaining > 1
                ? "觉醒塔石碑 · 补选职业神话卡（剩余 " + remaining + " 张）"
                : "觉醒塔石碑 · 补选职业神话卡";
            centered(graphics, title, width / 2, top - 22, WHITE);
        } else {
            String title = shown.isEmpty() ? "等待开战"
                : TowerClient.careerOffer() ? "第 " + TowerClient.floor() + " 层备战 · 职业神话卡"
                : TowerClient.finalOffer() ? "第 " + TowerClimbRules.FLOOR_COUNT + " 层通关 · 最后一张牌"
                : "第 " + TowerClient.floor() + " 层备战";
            if (!DungeonEntryTitle.renderCardHeader(graphics, top)) centered(graphics, title, width / 2, top - 22, WHITE);
            if (shown.isEmpty()) centered(graphics, "等待队友或同步选牌进度", width / 2, top + cardHeight / 2, UiTheme.MUTED);
        }
        for (int index = 0; index < shown.size(); index++) {
            int x = cardX(index);
            var face = faces[index];
            face.draw(graphics, x, top, cardHeight, CHOICE_FOOTER, scroll[index]);
            int glow = (int) Math.max(0, 115 - (ClientUi.now() - refreshed[index]) / 2);
            if (glow > 0) face.flash(graphics, x, top, cardHeight, glow);
        }
        super.drawContents(graphics, mouseX, mouseY, delta);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (y >= top + TowerCardFace.DESCRIPTION_TOP && y < top + cardHeight - CHOICE_FOOTER) {
            for (int index = 0; index < shown.size(); index++) {
                int left = cardX(index);
                if (x < left || x >= left + cardWidth) continue;
                var face = faces[index];
                int maximum = face.maximumScroll(cardHeight, CHOICE_FOOTER);
                scroll[index] = Math.clamp(scroll[index] - (int) Math.signum(vertical) * face.lineStep(), 0, maximum);
                return true;
            }
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    private static void centered(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        var font = UiFonts.font();
        graphics.text(font, text, x - font.width(text) / 2, y, color, false);
    }

    @Override public boolean shouldCloseOnEsc() { return steleMode; }
    @Override public void onClose() { if (steleMode) TowerClient.closeCareerStele(); else super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
}
