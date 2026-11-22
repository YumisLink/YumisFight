package cn.yumis.fight;
import cn.yumis.fight.modules.economy.api.AutoSellRule;
import cn.yumis.fight.modules.equipment.api.Rarity;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.EnumSet;
import java.util.List;

/** 获得时自动出售：稀有度多选、等级大于/小于/不限与本地持久化解码。 */
class AutoSellRuleTest {
    @Test void anyRuleHitSellsRegardlessOfOrder(){
        var lowCommon=new AutoSellRule(EnumSet.of(Rarity.COMMON,Rarity.RARE),AutoSellRule.Comparison.LESS,30);
        var highEpic=new AutoSellRule(EnumSet.of(Rarity.EPIC,Rarity.MYTHIC),AutoSellRule.Comparison.GREATER,60);
        var rules=List.of(lowCommon,highEpic);
        assertTrue(AutoSellRule.matches(rules,Rarity.COMMON,10));
        assertTrue(AutoSellRule.matches(rules,Rarity.MYTHIC,70));
        assertFalse(AutoSellRule.matches(rules,Rarity.COMMON,50));
        assertFalse(AutoSellRule.matches(rules,Rarity.EPIC,20));
        assertFalse(AutoSellRule.matches(rules,Rarity.ARTIFACT,50));
        assertFalse(AutoSellRule.matches(List.of(),Rarity.COMMON,10));
    }
    @Test void comparisonBoundariesUseStrictOrdering(){
        var rule=new AutoSellRule(EnumSet.of(Rarity.COMMON),AutoSellRule.Comparison.GREATER,30);
        assertFalse(rule.matches(Rarity.COMMON,30));
        assertTrue(rule.matches(Rarity.COMMON,31));
        rule=new AutoSellRule(EnumSet.of(Rarity.COMMON),AutoSellRule.Comparison.LESS,30);
        assertFalse(rule.matches(Rarity.COMMON,30));
        assertTrue(rule.matches(Rarity.COMMON,29));
        rule=new AutoSellRule(EnumSet.of(Rarity.COMMON),AutoSellRule.Comparison.ANY,30);
        assertTrue(rule.matches(Rarity.COMMON,1));
        assertTrue(rule.matches(Rarity.COMMON,85));
        assertFalse(rule.matches(Rarity.RARE,50));
    }
    @Test void emptyRaritySetNeverMatches(){
        var rule=new AutoSellRule(EnumSet.noneOf(Rarity.class),AutoSellRule.Comparison.ANY,30);
        for(var rarity:Rarity.values())assertFalse(rule.matches(rarity,50));
    }
    @Test void levelIsClampedToValidGearRange(){
        var below=new AutoSellRule(EnumSet.of(Rarity.COMMON),AutoSellRule.Comparison.GREATER,0);
        assertEquals(AutoSellRule.LEVEL_MIN,below.level());
        var above=new AutoSellRule(EnumSet.of(Rarity.COMMON),AutoSellRule.Comparison.GREATER,999);
        assertEquals(AutoSellRule.LEVEL_MAX,above.level());
        assertEquals(AutoSellRule.LEVEL_MIN,below.withLevel(-5).level());
        assertEquals(AutoSellRule.LEVEL_MAX,above.withLevel(1000).level());
    }
    @Test void withersKeepOtherParts(){
        var rule=new AutoSellRule(EnumSet.of(Rarity.COMMON),AutoSellRule.Comparison.LESS,30);
        var cycled=rule.withComparison(AutoSellRule.cycle(rule.comparison()));
        assertEquals(AutoSellRule.Comparison.ANY,cycled.comparison());
        assertEquals(30,cycled.level());
        assertEquals(EnumSet.of(Rarity.COMMON),cycled.rarities());
        var toggled=rule.withRarity(Rarity.RARE,true).withRarity(Rarity.COMMON,false);
        assertEquals(EnumSet.of(Rarity.RARE),toggled.rarities());
        var removed=rule.withRarity(Rarity.COMMON,false);
        assertTrue(removed.rarities().isEmpty());
        assertEquals(AutoSellRule.Comparison.GREATER,AutoSellRule.cycle(AutoSellRule.Comparison.ANY));
        assertEquals(AutoSellRule.Comparison.LESS,AutoSellRule.cycle(AutoSellRule.Comparison.GREATER));
    }
    @Test void localPersistenceRoundTripAndTolerantDecode(){
        var rule=new AutoSellRule(EnumSet.of(Rarity.RARE,Rarity.PRIMORDIAL),AutoSellRule.Comparison.GREATER,42);
        var encoded=rule.encode().toString();
        assertEquals(rule,AutoSellRule.decode(JsonParser.parseString(encoded)));
        assertEquals(new AutoSellRule(EnumSet.of(Rarity.RARE,Rarity.PRIMORDIAL),AutoSellRule.Comparison.GREATER,AutoSellRule.LEVEL_MAX),
                AutoSellRule.decode(JsonParser.parseString("{\"rarities\":[\"RARE\",\"PRIMORDIAL\"],\"comparison\":\"GREATER\",\"level\":999}")));
        assertNull(AutoSellRule.decode(JsonParser.parseString("{\"rarities\":[\"NO_SUCH\"],\"comparison\":\"ANY\",\"level\":10}")));
        assertNull(AutoSellRule.decode(JsonParser.parseString("[\"COMMON\"]")));
        assertNull(AutoSellRule.decode(JsonParser.parseString("{\"comparison\":\"ANY\"}")));
        assertNull(AutoSellRule.decode(JsonParser.parseString("{\"rarities\":[],\"comparison\":\"WHAT\",\"level\":10}")));
    }
}
