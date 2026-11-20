package cn.yumis.fight.modules.presentation.api.client;

import cn.yumis.fight.modules.presentation.api.PlayerPages;
import cn.yumis.fight.platform.ItemData;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Menu shortcuts and navigation callback owned by presentation. */
public final class MenuInput {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(ItemData.NS, "combat"));
    public static final KeyMapping MENU = new KeyMapping("key.ydnf.menu", GLFW.GLFW_KEY_I, CATEGORY);
    private static final Map<String, KeyMapping> PAGE_KEYS = new LinkedHashMap<>();
    private static Consumer<String> open = page -> {};

    private MenuInput() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(MENU);
        for (var page : PlayerPages.all()) {
            int defaultKey = switch (page.id()) {
                case "character" -> GLFW.GLFW_KEY_N;
                case "bag" -> GLFW.GLFW_KEY_L;
                default -> GLFW.GLFW_KEY_UNKNOWN;
            };
            var mapping = new KeyMapping("key.ydnf.page." + page.id(), defaultKey, CATEGORY);
            PAGE_KEYS.put(page.id(), mapping);
            event.register(mapping);
        }
    }

    public static void clearDefaultConflictsOnce(Minecraft client) {
        if (ClientSettings.menuShortcutDefaultsApplied) return;
        var character = PAGE_KEYS.get("character");
        var bag = PAGE_KEYS.get("bag");
        Set<String> clearedOptions = new HashSet<>();
        for (var mapping : client.options.keyMappings) {
            if (mapping == character || mapping == bag) continue;
            var key = mapping.getKey();
            if (key.equals(character.getDefaultKey()) || key.equals(bag.getDefaultKey())) {
                mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
                clearedOptions.add("key_" + mapping.getName() + ":" + mapping.saveString());
            }
        }
        if (!clearedOptions.isEmpty()) {
            KeyMapping.resetMapping();
            client.options.save();
            // Options.save() reports errors internally. Persist the marker after the new bindings are on disk.
            try {
                var savedOptions = new HashSet<>(Files.readAllLines(client.gameDirectory.toPath().resolve("options.txt")));
                if (!savedOptions.containsAll(clearedOptions)) {
                    LogUtils.getLogger().warn("Menu shortcut conflicts were not saved");
                    return;
                }
            } catch (IOException failure) {
                LogUtils.getLogger().warn("Unable to verify saved menu shortcuts", failure);
                return;
            }
        }
        ClientSettings.menuShortcutDefaultsApplied = true;
        ClientSettings.save();
    }

    public static void consumeClicks(BooleanSupplier canOpen) {
        while (MENU.consumeClick()) {
            if (canOpen.getAsBoolean()) open();
        }
        for (var entry : PAGE_KEYS.entrySet()) {
            while (entry.getValue().consumeClick()) {
                if (canOpen.getAsBoolean()) open(entry.getKey());
            }
        }
    }

    public static String pageShortcut(InputConstants.Key key) {
        for (var entry : PAGE_KEYS.entrySet()) {
            if (entry.getValue().isActiveAndMatches(key)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static boolean matchesPageShortcut(String pageId, InputConstants.Key key) {
        var mapping = PAGE_KEYS.get(pageId);
        return mapping != null && mapping.isActiveAndMatches(key);
    }

    public static String shortcutLabel(String pageId) {
        var mapping = PAGE_KEYS.get(pageId);
        return mapping == null || mapping.isUnbound() ? "" : mapping.getTranslatedKeyMessage().getString();
    }

    public static void navigation(Consumer<String> action) {open = Objects.requireNonNull(action);}
    public static void open() {open.accept(null);}
    public static void open(String pageId) {open.accept(Objects.requireNonNull(pageId));}
}
