package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Career behavior content, registered through the existing skill dispatcher. */
final class CareerSkillBehaviors {
    private CareerSkillBehaviors(){}
    static void install(){
        SkillBehaviors.registerStart(CareerSkills.EYE,(x,c,now)->{if(!c.terminal)CareerClient.INSTANCE.eyeStarted(c,now);});
        SkillBehaviors.register(CareerSkills.FIELD,(x,c,n,e,s,v)->x.cancel(true));
        SkillBehaviors.register(CareerSkills.FRENZY,(x,c,n,e,s,v)->x.cancel(true));
        SkillBehaviors.register(CareerSkills.KALLA,(x,c,n,e,s,v)->{
            if(!c.terminal){x.cancel(true);return;}int kallaHits=(int)Math.max(1,c.parameters.get("slashes",3));double kallaGap=Math.max(50,c.parameters.get("interval",.3)*1000);int due=Math.min(kallaHits,(int)(e/kallaGap)+1);while(c.slashes<due){c.hit.clear();c.hitIndex=c.slashes++;x.slash(c,Math.max(.5,c.parameters.get("range",6)),4,3,c.parameters.get("damage",2),.1,false);x.visual(c,"HIT");}if(e>=kallaHits*kallaGap)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.TOMBSTONE,(x,c,n,e,s,v)->{
            int tombHits=(int)Math.max(1,c.parameters.get("slashes",10));double tombGap=Math.max(50,c.parameters.get("interval",.2)*1000);
            if(e<tombGap)stationary(x,c);else releaseEffect(c);
            int due=Math.min(tombHits,(int)(e/tombGap));while(c.slashes<due){c.hit.clear();c.hitIndex=c.slashes++;x.area(c,Math.max(.5,c.parameters.get("range",4)),c.parameters.get("damage",.65),false);x.visual(c,"HIT");}if(e>=tombHits*tombGap)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.PHANTOM,(x,c,n,e,s,v)->{
            PlayerPoseChannel.lock(c);
            double phantomSpan=Math.max(50,c.parameters.get("dashSeconds",.25)*1000),desired=Math.max(.5,c.parameters.get("dashDistance",8))*Math.min(1,e/phantomSpan),move=Math.max(0,desired-c.moved);x.player().move(MoverType.SELF,c.direction.scale(move));c.moved=desired;c.controlSeconds=Math.max(0,c.parameters.get("controlSeconds",.5));x.slash(c,1.5,3,3,c.parameters.get("damage",8.8),0,false);x.visual(c,"TRAIL");if(e>=phantomSpan)PlayerPoseChannel.release(c);if(e>=(long)(c.parameters.get("duration",.4)*1000))x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.BLACHE,(x,c,n,e,s,v)->{if(e>=(long)(c.parameters.get("finishDelay",1.2)*1000)&&!c.finisher){c.finisher=true;c.center=c.origin;c.hitIndex=0;x.area(c,Math.max(.5,c.parameters.get("range",8)),c.parameters.get("damage",12),false);x.visual(c,"FINISH");PlayerPoseChannel.release(c);}if(e>=(long)(c.parameters.get("duration",2)*1000))x.cancel(true);});
        SkillBehaviors.register(CareerSkills.ORB,(x,c,n,e,s,v)->{CareerClient.INSTANCE.orb(c,x);x.cancel(true);});
        SkillBehaviors.register(CareerSkills.CRESCENT,(x,c,n,e,s,v)->{
            double crescentMs=Math.max(50,c.parameters.get("crescentChargeSeconds",1)*1000);if(!c.release&&e<crescentMs){c.center=c.origin.add(c.direction.scale(.8)).add(0,1.2,0);if(n-c.visualTrail>=100){x.visual(c,"TRAIL");c.visualTrail=n;}return;}c.moved=Math.min(1,e/crescentMs);c.charging=false;CareerClient.INSTANCE.wave(c,x);x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.ICE,(x,c,n,e,s,v)->waves(x,c,e,(int)Math.max(1,c.parameters.get("slashes",3)),Math.max(.5,c.parameters.get("range",12)),3,2));
        SkillBehaviors.register(CareerSkills.FIRE,(x,c,n,e,s,v)->{CareerClient.INSTANCE.wave(c,x);x.cancel(true);});
        SkillBehaviors.register(CareerSkills.AGNI,(x,c,n,e,s,v)->{
            int count=(int)Math.max(1,c.parameters.get("slashes",10));double agniGap=Math.max(50,c.parameters.get("interval",.85/3)*1000);long agniEnd=Math.max((long)(c.parameters.get("duration",3)*1000),(long)(count*agniGap));
            if(e<agniGap)stationary(x,c);else releaseEffect(c);
            int due=Math.min(count,(int)(e/agniGap));while(c.slashes<due){c.hit.clear();c.hitIndex=c.slashes++;c.controlSeconds=c.hitIndex==0?Math.max(0,c.parameters.get("agniControlSeconds",3)):0;x.area(c,Math.max(.5,c.parameters.get("range",5)),c.parameters.get("damage",0),false);x.visual(c,"HIT");}if(e>=agniEnd&&!c.finisher){c.finisher=true;c.controlSeconds=0;c.hit.clear();c.hitIndex=count;x.area(c,Math.max(.5,c.parameters.get("range",5)),c.parameters.get("finisher",0),false);x.visual(c,"FINISH");}if(e>=agniEnd)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.EYE,(x,c,n,e,s,v)->{if(!c.terminal){x.cancel(true);return;}long eyeGap=Math.max(50,Math.round(c.parameters.get("interval",.8)*1000));if(e>=eyeGap&&!c.finisher){c.finisher=true;c.center=c.origin;x.area(c,c.parameters.get("range",16),c.parameters.get("damage",16),false);x.visual(c,"FINISH");}if(e>=eyeGap+400)x.cancel(true);});
        SkillBehaviors.register(CareerSkills.CRASH,(x,c,n,e,s,v)->{
            double crashRange=Math.max(.5,c.parameters.get("range",3));if(!c.finisher&&land(x,c,n,e,c.parameters.get("hopSeconds",.5)*1000,Math.max(.5,c.parameters.get("hopDistance",3)),Math.max(0,c.parameters.get("hopHeight",1.2)))){c.finisher=true;c.center=x.player().position();c.hitIndex=0;x.area(c,crashRange,c.parameters.get("damage",.6),false);c.hit.clear();c.hitIndex=1;x.area(c,crashRange,c.parameters.get("finisher",2),false);x.visual(c,"FINISH");}if(c.finisher&&(n-c.landedAt)*v>=200)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.HAND,(x,c,n,e,s,v)->{long handGap=Math.max(50,Math.round(c.parameters.get("interval",.35)*1000));if(e>=handGap&&!c.finisher){c.finisher=true;c.controlSeconds=Math.max(0,c.parameters.get("controlSeconds",.3));x.slash(c,Math.max(.5,c.parameters.get("range",3)),2,3,c.parameters.get("damage",4),0,false);x.visual(c,"RELEASE");PlayerPoseChannel.release(c);}if(e>=handGap+250)x.cancel(true);});
        SkillBehaviors.register(CareerSkills.RAGE,(x,c,n,e,s,v)->{releaseEffect(c);int rageHits=(int)Math.max(1,c.parameters.get("slashes",5));double rageGap=Math.max(50,c.parameters.get("interval",.15)*1000);int due=Math.min(rageHits,(int)(e/rageGap)+1);while(c.slashes<due){c.hit.clear();c.hitIndex=c.slashes++;x.area(c,Math.max(.5,c.parameters.get("range",4)),c.parameters.get("damage",1.3),false);x.visual(c,"HIT");}if(e>=(rageHits-1)*rageGap+200)x.cancel(true);});
        SkillBehaviors.register(CareerSkills.BLADE,(x,c,n,e,s,v)->{
            if(e>=200&&c.slashes==0){c.slashes=1;c.hitIndex=0;c.center=CareerClient.INSTANCE.firstTarget(c,5,2);x.slash(c,5,2,3,c.parameters.get("damage",2.2),0,false);releaseEffect(c);}if(e>=350&&!c.finisher){c.finisher=true;if(!c.hit.isEmpty()){c.hit.clear();c.hitIndex=1;x.area(c,3,c.parameters.get("finisher",6.6),false);}x.visual(c,"FINISH");}if(e>=650)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.ERUPTION,(x,c,n,e,s,v)->{
            long ready=Math.round(c.parameters.get("bloodSwordWindup",.25)*1000);
            if(e<ready)return;
            PlayerPoseChannel.lock(c);
            double eruptionRange=Math.max(.5,c.parameters.get("range",6));if(!c.finisher&&land(x,c,n,e-ready,c.parameters.get("hopSeconds",.6)*1000,Math.max(.5,c.parameters.get("hopDistance",6)),Math.max(0,c.parameters.get("hopHeight",2)))){c.finisher=true;c.center=x.player().position();c.hitIndex=0;x.area(c,eruptionRange,c.parameters.get("finisher",4),false);x.visual(c,"RELEASE");releaseEffect(c);}
            if(!c.finisher)return;int eruptionHits=(int)Math.max(1,c.parameters.get("slashes",4));double eruptionGap=Math.max(50,c.parameters.get("interval",.2)*1000);long landed=(long)((n-c.landedAt)*v);int due=Math.min(eruptionHits,(int)(landed/eruptionGap)+1);while(c.slashes<due){c.hit.clear();c.hitIndex=1+c.slashes++;x.area(c,eruptionRange,c.parameters.get("damage",2),false);x.visual(c,"HIT");}if(landed>=eruptionHits*eruptionGap+100)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.SWORD,(x,c,n,e,s,v)->{
            if(!c.terminal){x.cancel(true);return;}double total=c.parameters.get("damage",24),radius=c.parameters.get("range",StormChoreography.RADIUS),share=Math.clamp(c.parameters.get("swordFirstShare",.3),0,1);int rushes=(int)Math.max(1,c.parameters.get("swordRushes",5));if(e>=300&&!c.finisher){c.finisher=true;c.hitIndex=0;x.area(c,radius,total*share,false);x.visual(c,"RELEASE");releaseEffect(c);}int due=Math.min(rushes,Math.max(0,(int)((e-600)/300)+1));while(c.slashes<due){c.hit.clear();c.hitIndex=1+c.slashes++;x.area(c,radius,total*(1-share)/rushes,false);x.visual(c,c.slashes==rushes?"FINISH":"HIT");}if(e>=rushes*300+500)x.cancel(true);
        });
    }
    private static void releaseEffect(SkillCast cast){
        if(cast.effectContinues)return;
        cast.effectContinues=true;
        PlayerPoseChannel.release(cast);
    }
    private static void waves(SkillBehaviors.Context x,SkillCast c,long elapsed,int count,double range,double width,double fallback){double waveGap=Math.max(50,c.parameters.get("interval",.2)*1000);int due=Math.min(count,(int)(elapsed/waveGap)+1);while(c.slashes<due){c.hitIndex=c.slashes++;c.controlSeconds=c.skill.id.equals(CareerSkills.ICE)?Math.max(0,c.parameters.get("controlSeconds",1.5)):0;x.wave(c.origin.add(0,.15,0),c.direction,range,width,2,c.stats,c.skill,c.parameters.get("damage",fallback),false,true);x.visual(c,"HIT");}if(elapsed>=count*waveGap)x.cancel(true);}
    private static void stationary(SkillBehaviors.Context x,SkillCast c){x.player().setPos(c.origin);x.player().setDeltaMovement(Vec3.ZERO);}
    private static boolean land(SkillBehaviors.Context x,SkillCast c,long now,long elapsed,double duration,double length,double height){
        var p=x.player();
        // The authored hop ends at its starting height; from there collision and gravity must find real ground.
        if(elapsed<duration){double u=elapsed/duration,desired=length*u,delta=Math.max(0,desired-c.moved);double y=c.origin.y+4*height*u*(1-u);p.move(MoverType.SELF,c.direction.scale(delta).add(0,y-p.getY(),0));c.moved=desired;p.setDeltaMovement(Vec3.ZERO);return false;}
        double delta=Math.max(0,length-c.moved);p.move(MoverType.SELF,c.direction.scale(delta).add(0,-Math.max(.35,Math.min(1.5,p.getY()-c.origin.y)),0));c.moved=length;
        if(!p.onGround()){p.setDeltaMovement(0,Math.min(-.6,p.getDeltaMovement().y),0);if(now-c.start>=30000)x.cancel(false);return false;}
        c.landedAt=now;p.setDeltaMovement(Vec3.ZERO);return true;
    }
}
