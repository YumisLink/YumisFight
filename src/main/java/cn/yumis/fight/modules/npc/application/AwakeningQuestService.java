package cn.yumis.fight.modules.npc.application;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.character.api.CharacterPersistence;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.dungeons.api.AwakeningTower;
import cn.yumis.fight.modules.economy.api.RewardPorts;
import cn.yumis.fight.modules.inventory.api.InventoryEdits;
import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import cn.yumis.fight.modules.inventory.api.MenuHooks;
import cn.yumis.fight.modules.npc.api.AwakeningQuest;
import cn.yumis.fight.modules.npc.api.AwakeningQuest.Stage;
import cn.yumis.fight.modules.npc.api.AwakeningQuest.State;
import cn.yumis.fight.modules.npc.api.AwakeningQuest.WorldProgress;
import cn.yumis.fight.platform.ItemData;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/** Persists owner-authored quest transitions and receives committed world clear facts. */
public final class AwakeningQuestService {
    private static final Gson JSON = new Gson();

    /** Merge the writes into the encounter commit, use the page recipients for loot, then publish. */
    public record ClearCommit(Map<String, JsonElement> writes, Set<UUID> chantPagePlayers, Runnable publish) {}
    private static final ClearCommit NO_CLEAR = new ClearCommit(Map.of(), Set.of(), () -> {});

    private final Journal journal;
    private final InventoryOperations inventory;
    private final CharacterPersistence characters;
    private final RewardPorts.Players players;
    private final ToLongFunction<ServerPlayer> lifeGeneration;
    private final Function<UUID, ServerPlayer> onlinePlayer;

    public AwakeningQuestService(Journal journal, InventoryOperations inventory, CharacterPersistence characters,
                                 RewardPorts.Players players, ToLongFunction<ServerPlayer> lifeGeneration,
                                 Function<UUID, ServerPlayer> onlinePlayer) {
        this.journal = journal;
        this.inventory = inventory;
        this.characters = characters;
        this.players = players;
        this.lifeGeneration = lifeGeneration;
        this.onlinePlayer = onlinePlayer;
    }

    public void register(CommandRouter<ServerPlayer, JsonObject> router) {
        router.register("npc", AwakeningQuest.ACTION, this::receive);
    }

    private static String taskKey(UUID player) { return "awakeningQuest/v1/" + player; }
    private static String worldKey(UUID player) { return "awakeningWorld/v1/" + player; }

    private State state(UUID player) { return journal.get(taskKey(player), State.class, State.INITIAL); }
    private WorldProgress world(UUID player) {
        return journal.get(worldKey(player), WorldProgress.class, WorldProgress.EMPTY);
    }

    public ClearCommit prepareAltarClear(UUID runId, String bossId, boolean captured, Set<UUID> participants) {
        if (!AwakeningQuest.definition().tracks(bossId)) return NO_CLEAR;
        return prepareClear("altar/" + runId, bossId, captured, participants);
    }

    public ClearCommit prepareTowerClear(UUID runId, Set<UUID> participants) {
        return towerOpen() ? prepareClear("tower/" + runId, null, false, participants) : NO_CLEAR;
    }

    private boolean towerOpen() { return AwakeningTower.GOAL.equals(AwakeningQuest.definition().towerGoal()); }

    private ClearCommit prepareClear(String identity, String bossId, boolean captured, Set<UUID> participants) {
        String receipt = "awakeningWorld/clear/v1/" + identity;
        if (!journal.get(receipt).isJsonNull()) return NO_CLEAR;
        Set<UUID> eligible = Set.copyOf(participants);
        var writes = new LinkedHashMap<String, JsonElement>();
        var pages = new LinkedHashSet<UUID>();
        for (UUID player : eligible) {
            WorldProgress before = world(player);
            if (before.grantsChantPage(bossId, captured)) pages.add(player);
            WorldProgress after = bossId == null ? before.towerClear() : before.altarClear(bossId, captured);
            writes.put(worldKey(player), JSON.toJsonTree(after));
        }
        writes.put(receipt, new JsonPrimitive(true));
        return new ClearCommit(Map.copyOf(writes), Set.copyOf(pages), () -> {
            for (UUID id : eligible) {
                ServerPlayer player = onlinePlayer.apply(id);
                if (player != null) players.profile(player);
            }
        });
    }

    public JsonObject view(ServerPlayer player) {
        UUID id = player.getUUID();
        State state = state(id);
        var view = new JsonObject();
        view.add("state", JSON.toJsonTree(state));
        view.add("world", JSON.toJsonTree(world(id)));
        view.addProperty("towerOpen", towerOpen());
        if (state.stage() == Stage.MATERIALS) view.add("bag", materialSources(player));
        return view;
    }

