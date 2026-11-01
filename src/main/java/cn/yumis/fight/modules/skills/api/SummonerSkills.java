package cn.yumis.fight.modules.skills.api;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.attributes.api.*;
import java.util.*;
/** Summoner skill content and finite-window action budgets. No runtime renormalization. */
public final class SummonerSkills {
 private static final String NS="yumis_dungeon_and_fight:";
 public static final String SPIRITS=NS+"four_spirits",MASTERY=NS+"summon_mastery",LOUISE=NS+"louise",SANDOR=NS+"sandor",KING=NS+"echeverria",FRENZY=NS+"summon_frenzy",KASIJAS=NS+"kasijas";
 public static final List<String> ALL=List.of(SPIRITS,MASTERY,LOUISE,SANDOR,KING,FRENZY,KASIJAS);
 public static final List<String> RENEW=List.of(MageSkills.HERDER,SPIRITS,LOUISE,SANDOR,KING);
 private SummonerSkills(){}
 public static boolean contains(String id){return ALL.contains(id);}
 public static List<SummonKind> kinds(String id){return Arrays.stream(SummonKind.values()).filter(k->k.skill().equals(id)).toList();}
 static void install(){
  add(SPIRITS,"精灵召唤：四灵契约",20,30,60,20,false,2);
  add(MASTERY,"召唤兽强化",25,40,0,0,true,0);
  add(LOUISE,"契约召唤：路易丝",30,50,100,25,false,3);
  add(SANDOR,"契约召唤：桑德尔",35,50,110,25,false,2);
  add(KING,"精灵召唤：伊伽贝拉",40,60,160,30,false,5);
  add(FRENZY,"召唤兽狂化",45,80,186,60,false,0);
  add(KASIJAS,"契约召唤：征服者卡西利亚斯",50,100,300,120,false,10);
 }
 private static void add(String id,String title,int level,int sp,int mp,int cd,boolean passive,double weight){
  var s=new SkillDefinition(id,title,"SUMMON",JobCatalog.SUMMONER,level,sp,mp/(100d+14*(level-1)),cd,weight>0?1:0,passive,Map.of("duration",id.equals(KASIJAS)?1d:.4,"slashes",1d,"range",16d,"summonDuration",id.equals(KASIJAS)?20d:120d));
  s.baseJob=JobCatalog.MAGE;s.magicDamage=true;s.magicTag=true;SkillCatalog.register(s);
 }
 static void balance(){for(String id:ALL){var skill=SkillCatalog.require(id);if(id.equals(MASTERY)||id.equals(FRENZY)){SkillBalance.nonDamage(skill);continue;}
  double w=id.equals(SPIRITS)||id.equals(SANDOR)?2:id.equals(LOUISE)?3:id.equals(KING)?5:10;
  SkillBalance.register(skill,new SkillBalanceProfile(w,120,false,false,0,(s,r,c)->{var hits=new ArrayList<SkillBalanceProfile.Hit>();for(var k:kinds(id))for(var a:SummonMoves.cues(k,r,120000,1))hits.add(new SkillBalanceProfile.Hit(a.at(),s.parameters(r).get("damage")*a.share(),false));return hits;}));
 }}
 public static Stats inherit(Stats player,SummonKind kind,int mastery){var s=player.copy();s.values.put(Stat.HEALTH,s.get(Stat.HEALTH)*kind.health);for(var stat:List.of(Stat.PHYSICAL_DEFENSE,Stat.MAGICAL_DEFENSE))s.values.put(stat,s.get(stat)*kind.defense);for(var stat:List.of(Stat.LIGHT_RESIST,Stat.FIRE_RESIST,Stat.ICE_RESIST,Stat.DARK_RESIST,Stat.EVASION))s.values.put(stat,s.get(stat)*kind.resist);s.values.put(Stat.SHIELD,s.get(Stat.SHIELD)*kind.shield);s.values.put(Stat.LIFE_STEAL,0d);if(mastery>0){for(var stat:List.of(Stat.HEALTH,Stat.PHYSICAL_DEFENSE,Stat.MAGICAL_DEFENSE))s.mut(stat,.20+.04*(mastery-1));s.modifier("final").mut+=.10+.02*(mastery-1);}return s;}
 public static int[] elements(Stats stats,int[] original){return stats.modifiers.containsKey("summonElement")?(stats.modifiers.get("summonElement")<0?new int[0]:new int[]{stats.modifiers.get("summonElement").intValue()}):original;}
}
