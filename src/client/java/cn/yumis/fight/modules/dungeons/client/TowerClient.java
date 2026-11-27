package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.combat.api.Runtime;
import cn.yumis.fight.modules.dungeons.api.AwakeningTower;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.api.TowerCard;
import cn.yumis.fight.modules.dungeons.api.TowerCardDraft;
import cn.yumis.fight.modules.dungeons.api.TowerCareerDeck;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.dungeons.api.TowerBattleState;
import cn.yumis.fight.modules.dungeons.api.TowerClimbRules;
import cn.yumis.fight.modules.dungeons.api.TowerClimbRun;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/** The local player owns card effects across scenes, and draws new cards during eligible climbs. */
public final class TowerClient {
    private static final Gson JSON = new Gson();
    private static Dungeon dungeon;
    private static TowerCardDraft draft;
    private static TowerCareerDeck.DeckSnapshot retainedDeck;
    private static TowerCareerDeck.Session careerSession;
    private static TowerBattleState battle;
    private static String deckId = "";
    private static String syncedJob = "";
    private static int battleSaved;
    private static long battleSent;
    private static JsonObject pending, pendingStele, pendingCareer;
    private static long sentAt, careerSentAt, recoverAt, nextFire;
    private static int lastFloorStart;
    private static double frostPoints;
    private static long frostGeneration;
    private static Entity watching;
    private static LobbyView lobby;
    private static long combatUntil;
    private static long deckRevision;
    private static Supplier<List<TowerBattleCard.SkillChoice>> skillSupplier = List::of;

    record LobbyView(String id, String title, boolean leader, boolean accepted, String deck, List<Member> members) {}
    record Member(String name, boolean ready) {}

    private TowerClient() {}

    public static void sync(Dungeon next, JsonElement saved, JsonElement savedBattle, Progress progress,
                            Life life, Runtime resources, Supplier<List<TowerBattleCard.SkillChoice>> skills) {
        skillSupplier = skills == null ? List::of : skills;
        deckRevision++;
        combatUntil = resources == null ? 0 : resources.combatUntil;
        if (next != null && next.tower == null) next = null;
        var deck = saved != null && saved.isJsonObject() ? saved.getAsJsonObject() : null;
        var parsedDeck = deck != null && deck.has("id") ? JSON.fromJson(deck, TowerCareerDeck.DeckSnapshot.class) : null;
        boolean hasCards = parsedDeck != null && parsedDeck.hasCards();
        String incoming = next == null ? "" : next.id.toString();
        if (hasCards) incoming = parsedDeck.id();
        String job = progress == null ? "" : progress.jobId();
        boolean newDeck = !deckId.equals(incoming);
        boolean jobChanged = !syncedJob.equals(job);
        boolean newScene = dungeon == null ? next != null : next == null || !dungeon.id.equals(next.id);
        if (!hasCards || newDeck && !deckId.isEmpty()) TowerBattleState.clearShields(resources);
        if (newDeck || newScene) {
            clearScene();
            if (next != null) lastFloorStart = next.tower.floorStarts;
            draft = incoming.isEmpty() ? null : new TowerCardDraft(new Random());
        }
        if (newDeck || jobChanged) {
            clearCareerStele();
            pendingCareer = null;
            careerSentAt = 0;
        }
        if (newDeck) {
            deckId = incoming;
            battle = incoming.isEmpty() ? null : new TowerBattleState();
            battleSent = battleSaved = 0;
        }
        syncedJob = job;
        retainedDeck = hasCards ? mergeLocalCareerState(parsedDeck, job) : null;
        if (careerSession != null && retainedDeck != null) careerSession.syncFrom(retainedDeck);
        if (pendingCareer != null) {
            String sentDeck = pendingCareer.get("deck").getAsString();
            String sentJob = pendingCareer.get("job").getAsString();
            int sentRev = pendingCareer.getAsJsonObject("offer").get("revision").getAsInt();
            if (!sentDeck.equals(deckId) || !sentJob.equals(syncedJob)
                || parsedDeck != null && parsedDeck.offerFor(sentJob).revision() >= sentRev) {
                pendingCareer = null;
            }
        }
        boolean hasInRunDraft = deck != null && deck.has("draft") && deck.get("draft").isJsonObject();
        if (draft != null && retainedDeck != null && !hasInRunDraft) {
            draft.restoreChosen(retainedDeck.activeCards(job));
        }
        if (next == null || dungeon == null || dungeon.tower.stage != next.tower.stage) nextFire = 0;
        dungeon = next;
        if (battle == null) return;
        battle.life(life.generation, resources);
        if (savedBattle != null && savedBattle.isJsonObject()) {
            var checkpoint = JSON.fromJson(savedBattle, TowerBattleState.Checkpoint.class);
            battle.restore(checkpoint);
            battleSaved = Math.max(battleSaved, checkpoint.revision());
        }
        if (hasInRunDraft) {
            var state = JSON.fromJson(deck.get("draft"), TowerCardDraft.Snapshot.class);
            if (state.revision() > draft.revision()) draft.restore(state, TowerCard.pool(progress.jobId(), skills.get()));
            if (pending != null && state.revision() >= pending.getAsJsonObject("draft").get("revision").getAsInt()) pending = null;
        }
        if (draft != null) {
            var heldIds = new java.util.HashSet<String>();
            for (TowerCard card : draft.chosen()) heldIds.add(card.id());
            battle.pruneRemoved(heldIds, resources);
        }
    }

