package cn.yumis.fight.modules.skills.api;

import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Registered server visual lifecycle policies. Each content entry owns its lifecycle key. */
public final class SkillVisualRules {
    public record Policy(boolean invulnerable,String gravityActiveKey,String previousGravityKey){}
    private static final Policy NONE=new Policy(false,"","");
    private static final Map<String,Policy> POLICIES=new LinkedHashMap<>();
    static {register("yumis_dungeon_and_fight:storm",new Policy(true,"ydnf_storm_active","ydnf_storm_previous_gravity"));}
    private SkillVisualRules(){}
    public static void register(String id,Policy policy){if(POLICIES.putIfAbsent(id,Objects.requireNonNull(policy))!=null)throw new IllegalArgumentException("Duplicate visual policy: "+id);}
    public static Policy policy(String id){return POLICIES.getOrDefault(id,NONE);}
    public static void start(ServerPlayer player,String kind){
        var rule=policy(kind);if(rule.gravityActiveKey().isEmpty())return;
        var data=player.getPersistentData();
        if(!data.getBooleanOr(rule.gravityActiveKey(),false))data.putBoolean(rule.previousGravityKey(),player.isNoGravity());
        data.putBoolean(rule.gravityActiveKey(),true);player.setNoGravity(true);player.fallDistance=0;
    }
    public static void end(ServerPlayer player,String kind){restore(player,policy(kind));}
    public static void restore(ServerPlayer player){for(var policy:POLICIES.values())restore(player,policy);}
    private static void restore(ServerPlayer player,Policy rule){
        if(rule.gravityActiveKey().isEmpty())return;var data=player.getPersistentData();
        if(data.getBooleanOr(rule.gravityActiveKey(),false)){
            player.setNoGravity(data.getBooleanOr(rule.previousGravityKey(),false));
            data.remove(rule.gravityActiveKey());data.remove(rule.previousGravityKey());
        }
    }
}
