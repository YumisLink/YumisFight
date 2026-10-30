package cn.yumis.fight.modules.combat.api;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import java.util.UUID;
/** Each interface describes one participant in the existing receipt transaction. */
public final class CombatPorts {
    private CombatPorts(){}
    public record NativeLaunch(cn.yumis.fight.modules.attributes.api.Stats stats,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot effects){}
    public interface Encounters {boolean participant(ServerPlayer player,String instance);void died(LivingEntity target);void cancelBoss(UUID target);}
    public interface Rewards {void death(ServerPlayer player,LivingEntity entity,CombatTarget target)throws Exception;void publish();void abort();}
    public interface NativeHits {
        boolean validate(ServerPlayer player,LivingEntity entity,JsonObject report);
        long occurredAt(JsonObject report);
        void commit(ServerPlayer player,JsonObject report)throws Exception;
        void complete(JsonObject report);
        DamageSource source(JsonObject report);
        void suppressProjectionFeedback(boolean enabled);
    }
    public interface Attacks {default cn.yumis.fight.modules.attributes.api.Stats sourceStats(ServerPlayer p,JsonObject j){return null;}default cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot sourceEffects(ServerPlayer p,JsonObject j){return null;}void validate(ServerPlayer player,JsonObject report);void committed(JsonObject report);}
    public interface SkillEffects {
        default cn.yumis.fight.modules.attributes.api.Stats sourceStats(ServerPlayer p,JsonObject j){return null;}
        default cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot sourceEffects(ServerPlayer p,JsonObject j){return null;}
        default boolean direct(ServerPlayer player,JsonObject report){return true;}
        default long occurredAt(ServerPlayer player,JsonObject report){return -1;}
        default void validate(ServerPlayer player,LivingEntity target,JsonObject report){}
        default Commit prepare(ServerPlayer player,LivingEntity target,JsonObject report,Receipt receipt,Life actorLife){return Commit.NONE;}
        default void reconcile(ServerPlayer player,Runtime previous,Runtime incoming){}
    }
    public static SkillEffects combine(SkillEffects... effects){return new SkillEffects(){
        public cn.yumis.fight.modules.attributes.api.Stats sourceStats(ServerPlayer p,JsonObject j){for(var e:effects){var value=e.sourceStats(p,j);if(value!=null)return value;}return null;}
        public cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot sourceEffects(ServerPlayer p,JsonObject j){for(var e:effects){var value=e.sourceEffects(p,j);if(value!=null)return value;}return null;}
        public boolean direct(ServerPlayer p,JsonObject j){for(var e:effects)if(!e.direct(p,j))return false;return true;}
        public long occurredAt(ServerPlayer p,JsonObject j){for(var e:effects){long time=e.occurredAt(p,j);if(time>=0)return time;}return -1;}
        public void validate(ServerPlayer p,LivingEntity t,JsonObject j){for(var e:effects)e.validate(p,t,j);}
        public Commit prepare(ServerPlayer p,LivingEntity t,JsonObject j,Receipt r,Life actor){var commits=java.util.Arrays.stream(effects).map(e->e.prepare(p,t,j,r,actor)).toList();return new Commit(){public void persist()throws Exception{for(var c:commits)c.persist();}public void publish(){for(var c:commits)c.publish();}};}
        public void reconcile(ServerPlayer p,Runtime previous,Runtime incoming){for(var e:effects)e.reconcile(p,previous,incoming);}
    };}
    public interface Commit {
        Commit NONE=new Commit(){};
        default void persist()throws Exception{}
        default void publish(){}
    }
    @FunctionalInterface public interface Recorder {void record(ServerPlayer player,CombatTarget target,double fraction,long time)throws Exception;}
    /** Timed practice participates in the existing hit transaction. */
    public interface Training {
        void validate(ServerPlayer player,CombatTarget target,long occurredAt);
        void record(ServerPlayer player,CombatTarget target,double fraction,long occurredAt)throws Exception;
    }
}
