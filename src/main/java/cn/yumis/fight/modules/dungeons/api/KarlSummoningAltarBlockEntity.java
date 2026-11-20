package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import cn.yumis.fight.modules.inventory.api.PersistentContainer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ContainerSingleItem;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** The throne guardian and later paid summons share one active actor slot. */
public final class KarlSummoningAltarBlockEntity extends BlockEntity
    implements PersistentContainer, ContainerSingleItem.BlockContainerSingleItem, WorldlyContainer {
    private static final int ARENA_RADIUS = CastleEnemy.KARL_HOME_RADIUS;
    private static final int[] NO_SLOTS = {};
    private static final String MESSAGE = "message.yumis_dungeon_and_fight.karl_summoning_altar.";
    private static InventoryOperations inventory;

    private UUID identity = UUID.randomUUID();
    private UUID bossId;
    private BlockPos spawn;
    private boolean guardianPending;
    private ItemStack offering = ItemStack.EMPTY;

    public KarlSummoningAltarBlockEntity(BlockPos pos, BlockState state) {
        super(DungeonContentTypes.KARL_ALTAR_ENTITY.get(), pos, state);
    }

    public static void install(InventoryOperations operations) { inventory = operations; }

    void offer(ServerPlayer player, InteractionHand hand) {
        if (!(getLevel() instanceof ServerLevel level) || inventory == null || !stillValid(player)) return;
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(DungeonContentTypes.KARL_TOKEN.get())) return;
        maintain(level);
        if (bossId != null && !guardianPending) {
            message(player, offering.isEmpty() ? "active" : "waiting");
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            message(player, "peaceful");
            return;
        }
        if (guardianPending) {
            message(player, "guardian_pending");
            return;
        }
        BlockPos destination = findSpawn(level);
        if (destination == null) {
            message(player, "space");
            return;
        }
        UUID actor = UUID.randomUUID();
        JsonObject intent = new JsonObject();
        intent.addProperty("altar", key());
        intent.addProperty("boss", actor.toString());
        intent.addProperty("hand", hand.name());
        if (inventory.transactMachine(player, "karl-summon/" + actor, intent, this, () -> {
            offering = held.split(1);
            bossId = actor;
            spawn = destination;
            setChanged();
        }, Map.of())) {
            maintain(level);
            message(player, offering.isEmpty() ? "summoned" : "waiting");
        }
    }

    void inspect(ServerPlayer player) {
        if (getLevel() instanceof ServerLevel level && (guardianPending
            || bossId != null && !offering.isEmpty() && level.getEntity(bossId) != null)) {
            maintain(level);
        }
        if (guardianPending) {
            message(player, "guardian_pending");
            return;
        }
        if (player.isShiftKeyDown() && bossId != null && !offering.isEmpty() && inventory != null && stillValid(player)) {
            JsonObject intent = new JsonObject();
            intent.addProperty("altar", key());
            intent.addProperty("withdraw", bossId.toString());
            Map<String, JsonElement> changes = new LinkedHashMap<>();
            inventory.transactMachine(player, "karl-withdraw/" + UUID.randomUUID(), intent, this, () -> {
                inventory.deliverOrStash(player, offering.copy(), changes);
                reset();
            }, changes);
            return;
        }
        message(player, bossId == null ? "hint" : offering.isEmpty() ? "active" : "waiting");
    }

    private static void message(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(MESSAGE + key));
    }

    private BlockPos findSpawn(ServerLevel level) {
        BlockPos throneCourt = throneCourt();
        if (canSpawn(level, throneCourt)) return throneCourt;
        BlockPos local = worldPosition.relative(getBlockState().getValue(KarlSummoningAltarBlock.FACING), 4);
        return canSpawn(level, local) ? local : null;
    }

    private BlockPos throneCourt() {
        return worldPosition.relative(getBlockState().getValue(KarlSummoningAltarBlock.FACING), 7).below(3);
    }

    private static boolean canSpawn(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
            || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
            || !level.getFluidState(pos).isEmpty()) return false;
        return level.noCollision(DungeonContentTypes.KARL.get().getDimensions().makeBoundingBox(Vec3.atBottomCenterOf(pos)));
    }

    private boolean arenaLoaded(ServerLevel level) {
        int firstX = SectionPos.blockToSectionCoord(spawn.getX() - ARENA_RADIUS);
        int lastX = SectionPos.blockToSectionCoord(spawn.getX() + ARENA_RADIUS);
        int firstZ = SectionPos.blockToSectionCoord(spawn.getZ() - ARENA_RADIUS);
        int lastZ = SectionPos.blockToSectionCoord(spawn.getZ() + ARENA_RADIUS);
        for (int x = firstX; x <= lastX; x++) for (int z = firstZ; z <= lastZ; z++) {
            if (!level.areEntitiesLoaded(ChunkPos.pack(x, z))) return false;
        }
        return true;
    }

    static void tick(Level level, BlockPos pos, BlockState state, KarlSummoningAltarBlockEntity altar) {
        if ((altar.bossId != null || altar.guardianPending) && level instanceof ServerLevel server && (server.getGameTime() + pos.asLong()) % 20 == 0) {
            altar.maintain(server);
        }
    }

    private void maintain(ServerLevel level) {
        if (bossId == null) {
            if (!guardianPending || level.getDifficulty() == Difficulty.PEACEFUL) return;
            spawn = throneCourt();
            String placement = level.dimension().identifier() + "/karl/" + worldPosition.asLong();
            bossId = UUID.nameUUIDFromBytes(placement.getBytes(StandardCharsets.UTF_8));
            setChanged();
        }
        if (spawn == null) return;
        if (level.getEntity(bossId) instanceof CastleEnemy actor && actor.boss()) {
            if (guardianPending || !offering.isEmpty()) confirmSpawn();
            if (!actor.isAlive()) {
                reset();
            } else {
                if ((actor.distanceToSqr(Vec3.atBottomCenterOf(spawn)) > ARENA_RADIUS * ARENA_RADIUS
                    || Math.abs(actor.getY() - spawn.getY()) > 4) && canSpawn(level, spawn)) {
                    actor.interruptPhantomSword();
                    actor.getNavigation().stop();
                    actor.snapTo(spawn, getBlockState().getValue(KarlSummoningAltarBlock.FACING).toYRot(), 0);
                    actor.setDeltaMovement(Vec3.ZERO);
                }
            }
            return;
        }
        if (!arenaLoaded(level)) return;
        if (!guardianPending && offering.isEmpty()) {
            reset();
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL || !canSpawn(level, spawn)) return;
        var actor = DungeonContentTypes.KARL.get().create(level, EntitySpawnReason.TRIGGERED);
        if (actor == null) return;
        actor.setUUID(bossId);
        actor.prepareGuard(spawn);
        actor.snapTo(spawn, getBlockState().getValue(KarlSummoningAltarBlock.FACING).toYRot(), 0);
        if (level.addFreshEntity(actor)) {
            confirmSpawn();
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, actor.getX(), actor.getY() + 1, actor.getZ(), 36, .5, .75, .5, .04);
        }
    }

    private void confirmSpawn() {
        guardianPending = false;
        setTheItem(ItemStack.EMPTY);
    }

    private void reset() {
        bossId = null;
        spawn = null;
        setTheItem(ItemStack.EMPTY);
    }

    @Override public String key() { return identity.toString(); }
    @Override public BlockEntity getContainerBlockEntity() { return this; }
    @Override public ItemStack getTheItem() { return offering; }
    @Override public void setTheItem(ItemStack item) { offering = item; setChanged(); }
    @Override public int getMaxStackSize() { return 1; }
    @Override public int[] getSlotsForFace(Direction direction) { return NO_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack item, Direction direction) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack item, Direction direction) { return false; }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("identity", UUIDUtil.CODEC, identity);
        if (guardianPending) output.putBoolean("guardian_pending", true);
        if (bossId != null && spawn != null) {
            output.store("boss", UUIDUtil.CODEC, bossId);
            output.store("spawn", BlockPos.CODEC, spawn);
        }
        output.store("offering", ItemStack.OPTIONAL_CODEC, offering);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        identity = input.read("identity", UUIDUtil.CODEC).orElse(identity);
        guardianPending = input.getBooleanOr("guardian_pending", false);
        bossId = input.read("boss", UUIDUtil.CODEC).orElse(null);
        spawn = input.read("spawn", BlockPos.CODEC).orElse(null);
        offering = input.read("offering", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override public JsonElement transactionState() {
        var state = new JsonObject();
        state.addProperty("nbt", saveCustomOnly(getLevel().registryAccess()).toString());
        return state;
    }

    @Override public void restoreTransactionState(JsonElement state) {
        try {
            var nbt = TagParser.parseCompoundFully(state.getAsJsonObject().get("nbt").getAsString());
            loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, getLevel().registryAccess(), nbt));
            setChanged();
        } catch (Exception error) {
            throw new IllegalStateException("卡尔召唤台事务恢复失败", error);
        }
    }
}
