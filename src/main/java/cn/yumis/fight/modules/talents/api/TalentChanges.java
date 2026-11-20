package cn.yumis.fight.modules.talents.api;

import cn.yumis.fight.modules.character.api.Progress;

/** 天赋盘点亮／拆除的纯判定：经 TalentLedger 校验后改写进度，供本人客户端在提交存档结果前调用。 */
public final class TalentChanges {
    private TalentChanges(){}
    public static void apply(TalentCatalog catalog,Progress progress,String kind,String talent){
        switch(kind){
            case "placeBirth"->{
                if(!TalentLedger.canPlaceBirth(catalog,progress,talent))throw new IllegalArgumentException(progress.talentAxis==null?"没有可用天赋点或该轴不存在":"已点亮其他轴的出生点，换轴须先拆空原轴两线再拆出生点");
                progress.talentAxis=talent;
            }
            case "removeBirth"->{
                if(!TalentLedger.canRemoveBirth(catalog,progress))throw new IllegalArgumentException(progress.talentAxis==null?"尚未点亮出生点":"本轴仍有节点，先拆空两线再拆出生点");
                progress.talentAxis=null;
            }
            case "placeTalent"->{
                if(!TalentLedger.canPlace(catalog,progress,talent))throw new IllegalArgumentException("该节点当前不可点亮");
                progress.talentNodes.add(talent);
            }
            case "removeTalent"->{
                if(!TalentLedger.canRemove(catalog,progress,talent))throw new IllegalArgumentException("该节点当前不可拆除");
                progress.talentNodes.remove(talent);
            }
            default->throw new IllegalArgumentException("未知天赋操作："+kind);
        }
    }
}
