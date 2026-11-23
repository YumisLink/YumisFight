package cn.yumis.fight.modules.skills.api;

import java.util.*;

/** Registered presentation policies. Animation messages never change player physics. */
public final class SkillVisualRules {
    public static final String PLAYER_POSE="PLAYER_POSE";
    public record Policy(boolean invulnerable,String gravityActiveKey,String previousGravityKey,boolean refreshable){}
    private static final Policy NONE=new Policy(false,"","",false);
    private static final Map<String,Policy> POLICIES=new LinkedHashMap<>();
    static {register(OracleSkills.REPLICA,new Policy(false,"","",true));register("yumis_dungeon_and_fight:storm",new Policy(true,"ydnf_storm_active","ydnf_storm_previous_gravity",false));register("yumis_dungeon_and_fight:ghost_orb",new Policy(false,"","",true));}
    private SkillVisualRules(){}
    public static void register(String id,Policy policy){if(POLICIES.putIfAbsent(id,Objects.requireNonNull(policy))!=null)throw new IllegalArgumentException("Duplicate visual policy: "+id);}
    public static Policy policy(String id){return POLICIES.getOrDefault(id,NONE);}
    public static boolean known(String id){return SkillCatalog.contains(id)||POLICIES.containsKey(id)||Set.of("ATTACK","MASTERY_WAVE",PLAYER_POSE).contains(id);}
}
