package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import java.util.Objects;
import java.util.function.BiFunction;

/** A temporary ritual mechanism (bait, spring, pull line, watch, pan, stele, shard...); the run owns its lifecycle. */
public class RitualMechanismEntity extends DungeonEnemy implements WorldTargetEntity {
    private static volatile BiFunction<ServerPlayer, RitualMechanismEntity, Boolean> interaction = (p, e) -> false;
    private Target initial;

    public static void wire(BiFunction<ServerPlayer, RitualMechanismEntity, Boolean> interactions) {
        interaction = Objects.requireNonNull(interactions);
    }

    public RitualMechanismEntity(EntityType<? extends Zombie> type, Level level) { super(type, level); }
    public void configure(Target target) { initial = Objects.requireNonNull(target); scripted(); setNoGravity(true); }
    @Override public Target initialCombatTarget() { return Objects.requireNonNull(initial); }
    @Override public int combatLevel() { return initial == null ? 1 : initial.originalLevel; }
    @Override public double combatMaximumHealth() { return initial == null ? 1 : initial.health(initial.originalLevel); }
    @Override public boolean rejectsNativeDamage(net.minecraft.world.damagesource.DamageSource source) {
        return !(source.getEntity() instanceof Player);
    }
    @Override public boolean acceptsPlayerEffects() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public void checkDespawn() {}

    public String kind() { return getPersistentData().getStringOr("ydnf_ritual_kind", ""); }
    public void kind(String value) { getPersistentData().putString("ydnf_ritual_kind", value); }
    public String ritualRun() { return getPersistentData().getStringOr("ydnf_ritual_run", ""); }
    public void bindRun(String runId) { getPersistentData().putString("ydnf_ritual_run", runId == null ? "" : runId); }

    @Override protected boolean convertsInWater() { return false; }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide() && player instanceof ServerPlayer server
                && server.distanceToSqr(this) <= 25 && interaction.apply(server, this)) {
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

}