    // The client never needs unrelated dimensional bag slots to plan the turn-in.
    private JsonArray materialSources(ServerPlayer player) {
        var missing = new HashMap<String, Integer>();
        for (var material : AwakeningQuest.definition().materials()) missing.put(material.id(), material.count());
        var rows = new JsonArray();
        var extra = MenuHooks.extra(player);
        for (int slot = 0; slot < extra.bagLimit() && !missing.isEmpty(); slot++) {
            var stack = extra.bag.getItem(slot);
            if (stack.isEmpty() || !ItemData.own(stack)) continue;
            String item = ItemData.id(stack);
            Integer count = missing.get(item);
            if (count == null) continue;
            var row = new JsonObject();
            row.addProperty("slot", slot);
            row.add("source", inventory.encode(player, stack));
            rows.add(row);
            if (stack.getCount() >= count) missing.remove(item);
            else missing.put(item, count - stack.getCount());
        }
        return rows;
    }

    private void receive(ServerPlayer player, JsonObject request) {
        UUID id = player.getUUID();
        if (!players.session(player).toString().equals(request.get("session").getAsString())) {
            throw new IllegalArgumentException("会话已失效");
        }
        String operation = UUID.fromString(request.get("operation").getAsString()).toString();
        var old = journal.get("inventoryReceipt/" + id + "/" + operation);
        if (!old.isJsonNull()) {
            if (!old.getAsJsonObject().get("intent").equals(request)) throw new IllegalArgumentException("觉醒操作号冲突");
            restore(player);
            return;
        }
        if (lifeGeneration.applyAsLong(player) != request.get("generation").getAsLong()) {
            throw new IllegalArgumentException("生命代际已变化，请重试");
        }
        State before = state(id);
        WorldProgress snapshot = world(id);
        if (!JSON.toJsonTree(before).equals(request.get("beforeQuest"))
                || !JSON.toJsonTree(snapshot).equals(request.get("beforeWorld"))) {
            throw new IllegalArgumentException("觉醒任务快照已变化，请重试");
        }
        var definition = AwakeningQuest.definition();
        State next = JSON.fromJson(request.get("quest"), State.class);
        boolean reward = before.stage() != Stage.LOCKED;
        if (!before.advance(snapshot, definition).equals(next) || reward != request.has("reward")
                || (before.stage() == Stage.ELAINA) != request.has("progress")) {
            throw new IllegalArgumentException("觉醒任务结果格式无效");
        }
        if (before.stage() == Stage.MATERIALS && definition.bosses().stream().anyMatch(boss -> snapshot.clears(boss.id()) < boss.clears())) {
            throw new IllegalArgumentException("祭坛讨伐次数不足");
        }
        if (before.stage() == Stage.CONFESSION && snapshot.clears(definition.confession()) <= before.confessionBaseline()) {
            throw new IllegalArgumentException("尚未记录本阶段的千室告解通关");
        }
        if (before.stage() == Stage.TOWER && (!towerOpen() || snapshot.towerClears() <= before.towerBaseline())) {
            throw new IllegalArgumentException("尚未记录本阶段的登塔通关");
        }
        if (before.stage() == Stage.MATERIALS) {
            var level = player.level();
            var altar = BlockPos.of(request.get("altar").getAsLong());
            if (!level.dimension().identifier().toString().equals(request.get("dimension").getAsString())
                    || !level.hasChunkAt(altar) || !AwakeningQuest.isAltar(level.getBlockState(altar))) {
                throw new IllegalArgumentException("魔王祭坛已变化，请重新提交");
            }
        }
        var writes = new LinkedHashMap<String, JsonElement>();
        writes.put(taskKey(id), request.get("quest").deepCopy());
        if (reward) {
            String claim = "awakeningQuest/reward/v1/" + id + "/" + before.stage().id();
            if (!journal.get(claim).isJsonNull()) throw new IllegalArgumentException("该觉醒阶段的奖励已发放");
            writes.put(claim, new JsonPrimitive(operation));
        }

        Progress nextProgress = null;
        if (request.has("progress")) {
            characters.profiles(id).requireCurrent(request);
            if (!JSON.toJsonTree(players.progress(player)).equals(request.get("beforeProgress"))) {
                throw new IllegalArgumentException("角色资料已更新，请重试");
            }
            nextProgress = JSON.fromJson(request.get("progress"), Progress.class);
            writes.putAll(characters.writes(id, nextProgress));
        }

        try {
            if (!inventory.transact(player, operation, request, () -> {
                InventoryEdits.apply(player, inventory, request);
                if (reward) inventory.deliverOrStash(player, inventory.decode(player, request.get("reward")), writes);
            }, writes)) {
                throw new IllegalStateException("觉醒任务尚未保存，请重试");
            }
        } catch (RuntimeException failure) {
            if (!journal.get("inventoryReceipt/" + id + "/" + operation).isJsonNull() && nextProgress != null) {
                players.progress(player, characters.read(id));
            }
            throw failure;
        }
        if (nextProgress != null) players.progress(player, nextProgress);
    }

    private void restore(ServerPlayer player) {
        inventory.restore(player);
        players.progress(player, characters.read(player.getUUID()));
    }
}
