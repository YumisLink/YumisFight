package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.AwakeningTower;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.api.DungeonCatalog;
import cn.yumis.fight.modules.dungeons.api.PracticeRoom;
import cn.yumis.fight.modules.dungeons.api.TowerClimbRules;
import cn.yumis.fight.modules.presentation.api.RichText;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.RichTextDrawing;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Map.entry;

/** A local entrance announcement driven by the committed dungeon and floor. */
public final class DungeonEntryTitle {
    private static final String KEY = "hud.yumis_dungeon_and_fight.dungeon_entry.";
    private static final long OPENING_MS = 2100;
    private static final long DURATION_MS = 4600;
    private static final Map<String, Palette> PALETTES = Map.ofEntries(
        entry("goblin_camp", new Palette(0xe6fab4, 0x80b64f)),
        entry("dragon_tower", new Palette(0xffefba, 0xcf8f47)),
        entry("castle_palace", new Palette(0xd5f9ff, 0x5ca7d9)),
        entry("gbl_church", new Palette(0xf1e4ff, 0xad8bd7)),
        entry("behemoth_forbidden", new Palette(0xfcd6e1, 0xbf6f8a)),
        entry("dark_city", new Palette(0xe4deff, 0x745bc8)),
        entry("skasa", new Palette(0xe5faff, 0x80c9f2)),
        entry("moonlight_tavern", new Palette(0xffe7c8, 0xc67758)),
        entry("hamelin", new Palette(0xf3e6b4, 0xa39761)),
        entry("pursuit", new Palette(0xffdfd6, 0xe46755)),
        entry("sea_train", new Palette(0xbfffea, 0x4ab8a6)),
        entry("time_rift", new Palette(0xddf5ff, 0x6981ee)),
        entry("mysterious_awareness", new Palette(0xffe0ff, 0xe080c5)),
        entry("grandi", new Palette(0xfff6bb, 0xeeaa43)),
        entry("central_park", new Palette(0xffe3ee, 0xc772ad)),
        entry("writhing_castle", new Palette(0xe2eed4, 0x94aa7e)),
        entry("awakening_tower_root", new Palette(0xd2ffd4, 0x5cba6c)),
        entry("awakening_tower_forge", new Palette(0xffe4a8, 0xf07741)),
        entry("awakening_tower_sluice", new Palette(0xc9faff, 0x43bbda)),
        entry("awakening_tower_sand_market", new Palette(0xfff1cd, 0xd3a063)),
        entry("awakening_tower_frost", new Palette(0xf5fcff, 0x91aaf4)),
        entry("awakening_tower_orrery", new Palette(0xede0ff, 0x9870ec)),
        entry("awakening_tower_seal", new Palette(0xffd9f7, 0xbc61a4)),
        entry("awakening_tower_theater", new Palette(0xffe1dc, 0xd9617f)),
        entry("practice_mob", new Palette(0xe8edf5, 0x92a9c2)),
        entry("practice_gold", new Palette(0xfffbd2, 0xe5bd4f)),
        entry("practice_currency", new Palette(0xcdfff7, 0x6ccfbd)),
        entry("treasure_forest", new Palette(0xe6ffc9, 0x8ec467)),
        entry("treasure_tower", new Palette(0xffeac8, 0xb488e6)),
        entry("treasure_palace", new Palette(0xfff7d9, 0xf5bd5e))
    );

    private record Palette(int top, int bottom) {
        Palette {
            top &= 0xffffff;
            bottom &= 0xffffff;
        }

        RichText title(String text) {
            return new RichText(List.of(new RichText.Span(text, List.of("#%06x".formatted(top), "#%06x".formatted(bottom)))));
        }
    }

    private record Entry(UUID instance, int floorOrRegion, int floorStart) {}
    private record Banner(String plainTitle, FormattedCharSequence title, FormattedCharSequence subtitle,
                          int lineColor, boolean abyss, long startedAt) {}
    private static Entry entry;
    private static Banner banner;

