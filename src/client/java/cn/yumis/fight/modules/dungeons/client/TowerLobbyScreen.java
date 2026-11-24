package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class TowerLobbyScreen extends ThemedScreen {
    private final TowerClient.LobbyView view;
    private int left, top, boxHeight;

    TowerLobbyScreen(TowerClient.LobbyView view) {
        super(Component.literal(view.title()), ThemeDomain.MENU);
        this.view = view;
    }

    @Override protected void init() {
        boolean chooseDeck = !view.deck().isEmpty() && !view.accepted();
        boxHeight = 83 + Math.min(8, view.members().size()) * 13 + (chooseDeck ? 51 : 0);
        left = width / 2 - 120;
        top = (height - boxHeight) / 2;
        String action = view.leader() ? "start" : "accept";
        String text;
        if (view.leader()) {
            if (view.members().isEmpty()) text = "开始爬塔";
            else {
                int ready = 1 + (int) view.members().stream().filter(TowerClient.Member::ready).count();
                int total = 1 + view.members().size();
                text = "开始爬塔（已就绪 " + ready + "/" + total + "）";
            }
        } else {
            text = view.accepted() ? "已确认" : "确认参战";
        }
        int confirmY = top + boxHeight - (chooseDeck ? 54 : 30);
        var confirm = Button.builder(Component.literal(chooseDeck ? "清空并重新选牌" : text),
                b -> TowerClient.lobbyAction(view, action, false))
            .bounds(left + 12, confirmY, chooseDeck ? 104 : 136, 20).build(ThemedButton::new);
        confirm.active = view.leader() || !view.accepted();
        addRenderableWidget(confirm);
        if (chooseDeck) addRenderableWidget(Button.builder(Component.literal("保留卡牌入塔"),
            b -> TowerClient.lobbyAction(view, action, true))
            .bounds(left + 124, confirmY, 104, 20).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal(view.leader() ? "取消" : "退出报名"),
            b -> TowerClient.lobbyAction(view, view.leader() ? "cancel" : "decline", false))
            .bounds(left + (chooseDeck ? 68 : 154), top + boxHeight - 30, chooseDeck ? 104 : 74, 20).build(ThemedButton::new));
    }

    @Override protected void drawBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, UiTheme.OVERLAY);
        UiTheme.panel(graphics, left, top, 240, boxHeight);
    }

    @Override protected void drawContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var type = UiFonts.font();
        graphics.text(type, getTitle(), left + 12, top + 10, UiTheme.GOLD, true);
        graphics.text(type, view.leader() ? (view.members().isEmpty() ? "进入觉醒塔" : "向附近队友发起参战确认") : "队长邀请你一起爬塔",
            left + 12, top + 28, UiTheme.TEXT, false);
        int y = top + 42;
        for (var member : view.members().stream().limit(8).toList()) {
            graphics.text(type, member.name(), left + 12, y, UiTheme.TEXT, false);
            graphics.text(type, member.ready() ? "已确认" : "待确认", left + 174, y,
                member.ready() ? UiTheme.SUCCESS : UiTheme.MUTED, false);
            y += 13;
        }
        if (!view.deck().isEmpty() && !view.accepted()) {
            graphics.text(type, "重新选牌将清空你在塔内获得的全部增益和卡牌。", left + 12, y, UiTheme.GOLD, false);
            graphics.text(type, "保留卡牌则本次爬塔全程无法选牌。", left + 12, y + 13, UiTheme.TEXT, false);
        }
        super.drawContents(graphics, mouseX, mouseY, delta);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }
}
