package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.combat.api.ElementAccumulation;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.dungeons.api.TowerCardEffects;
import cn.yumis.fight.modules.equipment.api.EquipmentEffects;
import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.skills.api.client.TrainingCombat;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Client-side runtime for Summoner career mythic VP cards (mark, soul pact, auras, sacrifice, magma, shatter). */
public final class SummonerClient {
    private static final Identifier ICE_SLOW = Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "louise_needle_slow");
    private static final int[] ELEMENT_COLORS = {0xffed8d, 0xff8a58, 0x83dfff, 0xc783ff};

    static final class MagmaZone {
        final SkillCast template;
        final Vec3 center;
        final double radius;
        final double tickCoeff;
        long nextTickAt;
        int ticksLeft;
        final long expiresAt;

        MagmaZone(SkillCast template, Vec3 center, double radius, double tickCoeff, long nextTickAt, int ticksLeft, long expiresAt) {
            this.template = template;
            this.center = center;
            this.radius = radius;
            this.tickCoeff = tickCoeff;
            this.nextTickAt = nextTickAt;
            this.ticksLeft = ticksLeft;
            this.expiresAt = expiresAt;
        }
    }

    private static UUID markedTarget;
    private static long markedUntil;
    private static long lastMarkSentAt;
    private static UUID lastMarkSentTarget;
    private static long lastAuraHealAt;
    private static final Map<UUID, Long> sandorShredUntil = new HashMap<>();
    private static final Map<UUID, Long> louiseSlowUntil = new HashMap<>();
    private static final List<MagmaZone> magmaZones = new ArrayList<>();
    private static final Set<String> dirtyPets = new HashSet<>();

    private SummonerClient() {}

    private static SkillsClient state() { return SkillsClient.current(); }

    public static void clear() {
        markedTarget = null;
        markedUntil = 0;
        lastMarkSentAt = 0;
        lastMarkSentTarget = null;
        lastAuraHealAt = 0;
        sandorShredUntil.clear();
        louiseSlowUntil.clear();
        magmaZones.clear();
        dirtyPets.clear();
    }

    public static UUID markedTarget(long serverNow) {
        return markedTarget != null && markedUntil > serverNow ? markedTarget : null;
    }

    public static boolean isMarked(LivingEntity target, long serverNow) {
        return target != null && markedTarget != null && markedUntil > serverNow && markedTarget.equals(target.getUUID());
    }

    public static List<MagmaZone> activeMagmaZones() {
        return magmaZones;
    }

    public static boolean isKingAlive(long serverNow) {
        for (var pet : MageClient.INSTANCE.army.values()) {
            if ("KING".equals(pet.get("kind").getAsString())
                && pet.get("until").getAsLong() > serverNow
                && pet.get("health").getAsDouble() > 0) {
                return true;
            }
        }
        return false;
    }

    public static int lowerSpiritCount(long serverNow) {
        int count = 0;
        for (var pet : MageClient.INSTANCE.army.values()) {
            if (pet.get("until").getAsLong() <= serverNow || pet.get("health").getAsDouble() <= 0) continue;
            SummonKind kind = SummonKind.valueOf(pet.get("kind").getAsString());
            if (SummonerSkills.lowerSpirit(kind)) count++;
        }
        return count;
    }

    public static SummonedCreature findSummonEntity(JsonObject pet) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || pet == null) return null;
        if (pet.has("entityId")) {
            var entity = mc.level.getEntity(pet.get("entityId").getAsInt());
            if (entity instanceof SummonedCreature summon && !summon.isRemoved()) return summon;
        }
        if (mc.player == null) return null;
        SummonKind kind = SummonKind.valueOf(pet.get("kind").getAsString());
        for (var raw : mc.level.entitiesForRendering()) {
            if (raw instanceof SummonedCreature summon && !summon.isRemoved()
                && summon.kind() == kind && mc.player.getUUID().equals(summon.owner)) {
                return summon;
            }
        }
        return null;
    }

    public static SummonedCreature activePaladinSandor(Stats ownerStats) {
        if (!TowerBattleCard.SUMMONER_SANDOR_PALADIN.held(ownerStats)) return null;
        long serverNow = state().serverNow();
        for (var pet : MageClient.INSTANCE.army.values()) {
            if ("SANDOR".equals(pet.get("kind").getAsString())
                && pet.get("until").getAsLong() > serverNow
                && pet.get("health").getAsDouble() > 0) {
                return findSummonEntity(pet);
            }
        }
        return null;
    }

    public static boolean inSandorAura(Vec3 pos, Stats ownerStats) {
        if (pos == null) return false;
        SummonedCreature sandor = activePaladinSandor(ownerStats);
        if (sandor == null) return false;
        double r = TowerBattleCard.SUMMONER_SANDOR_PALADIN.value("radius");
        return sandor.position().distanceToSqr(pos) <= r * r;
    }

    /** Applies Sandor Paladin Guardian Aura (+60% physical/magical defense) to player snapshot. */
    public static Stats applyAuraStats(Stats base) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !state().ready() || !inSandorAura(mc.player.position(), base)) return base;
        Stats copy = base.copy();
        double def = TowerBattleCard.SUMMONER_SANDOR_PALADIN.ratio("defense");
        copy.mut(Stat.PHYSICAL_DEFENSE, def);
        copy.mut(Stat.MAGICAL_DEFENSE, def);
        return copy;
    }

    /** Damage reduction from Sandor Paladin Guardian Aura (-25% incoming damage). */
    public static double ownerDamageTakenMultiplier(Stats current) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !state().ready() || !inSandorAura(mc.player.position(), current)) return 1.0;
        return Math.max(0.0, 1.0 - TowerBattleCard.SUMMONER_SANDOR_PALADIN.ratio("reduction"));
    }

    public static void onSandorWarCry(Vec3 sandorPos, long now) {
        if (sandorPos == null) return;
        double r = TowerBattleCard.SUMMONER_SANDOR_PALADIN.value("taunt");
        long until = now + TowerBattleCard.SUMMONER_SANDOR_PALADIN.millis("duration");
        for (var target : SkillController.INSTANCE.targets(new AABB(sandorPos, sandorPos).inflate(r))) {
            if (target.position().distanceToSqr(sandorPos) <= r * r) {
                sandorShredUntil.put(target.getUUID(), until);
            }
        }
    }

    public static double defenseScale(LivingEntity entity, long now) {
        if (entity != null && sandorShredUntil.getOrDefault(entity.getUUID(), 0L) > now) {
            return Math.max(0.0, 1.0 - TowerBattleCard.SUMMONER_SANDOR_PALADIN.ratio("shred"));
        }
        return 1.0;
    }

    /** Applies Mark target bonus (+45% total damage, +25% crit) when a summon strikes the marked enemy. */
    public static Stats applyMarkBonus(Stats attackStats, LivingEntity target, Stats ownerStats, long serverNow) {
        if (!TowerBattleCard.SUMMONER_MASTERY_MARK.held(ownerStats) || !isMarked(target, serverNow)) return attackStats;
        Stats copy = attackStats.copy();
        copy.modifier("skillProduct").alls *= 1.0 + TowerBattleCard.SUMMONER_MASTERY_MARK.ratio("damage");
        copy.modifiers.merge("crit", TowerBattleCard.SUMMONER_MASTERY_MARK.ratio("crit"), Double::sum);
        return copy;
    }

    /** Called on player hit: marks enemy if hit is basic attack or Star/Fang/Jack with Mark card held. */
    public static void onOwnerHit(SkillCast source, LivingEntity target, Stats stats, boolean basic) {
        if (target == null || !SkillController.canTarget(target) || !state().ready()) return;
        boolean summonHit = source != null && !source.summonSource.isEmpty();
        if (summonHit) return;
        boolean eligible = basic || (source != null && source.skill != null
            && (MageSkills.STAR.equals(source.skill.id) || MageSkills.FANG.equals(source.skill.id) || MageSkills.JACK.equals(source.skill.id)));
        if (!eligible) return;
        Stats ownerStats = SkillController.INSTANCE.snapshot();
        if (!TowerBattleCard.SUMMONER_MASTERY_MARK.held(ownerStats)) return;
        if (SkillLearning.effective(ownerStats, state().progress(), SkillCatalog.require(SummonerSkills.MASTERY)) <= 0) return;

        long serverNow = state().serverNow();
        long until = serverNow + TowerBattleCard.SUMMONER_MASTERY_MARK.millis("duration");
        boolean targetChanged = !target.getUUID().equals(markedTarget);
        markedTarget = target.getUUID();
        markedUntil = until;
        long uiNow = ClientUi.now();
        if (targetChanged || !target.getUUID().equals(lastMarkSentTarget) || uiNow - lastMarkSentAt >= 500L) {
            lastMarkSentAt = uiNow;
            lastMarkSentTarget = target.getUUID();
            JsonObject packet = new JsonObject();
            packet.addProperty("session", state().session());
            packet.addProperty("generation", state().own().generation);
            packet.addProperty("target", target.getStringUUID());
            packet.addProperty("until", until);
            state().send("summonMark", packet);
        }
    }

    /** Soul Pact: heals summon max HP on each summon hit (no shield recovery; throttled sync). */
    public static void onSummonHit(String sourceId, Stats ownerStats) {
        if (sourceId == null || sourceId.isEmpty() || !TowerBattleCard.SUMMONER_MASTERY_PACT.held(ownerStats)) return;
        if (SkillLearning.effective(ownerStats, state().progress(), SkillCatalog.require(SummonerSkills.MASTERY)) <= 0) return;
        JsonObject pet = MageClient.INSTANCE.army.get(sourceId);
        if (pet == null || pet.get("health").getAsDouble() <= 0) return;
        double ratio = TowerBattleCard.SUMMONER_MASTERY_PACT.ratio("heal");
        double maxHp = pet.get("maximum").getAsDouble();
        double hp = pet.get("health").getAsDouble();
        double nextHp = Math.min(maxHp, hp + maxHp * ratio);
        if (nextHp > hp) {
            pet.addProperty("health", nextHp);
            dirtyPets.add(sourceId);
        }
    }

    static boolean consumeDirtyPet(String sourceId) {
        return dirtyPets.remove(sourceId);
    }

    /** Soul Pact: transfers 50% of owner incoming damage evenly across all alive summons. */
    public static double shareIncomingDamage(Stats ownerStats, double damage) {
        if (damage <= 0 || !Double.isFinite(damage) || !state().ready()) return 0;
        if (!TowerBattleCard.SUMMONER_MASTERY_PACT.held(ownerStats)) return 0;
        if (SkillLearning.effective(ownerStats, state().progress(), SkillCatalog.require(SummonerSkills.MASTERY)) <= 0) return 0;
        long serverNow = state().serverNow();
        var alive = new ArrayList<JsonObject>();
        for (var pet : MageClient.INSTANCE.army.values()) {
            if (pet.get("health").getAsDouble() > 0 && pet.get("until").getAsLong() > serverNow) alive.add(pet);
        }
        if (alive.isEmpty()) return 0;
        double shared = damage * TowerBattleCard.SUMMONER_MASTERY_PACT.ratio("share");
        double perPet = shared / alive.size();
        for (var pet : alive) {
            MageClient.INSTANCE.applyDirectDamageToPet(pet, perPet, serverNow, ownerStats);
        }
        return shared;
    }

    /** Four Spirits Sacrifice: actively detonate all alive lower spirits before re-summoning. */
    public static void detonateAllAliveSpirits(SkillCast cast) {
        if (!TowerBattleCard.SUMMONER_SPIRITS_SACRIFICE.held(cast.stats)) return;
        long serverNow = state().serverNow();
        var spirits = new ArrayList<JsonObject>();
        for (var pet : MageClient.INSTANCE.army.values()) {
            if (pet.get("health").getAsDouble() <= 0 || pet.get("until").getAsLong() <= serverNow) continue;
            SummonKind kind = SummonKind.valueOf(pet.get("kind").getAsString());
            if (SummonerSkills.lowerSpirit(kind)) spirits.add(pet);
        }
        for (var pet : spirits) {
            detonateSpirit(pet, cast.stats, cast.parameters, cast.equipment, 1.0);
        }
    }

    /** Four Spirits Sacrifice: death auto-detonation at 50% power. */
    public static void onSpiritDied(JsonObject pet, Stats ownerStats) {
        if (pet == null || !state().ready() || !TowerBattleCard.SUMMONER_SPIRITS_SACRIFICE.held(ownerStats)) return;
        SummonKind kind = SummonKind.valueOf(pet.get("kind").getAsString());
        if (!SummonerSkills.lowerSpirit(kind)) return;
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        SkillDefinition skill = SkillCatalog.require(SummonerSkills.SPIRITS);
        int rank = pet.has("rank") ? Math.max(1, pet.get("rank").getAsInt()) : Math.max(1, SkillLearning.effective(ownerStats, state().progress(), skill));
        var params = SkillParameters.forCast(p.getUUID(), skill, rank, ownerStats.parameters);
        detonateSpirit(pet, ownerStats, params, state().resources().equipment, TowerBattleCard.SUMMONER_SPIRITS_SACRIFICE.ratio("death"));
    }

    private static void detonateSpirit(JsonObject pet, Stats ownerStats, SkillParameters.Snapshot params, EquipmentEffects.Snapshot equipment, double powerScale) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        SummonKind kind = SummonKind.valueOf(pet.get("kind").getAsString());
        int element = SummonerSkills.lowerSpiritElement(kind);
        if (element < 0) return;
        SummonedCreature entity = findSummonEntity(pet);
        Vec3 center = entity != null ? entity.position().add(0, 0.5, 0) : mc.player.position().add(0, 0.5, 0);
        double radius = TowerBattleCard.SUMMONER_SPIRITS_SACRIFICE.value("radius");
        double share = TowerBattleCard.SUMMONER_SPIRITS_SACRIFICE.ratio("damage") * powerScale;
        int mastery = SkillLearning.effective(ownerStats, state().progress(), SkillCatalog.require(SummonerSkills.MASTERY));
        Stats inherited = SummonerSkills.inherit(ownerStats, kind, mastery);
        double dmgCoeff = params.get("damage", 0) * share;
        if (dmgCoeff <= 0) return;

        Stats attack = SummonRules.attack(inherited, dmgCoeff);
        attack.modifiers.put("summonElement", (double) element);
        if (MageClient.INSTANCE.frenzyUntil > state().serverNow()) {
            int frenzyRank = Math.max(1, SkillLearning.effective(ownerStats, state().progress(), SkillCatalog.require(SummonerSkills.FRENZY)));
            attack.modifier("final").mut += 0.20 + 0.03 * (frenzyRank - 1);
        }
        ElementAccumulation.addConversion(attack, element, ElementAccumulation.Kind.values()[element],
            TowerBattleCard.SUMMONER_SPIRITS_SACRIFICE.ratio("buildup"));

        SkillCast burst = new SkillCast();
        burst.id = UUID.randomUUID().toString();
        burst.summonSource = pet.get("source").getAsString();
        burst.skill = SkillCatalog.require(SummonerSkills.SPIRITS);
        burst.parameters = params;
        burst.stats = attack;
        burst.equipment = equipment.copy();
        burst.elementOverride = element;
        burst.origin = center;
        burst.center = center;
        burst.direction = new Vec3(0, 0, 1);
        burst.start = ClientUi.now();
        burst.session = state().session();
        burst.generation = state().own().generation;
        burst.job = state().progress().jobId();
        burst.dimension = mc.level.dimension().identifier().toString();
        burst.instance = state().own().instance;
        burst.summonMove = "sacrifice_burst_impact";

        for (var target : SkillController.INSTANCE.targets(new AABB(center, center).inflate(radius))) {
            if (target.getBoundingBox().getCenter().distanceToSqr(center) <= radius * radius
                && SkillController.INSTANCE.sight(center, target.getBoundingBox().getCenter())) {
                MageSkillBehaviors.contact(burst, target, 1.0);
            }
        }
        JsonObject motion = new JsonObject();
        motion.addProperty("radius", radius);
        motion.addProperty("element", element);
        emitSummonVisual(burst, burst.id, "START", center, new Vec3(0, 1, 0), 650, ELEMENT_COLORS[element], motion);
        CameraShake.impulse(center, 14, powerScale >= 0.9 ? 0.30f : 0.16f, 80, 160);
    }

    /** Louise Inferno: spawns a 4-second burning magma sea at meteor impact. */
    static void spawnLouiseMagma(SkillCast meteorCast, Vec3 center, double radius) {
        long now = ClientUi.now();
        long durationMs = TowerBattleCard.SUMMONER_LOUISE_INFERNO.millis("duration");
        int ticks = Math.max(1, (int) (durationMs / 500L));
        // Each magma tick deals the configured percentage of the meteor impact's baked attack panel.
        double tickRatio = TowerBattleCard.SUMMONER_LOUISE_INFERNO.ratio("magma");
        Stats tickStats = meteorCast.stats.copy();
        tickStats.modifiers.put("summonElement", 1.0);
        ElementAccumulation.addConversion(tickStats, 1, ElementAccumulation.Kind.IGNITE, 1.0);
        SkillCast template = copyCast(meteorCast, tickStats, 1, "louise_magma_impact");
        magmaZones.add(new MagmaZone(template, center, radius, tickRatio, now + 500L, ticks, now + durationMs));
        TrainingCombat.hold(meteorCast.id, "louise_magma_" + now, now + durationMs + 500L);
        emitSummonVisual(template, UUID.randomUUID().toString(), "START", center, new Vec3(0, 1, 0), durationMs, 0xff6e2b, null);
    }

    /** Louise Frost Queen: records Louise ice slow and triggers +150% shatter if target is frozen or chilled/slowed. */
    static void onLouiseFrostHit(SkillCast sourceCast, LivingEntity target, boolean applySlow) {
        if (target == null || !state().ready()) return;
        long now = ClientUi.now();
        if (applySlow) louiseSlowUntil.put(target.getUUID(), now + 1800L);
        if (!TowerBattleCard.SUMMONER_LOUISE_FROST.held(sourceCast.stats)) return;
        if (sourceCast.summonMove.startsWith("louise_shatter")) return;
        Target t = state().context(target);
        boolean frozenOrChilled = t != null && t.life.elemental.active(ElementAccumulation.Kind.FREEZE, t.life, state().serverNow());
        var moveAttr = target.getAttribute(Attributes.MOVEMENT_SPEED);
        boolean slowed = target.hasEffect(MobEffects.SLOWNESS)
            || (moveAttr != null && moveAttr.hasModifier(ICE_SLOW))
            || louiseSlowUntil.getOrDefault(target.getUUID(), 0L) > now;
        if (!frozenOrChilled && !slowed) return;

        double mult = TowerBattleCard.SUMMONER_LOUISE_FROST.ratio("shatter");
        Stats shatterStats = sourceCast.stats.copy();
        shatterStats.modifiers.put("summonElement", 2.0);
        SkillCast shatter = copyCast(sourceCast, shatterStats, 2, "louise_shatter_impact");
        MageSkillBehaviors.contact(shatter, target, mult);
        emitSummonVisual(shatter, UUID.randomUUID().toString(), "START", target.getBoundingBox().getCenter(), new Vec3(0, 1, 0), 450, 0x9cf0ff, null);
    }

    private static SkillCast copyCast(SkillCast src, Stats stats, int element, String summonMove) {
        SkillCast c = new SkillCast();
        c.id = src.id;
        c.summonSource = src.summonSource;
        c.session = src.session;
        c.generation = src.generation;
        c.job = src.job;
        c.dimension = src.dimension;
        c.instance = src.instance;
        c.skill = src.skill;
        c.parameters = src.parameters;
        c.stats = stats;
        c.equipment = src.equipment;
        c.elementOverride = element;
        c.origin = src.origin;
        c.center = src.center;
        c.direction = src.direction;
        c.start = ClientUi.now();
        c.summonMove = summonMove;
        return c;
    }

    private static void emitSummonVisual(SkillCast c, String id, String phase, Vec3 point, Vec3 direction, long duration, int color, JsonObject motion) {
        var p = Minecraft.getInstance().player;
        if (p == null || !state().ready()) return;
        long now = state().serverNow();
        JsonObject j = new JsonObject();
        j.addProperty("caster", p.getStringUUID());
        j.addProperty("session", state().session());
        j.addProperty("generation", state().own().generation);
        j.addProperty("dimension", c.dimension);
        j.addProperty("instance", c.instance);
        j.addProperty("id", id);
        j.addProperty("kind", c.skill.id);
        j.addProperty("summonMove", c.summonMove);
        j.addProperty("phase", phase);
        j.addProperty("sequence", 0);
        j.addProperty("seed", id.hashCode());
        j.addProperty("startedAt", now);
        j.addProperty("at", now);
        j.addProperty("serverNow", now);
        j.addProperty("duration", Math.max(1, duration));
        j.addProperty("cosmeticOnly", true);
        j.addProperty("color", color);
        j.addProperty("x", point.x); j.addProperty("y", point.y); j.addProperty("z", point.z);
        j.addProperty("px", point.x); j.addProperty("py", point.y); j.addProperty("pz", point.z);
        j.addProperty("dx", direction.x); j.addProperty("dy", direction.y); j.addProperty("dz", direction.z);
        if (motion != null) j.add("motion", motion);
        Effects.receive(j);
        state().send("visual", j);
    }

    public static void tick(long uiNow) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !state().ready() || state().own().dead) return;
        sandorShredUntil.values().removeIf(until -> until <= uiNow);
        louiseSlowUntil.values().removeIf(until -> until <= uiNow);

        // Sandor Paladin Guardian Aura owner regen (3%/sec HP + shield, 1Hz sync)
        Stats snapshot = SkillController.INSTANCE.snapshot();
        if (inSandorAura(mc.player.position(), snapshot) && uiNow - lastAuraHealAt >= 1000L) {
            lastAuraHealAt = uiNow;
            double ratio = TowerBattleCard.SUMMONER_SANDOR_PALADIN.ratio("regen");
            double maxShield = snapshot.get(Stat.SHIELD);
            if (maxShield > 0 && state().resources().shield < maxShield) {
                state().resources().shield = Math.min(maxShield, state().resources().shield + maxShield * ratio);
                state().upload();
            }
            if (state().predicted(state().own()) < 1.0) {
                double maxHp = snapshot.get(Stat.HEALTH);
                double healFraction = ratio * TowerCardEffects.healingFactor(snapshot);
                if (maxHp > 0 && healFraction > 0) {
                    state().report(state().own(), healFraction, true, maxHp, 0xffe49c, false, null);
                }
            }
        }

        // Louise Inferno Magma Sea periodic ticks
        for (var it = magmaZones.iterator(); it.hasNext(); ) {
            var zone = it.next();
            if (!MageClient.INSTANCE.valid(zone.template) || zone.ticksLeft <= 0 || uiNow > zone.expiresAt + 200L) {
                it.remove();
                continue;
            }
            if (uiNow >= zone.nextTickAt) {
                zone.nextTickAt += 500L;
                zone.ticksLeft--;
                for (var target : SkillController.INSTANCE.targets(new AABB(zone.center, zone.center).inflate(zone.radius))) {
                    if (target.getBoundingBox().getCenter().distanceToSqr(zone.center) <= zone.radius * zone.radius
                        && SkillController.INSTANCE.sight(zone.center.add(0, 0.4, 0), target.getBoundingBox().getCenter())) {
                        MageSkillBehaviors.contact(zone.template, target, zone.tickCoeff);
                    }
                }
            }
        }
    }
}
