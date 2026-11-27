package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.combat.api.ActionTimeline;
import cn.yumis.fight.modules.skills.api.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Career behavior content, registered through the existing skill dispatcher. */
final class CareerSkillBehaviors {
    private CareerSkillBehaviors(){}
    static void install(){
        SkillBehaviors.registerStart(CareerSkills.EYE,(x,c,now)->{if(!c.terminal)CareerClient.INSTANCE.eyeStarted(c,now);});
        SkillBehaviors.registerStart(CareerSkills.KALLA,(x,c,now)->{if(c.terminal)PlayerPoseChannel.lock(c);});
        SkillBehaviors.registerStart(CareerSkills.CRESCENT,(x,c,now)->{
            if(!cn.yumis.fight.modules.dungeons.api.TowerBattleCard.ASURA_CRESCENT_MAX.held(c.stats))
                PlayerPoseChannel.loop(c,c.skill.id,PlayerPoseChannel.scaled(crescentChargeMillis(c),c.speed)+50,c.direction,true);
        });
        SkillBehaviors.registerStart(CareerSkills.AGNI,(x,c,now)->PlayerPoseChannel.loop(c,c.skill.id,
            PlayerPoseChannel.scaled(agniSchedule(c).durationMillis(),c.speed)+50,c.direction,false));
        SkillBehaviors.register(CareerSkills.FIELD,(x,c,n,e,s,v)->x.cancel(true));
        SkillBehaviors.register(CareerSkills.FRENZY,(x,c,n,e,s,v)->x.cancel(true));
        SkillBehaviors.register(CareerSkills.KALLA,(x,c,n,e,s,v)->{
            if(!c.terminal){x.cancel(true);return;}int kallaHits=(int)Math.max(1,c.parameters.get("slashes",3));double kallaGap=Math.max(50,c.parameters.get("interval",.3)*1000);int due=Math.min(kallaHits,(int)(e/kallaGap)+1);while(c.slashes<due){c.hit.clear();c.hitIndex=c.slashes++;x.slash(c,Math.max(.5,c.parameters.get("range",6)),4,3,c.parameters.get("damage",2),.1,false);x.visual(c,"HIT");}if(e>=kallaHits*kallaGap)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.MOONLIGHT,(x,c,n,e,s,v)->{
            var p=x.player();
            boolean fullMoon=SoulBenderRules.moonlightFullMoon(c.segment);
            double duration=Math.max(50,c.parameters.get("dashSeconds",.2)*1000);
            double length=Math.max(.5,c.parameters.get("dashDistance",2.4)*(fullMoon?1.15:1.0));
            double coeff=SoulBenderRules.moonlightSegmentCoefficient(c.parameters,c.stats,c.segment);
            x.buff("movementProtection",n+500);
            if(c.hitIndex!=c.segment){
                c.hitIndex=c.segment;
                if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_MOON_WAVE.held(c.stats)){
                    SoulBenderClient.spawnMoonWave(c,p.getLookAngle(),fullMoon,SoulBenderRules.moonlightWaveCoefficient(c.parameters,c.stats,c.segment));
                }
            }
            if(s<=duration||c.moved<length){
                Vec3 before=p.position();
                double desired=length*Math.min(1,s/duration),move=Math.max(0,desired-c.moved);
                p.move(MoverType.SELF,c.direction.scale(move));
                c.moved+=move;
                TowerSkillCombat.moved(c,before);
                c.sweepDegrees=fullMoon?360:0;
                double reach=fullMoon?Math.max(.5,c.parameters.get("finisherRange",4.2)):Math.max(.5,c.parameters.get("range",3.8));
                double width=Math.max(.5,c.parameters.get("width",3.2));
                x.slash(c,reach,width,2.8,coeff,fullMoon?.35:.25,fullMoon);
                if(move>0&&p.position().distanceToSqr(before)<.00001&&s>50){c.stageStart=n-(long)(duration/v);c.moved=length;}
            }
            if(s>=duration&&c.slashes<c.segment){
                c.slashes=c.segment;
                PlayerPoseChannel.release(c);
            }
            if(n>c.window||c.segment>=SoulBenderRules.moonlightSegments(c.parameters)&&s>duration)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.TOMBSTONE,(x,c,n,e,s,v)->{
            if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_TOMB_DETACH.held(c.stats)){
                SoulBenderClient.startDetachedTombstone(c);
                x.cancel(true);
                return;
            }
            int tombHits=(int)Math.max(1,c.parameters.get("slashes",10));double tombGap=Math.max(50,c.parameters.get("interval",.2)*1000);
            if(e<tombGap)stationary(x,c);else releaseEffect(c);
            int due=Math.min(tombHits,(int)(e/tombGap));while(c.slashes<due){c.hit.clear();c.hitIndex=c.slashes++;x.area(c,Math.max(.5,c.parameters.get("range",4)),c.parameters.get("damage",.65),false);x.visual(c,"HIT");}if(e>=tombHits*tombGap)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.PHANTOM,(x,c,n,e,s,v)->{
            if(c.slashes<c.segment){
                c.slashes=c.segment;
                c.hitIndex=c.segment-1;
                long invMs=Math.max(1,(long)(c.parameters.get("invulnerableMillis",250)/c.speed));
                cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.acquireStatus(c.id,cn.yumis.fight.modules.combat.api.ActionStatus.INVULNERABLE,n+invMs);
            }
            double phantomSpan=Math.max(50,c.parameters.get("dashSeconds",.25)*1000);
            if(s<phantomSpan)PlayerPoseChannel.lock(c);
            double dashDist=Math.max(.5,c.parameters.get("dashDistance",8)),desired=dashDist*Math.min(1,s/phantomSpan),move=Math.max(0,desired-c.moved);
            var before=x.player().position();x.player().move(MoverType.SELF,c.direction.scale(move));TowerSkillCombat.moved(c,before);c.moved=desired;
            c.controlSeconds=Math.max(0,c.parameters.get("controlSeconds",.5));
            double width=Math.max(.5,c.parameters.get("width",3));
            if(s<=phantomSpan+50)x.path(c,before,x.player().position().add(c.direction.scale(1.5)),width,3,c.parameters.get("damage",8.8),0);
            x.visual(c,"TRAIL");
            if(s>=phantomSpan)PlayerPoseChannel.release(c);
            int maxSeg=(int)Math.max(1,Math.round(c.parameters.get("slashes",1)));
            long segDur=(long)(c.parameters.get("duration",.4)*1000);
            if(maxSeg<=1){if(s>=segDur)x.cancel(true);}
            else if(n>c.window||c.segment>=maxSeg&&s>=segDur)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.BLACHE,(x,c,n,e,s,v)->{
            long finishAt=(long)(c.parameters.get("finishDelay",1.2)*1000);
            long endAt=(long)(c.parameters.get("duration",2)*1000);
            SkillBehaviors.runTimeline(x,c,e,ActionTimeline.<SkillBehaviors.Step>builder()
                .at(finishAt,"blache_finish",step->{
                    step.cast().finisher=true;step.cast().center=step.cast().origin;step.cast().hitIndex=0;
                    step.context().area(step.cast(),Math.max(.5,step.cast().parameters.get("range",8)),step.cast().parameters.get("damage",12),false);
                    step.context().visual(step.cast(),"FINISH");PlayerPoseChannel.release(step.cast());
                })
                .at(endAt,"blache_end",step->step.context().cancel(true))
                .build());
        });
        SkillBehaviors.register(CareerSkills.ORB,(x,c,n,e,s,v)->{CareerClient.INSTANCE.orb(c,x);x.cancel(true);});
        SkillBehaviors.register(CareerSkills.CRESCENT,(x,c,n,e,s,v)->{
            if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.ASURA_CRESCENT_MAX.held(c.stats)){
                c.moved=1;
                c.charging=false;
                PlayerPoseChannel.play(c,"ghost_release",PlayerPoseChannel.scaled(PlayerPoseChannel.length("ghost_release"),c.speed),c.direction,false);
                CareerClient.INSTANCE.wave(c,x);
                x.cancel(true);
                return;
            }
            double chargeMillis=crescentChargeMillis(c);
            if(!c.release&&e<chargeMillis){
                c.center=c.origin.add(c.direction.scale(.8)).add(0,1.2,0);
                if(n-c.visualTrail>=100){x.visual(c,"TRAIL");c.visualTrail=n;}
                return;
            }
            c.moved=Math.min(1,e/chargeMillis);
            c.charging=false;
            PlayerPoseChannel.play(c,"ghost_release",PlayerPoseChannel.scaled(PlayerPoseChannel.length("ghost_release"),c.speed),c.direction,false);
            CareerClient.INSTANCE.wave(c,x);
            x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.ICE,(x,c,n,e,s,v)->{
            if(c.slashes==0&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.ASURA_ICE_HOMING.held(c.stats))
                CareerClient.INSTANCE.startHomingIce(c);
            waves(x,c,e,(int)Math.max(1,c.parameters.get("slashes",3)),Math.max(.5,c.parameters.get("range",12)),3,2);
        });
        SkillBehaviors.register(CareerSkills.FIRE,(x,c,n,e,s,v)->{CareerClient.INSTANCE.wave(c,x);x.cancel(true);});
        SkillBehaviors.register(CareerSkills.AGNI,(x,c,n,e,s,v)->{
            var schedule=agniSchedule(c);
            stationary(x,c);
            int due=Math.min(schedule.slashes(),(int)(e/schedule.intervalMillis()));
            while(c.slashes<due){
                c.hit.clear();
                c.hitIndex=c.slashes++;
                c.controlSeconds=c.hitIndex==0?Math.max(0,c.parameters.get("agniControlSeconds",3)):0;
                x.area(c,Math.max(.5,c.parameters.get("range",5)),c.parameters.get("damage",0),false);
                x.visual(c,"HIT");
            }
            if(e>=schedule.durationMillis()){
                c.finisher=true;
                c.controlSeconds=0;
                c.hit.clear();
                c.hitIndex=schedule.slashes();
                x.area(c,Math.max(.5,c.parameters.get("range",5)),c.parameters.get("finisher",0),false);
                x.visual(c,"FINISH");
                PlayerPoseChannel.cancel(c);
                x.cancel(true);
            }
        });
        SkillBehaviors.register(CareerSkills.EYE,(x,c,n,e,s,v)->{
            if(!c.terminal){x.cancel(true);return;}
            long eyeGap=Math.max(50,Math.round(c.parameters.get("interval",.8)*1000));
            SkillBehaviors.runTimeline(x,c,e,ActionTimeline.<SkillBehaviors.Step>builder()
                .at(eyeGap,"eye_finish",step->{
                    step.cast().finisher=true;step.cast().center=step.cast().origin;
                    step.context().area(step.cast(),step.cast().parameters.get("range",16),step.cast().parameters.get("damage",16),false);
                    step.context().visual(step.cast(),"FINISH");
                })
                .at(eyeGap+400,"eye_end",step->step.context().cancel(true))
                .build());
        });
        SkillBehaviors.register(CareerSkills.CRASH,(x,c,n,e,s,v)->{
            double crashRange=Math.max(.5,c.parameters.get("range",4));if(!c.finisher&&land(x,c,n,e,c.parameters.get("hopSeconds",.5)*1000,Math.max(.5,c.parameters.get("hopDistance",3)),Math.max(0,c.parameters.get("hopHeight",1.2)))){c.finisher=true;c.center=x.player().position();c.hitIndex=0;x.area(c,crashRange,c.parameters.get("damage",.6),false);c.hit.clear();c.hitIndex=1;x.area(c,crashRange,c.parameters.get("finisher",2),false);x.visual(c,"FINISH");}if(c.finisher&&(n-c.landedAt)*v>=200)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.HAND,(x,c,n,e,s,v)->{
            if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.BERSERKER_HAND_CLAW.held(c.stats)){
                BerserkerClient.spawnBloodClaw(c);
                PlayerPoseChannel.release(c);
                x.cancel(true);
                return;
            }
            long handGap=Math.max(50,Math.round(c.parameters.get("interval",.35)*1000));
            SkillBehaviors.runTimeline(x,c,e,ActionTimeline.<SkillBehaviors.Step>builder()
                .at(handGap,"hand_release",step->{
                    step.cast().finisher=true;step.cast().controlSeconds=Math.max(0,step.cast().parameters.get("controlSeconds",.3));
                    double handRange=Math.max(.5,step.cast().parameters.get("range",4));
                    double handWidth=Math.max(.5,step.cast().parameters.get("width",3));
                    step.context().slash(step.cast(),handRange,handWidth,3,step.cast().parameters.get("damage",4),0,false);
                    step.context().visual(step.cast(),"RELEASE");PlayerPoseChannel.release(step.cast());
                })
                .at(handGap+250,"hand_end",step->step.context().cancel(true))
                .build());
        });
        SkillBehaviors.register(CareerSkills.RAGE,(x,c,n,e,s,v)->{releaseEffect(c);int rageHits=(int)Math.max(1,c.parameters.get("slashes",5));double rageGap=Math.max(50,c.parameters.get("interval",.15)*1000);int due=Math.min(rageHits,(int)(e/rageGap)+1);while(c.slashes<due){c.hit.clear();c.hitIndex=c.slashes++;x.area(c,Math.max(.5,c.parameters.get("range",5)),c.parameters.get("damage",1.3),false);x.visual(c,"HIT");}if(e>=(rageHits-1)*rageGap+200)x.cancel(true);});
        SkillBehaviors.register(CareerSkills.BLADE,(x,c,n,e,s,v)->{
            boolean hasThrow=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.BERSERKER_BLADE_THROW.held(c.stats);
            boolean hasAssault=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.BERSERKER_BLADE_ASSAULT.held(c.stats);
            if(hasThrow&&!hasAssault){
                BerserkerClient.spawnFlyingBlade(c,new HashSet<>(),1,1.0);
                PlayerPoseChannel.release(c);
                x.cancel(true);
                return;
            }
            if(hasAssault){
                double dashSpan=240.0;
                double dashDist=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.BERSERKER_BLADE_ASSAULT.value("dash");
                double bladeWidth=Math.max(.5,c.parameters.get("width",3));
                int blasts=Math.max(1,cn.yumis.fight.modules.dungeons.api.TowerBattleCard.BERSERKER_BLADE_ASSAULT.count("blasts"));
                double ratio=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.BERSERKER_BLADE_ASSAULT.ratio("ratio");
                if(hasThrow&&c.slashes==0){
                    c.slashes=1;
                    BerserkerClient.spawnFlyingBlade(c,c.hit,blasts,ratio);
                }
                if(e<=dashSpan&&c.moved<dashDist){
                    PlayerPoseChannel.lock(c);
                    x.buff("movementProtection",n+500);
                    var p=x.player();
                    Vec3 before=p.position();
                    if(!p.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.RITUAL_ROOT)){
                        double desired=dashDist*Math.min(1,e/dashSpan);
                        double move=Math.max(0,desired-c.moved);
                        p.move(MoverType.SELF,c.direction.scale(move));
                        TowerSkillCombat.moved(c,before);
                        c.moved=desired;
                    }else{
                        c.moved=dashDist;
                    }
                    int beforeHits=c.hit.size();
                    c.hitIndex=0;
                    x.path(c,before,p.position().add(c.direction.scale(1.5)),bladeWidth,3,c.parameters.get("damage",2.2),0);
                    if(beforeHits==0&&!c.hit.isEmpty()){
                        Vec3 hitPos=CareerClient.INSTANCE.firstTarget(c,dashDist,bladeWidth);
                        c.center=hitPos;
                        if(hasThrow)BerserkerClient.notifyBladeDashHit(c.id,hitPos);
                    }
                }else{
                    releaseEffect(c);
                }
                if(hasThrow){
                    if(e>=260){releaseEffect(c);x.cancel(true);}
                    return;
                }
                SkillBehaviors.runTimeline(x,c,e,ActionTimeline.<SkillBehaviors.Step>builder()
                    .at(300,"blade_assault_burst1",step->{
                        step.cast().finisher=true;
                        releaseEffect(step.cast());
                        if(!step.cast().hit.isEmpty()){
                            step.cast().slashes=2;
                            double blastRadius=Math.max(.5,step.cast().parameters.get("blastRadius",4));
                            step.cast().hit.clear();step.cast().hitIndex=1;
                            step.context().area(step.cast(),blastRadius,step.cast().parameters.get("finisher",6.6)*ratio,false);
                            step.context().visual(step.cast(),"FINISH");
                        }
                    })
                    .at(500,"blade_assault_burst2",step->{
                        if(step.cast().slashes>=2&&blasts>=2){
                            double blastRadius=Math.max(.5,step.cast().parameters.get("blastRadius",4));
                            step.cast().hit.clear();step.cast().hitIndex=2;
                            step.context().area(step.cast(),blastRadius,step.cast().parameters.get("finisher",6.6)*ratio,false);
                            step.context().visual(step.cast(),"FINISH");
                        }
                    })
                    .at(700,"blade_assault_end",step->step.context().cancel(true))
                    .build());
                return;
            }
            SkillBehaviors.runTimeline(x,c,e,ActionTimeline.<SkillBehaviors.Step>builder()
                .at(200,"blade_thrust",step->{
                    double bladeRange=Math.max(.5,step.cast().parameters.get("range",6));
                    double bladeWidth=Math.max(.5,step.cast().parameters.get("width",3));
                    step.cast().slashes=1;step.cast().hitIndex=0;step.cast().center=CareerClient.INSTANCE.firstTarget(step.cast(),bladeRange,bladeWidth);
                    step.context().slash(step.cast(),bladeRange,bladeWidth,3,step.cast().parameters.get("damage",2.2),0,false);
                    releaseEffect(step.cast());
                })
                .at(350,"blade_burst",step->{
                    step.cast().finisher=true;
                    double blastRadius=Math.max(.5,step.cast().parameters.get("blastRadius",4));
                    if(!step.cast().hit.isEmpty()){step.cast().hit.clear();step.cast().hitIndex=1;step.context().area(step.cast(),blastRadius,step.cast().parameters.get("finisher",6.6),false);}
                    step.context().visual(step.cast(),"FINISH");
                })
                .at(650,"blade_end",step->step.context().cancel(true))
                .build());
        });
        SkillBehaviors.register(CareerSkills.ERUPTION,(x,c,n,e,s,v)->{
            long ready=Math.round(c.parameters.get("bloodSwordWindup",.25)*1000);
            if(e<ready)return;
            PlayerPoseChannel.lock(c);
            double eruptionRange=Math.max(.5,c.parameters.get("range",7));if(!c.finisher&&land(x,c,n,e-ready,c.parameters.get("hopSeconds",.6)*1000,Math.max(.5,c.parameters.get("hopDistance",6)),Math.max(0,c.parameters.get("hopHeight",2)))){c.finisher=true;c.center=x.player().position();c.hitIndex=0;x.area(c,eruptionRange,c.parameters.get("finisher",4),false);x.visual(c,"RELEASE");releaseEffect(c);}
            if(!c.finisher)return;int eruptionHits=(int)Math.max(1,c.parameters.get("slashes",4));double eruptionGap=Math.max(50,c.parameters.get("interval",.2)*1000);long landed=(long)((n-c.landedAt)*v);int due=Math.min(eruptionHits,(int)(landed/eruptionGap)+1);while(c.slashes<due){c.hit.clear();c.hitIndex=1+c.slashes++;x.area(c,eruptionRange,c.parameters.get("damage",2),false);x.visual(c,"HIT");}if(landed>=eruptionHits*eruptionGap+100)x.cancel(true);
        });
        SkillBehaviors.register(CareerSkills.SWORD,(x,c,n,e,s,v)->{
            if(!c.terminal){x.cancel(true);return;}double total=c.parameters.get("damage",24),radius=Math.max(.5,c.parameters.get("range",7.5)),share=Math.clamp(c.parameters.get("swordFirstShare",.3),0,1);int rushes=(int)Math.max(1,c.parameters.get("swordRushes",5));if(e>=300&&!c.finisher){c.finisher=true;c.hitIndex=0;x.area(c,radius,total*share,false);x.visual(c,"RELEASE");releaseEffect(c);}int due=Math.min(rushes,Math.max(0,(int)((e-600)/300)+1));while(c.slashes<due){c.hit.clear();c.hitIndex=1+c.slashes++;x.area(c,radius,total*(1-share)/rushes,false);x.visual(c,c.slashes==rushes?"FINISH":"HIT");}if(e>=rushes*300+500)x.cancel(true);
        });
    }
    private static double crescentChargeMillis(SkillCast cast){
        return Math.max(50,cast.parameters.get("crescentChargeSeconds",1)*1000);
    }
    private static SkillSchedule agniSchedule(SkillCast cast){
        int count=(int)Math.max(1,cast.parameters.get("slashes",10));
        double interval=Math.max(10,cast.parameters.get("interval",.85/3)*1000);
        double end=Math.max(cast.parameters.get("duration",3)*1000,count*interval);
        return new SkillSchedule(count,interval,end);
    }
    private static void releaseEffect(SkillCast cast){
        if(cast.effectContinues)return;
        cast.effectContinues=true;
        PlayerPoseChannel.release(cast);
    }
    private static void waves(SkillBehaviors.Context x,SkillCast c,long elapsed,int count,double range,double width,double fallback){double waveGap=Math.max(50,c.parameters.get("interval",.2)*1000);int due=Math.min(count,(int)(elapsed/waveGap)+1);while(c.slashes<due){c.hitIndex=c.slashes++;c.controlSeconds=c.skill.id.equals(CareerSkills.ICE)?Math.max(0,c.parameters.get("controlSeconds",1.5)):0;x.wave(c.origin.add(0,.15,0),c.direction,range,width,2,c.stats,c.skill,c.parameters.get("damage",fallback),false,true);x.visual(c,"HIT");if(count>3)releaseEffect(c);}if(elapsed>=count*waveGap)x.cancel(true);}
    private static void stationary(SkillBehaviors.Context x,SkillCast c){x.player().setPos(c.origin);x.player().setDeltaMovement(Vec3.ZERO);}
    private static boolean land(SkillBehaviors.Context x,SkillCast c,long now,long elapsed,double duration,double length,double height){
        var p=x.player();
        // The authored hop ends at its starting height; from there collision and gravity must find real ground.
        var before=p.position();
        if(elapsed<duration){double u=elapsed/duration,desired=length*u,delta=Math.max(0,desired-c.moved);double y=c.origin.y+4*height*u*(1-u);p.move(MoverType.SELF,c.direction.scale(delta).add(0,y-p.getY(),0));TowerSkillCombat.moved(c,before);c.moved=desired;p.setDeltaMovement(Vec3.ZERO);return false;}
        double delta=Math.max(0,length-c.moved);p.move(MoverType.SELF,c.direction.scale(delta).add(0,-Math.max(.35,Math.min(1.5,p.getY()-c.origin.y)),0));c.moved=length;
        TowerSkillCombat.moved(c,before);
        if(!p.onGround()){p.setDeltaMovement(0,Math.min(-.6,p.getDeltaMovement().y),0);if(now-c.start>=30000)x.cancel(false);return false;}
        c.landedAt=now;p.setDeltaMovement(Vec3.ZERO);return true;
    }
}
