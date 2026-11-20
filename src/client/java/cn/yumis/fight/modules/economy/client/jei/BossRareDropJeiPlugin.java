package cn.yumis.fight.modules.economy.client.jei;

import cn.yumis.fight.modules.dungeons.api.CastleEnemy;
import cn.yumis.fight.modules.dungeons.api.DungeonCatalog;
import cn.yumis.fight.modules.dungeons.api.GblEnemyKind;
import cn.yumis.fight.modules.dungeons.api.PalaceEnemyKind;
import cn.yumis.fight.modules.economy.api.ChestDefinitions;
import cn.yumis.fight.modules.economy.api.ChestRewards;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.equipment.api.NamedEquipment;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.platform.ItemRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.ScrollbarVisibility;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@JeiPlugin
public final class BossRareDropJeiPlugin implements IModPlugin {
    private static final String TEXT = "jei." + ItemData.NS + ".boss_rare_drops";
    private static final IRecipeType<BossDrops> TYPE = IRecipeType.create(ItemData.NS, "boss_rare_drops", BossDrops.class);
    private static final Map<String, Identifier> DUNGEON_BOSSES = Map.of(
        "gbl_church", Identifier.fromNamespaceAndPath(ItemData.NS, GblEnemyKind.ASOR.id),
        "castle_palace", Identifier.fromNamespaceAndPath(ItemData.NS, PalaceEnemyKind.SIEGHART.id)
    );

    @Override
    public Identifier getPluginUid() {
        return TYPE.getUid();
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        for (String id : EquipmentItems.GEAR_IDS) {
            registration.registerSubtypeInterpreter(ItemRegistry.item(id, 1).getItem(), (stack, context) -> {
                var gear = EquipmentItems.gear(stack);
                return gear != null && NamedEquipment.find(gear.template) != null ? gear.template : null;
            });
        }
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        // JEI can start before the mod's rules arrive, and the server can reload them in play.
        registration.addSimpleRecipeManagerPlugin(TYPE, new Drops(registration.getJeiHelpers().getStackHelper()));
    }

    private record BossDrops(Boss boss, Map<ResultKey, ItemStack> items) {}
    private record ResultKey(Object ingredientUid, int count) {}

    private record Boss(String id, @Nullable EntityType<?> type) {
        static Boss fromSource(String source) {
            var entityId = source.contains(":") ? Identifier.tryParse(source) : DUNGEON_BOSSES.get(source);
            if (entityId != null) {
                var entity = BuiltInRegistries.ENTITY_TYPE.get(entityId);
                if (entity.isPresent()) return new Boss(entityId.toString(), entity.get().value());
            }
            return new Boss(source, null);
        }

        Component name() {
            if (type != null) return type.getDescription();
            String dungeonName;
            try {
                dungeonName = DungeonCatalog.current().dungeon(id).name();
            } catch (IllegalArgumentException unknownDungeon) {
                dungeonName = id;
            }
            return Component.translatable(TEXT + ".dungeon_boss", dungeonName);
        }
    }

    private static final class Drops implements ISimpleRecipeManagerPlugin<BossDrops> {
        private final IStackHelper stacks;
        private Map<String, ChestDefinitions.SpecialPool> pools;
        private List<BossDrops> drops = List.of();

        Drops(IStackHelper stacks) {
            this.stacks = stacks;
        }

        @Override
        public boolean isHandledInput(ITypedIngredient<?> ingredient) {
            return false;
        }

        @Override
        public List<BossDrops> getRecipesForInput(ITypedIngredient<?> ingredient) {
            return List.of();
        }

        @Override
        public boolean isHandledOutput(ITypedIngredient<?> ingredient) {
            return !getRecipesForOutput(ingredient).isEmpty();
        }

