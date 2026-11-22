package cn.yumis.fight.modules.quests.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.combat.api.CombatPorts;
import cn.yumis.fight.modules.dungeons.api.Difficulty;
import cn.yumis.fight.modules.economy.api.RewardPorts;
import cn.yumis.fight.modules.inventory.api.InventoryEdits;
import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import cn.yumis.fight.modules.inventory.api.MenuHooks;
import cn.yumis.fight.modules.npc.api.NpcDefinitions;
import cn.yumis.fight.modules.npc.api.NpcInteractionComponent;
import cn.yumis.fight.modules.npc.api.NpcSpawns;
import cn.yumis.fight.modules.quests.api.Commissions;
import cn.yumis.fight.modules.quests.api.Commissions.Active;
import cn.yumis.fight.modules.quests.api.Commissions.CommissionState;
import cn.yumis.fight.modules.quests.api.Commissions.Kind;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.platform.ItemData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.google.gson.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/** Stores the single active commission; facts, snapshots and one transaction commit the settlement. */
public final class CommissionService {
    private static final Gson JSON = new Gson();

    private final Journal journal;
    private final InventoryOperations inventory;
    private final RewardPorts.Players players;
    private final RewardPorts.Balance balances;
    private final ToLongFunction<ServerPlayer> lifeGeneration;
    private final Function<UUID, ServerPlayer> onlinePlayer;

    public CommissionService(Journal journal, InventoryOperations inventory, RewardPorts.Players players,
                             RewardPorts.Balance balances, ToLongFunction<ServerPlayer> lifeGeneration,
                             Function<UUID, ServerPlayer> onlinePlayer) {
        this.journal = journal;
        this.inventory = inventory;
        this.players = players;
        this.balances = balances;
        this.lifeGeneration = lifeGeneration;
        this.onlinePlayer = onlinePlayer;
    }

    public void register(CommandRouter<ServerPlayer, JsonObject> router) {
        router.register("quests", Commissions.ACTION, this::receive);
    }

    private static String key(UUID player) { return "commission/v1/" + player; }
    private static String receipt(UUID player, String operation) { return "commission/receipt/v1/" + player + "/" + operation; }

    public CommissionState state(UUID player) {
        return journal.get(key(player), CommissionState.class, CommissionState.EMPTY);
    }

    public JsonObject view(ServerPlayer player) {
        var view = new JsonObject();
        var state = state(player.getUUID());
        view.add("state", JSON.toJsonTree(state));
        // The bag attachment never syncs; deliverable commissions get the matching rows from the server's own facts.
        var active = state.active();
        if (active != null && Commissions.delivered(active)) {
            var rows = new JsonArray();
            var extra = MenuHooks.extra(player);
            int listed = 0;
            for (int slot = 0; slot < extra.bagLimit() && listed < active.count(); slot++) {
                var stack = extra.bag.getItem(slot);
                if (stack.isEmpty() || !ItemData.own(stack) || !ItemData.id(stack).equals(active.item())) continue;
                var row = new JsonObject();
                row.addProperty("slot", slot);
                row.add("source", inventory.encode(player, stack));
                rows.add(row);
                listed += stack.getCount();
            }
            view.add("bag", rows);
        }
        return view;
    }

    /** Dungeon completions are server facts: matching active commissions advance inside the encounter commit. */
    public CombatPorts.Commit prepareDungeonClear(UUID runId, String dungeon, Difficulty difficulty, Set<UUID> participants) {
        var eligible = Set.copyOf(participants);
        var writes = new LinkedHashMap<String, JsonElement>();
        var touched = new LinkedHashSet<UUID>();
        for (UUID id : eligible) {
            String clear = "commissionClear/v1/" + runId + "/" + id;
            if (!journal.get(clear).isJsonNull()) continue;
            CommissionState state = state(id);
            Active active = state.active();
            if (active == null || Kind.of(active.kind()) != Kind.DUNGEON_CLEAR
                    || !active.dungeon().equals(dungeon) || !active.difficulty().equals(difficulty.name())) continue;
            var next = new CommissionState(cleared(active, active.progress() + 1), state.done());
            writes.put(key(id), JSON.toJsonTree(next));
            writes.put(clear, new JsonPrimitive(true));
            touched.add(id);
        }
        if (touched.isEmpty()) return CombatPorts.Commit.NONE;
        return new CombatPorts.Commit() {
            @Override public void persist() throws Exception { journal.transaction(writes); }
            @Override public void publish() {
                for (UUID id : touched) {
                    ServerPlayer player = onlinePlayer.apply(id);
                    if (player != null) players.profile(player);
                }
            }
        };
    }

    private static Active cleared(Active active, int progress) {
        return new Active(active.kind(), active.dungeon(), active.difficulty(), active.item(), active.count(), progress, active.points(), active.bonus(), active.bonusCount());
    }

