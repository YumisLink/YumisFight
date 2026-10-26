package cn.yumis.fight.rules;
import com.google.gson.*;
import java.math.BigInteger;
import java.util.*;
import static cn.yumis.fight.rules.Rules.*;
public final class Models {
    public static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private Models() {
    }
    public static <T>T copy(T value,Class<T> type){
        return JSON.fromJson(JSON.toJson(value),type);
    }
    public static class Progress {
        public int level=1,sp=15,strength,intelligence,spirit,questKills,questElites;
        public long experience;
        public boolean swordmaster,sync=true,questAccepted,starterGranted;
        public Map<String,Integer> skills=new LinkedHashMap<>();
        public String[] slots=new String[SLOT_COUNT];
        public Progress(){
            Arrays.fill(slots,"");
        }
        public int free(){
            return 2*(level-1)-strength-intelligence-spirit;
        }
        public int learned(Skill s){
            return skills.getOrDefault(s.name(),0);
        }
        public void normalizeSlots(){
            String[] old=slots,next=new String[SLOT_COUNT];
            Arrays.fill(next,"");
            if(old!=null){
                for(int i=0;i<Math.min(16,old.length);i++)next[i]=old[i]==null?"":old[i];
                int alt=old.length==32?24:16;
                for(int i=0;i<8&&alt+i<old.length;i++)next[16+i]=old[alt+i]==null?"":old[alt+i];
            }
            slots=next;
        }
        public void grant(long amount){
            if(amount<0)throw new IllegalArgumentException("经验不可为负");
            experience=Math.addExact(experience,amount);
            while(level<80&&experience>=nextExperience(level)){
                experience-=nextExperience(level);
                level++;
                sp+=level<20?15:level<30?25:level<40?40:65;
            }
            if(level==80)experience=0;
        }
        public void learn(Skill skill){
            int r=learned(skill);
            if(r>=skill.limit(level,swordmaster)||sp<skill.sp)throw new IllegalArgumentException("等级、职业或SP不足");
            skills.put(skill.name(),r+1);
            sp-=skill.sp;
        }
        public int learn(Skill skill,int count){
            batchCount(count);
            int amount=Math.min(count,Math.min(skill.limit(level,swordmaster)-learned(skill),sp/skill.sp));
            if(amount<=0)throw new IllegalArgumentException("等级、职业或SP不足");
            skills.put(skill.name(),learned(skill)+amount);
            sp-=skill.sp*amount;
            return amount;
        }
        public int allocate(String stat,int count){
            batchCount(count);
            int amount=Math.min(count,free());
            if(amount<=0)throw new IllegalArgumentException("自由属性点不足");
            switch(stat){
                case "strength"->strength+=amount;
                case "intelligence"->intelligence+=amount;
                case "spirit"->spirit+=amount;
                default->throw new IllegalArgumentException("未知属性");
            }
            return amount;
        }
        private static void batchCount(int count){
            if(count!=1&&count!=5)throw new IllegalArgumentException("加点数量必须为1或5");
        }
        public void resetSkills(){
            for(Skill s:Skill.values())sp+=learned(s)*s.sp;
            skills.clear();
            Arrays.fill(slots,"");
        }
    }
    public static class Profession {
        public BigInteger experience=BigInteger.ZERO,coins=BigInteger.ZERO;
        public int level(){
            return professionLevel(experience);
        }
        public void award(long xp,boolean admin){
            if(xp<=0)throw new IllegalArgumentException("奖励必须为正");
            experience=experience.add(BigInteger.valueOf(xp));
            if(!admin)coins=coins.add(BigInteger.valueOf(xp));
        }
    }
    public static class Runtime {
        public double mp=-1,shield=-1,trainingBoundary=-1;
        public long combatUntil,recordedAt,controlEpoch;
        public boolean training;
        public Map<String,Long> cooldowns=new HashMap<>(),buffs=new HashMap<>(),absorptionUntil=new HashMap<>();
        public Map<String,Double> absorption=new HashMap<>();
        public void validate(){
            finite(mp);
            finite(shield);
            if(mp<0||shield<0)throw new IllegalArgumentException("无效资源");
            for(double x:absorption.values()){
                finite(x);
                if(x<0)throw new IllegalArgumentException("无效护盾");
            }
        }
    }
    public static class Affix {
        public String id,stat="",operation="FLAT",family="",skill="",parameter="";
        public double value,maximum,baseline,baselineMaximum,roll=1;
        public boolean base,fixed;
        public int element=-1,rank;
        public Affix(){
        }
        public Affix(String id,Stat stat,double value,boolean base){
            this.id=id;
            this.stat=stat.name();
            this.value=value;
            this.baseline=value;
            this.base=base;
            this.fixed=base;
        }
    }
    public static class Gem {
        public String name="宝石";
        public int level=1;
        public String group="";
        public List<Affix> affixes=new ArrayList<>();
    }
    public static class Gear {
        public UUID instance=UUID.randomUUID();
        public int version=VERSION,level=1,revision,sockets;
        public Slot slot=Slot.WEAPON;
        public Weapon weapon=Weapon.KATANA;
        public Rarity rarity=Rarity.COMMON;
        public String set="",source="";
        public List<Affix> affixes=new ArrayList<>();
        public List<Gem> gems=new ArrayList<>();
        public boolean valid(Progress p){
            return level<=p.level&&p.level>=slot.minimum()&&(slot!=Slot.WEAPON||weapon!=Weapon.LIGHTSABER||p.swordmaster);
        }
    }
    public static class Stats {
        public EnumMap<Stat,Double> values=new EnumMap<>(Stat.class);
        public Map<String,Double> modifiers=new HashMap<>();
        public EnumMap<Family,Integer> ranks=new EnumMap<>(Family.class);
        public List<Affix> extras=new ArrayList<>();
        public List<cn.yumis.fight.api.SkillParameters.Change> parameters=new ArrayList<>();
        public String weaponInstance="",weaponType="";
        public int armorSet,jewelrySet;
        public boolean weaponValid;
        public double get(Stat s){
            return values.getOrDefault(s,0d);
        }
        public void add(Stat s,double v){
            finite(v);
            values.merge(s,v,Double::sum);
        }
        public int effective(Progress p,Skill s){
            int learned=p.learned(s);
            return learned==0||s.advanced()&&!p.swordmaster?0:learned+ranks.getOrDefault(s.family,0);
        }
        public double panel(boolean magic){
            return get(magic?Stat.MAGICAL_ATTACK:Stat.PHYSICAL_ATTACK)*(1+get(magic?Stat.INTELLIGENCE:Stat.STRENGTH)/100);
        }
    }
    public static class Life {
        public UUID target;
        public String dimension="",instance="";
        public long generation=1,revision,basis;
        public double q=1;
        public boolean dead;
    }
    public static class Target {
        public UUID id;
        public int entityId,originalLevel=1,partySize=1;
        public String dimension="",instance="",kind="NORMAL";
        public Difficulty difficulty=Difficulty.NORMAL;
        public boolean elite,noRewards;
        public double speciesHealth=1,speciesAttack=1;
        public Life life=new Life();
        public Map<String,Double> contributions=new HashMap<>();
        public int level(int playerLevel,boolean sync){
            return sync?Math.max(originalLevel,playerLevel):originalLevel;
        }
        public double health(int level){
            double n=level-1,base=switch(kind){
                case "BOSS","DRAGON"->2000+1000*n;
                case "WITHER"->1500+750*n;
                default->50+25*n;
            }
            ;
            return base*difficulty.health*partyMultiplier(partySize)*(elite?3:1)*speciesHealth;
        }
        public double attack(int level){
            return (kind.equals("DRAGON")?15+3*(level-1):kind.equals("WITHER")?12.5+2.5*(level-1):kind.equals("BOSS")?10+2*(level-1):5+(level-1))*difficulty.attack*(elite?1.5:1)*speciesAttack;
        }
    }
    public static class Team {
        public UUID id=UUID.randomUUID(),leader;
        public String name="战斗队伍";
        public LinkedHashSet<UUID> members=new LinkedHashSet<>();
        public Map<String,Long> invitations=new HashMap<>(),applications=new HashMap<>();
    }
    public static class ReturnPoint {
        public String dimension;
        public double x,y,z;
        public float yaw,pitch;
    }
    public static class Dungeon {
        public UUID id=UUID.randomUUID();
        public String dungeon="goblin_camp",status="RUNNING";
        public int cell,wave;
        public Difficulty difficulty=Difficulty.NORMAL;
        public boolean sync=true,summoned;
        public Set<UUID> roster=new LinkedHashSet<>(),withdrawn=new HashSet<>(),enemies=new HashSet<>();
        public Map<String,ReturnPoint> returns=new HashMap<>();
    }
    public record Receipt(boolean accepted,String reason,double actual,double q,long revision,String deathId) {
    }
    public static Receipt apply(Life life,double fraction,boolean heal){
        finite(fraction);
        if(fraction<0)throw new IllegalArgumentException("份额不可为负");
        if(life.dead)return new Receipt(false,"目标已死亡",0,life.q,life.revision,"");
        double actual=Math.min(heal?1-life.q:life.q,fraction);
        life.q=Math.clamp(life.q+(heal?actual:-actual),0,1);
        life.revision++;
        life.dead=life.q<=0;
        return new Receipt(true,"",actual,life.q,life.revision,life.dead?life.target+"/"+life.generation:"");
    }
}
