package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Career behavior content, registered through the existing skill dispatcher. */
final class CareerSkillBehaviors {
    private CareerSkillBehaviors(){}
    static void install(){
        SkillBehaviors.register(CareerSkills.FIELD,(x,c,n,e,s,v)->x.cancel(true));
        SkillBehaviors.register(CareerSkills.FRENZY,(x,c,n,e,s,v)->x.cancel(true));
        SkillBehaviors.register(CareerSkills.KALLA,(x,c,n,e,s,v)->{
            if(!c.terminal){x.cancel(true);return;}int due=Math.min(3,(int)(e/300)+1);while(c.slashes<due){c.hit.clear();c.hitIndex=c.slashes++;x.slash(c,6,4,3,c.parameters.get("damage",2),.1,false);x.visual(c,"HIT");}if(e>=900)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.TOMBSTONE,(x,c,n,e,s,v)->{
            stationary(x,c);int due=Math.min(10,(int)(e/200));while(c.slashes<due){c.hit.clear();c.hitIndex=c.slashes++;x.area(c,4,c.parameters.get("damage",.65),false);x.visual(c,"HIT");}if(e>=2000)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.PHANTOM,(x,c,n,e,s,v)->{
            double desired=8*Math.min(1,e/250d),move=Math.max(0,desired-c.moved);x.player().move(MoverType.SELF,c.direction.scale(move));c.moved=desired;c.controlSeconds=.5;x.slash(c,1.5,3,3,c.parameters.get("damage",8.8),0,false);x.visual(c,"TRAIL");if(e>=400)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.BLACHE,(x,c,n,e,s,v)->{if(e>=1200&&!c.finisher){c.finisher=true;c.center=c.origin;c.hitIndex=0;x.area(c,8,c.parameters.get("damage",12),false);x.visual(c,"FINISH");}if(e>=2000)x.cancel(true);});
        SkillBehaviors.register(CareerSkills.ORB,(x,c,n,e,s,v)->{CareerClient.INSTANCE.orb(c,x);x.cancel(true);});
        SkillBehaviors.register(CareerSkills.CRESCENT,(x,c,n,e,s,v)->{
            if(!c.release&&e<1000){c.center=c.origin.add(c.direction.scale(.8)).add(0,1.2,0);if(n-c.visualTrail>=100){x.visual(c,"TRAIL");c.visualTrail=n;}return;}c.moved=Math.min(1,e/1000d);c.charging=false;CareerClient.INSTANCE.wave(c,x);x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.ICE,(x,c,n,e,s,v)->waves(x,c,e,3,12,3,2));
        SkillBehaviors.register(CareerSkills.FIRE,(x,c,n,e,s,v)->{CareerClient.INSTANCE.wave(c,x);x.cancel(true);});
        SkillBehaviors.register(CareerSkills.AGNI,(x,c,n,e,s,v)->{
            stationary(x,c);int count=(int)c.parameters.get("slashes",10),due=Math.min(count,(int)(e/(c.parameters.get("interval",.85/3)*1000)));while(c.slashes<due){c.hit.clear();c.hitIndex=c.slashes++;c.controlSeconds=c.hitIndex==0?3:0;x.area(c,5,c.parameters.get("damage",0),false);x.visual(c,"HIT");}if(e>=3000&&!c.finisher){c.finisher=true;c.controlSeconds=0;c.hit.clear();c.hitIndex=count;x.area(c,5,c.parameters.get("finisher",0),false);x.visual(c,"FINISH");}if(e>=3000)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.EYE,(x,c,n,e,s,v)->{if(!c.terminal){x.cancel(true);return;}if(e>=800&&!c.finisher){c.finisher=true;c.center=c.origin;x.area(c,c.parameters.get("range",16),c.parameters.get("damage",16),false);x.visual(c,"FINISH");}if(e>=1200)x.cancel(true);});
        SkillBehaviors.register(CareerSkills.CRASH,(x,c,n,e,s,v)->{
            if(!c.finisher&&land(x,c,n,e,500,3,1.2)){c.finisher=true;c.center=x.player().position();c.hitIndex=0;x.area(c,3,c.parameters.get("damage",.6),false);c.hit.clear();c.hitIndex=1;x.area(c,3,c.parameters.get("finisher",2),false);x.visual(c,"FINISH");}if(c.finisher&&(n-c.landedAt)*v>=200)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.HAND,(x,c,n,e,s,v)->{if(e>=350&&!c.finisher){c.finisher=true;c.controlSeconds=.3;x.slash(c,3,2,3,c.parameters.get("damage",4),0,false);x.visual(c,"RELEASE");}if(e>=600)x.cancel(true);});
        SkillBehaviors.register(CareerSkills.RAGE,(x,c,n,e,s,v)->{int due=Math.min(5,(int)(e/150)+1);while(c.slashes<due){c.hit.clear();c.hitIndex=c.slashes++;x.area(c,4,c.parameters.get("damage",1.3),false);x.visual(c,"HIT");}if(e>=800)x.cancel(true);});
        SkillBehaviors.register(CareerSkills.BLADE,(x,c,n,e,s,v)->{
            if(e>=200&&c.slashes==0){c.slashes=1;c.hitIndex=0;c.center=CareerClient.INSTANCE.firstTarget(c,5,2);x.slash(c,5,2,3,c.parameters.get("damage",2.2),0,false);}if(e>=350&&!c.finisher){c.finisher=true;if(!c.hit.isEmpty()){c.hit.clear();c.hitIndex=1;x.area(c,3,c.parameters.get("finisher",6.6),false);}x.visual(c,"FINISH");}if(e>=650)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.ERUPTION,(x,c,n,e,s,v)->{
            if(!c.finisher&&land(x,c,n,e,600,6,2)){c.finisher=true;c.center=x.player().position();c.hitIndex=0;x.area(c,6,c.parameters.get("finisher",4),false);x.visual(c,"RELEASE");}
            if(!c.finisher)return;long landed=(long)((n-c.landedAt)*v);int due=Math.min(4,(int)(landed/200)+1);while(c.slashes<due){c.hit.clear();c.hitIndex=1+c.slashes++;x.area(c,6,c.parameters.get("damage",2),false);x.visual(c,"HIT");}if(landed>=900)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.SWORD,(x,c,n,e,s,v)->{
            if(!c.terminal){x.cancel(true);return;}double total=c.parameters.get("damage",24),radius=c.parameters.get("range",StormChoreography.RADIUS);if(e>=300&&!c.finisher){c.finisher=true;c.hitIndex=0;x.area(c,radius,total*.3,false);x.visual(c,"RELEASE");}int due=Math.min(5,Math.max(0,(int)((e-600)/300)+1));while(c.slashes<due){c.hit.clear();c.hitIndex=1+c.slashes++;x.area(c,radius,total*.7/5,false);x.visual(c,c.slashes==5?"FINISH":"HIT");}if(e>=2000)x.cancel(true);
        });
    }
    private static void waves(SkillBehaviors.Context x,SkillCast c,long elapsed,int count,double range,double width,double fallback){int due=Math.min(count,(int)(elapsed/200)+1);while(c.slashes<due){c.hitIndex=c.slashes++;c.controlSeconds=c.skill.id.equals(CareerSkills.ICE)?1.5:0;x.wave(c.origin.add(0,.15,0),c.direction,range,width,2,c.stats,c.skill,c.parameters.get("damage",fallback),false,true);x.visual(c,"HIT");}if(elapsed>=count*200)x.cancel(true);}
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
