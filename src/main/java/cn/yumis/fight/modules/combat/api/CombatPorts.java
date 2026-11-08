package cn.yumis.fight.modules.combat.api;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import java.util.UUID;
/** Each interface describes one participant in the existing receipt transaction. */
public final class CombatPorts {
    private CombatPorts(){}
    public interface Encounters {boolean participant(ServerPlayer player,String instance);void died(LivingEntity target);void cancelBoss(UUID target);}
    public interface Rewards {void death(ServerPlayer player,LivingEntity entity,CombatTarget target)throws Exception;void publish();void abort();}
    public interface NativeHits {
        void commit(ServerPlayer player,JsonObject report)throws Exception;
        void complete(JsonObject report);
        DamageSource source(JsonObject report);
        void suppressProjectionFeedback(boolean enabled);
    }
    /** Enemy/world reactions enlisted after a client fact; no player gameplay validation. */
    public interface SkillEffects {
        default Commit prepare(ServerPlayer player,LivingEntity target,JsonObject report,Receipt receipt,Life actorLife){return Commit.NONE;}
    }
    public static SkillEffects combine(SkillEffects... effects){return new SkillEffects(){
        public Commit prepare(ServerPlayer p,LivingEntity t,JsonObject j,Receipt r,Life actor){var commits=java.util.Arrays.stream(effects).map(e->e.prepare(p,t,j,r,actor)).toList();return new Commit(){public void persist()throws Exception{for(var c:commits)c.persist();}public void publish(){for(var c:commits)c.publish();}};}
    };}
    public interface Commit {
        Commit NONE=new Commit(){};
        default void persist()throws Exception{}
        default void publish(){}
    }
    @FunctionalInterface public interface Recorder {void record(ServerPlayer player,CombatTarget target,double fraction,long time)throws Exception;}
    /** All practice hits use the existing transaction; only timed sessions record a damage meter. */
    public interface Training {
        void validate(ServerPlayer player,CombatTarget target,long occurredAt);
        void record(ServerPlayer player,CombatTarget target,double fraction,long occurredAt)throws Exception;
    }
}
