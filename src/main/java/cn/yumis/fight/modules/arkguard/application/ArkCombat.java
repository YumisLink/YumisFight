package cn.yumis.fight.modules.arkguard.application;

import cn.yumis.fight.modules.arkguard.api.*;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static cn.yumis.fight.modules.arkguard.application.ArkBattle.*;

/** World-entity AI and the fifteen content behaviours; no player outcome is adjudicated here. */
final class ArkCombat {
    private final ArkBattle b;
    private final List<Zone> zones=new ArrayList<>();
    private final List<Wind> winds=new ArrayList<>();
    private final List<Ailment> ailments=new ArrayList<>();
    private final List<Shot> shots=new ArrayList<>();
    private final Map<Actor,EnemyCast> enemyCasts=new LinkedHashMap<>();
    private long now;
    private static final class Zone {
        Actor source;Stats stats;Vec3 point;double radius,coefficient,fixed;
        long until,next;int element,tier;boolean fire;
    }
    private static final class Wind {
        Actor source;Stats stats;Vec3 point,direction;double traveled;Set<UUID> hit=new HashSet<>();
    }
    private static final class Ailment {
        Actor source,target;Stats stats;ElementAccumulation.Kind kind;long next;int remaining;
    }
    private static final class EnemyCast {
        Actor source,victim;String kind;Vec3 origin,point,direction;
        double radius,angle,coefficient,width,traveled;boolean magic,released;
        long release,activeUntil,until;Set<UUID> hit=new HashSet<>();
    }
    private record Shot(Actor source,Actor victim,double coefficient,boolean magic,int[] elements,
                        boolean basic,Stats stats,long arrival,Runnable followUp){}
    ArkCombat(ArkBattle battle){b=battle;}
    void start(long now){
        this.now=now;
        for(var seat:b.seats)for(var a:seat.allies)if(a.deployed()){
            a.element=a.unit.id().equals("ark_aimiluo")?b.random.nextInt(4):-1;
            if(a.unit.id().equals("ark_timina")&&active(seat,"victoria"))b.gain(seat,"victoria",(int)a.unit.s("victoriaStart",a.star()));
            if(a.unit.id().equals("ark_baiyan"))b.gain(seat,"white_church",(int)a.unit.s("churchStart",a.star()));
            if(a.unit.id().equals("ark_benares"))for(String trait:a.unit.traits())if(active(seat,trait))b.gain(seat,trait,(int)a.unit.s("startGain",a.star()));
        }
        for(var a:b.actors)if(a.alive())refresh(a);
    }
    void end(){zones.clear();winds.clear();ailments.clear();shots.clear();enemyCasts.clear();}
    void tick(long now,double dt){
        this.now=now;
        for(var a:b.actors){
            if(!a.alive()&&a.castUntil>0){a.castUntil=0;a.meteorPoint=null;}
            if(a.meteorAt>0&&now>=a.meteorAt){meteor(a);a.meteorAt=0;a.meteorPoint=null;}
        }
        for(var a:b.actors)if(a.alive())refresh(a);
        if(now>=b.nextCloud){cloud();b.nextCloud=now+1000;}
        for(var a:List.copyOf(b.actors))if(a.alive()&&!a.core){
            if(a.enemy)enemy(a,dt);else if(a.deployed())hero(a,dt);
        }
        enemyActions(dt);projectiles();fields(dt);statusTicks();
        while(now>=b.nextPeriodic){periodic();b.nextPeriodic+=500;}
        while(now>=b.nextTowers){towers();b.nextTowers+=1000;}
    }
    private boolean active(Seat seat,String trait){return ArkTraits.active(trait,seat.counts,b.rules);}
    private int tier(Seat seat,String trait){return ArkTraits.tier(trait,seat.counts,b.rules);}
    private int layer(Seat seat,String trait){return seat.layers.getOrDefault(trait,0);}
    private static double distance(Vec3 first,Vec3 second){return Math.sqrt(distanceSqr(first,second));}
    private static double distanceSqr(Vec3 first,Vec3 second){double x=first.x-second.x,z=first.z-second.z;return x*x+z*z;}
    private double range(Actor a){return a.unit==null?16:ArkTraits.range(a.unit,a.seat.counts,a.seat.layers,b.rules);}
    private boolean controlled(Actor a){return now<a.stasisUntil||a.target.life.elemental.active(ElementAccumulation.Kind.FREEZE,a.target.life,now)||CombatContentTypes.controlled(a.entity);}
    private boolean region(Actor source,Actor target){
        if(source.enemy||source.core)return true;
        return source.transferred?target.phase==2&&ArkWar.coreArea(target.position()):target.seat==source.seat&&target.phase==0;
    }
    private List<Actor> inRange(Actor source,double radius,boolean farthest){
        return b.enemies().stream().filter(a->region(source,a)&&distanceSqr(source.position(),a.position())<=radius*radius)
            .sorted(farthest?Comparator.<Actor>comparingDouble(a->distanceSqr(source.position(),a.position())).reversed():
                Comparator.comparingDouble(a->distanceSqr(source.position(),a.position()))).toList();
    }
    private Actor selected(Actor a){
        if(a.victim!=null){Actor old=b.actor(a.victim);if(old!=null&&old.enemy&&old.alive()&&old.phase!=1&&region(a,old)&&distanceSqr(a.position(),old.position())<=Math.max(16,range(a))*Math.max(16,range(a)))return old;}
        var list=inRange(a,16,a.unit.id().equals("ark_benares"));Actor chosen=list.isEmpty()?null:list.getFirst();a.victim=chosen==null?null:chosen.entity.getUUID();return chosen;
    }
    private void refresh(Actor a){
        Stats s=new Stats();double attack=a.attackBase;
        s.add(Stat.HEALTH,a.healthBase);s.add(Stat.PHYSICAL_ATTACK,attack);s.add(Stat.MAGICAL_ATTACK,attack);s.add(Stat.MOVEMENT_SPEED,1);s.add(Stat.COOLDOWN_RECOVERY,1);
        if(a.unit!=null){
            var u=a.unit;var seat=a.seat;double attackGain=0,haste=0,defense=0;
            if(u.id().equals("ark_baiyan"))defense+=u.p("defense");
            if(active(seat,"guardian"))defense+=(30+2*layer(seat,"guardian"))/100d;
            s.add(Stat.PHYSICAL_DEFENSE,u.physicalDefense());s.add(Stat.MAGICAL_DEFENSE,u.magicalDefense());
            s.mut(Stat.PHYSICAL_DEFENSE,defense);s.mut(Stat.MAGICAL_DEFENSE,defense);
            if(ArkTraits.brute(u,seat.counts,seat.layers,b.rules))attackGain+=(30+layer(seat,"brute"))/100d;
            if(u.has("kalien")&&active(seat,"kalien"))attackGain+=(5+.5*layer(seat,"kalien"))/100d;
            if(u.has("flame_lord")&&active(seat,"flame_lord"))attackGain+=(25+layer(seat,"flame_lord"))/100d;
            if(u.has("white_church")&&active(seat,"white_church"))attackGain+=switch(tier(seat,"white_church")){case 1->.2;case 2->.5;default->1;};
            if(u.has("victoria")&&active(seat,"victoria")){haste+=a.emotion/100d;if(tier(seat,"victoria")>=2)attackGain+=a.emotion/100d;}
            if(u.id().equals("ark_vivian"))attackGain+=Math.max(layer(seat,"victoria"),layer(seat,"kalien"))*u.s("attackPerLayer",a.star());
            if(u.id().equals("ark_timir"))attackGain+=(long)((layer(seat,"victoria")+layer(seat,"chronology"))/3)*u.s("attackPerThree",a.star());
            if(u.id().equals("ark_yunyimu")){double bonus=seat.roster.coins*u.s("coinGrowth",a.star());attackGain+=bonus;haste+=bonus;}
            if(u.id().equals("ark_ruisite")&&now<a.buffUntil){attackGain+=u.p("attackGain");haste+=u.p("haste");}
            if(u.id().equals("ark_vivina")&&now<a.buffUntil){attackGain+=u.p("attackGain");haste+=u.p("haste");}
            if(a.flowerUntil>now)haste+=.1;
            if(active(seat,"chronology")){
                double bonus=(10+layer(seat,"chronology"))/100d;
                if(u.has("chronology"))haste+=bonus;
                else if(layer(seat,"chronology")>=40&&b.friends(seat).stream().anyMatch(other->other.unit.has("chronology")
                    &&Math.abs(other.entity.blockPosition().getX()-a.entity.blockPosition().getX())<=1
                    &&Math.abs(other.entity.blockPosition().getZ()-a.entity.blockPosition().getZ())<=1))haste+=bonus;
            }
            if(b.friends(seat).stream().anyMatch(other->other.unit.id().equals("ark_yunyimu")
                &&(other==a||a.entity.getUUID().equals(other.cloudTarget))&&distanceSqr(other.position(),a.position())<=range(other)*range(other)))haste+=1;
            s.mut(Stat.PHYSICAL_ATTACK,attackGain);s.mut(Stat.MAGICAL_ATTACK,attackGain);s.add(Stat.ATTACK_SPEED,haste*100);
            if(u.id().equals("ark_siliu"))s.add(Stat.COOLDOWN_RECOVERY,(layer(seat,"arcane")+layer(seat,"winter"))*u.s("recoveryPerLayer",a.star()));
        }else{
            s.add(Stat.PHYSICAL_DEFENSE,a.physicalBase);s.add(Stat.MAGICAL_DEFENSE,a.magicalBase);
        }
        double recovery=s.get(Stat.COOLDOWN_RECOVERY);
        if(a.cooldownRecovery!=recovery&&a.nextSkill>now){a.nextSkill=now+(long)((a.nextSkill-now)*a.cooldownRecovery/recovery);}
        a.cooldownRecovery=recovery;a.stats=s;
        double pd=s.get(Stat.PHYSICAL_DEFENSE),md=s.get(Stat.MAGICAL_DEFENSE);
        if(a.enemy&&zones.stream().anyMatch(z->z.fire&&z.tier>=2&&z.until>now&&sameBlock(z.point,a.position())))md*=.7;
        if(a.target.physicalDefenseBase!=pd||a.target.magicalDefenseBase!=md){a.target.physicalDefenseBase=pd;a.target.magicalDefenseBase=md;b.changed(a);}
        double atk=s.panel(false)/a.attackDivisor;
        if(a.target.speciesAttack!=atk){a.target.speciesAttack=atk;b.changed(a);}
    }
    private void hero(Actor a,double dt){
        if(a.unit.attackRate()<=0){stop(a);return;}
        if(controlled(a)){
            if(a.castUntil>0){a.castUntil=0;a.meteorPoint=null;}
            stop(a);return;
        }
        if(a.castUntil>0){if(now<a.castUntil){stop(a);return;}a.meteorAt=a.castUntil+(long)(a.unit.p("flight")*1000);a.castUntil=0;}
        if(a.meteorAt>0&&now>=a.meteorAt){meteor(a);a.meteorAt=0;a.meteorPoint=null;}
        Actor victim=selected(a);
        if(victim==null){a.attackProgress=Math.min(1,a.attackProgress);stop(a);return;}
        if(cast(a,victim))refresh(a);
        if(a.castUntil>0){stop(a);return;}
        double reach=range(a);
        if(distanceSqr(a.position(),victim.position())>reach*reach){move(a,victim.position(),1);a.attackProgress=Math.min(1,a.attackProgress);return;}
        stop(a);look(a,victim.position());
        double rate=a.unit.attackRate()*(1+a.stats.get(Stat.ATTACK_SPEED)/100);a.attackProgress+=rate*dt;
        while(a.attackProgress>=1&&a.alive()){
            Actor current=selected(a);if(current==null||distanceSqr(a.position(),current.position())>range(a)*range(a)){a.attackProgress=1;break;}
            a.attackProgress-=1;basic(a,current);refresh(a);
        }
    }
    private boolean cast(Actor a,Actor victim){
        var u=a.unit;if(u.p("cooldown")<=0||now<a.nextSkill)return false;
        boolean meleeTarget=distanceSqr(a.position(),victim.position())<=range(a)*range(a);
        switch(u.id()){
            case "ark_siliu"->{if(!meleeTarget)return false;freeze(victim,victim.target.boss()?u.p("bossFreeze"):u.p("freeze"));}
            case "ark_yiluo"->{if(!meleeTarget)return false;stasis(victim,victim.target.boss()?u.p("bossControl"):u.p("control"));}
            case "ark_ruisite"->{if(now<a.buffUntil)return false;a.buffUntil=now+(long)(u.p("duration")*1000);}
            case "ark_vivina"->{if(now<a.buffUntil)return false;a.buffUntil=now+(long)(u.p("duration")*1000);}
            case "ark_benares"->{
                var list=inRange(a,range(a),false);if(list.isEmpty())return false;
                Vec3 direction=list.getFirst().position().subtract(a.position());direction=new Vec3(direction.x,0,direction.z).normalize();
                var wind=new Wind();wind.source=a;wind.stats=a.stats.copy();wind.point=a.position().add(0,.6,0);wind.direction=direction;winds.add(wind);
            }
            case "ark_vivian"->{
                Vec3 point=nearestPath(a);
                if(b.enemies().stream().noneMatch(e->distanceSqr(e.position(),point)<=u.p("meteorRadius")*u.p("meteorRadius")))return false;
                a.meteorPoint=point;a.castUntil=now+(long)(u.p("chant")*1000);
            }
            case "ark_snake"->{
                var targets=inRange(a,range(a),false).stream().limit((long)u.p("targets")).toList();if(targets.isEmpty())return false;
                for(var target:targets)attack(a,target,u.p("skillDamage"),false,new int[0],false,0,null);
            }
            default->{return false;}
        }
        double seconds=u.p("cooldown")/a.stats.get(Stat.COOLDOWN_RECOVERY);
        a.nextSkill=now+(long)(seconds*1000)+(u.id().equals("ark_vivina")?(long)(u.p("duration")*1000):0);
        castEvent(a);a.entity.swing(InteractionHand.MAIN_HAND);return true;
    }
    private void basic(Actor a,Actor victim){
        var u=a.unit;a.attacks++;a.lastFight=now;victim.lastFight=now;
        if(u.has("victoria")&&active(a.seat,"victoria"))a.emotion=Math.min(300+layer(a.seat,"victoria"),a.emotion+(tier(a.seat,"victoria")>=3?5:1));
        refresh(a);
        if(u.id().equals("ark_baiyan"))heal(a,u.p("heal"));
        if(u.id().equals("ark_hanasaki")){
            for(var friend:b.friends(a.seat))if(distanceSqr(a.position(),friend.position())<=range(a)*range(a))
                friend.flowerUntil=Math.max(friend.flowerUntil,now+(long)(u.p("hasteDuration")*1000));
            b.effects.cast(a,-1,now,700);
        }
        boolean enhanced=u.id().equals("ark_timir")&&a.attacks%(int)u.p("attackCycle")==0;
        if(enhanced)castEvent(a);
        int hits=enhanced?(int)u.p("enhancedHits"):u.id().equals("ark_vivian")?(int)u.p("basicHits"):1;
        double coefficient=enhanced?u.p("enhancedDamage"):1;
        boolean ranged=!ArkEffects.projectile(a).isEmpty();
        List<Actor> targets=u.id().equals("ark_vivina")&&now<a.buffUntil?inRange(a,range(a),false).stream().limit((long)u.p("targets")).toList():List.of(victim);
        Runnable splash=null;
        if(ArkTraits.brute(u,a.seat.counts,a.seat.layers,b.rules)&&layer(a.seat,"brute")>120){
            double amount=a.stats.panel(u.magic())*coefficient*hits*(10+.01*layer(a.seat,"brute"))/100;
            splash=()->{for(var target:b.enemies())if(distanceSqr(target.position(),victim.position())<=1)fixed(a,target,amount,false,-1,false);};
        }
        for(var target:targets)for(int hit=0;hit<hits&&target.alive();hit++){
            boolean last=target==targets.getLast()&&hit==hits-1;
            attack(a,target,coefficient,u.magic(),elements(a),true,hit,last&&ranged?splash:null);
        }
        if(!ranged&&splash!=null)splash.run();
        if(u.id().equals("ark_maryjanna")&&a.attacks%(int)u.p("attackCycle")==0){castEvent(a);beam(a,victim.position(),u.p("beamRadius"),u.p("beamDamage"),u.p("beamDuration")*(1+u.p("durationPerLayer")*layer(a.seat,"white_church")));}
        a.entity.swing(InteractionHand.MAIN_HAND);
    }
    private void attack(Actor source,Actor victim,double coefficient,boolean magic,int[] elements,boolean basic,int index,Runnable followUp){
        String style=ArkEffects.projectile(source);
        if(style.isEmpty()){
            impact(new Shot(source,victim,coefficient,magic,elements,basic,null,now,null));
            if(followUp!=null)followUp.run();return;
        }
        double speed=style.equals("arrow")?28:style.equals("stone")?14:18;
        long duration=(long)(Math.clamp(ArkEffects.center(source).distanceTo(ArkEffects.center(victim))/speed,.1,1)*1000);
        var shot=new Shot(source,victim,coefficient,magic,elements.clone(),basic,source.stats.copy(),now+duration,followUp);
        shots.add(shot);b.effects.projectile(source,victim,style,elements.length==0?-1:elements[0],now,duration,index);
    }
    private void projectiles(){
        for(var shot:List.copyOf(shots))if(now>=shot.arrival()){
            shots.remove(shot);if(eligible(shot.victim()))impact(shot);
            if(shot.followUp()!=null)shot.followUp().run();
        }
    }
    private void impact(Shot shot){
        var source=shot.source();var victim=shot.victim();
        damage(source,victim,shot.coefficient(),shot.magic(),shot.elements(),shot.basic(),false,shot.stats());
        if(shot.basic()&&source.unit!=null){
            if(source.unit.id().equals("ark_timina"))slow(victim,source.unit.p("slow"),source.unit.p("slowDuration"));
            if(source.unit.has("flame_lord")&&active(source.seat,"flame_lord"))fire(source,victim);
        }
    }
    private int[] elements(Actor a){
        if(a.unit.id().equals("ark_aimiluo"))return new int[]{a.element};
        if(a.unit.id().equals("ark_ruisite")&&now<a.buffUntil)return new int[]{3};
        if(a.unit.id().equals("ark_vivina")&&now<a.buffUntil)return new int[]{0,1,2,3};
        return a.unit.elements();
    }
    private void castEvent(Actor a){
        var seat=a.seat;var u=a.unit;
        int[] elements=elements(a);b.effects.cast(a,elements.length==0?-1:elements[0],now,Math.max(a.buffUntil,a.castUntil)-now);
        if(ArkTraits.beast(seat.opening,b.rules).equals("ark_ruisite"))for(String trait:u.traits())if(active(seat,trait))b.gain(seat,trait,1);
        if(active(seat,"witch_garden")&&u.has("white_church")&&active(seat,"white_church"))b.gain(seat,"white_church",2);
        if(u.id().equals("ark_yiluo"))for(String trait:u.traits())if(active(seat,trait))capped(a,"cast/"+trait,trait,(int)u.s("castGain",a.star()),(int)u.s("castCap",a.star()));
        if(u.id().equals("ark_snake"))a.emotion=Math.min(300+layer(seat,"victoria"),a.emotion+(int)u.p("emotion"));
        if(u.has("white_church")&&tier(seat,"white_church")>=3)beam(a,a.position(),1,.8,3);
    }
    private void capped(Actor a,String source,String trait,int gain,int cap){
        int used=a.gains.getOrDefault(source,0),amount=Math.min(gain,Math.max(0,cap-used));
        if(amount>0){a.gains.put(source,used+amount);b.gain(a.seat,trait,amount);}
    }
    private void enemy(Actor a,double dt){
        if(controlled(a)){stop(a);enemyCasts.remove(a);return;}
        if(enemyCasts.containsKey(a)){stop(a);return;}
        var route=a.seat.plot().route();
        if(a.phase==1){
            if(ArkWar.coreArea(a.position())){a.phase=2;a.target.damageImmune=false;b.changed(a);}
            else{advance(a,route,3);return;}
        }
        if(a.phase==0&&a.routeIndex>=1){a.phase=1;a.target.damageImmune=true;b.changed(a);advance(a,route,3);return;}
        List<Actor> friends=a.phase==0?b.friends(a.seat):b.friends();
        Actor victim=friends.stream().filter(v->distanceSqr(a.position(),v.position())<=16*16)
            .min(Comparator.comparingDouble(v->distanceSqr(a.position(),v.position()))).orElse(a.phase==2?b.core:null);
        var tank=friends.stream().filter(v->v.unit.id().equals("ark_baiyan")&&distanceSqr(a.position(),v.position())<=range(v)*range(v))
            .min(Comparator.comparingDouble(v->distanceSqr(a.position(),v.position()))).orElse(null);
        if(tank!=null)victim=tank;
        if(victim==null){advance(a,route,1);return;}
        double reach=a.kind.equals("goblin_thrower")||a.kind.equals("goblin_shaman")?8:2.5;
        double distance=distance(a.position(),victim.position());look(a,victim.position());
        if(a.kind.equals("goblin_shaman")){
            if(distance<12&&ready(a,"LIGHTNING")){enemyCast(a,victim,"LIGHTNING",2,Math.PI,1200,200,1000,1.4,true,10);return;}
            if(distance<6&&ready(a,"FAN")){enemyCast(a,victim,"FAN",6,Math.PI/4,800,200,800,1.2,true,6);return;}
            if(distance<3&&ready(a,"BASIC")){enemyCast(a,victim,"BASIC",3,Math.PI/3,300,200,400,1,false,2);return;}
        }else if(a.kind.equals("lucas")){
            if(distance<12&&ready(a,"CAGE")){enemyCast(a,victim,"CAGE",1.5,Math.PI,1000,2000,800,1.4,true,12);return;}
            if(distance<6&&ready(a,"CHARGE")){enemyCast(a,victim,"CHARGE",6,Math.PI/2,800,200,800,1.8,false,7);return;}
            if(distance<3&&ready(a,"DISCHARGE")){enemyCast(a,victim,"DISCHARGE",3,Math.PI,900,200,800,1.4,true,8);return;}
            if(distance<3&&ready(a,"BASIC")){enemyCast(a,victim,"BASIC",3,Math.PI/6,350,150,500,1,false,2);return;}
        }else if(a.kind.equals("blue_wyvern")&&distance<8&&ready(a,"WYVERN_CHARGE")){
            enemyCast(a,victim,"WYVERN_CHARGE",4.5,.15,600,450,500,1.2,false,5);return;
        }else{
            if(distance<=reach&&ready(a,"BASIC")){
                enemyCast(a,victim,"BASIC",reach,Math.PI/3,a.kind.equals("goblin_thrower")?400:250,200,400,1,false,a.kind.contains("wyvern")||a.kind.equals("goblin_thrower")?2:1);return;
            }
        }
        if(distance>reach)move(a,victim.position(),1);else stop(a);
    }
    private boolean ready(Actor a,String kind){return a.cooldowns.getOrDefault(kind,0L)<=now;}
    private void enemyCast(Actor source,Actor victim,String kind,double radius,double angle,long windup,long active,long recovery,double coefficient,boolean magic,int cooldown){
        stop(source);source.lastFight=victim.lastFight=now;source.cooldowns.put(kind,now+cooldown*1000L);
        var cast=new EnemyCast();cast.source=source;cast.victim=victim;cast.kind=kind;cast.radius=radius;cast.angle=angle;
        cast.coefficient=coefficient;cast.magic=magic;cast.origin=source.position();
        cast.point=kind.equals("CAGE")||kind.equals("LIGHTNING")?victim.position():source.position();
        Vec3 delta=victim.position().subtract(source.position());cast.direction=new Vec3(delta.x,0,delta.z).normalize();
        cast.width=kind.equals("WYVERN_CHARGE")?1.4:2;cast.release=now+windup;cast.activeUntil=cast.release+active;cast.until=cast.activeUntil+recovery;
        enemyCasts.put(source,cast);
        if(!kind.equals("BASIC"))b.effects.cast(source,magic?0:-1,now,windup+active);
        b.afterCommit.add(()->{((DungeonEnemy)source.entity).play(kind.toLowerCase(Locale.ROOT),windup+active+recovery);source.entity.swing(InteractionHand.MAIN_HAND);});
        if(magic)b.afterCommit.add(()->b.level.sendParticles(ParticleTypes.ELECTRIC_SPARK,cast.point.x,cast.point.y+.1,cast.point.z,12,radius*.5,.1,radius*.5,.01));
    }
    private void enemyActions(double dt){
        for(var cast:List.copyOf(enemyCasts.values())){
            Actor source=cast.source;
            if(!source.alive()||controlled(source)){enemyCasts.remove(source);continue;}
            if(now<cast.release)continue;
            boolean charge=cast.kind.equals("CHARGE")||cast.kind.equals("WYVERN_CHARGE");
            Vec3 start=source.position(),end=start;
            if(charge&&cast.traveled<cast.radius){
                double step=Math.min(cast.radius-cast.traveled,cast.radius*dt/Math.max(.001,(cast.activeUntil-cast.release)/1000d));
                Vec3 destination=start.add(cast.direction.scale(step));
                boolean legal=source.phase==2?ArkWar.coreArea(destination):source.seat.plot().contains(BlockPos.containing(destination));
                if(legal){source.entity.move(net.minecraft.world.entity.MoverType.SELF,cast.direction.scale(step));end=source.position();}
                cast.traveled+=step;
            }
            if(!cast.released||charge){
                List<Actor> targets=new ArrayList<>(source.phase==0?b.friends(source.seat):b.friends());if(source.phase==2)targets.add(b.core);
                for(var victim:targets){
                    if(cast.hit.contains(victim.entity.getUUID()))continue;
                    Vec3 delta=victim.position().subtract(cast.point);double d=delta.horizontalDistance();
                    boolean hit=charge?segmentDistance(victim.position(),start,end)<=cast.width*.5+.3:
                        cast.kind.equals("CAGE")?Math.abs(delta.x)<=cast.radius&&Math.abs(delta.z)<=cast.radius:
                        cast.kind.equals("LIGHTNING")||cast.kind.equals("DISCHARGE")?d<=cast.radius:
                        source.kind.equals("goblin_thrower")||!source.target.boss()&&!source.kind.contains("wyvern")?victim==cast.victim&&d<=cast.radius:
                        d<=cast.radius&&(d<.01||new Vec3(delta.x,0,delta.z).normalize().dot(cast.direction)>=Math.cos(cast.angle));
                    if(hit){cast.hit.add(victim.entity.getUUID());attack(source,victim,cast.coefficient,cast.magic,cast.magic?new int[]{0}:new int[0],false,0,null);if(cast.kind.equals("CAGE")&&!victim.core)stasis(victim,2);}
                }
                cast.released=true;if(cast.magic)sparks(cast.point);
            }
            if(now>=cast.until)enemyCasts.remove(source);
        }
    }
    private void advance(Actor a,List<Vec3> route,double speed){
        int next=Math.min(route.size()-1,a.routeIndex+1);Vec3 point=route.get(next);
        if(distanceSqr(a.position(),point)<1){
            a.routeIndex=next;
            if(a.routeIndex>=1&&a.phase==0){a.phase=1;a.target.damageImmune=true;b.changed(a);}
            if(next<route.size()-1)point=route.get(next+1);
        }
        move(a,point,speed);
    }
    private void move(Actor a,Vec3 point,double speed){
        if(!(a.entity instanceof Mob mob))return;
        if(!a.enemy&&!a.transferred&&!a.seat.plot().contains(BlockPos.containing(point))){
            Vec3 center=new Vec3(ArkWar.ORIGIN.getX()+a.seat.plot().centerX()+.5,point.y,ArkWar.ORIGIN.getZ()+a.seat.plot().centerZ()+.5);
            Vec3 delta=point.subtract(center);double length=Math.sqrt(delta.x*delta.x+delta.z*delta.z);
            if(length>27)point=new Vec3(center.x+delta.x/length*27,point.y,center.z+delta.z/length*27);
        }
        if((a.transferred||a.enemy&&a.phase==2)&&!ArkWar.coreArea(point)){
            Vec3 center=Vec3.atCenterOf(ArkWar.CORE);Vec3 delta=point.subtract(center);double length=delta.horizontalDistance();
            if(length>25)point=new Vec3(center.x+delta.x/length*25,point.y,center.z+delta.z/length*25);
        }
        double slow=a.slowUntil>now?a.slow:0;
        for(var friend:b.friends())if(friend.unit.has("geology")&&active(friend.seat,"geology")&&a.enemy&&distanceSqr(friend.position(),a.position())<=range(friend)*range(friend))slow=Math.max(slow,.3);
        mob.getNavigation().moveTo(point.x,point.y,point.z,speed*(1-slow));
    }
    private void stop(Actor a){if(a.entity instanceof Mob mob)mob.getNavigation().stop();}
    private void look(Actor a,Vec3 point){if(a.entity instanceof Mob mob)mob.getLookControl().setLookAt(point.x,point.y+1,point.z);}
    void skillState(Actor actor,com.google.gson.JsonObject view,long time,boolean preparing){
        var unit=actor.unit;String mode;double progress,rate=0;
        if(!actor.deployed()){mode="备战";progress=0;}
        else if(!preparing&&actor.castUntil>time){
            mode="咏唱";double duration=unit.p("chant")*1000;
            progress=1-(actor.castUntil-time)/Math.max(1,duration);rate=1/Math.max(1,duration);
        }else if(unit.p("cooldown")>0){
            mode=unit.skillTrigger();double duration=unit.p("cooldown")*1000/actor.cooldownRecovery;
            if(unit.id().equals("ark_vivina"))duration+=unit.p("duration")*1000;
            progress=preparing?1:1-Math.max(0,actor.nextSkill-time)/Math.max(1,duration);
            if(!preparing&&actor.nextSkill>time)rate=1/Math.max(1,duration);
        }else{mode=unit.skillTrigger();progress=0;}
        view.addProperty("skillMode",mode);view.addProperty("skill",Math.clamp(progress,0,1));view.addProperty("skillRate",rate);
    }
    private void slow(Actor a,double value,double seconds){if(a.slowUntil<=now||value>=a.slow){a.slow=value;a.slowUntil=now+(long)(seconds*1000);}}
    private void freeze(Actor a,double seconds){
        int index=ElementAccumulation.Kind.FREEZE.ordinal();a.target.life.elemental.until[index]=Math.max(a.target.life.elemental.until[index],now+(long)(seconds*1000));
        a.target.life.revision++;b.changed(a);b.afterCommit.add(()->a.entity.addEffect(new MobEffectInstance(CombatContentTypes.FREEZE,(int)Math.ceil(seconds*20),0,false,true)));
    }
    private void stasis(Actor a,double seconds){a.stasisUntil=Math.max(a.stasisUntil,now+(long)(seconds*1000));b.afterCommit.add(()->a.entity.addEffect(new MobEffectInstance(CombatContentTypes.STUN,(int)Math.ceil(seconds*20),0,false,true)));}
    private void heal(Actor a,double fraction){if(LifeRules.apply(a.target.life,fraction,true).actual()>0)b.changed(a);}
    private void sparks(Vec3 point){b.afterCommit.add(()->b.level.sendParticles(ParticleTypes.ELECTRIC_SPARK,point.x,point.y+1,point.z,16,.7,.7,.7,.02));}
    private Vec3 nearestPath(Actor a){
        List<Vec3> path=a.transferred?List.of(Vec3.atBottomCenterOf(ArkWar.ORIGIN.offset(0,0,70)),Vec3.atBottomCenterOf(ArkWar.ORIGIN.offset(0,3,92))):a.seat.plot().route().subList(0,2);
        Vec3 nearest=path.getFirst();double best=Double.MAX_VALUE;
        for(int i=1;i<path.size();i++){Vec3 start=path.get(i-1),delta=path.get(i).subtract(start);double length=delta.x*delta.x+delta.z*delta.z;double t=length==0?0:Math.clamp(((a.position().x-start.x)*delta.x+(a.position().z-start.z)*delta.z)/length,0,1);Vec3 p=start.add(delta.scale(t));double d=distanceSqr(a.position(),p);if(d<best){best=d;nearest=p;}}
        return nearest;
    }
    private boolean eligible(Actor victim){return victim.alive()&&(!victim.enemy||victim.phase!=1);}
    private boolean precise(Actor source){return source.unit!=null&&layer(source.seat,"precision")>=120&&ArkTraits.precise(source.unit,source.seat.counts,source.seat.layers,b.rules);}
    private CombatRules.Damage calculate(Actor source,Actor victim,Stats stats,double coefficient,boolean magic,int[] elements){
        Stats attack=stats.copy();
        if(precise(source))attack.modifiers.put("final",attack.modifiers.getOrDefault("final",0d)+.2*Math.floor(Math.max(0,distance(source.position(),victim.position())-1)));
        double defense=victim.target.defense(1,magic);
        if(source.unit!=null){
            if(source.unit.id().equals("ark_aimiluo"))defense=0;
            else if(magic&&source.unit.has("arcane")&&active(source.seat,"arcane")&&layer(source.seat,"arcane")>120)
                defense*=1-(40+.01*layer(source.seat,"arcane"))/100;
        }
        var hit=CombatRules.calculate(attack,defense,victim.target.resistances(),magic,elements,coefficient,false,b.random);
        double multiplier=1;
        if(magic)multiplier*=ElementAccumulation.magicTaken(victim.target.life.elemental,victim.target.life,victim.target.bossOrElite(),now);
        if(source.unit!=null){
            if(hit.element()==0&&source.unit.has("white_church")&&active(source.seat,"white_church"))
                multiplier*=(tier(source.seat,"white_church")>=3?150+2*layer(source.seat,"white_church"):125+layer(source.seat,"white_church"))/100d;
            if(source.unit.has("winter")&&active(source.seat,"winter")&&layer(source.seat,"winter")>=40
                &&victim.target.life.elemental.active(ElementAccumulation.Kind.FREEZE,victim.target.life,now))
                multiplier*=(220+5*layer(source.seat,"winter"))/100d;
        }
        return hit.scaled(multiplier);
    }
    /** direct excludes ground ticks; basic identifies one main attack segment for beast buildup. */
    private double damage(Actor source,Actor victim,double coefficient,boolean magic,int[] elements,boolean basic,boolean dot,Stats captured){
        if(!eligible(victim))return 0;
        if(!victim.enemy&&victim.unit!=null&&victim.unit.id().equals("ark_yiluo")&&b.random.nextDouble()<victim.unit.p("evasion"))return 0;
        refresh(source);refresh(victim);Stats stats=captured==null?source.stats:captured;
        var main=calculate(source,victim,stats,coefficient,magic,elements);double[] components=main.components();
        if(!dot&&source.unit!=null){
            if(source.unit.has("arcane")&&active(source.seat,"arcane")){
                int l=layer(source.seat,"arcane");double ratio=(l>120?25+.25*l:10+.1*l)/100;
                add(components,calculate(source,victim,stats,coefficient*ratio,true,new int[0]).components());
            }
            if(source.unit.has("white_church")&&b.friends(source.seat).stream().anyMatch(a->a.unit.id().equals("ark_letiya_sloth")))
                add(components,calculate(source,victim,stats,coefficient*.1,true,new int[]{0}).components());
        }
        if(victim.target.life.elemental.active(ElementAccumulation.Kind.SHOCK,victim.target.life,now)){
            double base=Arrays.stream(components).sum();
            components[1]+=ElementAccumulation.shockDamage(base,victim.target.defense(1,true),stats.get(Stat.LIGHT),victim.target.resistances()[0]);
        }
        double points=Arrays.stream(components).sum(),actual=settle(source,victim,points,main.element(),main.critical(),!dot);
        if(actual<=0)return 0;
        double accepted=actual/points;
        if(!victim.target.life.dead)accumulate(source,victim,components,main.total(),basic,accepted,stats);
        if(main.element()>=0&&source.unit!=null&&source.alive()){
            elementEvent(source,victim,main.element());
            if(source.unit.has("kalien")&&active(source.seat,"kalien")){
                boolean negative=victim.target.elementalResists[main.element()]<0;victim.target.elementalResists[main.element()]-=1;b.changed(victim);
                if(negative&&tier(source.seat,"kalien")>=2){
                    var targets=tier(source.seat,"kalien")>=3?b.enemies().stream().filter(a->distanceSqr(a.position(),victim.position())<=1).toList():List.of(victim);
                    for(var target:targets)derived(source,target,stats,coefficient*.3,magic,new int[]{main.element()});
                }
            }
        }
        return actual;
    }
    private static void add(double[] target,double[] values){for(int i=0;i<target.length;i++)target[i]+=values[i];}
    private double derived(Actor source,Actor victim,Stats stats,double coefficient,boolean magic,int[] elements){
        if(!eligible(victim))return 0;
        var hit=calculate(source,victim,stats,coefficient,magic,elements);
        return settle(source,victim,hit.total(),hit.element(),hit.critical(),false);
    }
    private double fixed(Actor source,Actor victim,double points,boolean magic,int element,boolean pure){
        return fixed(source,victim,points,magic,element,pure,true);
    }
    private double fixed(Actor source,Actor victim,double points,boolean magic,int element,boolean pure,boolean impact){
        if(!eligible(victim)||points<=0)return 0;
        if(pure)return settle(source,victim,points,element,false,impact);
        var stats=new Stats();stats.add(Stat.PHYSICAL_ATTACK,points);stats.add(Stat.MAGICAL_ATTACK,points);
        var hit=CombatRules.calculate(stats,victim.target.defense(1,magic),victim.target.resistances(),magic,element<0?new int[0]:new int[]{element},1,false,b.random);
        return settle(source,victim,hit.total(),hit.element(),hit.critical(),impact);
    }
    private double settle(Actor source,Actor victim,double points,int element,boolean critical,boolean impact){
        if(points<=0||!Double.isFinite(points)||!eligible(victim))return 0;
        var receipt=LifeRules.apply(victim.target.life,points/victim.target.health(1),false);
        if(receipt.actual()<=0)return 0;double actual=receipt.actual()*victim.target.health(1);b.changed(victim);
        b.effects.hit(source,victim,actual,element,critical,impact,now);
        victim.lastFight=now;if(source!=null)source.lastFight=now;
        if(source!=null&&source.enemy){
            if(victim.core&&b.seats.stream().anyMatch(s->tier(s,"longevity")>=3))fixed(victim,source,actual,false,-1,true);
            else if(victim.unit!=null&&tier(victim.seat,"guardian")>=2)fixed(victim,source,actual,true,-1,false);
        }
        return actual;
    }
    private void elementEvent(Actor source,Actor victim,int element){
        var u=source.unit;
        if(u.id().equals("ark_ruisite"))capped(source,"kalien","kalien",(int)u.s("kalienGain",source.star()),(int)u.s("kalienCap",source.star()));
        if(u.id().equals("ark_vivina"))for(String trait:List.of("victoria","kalien"))if(active(source.seat,trait))
            capped(source,"element/"+trait,trait,(int)u.s("elementGain",source.star()),(int)u.s("elementCap",source.star()));
    }
    private void accumulate(Actor source,Actor victim,double[] components,double main,boolean basic,double accepted,Stats sourceStats){
        double maximum=victim.target.health(1);var stats=new Stats();
        double winter=source.unit!=null&&source.unit.has("winter")&&active(source.seat,"winter")?(100+5*layer(source.seat,"winter"))/100d:0;
        if(winter>0)ElementAccumulation.addConversion(stats,2,ElementAccumulation.Kind.FREEZE,winter);
        var change=ElementAccumulation.advance(victim.target.life.elemental,victim.target.life,ElementAccumulation.fractions(components,maximum),accepted,stats,victim.target.bossOrElite(),now);
        victim.target.life.elemental=change.state();triggered(source,victim,change.triggered(),sourceStats);
        if(basic&&source.unit!=null&&ArkTraits.beast(source.seat.opening,b.rules).equals("ark_siliu")){
            var extra=new Stats();ElementAccumulation.addConversion(extra,-1,ElementAccumulation.Kind.FREEZE,1+winter);
            change=ElementAccumulation.advance(victim.target.life.elemental,victim.target.life,new double[]{main/maximum,0,0,0,0},accepted,extra,victim.target.bossOrElite(),now);
            victim.target.life.elemental=change.state();triggered(source,victim,change.triggered(),sourceStats);
        }
        victim.target.life.revision++;b.changed(victim);
    }
    private void triggered(Actor source,Actor victim,List<ElementAccumulation.Kind> kinds,Stats sourceStats){
        for(var kind:kinds){
            long remaining=victim.target.life.elemental.until[kind.ordinal()]-now;
            b.afterCommit.add(()->victim.entity.addEffect(new MobEffectInstance(CombatContentTypes.elementalEffect(kind),(int)Math.max(1,(remaining+49)/50),0,false,true)));
            if(kind.damaging()){
                var ailment=new Ailment();ailment.source=source;ailment.target=victim;ailment.stats=sourceStats.copy();ailment.kind=kind;
                ailment.next=now+(kind==ElementAccumulation.Kind.IGNITE?0:1000);ailment.remaining=kind.ticks();ailments.add(ailment);
            }
        }
    }
    private void statusTicks(){
        for(var effect:List.copyOf(ailments)){
            var victim=effect.target;
            if(!victim.alive()||effect.remaining<=0){ailments.remove(effect);continue;}
            if(now<effect.next)continue;effect.remaining--;effect.next+=1000;
            if(!eligible(victim))continue;
            boolean strong=victim.target.bossOrElite();
            double fraction=effect.kind==ElementAccumulation.Kind.EROSION?ElementAccumulation.erosionFraction(strong):
                ElementAccumulation.igniteFraction(strong,victim.target.defense(1,true),effect.stats.get(Stat.FIRE),victim.target.resistances()[1],
                    ElementAccumulation.magicTaken(victim.target.life.elemental,victim.target.life,strong,now));
            double actual=settle(effect.source,victim,fraction*victim.target.health(1),effect.kind==ElementAccumulation.Kind.IGNITE?1:3,false,false);
            if(actual>0&&effect.kind==ElementAccumulation.Kind.IGNITE&&effect.source.unit!=null&&effect.source.alive())elementEvent(effect.source,victim,1);
        }
    }
    private void beam(Actor source,Vec3 point,double radius,double coefficient,double seconds){
        var zone=new Zone();zone.source=source;refresh(source);zone.stats=source.stats.copy();zone.point=point;zone.radius=radius;
        zone.coefficient=coefficient*.5;zone.element=0;zone.until=now+(long)(seconds*1000);zone.next=now+500;zones.add(zone);
    }
    private void fire(Actor source,Actor target){
        var point=new Vec3(Math.floor(target.position().x)+.5,target.position().y,Math.floor(target.position().z)+.5);
        int tier=tier(source.seat,"flame_lord"),l=layer(source.seat,"flame_lord");
        double fixed=tier>=3?100000+500d*l:tier==2?50000+250d*l:30000+200d*l;
        var existing=zones.stream().filter(z->z.fire&&sameBlock(z.point,point)).findFirst().orElse(null);
        if(existing!=null){existing.until=Math.max(existing.until,now+3000);if(existing.fixed>=fixed)return;zones.remove(existing);}
        var zone=new Zone();zone.source=source;zone.stats=source.stats.copy();zone.point=point;zone.radius=.71;zone.fixed=fixed;
        zone.fire=true;zone.element=1;zone.tier=tier;zone.until=now+3000;zone.next=now+500;zones.add(zone);
    }
    private static boolean sameBlock(Vec3 first,Vec3 second){return Math.floor(first.x)==Math.floor(second.x)&&Math.floor(first.z)==Math.floor(second.z);}
    private void fields(double dt){
        for(var zone:List.copyOf(zones)){
            while(zone.next<=now&&zone.next<=zone.until){
                zone.next+=500;
                for(var target:b.enemies())if(zone.fire?sameBlock(zone.point,target.position()):distanceSqr(zone.point,target.position())<=zone.radius*zone.radius){
                    if(zone.fire)fixed(zone.source,target,zone.fixed,true,1,zone.tier>=3,false);
                    else damage(zone.source,target,zone.coefficient,true,new int[]{0},false,true,zone.stats);
                }
                Vec3 point=zone.point;
                b.afterCommit.add(()->b.level.sendParticles(zone.fire?ParticleTypes.FLAME:ParticleTypes.END_ROD,point.x,point.y+.6,point.z,zone.fire?5:12,zone.radius,.6,zone.radius,.01));
            }
            if(now>=zone.until)zones.remove(zone);
        }
        for(var wind:List.copyOf(winds)){
            double step=Math.min(12*dt,wind.source.unit.p("windLength")-wind.traveled);Vec3 end=wind.point.add(wind.direction.scale(step));
            for(var target:b.enemies())if(!wind.hit.contains(target.entity.getUUID())&&segmentDistance(target.position(),wind.point,end)<=wind.source.unit.p("windHalfWidth")){
                wind.hit.add(target.entity.getUUID());damage(wind.source,target,wind.source.unit.p("windDamage"),true,new int[0],false,false,wind.stats);
            }
            Vec3 point=end;b.afterCommit.add(()->b.level.sendParticles(ParticleTypes.CLOUD,point.x,point.y,point.z,6,.7,.3,.7,.02));
            wind.point=end;wind.traveled+=step;if(wind.traveled>=wind.source.unit.p("windLength"))winds.remove(wind);
        }
    }
    private static double segmentDistance(Vec3 point,Vec3 start,Vec3 end){Vec3 delta=end.subtract(start);double length=delta.x*delta.x+delta.z*delta.z;double t=length==0?0:Math.clamp(((point.x-start.x)*delta.x+(point.z-start.z)*delta.z)/length,0,1);return distance(point,start.add(delta.scale(t)));}
    private void meteor(Actor source){
        if(source.meteorPoint==null)return;Vec3 point=source.meteorPoint;refresh(source);Stats snapshot=source.stats.copy();
        for(var target:b.enemies())if(distanceSqr(point,target.position())<=source.unit.p("meteorRadius")*source.unit.p("meteorRadius"))
            damage(source,target,source.unit.p("meteorDamage"),true,new int[]{1},false,false,snapshot);
        b.afterCommit.add(()->{b.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,point.x,point.y+1,point.z,1,0,0,0,0);b.level.sendParticles(ParticleTypes.FLAME,point.x,point.y+.3,point.z,100,7,.5,7,.05);});
    }
    private void cloud(){
        for(var seat:b.seats){
            var affected=new HashSet<Actor>();
            for(var cloud:b.friends(seat))if(cloud.unit.id().equals("ark_yunyimu")){
                Actor current=cloud.cloudTarget==null?null:b.actor(cloud.cloudTarget);
                Actor chosen=b.friends(seat).stream().filter(a->a!=cloud&&distanceSqr(a.position(),cloud.position())<=range(cloud)*range(cloud))
                    .max(Comparator.<Actor>comparingDouble(a->a.stats.panel(false)).thenComparingInt(a->a==current?1:0)).orElse(null);
                cloud.cloudTarget=chosen==null?null:chosen.entity.getUUID();affected.add(cloud);if(chosen!=null)affected.add(chosen);
            }
            for(var a:affected)if(a.nextSkill>now&&(!a.unit.id().equals("ark_vivina")||now>=a.buffUntil))a.nextSkill=Math.max(now,a.nextSkill-1000);
        }
    }
    private void periodic(){
        for(var a:b.friends())if(a.unit.has("victoria")&&active(a.seat,"victoria")&&now-a.lastFight>=CombatSettings.DEFAULT.delayMillis()){
            String key="emotionDecay";int halves=a.gains.getOrDefault(key,0)+1;a.gains.put(key,halves);
            if(halves%2==0)a.emotion=Math.max(0,a.emotion-(tier(a.seat,"victoria")>=2?2:5));
        }
        for(var target:b.enemies())
            if(b.friends().stream().anyMatch(a->active(a.seat,"seven_stars")&&region(a,target)&&distanceSqr(a.position(),target.position())<=range(a)*range(a)))
                fixed(null,target,target.target.health(1)*.035,false,-1,true,false);
    }
    private void towers(){
        for(int side:new int[]{-1,1}){
            Vec3 origin=Vec3.atBottomCenterOf(ArkWar.ORIGIN.offset(side*12,3,84));
            var target=b.enemies().stream().filter(a->a.phase==2&&distanceSqr(origin,a.position())<=16*16)
                .min(Comparator.comparingDouble(a->distanceSqr(a.position(),b.core.position()))).orElse(null);
            if(target==null)continue;
            fixed(b.core,target,3000*b.towerMultiplier,true,-1,false);
            Vec3 delta=target.position().add(0,.7,0).subtract(origin);int points=Math.max(1,(int)delta.length());
            b.afterCommit.add(()->{for(int i=0;i<=points;i++){Vec3 p=origin.add(delta.scale(i/(double)points));b.level.sendParticles(ParticleTypes.END_ROD,p.x,p.y,p.z,1,0,0,0,0);}});
        }
    }
}
