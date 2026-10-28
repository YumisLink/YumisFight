package cn.yumis.fight.modules.character.api;
import cn.yumis.fight.architecture.StableId;
import java.util.*;
/** 转职内容注册表。新增职业只添加定义和本模块任务处理。 */
public final class JobCatalog {
    public static final String BASE="yumis_dungeon_and_fight:ghost_swordsman", SWORDMASTER="yumis_dungeon_and_fight:swordmaster",
        SOUL_BENDER="yumis_dungeon_and_fight:soul_bender", ASURA="yumis_dungeon_and_fight:asura", BERSERKER="yumis_dungeon_and_fight:berserker";
    public record CombatStyle(boolean magic,double damageTaken,String awakening){}
    private static final Map<String,CombatStyle> STYLES=new LinkedHashMap<>();
    public record Definition(String id,String title,String prerequisite,int minimumLevel,int kills,int elites,Map<String,Integer> materials){public Definition{new StableId(id);materials=Map.copyOf(materials);if(minimumLevel<1||kills<0||elites<0)throw new IllegalArgumentException("Invalid job");}}
    private static final Map<String,Definition> DEFINITIONS=new LinkedHashMap<>();
    static{
        register(new Definition(BASE,"鬼剑士","",1,0,0,Map.of()));
        STYLES.put(BASE,new CombatStyle(false,.65,""));
        add(SWORDMASTER,"剑魂",false,.35,"剑圣");
        add(SOUL_BENDER,"鬼泣",true,.50,"弑魂");
        add(ASURA,"阿修罗",true,.75,"大暗黑天");
        add(BERSERKER,"狂战士",false,.25,"狱血魔神");
    }
    private JobCatalog(){}
    private static void add(String id,String title,boolean magic,double taken,String awakening){
        register(new Definition(id,title,BASE,20,50,3,Map.of("minecraft:iron_ingot",32,"minecraft:lapis_lazuli",16)));
        STYLES.put(id,new CombatStyle(magic,taken,awakening));
    }
    public static CombatStyle style(String id){require(id);return STYLES.getOrDefault(id,new CombatStyle(false,.65,""));}
    public static String questId(String id){return require(id).prerequisite().equals(BASE)?SWORDMASTER:id;}
    public static synchronized void register(Definition job){if(DEFINITIONS.putIfAbsent(job.id(),job)!=null)throw new IllegalArgumentException("Duplicate job: "+job.id());}
    public static synchronized Definition require(String id){var job=DEFINITIONS.get(id);if(job==null)throw new IllegalArgumentException("未知职业");return job;}
    public static synchronized List<Definition> all(){return List.copyOf(DEFINITIONS.values());}
    public static boolean canAccept(Progress player,String id){var job=require(id);return !job.prerequisite().isEmpty()&&!player.hasJob(id)&&player.level>=job.minimumLevel()&&player.hasJob(job.prerequisite());}
    public static boolean canComplete(Progress player,String id){var job=require(id);var quest=player.quest(id);return canAccept(player,id)&&quest.accepted&&quest.kills>=job.kills()&&quest.elites>=job.elites();}
}

