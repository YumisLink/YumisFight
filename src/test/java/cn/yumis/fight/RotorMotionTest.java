package cn.yumis.fight;

import cn.yumis.fight.industry.RotorMotion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RotorMotionTest {
    @Test void stopsAndResumesWithoutSnappingToWorldTime(){
        var motion=new RotorMotion();assertEquals(0,motion.sample(100,true,6));
        assertEquals(60,motion.sample(110,false,6));assertEquals(60,motion.sample(500,false,6));
        assertEquals(60,motion.sample(501,true,6));assertEquals(63,motion.sample(501.5,true,6));
    }
    @Test void wrapsAndHandlesReloadClockReset(){
        var motion=new RotorMotion();motion.sample(0,true,12);assertEquals(0,motion.sample(30,true,12));
        assertEquals(6,motion.sample(30.5,true,12));assertEquals(6,motion.sample(0,false,12));
    }
}
