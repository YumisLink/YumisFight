package cn.yumis.fight;
import cn.yumis.fight.modules.dungeons.api.DamageMeter;
import cn.yumis.fight.bootstrap.ApplicationJson;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DamageMeterTest {
    @Test void individualCombatIntervalsPauseMergeAndHandleLateEvents(){
        var entry=new DamageMeter.Entry();entry.event(1000);entry.event(3000);entry.event(10000);entry.event(2000);
        assertEquals(5000,entry.elapsed(9000));assertEquals(6500,entry.elapsed(11500));assertEquals(8000,entry.elapsed(20000));
    }

    @Test void saveRoundTripKeepsDamageAndTime(){var meter=new DamageMeter();var entry=new DamageMeter.Entry();entry.event(1000);entry.damage.put(15,500d);meter.players.put("player",entry);meter.frozenAt=2000;var saved=ApplicationJson.copy(meter,DamageMeter.class);assertEquals(1000,saved.players.get("player").elapsed(saved.frozenAt));assertEquals(500,saved.players.get("player").canonical());}
}
