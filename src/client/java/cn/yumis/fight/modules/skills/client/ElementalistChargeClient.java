package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.client.SealBossState;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Owning-client hold-to-charge state machine for Elementalist Moving Cast.
 * MP and cooldown are already deducted on press; cancel never refunds.
 */
public final class ElementalistChargeClient {
    private static final Identifier MOVE_MODIFIER = Identifier.parse("yumis_dungeon_and_fight:elementalist_charge_move");
    private static final Set<String> DAMAGE_KEYS = Set.of("damage", "finisher", "emberDot", "pulseDot", "volcanoDot");
    private static final Set<String> SCALE_KEYS = Set.of("radius", "blastRadius", "shockwaveRadius", "pullRadius");

    private static SkillCast cast;
    private static Runnable release;
    private static int boundSlot = -1;
    private static String session = "", job = "", instance = "";
    private static long generation, start, chargeMillis, fullAt, autoReleaseAt;

    private ElementalistChargeClient() {}

    public static boolean active() {
        return cast != null;
    }

    public static SkillDefinition skill() {
        return cast == null ? null : cast.skill;
    }

    public static SkillCast cast() {
        return cast;
    }

    public static int effectiveElement() {
        if (cast == null) return -1;
        return ElementalistRules.effectiveElement(cast.parameters, cast.stats, ElementalistSkills.innateElement(cast.skill.id));
    }

    public static double chargeProgress(long now) {
        if (cast == null) return 0.0;
        return Math.clamp((now - start) / (double) Math.max(1L, chargeMillis), 0.0, 1.0);
    }

    public static boolean isFull(long now) {
        return cast != null && now >= fullAt;
    }

    public static double holdRemainingRatio(long now) {
        if (cast == null) return 0.0;
        long total = Math.max(1L, autoReleaseAt - fullAt);
        return Math.clamp((autoReleaseAt - now) / (double) total, 0.0, 1.0);
    }

    public static double holdRemainingSeconds(long now) {
        if (cast == null) return 0.0;
        return Math.max(0.0, (autoReleaseAt - now) / 1000.0);
    }

    public static double chargeRemainingSeconds(long now) {
        if (cast == null) return 0.0;
        return Math.max(0.0, (fullAt - now) / 1000.0);
    }

    public static double previewRadius(long now) {
        if (cast == null) return 0.0;
        double base = cast.parameters.get("radius", 0.0);
        if (base <= 0.0) return 0.0;
        return base * ElementalistRules.chargeScaleMultiplier(cast.parameters, chargeProgress(now));
    }

    public static double previewRange() {
        if (cast == null) return 16.0;
        return Math.max(1.0, cast.parameters.get("range", 16.0));
    }

    public static boolean showsGroundRing() {
        if (cast == null) return false;
        String id = cast.skill.id;
        return cast.skill.groundTargeted
            || ElementalistSkills.ARCTIC_FEAST.equals(id)
            || ElementalistSkills.LIGHTNING_STRIKE.equals(id);
    }

    public static void begin(SkillCast source, int slot, Runnable completion) {
        clear();
        var state = SkillsClient.current();
        cast = source;
        cast.phase = cn.yumis.fight.modules.combat.api.ActionPhase.CHANTING;
        release = completion;
        boundSlot = slot;
        session = state.session();
        generation = state.own().generation;
        job = state.progress().jobId();
        instance = state.own().instance;

        start = ClientUi.now();
        double baseCharge = source.parameters.get("chargeSeconds", 0.5);
        double effSeconds = ElementalistRules.effectiveChargeSeconds(baseCharge, source.stats);
        chargeMillis = Math.max(50L, Math.round(effSeconds * 1000.0));
        long maxHoldMillis = Math.max(200L, Math.round(source.parameters.get("maxHoldSeconds", 2.0) * 1000.0));
        fullAt = start + chargeMillis;
        autoReleaseAt = fullAt + maxHoldMillis;

        var player = Minecraft.getInstance().player;
        if (player != null) {
            boolean movingLearned = ElementalistRules.movingCastLearned(source.stats, state.progress());
            double ratio = movingLearned ? source.parameters.get("chargeMoveSpeedRatio", 1.0) : 0.2;
            applyMovementModifier(player, ratio);
            PlayerPoseChannel.loop(source, "elementalist_charge", chargeMillis + maxHoldMillis, source.direction, true);
        }
    }

