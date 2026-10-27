package cn.yumis.fight;
import cn.yumis.fight.rules.CurrencyRules;
import cn.yumis.fight.rules.CurrencyRules.Currency;
import static cn.yumis.fight.rules.Rules.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;
class CurrencyRulesTest {
    private static class Draws extends Random {
        private final double[] draws;
        private final int item;
        private int count,itemDraws,bound;
        Draws(double gate){this.draws=new double[]{gate};item=0;}
        Draws(double gate,double tier,int item){this.draws=new double[]{gate,tier};this.item=item;}
        @Override public double nextDouble(){return draws[count++];}
        @Override public int nextInt(int bound){this.bound=bound;itemDraws++;assertTrue(item>=0&&item<bound);return item;}
    }
    @Test void allFifteenCurrenciesHaveTheirAgreedRarity(){
        Map<String,Rarity> expected=Map.ofEntries(
            Map.entry("transmutation_stone",Rarity.COMMON),Map.entry("augmentation_stone",Rarity.RARE),
            Map.entry("regal_stone",Rarity.ARTIFACT),Map.entry("exalted_stone",Rarity.LEGENDARY),
            Map.entry("divine_stone",Rarity.EPIC),Map.entry("chaos_stone",Rarity.ARTIFACT),Map.entry("upgrade_stone",Rarity.ARTIFACT),
            Map.entry("fracturing_orb",Rarity.EPIC),Map.entry("socket_stone",Rarity.RARE),
            Map.entry("set_extraction_stone",Rarity.LEGENDARY),Map.entry("set_stone",Rarity.LEGENDARY),
            Map.entry("affix_extraction_stone",Rarity.LEGENDARY),Map.entry("affix_gem",Rarity.ARTIFACT),
            Map.entry("epic_upgrade_stone",Rarity.EPIC),Map.entry("mythic_upgrade_stone",Rarity.MYTHIC));
        assertEquals(expected.size(),CurrencyRules.ALL.size());
        expected.forEach((id,rarity)->assertEquals(rarity,CurrencyRules.find(id).rarity(),id));
        assertNull(CurrencyRules.find("magic_stone"));
        assertNull(CurrencyRules.find("minecraft:chaos_stone"));
    }
    @Test void naturalPoolExcludesDerivedItemsAndUnreleasedExtraction(){
        assertEquals(Set.of("transmutation_stone","augmentation_stone","regal_stone","exalted_stone","divine_stone",
            "chaos_stone","upgrade_stone","fracturing_orb","socket_stone","set_extraction_stone"),
            new HashSet<>(CurrencyRules.DROP_POOL.stream().map(Currency::id).toList()));
        for(String id:List.of("epic_upgrade_stone","mythic_upgrade_stone","set_stone","affix_gem","affix_extraction_stone"))
            assertFalse(CurrencyRules.find(id).naturalDrop(),id);
    }
    @Test void fivePercentOccurrenceIsIndependentOfPlayerDropRate(){
        for(double dropRate:new double[]{-30,0,100,10000,Double.MAX_VALUE}){
            for(double gate:new double[]{.05,.5,Math.nextDown(1d)}){
                Draws random=new Draws(gate);
                assertNull(CurrencyRules.roll(dropRate,random));
                assertEquals(1,random.count,"failed gate must not roll the currency type");
                assertEquals(0,random.itemDraws);
            }
            Draws random=new Draws(Math.nextDown(.05),.5,0);
            assertNotNull(CurrencyRules.roll(dropRate,random));
            assertEquals(2,random.count);
            assertEquals(1,random.itemDraws);
        }
    }
    @Test void conditionalWeightsUseCurrencyRarityAndNormalize(){
        assertEquals(100,CurrencyRules.DROP_TIERS.stream().mapToDouble(CurrencyRules.Tier::baseWeight).sum());
        double[] normal=CurrencyRules.rarityProbabilities(0),boosted=CurrencyRules.rarityProbabilities(1000);
        for(int i=0;i<normal.length;i++){
            var tier=CurrencyRules.DROP_TIERS.get(i);
            assertEquals(tier.baseWeight()/100,normal[i],1e-12);
            assertEquals(tier.baseWeight()*Math.pow(2,tier.rarity().ordinal())/264,boosted[i],1e-12);
        }
        assertArrayEquals(normal,CurrencyRules.rarityProbabilities(-100));
        assertEquals(50/109.3593,CurrencyRules.rarityProbabilities(100)[0],1e-12);
        assertEquals(1,Arrays.stream(boosted).sum(),1e-12);
    }
    @Test void rarityProbabilityIsSharedEquallyInsteadOfGrowingWithItemCount(){
        double[] probabilities=CurrencyRules.probabilities(0);
        for(var tier:CurrencyRules.DROP_TIERS){
            double total=0;
            for(Currency currency:tier.currencies()){
                double chance=probabilities[CurrencyRules.DROP_POOL.indexOf(currency)];
                assertEquals(tier.baseWeight()/100/tier.currencies().size(),chance,1e-12);
                total+=chance;
            }
            assertEquals(tier.baseWeight()/100,total,1e-12);
        }
        assertEquals(.05,probabilities[CurrencyRules.DROP_POOL.indexOf(CurrencyRules.find("chaos_stone"))],1e-12);
        assertEquals(.015,probabilities[CurrencyRules.DROP_POOL.indexOf(CurrencyRules.find("fracturing_orb"))],1e-12);
    }
    @Test void everyEligibleCurrencyCanBeSelectedAtBothDropRates(){
        for(double dropRate:new double[]{0,100,1000}){
            double[] probabilities=CurrencyRules.rarityProbabilities(dropRate);
            double start=0;
            for(int i=0;i<probabilities.length;i++){
                var pool=CurrencyRules.DROP_TIERS.get(i).currencies();
                for(int item=0;item<pool.size();item++){
                    Draws random=new Draws(0,start+probabilities[i]/2,item);
                    assertEquals(pool.get(item),CurrencyRules.roll(dropRate,random));
                    assertEquals(pool.size(),random.bound,"uniform selection must only see currencies in the selected rarity");
                    assertEquals(1,random.itemDraws);
                }
                start+=probabilities[i];
            }
        }
    }
    @Test void veryLargeFiniteDropRateStillProducesValidProbabilities(){
        double[] probabilities=CurrencyRules.probabilities(Double.MAX_VALUE);
        assertEquals(1,Arrays.stream(probabilities).sum(),1e-12);
        for(double probability:probabilities)assertTrue(Double.isFinite(probability)&&probability>=0);
        assertThrows(IllegalArgumentException.class,()->CurrencyRules.probabilities(Double.NaN));
    }
}
