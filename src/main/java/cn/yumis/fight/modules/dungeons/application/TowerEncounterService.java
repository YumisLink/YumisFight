package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.dungeons.api.AwakeningTower;
import cn.yumis.fight.modules.dungeons.api.AwakeningTowerStele;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.api.DungeonContentTypes;
import cn.yumis.fight.modules.dungeons.api.DungeonPorts;
import cn.yumis.fight.modules.dungeons.api.TowerCardDraft;
import cn.yumis.fight.modules.dungeons.api.TowerClimbRules;
import cn.yumis.fight.modules.dungeons.api.TowerClimbRun;
import cn.yumis.fight.modules.dungeons.content.AwakeningTowerLayout;
import cn.yumis.fight.modules.dungeons.content.TowerClimbEncounters;
import cn.yumis.fight.modules.party.api.Team;
import cn.yumis.fight.network.Network;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Enrollment, floors, checkpoints and the shared barrier between personal card offers. */
final class TowerEncounterService {
    public static final long AFK_TIMEOUT_MILLIS = 600_000L;
    private static final Gson JSON = new Gson();
    private static final double ENROLLMENT_DISTANCE_SQUARED = 64;
    private static final int FLOOR_HEIGHT = 80;
    private final DungeonService owner;
    private final Journal journal;
    private final MinecraftServer server;
    private final DungeonPorts.Players players;
    private final DungeonPorts.Meter meter;
    private final DungeonPorts.TowerClear clear;
    private final Function<UUID, Team> teams;
    private final Random random = new Random();
    private final Map<UUID, Lobby> lobbies = new LinkedHashMap<>();
    private final Map<UUID, List<BlockPos>> spawnGround = new HashMap<>();

    private record Lobby(UUID id, UUID leader, UUID team, AwakeningTower tower, BlockPos stele,
                         Set<UUID> invited, Map<UUID, EntryChoice> accepted, long createdAt) {}

    private record EntryChoice(String deck, boolean keep) {}

    TowerEncounterService(DungeonService owner, Journal journal, MinecraftServer server, DungeonPorts.Players players,
                          DungeonPorts.Meter meter, DungeonPorts.TowerClear clear, Function<UUID, Team> teams) {
        this.owner = owner;
        this.journal = journal;
        this.server = server;
        this.players = players;
        this.meter = meter;
        this.clear = clear;
        this.teams = teams;
    }

    void requestEntry(ServerPlayer leader, AwakeningTower tower, BlockPos stele) throws Exception {
        if (owner.inArkWar(leader)) throw new IllegalArgumentException("请先离开方舟战争场地，再进入副本");
        if (AwakeningTowerStele.at(leader, stele) != tower) throw new IllegalArgumentException("请在对应石碑旁入场");
        if (owner.current(leader.getUUID()) != null) throw new IllegalArgumentException("请先退出当前副本");
        Team team = teams.apply(leader.getUUID());
        if (team != null && !team.canLead(leader.getUUID(), id -> online(id) != null)) throw new IllegalArgumentException("请由队长在石碑发起爬塔");
        for (Lobby previous : new ArrayList<>(lobbies.values())) {
            if (previous.leader().equals(leader.getUUID())) closeLobby(previous);
        }
        Set<UUID> invited = new LinkedHashSet<>();
        if (team != null) for (UUID member : team.members) {
            if (!leader.getUUID().equals(member) && nearby(online(member), stele)) invited.add(member);
        }
        if (invited.isEmpty() && retainedDeck(leader.getUUID()).isEmpty()) {
            begin(tower, List.of(leader), Set.of());
            return;
        }
        var lobby = new Lobby(UUID.randomUUID(), leader.getUUID(), team == null ? null : team.id,
            tower, stele.immutable(), invited, new LinkedHashMap<>(), System.currentTimeMillis());
        lobbies.put(lobby.id(), lobby);
        invited.forEach(member -> sendLobby(lobby, online(member), true));
        sendLobby(lobby, leader, true);
    }