    /** Pressing the same skill consumes the repeat; pressing another skill cancels charge without refund. */
    public static boolean press(int slot) {
        if (!active()) return false;
        String binding = SkillsClient.current().progress().slots[slot];
        if (cast.skill.id.equals(binding)) return true;
        cancel();
        return false;
    }

    /** Releasing the bound key locks in the current charge and begins the cast. */
    public static void released(int baseSlot) {
        if (!active()) return;
        if (boundSlot >= 0 && boundSlot % 8 == baseSlot) {
            releaseNow();
        }
    }

    public static void cancel() {
        if (!active()) return;
        var source = cast;
        clear();
        PlayerPoseChannel.cancel(source);
        CareerClient.INSTANCE.cancel(source);
    }

    public static void reset() {
        clear();
    }

    public static void clear() {
        var player = Minecraft.getInstance().player;
        if (player != null) removeMovementModifier(player);
        cast = null;
        release = null;
        boundSlot = -1;
    }

    public static void tick() {
        if (!active()) return;
        var mc = Minecraft.getInstance();
        var state = SkillsClient.current();
        var player = mc.player;
        var hand = player == null ? null : EquipmentItems.view(player.getMainHandItem());
        if (player == null || mc.level == null || !state.ready() || state.own().dead || player.isDeadOrDying()
            || !state.session().equals(session) || state.own().generation != generation
            || !state.own().instance.equals(instance) || !state.progress().jobId().equals(job)
            || !mc.level.dimension().identifier().toString().equals(cast.dimension)
            || hand == null || !hand.instance().toString().equals(cast.weapon)
            || CombatContentTypes.controlled(player) || SealBossState.isFrozen()) {
            cancel();
            return;
        }
        long now = ClientUi.now();
        if (now >= autoReleaseAt) {
            releaseNow();
        }
    }

    private static void releaseNow() {
        if (!active()) return;
        var player = Minecraft.getInstance().player;
        var source = cast;
        var completion = release;
        long now = ClientUi.now();
        double u = chargeProgress(now);
        clear();
        if (player == null || completion == null) {
            if (source != null) {
                PlayerPoseChannel.cancel(source);
                CareerClient.INSTANCE.cancel(source);
            }
            return;
        }
        source.chargeProgress = u;
        double dmgMul = ElementalistRules.chargeDamageMultiplier(source.parameters, u);
        double scaleMul = ElementalistRules.chargeScaleMultiplier(source.parameters, u);
        var values = new LinkedHashMap<>(source.parameters.values());
        for (String key : DAMAGE_KEYS) {
            if (values.containsKey(key)) values.put(key, values.get(key) * dmgMul);
        }
        for (String key : SCALE_KEYS) {
            if (values.containsKey(key) && values.get(key) > 0) values.put(key, values.get(key) * scaleMul);
        }
        if (ElementalistSkills.LIGHTNING_VORTEX.equals(source.skill.id)
            && ElementalistRules.morphMode(source.parameters) == 0 && u >= 0.999) {
            values.put("orbCount", values.getOrDefault("orbCount", 4.0) + 1.0);
        }
        source.parameters = new SkillParameters.Snapshot(source.skill, source.parameters.rank(), values);
        source.start = source.stageStart = now;
        source.origin = player.position();
        Vec3 look = player.getLookAngle();
        source.direction = look.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : look.normalize();
        source.center = source.origin;
        completion.run();
    }

    private static void applyMovementModifier(LivingEntity actor, double ratio) {
        var attr = actor.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        attr.removeModifier(MOVE_MODIFIER);
        double delta = Math.max(-0.9, ratio - 1.0);
        if (Math.abs(delta) > 1e-4) {
            attr.addTransientModifier(new AttributeModifier(MOVE_MODIFIER, delta, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeMovementModifier(LivingEntity actor) {
        var attr = actor.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) attr.removeModifier(MOVE_MODIFIER);
    }
}