    private void receive(ServerPlayer player, JsonObject request) {
        UUID id = player.getUUID();
        if (!players.session(player).toString().equals(request.get("session").getAsString())) {
            throw new IllegalArgumentException("会话已失效");
        }
        String kind = request.get("kind").getAsString();
        String operation = UUID.fromString(request.get("operation").getAsString()).toString();
        boolean transacted = kind.equals("deliver") || kind.equals("claim");
        var old = journal.get(transacted ? "inventoryReceipt/" + id + "/" + operation : receipt(id, operation));
        if (!old.isJsonNull()) {
            if (!transacted && !old.equals(request)) throw new IllegalArgumentException("委托操作号冲突");
            if (transacted && !old.getAsJsonObject().get("intent").equals(request)) throw new IllegalArgumentException("委托操作号冲突");
            if (transacted) inventory.restore(player);
            players.profile(player);
            return;
        }
        if (lifeGeneration.applyAsLong(player) != request.get("generation").getAsLong()) {
            throw new IllegalArgumentException("生命代际已变化，请重试");
        }
        CommissionState before = state(id);
        if (!JSON.toJsonTree(before).equals(request.get("beforeCommission"))) {
            throw new IllegalArgumentException("委托进度已变化，请重试");
        }
        var writes = new LinkedHashMap<String, JsonElement>();
        switch (kind) {
            case "accept" -> {
                Active next = JSON.fromJson(request.get("commission"), Active.class);
                Commissions.validate(next, players.progress(player).level);
                if (before.active() != null) throw new IllegalArgumentException("已有进行中的委托");
                requireAgent(player, request);
                writes.put(key(id), JSON.toJsonTree(new CommissionState(next, before.done())));
            }
            case "abandon" -> {
                if (before.active() == null) throw new IllegalArgumentException("没有进行中的委托");
                writes.put(key(id), JSON.toJsonTree(new CommissionState(null, before.done())));
            }
            case "progress" -> {
                Active active = before.active();
                if (active == null || !Commissions.ownerCounted(active)) throw new IllegalArgumentException("当前委托不按次数推进");
                int value = request.get("progress").getAsInt();
                if (value < active.progress() || value > active.count()) throw new IllegalArgumentException("委托进度无效");
                writes.put(key(id), JSON.toJsonTree(new CommissionState(cleared(active, value), before.done())));
            }
            case "deliver" -> {
                Active active = before.active();
                if (active == null || !Commissions.delivered(active)) throw new IllegalArgumentException("当前委托不可交付");
                requireAgent(player, request);
                int removed = removedCount(player, request, active.item());
                if (removed < active.count()) throw new IllegalArgumentException("交付数量不足，需要 " + active.item() + " ×" + active.count());
                writes.putAll(balances.receive(player, request));
                writes.put(key(id), JSON.toJsonTree(new CommissionState(null, before.done() + 1)));
            }
            case "claim" -> {
                Active active = before.active();
                var kindOf = active == null ? null : Kind.of(active.kind());
                if (active == null || kindOf != Kind.DUNGEON_CLEAR && !Commissions.ownerCounted(active)) {
                    throw new IllegalArgumentException("当前委托不可自动结算");
                }
                if (active.progress() < active.count()) throw new IllegalArgumentException("委托目标尚未完成");
                writes.putAll(balances.receive(player, request));
                writes.put(key(id), JSON.toJsonTree(new CommissionState(null, before.done() + 1)));
            }
            default -> throw new IllegalArgumentException("未知委托操作：" + kind);
        }
        if (transacted) {
            try {
                if (!inventory.transact(player, operation, request, () -> {
                    InventoryEdits.apply(player, inventory, request);
                    if (request.has("bonus")) inventory.deliverOrStash(player, inventory.decode(player, request.get("bonus")), writes);
                }, writes)) {
                    throw new IllegalStateException("委托结算尚未保存，请重试");
                }
            } catch (RuntimeException failure) {
                if (!journal.get("inventoryReceipt/" + id + "/" + operation).isJsonNull()) players.profile(player);
                throw failure;
            }
        } else {
            writes.put(receipt(id, operation), request.deepCopy());
            try {
                journal.transaction(writes);
            } catch (Exception failure) {
                throw new IllegalStateException("委托尚未保存，请重试", failure);
            }
        }
        players.profile(player);
        if (transacted && kind.equals("claim") && request.has("pointsGain")) {
            players.message(player, "委托完成，点券 +" + request.get("pointsGain").getAsLong());
        }
    }

    /** Settlements that need an agent present are checked on the same shared entity as every other NPC action. */
    private static void requireAgent(ServerPlayer player, JsonObject request) {
        if (!request.has("entity") || !(player.level().getEntity(UUID.fromString(request.get("entity").getAsString())) instanceof LivingEntity living)
                || !living.isAlive() || !NpcInteractionComponent.id(living).equals(Commissions.NPC)) {
            throw new IllegalArgumentException("请在委托NPC附近操作");
        }
    }

