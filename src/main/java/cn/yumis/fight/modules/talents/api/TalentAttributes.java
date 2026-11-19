package cn.yumis.fight.modules.talents.api;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.Progress;

/** 静态合成 applier，与 CharacterAttributes 同构；在 EquipmentAttributes 装配点的 sources.applyCharacter 之后调用。 */
public final class TalentAttributes {
    private TalentAttributes(){}
    public static void apply(Progress p,Stats s){
        if(p.talentAxis==null&&p.talentNodes.isEmpty())return;
        TalentEffects.apply(TalentCatalog.current(),s,p);
    }
}
