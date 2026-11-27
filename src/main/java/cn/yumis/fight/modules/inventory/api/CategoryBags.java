package cn.yumis.fight.modules.inventory.api;

import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.platform.ItemRegistry;
import com.google.gson.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Predicate;

/** The three personal storage entitlements and their contents. Currency quantities are separate from item stacks. */
public final class CategoryBags {
    public static final int ITEM_SLOTS = 108;
    public enum Kind {
        CURRENCY("currency", "标准通货"),
        GEMS("gems", "宝石"),
        ESSENCES("essences", "精华");

        public final String id, title;
        Kind(String id, String title) { this.id = id; this.title = title; }
        public String product() { return "storage_" + id; }
        public String area() { return "storage/" + id; }
        public static Kind product(String id) {
            for (var kind : values()) if (kind.product().equals(id)) return kind;
            return null;
        }
        public static Kind area(String id) {
            for (var kind : values()) if (kind.area().equals(id)) return kind;
            return null;
        }
    }

    public record Stock(Kind kind, int slot, ItemStack item, long quantity) {}

    private static List<String> currencyIds = List.of();
    private static Set<String> gemIds = Set.of(), essenceIds = Set.of();

    public static void install(List<String> currencies, Collection<String> gems, Collection<String> essences) {
        currencyIds = List.copyOf(currencies);
        gemIds = Set.copyOf(gems);
        essenceIds = Set.copyOf(essences);
    }

    public static int currencySlot(String id) { return currencyIds.indexOf(id); }
    public static int size(Kind kind) { return kind == Kind.CURRENCY ? currencyIds.size() : ITEM_SLOTS; }
    public static ItemStack currency(int slot) { return ItemRegistry.item(currencyIds.get(slot), 1); }
    public static Kind kind(ItemStack stack) {
        if (stack.isEmpty() || !ItemData.own(stack)) return null;
        String id = ItemData.id(stack);
        if (gemIds.contains(id)) return Kind.GEMS;
        if (essenceIds.contains(id)) return Kind.ESSENCES;
        int slot = currencySlot(id);
        return slot >= 0 && ItemStack.isSameItemSameComponents(stack, currency(slot)) ? Kind.CURRENCY : null;
    }

    private final long[] quantities = new long[size(Kind.CURRENCY)];
    private final TrackedContainer currencyIcons = new TrackedContainer(quantities.length);
    private final TrackedContainer gems = new TrackedContainer(ITEM_SLOTS);
    private final TrackedContainer essences = new TrackedContainer(ITEM_SLOTS);
    private int unlocked;

    public int unlocks() { return unlocked; }
    public void unlocks(int value) { unlocked = value; }
    public boolean unlocked(Kind kind) { return (unlocked & (1 << kind.ordinal())) != 0; }
    public void unlock(Kind kind) {
        if (unlocked(kind)) throw new IllegalArgumentException("该背包已开通");
        unlocked |= 1 << kind.ordinal();
    }

    public TrackedContainer container(Kind kind) {
        return switch (kind) { case CURRENCY -> currencyIcons; case GEMS -> gems; case ESSENCES -> essences; };
    }
    public boolean owns(Container container) { return container == currencyIcons || container == gems || container == essences; }
    public long quantity(int slot) { return quantities[slot]; }
    public void quantity(int slot, long count) {
        if (count < 0 || !unlocked(Kind.CURRENCY) && count != 0) throw new IllegalArgumentException("通货袋数量无效");
        if (quantities[slot] == count && currencyIcons.getItem(slot).isEmpty() == (count == 0)) return;
        quantities[slot] = count;
        currencyIcons.setItem(slot, count == 0 ? ItemStack.EMPTY : currency(slot));
    }