    private static TowerCareerDeck.DeckSnapshot mergeLocalCareerState(TowerCareerDeck.DeckSnapshot incoming, String job) {
        if (incoming == null || job.isEmpty()) return incoming;
        if (careerSession != null && incoming.id().equals(careerSession.deckId()) && job.equals(careerSession.jobId())
            && careerSession.revision() > incoming.offerFor(job).revision()) {
            return incoming.withCareer(job, careerSession.savedCards(), careerSession.savedOffer());
        }
        if (retainedDeck != null && incoming.id().equals(retainedDeck.id())
            && retainedDeck.offerFor(job).revision() > incoming.offerFor(job).revision()) {
            return incoming.withCareer(job, retainedDeck.cardsFor(job), retainedDeck.offerFor(job));
        }
        return incoming;
    }

    public static void tick(Progress progress, Life life, Runnable restore, Runnable fireDamage, DoubleConsumer frostDamage,
                            Supplier<List<TowerBattleCard.SkillChoice>> skills) {
        skillSupplier = skills == null ? List::of : skills;
        long now = ClientUi.now();
        if (battle != null && battle.revision() > battleSaved && now >= battleSent + 1000) saveBattle();
        if (pendingCareer != null && now >= careerSentAt + 1000) transmitCareer();
        var mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof TowerCardScreen screen && screen.steleMode()) {
            if (dungeon != null || life.dead || mc.player == null || DungeonActions.inDungeonDimension()
                || careerSession == null || careerSession.complete() || !nearPendingStele(mc)) {
                closeCareerStele();
            }
        }
        if (dungeon == null || !dungeon.id.toString().equals(life.instance)) return;
        if (mc.player == null || !DungeonActions.inDungeonDimension()) return;
        var run = dungeon.tower;
        boolean canRecover = run.canRecover(mc.player.getUUID());
        updateView(mc, life.dungeonDefeated && !canRecover);
        if (awake() && !life.dead && !life.dungeonDefeated && run.floorStarts > lastFloorStart) {
            lastFloorStart = run.floorStarts;
            if (run.floorStarts > 1) {
                nextFire = 0;
                frostPoints = 0;
                if (battle != null && battle.resetCooldowns()) saveBattle();
                restore.run();
            }
        }
        double accumulated = frostPoints;
        frostPoints = 0;
        if (accumulated > 0 && life.generation == frostGeneration) frostDamage.accept(accumulated);
        if (pending != null && now >= sentAt + 1000) transmit();
        if (life.dungeonDefeated && canRecover && now >= recoverAt) {
            var choice = ClientUi.current().operation("return");
            choice.addProperty("generation", life.generation);
            ClientUi.current().send("dungeonDefeat", choice);
            recoverAt = now + 1000;
        }
        if (!life.dead && !life.dungeonDefeated && !run.keepingCards.contains(mc.player.getUUID())) {
            if (pending == null && draft.offer() < run.offer && draft.open(draft.offer() + 1, TowerCard.pool(progress.jobId(), skills.get()))) save();
            boolean needsChoice = !draft.slots().isEmpty() || draft.offer() < run.offer || run.stage == TowerClimbRun.Stage.SELECTING;
            if (needsChoice) {
                if (!(mc.gui.screen() instanceof TowerCardScreen) && (run.offer != 0 || !DungeonEntryTitle.opening()))
                    mc.gui.setScreen(new TowerCardScreen());
            } else if (mc.gui.screen() instanceof TowerCardScreen) mc.gui.setScreen(null);
        }
        if (!life.dead && awake() && TowerClimbRules.fireDamageActive(tower(), run.floor)) {
            if (nextFire == 0) nextFire = now + 1000;
            if (now >= nextFire) { nextFire = now + 1000; fireDamage.run(); }
        } else nextFire = 0;
    }

    public static Stats apply(Stats stats, Progress progress) {
        if (draft != null) draft.applyPassives(stats, progress, battle, ClientUi.now());
        if (inRun() && DungeonActions.inDungeonDimension()) TowerClimbRules.applyPlayerField(tower(), stats);
        return stats;
    }

    private static AwakeningTower tower() { return dungeon == null ? null : AwakeningTower.find(dungeon.dungeon); }

    public static boolean inRun() { return dungeon != null; }
    public static boolean awake() {
        var player = Minecraft.getInstance().player;
        return dungeon != null && player != null && dungeon.tower.fighting() && dungeon.tower.hasSelected(player.getUUID());
    }
    public static boolean blocksCombat() { return inRun() && !awake(); }

    public static TowerBattleState battle() { return battle; }

    public static String boundSkill(TowerBattleCard card) { return draft == null ? "" : draft.boundSkill(card); }

    public static void saveBattle() {
        if (battle == null || battle.revision() <= battleSaved) return;
        var request = ClientUi.current().operation("towerBattle");
        request.addProperty("deck", deckId);
        request.add("state", JSON.toJsonTree(battle.checkpoint()));
        ClientUi.current().send("towerClimb", request);
        battleSent = ClientUi.now();
    }

    static void receiveBattle(JsonObject packet) {
        if (deckId.equals(packet.get("deck").getAsString()))
            battleSaved = Math.max(battleSaved, packet.get("revision").getAsInt());
    }

    public static void recordFrost(String instance, long generation, double points) {
        if (tower() != AwakeningTower.FROST || !dungeon.id.toString().equals(instance)) return;
        if (generation != frostGeneration) frostPoints = 0;
        frostGeneration = generation;
        frostPoints += points;
    }

    public static void hud(GuiGraphicsExtractor graphics) {
        var mc = Minecraft.getInstance();
        if (!inRun() || !DungeonActions.inDungeonDimension() || mc.gui.screen() instanceof TowerCardScreen) return;
        var font = UiFonts.font();
        String title = tower().dungeon().name() + "  " + floor() + " / " + TowerClimbRules.FLOOR_COUNT;
        if (dungeon.tower.stage == TowerClimbRun.Stage.SELECTING && mc.player != null
            && dungeon.tower.keepingCards.contains(mc.player.getUUID())) title += "  等待开战";
        if (dungeon.tower.stage == TowerClimbRun.Stage.COMPLETED) title += "  已通关";
        int center = graphics.guiWidth() / 2;
        UiTheme.hud(graphics, center - font.width(title) / 2 - 8, 7, font.width(title) + 16, 18);
        graphics.text(font, title, center - font.width(title) / 2, 12, 0xffffffff, true);
    }

    private static void updateView(Minecraft mc, boolean spectate) {
        if (spectate && mc.level != null && mc.player != null) {
            var teammate = mc.level.players().stream().filter(player -> player != mc.player && player.isAlive()
                && player.getHealth() >= .01 && dungeon.roster.contains(player.getUUID())).findFirst().orElse(null);
            if (teammate != null) {
                if (watching != teammate) mc.setCameraEntity(teammate);
                watching = teammate;
                return;
            }
        }
        if (watching != null && mc.getCameraEntity() == watching) mc.setCameraEntity(mc.player);
        watching = null;
    }

    public static int pendingCareerPicks() {
        return retainedDeck == null ? 0 : retainedDeck.pendingCount(syncedJob);
    }

    static boolean tryOpenCareerStele(JsonObject stele) {
        if (inRun() || DungeonActions.inDungeonDimension()) return false;
        if (retainedDeck == null || !retainedDeck.hasCards()) return false;
        if (retainedDeck.pendingCount(syncedJob) <= 0) return false;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (careerSession == null || !careerSession.deckId().equals(retainedDeck.id()) || !careerSession.jobId().equals(syncedJob)) {
            careerSession = new TowerCareerDeck.Session(retainedDeck, syncedJob, new Random());
        }
        pendingStele = stele.deepCopy();
        if (careerSession.ensureOpen()) {
            applyLocalCareerUpdate();
            saveCareerStele();
        }
        if (careerSession.slots().isEmpty()) {
            clearCareerStele();
            return false;
        }
        mc.gui.setScreen(new TowerCardScreen(true));
        return true;
    }

    static int steleRemaining() { return careerSession == null ? 0 : careerSession.pending(); }
    static List<TowerCardDraft.Slot> steleSlots() { return careerSession == null ? List.of() : careerSession.slots(); }
    static int steleRevision() { return careerSession == null ? 0 : careerSession.revision(); }
    static boolean canRefreshCareer(int slot) { return careerSession != null && careerSession.canRefresh(slot); }

    static void refreshCareer(int slot, int version) {
        if (careerSession == null || !careerSession.refresh(slot, version)) return;
        applyLocalCareerUpdate();
        saveCareerStele();
    }

    static void chooseCareer(int slot, int version) {
        if (careerSession == null || careerSession.choose(slot, version).isEmpty()) return;
        applyLocalCareerUpdate();
        saveCareerStele();
        if (careerSession.complete()) {
            clearCareerStele();
            ClientUi.current().message("职业神话卡已补选完成，当前职业牌组已生效。");
        }
    }

    static void closeCareerStele() {
        pendingStele = null;
        var mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof TowerCardScreen screen && screen.steleMode()) mc.gui.setScreen(null);
    }

    static void enterTowerFromCareerStele() {
        var stele = pendingStele;
        closeCareerStele();
        if (stele != null) DungeonActions.joinTower(stele);
    }

    private static void applyLocalCareerUpdate() {
        if (retainedDeck == null || careerSession == null) return;
        retainedDeck = retainedDeck.withCareer(careerSession.jobId(), careerSession.savedCards(), careerSession.savedOffer());
        if (draft != null && !inRun()) draft.restoreChosen(retainedDeck.activeCards(syncedJob));
    }

    private static void saveCareerStele() {
        if (careerSession == null || pendingStele == null || !pendingStele.has("pos")) return;
        pendingCareer = ClientUi.current().operation("towerCareerDeck");
        pendingCareer.addProperty("deck", careerSession.deckId());
        pendingCareer.addProperty("job", careerSession.jobId());
        pendingCareer.addProperty("stele", pendingStele.get("pos").getAsLong());
        pendingCareer.add("cards", JSON.toJsonTree(careerSession.savedCards()));
        pendingCareer.add("offer", JSON.toJsonTree(careerSession.savedOffer()));
        transmitCareer();
    }

    private static void transmitCareer() {
        ClientUi.current().send("towerClimb", pendingCareer);
        careerSentAt = ClientUi.now();
    }

    private static boolean nearPendingStele(Minecraft mc) {
        if (pendingStele == null || !pendingStele.has("pos") || mc.player == null) return false;
        BlockPos pos = BlockPos.of(pendingStele.get("pos").getAsLong());
        return mc.player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }

    private static void clearCareerStele() {
        careerSession = null;
        pendingStele = null;
        var mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof TowerCardScreen screen && screen.steleMode()) mc.gui.setScreen(null);
    }

    static int floor() { return dungeon.tower.floor; }
    static int offer() { return draft.offer(); }
    static int revision() { return draft.revision(); }
    static List<TowerCardDraft.Slot> slots() { return draft.slots(); }
    static List<TowerCard> chosenCards() { return draft == null ? List.of() : draft.chosen(); }
    static List<TowerCard> activeChosenCards() { return chosenCards(); }
    static TowerCareerDeck.DeckSnapshot retainedDeck() { return retainedDeck; }
    static String currentJob() { return syncedJob; }
    static long deckRevision() { return deckRevision; }
    static List<TowerBattleCard.SkillChoice> availableSkills() { return skillSupplier == null ? List.of() : skillSupplier.get(); }
    static boolean finalOffer() { return draft.offer() == TowerClimbRules.FLOOR_COUNT / 5; }
    static boolean careerOffer() { return draft != null && draft.careerOffer(); }
    static boolean canRefresh(int slot) { return draft != null && draft.canRefresh(slot); }

    public static boolean canOpenCreativeLibrary() {
        var player = Minecraft.getInstance().player;
        return player != null && player.isCreative()
            && player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    public static boolean isCreativeLibraryOpen() {
        return Minecraft.getInstance().gui.screen() instanceof TowerCardLibraryScreen;
    }

    public static void openCreativeLibrary() {
        if (!canOpenCreativeLibrary()) return;
        Minecraft.getInstance().gui.setScreen(new TowerCardLibraryScreen());
    }

    public static void closeCreativeLibrary() {
        var mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof TowerCardLibraryScreen) mc.gui.setScreen(null);
    }

    static void creativeAdd(TowerCard.Reference ref) {
        if (!canOpenCreativeLibrary() || ref == null) return;
        clearCareerStele();
        pendingCareer = null;
        var request = ClientUi.current().operation("towerCreativeDeck");
        request.addProperty("op", "add");
        request.add("card", JSON.toJsonTree(ref));
        ClientUi.current().send("towerClimb", request);
    }

    static void creativeRemove(String cardId) {
        if (!canOpenCreativeLibrary() || cardId == null || cardId.isEmpty()) return;
        clearCareerStele();
        pendingCareer = null;
        var request = ClientUi.current().operation("towerCreativeDeck");
        request.addProperty("op", "remove");
        request.addProperty("cardId", cardId);
        ClientUi.current().send("towerClimb", request);
    }

    static void creativeClear() {
        if (!canOpenCreativeLibrary()) return;
        clearCareerStele();
        pendingCareer = null;
        var request = ClientUi.current().operation("towerCreativeDeck");
        request.addProperty("op", "clear");
        ClientUi.current().send("towerClimb", request);
    }

    static void refresh(int slot, int version) {
        if (draft != null && draft.refresh(draft.offer(), slot, version)) save();
    }

    static void choose(int slot, int version) {
        if (draft != null && draft.choose(draft.offer(), slot, version).isPresent()) save();
    }

    static void leave() { ClientUi.current().send("dungeon", ClientUi.current().operation("leave")); }

    private static void save() {
        pending = ClientUi.current().operation("towerCards");
        pending.addProperty("instance", dungeon.id.toString());
        pending.add("draft", JSON.toJsonTree(draft.snapshot()));
        transmit();
    }
    private static void transmit() {
        ClientUi.current().send("towerClimb", pending);
        sentAt = ClientUi.now();
    }

    static void receiveLobby(JsonObject packet) {
        var mc = Minecraft.getInstance();
        String id = packet.get("id").getAsString();
        if (packet.has("closed")) {
            if (lobby != null && id.equals(lobby.id())) {
                lobby = null;
                if (mc.gui.screen() instanceof TowerLobbyScreen) mc.gui.setScreen(null);
            }
            return;
        }
        var rows = new java.util.ArrayList<Member>();
        packet.getAsJsonArray("members").forEach(value -> rows.add(JSON.fromJson(value, Member.class)));
        lobby = new LobbyView(id, packet.get("tower").getAsString(), packet.get("leader").getAsBoolean(),
            packet.get("accepted").getAsBoolean(), packet.get("deck").getAsString(), List.copyOf(rows));
        if (mc.gui.screen() instanceof TowerLobbyScreen) {
            mc.gui.setScreen(new TowerLobbyScreen(lobby));
            return;
        }
        if (packet.get("open").getAsBoolean()) {
            boolean busy = !lobby.leader() && (mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> || ClientUi.now() < combatUntil);
            if (busy) {
                ClientUi.current().message("队长发起了「" + lobby.title() + "」爬塔邀请；当前处于战斗或容器中，已暂不顶掉界面，请完成后前往石碑确认。");
                return;
            }
            mc.gui.setScreen(new TowerLobbyScreen(lobby));
        }
    }

    static void lobbyAction(LobbyView view, String action, boolean keep) {
        if (lobby == null || !lobby.id().equals(view.id())) return;
        var request = ClientUi.current().operation(action);
        request.addProperty("lobby", view.id());
        request.addProperty("deck", view.deck());
        request.addProperty("mode", keep ? "keep" : "fresh");
        ClientUi.current().send("towerClimb", request);
    }

    public static void clear() {
        clearScene();
        clearCareerStele();
        deckId = "";
        syncedJob = "";
        retainedDeck = null;
        pendingCareer = null;
        careerSentAt = 0;
        draft = null;
        battle = null;
        battleSaved = 0;
        battleSent = 0;
        lobby = null;
        combatUntil = 0;
        skillSupplier = List::of;
        deckRevision++;
        var screen = Minecraft.getInstance().gui.screen();
        if (screen instanceof TowerLobbyScreen || screen instanceof TowerCardLibraryScreen) Minecraft.getInstance().gui.setScreen(null);
    }
    private static void clearScene() {
        dungeon = null;
        pending = null;
        nextFire = recoverAt = 0;
        lastFloorStart = 0;
        frostPoints = 0;
        updateView(Minecraft.getInstance(), false);
        if (Minecraft.getInstance().gui.screen() instanceof TowerCardScreen) Minecraft.getInstance().gui.setScreen(null);
    }
}
