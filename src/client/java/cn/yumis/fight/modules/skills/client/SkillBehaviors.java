package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.SkillPresentation;
import cn.yumis.fight.modules.skills.api.SkillCast;
import cn.yumis.fight.modules.skills.api.SkillSchedule;
import cn.yumis.fight.modules.skills.api.SkillDefinition;
import cn.yumis.fight.modules.skills.api.SkillCatalog;

import cn.yumis.fight.modules.combat.api.ActionStatus;
import cn.yumis.fight.modules.combat.api.ActionTimeline;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.attributes.api.Stats;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** SkillDefinition behavior registry. Combat provides geometry and lifecycle ports; it does not select skill content. */
public final class SkillBehaviors {
    public interface Context {
        LocalPlayer player();
        void buff(String name,long expires);
        void absorption(String name,double value);
        void visual(SkillCast cast,String phase);
        void slash(SkillCast cast,double length,double width,double height,double coefficient,double knockback,boolean fan);
        void path(SkillCast cast,Vec3 from,Vec3 to,double width,double height,double coefficient,double knockback);
        void wave(Vec3 origin,Vec3 direction,double range,double width,double height,Stats stats,SkillDefinition skill,double coefficient,boolean passive,boolean ground);
        void area(SkillCast cast,double radius,double coefficient,boolean inward);
        void passive(SkillCast cast);
        boolean hit(SkillCast cast,net.minecraft.world.entity.LivingEntity target,double coefficient);
        void cancel(boolean normal);
    }
    @FunctionalInterface public interface Behavior {void tick(Context context,SkillCast cast,long now,long elapsed,long stage,double speed);}
    @FunctionalInterface public interface Starter {void start(Context context,SkillCast cast,long now);}
    public record Step(Context context,SkillCast cast){}
    public static void runTimeline(Context context,SkillCast cast,long elapsed,ActionTimeline<Step> timeline){
        cast.timelineCursor=timeline.advance(new Step(context,cast),cast.timelineCursor,elapsed,cast::running);
    }
    private static final Map<String,java.util.function.Supplier<Vec3>> PREPARATIONS=new LinkedHashMap<>();
    public static Vec3 prepare(SkillDefinition skill){var preparation=PREPARATIONS.get(skill.id);return preparation==null?null:preparation.get();}
    public static void finish(SkillCast cast){if(cast!=null)SkillController.INSTANCE.releaseStatusOwner(cast.id);}
    private static final Map<String,Starter> STARTERS=new LinkedHashMap<>();
    public static void registerStart(String id,Starter starter){if(STARTERS.putIfAbsent(id,Objects.requireNonNull(starter))!=null)throw new IllegalArgumentException("Duplicate skill start: "+id);}
    public static void start(Context context,SkillCast cast,long now){var starter=STARTERS.get(cast.skill.id);if(starter!=null)starter.start(context,cast,now);}
    private static final Map<String,Behavior> BEHAVIORS=new LinkedHashMap<>();
    static {
        PREPARATIONS.put(SkillCatalog.BLINK.id,SkillBehaviors::blink);
        registerStart(SkillCatalog.BLINK.id,(x,c,now)->{var before=x.player().position();x.player().setPos(c.center);TowerSkillCombat.moved(c,before);x.player().fallDistance=0;});
        registerStart(SkillCatalog.STORM.id,(x,c,now)->SkillController.INSTANCE.acquireStatus(c.id,ActionStatus.NO_GRAVITY));
        registerStart(SkillCatalog.OVERDRIVE.id,(x,c,now)->{x.buff("yumis_dungeon_and_fight:overdrive",now+30000);x.absorption("overdrivePower",c.parameters.get("damage",.13));});
        registerStart(SkillCatalog.GHOST.id,(x,c,now)->{
            if(!cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_INSTANT_SOUL.held(c.stats))
                PlayerPoseChannel.loop(c,c.skill.id,PlayerPoseChannel.scaled(c.parameters.get("maxChargeSeconds",3)*1000,c.speed),c.direction,true);
        });
        registerStart(SkillCatalog.DANCE.id,(x,c,now)->{
            var schedule=SkillSchedule.dance(c.parameters);
            double interval=Math.max(1,schedule.intervalMillis());
            double attacks=Math.min(schedule.durationMillis(),interval*schedule.slashes());
            double rate=PlayerPoseChannel.length(c.skill.id)*c.speed/interval;
            boolean omni=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DANCE_OMNI.held(c.stats);
            PlayerPoseChannel.loopAt(c,c.skill.id,PlayerPoseChannel.scaled(attacks,c.speed),c.direction,omni,rate,c.start);
        });
        registerStart(SkillCatalog.DRAGON.id,(x,c,now)->{
            if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAGON_WHIRLWIND.held(c.stats)){
                SwordmasterClient.spawnDragonWhirlwind(c,x.player().position(),c.direction);
            }
        });
        registerStart(SkillCatalog.RISING.id,(x,c,now)->{
            if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_RISING_DRAGON_SLAY.held(c.stats)){
                PlayerPoseChannel.play(c,"rising_slay",PlayerPoseChannel.scaled(PlayerPoseChannel.length("rising_slay"),c.speed),c.direction,false);
            }
        });
        registerStart(SkillCatalog.METEOR_RISE.id,(x,c,now)->{
            var p=x.player();
            if(p.onGround())p.setDeltaMovement(p.getDeltaMovement().x,0.38,p.getDeltaMovement().z);
            p.fallDistance=0;
        });
        registerStart(SkillCatalog.METEOR_LEAP.id,(x,c,now)->{
            var p=x.player();
            if(p.onGround())p.setDeltaMovement(p.getDeltaMovement().x,0.42,p.getDeltaMovement().z);
            p.fallDistance=0;
        });
        register(List.of(SkillCatalog.GHOST),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
                boolean instant=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_INSTANT_SOUL.held(c.stats);
                double chargeSeconds=Math.max(.05,c.parameters.get("chargeSeconds",1)),chargeSpan=Math.max(50,chargeSeconds*1000);long chargedMs=(long)chargeSpan;
                if(instant||c.release||elapsed>=(long)(c.parameters.get("maxChargeSeconds",3)*1000)){
                    boolean fullCharged=instant||elapsed>=chargedMs;
                    PlayerPoseChannel.play(c,"ghost_release",PlayerPoseChannel.scaled(PlayerPoseChannel.length("ghost_release"),c.speed),c.direction,false);
                    x.visual(c,"RELEASE");
                    double charge=instant?Math.max(1,c.parameters.get("chargeMultiplier",2)):1+Math.min(1,elapsed/chargeSpan)*(Math.max(1,c.parameters.get("chargeMultiplier",2))-1);
                    double coefficient=c.parameters.get("damage",1.6)*charge+(fullCharged?c.parameters.get("soulBonus",0):0);if(fullCharged)coefficient*=c.parameters.get("ghostFieldMultiplier",1);
                    x.slash(c,c.parameters.get("range",3.8),3.8,2.6,coefficient,.4,false);
                    if(fullCharged&&c.parameters.get("soulBonus",0)>0){
                        // 满蓄力鬼斩的噬灵冲击是追加装饰轨道；命中判定仍由鬼斩本体承载。
                        var look=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z);look=look.lengthSqr()<.01?new Vec3(0,0,1):look.normalize();
                        var soul=new SkillCast();soul.id=UUID.randomUUID().toString();soul.session=c.session;soul.generation=c.generation;soul.job=c.job;soul.dimension=c.dimension;soul.instance=c.instance;soul.skill=SkillCatalog.require(CareerSkills.SOUL_SLASH);soul.stats=c.stats;soul.parameters=c.parameters;soul.speed=c.speed;soul.start=now;
                        CareerClient.INSTANCE.soulSlashVisual(soul,p.position().add(0,1,0),look,c.stats.modifiers.getOrDefault("ownGhostField",0d)>0);
                        if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_HELL_SLASH.held(c.stats)){
                            SoulBenderClient.spawnHellSlashWave(c,look,coefficient);
                        }
                    }
                    x.cancel(true);
                }
            
        });
        register(List.of(SkillCatalog.TRIPLE,SkillCatalog.DRAGON),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
                double duration=c.parameters.get("dashSeconds",c.skill==SkillCatalog.TRIPLE?.2:.25),length=Math.max(.5,c.parameters.get("dashDistance",c.skill==SkillCatalog.TRIPLE?3.0:4.8));
                x.buff("movementProtection",now+500);
                if(stage<=duration*1000||c.moved<length){
                    if(now-c.visualTrail>=100){
                        x.visual(c,"TRAIL");
                        c.visualTrail=now;
                    }
                    Vec3 before=p.position();
                    double desired=length*Math.min(1,stage/(duration*1000));double move=Math.max(0,desired-c.moved);p.move(MoverType.SELF,c.direction.scale(move));c.moved+=move;
                    TowerSkillCombat.moved(c,before);
                    boolean dragon=c.skill==SkillCatalog.DRAGON;
                    x.slash(c,dragon?2.5:2.2,dragon?3.2:2.6,dragon?2.8:2.5,c.parameters.get("damage",.8),.4,false);
                    if(move>0&&p.position().distanceToSqr(before)<.00001&&stage>50){c.stageStart=now-(long)(duration*1000/speed);c.moved=length;}
                }
                if(stage>=duration*1000&&c.hit.size()>=0&&c.slashes<c.segment){
                    c.slashes=c.segment;
                    if(c.stats.weaponType.equals("SHORTSWORD")&&c.skill==SkillCatalog.TRIPLE)x.passive(c);
                    PlayerPoseChannel.release(c);
                }
                boolean unlimited=c.skill==SkillCatalog.TRIPLE&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_TRIPLE_INFINITE.held(c.stats);
                if(now>c.window||!unlimited&&c.segment>=c.parameters.get("slashes",3)&&stage>duration*1000)x.cancel(true);
            
        });
        register(List.of(SkillCatalog.WAVE),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
            Vec3 origin=p.position().add(0,.15,0);
            double range=c.parameters.get("range",12),damage=c.parameters.get("damage",2);
            if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.ASURA_WAVE_RING.held(c.stats)){
                int count=Math.max(1,cn.yumis.fight.modules.dungeons.api.TowerBattleCard.ASURA_WAVE_RING.count("angles"));
                for(int i=0;i<count;i++){
                    double angle=i*Math.TAU/count,cos=Math.cos(angle),sin=Math.sin(angle);
                    Vec3 dir=new Vec3(c.direction.x*cos-c.direction.z*sin,0,c.direction.x*sin+c.direction.z*cos).normalize();
                    x.wave(origin,dir,range,3.2,2.2,c.stats,c.skill,damage,false,true);
                }
            }else{
                x.wave(origin,c.direction,range,3.2,2.2,c.stats,c.skill,damage,false,true);
            }
            x.cancel(true);
        });
        register(List.of(SkillCatalog.OVERDRIVE),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
                if(elapsed>=150)x.cancel(true);
            
        });
        register(List.of(SkillCatalog.RISING),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
                PlayerPoseChannel.lock(c);
                if(now-c.visualTrail>=100){
                    x.visual(c,"TRAIL");
                    c.visualTrail=now;
                }
                x.buff("movementProtection",now+500);
                if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_RISING_DRAGON_SLAY.held(c.stats)){
                    double dashSpan=Math.max(120,c.parameters.get("dashSeconds",.3)*1000);
                    double dashDist=Math.max(7.0,c.parameters.get("dashDistance",4.5)*1.55);
                    if(elapsed<=dashSpan&&c.moved<dashDist){
                        double desired=dashDist*Math.min(1,elapsed/dashSpan);
                        double move=Math.max(0,desired-c.moved);
                        var before=p.position();
                        p.move(MoverType.SELF,c.direction.scale(move));
                        TowerSkillCombat.moved(c,before);
                        c.moved+=move;
                    }
                    double baseD=c.parameters.get("damage",1.6),baseF=c.parameters.get("finisher",2.4);
                    long t1=Math.round(dashSpan/3.0),t2=Math.round(dashSpan*2.0/3.0),tUpper=Math.round(dashSpan),tSlam=tUpper+220L,tEnd=tSlam+300L;
                    var timeline=ActionTimeline.<Step>builder()
                        .at(0L,"slay_rush_1",s->{s.cast().hit.clear();s.cast().hitIndex=0;s.context().slash(s.cast(),3.2,3.4,3.0,WeaponSkills.risingDragonSlayRushCoeff(baseD),.35,false);s.context().visual(s.cast(),"SEGMENT");})
                        .at(t1,"slay_rush_2",s->{s.cast().hit.clear();s.cast().hitIndex=1;s.context().slash(s.cast(),3.2,3.4,3.0,WeaponSkills.risingDragonSlayRushCoeff(baseD),.35,false);s.context().visual(s.cast(),"SEGMENT");})
                        .at(t2,"slay_rush_3",s->{s.cast().hit.clear();s.cast().hitIndex=2;s.context().slash(s.cast(),3.2,3.4,3.0,WeaponSkills.risingDragonSlayRushCoeff(baseD),.35,false);s.context().visual(s.cast(),"SEGMENT");})
                        .at(tUpper,"slay_upper",s->{s.cast().hit.clear();s.cast().hitIndex=3;s.context().slash(s.cast(),3.6,3.6,3.8,WeaponSkills.risingDragonSlayUpperCoeff(baseD),.55,false);s.context().visual(s.cast(),"HIT");})
                        .at(tSlam,"slay_slam",s->{
                            s.cast().finisher=true;s.cast().hit.clear();s.cast().hitIndex=4;
                            s.cast().center=s.context().player().position().add(s.cast().direction.scale(2.8));
                            s.context().area(s.cast(),5.5,WeaponSkills.risingDragonSlaySlamCoeff(baseF),false);
                            s.context().visual(s.cast(),"FINISH");
                            if(s.cast().stats.weaponType.equals("SHORTSWORD"))s.context().passive(s.cast());
                            PlayerPoseChannel.release(s.cast());
                        })
                        .at(tEnd,"slay_end",s->s.context().cancel(true))
                        .build();
                    runTimeline(x,c,elapsed,timeline);
                    return;
                }
                double risingSpan=Math.max(50,c.parameters.get("dashSeconds",.3)*1000);if(!c.finisher){double desired=Math.max(.5,c.parameters.get("dashDistance",4.5))*Math.min(1,elapsed/risingSpan);double move=Math.max(0,desired-c.moved);var before=p.position();p.move(MoverType.SELF,c.direction.scale(move));TowerSkillCombat.moved(c,before);c.moved+=move;}
                if(elapsed<risingSpan){
                    x.slash(c,2.2,2.8,2.6,c.parameters.get("damage",1.6),.5,false);
                }
                else if(!c.finisher){
                    c.finisher=true;
                    x.visual(c,"RELEASE");
                    c.hit.clear();
                    x.slash(c,3.2,3.2,3.6,c.parameters.get("finisher",2.4),.6,false);
                    PlayerPoseChannel.release(c);
                }
                if(elapsed>=risingSpan+400)x.cancel(true);
            
        });
        register(List.of(SkillCatalog.DRAW),(x,c,now,elapsed,stage,speed)->{
            c.sweepDegrees=360;
            long drawMs=(long)(Math.max(.05,c.parameters.get("drawSeconds",.4))*1000);
            boolean barrage=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAW_BARRAGE.held(c.stats);
            boolean arcWave=!barrage&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAW_ARC_WAVE.held(c.stats);
            boolean light=!barrage&&Set.of("LIGHTSABER","KATANA").contains(c.stats.weaponType)&&SkillLearning.effective(c.stats,cn.yumis.fight.modules.skills.api.client.SkillsClient.current().progress(),SkillCatalog.MASTERY)>0;
            // Ensure Lightsaber/Katana 2nd light slash always keeps >=160ms real-time separation so both stages stay distinct even at high cast speed.
            long followupAt=drawMs+Math.max(200L,Math.round(160.0*speed));
            long endAt=light?followupAt+240L:drawMs+400L;
            var timeline=ActionTimeline.<Step>builder()
                .at(drawMs,"draw_release",s->{
                    s.cast().release=true;s.cast().hitIndex=0;
                    if(barrage){
                        s.context().visual(s.cast(),"RELEASE");
                        SwordmasterClient.spawnDrawBarrage(s.cast());
                        if(s.cast().stats.weaponType.equals("SHORTSWORD"))s.context().passive(s.cast());
                        PlayerPoseChannel.release(s.cast());
                    }else{
                        s.context().slash(s.cast(),s.cast().parameters.get("range",6),6,3.6,s.cast().parameters.get("damage",1),.4,true);
                        s.context().visual(s.cast(),"RELEASE");
                        if(arcWave)SwordmasterClient.spawnDrawArcWave(s.cast(),s.cast().parameters.get("damage",1)*WeaponSkills.drawArcWaveDamageRatio());
                        if(s.cast().stats.weaponType.equals("SHORTSWORD"))s.context().passive(s.cast());
                        if(!light)PlayerPoseChannel.release(s.cast());
                    }
                })
                .at(followupAt,"draw_light_followup",s->{
                    if(!light)return;
                    s.cast().finisher=true;s.cast().hit.clear();s.cast().hitIndex=1;s.cast().elementOverride=0;s.cast().passiveDamage=true;s.cast().suppressEquipment=true;
                    Stats original=s.cast().stats;s.cast().stats=original.copy();s.cast().stats.modifiers.put("crit",0d);
                    s.context().slash(s.cast(),s.cast().parameters.get("range",6),6,3.6,s.cast().parameters.get("damage",1)*.2,0,true);
                    s.context().visual(s.cast(),"HIT");
                    s.cast().stats=original;s.cast().elementOverride=-2;s.cast().passiveDamage=false;s.cast().suppressEquipment=false;
                    PlayerPoseChannel.release(s.cast());
                })
                .at(endAt,"draw_end",s->s.context().cancel(true))
                .build();
            runTimeline(x,c,elapsed,timeline);
        });
        register(List.of(SkillCatalog.DANCE),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
                if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DANCE_OMNI.held(c.stats)){
                    Vec3 look=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z);
                    if(look.lengthSqr()>=.001)c.direction=look.normalize();
                }
                SkillSchedule schedule=SkillSchedule.dance(c.parameters);
                while(c.slashes<schedule.due(elapsed)){
                    x.visual(c,"HIT");
                    c.hit.clear();
                    x.slash(c,c.parameters.get("range",4.8),3.8,2.6,c.parameters.get("damage",.7),.04,false);
                    if(c.stats.weaponType.equals("SHORTSWORD"))x.passive(c);
                    c.slashes++;
                }
                if(elapsed>=schedule.durationMillis()){
                    x.wave(p.position().add(0,.8,0),c.direction,Math.max(.5,c.parameters.get("finisherRange",10)),4.4,2.8,c.stats,c.skill,c.parameters.get("finisher",5),false,false);
                    x.cancel(true);
                }
            
        });
        register(List.of(SkillCatalog.METEOR_RISE),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
            x.buff("movementProtection",now+500);
            p.fallDistance=0;
            double dashSpan=Math.max(80,c.parameters.get("dashSeconds",.22)*1000);
            double dashDist=Math.max(.5,c.parameters.get("dashDistance",2.4));
            if(elapsed<=dashSpan&&c.moved<dashDist){
                double desired=dashDist*Math.min(1,elapsed/dashSpan);
                double move=Math.max(0,desired-c.moved);
                var before=p.position();
                p.move(MoverType.SELF,c.direction.scale(move));
                TowerSkillCombat.moved(c,before);
                c.moved+=move;
            }
            long tFin=Math.round(dashSpan),tEnd=tFin+260L;
            var timeline=ActionTimeline.<Step>builder()
                .at(40L,"meteor_rise_hit",s->{
                    s.cast().hit.clear();s.cast().hitIndex=0;
                    s.context().slash(s.cast(),3.4,3.2,3.4,s.cast().parameters.get("damage",11.5),.35,false);
                    s.context().visual(s.cast(),"HIT");
                })
                .at(tFin,"meteor_rise_apex",s->{
                    s.cast().finisher=true;s.cast().hit.clear();s.cast().hitIndex=1;
                    s.context().slash(s.cast(),s.cast().parameters.get("range",4.2),3.4,4.2,s.cast().parameters.get("finisher",13.5),.5,false);
                    s.context().visual(s.cast(),"RELEASE");
                    if(s.cast().stats.weaponType.equals("SHORTSWORD"))s.context().passive(s.cast());
                    PlayerPoseChannel.release(s.cast());
                })
                .at(tEnd,"meteor_rise_end",s->s.context().cancel(true))
                .build();
            runTimeline(x,c,elapsed,timeline);
        });
        register(List.of(SkillCatalog.METEOR_LEAP),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
            x.buff("movementProtection",now+500);
            p.fallDistance=0;
            if(now-c.visualTrail>=80){x.visual(c,"TRAIL");c.visualTrail=now;}
            double dashSpan=Math.max(100,c.parameters.get("dashSeconds",.28)*1000);
            double dashDist=Math.max(.5,c.parameters.get("dashDistance",6.5));
            if(elapsed<=dashSpan&&c.moved<dashDist){
                double desired=dashDist*Math.min(1,elapsed/dashSpan);
                double move=Math.max(0,desired-c.moved);
                var before=p.position();
                p.move(MoverType.SELF,c.direction.scale(move));
                TowerSkillCombat.moved(c,before);
                c.moved+=move;
            }
            long tLand=Math.round(dashSpan),tEnd=tLand+280L;
            var timeline=ActionTimeline.<Step>builder()
                .at(60L,"meteor_leap_rush",s->{
                    s.cast().hit.clear();s.cast().hitIndex=0;
                    s.context().slash(s.cast(),3.2,3.0,3.0,s.cast().parameters.get("damage",9.0),.25,false);
                    s.context().visual(s.cast(),"HIT");
                })
                .at(tLand,"meteor_leap_plunge",s->{
                    s.cast().finisher=true;s.cast().hit.clear();s.cast().hitIndex=1;
                    s.cast().center=s.context().player().position().add(s.cast().direction.scale(1.6));
                    s.context().area(s.cast(),s.cast().parameters.get("range",4.4),s.cast().parameters.get("finisher",18.5),false);
                    s.context().visual(s.cast(),"RELEASE");
                    if(s.cast().stats.weaponType.equals("SHORTSWORD"))s.context().passive(s.cast());
                    PlayerPoseChannel.release(s.cast());
                })
                .at(tEnd,"meteor_leap_end",s->s.context().cancel(true))
                .build();
            runTimeline(x,c,elapsed,timeline);
        });
        register(List.of(SkillCatalog.METEOR_PIERCE),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
            x.buff("movementProtection",now+500);
            double dashSpan=Math.max(80,c.parameters.get("dashSeconds",.22)*1000);
            double dashDist=Math.max(.5,c.parameters.get("dashDistance",3.2));
            if(elapsed<=dashSpan&&c.moved<dashDist){
                double desired=dashDist*Math.min(1,elapsed/dashSpan);
                double move=Math.max(0,desired-c.moved);
                var before=p.position();
                p.move(MoverType.SELF,c.direction.scale(move));
                TowerSkillCombat.moved(c,before);
                c.moved+=move;
            }
            int total=(int)Math.max(1,c.parameters.get("slashes",3));
            long stepMs=Math.max(50L,Math.round(c.parameters.get("interval",.12)*1000));
            while(c.slashes<total&&elapsed>=c.slashes*stepMs){
                c.hit.clear();
                c.hitIndex=c.slashes;
                boolean last=c.slashes==total-1;
                x.slash(c,c.parameters.get("range",5.4),2.4,2.6,c.parameters.get("damage",8.4),last?.4:.15,false);
                x.visual(c,last?"RELEASE":"HIT");
                if(last){
                    if(c.stats.weaponType.equals("SHORTSWORD"))x.passive(c);
                    PlayerPoseChannel.release(c);
                }
                c.slashes++;
            }
            if(elapsed>=total*stepMs+180L)x.cancel(true);
        });
        register(List.of(SkillCatalog.STORM),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
            p.setPos(c.origin);
            p.setDeltaMovement(Vec3.ZERO);
            var schedule=StormChoreography.schedule(c.parameters.values());
            while(c.slashes<schedule.slashes()&&elapsed>=schedule.slashAt(c.slashes)){
                c.hit.clear();
                x.area(c,c.parameters.get("range",6.5),c.parameters.get("damage",1),true);
                c.slashes++;
            }
            if(elapsed>=schedule.finisherAt()&&!c.finisher){
                c.finisher=true;
                x.visual(c,"FINISH");
                c.hit.clear();
                x.area(c,c.parameters.get("range",6.5),c.parameters.get("finisher",8),true);
                PlayerPoseChannel.release(c);
            }
            if(elapsed>=schedule.endAt())x.cancel(true);
        });
    }
    static {CareerSkillBehaviors.install();OracleClient.install();}
    private SkillBehaviors(){}
    private static void register(List<SkillDefinition> skills,Behavior behavior){for(SkillDefinition skill:skills)register(skill.id,behavior);}
    public static void register(String id,Behavior behavior){if(BEHAVIORS.putIfAbsent(id,Objects.requireNonNull(behavior))!=null)throw new IllegalArgumentException("Duplicate skill behavior: "+id);}
    public static void advance(Context context,SkillCast cast,long now){
        var behavior=BEHAVIORS.get(cast.skill.id);
        if(behavior==null){context.cancel(true);return;}
        double speed=SkillPresentation.fixedActionTime(cast.skill)?1:cast.speed;
        behavior.tick(context,cast,now,(long)((now-cast.start)*speed),(long)((now-cast.stageStart)*speed),speed);
    }
    private static Vec3 blink(){
        var p=net.minecraft.client.Minecraft.getInstance().player;
        Vec3 origin=p.position(),direction=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z).normalize();if(direction.lengthSqr()<.001)throw new IllegalArgumentException("没有有效前进方向");
        var blinkState=cn.yumis.fight.modules.skills.api.client.SkillsClient.current();double blinkRange=Math.max(.5,SkillParameters.forCast(p.getUUID(),SkillCatalog.BLINK,Math.max(1,SkillLearning.effective(blinkState.stats(),blinkState.progress(),SkillCatalog.BLINK)),blinkState.stats().parameters).get("range",5));
        double limit=blinkRange;
        for(double height: new double[]{.1,p.getBbHeight()*.5,p.getEyeHeight()}){Vec3 start=origin.add(0,height,0);var obstruction=p.level().clip(new net.minecraft.world.level.ClipContext(start,start.add(direction.scale(blinkRange)),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));if(obstruction.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)limit=Math.min(limit,Math.max(0,start.distanceTo(obstruction.getLocation())-p.getBbWidth()*.5-.02));}
        for(double distance=limit;distance>=.25;distance-=.25){Vec3 desired=origin.add(direction.scale(distance));var floor=p.level().clip(new net.minecraft.world.level.ClipContext(desired.add(0,1,0),desired.add(0,-4.99,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));if(floor.getType()==net.minecraft.world.phys.HitResult.Type.MISS)continue;Vec3 point=floor.getLocation().add(0,.001,0);net.minecraft.core.BlockPos pos=net.minecraft.core.BlockPos.containing(point);
            if(origin.distanceToSqr(point)>blinkRange*blinkRange||!p.level().hasChunkAt(pos)||!p.level().getWorldBorder().isWithinBounds(pos))continue;net.minecraft.world.phys.AABB box=p.getBoundingBox().move(point.subtract(origin));
            if(p.level().noCollision(p,box)&&!p.level().containsAnyLiquid(box)&&p.level().getBlockState(net.minecraft.core.BlockPos.containing(point.add(0,-.02,0))).blocksMotion())return point;
        }
        throw new IllegalArgumentException("前方"+cn.yumis.fight.modules.presentation.api.client.NumberText.number(blinkRange)+"格内没有可落地位置");
    }
}