    public int room(ItemStack stack) {
        Kind kind = kind(stack);
        if (kind == null || !unlocked(kind)) return 0;
        if (kind == Kind.CURRENCY) return (int)Math.min(Integer.MAX_VALUE, Long.MAX_VALUE - quantity(currencySlot(ItemData.id(stack))));
        int room = 0;
        var items = container(kind);
        for (int i = 0; i < ITEM_SLOTS; i++) {
            var target = items.getItem(i);
            if (target.isEmpty()) room += stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(target, stack)) room += Math.max(0, target.getMaxStackSize() - target.getCount());
        }
        return room;
    }

    /** Inserts eligible items into their paid category and leaves any remainder on the source. */
    public void insert(ItemStack stack) {
        Kind kind = kind(stack);
        if (kind == null || !unlocked(kind)) return;
        if (kind == Kind.CURRENCY) {
            int slot = currencySlot(ItemData.id(stack));
            int count = (int)Math.min(stack.getCount(), Long.MAX_VALUE - quantity(slot));
            quantity(slot, quantity(slot) + count);
            stack.shrink(count);
            return;
        }
        var items = container(kind);
        for (int i = 0; i < ITEM_SLOTS && !stack.isEmpty(); i++) {
            var target = items.getItem(i);
            if (!target.isEmpty() && ItemStack.isSameItemSameComponents(target, stack)) {
                int count = Math.min(stack.getCount(), Math.max(0, target.getMaxStackSize() - target.getCount()));
                if (count > 0) items.setItem(i, target.copyWithCount(target.getCount() + count));
                stack.shrink(count);
            }
        }
        for (int i = 0; i < ITEM_SLOTS && !stack.isEmpty(); i++) {
            if (items.getItem(i).isEmpty()) items.setItem(i, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
        }
    }

    public List<Stock> stock() {
        List<Stock> stock = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            if (!unlocked(kind)) continue;
            for (int slot = 0; slot < size(kind); slot++) {
                ItemStack item = container(kind).getItem(slot);
                if (!item.isEmpty()) stock.add(new Stock(kind, slot, item.copy(), kind == Kind.CURRENCY ? quantity(slot) : item.getCount()));
            }
        }
        return stock;
    }

    public int count(Predicate<ItemStack> accepts) {
        int count = 0;
        for (Kind kind : Kind.values()) {
            if (!unlocked(kind)) continue;
            var items = container(kind);
            for (int slot = 0; slot < size(kind); slot++) {
                var stack = items.getItem(slot);
                if (!stack.isEmpty() && accepts.test(stack)) {
                    long amount = kind == Kind.CURRENCY ? quantity(slot) : stack.getCount();
                    count += (int)Math.min(Integer.MAX_VALUE - count, amount);
                }
            }
        }
        return count;
    }

    public JsonObject summary() {
        var value = new JsonObject();
        value.addProperty("unlocked", unlocked);
        var counts = new JsonObject();
        for (int i = 0; i < quantities.length; i++) if (quantity(i) > 0) counts.addProperty(currencyIds.get(i), quantity(i));
        value.add("counts", counts);
        return value;
    }

    public void loadSummary(JsonObject value) {
        unlocked = value.get("unlocked").getAsInt();
        var counts = value.getAsJsonObject("counts");
        for (int i = 0; i < quantities.length; i++) quantity(i, counts.has(currencyIds.get(i)) ? counts.get(currencyIds.get(i)).getAsLong() : 0);
    }

    public JsonObject save(Player player) {
        var value = summary();
        for (Kind kind : List.of(Kind.GEMS, Kind.ESSENCES)) {
            if (!unlocked(kind)) continue;
            var slots = new JsonObject();
            for (int i = 0; i < ITEM_SLOTS; i++) {
                var item = container(kind).getItem(i);
                if (!item.isEmpty()) slots.add(Integer.toString(i), InventoryEdits.encode(player, item));
            }
            value.add(kind.id, slots);
        }
        return value;
    }

    public void load(Player player, JsonObject saved) {
        loadSummary(saved);
        for (Kind kind : List.of(Kind.GEMS, Kind.ESSENCES)) {
            var items = container(kind);
            items.clearContent();
            if (!unlocked(kind) || !saved.has(kind.id)) continue;
            for (var entry : saved.getAsJsonObject(kind.id).entrySet()) {
                items.setItem(Integer.parseInt(entry.getKey()), InventoryEdits.decode(player, entry.getValue()));
            }
        }
    }
}
