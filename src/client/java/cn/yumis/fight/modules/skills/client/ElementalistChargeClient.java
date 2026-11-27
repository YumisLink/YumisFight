package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.client.SealBossState;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillInputs;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Owning-client post-chant hold-to-charge state machine for Elementalist Moving Cast.
 * MP and cooldown are already deducted on press; releasing fires immediately; cancel never refunds.
 */
public final class ElementalistChargeClient {
    private static final Identifier MOVE_MODIFIER = Identifier.parse("yumis_dungeon_and_fight:elementalist_charge_move");

    private static SkillCast cast;
    private static Runnable release;
    private static int boundSlot = -1;
    private static boolean trackedByKey;
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
        return previewRadius(cast, chargeProgress(now));
    }

    static double previewRadius(SkillCast source, double progress) {
        if (source == null) return 0.0;
        double base = source.parameters.get("radius", 0.0);
        if (base <= 0.0) return 0.0;
        return base * ElementalistRules.chargeScaleMultiplier(source.parameters, progress);
    }

    public static double previewRange() {
        return previewRange(cast);
    }

    static double previewRange(SkillCast source) {
        if (source == null) return 16.0;
        return Math.max(1.0, source.parameters.get("range", 16.0));
    }

    public static boolean showsGroundRing() {
        return showsGroundRing(cast);
    }

    static boolean showsGroundRing(SkillCast source) {
        if (source == null) return false;
        String id = source.skill.id;
        return source.skill.groundTargeted
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
        trackedByKey = slot >= 0 && SkillInputs.SKILLS[slot % 8].isDown();
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

    /** Immediately releases a chargeable skill at 0% charge when the key was already released during chant. */
    static void releaseUncharged(SkillCast source, Runnable completion) {
        finalizeAndRelease(source, 0.0, completion);
    }

    /** Immediately releases a chargeable skill at 100% full charge (e.g. 多星储能). */
    static void releaseFull(SkillCast source, Runnable completion) {
        finalizeAndRelease(source, 1.0, completion);
    }

    /** Pressing the same skill consumes the repeat; pressing another skill cancels charge without refund. */
    public static boolean press(int slot) {
        if (!active()) return false;
        String binding = SkillsClient.current().progress().slots[slot];
        if (cast.skill.id.equals(binding)) return true;
        cancel();
        return false;
    }

    /** Releasing the bound key locks in the current charge and immediately fires the skill. */
    public static void released(int baseSlot) {
        if (!active()) return;
        if (boundSlot >= 0 && boundSlot % 8 == baseSlot) {
            releaseNow();
        }
    }

    public static void cancel() {
        if (!active()) return;
        var source = cast;
        source.phase = cn.yumis.fight.modules.combat.api.ActionPhase.ABANDONED;
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
        trackedByKey = false;
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
        if (trackedByKey && boundSlot >= 0 && !SkillInputs.SKILLS[boundSlot % 8].isDown()) {
            releaseNow();
            return;
        }
        long now = ClientUi.now();
        if (now >= autoReleaseAt) {
            releaseNow();
        }
    }

    private static void releaseNow() {
        if (!active()) return;
        var source = cast;
        var completion = release;
        double u = chargeProgress(ClientUi.now());
        clear();
        finalizeAndRelease(source, u, completion);
    }

    private static void finalizeAndRelease(SkillCast source, double progress, Runnable completion) {
        var player = Minecraft.getInstance().player;
        if (player == null || source == null || completion == null) {
            if (source != null) {
                PlayerPoseChannel.cancel(source);
                CareerClient.INSTANCE.cancel(source);
            }
            return;
        }
        long now = ClientUi.now();
        double u = Math.clamp(progress, 0.0, 1.0);
        source.chargeProgress = u;
        source.parameters = ElementalistRules.applyChargeParameters(source.parameters, u);
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
