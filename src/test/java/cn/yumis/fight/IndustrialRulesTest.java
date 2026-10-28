package cn.yumis.fight;
import cn.yumis.fight.modules.industry.api.ProcessingWork;
import cn.yumis.fight.modules.industry.api.IndustrialRules;
import cn.yumis.fight.modules.industry.api.Fraction;
import cn.yumis.fight.modules.industry.api.EnergyMath;

import cn.yumis.fight.modules.economy.api.CurrencyRules;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IndustrialRulesTest {

    @Test void fairAllocationConservesAndRotatesRemainder(){int cursor=0;long[] totals=new long[2];for(int i=0;i<100;i++){var a=EnergyMath.allocate(17,new long[]{10,30},cursor);assertEquals(17,Arrays.stream(a.grants()).sum());assertTrue(a.grants()[0]<=10&&a.grants()[1]<=30);totals[0]+=a.grants()[0];totals[1]+=a.grants()[1];cursor=a.nextCursor();}assertArrayEquals(new long[]{450,1250},totals);}
    @Test void allocationCannotOverflowIntermediateProducts(){var a=EnergyMath.allocate(Long.MAX_VALUE,new long[]{Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE},1);assertEquals(java.math.BigInteger.valueOf(Long.MAX_VALUE),Arrays.stream(a.grants()).mapToObj(java.math.BigInteger::valueOf).reduce(java.math.BigInteger.ZERO,java.math.BigInteger::add));}
    @Test void furnaceCapacityKeepsFractionalTicks(){int[] speeds={4,16,32,64,128},expected={40,160,320,640,1280};for(int i=0;i<speeds.length;i++){long paid=0,totalPaid=0;Fraction work=Fraction.ZERO;int made=0;long duration=200L*128/speeds[i];for(int tick=0;tick<2000;tick++){Fraction time=Fraction.of(128);while(time.positive()){long demand=ProcessingWork.demand(100,duration,work,paid,time);var step=ProcessingWork.advance(100,duration,work,paid,time,demand);totalPaid+=step.paid()-paid;work=step.work();paid=step.paid();time=step.timeLeft();if(step.finished()){made++;work=Fraction.ZERO;paid=0;}else break;}}assertEquals(expected[i],made);assertEquals(made*100L,totalPaid);}}
    @Test void veryExpensiveRecipeProgressesWithTinySupply(){Fraction work=Fraction.ZERO;long paid=0;for(int tick=0;tick<1000;tick++){var s=ProcessingWork.advance(209_715_200,2400L*128,work,paid,Fraction.of(128),1);work=s.work();paid=s.paid();}assertEquals(1000,paid);assertTrue(work.positive());assertTrue(work.value()<128);var finish=ProcessingWork.advance(209_715_200,2400L*128,work,paid,Fraction.of(2400L*128),209_715_200-paid);assertTrue(finish.finished());assertEquals(209_715_200,finish.paid());}
    @Test void zeroEnergyRecipeStillNeedsTime(){var s=ProcessingWork.advance(0,256,Fraction.ZERO,0,Fraction.of(128),0);assertFalse(s.finished());assertTrue(ProcessingWork.advance(0,256,s.work(),0,Fraction.of(128),0).finished());}

    @Test void factoryCurrencyNeverRollsAppearanceGate(){var random=new Random(473);Map<String,Integer> counts=new HashMap<>();for(int i=0;i<100000;i++){var c=CurrencyRules.rollGuaranteed(0,random);assertNotNull(c);counts.merge(c.id(),1,Integer::sum);}assertEquals(10,counts.size());assertEquals(.05,counts.get("upgrade_stone")/100000d,.003);assertEquals(.50,counts.get("transmutation_stone")/100000d,.008);}
    @Test void editableFactoryPoolDefaultsToCurrentNaturalPool(){var r=IndustrialRules.defaults();double[] configured=r.currencyPool(),natural=CurrencyRules.probabilities(0);for(int i=0;i<CurrencyRules.DROP_POOL.size();i++)assertEquals(natural[i],configured[CurrencyRules.ALL.indexOf(CurrencyRules.DROP_POOL.get(i))],1e-12);r.currencyItemWeights.put("upgrade_stone",3d);double[] changed=CurrencyRules.factoryProbabilities(r.currencyRarityWeights,r.currencyItemWeights);assertEquals(.09,changed[CurrencyRules.ALL.indexOf(CurrencyRules.find("upgrade_stone"))],1e-12);r.currencyItemWeights.put("upgrade_stone",-1d);assertThrows(IllegalArgumentException.class,()->CurrencyRules.factoryProbabilities(r.currencyRarityWeights,r.currencyItemWeights));}
}