    private DungeonEntryTitle() {}

    public static boolean update(Dungeon dungeon, String instance, boolean abyss) {
        if (dungeon == null || !dungeon.id.toString().equals(instance) || !DungeonActions.inDungeonDimension()
            || dungeon.treasure != null && dungeon.treasure.region == 0) {
            clear();
            return false;
        }
        int floor = dungeon.tower == null ? 0 : Math.max(1, dungeon.wave);
        int section = dungeon.treasure == null ? floor : dungeon.treasure.region;
        int start = dungeon.tower == null ? 0 : Math.max(1, dungeon.tower.floorStarts);
        var next = new Entry(dungeon.id, section, start);
        if (next.equals(entry)) return false;
        entry = next;
        banner = create(dungeon, floor, abyss);
        return true;
    }

    public static void clear() {
        entry = null;
        banner = null;
    }

    static boolean opening() {
        return banner != null && ClientUi.now() - banner.startedAt() < OPENING_MS;
    }

    private static Banner create(Dungeon dungeon, int floor, boolean abyss) {
        String paletteId = dungeon.dungeon;
        Component title;
        Component subtitle = Component.empty();
        if (dungeon.treasure != null) {
            var treasure = dungeon.treasure;
            paletteId = "treasure_" + treasure.theme;
            title = Component.translatable(KEY + "treasure", treasure.region);
            subtitle = Component.translatable(KEY + "treasure." + treasure.theme);
        } else if (dungeon.practice != null) {
            var room = dungeon.practice;
            title = room.kind() == PracticeRoom.Kind.MATERIAL
                ? Component.translatable(KEY + "practice.material", room.name()) : Component.literal(room.name());
        } else {
            String name = dungeonName(dungeon.dungeon);
            if (dungeon.tower != null) {
                title = Component.translatable(KEY + "tower", name, floor);
                subtitle = towerEffect(AwakeningTower.find(dungeon.dungeon));
            } else if (abyss) {
                title = Component.translatable(KEY + "abyss", name);
                subtitle = Component.translatable(KEY + "abyss.hint");
            } else {
                title = Component.literal(name);
                subtitle = dungeon.waystone == null ? Component.literal(dungeon.difficulty.title())
                    : Component.translatable(KEY + "waystone", dungeon.waystone.stone.tier, dungeon.waystone.monsterLevel);
            }
        }
        Palette palette = PALETTES.get(paletteId);
        if (palette == null) {
            float hue = Math.floorMod(paletteId.hashCode(), 360) / 360f;
            palette = new Palette(Mth.hsvToRgb(hue, .19f, 1), Mth.hsvToRgb(hue, .54f, .88f));
        }
        String plainTitle = title.getString();
        var gradient = palette.title(plainTitle).component().getVisualOrderText();
        return new Banner(plainTitle, gradient, subtitle.getVisualOrderText(), palette.bottom(), abyss, ClientUi.now());
    }

    private static String dungeonName(String id) {
        try {
            return DungeonCatalog.current().dungeon(id).name();
        } catch (IllegalArgumentException unknownDungeon) {
            return id;
        }
    }

    private static Component towerEffect(AwakeningTower tower) {
        String key = KEY + "tower." + tower.template();
        return switch (tower) {
            case ROOT -> Component.translatable(key, percent(1 - TowerClimbRules.MOVEMENT_FACTOR));
            case FORGE -> Component.translatable(key, percent(TowerClimbRules.FIRE_HEALTH_FRACTION));
            case SLUICE -> Component.translatable(key, percent(1 - TowerClimbRules.ACTION_SPEED_FACTOR));
            case SAND_MARKET -> Component.translatable(key, percent(1 - TowerClimbRules.MANA_RECOVERY_FACTOR));
            case FROST -> Component.translatable(key, percent(TowerClimbRules.FROST_DAMAGE_FRACTION));
            case ORRERY -> Component.translatable(key, percent(TowerClimbRules.enemyHealthFactor(tower) - 1),
                percent(TowerClimbRules.enemyAttackFactor(tower) - 1));
            case SEAL -> Component.translatable(key, percent(1 - TowerClimbRules.COOLDOWN_RECOVERY_FACTOR));
            case THEATER -> Component.translatable(key);
        };
    }

