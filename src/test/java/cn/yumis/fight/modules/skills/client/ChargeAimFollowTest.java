package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.skills.api.CareerSkills;
import cn.yumis.fight.modules.skills.api.SkillCatalog;
import cn.yumis.fight.modules.skills.api.SkillCast;
import cn.yumis.fight.modules.skills.api.SkillDefinition;
import cn.yumis.fight.modules.skills.api.SkillParameters;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/** 蓄力技能朝向跟随准心：蓄力中转向准心、松键沿最终准心出招、蓄力锚点随准心移动。 */
class ChargeAimFollowTest {
    private static final class Recording implements SkillBehaviors.Context {
        final List<Vec3> slashes=new ArrayList<>();
        final List<String> phases=new ArrayList<>();
        int cancels;
        @Override public net.minecraft.client.player.LocalPlayer player(){return null;}
        @Override public void buff(String name,long expires){}
        @Override public void absorption(String name,double value){}
        @Override public void visual(SkillCast cast,String phase){phases.add(phase);}
        @Override public void slash(SkillCast cast,double length,double width,double height,double coefficient,double knockback,boolean fan){slashes.add(cast.direction);}
        @Override public void path(SkillCast cast,Vec3 from,Vec3 to,double width,double height,double coefficient,double knockback){}
        @Override public void wave(Vec3 origin,Vec3 direction,double range,double width,double height,Stats stats,SkillDefinition skill,double coefficient,boolean passive,boolean ground){}
        @Override public void area(SkillCast cast,double radius,double coefficient,boolean inward){}
        @Override public void passive(SkillCast cast){}
        @Override public boolean hit(SkillCast cast,LivingEntity target,double coefficient){return false;}
        @Override public void cancel(boolean normal){cancels++;}
    }
    private static SkillCast cast(SkillDefinition skill,Map<String,Double> values,Vec3 launchHeading){
        var c=new SkillCast();
        c.skill=skill;
        c.parameters=new SkillParameters.Snapshot(skill,1,values);
        c.stats=new Stats();
        c.origin=new Vec3(0,0,0);
        c.direction=launchHeading;
        c.charging=true;
        c.start=c.stageStart=0;
        c.speed=1;
        return c;
    }
    /** 鬼斩：起手朝向 +X，蓄力中准心转向、松键时按最终准心方向出刀，而不是回退到起手朝向。 */
    @Test void ghostReleaseFollowsCrosshairHeading(){
        var values=new LinkedHashMap<>(Map.of("damage",1.6,"range",3.,"chargeSeconds",1.,"chargeMultiplier",2.,"maxChargeSeconds",3.));
        var c=cast(SkillCatalog.GHOST,values,new Vec3(1,0,0));
        var recorder=new Recording();
        c.direction=new Vec3(0,0,1);
        SkillBehaviors.advance(recorder,c,500);
        assertTrue(recorder.slashes.isEmpty(),"蓄力未完成不应出刀");
        assertEquals(0,recorder.cancels);
        c.direction=new Vec3(.6,0,.8);
        c.release=true;
        SkillBehaviors.advance(recorder,c,600);
        assertEquals(1,recorder.slashes.size());
        assertEquals(new Vec3(.6,0,.8),recorder.slashes.get(0),"松键应沿最终准心方向出刀");
        assertEquals(1,recorder.cancels);
    }
    /** 邪光斩：蓄力光球锚点 = 原点 + 当前准心方向×0.8，准心转动后锚点随之移动。 */
    @Test void crescentChargeAnchorFollowsCrosshairHeading(){
        var values=new LinkedHashMap<>(Map.of("damage",2.,"range",12.,"crescentChargeSeconds",1.));
        var c=cast(SkillCatalog.require(CareerSkills.CRESCENT),values,new Vec3(1,0,0));
        var recorder=new Recording();
        SkillBehaviors.advance(recorder,c,100);
        assertEquals(new Vec3(.8,1.2,0),c.center);
        c.direction=new Vec3(0,0,-1);
        SkillBehaviors.advance(recorder,c,200);
        assertEquals(new Vec3(0,1.2,-.8),c.center,"蓄力锚点应跟随准心移动");
        assertTrue(c.charging,"仍在蓄力阶段");
        assertTrue(recorder.slashes.isEmpty());
        assertEquals(0,recorder.cancels);
    }
    /** 蓄力演出方向在拿不到施法者时回退到起手朝向，且原样返回不重复构造。 */
    @Test void chargeAimFallsBackToLaunchHeadingWithoutActor(){
        Vec3 fallback=new Vec3(.3,0,.4);
        assertSame(fallback,PlayerVisualAnchor.chargeAim(null,fallback));
    }
}
