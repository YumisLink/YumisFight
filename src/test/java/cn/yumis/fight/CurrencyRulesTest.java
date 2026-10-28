package cn.yumis.fight;
import cn.yumis.fight.modules.economy.api.CurrencyRules;
import cn.yumis.fight.modules.economy.api.CurrencyRules.Currency;
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

    @Test void veryLargeFiniteDropRateStillProducesValidProbabilities(){
        double[] probabilities=CurrencyRules.probabilities(Double.MAX_VALUE);
        assertEquals(1,Arrays.stream(probabilities).sum(),1e-12);
        for(double probability:probabilities)assertTrue(Double.isFinite(probability)&&probability>=0);
        assertThrows(IllegalArgumentException.class,()->CurrencyRules.probabilities(Double.NaN));
    }
}