    void action(ServerPlayer player, JsonObject request) throws Exception {
        if (!players.session(player).id.toString().equals(request.get("session").getAsString())) return;
        String action = request.get("kind").getAsString();
        if (action.equals("towerCards")) {
            saveCards(player, request);
            return;
        }
        if (action.equals("towerBattle")) {
            saveBattle(player, request);
            return;
        }
        Lobby lobby = lobbies.get(UUID.fromString(request.get("lobby").getAsString()));
        if (lobby == null) return;
        UUID id = player.getUUID();
        if (id.equals(lobby.leader())) {
            switch (action) {
                case "cancel" -> closeLobby(lobby);
                case "start" -> {
                    if (!validLeader(lobby)) throw new IllegalArgumentException("请由队长在原石碑旁开始爬塔");
                    var entering = new ArrayList<ServerPlayer>();
                    entering.add(player);
                    var keep = new LinkedHashSet<UUID>();
                    if (entryChoice(id, request).keep()) keep.add(id);
                    for (var entry : lobby.accepted().entrySet()) {
                        UUID member = entry.getKey();
                        ServerPlayer found = online(member);
                        if (nearby(found, lobby.stele()) && inTeam(member, lobby)
                            && entry.getValue().deck().equals(retainedDeck(member))) {
                            entering.add(found);
                            if (entry.getValue().keep()) keep.add(member);
                        }
                    }
                    begin(lobby.tower(), entering, keep);
                    closeLobby(lobby);
                }
                default -> throw new IllegalArgumentException("未知觉醒塔组队操作");
            }
        } else if (lobby.invited().contains(id)) {
            switch (action) {
                case "accept" -> {
                    if (!validLeader(lobby) || !nearby(player, lobby.stele()) || !inTeam(id, lobby))
                        throw new IllegalArgumentException("请留在队长发起爬塔的石碑附近");
                    lobby.accepted().put(id, entryChoice(id, request));
                }
                case "decline" -> {
                    lobby.invited().remove(id);
                    lobby.accepted().remove(id);
                    hideLobby(lobby, player);
                }
                default -> throw new IllegalArgumentException("未知觉醒塔组队操作");
            }
            sendLobby(lobby, online(lobby.leader()), false);
            if (lobby.invited().contains(id)) sendLobby(lobby, player, false);
        }
    }

    void tickLobbies() {
        long now = System.currentTimeMillis();
        for (Lobby lobby : new ArrayList<>(lobbies.values())) {
            ServerPlayer leader = online(lobby.leader());
            boolean timedOut = now - lobby.createdAt() >= AFK_TIMEOUT_MILLIS
                || leader != null && owner.idleMillis(leader) >= AFK_TIMEOUT_MILLIS;
            if (timedOut) {
                if (leader != null) players.message(leader, "超过600秒无操作，觉醒塔组队大厅已自动关闭");
                closeLobby(lobby);
            } else if (!validLeader(lobby)) {
                closeLobby(lobby);
            }
        }
    }

    private boolean validLeader(Lobby lobby) {
        ServerPlayer leader = online(lobby.leader());
        Team team = teams.apply(lobby.leader());
        boolean sameTeam = team == null ? lobby.team() == null
            : team.id.equals(lobby.team()) && team.canLead(lobby.leader(), id -> online(id) != null);
        return sameTeam && nearby(leader, lobby.stele()) && AwakeningTowerStele.at(leader, lobby.stele()) == lobby.tower();
    }

    private boolean inTeam(UUID member, Lobby lobby) {
        Team team = teams.apply(member);
        return team != null && team.id.equals(lobby.team());
    }

    private boolean nearby(ServerPlayer player, BlockPos stele) {
        return player != null && player.level().dimension() == Level.OVERWORLD
            && player.distanceToSqr(Vec3.atCenterOf(stele)) <= ENROLLMENT_DISTANCE_SQUARED
            && owner.current(player.getUUID()) == null;
    }

