package cn.yumis.fight.modules.skills.api;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import java.util.*;
/** Summoner skill content and finite-window action budgets. No runtime renormalization. */
public final class SummonerSkills {
 private static final String NS="yumis_dungeon_and_fight:";
 public static final String SPIRITS=NS+"four_spirits",MASTERY=NS+"summon_mastery",LOUISE=NS+"louise",SANDOR=NS+"sandor",KING=NS+"echeverria",FRENZY=NS+"summon_frenzy",KASIJAS=NS+"kasijas";
 public static final List<String> ALL=List.of(SPIRITS,MASTERY,LOUISE,SANDOR,KING,FRENZY,KASIJAS);
 public static final List<String> RENEW=List.of(MageSkills.HERDER,SPIRITS,LOUISE,SANDOR,KING);
 public static final long PERMANENT_UNTIL=Long.MAX_VALUE/2;
 private SummonerSkills(){}
 public static boolean contains(String id){return ALL.contains(id);}
 public static List<SummonKind> kinds(String id){return Arrays.stream(SummonKind.values()).filter(k->k.skill().equals(id)).toList();}
 public static List<SummonKind> spawnKinds(String id,Stats stats){
  if(MageSkills.HERDER.equals(id)&&TowerBattleCard.SUMMONER_HERDER_SQUAD.held(stats)){
   return Collections.nCopies(Math.max(1,TowerBattleCard.SUMMONER_HERDER_SQUAD.count("count")),SummonKind.HERDER);
  }
  return kinds(id);
 }
 public static boolean lowerSpirit(SummonKind kind){return kind==SummonKind.FIRE||kind==SummonKind.ICE||kind==SummonKind.LIGHT||kind==SummonKind.DARK;}
 public static int lowerSpiritElement(SummonKind kind){
  return switch(kind){case FIRE->1;case ICE->2;case LIGHT->0;case DARK->3;default->-1;};
 }
 public static boolean permanentDuration(SummonKind kind,Stats stats,int mastery){
  return kind!=SummonKind.KASIJAS&&mastery>0&&TowerBattleCard.SUMMONER_MASTERY_PACT.held(stats);
 }
 public static boolean isPermanent(long until){return until>=PERMANENT_UNTIL/2;}
 public static float visualScale(SummonKind kind,Stats stats){
  return kind==SummonKind.HERDER&&TowerBattleCard.SUMMONER_HERDER_CANNON.held(stats)?1.65f:1.0f;
 }
 public static double herderBombDamageMultiplier(Stats stats){
  double mult=1d;
  if(TowerBattleCard.SUMMONER_HERDER_CANNON.held(stats))mult*=1+TowerBattleCard.SUMMONER_HERDER_CANNON.ratio("damage");
  if(TowerBattleCard.SUMMONER_HERDER_SQUAD.held(stats))mult*=1+TowerBattleCard.SUMMONER_HERDER_SQUAD.ratio("damage");
  return mult;
 }
 public static double effectiveRadius(SummonMoves.Move move,Stats stats){
  double r=move.radius();
  if(r<=0)return 0;
  if((move.id().equals("bomb")||move.id().equals("herder_cannon"))&&TowerBattleCard.SUMMONER_HERDER_SQUAD.held(stats)){
   r*=1+TowerBattleCard.SUMMONER_HERDER_SQUAD.ratio("radius");
  }
  if(move.id().equals("meteor")&&TowerBattleCard.SUMMONER_LOUISE_INFERNO.held(stats)){
   r*=1+TowerBattleCard.SUMMONER_LOUISE_INFERNO.ratio("radius");
  }
  return r;
 }
 public static boolean isRangedSummon(SummonKind kind,Stats stats){
  return switch(kind){
   case ICE,LOUISE,KING->true;
   case HERDER->TowerBattleCard.SUMMONER_HERDER_SQUAD.held(stats)||TowerBattleCard.SUMMONER_HERDER_CANNON.held(stats);
   case FIRE,LIGHT,DARK->TowerBattleCard.SUMMONER_SPIRITS_ASCEND.held(stats);
   default->false;
  };
 }
 public static String animationClip(String moveId){
  return switch(moveId){
   case "herder_cannon"->"bomb";
   case "fire_blast"->"flame_double";
   case "ice_burst"->"water_bolt";
   case "chain_light"->"light_contact";
   case "dark_wave"->"shadow_claw";
   case "fireball_volley"->"fireball";
   case "ice_pillar_tri"->"ice_pillar";
   case "king_mega_laser"->"king_laser";
   default->moveId;
  };
 }
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
 static void variants(){MageSkills.careerScales(JobCatalog.SUMMONER,.6,.8);}
 public static Stats inherit(Stats player,SummonKind kind,int mastery){
  var s=player.copy();
  double tankBonus=1d;
  if(kind==SummonKind.HERDER&&TowerBattleCard.SUMMONER_HERDER_CANNON.held(player))tankBonus*=1+TowerBattleCard.SUMMONER_HERDER_CANNON.ratio("tank");
  if(lowerSpirit(kind)&&TowerBattleCard.SUMMONER_SPIRITS_ASCEND.held(player))tankBonus*=1+TowerBattleCard.SUMMONER_SPIRITS_ASCEND.ratio("tank");
  s.values.put(Stat.HEALTH,s.get(Stat.HEALTH)*kind.health*tankBonus);
  for(var stat:List.of(Stat.PHYSICAL_DEFENSE,Stat.MAGICAL_DEFENSE))s.values.put(stat,s.get(stat)*kind.defense*tankBonus);
  for(var stat:List.of(Stat.LIGHT_RESIST,Stat.FIRE_RESIST,Stat.ICE_RESIST,Stat.DARK_RESIST,Stat.EVASION))s.values.put(stat,s.get(stat)*kind.resist);
  s.values.put(Stat.SHIELD,s.get(Stat.SHIELD)*kind.shield);
  s.values.put(Stat.LIFE_STEAL,0d);
  if(mastery>0){
   for(var stat:List.of(Stat.HEALTH,Stat.PHYSICAL_DEFENSE,Stat.MAGICAL_DEFENSE))s.mut(stat,.20+.04*(mastery-1));
   s.modifier("final").mut+=.10+.02*(mastery-1);
   if(TowerBattleCard.SUMMONER_MASTERY_PACT.held(player))s.extra(Stat.ATTACK_SPEED,TowerBattleCard.SUMMONER_MASTERY_PACT.value("speed"));
  }
  return s;
 }
 public static int[] elements(Stats stats,int[] original){return stats.modifiers.containsKey("summonElement")?(stats.modifiers.get("summonElement")<0?new int[0]:new int[]{stats.modifiers.get("summonElement").intValue()}):original;}
}
