package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.ThemeDomain;
import cn.yumis.fight.modules.presentation.api.client.ThemedButton;
import cn.yumis.fight.modules.presentation.api.client.ThemedScreen;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class DungeonDefeatScreen extends ThemedScreen {
    private final long generation;
    private long retryAt;

    private DungeonDefeatScreen(long generation) {
        super(Component.literal("你已倒下"), ThemeDomain.MENU);
        this.generation = generation;
    }

    public static void update(boolean ready, Life life) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var screen = mc.gui.screen();
        if (ready && life.dungeonDefeated) {
            if (!(screen instanceof DungeonDefeatScreen defeat) || defeat.generation != life.generation) {
                if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
                mc.gui.setScreen(new DungeonDefeatScreen(life.generation));
            }
        } else if (screen instanceof DungeonDefeatScreen) {
            mc.gui.setScreen(null);
        }
    }

    @Override protected void init() {
        int x = width / 2, y = height / 2;
        addRenderableWidget(Button.builder(Component.literal("返回副本出生点"), b -> choose("return"))
            .bounds(x - 123, y + 10, 119, 22).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("退出副本"), b -> choose("leave"))
            .bounds(x + 4, y + 10, 119, 22).build(ThemedButton::new));
    }

    private void choose(String kind) {
        long now = ClientUi.now();
        if (now < retryAt) return;
        retryAt = now + 1000;
        var choice = ClientUi.current().operation(kind);
        choice.addProperty("generation", generation);
        ClientUi.current().send("dungeonDefeat", choice);
    }

    @Override protected void drawBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x90501620);
        UiTheme.panel(g, width / 2 - 135, height / 2 - 38, 270, 82);
    }

    @Override protected void drawContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        var font = UiFonts.font();
        String title = getTitle().getString();
        g.text(font, title, (width - font.width(title)) / 2, height / 2 - 22, UiTheme.GOLD, true);
        super.drawContents(g, mouseX, mouseY, delta);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }
}