        @Override
        public List<BossDrops> getRecipesForOutput(ITypedIngredient<?> ingredient) {
            var stack = ingredient.getItemStack();
            if (stack.isEmpty()) return List.of();
            Object uid = stacks.getUidForStack(stack.get(), UidContext.Recipe);
            return getAllRecipes().stream()
                .filter(group -> group.items().keySet().stream().anyMatch(key -> key.ingredientUid().equals(uid)))
                .toList();
        }

        @Override
        public List<BossDrops> getAllRecipes() {
            var current = ChestDefinitions.current().bossRarePools;
            if (current != pools) {
                Map<Boss, Map<ResultKey, ItemStack>> byBoss = new LinkedHashMap<>();
                var random = new Random(0);
                current.forEach((source, pool) -> {
                    if (pool.chance() <= 0) return;
                    var items = byBoss.computeIfAbsent(Boss.fromSource(source), boss -> new LinkedHashMap<>());
                    for (var entry : pool.entries()) {
                        if (entry.weight() <= 0) continue;
                        ItemStack stack = ChestRewards.item(entry, random, "");
                        Object uid = stacks.getUidForStack(stack, UidContext.Recipe);
                        items.putIfAbsent(new ResultKey(uid, stack.getCount()), stack);
                    }
                });
                drops = byBoss.entrySet().stream().filter(group -> !group.getValue().isEmpty())
                    .map(group -> new BossDrops(group.getKey(), Collections.unmodifiableMap(group.getValue())))
                    .toList();
                pools = current;
            }
            return drops;
        }
    }

    private static final class Category extends AbstractRecipeCategory<BossDrops> {
        private static final int CONTENT_TOP = 20;
        private static final int GRID_COLUMNS = 4;

        Category(IGuiHelper gui) {
            super(TYPE, Component.translatable(TEXT),
                gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.NETHER_STAR)), 176, 76);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, BossDrops group, IFocusGroup focuses) {
            for (ItemStack stack : group.items().values()) builder.addOutputSlot().add(stack);
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, BossDrops group, IFocusGroup focuses) {
            var name = group.boss().name();
            builder.addText(name, getWidth() - 12, 18).setPosition(6, 3).setColor(0xff333333).setTooltip(name);

            var level = Minecraft.getInstance().level;
            var type = group.boss().type();
            if (level != null && type != null && type.create(level, EntitySpawnReason.COMMAND) instanceof LivingEntity entity) {
                if (entity instanceof CastleEnemy guard) guard.equipGuardGear();
                builder.addDrawableWidget(new BossModel(entity)).setPosition(6, CONTENT_TOP);
            }

            var slots = builder.getRecipeSlots().getSlots(RecipeIngredientRole.OUTPUT);
            if (slots.isEmpty()) return;
            int columns = Math.min(GRID_COLUMNS, slots.size());
            int rows = Math.min(2, Math.ceilDiv(slots.size(), columns));
            var grid = builder.addScrollGridWidget(slots, columns, rows).setScrollbarVisibility(ScrollbarVisibility.AUTO_HIDE);
            grid.setPosition(78, CONTENT_TOP + (BossModel.HEIGHT - grid.getHeight()) / 2);
        }
    }

    private record BossModel(LivingEntity entity) implements IDrawable {
        private static final int WIDTH = 64;
        private static final int HEIGHT = 52;

        @Override public int getWidth() { return WIDTH; }
        @Override public int getHeight() { return HEIGHT; }

        @Override
        public void draw(GuiGraphicsExtractor graphics, int x, int y) {
            // The inventory renderer takes screen coordinates. JEI positions drawables with its GUI pose.
            var bounds = new ScreenRectangle(x, y, WIDTH, HEIGHT).transformAxisAligned(graphics.pose());
            int scale = Math.max(1, (int) Math.min((bounds.width() - 12) / entity.getBbWidth(),
                (bounds.height() - 10) / entity.getBbHeight()));
            InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, bounds.left(), bounds.top(), bounds.right(),
                bounds.bottom(), scale, 0, .4f, .05f, entity);
        }
    }
}
