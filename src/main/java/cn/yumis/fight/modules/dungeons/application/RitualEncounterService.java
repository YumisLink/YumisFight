package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.combat.api.CombatPorts;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.combat.api.Receipt;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.RitualBossContent;
import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.network.Network;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.BiPredicate;

/** World ritual boss runs at demon altars. All mutable state belongs to the dungeons module. */
public final class RitualEncounterService implements CombatPorts.SkillEffects {
    private static final Gson JSON = new Gson();
    private static final String TEXT = "demon_altar." + cn.yumis.fight.platform.ItemData.NS + ".";
    private final Journal journal;
    private final MinecraftServer server;
    private final InventoryOperations inventory;
    private final WorldTargets targets;
    private final RitualPorts.AltarPower power;
    private final RitualPorts.Rewards rewards;
    private final BiPredicate<ServerLevel,LivingEntity> spawnActor;
    private final Random random;
    private final Map<String, Run> runs = new LinkedHashMap<>();
    private final Map<UUID, String> runsByBoss = new HashMap<>();
    private int tick;

    public static final class Run {
        public String runId;
        public String bossId;
        public String dimension;
        public BlockPos altar;
        public String altarKey;
        public Vec3 origin;
        public Vec3 spawn;
        public int radius;
        public UUID bossEntity;
        public Target bossTarget;
        public long committedAt, countdownUntil, startedAt, limitUntil;
        public int openingPlayers;
        public String status = "PENDING";
        public String ending = "";
        public final LinkedHashSet<String> joined = new LinkedHashSet<>();
        private final Set<String> damaged = new LinkedHashSet<>();
        private Set<BlockPos> floor = Set.of();
        public final List<UUID> mechanisms = new ArrayList<>();
        public final Map<String, Long> moveUsedAt = new HashMap<>();
        public final Map<String, UUID> casts = new LinkedHashMap<>();
        final Map<String, Long> castEnds = new HashMap<>();
        public final Map<String, JsonObject> safeSpots = new LinkedHashMap<>();
        public final Map<String, String> hudLines = new LinkedHashMap<>();
        public RitualBossContent content;
        private RitualBossCatalog.Boss definition;
        public long noSurvivorSince;
        public long summonMana;

        boolean terminal() { return status.equals("CLEARED") || status.equals("FAILED"); }

        ServerLevel level(MinecraftServer server) {
            for (ServerLevel candidate : server.getAllLevels())
                if (candidate.dimension().identifier().toString().equals(dimension)) return candidate;
            return null;
        }
    }

    public RitualEncounterService(Journal journal, MinecraftServer server, InventoryOperations inventory,
                                  WorldTargets targets, RitualPorts.AltarPower power, RitualPorts.Rewards rewards,
                                  BiPredicate<ServerLevel,LivingEntity> spawnActor, Random random) {
        this.journal = journal; this.server = server; this.inventory = inventory; this.targets = targets;
        this.power = power; this.rewards = rewards; this.spawnActor = spawnActor; this.random = random;
        recover();
    }

    /** World unlock records are per save, shared by every dimension and every placed altar. */
    public boolean unlocked(String bossId) { return !journal.get("ritual/unlock/" + bossId).isJsonNull(); }

    public void unlock(ServerPlayer player, BlockPos pos, ItemStack stack) {
        if (!power.usable(player, pos) || !cn.yumis.fight.platform.ItemData.own(stack)) return;
        String token = cn.yumis.fight.platform.ItemData.id(stack);
        var boss = RitualBossCatalog.current().ordered().stream().filter(entry -> entry.token().equals(token)).findFirst().orElse(null);
        if (boss == null) return;
        String bossId = boss.id();
        if (unlocked(bossId)) {
            player.sendOverlayMessage(Component.translatable(TEXT + "already_unlocked",
                    ModText.of(boss.name())));
            return;
        }
        JsonObject intent = new JsonObject();
        intent.addProperty("action", "unlock");
        intent.addProperty("boss", bossId);
        intent.addProperty("by", player.getStringUUID());
        JsonObject record = new JsonObject();
        record.addProperty("boss", bossId);
        record.addProperty("by", player.getStringUUID());
        record.addProperty("at", System.currentTimeMillis());
        record.addProperty("altar", pos.getX() + "," + pos.getY() + "," + pos.getZ());
        try {
            if (!inventory.transact(player, "altar-unlock/" + bossId, intent, () -> stack.shrink(1),
                    Map.of("ritual/unlock/" + bossId, record))) return;
            player.sendOverlayMessage(Component.translatable(TEXT + "unlocked", ModText.of(boss.name())));
        } catch (RuntimeException failure) {
            message(player, "解锁失败：" + failure.getMessage());
        }
    }

    public void sendMenu(ServerPlayer player, ServerLevel level, BlockPos pos, long stored, boolean connected, long grid) {
        JsonObject menu = new JsonObject();
        menu.addProperty("stored", stored);
        menu.addProperty("connected", connected);
        menu.addProperty("grid", grid);
        menu.addProperty("x", pos.getX());
        menu.addProperty("y", pos.getY());
        menu.addProperty("z", pos.getZ());
        menu.addProperty("dimension",level.dimension().identifier().toString());
        Run active = activeAt(level, pos);
        if (active != null) {
            menu.addProperty("active", active.definition.name());
            menu.addProperty("canJoin", active.status.equals("PENDING") && !active.joined.contains(player.getStringUUID()));
        }
        var entries = new ArrayList<JsonObject>();
        for (var boss : RitualBossCatalog.current().ordered()) {
            if (!unlocked(boss.id())) continue; // 未解锁的 Boss 不显示
            var entry = new JsonObject();
            entry.addProperty("id", boss.id());
            entry.addProperty("name", boss.name());
            entry.addProperty("level", boss.level());
            entry.addProperty("cost", boss.summonMana());
            entry.addProperty("affordable", stored + (connected ? grid : 0) >= boss.summonMana());
            if (active == null && entry.get("affordable").getAsBoolean()) {
                try { preflight(level, pos, player, boss); }
                catch (IllegalArgumentException blocked) { entry.addProperty("obstacle", blocked.getMessage()); }
            }
            entries.add(entry);
        }
        menu.add("bosses", JSON.toJsonTree(entries));
        menu.addProperty("at", System.currentTimeMillis());
        Network.send(player, "ritualMenu", menu);
    }

