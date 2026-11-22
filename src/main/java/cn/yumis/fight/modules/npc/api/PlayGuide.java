package cn.yumis.fight.modules.npc.api;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.dungeons.api.DungeonCatalog;
import java.util.*;

/** 玩法看板条目派生：按玩家当前状态列出“现在能做的事”。只读派生、不保存状态、不做催促。 */
public final class PlayGuide {
    /** title=条目标题；note=一句话收益说明；target=I菜单页面ID，空串表示没有直达页。 */
    public record GuideEntry(String title,String note,String target){}
    /** 派生输入全部为主数据类型，供无头验证；jobId 为空按未选择职业处理。 */
    public record Facts(int level,String jobId,boolean tutorialDone,boolean awakened,boolean abyssInvitation,boolean treasureMap){
        public Facts{if(level<1)throw new IllegalArgumentException("等级必须为正");jobId=jobId==null||jobId.isBlank()?JobCatalog.NONE:jobId;}
    }
    /** 看板副本窗口：只显示当前等级段附近的副本，避免满级玩家看到整行旧图。 */
    private static final int DUNGEON_WINDOW=15;
    private PlayGuide(){}
    public static List<GuideEntry> entries(Facts facts){
        Objects.requireNonNull(facts);
        List<GuideEntry> result=new ArrayList<>();
        if(!facts.tutorialDone())result.add(new GuideEntry("伊蕾娜的旅途指引","出生村七课：选职业、学技能、亲手做第一件装备","tutorial"));
        if(facts.level()>=20&&JobCatalog.basic(facts.jobId()))result.add(new GuideEntry("转职试炼","通关王者级哥布林营地、龙人之塔、城主宫殿，回魔王祭坛提交后选择进阶职业","tutorial"));
        if(facts.level()>=50&&!facts.awakened())result.add(new GuideEntry("觉醒任务","50级四阶段：祭坛献礼 → 千室告解 → 觉醒塔试炼 → 回村觉醒，每阶段一枚大师水晶","tutorial"));
        int unlocked=0;
        for(var dungeon:DungeonCatalog.current().dungeons){
            if(!dungeon.available()||dungeon.level()>facts.level())continue;
            unlocked++;
            if(facts.level()-dungeon.level()>DUNGEON_WINDOW)continue;
            result.add(new GuideEntry("副本 · "+dungeon.name()+"（"+dungeon.level()+"级）","材料："+String.join("、",dungeon.materials()),"dungeons"));
        }
        if(unlocked>0)result.add(new GuideEntry("材料练功房","按已解锁副本定向刷区域材料，敌人同步你的等级，也可以挂机练级","dungeons"));
        if(facts.abyssInvitation())result.add(new GuideEntry("深渊派对","背包里有邀请函：副本页开启深渊挑战，个人奖池含具名装备","dungeons"));
        if(facts.treasureMap())result.add(new GuideEntry("藏宝图挖宝","背包里有哥布林藏宝图：主世界手持右键使用，挖开宝藏并击退守宝者",""));
        if(facts.level()>=80)result.add(new GuideEntry("引路石 · 地狱+","满级爬阶循环：野外引路台报名（最多8人），怪物逐阶变强，按阶结算分阶宝箱","dungeons"));
        if(facts.level()>=20)result.add(new GuideEntry("方舟战争","自走棋式回合防守：在“其他玩法”页报名备战，波次结算经验","other_games"));
        return result;
    }
}
