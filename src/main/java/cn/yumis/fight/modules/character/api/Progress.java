package cn.yumis.fight.modules.character.api;
import static cn.yumis.fight.modules.character.api.CharacterRules.nextExperience;

import java.util.*;

/** character模块拥有的数据合同；仅接收当前存档代际。 */
public class Progress {
    public static final int SLOTS_PER_LAYER=8,SLOT_COUNT=24;
    /** 开发降级结果：退还的已分配属性点与被移除的超限天赋点。 */
    public record LevelRollback(int refunded,int talentsRemoved){}
    public int level=1,sp=15,strength,intelligence,vitality,spirit;
    public long experience;
    public boolean sync=true,starterGranted;
    public boolean dungeonSync=true;
    public String job=JobCatalog.NONE;
    public Set<String> completedJobQuests=new LinkedHashSet<>();
    public boolean jobChangeUnlocked(){return completedJobQuests!=null&&!completedJobQuests.isEmpty();}
    public void unlockJobChange(){completedJobQuests.add("yumis_dungeon_and_fight:all_jobs");}
    public boolean awakened;
    public boolean awakened(){return awakened;}
    public String baseJob(){return JobCatalog.base(jobId());}
    public boolean selected(){return !hasJob(JobCatalog.NONE);}
    public String jobId(){return job==null||job.isBlank()?JobCatalog.NONE:job;}
    public boolean hasJob(String id){return jobId().equals(id);}
    public void promote(String id){JobCatalog.require(id);job=id;}
    public Map<String,Integer> skills=new LinkedHashMap<>();
    public String[] slots=new String[SLOT_COUNT];
    public String talentAxis;
    public LinkedHashSet<String> talentNodes=new LinkedHashSet<>();
    public Progress(){
        Arrays.fill(slots,"");
    }
    public int free(){
        return 2*(level-1)-strength-intelligence-vitality-spirit;
    }
    public int learned(String id){
        return skills.getOrDefault(id,0);
    }
    public void normalizeSlots(){
        if(slots==null||slots.length!=SLOT_COUNT){slots=new String[SLOT_COUNT];Arrays.fill(slots,"");return;}
        for(int i=0;i<slots.length;i++)if(slots[i]==null)slots[i]="";
    }
    public long earnExperience(long base){
        grant(base);
        return base;
    }
    public void grant(long amount){
        if(amount<0)throw new IllegalArgumentException("经验不可为负");
        experience=Math.addExact(experience,amount);
        while(level<CharacterRules.MAX_LEVEL&&experience>=nextExperience(level)){
            experience-=nextExperience(level);
            level++;
            sp+=level<20?15:level<30?25:level<40?40:65;
        }
        if(level==CharacterRules.MAX_LEVEL)experience=0;
    }

    /** Exact level growth preserves spent SP and allocations; tickets clear the experience bar. */
    public void gainLevels(int amount){
        if(amount<=0||level>=CharacterRules.MAX_LEVEL)throw new IllegalArgumentException("已经满级或升级数量无效");
        int target=(int)Math.min(CharacterRules.MAX_LEVEL,(long)level+amount);
        sp=Math.addExact(sp,CharacterRules.spAt(target)-CharacterRules.spAt(level));level=target;experience=0;
    }

    /** 开发指令专用降级：按比例退还超出目标等级上限的加点，移除超限天赋，并扣回多出的SP；技能保留。 */
    public LevelRollback lowerLevel(int target){
        if(target>=level)return new LevelRollback(0,0);
        target=Math.clamp(target,1,CharacterRules.MAX_LEVEL);
        int budget=2*(target-1),total=strength+intelligence+vitality+spirit;
        int refunded=Math.max(0,total-budget);
        if(refunded>0){
            int[] values={strength,intelligence,vitality,spirit},scaled=new int[4];
            long[] remainders=new long[4];
            int assigned=0;
            for(int i=0;i<4;i++){
                long product=(long)values[i]*budget;
                scaled[i]=(int)(product/total);remainders[i]=product%total;assigned+=scaled[i];
            }
            for(int left=budget-assigned;left>0;left--){
                int pick=0;for(int i=1;i<4;i++)if(remainders[i]>remainders[pick])pick=i;
                scaled[pick]++;remainders[pick]=-1;
            }
            strength=scaled[0];intelligence=scaled[1];vitality=scaled[2];spirit=scaled[3];
        }
        int removed=0,cap=target/5;
        while(!talentNodes.isEmpty()&&talentNodes.size()+(talentAxis==null?0:1)>cap){talentNodes.removeLast();removed++;}
        if(talentAxis!=null&&talentNodes.size()+1>cap){talentAxis=null;removed++;}
        sp=Math.max(0,sp-(CharacterRules.spAt(level)-CharacterRules.spAt(target)));
        level=target;experience=0;
        return new LevelRollback(refunded,removed);
    }

    public int allocate(String stat,int count){
        batchCount(count);
        int amount=Math.min(count,free());
        if(amount<=0)throw new IllegalArgumentException("自由属性点不足");
        switch(stat){
            case "strength"->strength+=amount;
            case "intelligence"->intelligence+=amount;
            case "vitality"->vitality+=amount;
            case "spirit"->spirit+=amount;
            default->throw new IllegalArgumentException("未知属性");
        }
        return amount;
    }
    private static void batchCount(int count){
        if(count!=1&&count!=5)throw new IllegalArgumentException("加点数量必须为1或5");
    }

}