    private ServerPlayer online(UUID player) { return server.getPlayerList().getPlayer(player); }

    private void sendLobby(Lobby lobby, ServerPlayer player, boolean open) {
        if (player == null) return;
        var waiting = lobby.invited().stream().map(this::online).filter(Objects::nonNull)
            .map(p -> Map.of("name", p.getScoreboardName(), "ready", lobby.accepted().containsKey(p.getUUID()))).toList();
        Network.send(player, "towerLobby", Map.of("id", lobby.id().toString(), "tower", lobby.tower().dungeon().name(),
            "leader", player.getUUID().equals(lobby.leader()), "accepted", lobby.accepted().containsKey(player.getUUID()),
            "deck", retainedDeck(player.getUUID()),
            "members", waiting, "open", open));
    }

    private void closeLobby(Lobby lobby) {
        lobbies.remove(lobby.id());
        hideLobby(lobby, online(lobby.leader()));
        lobby.invited().forEach(id -> hideLobby(lobby, online(id)));
    }

    private void hideLobby(Lobby lobby, ServerPlayer player) {
        if (player != null) Network.send(player, "towerLobby", Map.of("id", lobby.id().toString(), "closed", true));
    }

    private String retainedDeck(UUID player) {
        var deck = journal.get(deckKey(player));
        return deck.isJsonObject() && !deck.getAsJsonObject().getAsJsonArray("chosen").isEmpty()
            ? deck.getAsJsonObject().get("id").getAsString() : "";
    }

    private EntryChoice entryChoice(UUID player, JsonObject request) {
        String deck = retainedDeck(player);
        String mode = request.get("mode").getAsString();
        if (!deck.equals(request.get("deck").getAsString()) || !(mode.equals("fresh") || mode.equals("keep") && !deck.isEmpty()))
            throw new IllegalArgumentException("请重新确认入塔时的卡牌选择");
        return new EntryChoice(deck, mode.equals("keep"));
    }

    void enterAt(ServerPlayer player, AwakeningTower tower, int floor) throws Exception {
        validateFloor(floor);
        if (owner.inArkWar(player)) throw new IllegalArgumentException("请先离开方舟战争场地，再进入副本");
        if (owner.current(player.getUUID()) != null) throw new IllegalArgumentException("请先退出当前副本");
        begin(tower, List.of(player), Set.of(player.getUUID()), floor);
    }

    void jumpFloor(ServerPlayer player, int floor) throws Exception {
        validateFloor(floor);
        Dungeon dungeon = owner.current(player.getUUID());
        if (dungeon == null || dungeon.tower == null) throw new IllegalArgumentException("当前不在觉醒塔副本中");
        var online = owner.playersIn(dungeon);
        if (online.isEmpty()) throw new IllegalArgumentException("觉醒塔内没有可参战的在线玩家");
        var run = dungeon.tower;
        if (online.stream().anyMatch(p -> !run.hasSelected(p.getUUID())))
            throw new IllegalArgumentException("请先完成当前觉醒塔选牌后再切换层数");
        owner.clearTowerFloor(dungeon);
        dungeon.enemies.clear();
        dungeon.summoned = false;
        dungeon.status = "RUNNING";
        run.floor = floor;
        run.checkpointFloor = floor;
        run.boss = "";
        run.bossEntity = null;
        if (online.stream().anyMatch(p -> players.session(p).life.dead)) {
            run.stage = TowerClimbRun.Stage.RECOVERING;
            save(dungeon, online);
        } else {
            spawnFloor(dungeon, online);
        }
    }

    private static void validateFloor(int floor) {
        if (floor < 1 || floor > TowerClimbRules.FLOOR_COUNT)
            throw new IllegalArgumentException("觉醒塔层数须在 1 到 " + TowerClimbRules.FLOOR_COUNT + " 之间");
    }

