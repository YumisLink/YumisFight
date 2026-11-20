package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.economy.api.WorldItemTransfers;
import cn.yumis.fight.modules.industry.api.EnergyMath;
import cn.yumis.fight.modules.industry.api.IndustrialPorts;
import cn.yumis.fight.modules.industry.api.IndustrialRecipe;
import cn.yumis.fight.modules.industry.api.IndustrialRituals;
import cn.yumis.fight.modules.inventory.api.InventoryPacking;
import cn.yumis.fight.modules.inventory.api.PersistentContainer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public final class DemonAltarBlockEntity extends BlockEntity implements PersistentContainer, WorldlyContainer {
    public static final int OFFERING_SLOTS = 4;
    public static final int OUTPUT_SLOT = OFFERING_SLOTS;
    private static final long NO_RITUAL = -1;
    private static final int[] NO_AUTOMATION_SLOTS = new int[0];
    private static final long CAPACITY = EnergyMath.crystal(EnergyMath.MAX_CRYSTAL_TIER);
    private static final String TEXT = "demon_altar." + IndustrialContent.NS + ".";
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    private final NonNullList<ItemStack> items = NonNullList.withSize(OFFERING_SLOTS + 1, ItemStack.EMPTY);
    private UUID identity = UUID.randomUUID();
    private String preferredBeacon = "";
    private long energy;
    private String summonDebit = "";
    private boolean recoveredSummonDebit;

    public record ManaStatus(long stored, boolean gridConnected, long gridAvailable, long ritualCost, boolean resultReady) {}

    private record RitualState(long energy, List<ItemStack> items, String summonDebit) {}
    private record RitualMatch(IndustrialRecipe recipe, int[] plan) {}

    private final SnapshotJournal<RitualState> ritual = new SnapshotJournal<>() {
        @Override protected RitualState createSnapshot() {
            return new RitualState(energy, items.stream().map(ItemStack::copy).toList(), summonDebit);
        }

        @Override protected void revertToSnapshot(RitualState snapshot) {
            energy = snapshot.energy();
            summonDebit = snapshot.summonDebit();
            for (int slot = 0; slot < items.size(); slot++) items.set(slot, snapshot.items().get(slot));
        }

        @Override protected void onRootCommit(RitualState snapshot) { publish(); }
    };

    public DemonAltarBlockEntity(BlockPos pos, BlockState state) {
        super(IndustrialContent.DEMON_ALTAR_BE.get(), pos, state);
    }

    public long storedMana() { return energy; }

    public ManaStatus manaStatus(ServerLevel server) {
        var grid = IndustrialWorld.get(server);
        var core = beacon(server, grid);
        boolean resultReady = !getItem(OUTPUT_SLOT).isEmpty();
        return new ManaStatus(energy, core != null, core == null ? 0 : grid.availableStoredEnergy(core),
            resultReady ? NO_RITUAL : displayRitualCost(server), resultReady);
    }

    public static boolean supports(IndustrialRecipe recipe) {
        return recipe.machine().equals(DemonAltarBlock.ID) && recipe.minimumTier() == 1 && recipe.ticks() == 1
            && !recipe.ingredients().isEmpty() && recipe.ingredients().size() <= OFFERING_SLOTS
            && recipe.outputs().size() == 1 && recipe.fluidInputs().isEmpty() && recipe.fluidOutput().isEmpty();
    }

    private static List<IndustrialRecipe> recipes(ServerLevel level) {
        return level.recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get()).stream()
            .filter(holder -> supports(holder.value()))
            .sorted(Comparator.comparing(holder -> holder.id().identifier().toString()))
            .map(RecipeHolder::value).toList();
    }

    void bind(ServerLevel server, MachineBlockEntity beacon) {
        if (beacon == null || IndustrialWorld.get(server).validBeacon(server, worldPosition, beacon.key()) != beacon) {
            throw new IllegalArgumentException("请选择覆盖魔王祭坛的魔能信标");
        }
        preferredBeacon = beacon.key();
        setChanged();
    }

    private MachineBlockEntity beacon(ServerLevel server, IndustrialWorld grid) {
        var preferred = grid.validBeacon(server, worldPosition, preferredBeacon);
        return preferred != null ? preferred : grid.nearestBeacon(server, worldPosition);
    }

    void interact(ServerPlayer player, InteractionHand hand, boolean top) {
        if (!stillValid(player) || !IndustrialPorts.ready()) return;
        recoverSummonDebit();
        ServerLevel server = player.level();
        ItemStack held = player.getItemInHand(hand);
        try {
            if (held.isEmpty()) {
                int slot = top ? withdrawalSlot(player.isShiftKeyDown()) : -1;
                if (slot >= 0) {
                    withdraw(player, slot);
                    return;
                }
                if (!top && IndustrialRituals.summonMenu(player, server, worldPosition, energy,
                        beacon(server, IndustrialWorld.get(server)) != null,
                        manaStatus(server).gridAvailable())) {
                    return;
                }
                showEnergy(player, server);
                return;
            }
            int tier = IndustrialContent.crystalTier(held);
            if (tier > 0) charge(player, server, held, tier);
            else if (IndustrialRituals.token(held)) {
                if (IndustrialRituals.ready()) IndustrialRituals.unlock(player, server, worldPosition, held);
            }
            else if (top) offer(player, server, held);
        } catch (RuntimeException failure) {
            LOG.error("Demon altar interaction failed at {}", worldPosition, failure);
            player.sendOverlayMessage(Component.translatable(TEXT + "failed"));
        }
    }

    private void charge(ServerPlayer player, ServerLevel server, ItemStack held, int tier) {
        long amount = EnergyMath.crystal(tier);
        if (amount > CAPACITY - energy) {
            player.sendOverlayMessage(Component.translatable(TEXT + "energy_full"));
            return;
        }
        if (transact(player, "charge", () -> {
            held.shrink(1);
            energy += amount;
        })) {
            tryRitual(server);
            showEnergy(player, server);
        }
    }

    private void offer(ServerPlayer player, ServerLevel server, ItemStack held) {
        if (!acceptsOffering(server, held)) {
            player.sendOverlayMessage(Component.translatable(TEXT + "invalid_offering"));
            return;
        }
        int slot = offeringSlot(held);
        if (slot == -1) {
            player.sendOverlayMessage(Component.translatable(TEXT + "offerings_full"));
            return;
        }
        if (transact(player, "offer", () -> held.shrink(placeOffering(slot, held)))) tryRitual(server);
    }

    void acceptDroppedOffering(ItemEntity drop) {
        if (!(level instanceof ServerLevel server) || !WorldItemTransfers.ready() || drop.getTarget() != null) return;
        recoverSummonDebit();
        ItemStack stack = drop.getItem();
        int slot = offeringSlot(stack);
        if (slot == -1 || !acceptsOffering(server, stack)) return;
        try {
            if (WorldItemTransfers.collect(drop, this, offering -> placeOffering(slot, offering)) > 0) {
                publish();
                tryRitual(server);
            }
        } catch (RuntimeException failure) {
            LOG.error("Demon altar offering intake failed at {}", worldPosition, failure);
        }
    }

    private static boolean acceptsOffering(ServerLevel level, ItemStack stack) {
        return recipes(level).stream().anyMatch(recipe -> accepts(recipe, stack));
    }

    private int placeOffering(int slot, ItemStack stack) {
        int stored = getItem(slot).getCount();
        int placed = Math.min(stack.getCount(), stack.getMaxStackSize() - stored);
        items.set(slot, stack.copyWithCount(stored + placed));
        return placed;
    }

    private int offeringSlot(ItemStack stack) {
        int empty = -1;
        for (int slot = 0; slot < OFFERING_SLOTS; slot++) {
            ItemStack current = getItem(slot);
            if (current.isEmpty()) {
                if (empty == -1) empty = slot;
            } else if (ItemStack.isSameItemSameComponents(current, stack) && current.getCount() < stack.getMaxStackSize()) {
                return slot;
            }
        }
        return empty;
    }

    private int withdrawalSlot(boolean offeringsFirst) {
        if (!offeringsFirst && !getItem(OUTPUT_SLOT).isEmpty()) return OUTPUT_SLOT;
        for (int slot = 0; slot < OFFERING_SLOTS; slot++) if (!getItem(slot).isEmpty()) return slot;
        return getItem(OUTPUT_SLOT).isEmpty() ? -1 : OUTPUT_SLOT;
    }

    private void withdraw(ServerPlayer player, int slot) {
        ItemStack stack = getItem(slot);
        if (InventoryPacking.rawRoom(player, stack) < stack.getCount()) {
            player.sendOverlayMessage(Component.translatable(TEXT + "inventory_full"));
            return;
        }
        transact(player, "withdraw", () -> {
            ItemStack withdrawn = ContainerHelper.takeItem(items, slot);
            InventoryPacking.insertRaw(player, withdrawn);
            if (!withdrawn.isEmpty()) throw new IllegalStateException("Demon altar withdrawal no longer fits");
        });
    }

    private boolean transact(ServerPlayer player, String action, Runnable mutation) {
        var intent = new JsonObject();
        intent.addProperty("altar", key());
        intent.addProperty("action", action);
        boolean committed = IndustrialPorts.inventory().transactMachine(player, "demon-altar/" + UUID.randomUUID(), intent, this, mutation, Map.of());
        if (committed) publish();
        return committed;
    }

    private void showEnergy(ServerPlayer player, ServerLevel server) {
        var status = Component.translatable(TEXT + (beacon(server, IndustrialWorld.get(server)) == null ? "offline" : "online"));
        player.sendOverlayMessage(Component.translatable(TEXT + "energy", energy, status));
    }

    static void tick(Level level, BlockPos pos, BlockState state, DemonAltarBlockEntity altar) {
        if (level instanceof ServerLevel server && Math.floorMod(server.getGameTime() + pos.asLong(), 20L) == 0) {
            altar.tryRitual(server);
        }
    }

    private static boolean accepts(IndustrialRecipe recipe, ItemStack stack) {
        return recipe.ingredients().stream().anyMatch(need -> need.ingredient().test(stack));
    }

    private static List<IndustrialRecipe> candidates(ServerLevel server, List<ItemStack> offerings) {
        return recipes(server).stream()
            .filter(recipe -> offerings.stream().allMatch(stack -> stack.isEmpty() || accepts(recipe, stack)))
            .toList();
    }

    private static RitualMatch match(List<IndustrialRecipe> candidates, List<ItemStack> offerings) {
        for (var recipe : candidates) {
            int[] plan = recipe.takePlan(offerings);
            if (plan != null) return new RitualMatch(recipe, plan);
        }
        return null;
    }

    private long displayRitualCost(ServerLevel server) {
        var offerings = items.subList(0, OFFERING_SLOTS);
        var candidates = candidates(server, offerings);
        if (candidates.size() == 1) return candidates.getFirst().energy();
        var ritual = match(candidates, offerings);
        return ritual == null ? NO_RITUAL : ritual.recipe().energy();
    }

    private void tryRitual(ServerLevel server) {
        if (!RitualEnergy.ready()) return;
        recoverSummonDebit();
        if (!getItem(OUTPUT_SLOT).isEmpty() || isEmpty()) return;
        var offerings = items.subList(0, OFFERING_SLOTS);
        var ritual = match(candidates(server, offerings), offerings);
        if (ritual != null) finishRitual(server, ritual.recipe(), ritual.plan());
    }

    private void finishRitual(ServerLevel server, IndustrialRecipe recipe, int[] plan) {
        long localCost = Math.min(energy, recipe.energy());
        long gridCost = recipe.energy() - localCost;
        ItemStack result = recipe.results().getFirst();
        if (result.isEmpty() || result.getCount() > result.getMaxStackSize()) return;

        try (var tx = Transaction.openRoot()) {
            if (gridCost > 0) {
                var grid = IndustrialWorld.get(server);
                var beacon = beacon(server, grid);
                if (beacon == null || !grid.spendStoredEnergy(beacon, gridCost, tx)) return;
            }
            ritual.updateSnapshots(tx);
            for (int slot = 0; slot < plan.length; slot++) if (plan[slot] > 0) ContainerHelper.removeItem(items, slot, plan[slot]);
            energy -= localCost;
            items.set(OUTPUT_SLOT, result);
            tx.commit();
        }

        server.sendParticles(ParticleTypes.PORTAL, worldPosition.getX() + .5, worldPosition.getY() + 1,
            worldPosition.getZ() + .5, 20, .26, .1, .26, .1);
        server.playSound(null, worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, .7f, .7f);
    }

    /** Commits the run and its energy projection in the same world journal entry. */
    public boolean commitSummonEnergy(ServerLevel server,long cost,String operation,Map<String,JsonElement> runWrites) throws java.io.IOException {
        recoverSummonDebit();
        long localCost = Math.min(energy, cost);
        long gridCost = cost - localCost;
        var writes = new LinkedHashMap<>(runWrites);
        try (var tx = Transaction.openRoot()) {
            if (gridCost > 0) {
                var grid = IndustrialWorld.get(server);
                var beacon = beacon(server, grid);
                if (beacon == null || !grid.spendStoredEnergy(beacon, gridCost, tx, operation, writes)) return false;
            }
            if (localCost > 0) {
                ritual.updateSnapshots(tx);
                energy -= localCost;
                summonDebit = operation;
                RitualEnergy.record(writes, key(), operation, energy);
            }
            RitualEnergy.commit(writes);
            tx.commit();
        }
        return true;
    }

    private void recoverSummonDebit() {
        if (recoveredSummonDebit || !RitualEnergy.ready() || !(level instanceof ServerLevel)) return;
        recoveredSummonDebit = true;
        var saved = RitualEnergy.recorded(key());
        if (saved != null && !saved.operation().equals(summonDebit)) {
            energy = Math.clamp(saved.stored(), 0L, CAPACITY);
            summonDebit = saved.operation();
            publish();
        }
    }

    @Override public void onLoad() { super.onLoad(); recoverSummonDebit(); }

    @Override public String key() { return identity.toString(); }
    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public boolean stillValid(Player player) {
        return !isRemoved() && level instanceof ServerLevel server && player.level() == server
            && server.mayInteract(player, worldPosition) && Container.stillValidBlockEntity(this, player);
    }

    @Override public int[] getSlotsForFace(Direction side) { return NO_AUTOMATION_SLOTS; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }

    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        publish();
    }

    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) publish();
        return removed;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }

    @Override public void clearContent() {
        items.clear();
        publish();
    }

    private void publish() {
        setChanged();
        if (level instanceof ServerLevel server && !isRemoved()) {
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void writeContents(ValueOutput out) {
        ContainerHelper.saveAllItems(out, items);
        out.putLong("energy", energy);
    }

    private void readContents(ValueInput input) {
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        energy = Math.clamp(input.getLongOr("energy", 0), 0, CAPACITY);
    }

    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("identity", key());
        out.putString("beacon", preferredBeacon);
        out.putString("summon_debit", summonDebit);
        writeContents(out);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        identity = UUID.fromString(input.getStringOr("identity", key()));
        preferredBeacon = input.getStringOr("beacon", "");
        summonDebit = input.getStringOr("summon_debit", "");
        readContents(input);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        writeContents(out);
        return out.buildResult();
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(ValueInput input) { readContents(input); }
    @Override public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }

    @Override public JsonElement transactionState() {
        var snapshot = new JsonObject();
        snapshot.addProperty("nbt", saveCustomOnly(level.registryAccess()).toString());
        return snapshot;
    }

    @Override public void restoreTransactionState(JsonElement snapshot) {
        try {
            var tag = TagParser.parseCompoundFully(snapshot.getAsJsonObject().get("nbt").getAsString());
            loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            setChanged();
        } catch (Exception failure) {
            throw new IllegalStateException("Demon altar transaction restore failed", failure);
        }
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server)) return;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = removeItemNoUpdate(slot);
            if (!stack.isEmpty()) Block.popResource(server, pos, stack);
        }
    }
}
