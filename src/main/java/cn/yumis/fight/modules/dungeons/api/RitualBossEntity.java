package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import java.util.function.BiFunction;
import java.util.Objects;

/** World boss published by a ritual altar run; AI stays with the encounter service, animation with DungeonEnemy. */
public class RitualBossEntity extends DungeonEnemy implements WorldTargetEntity {
    private static volatile BiFunction<ServerPlayer, RitualBossEntity, Boolean> interactions = (p, b) -> false;
    private Target initial;

    /** Bootstrap wires the ritual service interaction hook (head confirmations, listener dialogue). */
    public static void wire(BiFunction<ServerPlayer, RitualBossEntity, Boolean> value) {
        interactions = Objects.requireNonNull(value);
    }

    public RitualBossEntity(EntityType<? extends Zombie> type, Level level) { super(type, level); }
    public void configure(Target target) { initial = Objects.requireNonNull(target); }
    @Override public Target initialCombatTarget() { return Objects.requireNonNull(initial); }
    @Override public int combatLevel() { return initial == null ? 1 : initial.originalLevel; }
    @Override public double combatMaximumHealth() { return initial == null ? 1 : initial.health(initial.originalLevel); }
    @Override public boolean rejectsNativeDamage(net.minecraft.world.damagesource.DamageSource source) {
        return !(source.getEntity() instanceof Player);
    }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public void checkDespawn() {}
    /** The altar boss must keep its published shape; water never rewrites it into a drowned. */
    @Override protected boolean convertsInWater() { return false; }
    public String ritualRun() { return getPersistentData().getStringOr("ydnf_ritual_run", ""); }
    public void bindRun(String runId) { getPersistentData().putString("ydnf_ritual_run", runId == null ? "" : runId); }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide() && player instanceof ServerPlayer server
                && server.distanceToSqr(this) <= 36 && interactions.apply(server, this)) {
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
}