    private int removedCount(ServerPlayer player, JsonObject request, String item) {
        int removed = 0;
        for (var raw : request.getAsJsonArray("inventoryChanges")) {
            var row = raw.getAsJsonObject();
            var before = inventory.decode(player, row.get("before"));
            var after = row.has("after") && !row.get("after").isJsonNull() ? inventory.decode(player, row.get("after")) : net.minecraft.world.item.ItemStack.EMPTY;
            if (!before.isEmpty() && ItemData.id(before).equals(item)) removed += Math.max(0, before.getCount() - after.getCount());
        }
        return removed;
    }

    /** A single village agent appears at a random safe spot near spawn; journal idempotence keeps it once per world. */
    public void starterVillage(ServerLevel level) {
        NpcDefinitions.current().require(Commissions.NPC);
        var origin = level.getRespawnData().pos();
        var candidates = new ArrayList<NpcSpawns.Anchor>();
        for (int radius = 2; radius <= 48 && candidates.size() < 24; radius += 2) {
            for (int side = 0; side < 16; side++) {
                double angle = side * Math.PI / 8;
                int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius), z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                var feet = new BlockPos(x, y, z);
                var pos = Vec3.atBottomCenterOf(feet);
                if (!standing(level, pos) || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty()) continue;
                candidates.add(new NpcSpawns.Anchor(Commissions.NPC, pos, 0));
            }
        }
        if (candidates.isEmpty()) throw new IllegalStateException("出生村附近未找到委托NPC的安全投放位置");
        var random = new Random(level.getSeed());
        var anchor = candidates.get(random.nextInt(candidates.size()));
        NpcSpawns.group(level, "commission", List.of(anchor), List.of(Commissions.NPC), level.getSeed());
    }

    private static boolean standing(ServerLevel level, Vec3 pos) {
        var below = BlockPos.containing(pos.add(0, -.01, 0));
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.noCollision(new AABB(pos.x - .3, pos.y, pos.z - .3, pos.x + .3, pos.y + 1.8, pos.z + .3));
    }

    public static void commands(LiteralArgumentBuilder<CommandSourceStack> root, Supplier<CommissionService> service) {
        var commission = Commands.literal("commission");
        var admin = Commands.literal("quests").requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
        commission.then(Commands.literal("state").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
            var player = EntityArgument.getPlayer(c, "player");
            var state = service.get().state(player.getUUID());
            String text = state.active() == null ? player.getName().getString() + "：无进行中的委托，累计完成 " + state.done()
                    : player.getName().getString() + "：" + Commissions.title(state.active()) + "，进度 " + state.active().progress() + "/" + state.active().count() + "，累计完成 " + state.done();
            c.getSource().sendSuccess(() -> ModText.of(text), false);
            return 1;
        })));
        commission.then(Commands.literal("grant").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
            var player = EntityArgument.getPlayer(c, "player");
            var instance = service.get();
            var drawn = Commissions.draw(instance.players.progress(player).level, new Random());
            try {
                instance.journal.transaction(Map.of(key(player.getUUID()), JSON.toJsonTree(new CommissionState(drawn, instance.state(player.getUUID()).done()))));
            } catch (Exception failure) {
                throw new IllegalStateException("委托未发放", failure);
            }
            instance.players.profile(player);
            c.getSource().sendSuccess(() -> ModText.of("已向 " + player.getName().getString() + " 发放委托：" + Commissions.objective(drawn)), true);
            return 1;
        })));
        commission.then(Commands.literal("clear").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
            var player = EntityArgument.getPlayer(c, "player");
            var instance = service.get();
            var active = instance.state(player.getUUID()).active();
            if (active == null || Kind.of(active.kind()) != Kind.DUNGEON_CLEAR) {
                c.getSource().sendFailure(ModText.of("该玩家没有进行中的副本委托"));
                return 0;
            }
            var commit = instance.prepareDungeonClear(UUID.randomUUID(), active.dungeon(), Difficulty.valueOf(active.difficulty()), Set.of(player.getUUID()));
            try {
                commit.persist();
            } catch (Exception failure) {
                throw new IllegalStateException("委托通关事实未写入", failure);
            }
            commit.publish();
            c.getSource().sendSuccess(() -> ModText.of("已模拟 " + player.getName().getString() + " 的委托副本通关"), true);
            return 1;
        })));
        commission.then(Commands.literal("abandon").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
            var player = EntityArgument.getPlayer(c, "player");
            var instance = service.get();
            var before = instance.state(player.getUUID());
            if (before.active() == null) {
                c.getSource().sendFailure(ModText.of("该玩家没有进行中的委托"));
                return 0;
            }
            try {
                instance.journal.transaction(Map.of(key(player.getUUID()), JSON.toJsonTree(new CommissionState(null, before.done()))));
            } catch (Exception failure) {
                throw new IllegalStateException("委托未放弃", failure);
            }
            instance.players.profile(player);
            c.getSource().sendSuccess(() -> ModText.of("已放弃 " + player.getName().getString() + " 的委托"), true);
            return 1;
        })));
        root.then(admin.then(commission));
    }
}