    private void begin(AwakeningTower tower, List<ServerPlayer> participants, Set<UUID> keep) throws Exception {
        begin(tower, participants, keep, 1);
    }

    private void begin(AwakeningTower tower, List<ServerPlayer> participants, Set<UUID> keep, int startFloor) throws Exception {
        Dungeon dungeon = new Dungeon();
        dungeon.dungeon = tower.dungeon().id();
        dungeon.difficulty = TowerClimbRules.DIFFICULTY;
        dungeon.tower = new TowerClimbRun();
        dungeon.tower.floor = startFloor;
        dungeon.tower.checkpointFloor = startFloor;
        dungeon.tower.keepingCards.addAll(keep);
        dungeon.cell = owner.freeCell();
        var writes = new LinkedHashMap<String, JsonElement>();
        var previousDecks = new LinkedHashMap<String, JsonElement>();
        for (ServerPlayer player : participants) {
            UUID id = player.getUUID();
            if (owner.current(id) != null) throw new IllegalArgumentException("已有参战者进入其他副本");
            dungeon.roster.add(id);
            var point = owner.point(player);
            dungeon.returns.put(id.toString(), point);
            writes.put("return/" + id, JSON.toJsonTree(point));
            if (!keep.contains(id)) {
                previousDecks.put(deckKey(id), journal.get(deckKey(id)));
                previousDecks.put(battleKey(id), journal.get(battleKey(id)));
                writes.put(deckKey(id), JSON.toJsonTree(Map.of("id", dungeon.id, "chosen", List.of())));
                writes.put(battleKey(id), JsonNull.INSTANCE);
            }
        }
        try {
            owner.forceArena(dungeon, true);
            owner.ensureArena(dungeon.dungeon, dungeon.cell);
            owner.clearTowerFloor(dungeon);
            writes.put("instance/" + dungeon.id, JSON.toJsonTree(dungeon));
            journal.transaction(writes);
            owner.instanceCommitted(dungeon.id);
            for (ServerPlayer player : participants) {
                journal.transaction(meter.joinWrites(dungeon, player));
                attach(dungeon, player);
            }
        } catch (Exception failure) {
            try {
                if (owner.byId(dungeon.id.toString()) == null) owner.forceArena(dungeon, false);
                else {
                    if (!previousDecks.isEmpty()) journal.transaction(previousDecks);
                    for (ServerPlayer player : participants) if (owner.current(player.getUUID()) != null) owner.leave(player, true);
                }
            } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    void attach(Dungeon dungeon, ServerPlayer player) throws Exception {
        var life = players.session(player).life;
        life.instance = dungeon.id.toString();
        life.dungeonDefeated = life.dead;
        owner.enter(dungeon, player);
        life.dimension = DungeonService.DIMENSION.identifier().toString();
        players.save(player);
        players.profile(player);
    }

    JsonElement cards(Dungeon dungeon, UUID player) {
        var deck = journal.get(deckKey(player));
        if (deck.isJsonObject() && dungeon != null && dungeon.tower != null && !dungeon.tower.keepingCards.contains(player))
            deck.getAsJsonObject().add("draft", journal.get(cardKey(dungeon, player)));
        return deck;
    }

    private String cardKey(Dungeon dungeon, UUID player) { return "towerCards/" + dungeon.id + "/" + player; }

    private String deckKey(UUID player) { return "towerDeck/" + player; }

    private String battleKey(UUID player) { return "towerDeckBattle/" + player; }

    JsonElement battle(UUID player) {
        return journal.get(battleKey(player));
    }

    private void saveBattle(ServerPlayer player, JsonObject request) throws Exception {
        UUID id = player.getUUID();
        String deck = request.get("deck").getAsString();
        if (deck.isEmpty() || !deck.equals(retainedDeck(id))) return;
        var state = request.getAsJsonObject("state");
        int revision = state.get("revision").getAsInt();
        JsonElement saved = battle(id);
        int previous = saved.isJsonObject() ? saved.getAsJsonObject().get("revision").getAsInt() : 0;
        if (revision > previous) journal.put(battleKey(id), state);
        Network.send(player, "towerBattle", Map.of("deck", deck, "revision", Math.max(previous, revision)));
    }

    private void saveCards(ServerPlayer player, JsonObject request) throws Exception {
        UUID id = player.getUUID();
        Dungeon dungeon = owner.current(id);
        if (dungeon == null || dungeon.tower == null || !dungeon.id.toString().equals(request.get("instance").getAsString())) return;
        JsonObject value = request.getAsJsonObject("draft");
        if (dungeon.tower.keepingCards.contains(id)) return;
        var next = JSON.fromJson(value, TowerCardDraft.Snapshot.class);
        JsonElement saved = journal.get(cardKey(dungeon, id));
        var run = dungeon.tower;
        int previous = run.selected.getOrDefault(id, -1);
        if (saved.isJsonObject() && next.revision() <= saved.getAsJsonObject().get("revision").getAsInt()) {
            players.profile(player);
            return;
        }
        // Card identity and rolls are determined by the owner. Only the scene sequence is checked here.
        if (next.offer() != previous + 1 || next.offer() > run.offer || next.slots() == null
            || !next.slots().isEmpty() && next.slots().size() != TowerCardDraft.CARDS_PER_OFFER)
            throw new IllegalArgumentException("本次选牌序号无效");
        run.selected.put(id, next.selectedThrough());
        var writes = new LinkedHashMap<String, JsonElement>();
        writes.put(cardKey(dungeon, id), value);
        writes.put("instance/" + dungeon.id, JSON.toJsonTree(dungeon));
        if (next.slots().isEmpty())
            writes.put(deckKey(id), JSON.toJsonTree(Map.of("id", dungeon.id, "chosen", next.chosen())));
        journal.transaction(writes);
        players.profile(player);
    }

    List<ServerPlayer> fighters(Dungeon dungeon, List<ServerPlayer> online) {
        return online.stream().filter(p -> !players.session(p).life.dead && dungeon.tower.hasSelected(p.getUUID())).toList();
    }

    void tick(Dungeon dungeon, List<ServerPlayer> online) throws Exception {
        var run = dungeon.tower;
        if (run.stage == TowerClimbRun.Stage.COMPLETED) return;
        if (!online.isEmpty()) {
            if (online.stream().allMatch(p -> owner.idleMillis(p) >= AFK_TIMEOUT_MILLIS)) {
                for (ServerPlayer player : List.copyOf(online)) {
                    players.message(player, "超过600秒无操作，觉醒塔挑战已自动结束");
                    owner.leave(player, true);
                }
                if (owner.byId(dungeon.id.toString()) != null) owner.close(dungeon);
                return;
            }
            if (run.stage == TowerClimbRun.Stage.SELECTING && online.size() > 1) {
                boolean evicted = false;
                for (ServerPlayer player : List.copyOf(online)) {
                    if (!run.hasSelected(player.getUUID()) && owner.idleMillis(player) >= AFK_TIMEOUT_MILLIS) {
                        players.message(player, "选牌超时（超过600秒无操作），已自动移出当前觉醒塔场次");
                        owner.leave(player, true);
                        evicted = true;
                    }
                }
                if (evicted) return;
            }
        }
        if (!run.fighting()) {
            if (online.stream().anyMatch(p -> players.session(p).life.dead || !run.hasSelected(p.getUUID()))) return;
            if (run.floor == TowerClimbRules.FLOOR_COUNT && run.offer == run.floor / 5) {
                run.stage = TowerClimbRun.Stage.COMPLETED;
                dungeon.status = "COMPLETED";
                save(dungeon, online);
            } else {
                if (run.stage == TowerClimbRun.Stage.SELECTING) run.checkpointFloor = run.floor;
                spawnFloor(dungeon, online);
            }
            return;
        }
        boolean cleared = run.bossEntity != null ? owner.defeated(run.bossEntity)
            : !dungeon.enemies.isEmpty() && dungeon.enemies.stream().allMatch(owner::defeated);
        if (cleared && TowerClimbRules.bossFloor(run.floor)) {
            bossCleared(dungeon, online);
        } else if (online.stream().allMatch(p -> players.session(p).life.dead)) {
            owner.clearTowerFloor(dungeon);
            dungeon.enemies.clear();
            dungeon.summoned = false;
            run.floor = run.checkpointFloor;
            run.boss = "";
            run.bossEntity = null;
            run.stage = TowerClimbRun.Stage.RECOVERING;
            save(dungeon, online);
        } else if (cleared) {
            run.floor++;
            run.stage = TowerClimbRun.Stage.RECOVERING;
            save(dungeon, online);
        }
    }

    private void bossCleared(Dungeon dungeon, List<ServerPlayer> online) throws Exception {
        var run = dungeon.tower;
        boolean last = run.floor == TowerClimbRules.FLOOR_COUNT;
        if (!TowerClimbRules.specialBossFloor(run.floor)) run.clearedRandomBosses.add(run.boss);
        run.offer = run.floor / 5;
        run.stage = TowerClimbRun.Stage.SELECTING;
        run.boss = "";
        run.bossEntity = null;
        if (!last) run.floor++;
        owner.clearTowerFloor(dungeon);
        dungeon.enemies.clear();
        var completion = last ? clear.prepare(dungeon.id, online.stream().map(ServerPlayer::getUUID)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))) : null;
        journal.begin();
        try {
            if (completion != null) completion.persist();
            journal.put("instance/" + dungeon.id, dungeon);
            journal.finish();
        } catch (Exception failure) {
            journal.abort();
            throw failure;
        }
        if (completion != null) {
            completion.publish();
            meter.freeze(dungeon);
            for (ServerPlayer player : online) players.message(player, "觉醒塔第 50 层已通关。");
        }
        for (ServerPlayer player : online) players.profile(player);
    }