    // ------------------------------------------------------------------ client commands

    public void summonCommand(ServerPlayer player, JsonObject j) {
        try {
            BlockPos altar = new BlockPos(j.get("x").getAsInt(), j.get("y").getAsInt(), j.get("z").getAsInt());
            String bossId = j.get("boss").getAsString();
            validateInteraction(player,j);
            summon(player, altar, bossId, UUID.fromString(j.get("operation").getAsString()));
        } catch (RuntimeException failure) {
            message(player, Objects.toString(failure.getMessage(), "召唤请求未完成"));
        }
    }

    public void joinCommand(ServerPlayer player, JsonObject j) {
        try {
            BlockPos altar = new BlockPos(j.get("x").getAsInt(), j.get("y").getAsInt(), j.get("z").getAsInt());
            validateInteraction(player,j);
            join(player, altar);
        } catch (RuntimeException failure) {
            message(player, Objects.toString(failure.getMessage(), "参加请求未完成"));
        }
    }

    // ------------------------------------------------------------------ summon lifecycle

    private void validateInteraction(ServerPlayer player,JsonObject request) {
        if (!player.level().dimension().identifier().toString().equals(request.get("dimension").getAsString()))
            throw new IllegalArgumentException("魔王祭坛所在维度已改变");
    }

    void summon(ServerPlayer player, BlockPos altar, String bossId, UUID operation) {
        var boss = RitualBossCatalog.current().find(bossId);
        if (boss == null) throw new IllegalArgumentException("未知魔王：" + bossId);
        ServerLevel level = player.level();
        if (!power.usable(player, altar))
            throw new IllegalArgumentException("魔王祭坛不在此处");
        String runId = operation.toString();
        if (!journal.get("ritual/run/" + runId).isJsonNull()) return;
        if (!unlocked(bossId)) throw new IllegalArgumentException("本存档尚未解锁此魔王");
        if (activeAt(level, altar) != null) throw new IllegalArgumentException("此祭坛已有一场进行中的召唤");
        var arena = preflight(level, altar, player, boss);
        Vec3 origin = new Vec3(altar.getX() + .5, altar.getY(), altar.getZ() + .5);
        for (Run run : runs.values())
            if (run.dimension.equals(level.dimension().identifier().toString()) && !run.terminal())
                if (run.origin.distanceTo(origin) < run.radius + boss.arena().radius() + 4)
                    throw new IllegalArgumentException("附近已有活动战圈，请先结束那一场");

        Run run = new Run();
        run.runId = runId; run.bossId = bossId;
        run.dimension = level.dimension().identifier().toString();
        run.altar = altar; run.altarKey = power.identity(level, altar);
        run.origin = origin; run.spawn = arena.spawn(); run.floor = arena.floor(); run.radius = boss.arena().radius();
        run.definition = boss;
        run.summonMana = boss.summonMana();
        run.committedAt = System.currentTimeMillis();
        run.countdownUntil = run.committedAt + boss.countdownSeconds() * 1000;
        run.joined.add(player.getStringUUID());
        run.content = RitualBossContent.create(bossId, this);

        try {
            if (!power.commitSummon(level, altar, boss.summonMana(), runId, Map.of("ritual/run/" + runId, runRecord(run)))) {
                throw new IllegalArgumentException("魔能不足，无法召唤" + boss.name());
            }
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("召唤记录提交失败", failure);
        }
        runs.put(runId, run);
        broadcastArena(run, "ritualRun", runState(run));
    }

    private record ArenaPlan(Vec3 spawn, Set<BlockPos> floor) {}

    /** Solid footing, clear spawn volume and routes to the positions used for ground mechanisms. */
    private ArenaPlan preflight(ServerLevel level, BlockPos altar, ServerPlayer summoner, RitualBossCatalog.Boss boss) {
        Vec3 center = Vec3.atBottomCenterOf(altar);
        var arena = boss.arena();
        var floor = reachableFloor(level, altar, BlockPos.containing(summoner.position()), arena.radius());
        int outerRadius = arena.radius() - 3;
        if (floor.stream().noneMatch(point -> Vec3.atBottomCenterOf(point).subtract(center).horizontalDistanceSqr() >= outerRadius * outerRadius))
            throw new IllegalArgumentException("战圈不能通行：请从祭坛 " + altar.toShortString() + " 附近留一条通往场边的路");
        Vec3 offset = summoner.position().subtract(center);
        Vec3 preferred = offset.horizontalDistanceSqr() < .01 ? new Vec3(1, 0, 0) : new Vec3(offset.x, 0, offset.z).normalize();
        var clearance = arena.clearance();
        BlockPos first = BlockPos.containing(center.add(preferred.scale(arena.spawnAhead())));
        for (int step = 0; step < 12; step++) {
            Vec3 candidate = center.add(rotateY(preferred, step * Math.PI / 6).scale(arena.spawnAhead()));
            BlockPos column = BlockPos.containing(candidate);
            for (int dy : new int[]{0, 1, -1, 2, -2, -3, -4, -5}) {
                BlockPos ground = column.above(dy);
                if (floor.contains(ground) && clear(level, Vec3.atBottomCenterOf(ground), clearance))
                    return new ArenaPlan(Vec3.atBottomCenterOf(ground), floor);
            }
        }
        throw new IllegalArgumentException("首领出生点受阻：请清理 " + first.toShortString() + " 附近的 " + (int) clearance.length() + "×"
                + (int) clearance.width() + "×" + (int) clearance.height() + " 格净空，或从祭坛另一侧召唤");
    }

