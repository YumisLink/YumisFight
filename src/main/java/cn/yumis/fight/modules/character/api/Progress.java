package cn.yumis.fight.modules.character.api;
import static cn.yumis.fight.modules.character.api.CharacterRules.nextExperience;

import java.util.*;

/** character模块拥有的数据合同；仅接收当前存档代际。 */
public class Progress {
    public static final int SLOTS_PER_LAYER=8,SLOT_COUNT=24;
    public int level=1,sp=15,strength,intelligence,spirit;
    public long experience;
    public boolean sync=true,starterGranted;
    public boolean dungeonSync=true;
    public String job=JobCatalog.NONE;
    public Set<String> completedJobQuests=new LinkedHashSet<>();
    /** Null distinguishes existing current-format profiles whose learned awakening is already owned. */
    public Boolean awakened;
    public boolean awakened(){return awakened!=null?awakened:learned(JobCatalog.style(jobId()).awakeningSkill())>0;}
    public String baseJob(){return JobCatalog.base(jobId());}
    public boolean selected(){return !hasJob(JobCatalog.NONE);}
    public String jobId(){return job==null||job.isBlank()?JobCatalog.NONE:job;}
    public boolean hasJob(String id){return jobId().equals(id);}
    public void promote(String id){JobCatalog.require(id);job=id;}
    public Map<String,Integer> skills=new LinkedHashMap<>();
    public String[] slots=new String[SLOT_COUNT];
    public Progress(){
        Arrays.fill(slots,"");
    }
    public int free(){
        return 2*(level-1)-strength-intelligence-spirit;
    }
    public int learned(String id){
        return skills.getOrDefault(id,0);
    }
    public void normalizeSlots(){
        if(slots==null||slots.length!=SLOT_COUNT){slots=new String[SLOT_COUNT];Arrays.fill(slots,"");return;}
        for(int i=0;i<slots.length;i++)if(slots[i]==null)slots[i]="";
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

}
