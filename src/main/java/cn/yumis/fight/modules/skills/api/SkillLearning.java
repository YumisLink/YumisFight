package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.Progress;
import com.google.gson.JsonObject;
import java.util.Arrays;

/** Skill-owned learning and binding rules; the caller persists the resulting character transaction. */
public final class SkillLearning {
    private SkillLearning(){}
    /** Automatically granted ranks are separate from purchased ranks, so resets refund only spent SP. */
    public static int baseRank(Progress progress,SkillDefinition skill){
        return progress.learned(skill.id)+(skill.automaticByJob&&skill.available(progress)&&progress.level>=skill.unlock?1:0);
    }
    public static int effective(Stats stats,Progress progress,SkillDefinition skill){
        int learned=baseRank(progress,skill),granted=stats.ranks.getOrDefault("grant/"+skill.id,0);
        if(!skill.available(progress)||granted<=0&&learned==0)return 0;
        int rank=Math.max(learned,granted)+stats.ranks.getOrDefault(skill.family,0);
        if(SunMoonSkills.contains(skill.id))rank+=stats.ranks.getOrDefault("SUN_MOON",0);
        if(skill.passive())rank+=stats.ranks.getOrDefault("PASSIVE",0);
        if(!skill.attackTypeRanks)return rank;
        boolean magic=CareerRules.magic(skill,progress);
        return rank+stats.ranks.getOrDefault(magic?"MAGIC":"PHYSICAL",0)+(magic?stats.ranks.getOrDefault("tag/magic",0):0);
    }
    public static int learn(Progress progress,SkillDefinition skill,int count){
        if(skill.equipmentGranted)throw new IllegalArgumentException("装备授予技能无需SP，不能永久学习");
        if(!skill.available(progress))throw new IllegalArgumentException("尚未获得该职业或觉醒资格");
        if(count!=1&&count!=5)throw new IllegalArgumentException("加点数量必须为1或5");
        int amount=Math.min(count,Math.min(skill.limit(progress.level,progress.jobId())-baseRank(progress,skill),progress.sp/skill.sp));
        if(amount<=0)throw new IllegalArgumentException("等级、职业或SP不足");
        progress.skills.put(skill.id,progress.learned(skill.id)+amount);progress.sp-=skill.sp*amount;return amount;
    }
    public static void reset(Progress progress){
        // Unknown definitions remain saved until their content is available; they cannot consume a second refund.
        for(var skill:SkillCatalog.all()){progress.sp+=progress.learned(skill.id)*skill.sp;progress.skills.remove(skill.id);}
        Arrays.fill(progress.slots,"");
    }
    public static int unlearn(Progress progress,Stats stats,SkillDefinition skill,int count){
        if(skill.equipmentGranted)throw new IllegalArgumentException("装备授予等级不能退还SP");
        if(count!=1&&count!=5)throw new IllegalArgumentException("退级数量必须为1或5");
        int amount=Math.min(count,progress.learned(skill.id));if(amount<=0)throw new IllegalArgumentException("该技能没有可退还的学习等级");
        int remaining=progress.learned(skill.id)-amount;if(remaining==0)progress.skills.remove(skill.id);else progress.skills.put(skill.id,remaining);
        progress.sp=Math.addExact(progress.sp,Math.multiplyExact(amount,skill.sp));
        if(!canBind(stats,progress,skill))for(int i=0;i<progress.slots.length;i++)if(progress.slots[i].equals(skill.id))progress.slots[i]="";
        return amount;
    }
    public static boolean canBind(Stats stats,Progress progress,SkillDefinition skill){return (!skill.passive()||skill.bindingProxy)&&effective(stats,progress,skill)>0;}
    public static void apply(Progress progress,Stats stats,JsonObject request){
        switch(request.get("kind").getAsString()){
            case "learn"->learn(progress,SkillCatalog.require(request.get("skill").getAsString()),request.has("count")?request.get("count").getAsInt():1);
            case "unlearn"->unlearn(progress,stats,SkillCatalog.require(request.get("skill").getAsString()),request.get("count").getAsInt());
            case "resetSkills"->reset(progress);
            case "bind"->{
                int slot=request.get("slot").getAsInt();if(slot<0||slot>=progress.slots.length)throw new IllegalArgumentException("快捷槽无效");
                String id=request.get("skill").getAsString();
                if(!id.isEmpty()&&!canBind(stats,progress,SkillCatalog.require(id)))throw new IllegalArgumentException("该技能未学习、缺少有效装备来源或为被动");
                progress.slots[slot]=id;
            }
            default->throw new IllegalArgumentException("未知技能操作");
        }
    }
}