    private void spawnFloor(Dungeon dungeon, List<ServerPlayer> online) throws Exception {
        var run = dungeon.tower;
        AwakeningTower tower = AwakeningTower.find(dungeon.dungeon);
        boolean boss = TowerClimbRules.bossFloor(run.floor);
        run.boss = boss ? TowerClimbEncounters.boss(tower, run.floor, run.clearedRandomBosses, random) : "";
        var enemies = boss ? List.of(run.boss) : TowerClimbEncounters.ordinary(tower, run.floor, random);
        boolean returnToEntrance = dungeon.wave > 0;
        var playerPositions = returnToEntrance ? List.of(AwakeningTowerLayout.entry(dungeon.cell, tower))
            : online.stream().map(ServerPlayer::position).toList();
        var positions = boss ? List.of(Vec3.atBottomCenterOf(centralGround(dungeon.cell, p -> fits(p,run.boss))))
            : positions(dungeon,enemies,playerPositions);
        owner.clearTowerFloor(dungeon);
        dungeon.enemies.clear();
        dungeon.summoned = false;
        dungeon.wave = run.floor;
        run.bossEntity = null;
        try {
            for (int i = 0; i < enemies.size(); i++) {
                Vec3 point = positions.get(i);
                var entity = owner.spawn(dungeon, enemies.get(i), TowerClimbRules.forceElite(tower), boss, point.x, point.y, point.z);
                if (boss) run.bossEntity = entity.getUUID();
            }
            run.floorStarts = returnToEntrance ? Math.max(2, run.floorStarts + 1) : 1;
            run.stage = TowerClimbRun.Stage.FIGHTING;
            journal.put("instance/" + dungeon.id, dungeon);
        } catch (Exception failure) {
            owner.clearTowerFloor(dungeon);
            throw failure;
        }
        for (ServerPlayer player : online) {
            if (returnToEntrance) {
                player.stopRiding();
                owner.enter(dungeon, player);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
            }
            players.profile(player);
        }
    }

