package cn.yumis.fight.modules.presentation.api.client;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Shared crosshair world-interaction selector (aim target -> right HUD list -> scroll to choose -> right-click to confirm).
 * All world-space interactive actors (NPCs, treasure dice, future world objects) should register a provider here.
 */
public final class WorldInteractionMenu {
    public static final String DEFAULT_HINT = "滚轮选择 · 右键确认";

    public record Option(String id, String label, String hint, Runnable action) {
        public Option(String id, String label, Runnable action) {
            this(id, label, DEFAULT_HINT, action);
        }
    }

    public record Target(Object key, String title, String subtitle, List<Option> options) {
        public Target(Object key, String title, List<Option> options) {
            this(key, title, "", options);
        }
    }

    @FunctionalInterface
    public interface Provider {
        Target query();
    }

    private record Entry(String id, int priority, Provider provider) {}

    private static final List<Entry> PROVIDERS = new ArrayList<>();
    private static BooleanSupplier blocked = () -> false;
    private static Target active;
    private static Object activeKey;
    private static int selected;
    private static Object world;

    private WorldInteractionMenu() {}

    public static void blocked(BooleanSupplier value) {
        blocked = Objects.requireNonNull(value);
    }

    /** Higher priority providers win when multiple world targets overlap. */
    public static void register(String id, int priority, Provider provider) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(provider);
        PROVIDERS.removeIf(entry -> entry.id().equals(id));
        PROVIDERS.add(new Entry(id, priority, provider));
        PROVIDERS.sort(Comparator.comparingInt(Entry::priority).reversed().thenComparing(Entry::id));
    }

    public static void unregister(String id) {
        PROVIDERS.removeIf(entry -> entry.id().equals(id));
    }

    public static void clear() {
        active = null;
        activeKey = null;
        selected = 0;
        world = null;
    }

    public static boolean active() {
        return active != null;
    }

    private static boolean available() {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && mc.player.isAlive()
            && mc.gui.screen() == null && ClientUi.current().ready() && !blocked.getAsBoolean();
    }

    private static void refresh() {
        if (!available()) {
            active = null;
            activeKey = null;
            selected = 0;
            return;
        }
        Target found = null;
        for (Entry entry : PROVIDERS) {
            Target candidate = entry.provider().query();
            if (candidate != null && candidate.options() != null && !candidate.options().isEmpty()) {
                found = candidate;
                break;
            }
        }
        if (found == null) {
            active = null;
            activeKey = null;
            selected = 0;
            return;
        }
        if (!Objects.equals(activeKey, found.key())) {
            activeKey = found.key();
            selected = 0;
        }
        active = found;
        selected = Math.clamp(selected, 0, active.options().size() - 1);
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (world != mc.level) {
            clear();
            world = mc.level;
        }
        refresh();
    }

    public static void scroll(InputEvent.MouseScrollingEvent event) {
        refresh();
        if (active == null) return;
        event.setCanceled(true);
        int step = Integer.signum((int) Math.signum(event.getAccumulatedScrollY()));
        if (step == 0) return;
        int count = active.options().size();
        selected = Math.floorMod(selected - step, count);
    }

    public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        refresh();
        if (active == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Option chosen = active.options().get(Math.clamp(selected, 0, active.options().size() - 1));
        if (chosen.action() != null) chosen.action().run();
    }

    public static void render(GuiGraphicsExtractor g) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;
        refresh();
        if (active == null) return;
        drawPanel(g, active, selected);
    }

    public static void drawPanel(GuiGraphicsExtractor g, Target target, int selectedIndex) {
        var font = UiFonts.font();
        var options = target.options();
        int clamped = Math.clamp(selectedIndex, 0, options.size() - 1);
        Option current = options.get(clamped);
        String hint = current.hint() == null || current.hint().isBlank() ? DEFAULT_HINT : current.hint();
        boolean hasSubtitle = target.subtitle() != null && !target.subtitle().isBlank();

        int needed = Math.max(174, font.width(target.title()) + 20);
        needed = Math.max(needed, font.width(hint) + 18);
        if (hasSubtitle) needed = Math.max(needed, font.width(target.subtitle()) + 20);
        for (Option option : options) {
            needed = Math.max(needed, font.width("› " + option.label()) + 24);
        }
        int w = Math.min(Math.min(needed, 248), Math.max(120, g.guiWidth() - 16));
        int subtitleHeight = hasSubtitle ? 13 : 0;
        int h = 44 + subtitleHeight + options.size() * 18;
        int x = g.guiWidth() - w - 8;
        int y = Math.max(8, (g.guiHeight() - h) / 2);

        UiTheme.hud(g, x, y, w, h);
        g.text(font, font.plainSubstrByWidth(target.title(), w - 16), x + 8, y + 8, UiTheme.HUD_GOLD, false);
        if (hasSubtitle) {
            g.text(font, font.plainSubstrByWidth(target.subtitle(), w - 16), x + 8, y + 20, UiTheme.HUD_MUTED, false);
        }
        int listTop = y + 25 + subtitleHeight;
        for (int i = 0; i < options.size(); i++) {
            int row = listTop + i * 18;
            if (i == clamped) g.fill(x + 4, row - 2, x + w - 4, row + 14, UiTheme.SELECTED);
            String text = (i == clamped ? "› " : "  ") + options.get(i).label();
            g.text(font, font.plainSubstrByWidth(text, w - 16), x + 10, row,
                i == clamped ? UiTheme.HUD_GOLD : UiTheme.HUD_TEXT, false);
        }
        g.text(font, font.plainSubstrByWidth(hint, w - 16), x + 8, y + h - 13, UiTheme.HUD_MUTED, false);
    }
}
