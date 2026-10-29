package cn.yumis.fight.modules.character.api;
import cn.yumis.fight.architecture.StableId;
import java.util.*;
/** 转职内容注册表。新增职业只添加定义和本模块任务处理。 */
public final class JobCatalog {
    public static final String NONE="yumis_dungeon_and_fight:unselected", MAGE="yumis_dungeon_and_fight:mage", BASE="yumis_dungeon_and_fight:ghost_swordsman", SWORDMASTER="yumis_dungeon_and_fight:swordmaster",
        SOUL_BENDER="yumis_dungeon_and_fight:soul_bender", ASURA="yumis_dungeon_and_fight:asura", BERSERKER="yumis_dungeon_and_fight:berserker";
    public record CombatStyle(boolean magic,double damageTaken,String awakening,String awakeningSkill){}
    private static final Map<String,CombatStyle> STYLES=new LinkedHashMap<>();
    private static final Map<String,Set<String>> WEAPONS=new LinkedHashMap<>();
    public record Definition(String id,String title,String prerequisite,int minimumLevel){public Definition{new StableId(id);if(minimumLevel<1)throw new IllegalArgumentException("Invalid job");}}
    private static final Map<String,Definition> DEFINITIONS=new LinkedHashMap<>();
    static{
        register(new Definition(NONE,"未选择职业","",1));
        register(new Definition(BASE,"鬼剑士","",1));
        register(new Definition(MAGE,"魔法师","",1));
        STYLES.put(MAGE,new CombatStyle(true,.65,"",""));
        STYLES.put(BASE,new CombatStyle(false,.65,"",""));
        WEAPONS.put(BASE,Set.of("KATANA","LIGHTSABER","SHORTSWORD","GREATSWORD","BLUNT"));WEAPONS.put(MAGE,Set.of("STAFF","CLUB"));
        add(SWORDMASTER,"剑魂",false,.35,"剑圣","storm");
        add(SOUL_BENDER,"鬼泣",true,.50,"弑魂","blache");
        add(ASURA,"阿修罗",true,.75,"大暗黑天","wave_eye");
        add(BERSERKER,"狂战士",false,.25,"狱血魔神","blood_sword");
    }
    private JobCatalog(){}
    private static void add(String id,String title,boolean magic,double taken,String awakening,String awakeningSkill){
        register(new Definition(id,title,BASE,20));
        STYLES.put(id,new CombatStyle(magic,taken,awakening,"yumis_dungeon_and_fight:"+awakeningSkill));
    }
    public static CombatStyle style(String id){require(id);return STYLES.getOrDefault(id,new CombatStyle(false,.65,"",""));}
    public static boolean basic(String id){return !id.equals(NONE)&&require(id).prerequisite().isEmpty();}
    public static Set<String> weapons(String id){return WEAPONS.getOrDefault(base(id),Set.of());}
    public static String base(String id){var job=require(id);return job.prerequisite().isEmpty()?id:base(job.prerequisite());}
    public static synchronized void register(Definition job){if(DEFINITIONS.putIfAbsent(job.id(),job)!=null)throw new IllegalArgumentException("Duplicate job: "+job.id());}
    public static synchronized Definition require(String id){var job=DEFINITIONS.get(id);if(job==null)throw new IllegalArgumentException("未知职业");return job;}
    public static synchronized List<Definition> all(){return List.copyOf(DEFINITIONS.values());}
    public static boolean canPromote(Progress player,String id){var job=require(id);return !job.prerequisite().isEmpty()&&player.hasJob(job.prerequisite())&&player.level>=job.minimumLevel()&&player.completedJobQuests.contains(job.prerequisite());}
}