    private void save(Dungeon dungeon, List<ServerPlayer> online) throws Exception {
        journal.put("instance/" + dungeon.id, dungeon);
        for (ServerPlayer player : online) players.profile(player);
    }

    private List<Vec3> positions(Dungeon dungeon, List<String> enemies, List<Vec3> playerPositions) {
        var candidates = new ArrayList<>(spawnGround.computeIfAbsent(dungeon.id, ignored -> walkableGround(dungeon.cell)));
        Collections.shuffle(candidates, random);
        var result = new ArrayList<Vec3>(enemies.size());
        for (String id:enemies) {
            Vec3 found=null;
            for (BlockPos position:candidates) {
                Vec3 center=Vec3.atBottomCenterOf(position);
                if (playerPositions.stream().anyMatch(player -> player.distanceToSqr(center)<9)
                    || result.stream().anyMatch(other -> other.distanceToSqr(center)<4)
                    || !fits(position,id)) continue;
                found=center;
                break;
            }
            if(found==null)throw new IllegalStateException("觉醒塔主战场缺少一整波怪物的安全落点");
            result.add(found);
        }
        return result;
    }

    private boolean fits(BlockPos position,String enemy) {
        ServerLevel world=owner.level();
        if(!AwakeningTowerLayout.walkable(world,position))return false;
        var size=DungeonContentTypes.ENEMIES.get(enemy).get().getDimensions();
        double padding=size.width()/2+.025;
        Vec3 center=Vec3.atBottomCenterOf(position);
        var box=new AABB(center.x-padding,center.y,center.z-padding,center.x+padding,center.y+size.height(),center.z+padding);
        return !world.getBlockCollisions(null,box).iterator().hasNext();
    }

