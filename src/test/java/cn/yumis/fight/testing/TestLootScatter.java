package cn.yumis.fight.testing;
import cn.yumis.fight.modules.economy.api.LootPhysics;
import cn.yumis.fight.modules.economy.application.LootService;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 敌人死亡掉落散落：公共与私人掉落都按原版玩家死亡做法随机抛射，私人光柱补齐原版物品摩擦，落点高度与拾取窗口保持不变。 */
class TestLootScatter {
    /** 模拟一次敌人死亡结算的落点：坐标取整便于断言速度与偏移量。 */
    private static LootService.Drop drop(boolean privateLoot){
        var d=new LootService.Drop();
        if(privateLoot)d.owner="00000000-0000-0000-0000-000000000000";
        d.x=100;d.y=64.4;d.z=-200;
        return d;
    }
    @Test void publicDropsTossInRandomDirections(){
        for(long seed=0;seed<300;seed++){
            var random=RandomSource.create(seed);
            var d=drop(false);
            long before=System.currentTimeMillis();
            LootService.scatter(d,random);
            assertTrue(d.nativeToss,"公共掉落须走原版散落抛射，由物理散开");
            assertEquals(.2,d.dy,1e-12,"上抛速度与原版死亡散落一致");
            double horizontal=Math.hypot(d.dx,d.dz);
            assertTrue(horizontal>=0&&horizontal<=.5,"水平速度须落在原版 0~0.5 区间，实际 "+horizontal);
            assertEquals(100,d.x,0,"公共掉落出生点不动，散开交给物理");
            assertEquals(64.4,d.y,0);
            assertEquals(-200,d.z,0);
            assertTrue(d.pickupAt>=before+400&&d.pickupAt<=before+600,"拾取窗口保持 10 tick");
            assertTrue(d.owner.isEmpty());
        }
    }
    @Test void privateDropsTossLikeVanillaScatter(){
        for(long seed=0;seed<300;seed++){
            var random=RandomSource.create(seed);
            var d=drop(true);
            LootService.scatter(d,random);
            assertTrue(d.nativeToss,"私人掉落同样走原版散落抛射，由 PrivateLoot 物理散开");
            assertEquals(.2,d.dy,1e-12,"上抛速度与原版死亡散落一致");
            double horizontal=Math.hypot(d.dx,d.dz);
            assertTrue(horizontal>=0&&horizontal<=.5,"水平速度须落在原版 0~0.5 区间，实际 "+horizontal);
            assertEquals(100,d.x,0,"私人掉落出生点不动，散开交给物理");
            assertEquals(64.4,d.y,0);
            assertEquals(-200,d.z,0);
            assertEquals(0,d.pickupAt,"私人掉落拾取本就无延迟，不设置拾取窗口");
        }
    }
    @Test void privateLootFrictionMatchesVanillaItems(){
        // 空中：只受 0.98 空气阻力，不反弹。
        var air=LootPhysics.friction(new Vec3(.3,.2,-.4),false,.6F);
        assertEquals(.3*.98,air.x,1e-6);
        assertEquals(.2*.98,air.y,1e-6);
        assertEquals(-.4*.98,air.z,1e-6);
        // 地面：水平按脚下方块摩擦衰减，垂直下落反弹 -0.5。
        var ground=LootPhysics.friction(new Vec3(.3,-.1,-.4),true,.6F);
        assertEquals(.3*.98*.6,ground.x,1e-6);
        assertEquals(-.1*.98*-.5,ground.y,1e-6);
        assertEquals(-.4*.98*.6,ground.z,1e-6);
        // 地面上升段不反弹，仅空气阻力。
        var rising=LootPhysics.friction(new Vec3(0,.1,0),true,.6F);
        assertEquals(.1*.98,rising.y,1e-6);
    }
}
