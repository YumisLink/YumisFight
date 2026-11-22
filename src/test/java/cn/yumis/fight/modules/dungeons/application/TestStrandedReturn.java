package cn.yumis.fight.modules.dungeons.application;
import cn.yumis.fight.modules.dungeons.api.ReturnPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 滞留救援：身处地下城维度却无副本状态时，离开请求按进入前记录的返回点送回；记录缺失时回落主世界共享出生点。 */
class TestStrandedReturn {
    @Test void recordedReturnPointIsKeptUntouched(){
        var stored=new ReturnPoint();
        stored.dimension="minecraft:overworld";stored.x=11;stored.y=70;stored.z=-22;stored.yaw=90;stored.pitch=-10;
        var chosen=DungeonService.strandedReturn(stored,"minecraft:overworld",8.5,64,-3.5);
        assertSame(stored,chosen,"进入前位置记录有效时必须原样使用，不得改写或重建");
        assertEquals(11,chosen.x,0);
        assertEquals(70,chosen.y,0);
        assertEquals(-22,chosen.z,0);
    }
    @Test void missingRecordFallsBackToOverworldSpawn(){
        var chosen=DungeonService.strandedReturn(null,"minecraft:overworld",8.5,64,-3.5);
        assertEquals("minecraft:overworld",chosen.dimension,"记录缺失时必须回落主世界维度");
        assertEquals(8.5,chosen.x,0);
        assertEquals(64,chosen.y,0);
        assertEquals(-3.5,chosen.z,0);
        assertEquals(0,chosen.yaw,0);
        assertEquals(0,chosen.pitch,0);
    }
}
