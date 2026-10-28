package cn.yumis.fight;
import cn.yumis.fight.modules.equipment.api.EquipmentAffixRanges;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.Rarity;

import cn.yumis.fight.modules.affixes.api.Affix;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class AffixRangesTest {
    @Test void usesSavedBaselineInsteadOfCurrentValue(){
        Affix a=new Affix("flat_strength",Stat.STRENGTH,10,false);a.value=11.5;
        var range=EquipmentAffixRanges.reroll(a,Rarity.RARE,false);
        assertEquals(8,range.minimum());assertEquals(12,range.maximum());
    }

    @Test void fixedBaseGemsAndFixedRaritiesAreNeverOfferedAsRerollable(){
        Affix a=new Affix("flat_strength",Stat.STRENGTH,10,false);
        assertNull(EquipmentAffixRanges.reroll(a,Rarity.EPIC,false));assertNull(EquipmentAffixRanges.reroll(a,Rarity.MYTHIC,false));
        assertNull(EquipmentAffixRanges.reroll(a,Rarity.RARE,true));a.fixed=true;assertNull(EquipmentAffixRanges.reroll(a,Rarity.RARE,false));
        a.fixed=false;a.base=true;assertNull(EquipmentAffixRanges.reroll(a,Rarity.RARE,false));
        a.base=false;a.operation="SKILL_PARAMETER";assertNull(EquipmentAffixRanges.reroll(a,Rarity.RARE,false));
    }
}
