package cn.yumis.fight;

import cn.yumis.fight.rules.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DamageMeterTest {
    @Test void individualCombatIntervalsPauseMergeAndHandleLateEvents(){
        var entry=new DamageMeter.Entry();entry.event(1000);entry.event(3000);entry.event(10000);entry.event(2000);
        assertEquals(5000,entry.elapsed(9000));assertEquals(6500,entry.elapsed(11500));assertEquals(8000,entry.elapsed(20000));
    }
    @Test void viewerProjectionChangesNumbersButNotCanonicalContribution(){
        var entry=new DamageMeter.Entry();entry.damage.put(10,100d);assertEquals(100,entry.displayed(80,false));assertEquals(100*81d/11,entry.displayed(80,true),1e-9);assertEquals(100,entry.displayed(1,true));assertEquals(100,entry.canonical());
    }
    @Test void hitTimePartyScaleIsFrozenInHistoricalBuckets(){
        var t=new Models.Target();t.originalLevel=10;t.partySize=1;var entry=new DamageMeter.Entry();entry.damage.put(10,.2*t.health(10));double old=entry.canonical();t.partySize=3;assertEquals(old,entry.canonical());entry.damage.merge(10,.2*t.health(10),Double::sum);assertTrue(entry.canonical()>2*old);
    }
    @Test void saveRoundTripKeepsDamageAndTime(){var meter=new DamageMeter();var entry=new DamageMeter.Entry();entry.event(1000);entry.damage.put(15,500d);meter.players.put("player",entry);meter.frozenAt=2000;var saved=Models.copy(meter,DamageMeter.class);assertEquals(1000,saved.players.get("player").elapsed(saved.frozenAt));assertEquals(500,saved.players.get("player").canonical());}
}
