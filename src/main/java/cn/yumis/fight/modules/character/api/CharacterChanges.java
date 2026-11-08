package cn.yumis.fight.modules.character.api;

import cn.yumis.fight.modules.attributes.api.Stats;
import com.google.gson.JsonObject;

/** Pure personal progression decisions, invoked by the owner before submitting a stored result. */
public final class CharacterChanges {
    public record Result(Progress progress,CareerProfiles profiles,boolean starter,String ticket){}
    private CharacterChanges(){}
    public static Result apply(Progress current,CareerProfiles original,Stats stats,JsonObject request,SkillProgression skills,Runnable safe){
        String kind=request.get("kind").getAsString();var next=CareerProfiles.copy(current);var book=original.copy();boolean starter=false;String ticket="";
        switch(kind){
            case "learn","unlearn","resetSkills","bind"->skills.apply(next,stats,request);
            case "allocate"->next.allocate(request.get("stat").getAsString(),request.has("count")?request.get("count").getAsInt():1);
            case "resetAttributes"->next.strength=next.intelligence=next.spirit=0;
            case "sync"->next.sync=request.get("enabled").getAsBoolean();
            case "renameProfile"->book.rename(request.get("target").getAsString(),request.get("name").getAsString());
            case "unlockCareer"->{if(next.level<20)throw new IllegalArgumentException("转职解锁券需要20级");if(next.jobChangeUnlocked())throw new IllegalArgumentException("已经拥有通用转职资格，未消耗券");next.unlockJobChange();ticket=CharacterItems.CAREER_UNLOCK;}
            case "unlockAwakening"->{if(next.level<50)throw new IllegalArgumentException("觉醒券需要50级");if(next.awakened())throw new IllegalArgumentException("已经觉醒，未消耗券");next.awakened=true;ticket=CharacterItems.AWAKENING;}
            case "chooseJob"->{String id=request.get("job").getAsString();if(next.selected()||!JobCatalog.basic(id))throw new IllegalArgumentException("每套配置只能选择一次基础职业");next.promote(id);starter=!next.starterGranted;next.starterGranted=true;}
            case "promote"->{String id=request.get("job").getAsString();if(book.occupied(id))throw new IllegalArgumentException("已拥有该转职配置，请切换至已有配置");if(!JobCatalog.canPromote(next,id))throw new IllegalArgumentException("需要20级、对应基础职业和通用转职解锁资格");next.promote(id);book.fillSlots();}
            case "switchProfile"->{safe.run();next=book.select(next,request.get("target").getAsString());}
            case "openProfileSlot"->{safe.run();if(!book.unlocked(next))throw new IllegalArgumentException("职业栏需20级转职后解锁");int slot=request.get("slot").getAsInt();if(slot<book.entries.size()||slot>=book.capacity)throw new IllegalArgumentException("职业栏已变化，请刷新");book.fillSlots();next=book.select(next,new java.util.ArrayList<>(book.entries.keySet()).get(slot));}
            case "expandProfile"->{safe.run();book.expand(next);ticket=CharacterItems.PROFILE_EXPANSION;}
            case "levelOne","levelTen","levelMax"->{safe.run();next.gainLevels(kind.equals("levelMax")?CharacterRules.MAX_LEVEL-next.level:kind.equals("levelTen")?10:1);ticket=kind.equals("levelMax")?CharacterItems.LEVEL_MAX:kind.equals("levelTen")?CharacterItems.LEVEL_TEN:CharacterItems.LEVEL_ONE;}
            default->throw new IllegalArgumentException("未知进度操作");
        }
        book.validate(next);return new Result(next,book,starter,ticket);
    }
}