    static Vec3 rotateY(Vec3 vector, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new Vec3(vector.x * cos - vector.z * sin, vector.y, vector.x * sin + vector.z * cos);
    }

    private static final RitualBossCatalog.Clearance WALKER = new RitualBossCatalog.Clearance(.8, .8, 2);

    private static Set<BlockPos> reachableFloor(ServerLevel level, BlockPos altar, BlockPos entrance, int radius) {
        var floor = new LinkedHashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        var checked = new HashSet<BlockPos>();
        for (int dy : new int[]{0, -1, 1, -2}) {
            var start = entrance.above(dy);
            if (walkable(level, start)) { floor.add(start); queue.add(start); break; }
        }
        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos column = current.relative(direction);
                int x = column.getX() - altar.getX(), z = column.getZ() - altar.getZ();
                if (x * x + z * z > radius * radius) continue;
                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos next = column.above(dy);
                    if (Math.abs(next.getY() - altar.getY()) <= 8 && checked.add(next) && walkable(level, next)) {
                        floor.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return floor;
    }

    private static boolean walkable(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        return level.hasChunkAt(feet) && level.getWorldBorder().isWithinBounds(feet)
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getFluidState(feet).isEmpty() && clear(level, Vec3.atBottomCenterOf(feet), WALKER);
    }

    static boolean clear(ServerLevel level, Vec3 at, RitualBossCatalog.Clearance size) {
        double halfLength = size.length() / 2, halfWidth = size.width() / 2;
        AABB box = new AABB(at.x - halfLength, at.y + .1, at.z - halfWidth, at.x + halfLength, at.y + size.height(), at.z + halfWidth);
        return box.minY >= level.getMinY() && box.maxY <= level.getMaxY()
                && level.hasChunkAt(BlockPos.containing(box.minX, at.y, box.minZ))
                && level.hasChunkAt(BlockPos.containing(box.maxX, at.y, box.maxZ))
                && !level.getBlockCollisions(null, box).iterator().hasNext();
    }

    void join(ServerPlayer player, BlockPos altar) {
        if (!power.usable(player, altar)) throw new IllegalArgumentException("请靠近魔王祭坛");
        Run run = activeAt(player.level(), altar);
        if (run == null) throw new IllegalArgumentException("此祭坛当前没有可参加的召唤");
        if (!run.status.equals("PENDING")) throw new IllegalArgumentException("倒计时已结束");
        if (run.joined.add(player.getStringUUID()))
            broadcastArena(run, "ritualRun", runState(run));
    }

    Run activeAt(ServerLevel level, BlockPos altar) {
        for (Run run : runs.values())
            if (!run.terminal() && run.dimension.equals(level.dimension().identifier().toString())
                    && run.altar.equals(altar)) return run;
        return null;
    }

    Run pendingAt(ServerLevel level, BlockPos altar) {
        Run run = activeAt(level, altar);
        return run != null && run.status.equals("PENDING") ? run : null;
    }

    Run byBoss(UUID entity) {
        String runId = runsByBoss.get(entity);
        return runId == null ? null : runs.get(runId);
    }

    // ------------------------------------------------------------------ publish and tick

    void publish(ServerLevel level, Run run) {
        var boss = run.definition;
        var kind = AltarBossKind.find(run.bossId);
        RitualBossEntity entity = new RitualBossEntity(DungeonContentTypes.ENEMIES.get(kind.id).get(), level);
        entity.getPersistentData().putInt("ydnf_level", boss.level());
        entity.getPersistentData().putBoolean("ydnf_boss", true);
        entity.getPersistentData().putString("ydnf_ritual_run", run.runId);
        entity.setCustomName(ModText.of(boss.name()));
        entity.scripted();
        entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                .setBaseValue(Math.clamp(boss.speed() * .023, .03, .35));
        entity.setPos(run.spawn.x, run.spawn.y, run.spawn.z);
        entity.setYRot(yawToward(run.spawn, run.origin));
        run.bossEntity = entity.getUUID();
        Target target = initialTarget(run,entity);
        target.kind = "BOSS";
        target.originalLevel = boss.level();
        target.fixedLevel = true;
        run.openingPlayers = Math.max(1,fightersInCircle(run).size());
        target.partySize = run.openingPlayers;
        target.difficulty = Difficulty.WARRIOR;
        target.speciesHealth = boss.healthMultiplier() / Difficulty.WARRIOR.health;
        if (run.bossId.equals("debt_collector") || run.bossId.equals("thousandfold_confession")) {
            target.encounterLives = 2;
            target.encounterReviveMillis = Long.MAX_VALUE / 2;
        }
        run.bossTarget = target;
        entity.configure(target);
        run.status = "RUNNING";
        run.startedAt = System.currentTimeMillis();
        run.limitUntil = run.startedAt + boss.limitSeconds() * 1000;
        run.noSurvivorSince = run.startedAt;
        var projection = commitActor(run, entity, target);
        if (!spawnActor.test(level,entity)) throw new IllegalStateException("首领实体生成失败");
        projection.publish();
        runsByBoss.put(entity.getUUID(), run.runId);
        run.content.begin(run);
        if(!boss.intro().isBlank())for(var player:fightersInCircle(run))message(player,boss.intro());
        broadcastArena(run, "ritualRun", runState(run));
    }

    static float yawToward(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dz = to.z - from.z;
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
    }

    public void tick() {
        tick++;
        if (tick % 2 != 0) return;
        long now = System.currentTimeMillis();
        for (Run run : new ArrayList<>(runs.values())) {
            ServerLevel level = run.level(server);
            if(run.terminal()){
                if(now>=run.limitUntil)cleanup(run);
                continue;
            }
            if (level == null || !run.altarKey.equals(power.identity(level,run.altar))) {
                end(run,"FAILED","altar_lost");continue;
            }
            if (run.status.equals("PENDING")) {
                if (now >= run.countdownUntil) {
                    try { publish(level, run); }
                    catch (RuntimeException failure) { end(run, "FAILED", "spawn_failure"); }
                } else if (tick % 10 == 0) broadcastArena(run, "ritualRun", runState(run));
                continue;
            }
            if (!run.status.equals("RUNNING")) continue;
            // 参战者在圈内存活检查、时限
            var participants = participants(run);
            boolean survivor = participants.stream().anyMatch(player -> !player.isDeadOrDying() && inCircle(run, player));
            if (!participants.isEmpty() && participants.stream().allMatch(ServerPlayer::isDeadOrDying)) {
                end(run,"FAILED","party_wipe"); continue;
            }
            if (survivor) run.noSurvivorSince = now;
            else if (now - run.noSurvivorSince >= run.definition.noSurvivorSeconds() * 1000) {
                end(run, "FAILED", "no_survivor"); continue;
            }
            if (now >= run.limitUntil) { end(run, "FAILED", "limit"); continue; }
            Entity entity = level.getEntity(run.bossEntity);
            if (!(entity instanceof RitualBossEntity boss) || !boss.isAlive()) {
                if (run.ending.isEmpty()) { end(run, "FAILED", "boss_lost"); }
                continue;
            }
            run.castEnds.entrySet().removeIf(cast -> {
                if(now<cast.getValue())return false;
                run.casts.remove(cast.getKey());return true;
            });
            run.safeSpots.values().removeIf(spot->now>=spot.get("until").getAsLong());
            if (!cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(boss)) run.content.tick(run, boss, now);
            if (tick % 10 == 0) broadcastArena(run, "ritualRun", runState(run));
        }
    }

    public RitualBossCatalog.Boss catalog(Run run) { return run.definition; }

    List<ServerPlayer> participants(Run run) {
        var result = new ArrayList<ServerPlayer>();
        for (var player : server.getPlayerList().getPlayers()) {
            if (player.hasDisconnected()) continue;
            if (run.joined.contains(player.getStringUUID())
                    || run.damaged.contains(player.getStringUUID())
                    || run.content != null && run.content.handled(player.getStringUUID()))
                result.add(player);
        }
        return result;
    }

    public List<ServerPlayer> fightersInCircle(Run run) {
        var result = new ArrayList<ServerPlayer>();
        for (var player : participants(run))
            if (!player.isDeadOrDying() && inCircle(run, player))
                result.add(player);
        return result;
    }

    // ------------------------------------------------------------------ damage, death and interaction hooks

    @Override public CombatPorts.Commit prepare(ServerPlayer player,LivingEntity entity,JsonObject report,Receipt receipt,Life actorLife) {
        if (!(entity instanceof RitualBossEntity || entity instanceof RitualMechanismEntity)
                || !receipt.accepted() || receipt.actual() <= 0) return CombatPorts.Commit.NONE;
        if (entity instanceof RitualBossEntity&&!receipt.deathId().isEmpty()) {
            Run run=byBoss(entity.getUUID());
            if(run==null||!run.status.equals("RUNNING"))return CombatPorts.Commit.NONE;
            var completion=prepareEnd(run,"CLEARED","killed",inCircle(run,player)?player.getStringUUID():null);
            return new CombatPorts.Commit(){
                @Override public void persist() throws Exception {journal.transaction(completion.writes());}
                @Override public void publish(){run.bossTarget=targets.target(entity);completion.publish().run();}
            };
        }
        return new CombatPorts.Commit(){
            @Override public void publish(){committedDamage(player,entity,targets.target(entity),receipt.actual());}
        };
    }

    /** Called after the combat receipt is durable. */
    public void committedDamage(ServerPlayer player, LivingEntity entity, Target target, double fraction) {
        if (entity instanceof RitualMechanismEntity mechanism) {
            Run run=byMechanism(mechanism);
            if(target.life.dead&&run!=null)mechanismBroken(mechanism,inCircle(run,player)?player.getStringUUID():null);
            return;
        }
        Run run = byBoss(target.id());
        if (run == null || !run.status.equals("RUNNING")) return;
        run.bossTarget = target;
        if(inCircle(run,player))run.damaged.add(player.getStringUUID());
        run.content.damaged(run, player, fraction, target);
        if (target.life.dead && !run.terminal()) {
            run.content.death(run, target);
            if (!run.terminal()) end(run, "CLEARED", "killed");
        }
    }

    public boolean cancelBoss(UUID entityId) {
        Run run = byBoss(entityId);
        if (run == null) return false;
        for (String move : new ArrayList<>(run.casts.keySet())) cancelMove(run, move);
        return true;
    }

    /** Boss interaction (head confirmations, listener dialogue). */
    public boolean bossInteract(ServerPlayer player, RitualBossEntity boss) {
        Run run = byBoss(boss.getUUID());
        return run != null && run.status.equals("RUNNING") && inCircle(run,player) && run.content.bossInteract(run, player, boss);
    }

    private void mechanismBroken(RitualMechanismEntity mechanism,String breaker) {
        Run run = byMechanism(mechanism);
        if (run == null || run.terminal() || !run.mechanisms.remove(mechanism.getUUID())) return;
        run.content.mechanismBroken(run, mechanism.kind(), mechanism.getUUID(), breaker);
        targets.release(List.of(mechanism));
        mechanism.discard();
    }

    public boolean mechanismInteract(ServerPlayer player, RitualMechanismEntity mechanism) {
        Run run = byMechanism(mechanism);
        if (run == null || run.terminal() || !inCircle(run,player)) return false;
        boolean handled = run.content.mechanismInteract(run, player, mechanism.kind(), mechanism.getUUID());
        if (handled) persistRun(run);
        return handled;
    }

    Run byMechanism(RitualMechanismEntity mechanism) {
        Run run=runs.get(mechanism.ritualRun());
        return run!=null&&run.mechanisms.contains(mechanism.getUUID())?run:null;
    }

    private static boolean inCircle(Run run,ServerPlayer player) {
        Vec3 offset=player.position().subtract(run.origin);
        return !player.isSpectator()&&player.level().dimension().identifier().toString().equals(run.dimension)
            &&Math.abs(offset.y)<=8&&offset.horizontalDistanceSqr()<=(double)run.radius*run.radius;
    }

    // ------------------------------------------------------------------ move engine

    /** Content starts one cast: the packet drives the model animation, the telegraph and the local contacts. */
    public void startMove(Run run, RitualBossCatalog.Move move, Vec3 origin, Vec3 direction, UUID lockedPlayer) {
        long now = System.currentTimeMillis();
        run.moveUsedAt.put(move.id(), now);
        String castId = UUID.randomUUID().toString();
        JsonObject packet = new JsonObject();
        packet.addProperty("id", castId);
        packet.addProperty("runId", run.runId);
        packet.add("boss", JSON.toJsonTree(run.bossTarget));
        packet.addProperty("kind", move.id());
        packet.addProperty("startedAt", now);
        packet.addProperty("durationMs", move.durationMs());
        packet.addProperty("arenaX",run.origin.x);packet.addProperty("arenaY",run.origin.y);packet.addProperty("arenaZ",run.origin.z);
        packet.addProperty("arenaRadius",run.radius);
        var contacts = new ArrayList<Long>();
        for (long contact : move.contactMs()) contacts.add(contact);
        packet.add("contactMs", JSON.toJsonTree(contacts));
        var shape = move.shape();
        if (shape != null) {
            packet.addProperty("shapeType", shape.type());
            packet.addProperty("radius", shape.radius());
            packet.addProperty("range", shape.range());
            packet.addProperty("angle", shape.angle());
            packet.addProperty("length", shape.length());
            packet.addProperty("width", shape.width());
            packet.addProperty("spacing", shape.spacing());
        }
        packet.addProperty("x", origin.x);
        packet.addProperty("y", origin.y);
        packet.addProperty("z", origin.z);
        if (direction != null) { packet.addProperty("dx", direction.x); packet.addProperty("dz", direction.z); }
        if (lockedPlayer != null) packet.addProperty("lockedPlayer", lockedPlayer.toString());
        if (move.damage() != null) {
            packet.addProperty("coefficient", move.damage().coefficient());
            packet.addProperty("magic", move.damage().type().equals("magic"));
            packet.addProperty("element", ElementIds.of(move.damage().element()));
        } else { packet.addProperty("coefficient", 0); packet.addProperty("magic", false); packet.addProperty("element", -1); }
        packet.addProperty("hitLimit", move.hitLimit());
        if (move.linger() != null) {
            var linger = new JsonObject();
            linger.addProperty("ms", move.linger().ms());
            linger.addProperty("tickMs", move.linger().tickMs());
            linger.addProperty("perTickCoefficient", move.linger().perTickCoefficient());
            linger.addProperty("hitLimit", move.linger().hitLimit());
            linger.addProperty("magic", move.linger().type().equals("magic"));
            linger.addProperty("element", ElementIds.of(move.linger().element()));
            packet.add("linger", linger);
        }
        if (move.blocksBehindCover() != null && move.blocksBehindCover())
            packet.addProperty("blocksBehindCover", true);
        if (move.slowSeconds() != null) { packet.addProperty("selfSlowSeconds", move.slowSeconds()); packet.addProperty("selfSlowAmplifier", 1); }
        if (move.rootSeconds() != null) { packet.addProperty("selfRootSeconds", move.rootSeconds()); }
        if (move.freezeSeconds() != null) packet.addProperty("selfStunSeconds", move.freezeSeconds());
        if (move.sitSeconds() != null) packet.addProperty("selfSitSeconds", move.sitSeconds());
        if (move.healDownSeconds() != null) packet.addProperty("selfHealDownSeconds", move.healDownSeconds());
        if (move.debtSlowSeconds() != null) packet.addProperty("selfDebtSeconds",move.debtSlowSeconds());
        if (move.safeSpots() != null && !move.safeSpots().isEmpty()) packet.add("safeKinds", JSON.toJsonTree(move.safeSpots()));
        run.casts.put(move.id(), UUID.fromString(castId));
        run.castEnds.put(move.id(),now+move.durationMs());
        Entity entity = run.level(server) == null ? null : run.level(server).getEntity(run.bossEntity);
        if (entity instanceof RitualBossEntity boss) {
            boss.getNavigation().stop();
            if(direction!=null)boss.setYRot(yawToward(origin,origin.add(direction)));
            boss.play(move.animation(), move.durationMs());
        }
        broadcastArena(run, "ritualAttack", packet);
    }

    public void cancelMove(Run run, String moveId) {
        UUID cast = run.casts.remove(moveId);
        run.castEnds.remove(moveId);
        if (cast == null) return;
        if (entity(run,run.bossEntity) instanceof RitualBossEntity boss && boss.move().equals(run.definition.move(moveId).animation()))
            boss.play("",0);
        JsonObject packet = new JsonObject();
        packet.addProperty("id", cast.toString());
        packet.addProperty("runId", run.runId);
        packet.addProperty("cancelled", true);
        broadcastArena(run, "cancelRitualAttack", packet);
    }

    public boolean onCooldown(Run run, RitualBossCatalog.Move move, long now) {
        Long usedAt = run.moveUsedAt.get(move.id());
        return usedAt != null && now - usedAt < move.cooldownMs();
    }

    // ------------------------------------------------------------------ mechanisms

    public UUID spawnMechanism(Run run, String kind, double health, boolean immune, Vec3 position) {
        ServerLevel level = run.level(server);
        if (level == null) return null;
        var occupied = new HashSet<BlockPos>();
        for (UUID id : run.mechanisms) if (level.getEntity(id) instanceof RitualMechanismEntity active) occupied.add(active.blockPosition());
        var ground = run.floor.stream().filter(point -> !occupied.contains(point))
                .sorted(Comparator.comparingDouble(point -> Vec3.atBottomCenterOf(point).distanceToSqr(position)))
                .filter(point -> walkable(level, point)).findFirst().orElse(null);
        if (ground == null) return null;
        RitualMechanismEntity mechanism = new RitualMechanismEntity(DungeonContentTypes.MECHANISMS.get(kind).get(), level);
        mechanism.kind(kind);
        mechanism.bindRun(run.runId);
        var actual = Vec3.atBottomCenterOf(ground);
        mechanism.setPos(actual.x, actual.y, actual.z);
        Target target = initialTarget(run,mechanism);
        target.kind = "NORMAL";
        target.fixedLevel = true;
        target.noRewards = true;
        target.partySize = 1;
        target.damageImmune = immune;
        target.speciesHealth = immune ? 1 : health / target.health(target.originalLevel);
        mechanism.configure(target);
        mechanism.setCustomName(ModText.of(mechanismName(kind)));
        mechanism.setCustomNameVisible(true);
        run.mechanisms.add(mechanism.getUUID());
        var projection = commitActor(run, mechanism, target);
        if (!spawnActor.test(level,mechanism)) {
            removeMechanism(run, mechanism.getUUID());
            return null;
        }
        projection.publish();
        return mechanism.getUUID();
    }

    private CombatPorts.Commit commitActor(Run run, LivingEntity actor, Target target) {
        var projection = targets.prepare(Map.of(actor, target));
        try {
            journal.begin();
            projection.persist();
            persistRun(run);
            journal.finish();
            return projection;
        } catch (Exception failure) {
            journal.abort();
            throw new IllegalStateException("祭坛实体保存失败", failure);
        }
    }

    public Vec3 floorNear(Run run, Vec3 position) {
        var level=run.level(server);
        if(level==null)return null;
        return run.floor.stream().map(Vec3::atBottomCenterOf)
                .filter(point -> point.subtract(position).horizontalDistanceSqr() < 1)
                .sorted(Comparator.comparingDouble(point -> point.distanceToSqr(position)))
                .filter(point -> walkable(level, BlockPos.containing(point))).findFirst().orElse(null);
    }

    private Target initialTarget(Run run,LivingEntity entity) {
        Target target = new Target();
        target.id = entity.getUUID();
        target.entityId = entity.getId();
        target.life.target = target.id;
        target.dimension = run.dimension;
        target.life.dimension = run.dimension;
        target.originalLevel = run.definition.level();
        target.fixedLevel = true;
        return target;
    }

    private static String mechanismName(String kind) {
        return switch(kind) {
            case "ritual_fake_branch" -> "假枝";
            case "ritual_hedge" -> "树篱分身";
            case "ritual_root_knot" -> "根结";
            case "ritual_sprout" -> "活芽";
            case "ritual_spring" -> "发条";
            case "ritual_eye_coin" -> "眼钱";
            case "ritual_pull_line" -> "拉线";
            case "ritual_anti_magic" -> "反魔力阵";
            case "ritual_resonance" -> "共振台";
            case "ritual_broken_watch" -> "裂表";
            case "ritual_empty_pan" -> "空秤盘";
            case "ritual_campfire" -> "临时篝火";
            case "ritual_stele" -> "战绩碑";
            case "ritual_confessional" -> "告解台";
            case "ritual_shard" -> "启迪晶片";
            default -> "祭坛机关";
        };
    }

    public Entity entity(Run run, UUID id) {
        ServerLevel level = run.level(server);
        return level == null || id == null ? null : level.getEntity(id);
    }

    public void removeMechanism(Run run,UUID id) {
        if(id==null||!run.mechanisms.remove(id))return;
        Entity actor=entity(run,id);
        if(actor!=null)actor.discard();
        targets.releaseIds(List.of(id));
    }

    public void repair(Run run,double fraction) {
        if(run.bossTarget==null||run.bossTarget.damageImmune||fraction<=0)return;
        if(entity(run,run.bossEntity) instanceof RitualBossEntity boss){
            var target=targets.target(boss);
            cn.yumis.fight.modules.combat.api.LifeRules.apply(target.life,fraction,true);
            targets.commit(Map.of(boss,target));
            run.bossTarget=target;
        }
    }

    public void clearDebt(ServerPlayer player) { Network.send(player,"ritualClearDebt",new JsonObject()); }

    public void addSafeSpot(Run run, String kind, String key, double x, double y, double z, double radius, long until) {
        JsonObject spot = new JsonObject();
        spot.addProperty("kind", kind);
        spot.addProperty("x", x); spot.addProperty("y", y); spot.addProperty("z", z);
        spot.addProperty("radius", radius);
        spot.addProperty("until", until);
        run.safeSpots.put(key, spot);
    }

    public void hudLine(Run run, String key, String line) {
        if (line == null) run.hudLines.remove(key);
        else run.hudLines.put(key, line);
    }

    // ------------------------------------------------------------------ endings and settlement

    public void finishRun(Run run, String ending) {
        if (run.terminal()) return;
        end(run, "CLEARED", ending);
    }

    void end(Run run, String status, String ending) {
        var completion = prepareEnd(run, status, ending, null);
        try {
            journal.transaction(completion.writes());
        } catch (Exception failure) {
            throw new IllegalStateException("魔王祭坛结算保存失败",failure);
        }
        completion.publish().run();
    }

    private RitualPorts.ClearCommit prepareEnd(Run run, String status, String ending, String finisher) {
        var writes = new LinkedHashMap<String,JsonElement>();
        var record = runRecord(run);
        record.addProperty("status", status);
        record.addProperty("ending", ending);
        writes.put("ritual/run/" + run.runId, record);
        RitualPorts.ClearCommit settlement = RitualPorts.ClearCommit.NONE;
        if (status.equals("CLEARED")) {
            RitualClear clear = clearFact(run, ending, finisher);
            settlement = rewards.prepare(clear, run.definition.material());
            writes.put("ritual/clear/" + run.runId, JSON.toJsonTree(clear));
            writes.putAll(settlement.writes());
        }
        Runnable publish = settlement.publish();
        return new RitualPorts.ClearCommit(writes, () -> publishEnd(run, status, ending, publish));
    }

    private void publishEnd(Run run, String status, String ending, Runnable rewards) {
        run.status = status;
        run.ending = ending;
        for(String cast:new ArrayList<>(run.casts.keySet()))cancelMove(run,cast);
        JsonObject state=runState(run);state.addProperty("ended",true);
        broadcastArena(run,"ritualRun",state);
        rewards.run();
        if (status.equals("FAILED")) cleanup(run);
        else {
            var level=run.level(server);
            if(level!=null&&run.bossEntity!=null&&level.getEntity(run.bossEntity) instanceof RitualBossEntity boss){
                boss.getNavigation().stop();
                boss.play(ending.equals("killed")?"death":ending,1200);
            }
            run.limitUntil=System.currentTimeMillis()+1200;
        }
    }

    RitualClear clearFact(Run run, String ending, String finisher) {
        var boss = run.definition;
        var qualified = new LinkedHashSet<>(run.damaged);
        if (finisher != null) qualified.add(finisher);
        if (run.content != null) qualified.addAll(run.content.handlers());
        var inCircle = new ArrayList<String>();
        for (var player : server.getPlayerList().getPlayers())
            if (!player.hasDisconnected() && qualified.contains(player.getStringUUID()) && inCircle(run, player))
                inCircle.add(player.getStringUUID());
        return new RitualClear(run.runId, run.bossId, boss.level(), ending, run.dimension,
                run.origin.x, run.origin.y, run.origin.z, run.radius, inCircle, System.currentTimeMillis());
    }

    void cleanup(Run run) {
        var actors=new ArrayList<>(run.mechanisms);
        if(run.bossEntity!=null)actors.add(run.bossEntity);
        ServerLevel level = run.level(server);
        if(level!=null)for(UUID id:actors){Entity actor=level.getEntity(id);if(actor!=null)actor.discard();}
        targets.releaseIds(actors);
        run.mechanisms.clear();
        run.casts.clear();
        run.castEnds.clear();
        if (run.bossEntity != null) runsByBoss.remove(run.bossEntity);
        runs.remove(run.runId);
    }

    public void logout(ServerPlayer player) {
        String id=player.getStringUUID();
        for (Run run : new ArrayList<>(runs.values())) {
            if (run.terminal()) continue;
            boolean wasJoined=run.joined.remove(id);
            boolean damaged=run.damaged.remove(id);
            boolean handled=run.content!=null && run.content.handled(id);
            if (handled) run.content.forgetHandler(id);
            if (!wasJoined && !damaged && !handled) continue;
            if (participants(run).isEmpty()) end(run,"FAILED","no_survivor");
            else if (wasJoined) persistRun(run);
        }
    }

    public void shutdown() {
        for(Run run:new ArrayList<>(runs.values())){
            if(run.terminal())cleanup(run);else end(run,"FAILED","shutdown");
        }
    }

    // ------------------------------------------------------------------ run persistence and recovery

    JsonObject runRecord(Run run) {
        JsonObject record = new JsonObject();
        record.addProperty("runId", run.runId);
        record.addProperty("boss", run.bossId);
        record.addProperty("dimension", run.dimension);
        record.addProperty("altar", run.altar.getX() + "," + run.altar.getY() + "," + run.altar.getZ());
        record.addProperty("altarKey",run.altarKey);
        record.addProperty("origin", run.origin.x + "," + run.origin.y + "," + run.origin.z);
        if (run.spawn != null) record.addProperty("spawn", run.spawn.x + "," + run.spawn.y + "," + run.spawn.z);
        record.addProperty("radius", run.radius);
        record.addProperty("bossEntity", run.bossEntity == null ? "" : run.bossEntity.toString());
        record.addProperty("status", run.status);
        record.addProperty("ending", run.ending);
        record.add("joined", JSON.toJsonTree(run.joined));
        record.add("mechanisms", JSON.toJsonTree(run.mechanisms));
        record.addProperty("committedAt", run.committedAt);
        record.addProperty("countdownUntil", run.countdownUntil);
        record.addProperty("startedAt", run.startedAt);
        record.addProperty("openingPlayers", run.openingPlayers);
        record.addProperty("mana", run.summonMana);
        return record;
    }

    void persistRun(Run run) {
        try { journal.put("ritual/run/" + run.runId, runRecord(run)); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    void recover() {
        for (String key : journal.keys("ritual/run/")) {
            JsonElement archived = journal.get(key);
            if (archived.isJsonNull()) continue;
            try {
                JsonObject record = archived.getAsJsonObject();
                Run run = new Run();
                run.runId = record.get("runId").getAsString();
                run.bossId = record.get("boss").getAsString();
                run.definition = RitualBossCatalog.current().boss(run.bossId);
                run.dimension = record.get("dimension").getAsString();
                String[] altar = record.get("altar").getAsString().split(",");
                run.altar = new BlockPos(Integer.parseInt(altar[0]), Integer.parseInt(altar[1]), Integer.parseInt(altar[2]));
                String[] origin = record.get("origin").getAsString().split(",");
                run.origin = new Vec3(Double.parseDouble(origin[0]), Double.parseDouble(origin[1]), Double.parseDouble(origin[2]));
                run.radius = record.get("radius").getAsInt();
                run.status = record.get("status").getAsString();
                run.ending = record.has("ending") ? record.get("ending").getAsString() : "";
                if (record.has("bossEntity") && !record.get("bossEntity").getAsString().isEmpty())
                    run.bossEntity = UUID.fromString(record.get("bossEntity").getAsString());
                for (var element : record.getAsJsonArray("joined")) run.joined.add(element.getAsString());
                for (var element : record.getAsJsonArray("mechanisms")) run.mechanisms.add(UUID.fromString(element.getAsString()));
                if (!run.terminal()) {
                    record.addProperty("status","FAILED");
                    record.addProperty("ending","restart");
                    try{journal.put(key,record);}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
                }
                cleanup(run);
            } catch (RuntimeException malformed) {
                com.mojang.logging.LogUtils.getLogger().warn("祭坛场次恢复失败 {}",key,malformed);
            }
        }
    }

    // ------------------------------------------------------------------ broadcast helpers

    void broadcastArena(Run run, String action, Object value) {
        var dimension = run.dimension;
        double radius = Math.max(run.radius + 16, 40);
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            if (player.level().dimension().identifier().toString().equals(dimension)
                    && player.position().distanceToSqr(run.origin) <= radius * radius)
                Network.send(player, action, value);
    }

    JsonObject runState(Run run) {
        JsonObject state = new JsonObject();
        state.addProperty("runId", run.runId);
        state.addProperty("boss", run.bossId);
        state.addProperty("dimension",run.dimension);
        var boss = run.definition;
        state.addProperty("name", boss.name());
        state.addProperty("level", boss.level());
        state.addProperty("x", run.origin.x);
        state.addProperty("y", run.origin.y);
        state.addProperty("z", run.origin.z);
        state.addProperty("radius", run.radius);
        state.addProperty("status", run.status);
        state.addProperty("ending", run.ending);
        state.addProperty("joined", run.joined.size());
        if (run.status.equals("PENDING"))
            state.addProperty("countdown", Math.max(0, run.countdownUntil - System.currentTimeMillis()));
        if (run.bossTarget != null) {
            state.addProperty("hp", Math.max(0, run.bossTarget.life.q));
            Target target = run.bossTarget;
            double maxHealth = target.health(boss.level());
            state.addProperty("hpText", (long) Math.ceil(Math.max(0, target.life.q) * maxHealth) + " / " + (long) Math.ceil(maxHealth));
        }
        if (!run.hudLines.isEmpty()) state.add("lines", JSON.toJsonTree(run.hudLines));
        var spots = new ArrayList<JsonObject>();
        for (var spot : run.safeSpots.values()) spots.add(spot);
        state.add("safeSpots", JSON.toJsonTree(spots));
        state.addProperty("limitUntil", run.limitUntil);
        state.addProperty("at", System.currentTimeMillis());
        return state;
    }

    public void message(ServerPlayer player, String text) { player.sendOverlayMessage(ModText.of(text)); }

    public ServerPlayer playerByUuid(String uuid) {
        if (uuid == null || uuid.isEmpty()) return null;
        for (var player : server.getPlayerList().getPlayers()) if (player.getStringUUID().equals(uuid)) return player;
        return null;
    }

    public Random random() { return random; }

    public Journal journal() { return journal; }

    static final class ElementIds {
        static int of(String element) {
            return switch (element == null ? "none" : element) {
                case "light" -> 0; case "fire" -> 1; case "ice" -> 2; case "dark" -> 3; default -> -1;
            };
        }
    }

    /** Level access for one run dimension. */
    ServerLevel level(Run run) { return run.level(server); }

    public static Run runOf(RitualEncounterService service, String runId) {
        return service.runs.get(runId);
    }

    /** Content-visible view of run internals keeps the content classes in one package. */
    static Run[] accessible(RitualEncounterService service) { return service.runs.values().toArray(new Run[0]); }
}