    private static BlockPos centralGround(int cell,Predicate<BlockPos> usable) {
        BlockPos center=BlockPos.containing(AwakeningTowerLayout.guard(cell));
        if(usable.test(center))return center;
        return BlockPos.betweenClosedStream(center.offset(-6,-2,-6),center.offset(6,2,6))
            .filter(usable).map(BlockPos::immutable).min(Comparator.comparingDouble(p -> p.distSqr(center)))
            .orElseThrow(() -> new IllegalStateException("觉醒塔场地中央缺少安全落点"));
    }

    private List<BlockPos> walkableGround(int cell) {
        ServerLevel world = owner.level();
        BlockPos center = BlockPos.containing(AwakeningTowerLayout.guard(cell));
        var reachable = new LinkedHashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        BlockPos first=centralGround(cell, position -> AwakeningTowerLayout.walkable(world,position));
        reachable.add(first);
        queue.add(first);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int height : new int[]{0, 1, -1}) {
                    BlockPos candidate = current.relative(direction).above(height);
                    if (candidate.getY() < FLOOR_HEIGHT - 2 || candidate.getY() > FLOOR_HEIGHT + 2
                        || candidate.distSqr(center) > 22*22
                        || !AwakeningTowerLayout.containsBattle(cell, Vec3.atBottomCenterOf(candidate))
                        || reachable.contains(candidate) || !AwakeningTowerLayout.walkable(world, candidate)) continue;
                    reachable.add(candidate);
                    queue.addLast(candidate);
                }
            }
        }
        return List.copyOf(reachable);
    }

    void close(Dungeon dungeon) throws Exception {
        spawnGround.remove(dungeon.id);
        var deletes = new LinkedHashMap<String, JsonElement>();
        for (String key : journal.keys("towerCards/" + dungeon.id + "/")) deletes.put(key, JsonNull.INSTANCE);
        if (!deletes.isEmpty()) journal.transaction(deletes);
    }
}
