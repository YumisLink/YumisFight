package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.*;
import java.util.*;

/** Admin-only single-career mastering: one job reaches max level with every learnable skill bound and armed. */
public final class CareerMastering {
    public record Result(Progress active,CareerProfiles book){}
    private CareerMastering(){}
    /**
     * Masters one job for a character. The active profile is mastered in place when it already holds the
     * job; otherwise the job's stored career entry is created or refreshed. Awakening and job-change
     * eligibility are character-wide flags, so they are granted on the active progress for every stored
     * profile to use after switching.
     */
    public static Result apply(String jobId,Progress current,CareerProfiles stored){
        String job=JobCatalog.require(jobId).id();
        var book=stored.copy();
        var active=CareerProfiles.copy(current);
        active.normalizeSlots();
        active.unlockJobChange();
        active.awakened=true;
        if(active.jobId().equals(job)){
            if(active.level<CharacterRules.MAX_LEVEL){active.gainLevels(CharacterRules.MAX_LEVEL-active.level);active.experience=0;}
            master(active);
        }
        else{
            var snapshot=book.snapshot(mastered(job));
            var existing=slot(book,job);
            if(existing!=null)existing.growth=snapshot;
            else{
                var free=slot(book,JobCatalog.NONE);
                if(free!=null){free.name=JobCatalog.require(job).title();free.growth=snapshot;}
                else{
                    while(book.entries.size()>=book.capacity)book.capacity++;
                    book.entries.put(UUID.randomUUID().toString(),new CareerProfiles.Entry(JobCatalog.require(job).title(),snapshot));
                }
            }
        }
        book.revision=Math.addExact(book.revision,1);
        book.validate(active);
        return new Result(active,book);
    }
    private static CareerProfiles.Entry slot(CareerProfiles book,String job){
        for(var entry:book.entries.values())if(entry.growth!=null&&entry.growth.get("job").getAsString().equals(job))return entry;
        return null;
    }
    private static Progress mastered(String jobId){
        var p=new Progress();
        p.gainLevels(CharacterRules.MAX_LEVEL-1);
        p.starterGranted=true;
        if(!JobCatalog.basic(jobId))p.awakened=true;
        p.promote(jobId);
        master(p);
        return p;
    }
    /** Learns every learnable skill at its highest rank, then binds the usable actives into free slots. */
    private static void master(Progress p){
        int budget=p.sp;
        for(var skill:SkillCatalog.all()){
            if(skill.equipmentGranted||!skill.available(p))continue;
            int automatic=skill.automaticByJob&&p.level>=skill.unlock?1:0;
            int rank=skill.limit(p.level,p.jobId())-automatic;
            if(rank<=0)continue;
            p.skills.put(skill.id,rank);
            budget-=Math.multiplyExact(skill.sp,rank);
        }
        p.sp=Math.max(0,budget);
        for(var skill:SkillCatalog.all()){
            if(skill.equipmentGranted||skill.passive()&&!skill.bindingProxy||!usable(p,skill))continue;
            for(int i=0;i<p.slots.length;i++)if(p.slots[i].isEmpty()){p.slots[i]=skill.id;break;}
        }
    }
    private static boolean usable(Progress p,SkillDefinition skill){
        return p.level>=skill.unlock&&(p.learned(skill.id)>0||skill.automaticByJob&&skill.available(p));
    }
}
