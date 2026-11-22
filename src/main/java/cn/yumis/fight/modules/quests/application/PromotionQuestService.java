package cn.yumis.fight.modules.quests.application;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.character.api.CharacterPersistence;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.CombatPorts;
import cn.yumis.fight.modules.dungeons.api.Difficulty;
import cn.yumis.fight.modules.economy.api.RewardPorts;
import cn.yumis.fight.modules.quests.api.AwakeningQuest;
import cn.yumis.fight.modules.quests.api.PromotionQuest;
import cn.yumis.fight.modules.quests.api.PromotionQuest.WorldProgress;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/** Persists KING story clears and checks the altar unlock before storing the owner's progress. */
public final class PromotionQuestService {
    private static final Gson JSON = new Gson();

    private final Journal journal;
    private final CharacterPersistence characters;
    private final RewardPorts.Players players;
    private final ToLongFunction<ServerPlayer> lifeGeneration;
    private final Function<UUID, ServerPlayer> onlinePlayer;

    public PromotionQuestService(Journal journal, CharacterPersistence characters, RewardPorts.Players players,
                                 ToLongFunction<ServerPlayer> lifeGeneration, Function<UUID, ServerPlayer> onlinePlayer) {
        this.journal = journal;
        this.characters = characters;
        this.players = players;
        this.lifeGeneration = lifeGeneration;
        this.onlinePlayer = onlinePlayer;
    }

    public void register(CommandRouter<ServerPlayer, JsonObject> router) {
        router.register("quests", PromotionQuest.ACTION, this::receive);
    }

    private static String worldKey(UUID player) { return "promotionWorld/v1/" + player; }
    private static String receiptKey(UUID player) { return "promotionQuest/receipt/v1/" + player; }

    private WorldProgress world(UUID player) {
        return journal.get(worldKey(player), WorldProgress.class, WorldProgress.EMPTY);
    }

    /** Join the per-clearer facts into the encounter commit, then refresh their profile views. */
    public CombatPorts.Commit prepareStoryClear(UUID runId, String dungeon, Difficulty difficulty, Set<UUID> participants) {
        if (difficulty != PromotionQuest.DIFFICULTY || !PromotionQuest.DUNGEONS.contains(dungeon)) return CombatPorts.Commit.NONE;
        String receipt = "promotionWorld/clear/v1/" + runId;
        if (!journal.get(receipt).isJsonNull()) return CombatPorts.Commit.NONE;
        Set<UUID> eligible = Set.copyOf(participants);
        var writes = new LinkedHashMap<String, JsonElement>();
        for (UUID player : eligible) writes.put(worldKey(player), JSON.toJsonTree(world(player).clear(dungeon)));
        writes.put(receipt, new JsonPrimitive(true));
        return new CombatPorts.Commit() {
            @Override public void persist() throws Exception { journal.transaction(writes); }
            @Override public void publish() {
                for (UUID id : eligible) {
                    ServerPlayer player = onlinePlayer.apply(id);
                    if (player != null) players.profile(player);
                }
            }
        };
    }

    public JsonObject view(ServerPlayer player) {
        var view = new JsonObject();
        view.add("world", JSON.toJsonTree(world(player.getUUID())));
        return view;
    }

    private void receive(ServerPlayer player, JsonObject request) {
        UUID id = player.getUUID();
        if (!players.session(player).toString().equals(request.get("session").getAsString())) {
            throw new IllegalArgumentException("会话已失效");
        }
        String operation = UUID.fromString(request.get("operation").getAsString()).toString();
        var prior = journal.get(receiptKey(id) + "/" + operation);
        if (!prior.isJsonNull()) {
            if (!prior.equals(request)) throw new IllegalArgumentException("操作号冲突");
            players.progress(player, characters.read(id));
            players.profile(player);
            return;
        }
        if (lifeGeneration.applyAsLong(player) != request.get("generation").getAsLong()) {
            throw new IllegalArgumentException("生命代际已变化，请重试");
        }
        Progress before = players.progress(player);
        WorldProgress snapshot = world(id);
        PromotionQuest.checkSnapshots(before, snapshot, request);
        characters.profiles(id).requireCurrent(request);
        Progress next = PromotionQuest.unlock(before, snapshot, request);
        var level = player.level();
        var altar = BlockPos.of(request.get("altar").getAsLong());
        if (!level.dimension().identifier().toString().equals(request.get("dimension").getAsString())
                || !level.hasChunkAt(altar) || !AwakeningQuest.isAltar(level.getBlockState(altar))) {
            throw new IllegalArgumentException("魔王祭坛已变化，请重新提交");
        }
        var writes = new LinkedHashMap<String, JsonElement>();
        writes.put(receiptKey(id) + "/" + operation, request.deepCopy());
        writes.putAll(characters.writes(id, next));
        try {
            journal.transaction(writes);
        } catch (Exception failure) {
            throw new IllegalStateException("转职试炼尚未保存，请重试", failure);
        }
        players.progress(player, next);
        players.profile(player);
    }
}
