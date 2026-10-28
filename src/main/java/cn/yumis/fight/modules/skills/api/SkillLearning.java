package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.Progress;
import com.google.gson.JsonObject;
import java.util.Arrays;

/** Skill-owned learning and binding rules; the caller persists the resulting character transaction. */
public final class SkillLearning {
    private SkillLearning(){}
    public static int effective(Stats stats,Progress progress,SkillDefinition skill){
        int learned=progress.learned(skill.id),granted=stats.ranks.getOrDefault("grant/"+skill.id,0);
        if(granted<=0&&(learned==0||skill.advanced()&&!progress.hasJob(skill.requiredJob)))return 0;
        int rank=Math.max(learned,granted)+stats.ranks.getOrDefault(skill.family,0);
        return rank+(CareerRules.magic(skill,progress)?stats.ranks.getOrDefault("tag/magic",0):0);
    }
    public static int learn(Progress progress,SkillDefinition skill,int count){
        if(skill.equipmentGranted)throw new IllegalArgumentException("装备授予技能无需SP，不能永久学习");
        if(count!=1&&count!=5)throw new IllegalArgumentException("加点数量必须为1或5");
        int amount=Math.min(count,Math.min(skill.limit(progress.level,progress.jobId())-progress.learned(skill.id),progress.sp/skill.sp));
        if(amount<=0)throw new IllegalArgumentException("等级、职业或SP不足");
        progress.skills.put(skill.id,progress.learned(skill.id)+amount);progress.sp-=skill.sp*amount;return amount;
    }
    public static void reset(Progress progress){
        // Unknown definitions remain saved until their content is available; they cannot consume a second refund.
        for(var skill:SkillCatalog.all()){progress.sp+=progress.learned(skill.id)*skill.sp;progress.skills.remove(skill.id);}
        Arrays.fill(progress.slots,"");
    }
    public static void refundCareer(Progress progress){
        for(var skill:SkillCatalog.all())if(skill.advanced()){
            progress.sp+=progress.learned(skill.id)*skill.sp;progress.skills.remove(skill.id);
            for(int i=0;i<progress.slots.length;i++)if(progress.slots[i].equals(skill.id))progress.slots[i]="";
        }
    }
    public static void apply(Progress progress,JsonObject request){
        switch(request.get("kind").getAsString()){
            case "learn"->learn(progress,SkillCatalog.require(request.get("skill").getAsString()),request.has("count")?request.get("count").getAsInt():1);
            case "resetSkills"->reset(progress);
            case "refundCareer"->refundCareer(progress);
            case "bind"->{
                int slot=request.get("slot").getAsInt();if(slot<0||slot>=progress.slots.length)throw new IllegalArgumentException("快捷槽无效");
                String id=request.get("skill").getAsString();
                if(!id.isEmpty()&&(SkillCatalog.require(id).passive()||progress.learned(id)<=0&&!SkillCatalog.require(id).equipmentGranted||SkillCatalog.require(id).advanced()&&!progress.hasJob(SkillCatalog.require(id).requiredJob)))throw new IllegalArgumentException("该技能尚未学习或为被动");
                progress.slots[slot]=id;
            }
            default->throw new IllegalArgumentException("未知技能操作");
        }
    }
}