    private static String percent(double fraction) {
        return BigDecimal.valueOf(fraction * 100).stripTrailingZeros().toPlainString() + "%";
    }

    public static void render(GuiGraphicsExtractor graphics) {
        var gui = Minecraft.getInstance().gui;
        if (gui.hud.isHidden() || gui.screen() instanceof TowerCardScreen || !DungeonActions.inDungeonDimension()) return;
        draw(graphics, Math.round(graphics.guiHeight() * .43f), 3.25f, 1.14f);
    }

    static boolean renderCardHeader(GuiGraphicsExtractor graphics, int bottom) {
        int divider = bottom - 14;
        float scale = Math.min(2f, (divider - 6f) / UiFonts.font().lineHeight);
        return scale >= 1 && draw(graphics, divider, scale, .9f);
    }

    private static boolean draw(GuiGraphicsExtractor graphics, int divider, float titleScale, float subtitleScale) {
        if (banner == null) return false;
        long elapsed = ClientUi.now() - banner.startedAt();
        if (elapsed >= DURATION_MS) return false;
        float opacity = Math.clamp(Math.min(elapsed / 300f, (DURATION_MS - elapsed) / 850f), 0f, 1f);
        int alpha = Math.round(opacity * 255);
        if (alpha == 0) return true;
        int width = graphics.guiWidth(), center = width / 2;
        var font = UiFonts.font();
        var title = banner.title();
        var subtitle = banner.subtitle();
        int titleWidth = font.width(title), subtitleWidth = font.width(subtitle);
        titleScale = Math.min(titleScale, width * .86f / Math.max(1, titleWidth));
        subtitleScale = Math.min(subtitleScale, width * .9f / Math.max(1, subtitleWidth));
        int half = Math.min((width - 12) / 2, Math.max(Math.round(titleWidth * titleScale / 2 + 18),
            Math.round(width * (banner.abyss() ? .39f : .23f))));
        drawDivider(graphics, center, divider, half, alpha, banner.lineColor());

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(center, divider - 6 - font.lineHeight * titleScale);
        pose.scale(titleScale, titleScale);
        int x = -titleWidth / 2;
        graphics.text(font, banner.plainTitle(), x + 1, 1, (alpha * 3 / 4) << 24 | 0x100b17, false);
        RichTextDrawing.line(graphics, font, title, x, 0, alpha << 24 | 0xffffff);
        pose.popMatrix();

        if (subtitleWidth > 0) {
            pose.pushMatrix();
            pose.translate(center, divider + 5);
            pose.scale(subtitleScale, subtitleScale);
            graphics.text(font, subtitle, -subtitleWidth / 2, 0, alpha << 24 | 0xf5edf4, true);
            pose.popMatrix();
        }
        return true;
    }

    private static void drawDivider(GuiGraphicsExtractor graphics, int center, int y, int half, int alpha, int color) {
        int tapered = half / 3;
        graphics.fill(center - half + tapered, y + 1, center + half - tapered, y + 3, (alpha / 3) << 24 | 0x100b17);
        graphics.fill(center - half + tapered, y, center + half - tapered, y + 1, alpha << 24 | color);
        int segments = 12;
        for (int i = 0; i < segments; i++) {
            int from = half - tapered * (i + 1) / segments;
            int to = half - tapered * i / segments;
            int tint = (alpha * (i + 1) / segments) << 24 | color;
            graphics.fill(center - to, y, center - from, y + 1, tint);
            graphics.fill(center + from, y, center + to, y + 1, tint);
        }
        graphics.fill(center - 2, y - 1, center + 3, y + 2, alpha << 24 | color);
        graphics.fill(center - 1, y - 2, center + 2, y + 3, alpha << 24 | 0xfff5e5);
    }
}
