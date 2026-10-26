package cn.yumis.fight;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class AffixRangesTest {
    @Test void usesSavedBaselineInsteadOfCurrentValue(){
        Affix a=new Affix("flat_strength",Stat.STRENGTH,10,false);a.value=11.5;
        var range=AffixRanges.reroll(a,Rarity.RARE,false);
        assertEquals(8,range.minimum());assertEquals(12,range.maximum());
    }
    @Test void percentageAndBothDamageEndpointsAreDistinct(){
        Affix a=new Affix();a.operation="PERCENT";a.baseline=.175;
        var percentage=AffixRanges.reroll(a,Rarity.LEGENDARY,false);
        assertEquals(14,percentage.minimum(),1e-9);assertEquals(21,percentage.maximum(),1e-9);
        a.operation="EXTRA";a.baseline=10;a.baselineMaximum=20;
        var damage=AffixRanges.reroll(a,Rarity.RARE,false);
        assertTrue(damage.interval());assertEquals(8,damage.minimum());assertEquals(12,damage.maximum());
        assertEquals(16,damage.upperMinimum());assertEquals(24,damage.upperMaximum());
    }
    @Test void fixedBaseGemsAndFixedRaritiesAreNeverOfferedAsRerollable(){
        Affix a=new Affix("flat_strength",Stat.STRENGTH,10,false);
        assertNull(AffixRanges.reroll(a,Rarity.EPIC,false));assertNull(AffixRanges.reroll(a,Rarity.MYTHIC,false));
        assertNull(AffixRanges.reroll(a,Rarity.RARE,true));a.fixed=true;assertNull(AffixRanges.reroll(a,Rarity.RARE,false));
        a.fixed=false;a.base=true;assertNull(AffixRanges.reroll(a,Rarity.RARE,false));
        a.base=false;a.operation="SKILL_PARAMETER";assertNull(AffixRanges.reroll(a,Rarity.RARE,false));
    }
}
