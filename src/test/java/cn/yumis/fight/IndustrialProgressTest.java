package cn.yumis.fight;

import cn.yumis.fight.client.IndustrialProgress;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndustrialProgressTest {
    private JsonObject sample(long tick,long batch,double work,double rate){var j=new JsonObject();j.addProperty("server_tick",tick);j.addProperty("batch_sequence",batch);j.addProperty("progress",work);j.addProperty("progress_rate",rate);return j;}
    @Test void predictsEveryTickButFreezesWithoutFreshServerData(){var p=new IndustrialProgress();var j=sample(10,1,.2,.02);p.tick(j);assertEquals(.2,p.value(0),1e-9);for(int i=1;i<=5;i++){p.tick(j);assertEquals(.2+.02*i,p.value(0),1e-9);}for(int i=0;i<100;i++)p.tick(j);assertEquals(.3,p.value(1),1e-9);}
    @Test void authoritativeStopsAndNewBatchesReplacePrediction(){var p=new IndustrialProgress();var old=sample(10,1,.2,.02);p.tick(old);for(int i=0;i<4;i++)p.tick(old);var stalled=sample(15,1,.21,0);p.tick(stalled);for(int i=0;i<10;i++)p.tick(stalled);assertEquals(.21,p.value(1),1e-9);p.tick(sample(20,2,.01,.02));assertEquals(2,p.batch());assertEquals(.01,p.value(0),1e-9);}
    @Test void cannotClaimCompletionOrReplayAnOldSnapshot(){var p=new IndustrialProgress();var j=sample(10,1,.99,.2);p.tick(j);p.tick(j);assertTrue(p.value(1)<1);assertEquals("99.9%",String.format(java.util.Locale.ROOT,"%.1f%%",p.value(1)*100));p.tick(sample(9,1,.1,0));assertTrue(p.value(0)>.99);p.tick(sample(15,1,1,0));assertEquals(1,p.value(0));}
}
