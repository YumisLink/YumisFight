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
import java.util.function.BooleanSupplier;
import static cn.yumis.fight.modules.arkguard.application.ArkBattle.*;

/** World-entity AI and Ark unit abilities. */
final class ArkCombat {
    static final String JOSEPHINE="ark_josephine";
    private static final String PRIEST="ark_priest",KNOT="ark_knot",WEI="ark_wei",ANNA="ark_anna",KARL="ark_karl_phantom";
    private final ArkBattle b;
    private final ArkHealing healing;
    private final List<Zone> zones=new ArrayList<>();
    private final List<Wind> winds=new ArrayList<>();
    private final List<Ailment> ailments=new ArrayList<>();
    private final List<Prayer> prayers=new ArrayList<>();
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
    private static final class Prayer {
        Actor source,target;
        double amount;
        long next,end;
    }
    private static final class EnemyCast {
        Actor source,victim;String kind;Vec3 origin,point,direction;
        double radius,angle,coefficient,width,traveled;boolean magic,released;
        long release,activeUntil,until;Set<UUID> hit=new HashSet<>();
        int segments,hitsDone;long segmentInterval,nextSegmentAt;
    }
    /** Keno's in-memory run state: phase bookkeeping, reactive blink, and the post-blink follow-up. */
    private static final class Keno {
        int stage;long transitionUntil,meleeSince,followupAt;
        boolean followup,claimedImmune;double lastRevision=-1;
    }
    private final Map<Actor,Keno> keno=new HashMap<>();
    private record Strike(Actor source,double coefficient,boolean magic,int[] elements,boolean basic,Stats stats){
        Strike capture(){return new Strike(source,coefficient,magic,elements.clone(),basic,source.stats.copy());}
    }
    private record Shot(Strike strike,Actor victim,long arrival,Runnable followUp,boolean bounce){}
    ArkCombat(ArkBattle battle){b=battle;healing=new ArkHealing(battle);}
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
    void end(){zones.clear();winds.clear();ailments.clear();prayers.clear();shots.clear();enemyCasts.clear();}
    void tick(long now,double dt){
        this.now=now;
        for(var a:b.actors){
            if(a.castUntil>0){
                if(!a.alive()){a.castUntil=0;a.meteorPoint=null;a.skill.interrupt(now);}
                else if(now>=a.castUntil){
                    if(a.meteorPoint!=null)a.meteorAt=a.skill.end();
                    a.castUntil=0;
                }
            }
            if(a.meteorAt>0&&now>=a.meteorAt){meteor(a);a.meteorAt=0;a.meteorPoint=null;}
            if(a.skill.finish(now)&&a.alive()&&a.unit!=null){
                if(a.unit.id().equals(KNOT))gainKnot(a);
                if(a.airborne)land(a);
            }
        }
        for(var a:b.actors)if(a.alive())refresh(a);
        prayerTicks();
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
    static double distanceSqr(Vec3 first,Vec3 second){double x=first.x-second.x,z=first.z-second.z;return x*x+z*z;}
    double range(Actor a){
        if(a.unit==null)return 16;
        return ArkTraits.range(a.unit,a.seat.counts,a.seat.layers,b.rules)+(a.airborne?a.unit.p("rangeGain"):0);
    }
    double attackRate(Actor a){return a.unit.attackRate()*(1+a.stats.get(Stat.ATTACK_SPEED)/100);}
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
            boolean skillActive=a.skill.active(now);
            if(skillActive&&(u.id().equals("ark_baiyan")||u.id().equals(KNOT)))defense+=u.p("defense");
            if(active(seat,"guardian"))defense+=(30+2*layer(seat,"guardian"))/100d;
            s.add(Stat.PHYSICAL_DEFENSE,u.physicalDefense());s.add(Stat.MAGICAL_DEFENSE,u.magicalDefense());
            s.mut(Stat.PHYSICAL_DEFENSE,defense);s.mut(Stat.MAGICAL_DEFENSE,defense);
            if(u.id().equals(KNOT)&&a.skill.hasCast()&&!skillActive){
                s.attribute(Stat.PHYSICAL_DEFENSE).more(-u.p("defensePenalty"));
                s.attribute(Stat.MAGICAL_DEFENSE).more(-u.p("defensePenalty"));
            }
            if(ArkTraits.brute(u,seat.counts,seat.layers,b.rules))attackGain+=(30+layer(seat,"brute"))/100d;
            if(u.has("kalien")&&active(seat,"kalien"))attackGain+=(5+.5*layer(seat,"kalien"))/100d;
            if(u.has("flame_lord")&&active(seat,"flame_lord"))attackGain+=(25+layer(seat,"flame_lord"))/100d;
            if(u.has("white_church")&&active(seat,"white_church"))attackGain+=switch(tier(seat,"white_church")){case 1->.2;case 2->.5;default->1;};
            if(u.has("victoria")&&active(seat,"victoria")){haste+=a.emotion/100d;if(tier(seat,"victoria")>=2)attackGain+=a.emotion/100d;}
            if(u.id().equals("ark_vivian"))attackGain+=Math.max(layer(seat,"victoria"),layer(seat,"kalien"))*u.s("attackPerLayer",a.star());
            if(u.id().equals("ark_timir"))attackGain+=(long)((layer(seat,"victoria")+layer(seat,"chronology"))/3)*u.s("attackPerThree",a.star());
            if(u.id().equals("ark_yunyimu")){double bonus=seat.roster.coins*u.s("coinGrowth",a.star());attackGain+=bonus;haste+=bonus;}
            if(u.id().equals(PRIEST)){
                double bonus=layer(seat,"white_church")*u.s("churchGrowth",a.star());
                attackGain+=bonus;
                s.mut(Stat.HEALTH,bonus);
                b.maximum(a,s.get(Stat.HEALTH));
            }
            if(skillActive){
                attackGain+=u.p("attackGain");
                haste+=u.p("haste");
            }
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
        a.skill.recovery(s.get(Stat.COOLDOWN_RECOVERY),now);
        a.stats=s;
        double pd=s.get(Stat.PHYSICAL_DEFENSE),md=s.get(Stat.MAGICAL_DEFENSE);
        if(a.enemy&&zones.stream().anyMatch(z->z.fire&&z.tier>=2&&z.until>now&&sameBlock(z.point,a.position())))md*=.7;
        if(a.target.physicalDefenseBase!=pd||a.target.magicalDefenseBase!=md){a.target.physicalDefenseBase=pd;a.target.magicalDefenseBase=md;b.changed(a);}
        double atk=s.panel(false)/a.attackDivisor;
        if(a.target.speciesAttack!=atk){a.target.speciesAttack=atk;b.changed(a);}
    }
    private void hero(Actor a,double dt){
        if(a.unit.attackRate()<=0){stop(a);return;}
        if(controlled(a)){
            if(a.castUntil>now){a.castUntil=0;a.meteorPoint=null;a.skill.interrupt(now);}
            stop(a);return;
        }
        if(a.castUntil>now){stop(a);return;}
        if(a.unit.p("basicHeal")>0){healer(a,dt);return;}
        if(a.unit.id().equals(ANNA)&&a.skill.active(now)){
            stop(a);
            attackSteps(a,dt,()->{annaPulse(a);return true;});
            return;
        }
        Actor victim=attackTarget(a);
        if(victim==null){a.attackProgress=Math.min(1,a.attackProgress);stop(a);return;}
        if(cast(a,victim))refresh(a);
        if(a.castUntil>now){stop(a);return;}
        double reach=range(a);
        if(distanceSqr(a.position(),victim.position())>reach*reach){
            if(a.airborne)stop(a);else move(a,victim.position(),1);
            a.attackProgress=Math.min(1,a.attackProgress);
            return;
        }
        stop(a);look(a,victim.position());
        attackSteps(a,dt,()->{
            Actor current=attackTarget(a);
            if(current==null||distanceSqr(a.position(),current.position())>range(a)*range(a))return false;
            basic(a,current);
            return true;
        });
    }
    private Actor attackTarget(Actor a){
        if(!a.airborne)return selected(a);
        return inRange(a,range(a),false).stream().findFirst().orElse(null);
    }
    private void attackSteps(Actor a,double dt,BooleanSupplier action){
        a.attackProgress+=attackRate(a)*dt;
        while(a.attackProgress>=1&&a.alive()){
            if(!action.getAsBoolean()){a.attackProgress=1;break;}
            a.attackProgress-=1;
            refresh(a);
        }
    }
    private boolean cast(Actor a,Actor victim){
        var u=a.unit;if(u.p("cooldown")<=0||!a.skill.ready(now))return false;
        boolean meleeTarget=distanceSqr(a.position(),victim.position())<=range(a)*range(a);
        long duration=0;
        switch(u.id()){
            case "ark_siliu"->{if(!meleeTarget)return false;freeze(victim,victim.target.boss()?u.p("bossFreeze"):u.p("freeze"));}
            case "ark_yiluo"->{if(!meleeTarget)return false;stasis(victim,victim.target.boss()?u.p("bossControl"):u.p("control"));}
            case "ark_baiyan","ark_ruisite","ark_vivina"->duration=millis(u.p("duration"));
            case KNOT,WEI->{
                if(!meleeTarget)return false;
                duration=millis(u.p("duration"));
                if(u.id().equals(KNOT))gainKnot(a);
            }
            case ANNA->{
                if(inRange(a,range(a)+u.p("tauntRange"),false).isEmpty())return false;
                a.castUntil=now+millis(u.p("chant"));
                duration=millis(u.p("chant")+u.p("duration"));
            }
            case KARL->{
                double reach=range(a)+u.p("rangeGain");
                if(inRange(a,reach,false).isEmpty())return false;
                lift(a);
                duration=millis(u.p("duration"));
            }
            case "ark_benares"->{
                var list=inRange(a,range(a),false);if(list.isEmpty())return false;
                Vec3 direction=list.getFirst().position().subtract(a.position());direction=new Vec3(direction.x,0,direction.z).normalize();
                var wind=new Wind();wind.source=a;wind.stats=a.stats.copy();wind.point=a.position().add(0,.6,0);wind.direction=direction;winds.add(wind);
            }
            case "ark_vivian"->{
                Vec3 point=nearestPath(a);
                if(b.enemies().stream().noneMatch(e->distanceSqr(e.position(),point)<=u.p("meteorRadius")*u.p("meteorRadius")))return false;
                a.meteorPoint=point;a.castUntil=now+millis(u.p("chant"));
                duration=millis(u.p("chant")+u.p("flight"));
            }
            case "ark_snake"->{
                var targets=inRange(a,range(a),false).stream().limit((long)u.p("targets")).toList();if(targets.isEmpty())return false;
                for(var target:targets)attack(a,target,u.p("skillDamage"),false,new int[0],false,0,null);
            }
            default->{return false;}
        }
        startSkill(a,duration);
        a.entity.swing(InteractionHand.MAIN_HAND);
        return true;
    }
    private static long millis(double seconds){return (long)(seconds*1000);}
    private void startSkill(Actor a,long duration){
        a.skill.start(now,duration,a.unit.p("cooldown"));
        castEvent(a);
    }
    private void gainKnot(Actor a){
        if(active(a.seat,"victoria"))b.gain(a.seat,"victoria",(int)a.unit.s("victoriaGain",a.star()));
    }
    private void lift(Actor a){
        stop(a);
        a.airborne=true;
        a.entity.setNoGravity(true);
        a.entity.setDeltaMovement(Vec3.ZERO);
        a.entity.setPos(a.position().add(0,a.unit.p("flightHeight"),0));
    }
    private void land(Actor a){
        a.airborne=false;
        a.entity.setNoGravity(false);
        a.entity.setDeltaMovement(Vec3.ZERO);
    }
    private Actor lowestHealth(Actor source){
        return healing.friends(source).stream().filter(a->a.target.life.q<1)
            .min(Comparator.<Actor>comparingDouble(a->a.target.life.q)
                .thenComparingDouble(a->distanceSqr(source.position(),a.position())))
            .orElse(null);
    }
    private void healer(Actor a,double dt){
        var target=lowestHealth(a);
        double range=range(a);
        if(target==null||distanceSqr(a.position(),target.position())>range*range){
            if(target==null)stop(a);else move(a,target.position(),1);
            a.attackProgress=Math.min(1,a.attackProgress);
            return;
        }
        stop(a);
        look(a,target.position());
        if(a.skill.ready(now)&&a.unit.p("cooldown")>0){
            double attack=a.stats.panel(a.unit.magic());
            startSkill(a,millis(a.unit.p("duration")));
            healing.restore(a,List.of(target),attack*a.unit.p("burstHeal"),now);
            var prayer=new Prayer();
            prayer.source=a;prayer.target=target;prayer.amount=attack*a.unit.p("tickHeal");
            prayer.next=now+1000;prayer.end=a.skill.end();
            prayers.add(prayer);
            a.entity.swing(InteractionHand.MAIN_HAND);
        }
        attackSteps(a,dt,()->{
            var current=lowestHealth(a);
            if(current==null||distanceSqr(a.position(),current.position())>range*range)return false;
            look(a,current.position());
            healing.restore(a,List.of(current),a.stats.panel(a.unit.magic())*a.unit.p("basicHeal"),now);
            a.entity.swing(InteractionHand.MAIN_HAND);
            return true;
        });
    }
    private void prayerTicks(){
        var iterator=prayers.iterator();
        while(iterator.hasNext()){
            var prayer=iterator.next();
            while(prayer.target.alive()&&prayer.next<=now&&prayer.next<=prayer.end){
                healing.restore(prayer.source,List.of(prayer.target),prayer.amount,now);
                prayer.next+=1000;
            }
            if(prayer.next>prayer.end||!prayer.target.alive())iterator.remove();
        }
    }
    private void annaPulse(Actor a){
        double reach=range(a)+a.unit.p("healRange");
        var targets=healing.friends(a).stream().filter(v->distanceSqr(a.position(),v.position())<=reach*reach).toList();
        var restored=healing.restore(a,targets,a.stats.panel(a.unit.magic())*a.unit.p("pulseHeal"),now);
        int emotion=(int)a.unit.s("emotionGain",a.star());
        for(var target:restored){
            if(target!=a&&emotionEligible(target))addEmotion(target,emotion);
        }
        if(!restored.isEmpty()&&emotionEligible(a))addEmotion(a,emotion);
        a.lastFight=now;
        a.entity.swing(InteractionHand.MAIN_HAND);
    }
    private boolean emotionEligible(Actor a){return a.unit.has("victoria")&&active(a.seat,"victoria");}
    private void addEmotion(Actor a,int amount){a.emotion=Math.min(300+layer(a.seat,"victoria"),a.emotion+amount);}
    private void basic(Actor a,Actor victim){
        var u=a.unit;
        boolean josephine=u.id().equals(JOSEPHINE),powered=a.skill.active(now);
        if(!josephine||!powered)a.attacks++;
        a.lastFight=now;victim.lastFight=now;
        if(emotionEligible(a))addEmotion(a,tier(a.seat,"victoria")>=3?5:1);
        refresh(a);
        if(u.id().equals("ark_baiyan")&&powered)healing.restore(a,List.of(a),a.target.health(1)*u.p("heal"),now);
        if(u.id().equals("ark_hanasaki")){
            for(var friend:b.friends(a.seat))if(distanceSqr(a.position(),friend.position())<=range(a)*range(a))
                friend.flowerUntil=Math.max(friend.flowerUntil,now+(long)(u.p("hasteDuration")*1000));
            b.effects.cast(a,-1,now,700);
        }
        boolean enhanced=u.id().equals("ark_timir")&&a.attacks%(int)u.p("attackCycle")==0;
        if(enhanced)castEvent(a);
        int hits=enhanced?(int)u.p("enhancedHits"):u.id().equals("ark_vivian")?(int)u.p("basicHits"):josephine&&powered?(int)u.p("skillHits"):1;
        double coefficient=enhanced?u.p("enhancedDamage"):1;
        boolean ranged=!ArkEffects.projectile(a).isEmpty();
        List<Actor> targets=List.of(victim);
        if(u.id().equals("ark_vivina")&&powered)targets=inRange(a,range(a),false).stream().limit((long)u.p("targets")).toList();
        else if(powered&&(josephine||a.airborne)){
            var multiple=new ArrayList<>(targets);
            var extra=inRange(a,range(a),false).stream().filter(target->target!=victim).limit(Math.max(0,(long)u.p("skillTargets")-1)).toList();
            multiple.addAll(extra);
            targets=multiple;
        }
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
        if(josephine&&!powered&&a.attacks>=u.p("attackCycle")){
            a.attacks=0;
            startSkill(a,millis(u.p("duration")));
        }
        if(a.airborne&&active(a.seat,"chronology"))
            capped(a,"chronology","chronology",(int)u.s("chronologyGain",a.star()),(int)u.s("chronologyCap",a.star()));
        a.entity.swing(InteractionHand.MAIN_HAND);
    }
    private void attack(Actor source,Actor victim,double coefficient,boolean magic,int[] elements,boolean basic,int index,Runnable followUp){
        boolean bounce=basic&&source.unit!=null&&source.unit.id().equals(WEI)&&source.skill.active(now);
        launch(new Strike(source,coefficient,magic,elements,basic,null),victim,index,followUp,bounce,ArkEffects.center(source));
    }
    private void launch(Strike strike,Actor victim,int index,Runnable followUp,boolean bounce,Vec3 origin){
        String style=ArkEffects.projectile(strike.source());
        if(style.isEmpty()){
            impact(new Shot(strike,victim,now,null,bounce));
            if(followUp!=null)followUp.run();return;
        }
        double speed=style.equals("arrow")?28:style.equals("stone")?14:18;
        long duration=millis(Math.clamp(origin.distanceTo(ArkEffects.center(victim))/speed,.1,1));
        var captured=strike.stats()==null?strike.capture():strike;
        var elements=strike.elements();
        shots.add(new Shot(captured,victim,now+duration,followUp,bounce));
        b.effects.projectile(strike.source(),victim,origin,style,elements.length==0?-1:elements[0],now,duration,index);
    }
    private void projectiles(){
        for(var shot:List.copyOf(shots))if(now>=shot.arrival()){
            shots.remove(shot);if(eligible(shot.victim()))impact(shot);
            if(shot.followUp()!=null)shot.followUp().run();
        }
    }
    private void impact(Shot shot){
        var strike=shot.strike();
        var source=strike.source();
        var victim=shot.victim();
        double actual=damage(source,victim,strike.coefficient(),strike.magic(),strike.elements(),strike.basic(),false,strike.stats());
        if(shot.bounce()&&actual>0)bounce(strike,victim);
        if(strike.basic()&&source.unit!=null){
            if(source.unit.id().equals("ark_timina"))slow(victim,source.unit.p("slow"),source.unit.p("slowDuration"));
            if(source.unit.has("flame_lord")&&active(source.seat,"flame_lord"))fire(source,victim);
        }
    }
    private void bounce(Strike strike,Actor center){
        Actor source=strike.source();
        double range=source.unit.p("bounceRange");
        var targets=new ArrayList<>(b.enemies().stream().filter(a->a!=center&&region(source,a)
            &&distanceSqr(a.position(),center.position())<=range*range).toList());
        Collections.shuffle(targets,b.random);
        int limit=Math.min(targets.size(),(int)source.unit.p("bounceTargets"));
        Vec3 origin=ArkEffects.center(center);
        for(int i=0;i<limit;i++)launch(strike,targets.get(i),i,null,false,origin);
    }
    private int[] elements(Actor a){
        if(a.unit.id().equals("ark_aimiluo"))return new int[]{a.element};
        if(a.unit.id().equals("ark_ruisite")&&a.skill.active(now))return new int[]{3};
        if(a.unit.id().equals("ark_vivina")&&a.skill.active(now))return new int[]{0,1,2,3};
        return a.unit.elements();
    }
    private void castEvent(Actor a){
        var seat=a.seat;var u=a.unit;
        int[] elements=elements(a);b.effects.cast(a,elements.length==0?-1:elements[0],now,a.skill.end()-now);
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
    static boolean leaveField(Actor a,BlockPos position){
        if(a.phase!=0)return false;
        if(a.seat.plot().battlefield(position)){a.enteredField=true;return false;}
        if(!a.enteredField)return false;
        a.phase=1;a.target.damageImmune=true;return true;
    }
    private void enemy(Actor a,double dt){
        if(leaveField(a,a.entity.blockPosition())){enemyCasts.remove(a);b.changed(a);}
        if(controlled(a)){stop(a);enemyCasts.remove(a);return;}
        if(enemyCasts.containsKey(a)){stop(a);return;}
        var route=a.seat.plot().route();
        if(a.phase==1){
            if(ArkWar.coreArea(a.position())){a.phase=2;a.target.damageImmune=false;b.changed(a);}
            else{advance(a,route,3);return;}
        }
        if(a.phase==0&&a.routeIndex>=1){a.phase=1;a.target.damageImmune=true;b.changed(a);advance(a,route,3);return;}
        Actor victim=enemyTarget(a);
        if(victim==null){advance(a,route,1);return;}
        var coreAccess=victim.core?ArkWar.coreAccess(a.position()):null;
        Vec3 target=coreAccess==null?victim.position():coreAccess.contact();
        double reach=a.kind.equals("goblin_thrower")||a.kind.equals("goblin_shaman")?8:2.5;
        double distance=distance(a.position(),target);look(a,target);
        if(a.kind.equals("goblin_shaman")){
            if(kenoDecision(a,victim,distance,now))return;
            if(distance>reach)move(a,coreAccess==null?target:coreAccess.approach(),1,coreAccess!=null);else stop(a);
            return;
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
        if(distance>reach)move(a,coreAccess==null?target:coreAccess.approach(),1,coreAccess!=null);else stop(a);
    }
    private Actor enemyTarget(Actor enemy){
        List<Actor> friends=enemy.phase==0?b.friends(enemy.seat):b.friends();
        Actor victim=friends.stream().filter(v->!v.airborne&&distanceSqr(enemy.position(),v.position())<=16*16)
            .min(Comparator.comparingDouble(v->distanceSqr(enemy.position(),v.position()))).orElse(enemy.phase==2?b.core:null);
        var tank=friends.stream().filter(v->{
                double reach=tauntRange(v);
                return !v.airborne&&reach>0&&distanceSqr(enemy.position(),v.position())<=reach*reach;
            })
            .min(Comparator.comparingDouble(v->distanceSqr(enemy.position(),v.position()))).orElse(null);
        return tank==null?victim:tank;
    }
    private double tauntRange(Actor a){
        if(a.unit.id().equals("ark_baiyan")&&a.skill.active(now))return range(a);
        if(a.unit.id().equals(ANNA)&&a.castUntil>now)return range(a)+a.unit.p("tauntRange");
        return 0;
    }
    private boolean ready(Actor a,String kind){return a.cooldowns.getOrDefault(kind,0L)<=now;}
    private static Vec3 enemyHitPosition(Actor victim,Vec3 from){
        return victim.core?ArkWar.coreAccess(from).contact():victim.position();
    }
    private void enemyCast(Actor source,Actor victim,String kind,double radius,double angle,long windup,long active,long recovery,double coefficient,boolean magic,int cooldown){
        enemyCast(source,victim,kind,radius,angle,windup,active,recovery,coefficient,magic,cooldown,kind.toLowerCase(Locale.ROOT));
    }
    private void enemyCast(Actor source,Actor victim,String kind,double radius,double angle,long windup,long active,long recovery,double coefficient,boolean magic,int cooldown,String move){
        stop(source);source.lastFight=victim.lastFight=now;source.cooldowns.put(kind,now+cooldown*1000L);
        var cast=new EnemyCast();cast.source=source;cast.victim=victim;cast.kind=kind;cast.radius=radius;cast.angle=angle;
        cast.coefficient=coefficient;cast.magic=magic;cast.origin=source.position();
        Vec3 target=enemyHitPosition(victim,cast.origin);
        cast.point=kind.equals("CAGE")||kind.equals("LIGHTNING")?target:cast.origin;
        Vec3 delta=target.subtract(cast.origin);cast.direction=new Vec3(delta.x,0,delta.z).normalize();
        cast.width=kind.equals("WYVERN_CHARGE")?1.4:2;cast.release=now+windup;cast.activeUntil=cast.release+active;cast.until=cast.activeUntil+recovery;
        enemyCasts.put(source,cast);
        if(!kind.equals("BASIC"))b.effects.cast(source,magic?0:-1,now,windup+active);
        b.afterCommit.add(()->{((DungeonEnemy)source.entity).play(move,windup+active+recovery);source.entity.swing(InteractionHand.MAIN_HAND);});
        if(magic)b.afterCommit.add(()->b.level.sendParticles(ParticleTypes.ELECTRIC_SPARK,cast.point.x,cast.point.y+.1,cast.point.z,12,radius*.5,.1,radius*.5,.01));
    }
    /** The approved ark copy of Keno's decision table, phase thresholds, and fury economy. */
    private boolean kenoDecision(Actor a,Actor victim,double distance,long now){
        double q=a.target.life.q;int stage=q<=.33?3:q<=.67?2:1;
        var k=keno.computeIfAbsent(a,id->new Keno());
        if(k.lastRevision<0)k.lastRevision=a.target.life.revision;
        else if(a.target.life.revision>k.lastRevision){gainRage(a,(int)Math.min(10-a.target.rage,a.target.life.revision-k.lastRevision));k.lastRevision=a.target.life.revision;}
        if(now-a.lastFight>2000&&a.target.rage>0){a.target.rage--;b.changed(a);}
        if(k.stage==0)k.stage=stage;
        else if(stage>k.stage){
            k.stage=stage;
            long millis=stage==2?2200:2400;
            k.transitionUntil=now+millis;k.meleeSince=0;
            if(!a.target.damageImmune){a.target.damageImmune=true;k.claimedImmune=true;}
            b.changed(a);stop(a);
            b.afterCommit.add(()->{((DungeonEnemy)a.entity).play(stage==2?"summon_ritual":"phase3_transition",millis);});
            if(stage==2){b.spawnEnemy(a.seat,"goblin");b.spawnEnemy(a.seat,"goblin");b.spawnEnemy(a.seat,"goblin_captain");}
            return true;
        }
        if(now<k.transitionUntil){stop(a);return true;}
        if(k.claimedImmune&&now>=k.transitionUntil){k.claimedImmune=false;if(a.phase!=1){a.target.damageImmune=false;b.changed(a);}}
        if(k.followup&&now>=k.followupAt){
            k.followup=false;
            enemyCast(a,victim,"LIGHTNING",2,Math.PI,400,200,1000,1.4,true,(int)Math.round((k.stage>=2?8:10)*(k.stage>=3?.8:1)),"lightning_cast");
            return true;
        }
        if(distance<3.5){if(k.meleeSince==0)k.meleeSince=now;}
        else k.meleeSince=0;
        if(k.stage>=2&&k.meleeSince>0&&now-k.meleeSince>=3000&&ready(a,"BLINK")){blink(a,victim,k,now);return true;}
        if(stage>=3&&a.target.rage>=10&&ready(a,"ULTIMATE")){
            a.target.rage=0;b.changed(a);
            multiCast(a,victim,"ULTIMATE","ultimate_storm",5,Math.PI,2.6,true,4,800,600,600,25000);
            return true;
        }
        double scale=k.stage>=3?.8:1;
        String kind=distance<3.5?pick(a,now,"BASIC","45","FAN","30","CLAW","25")
            :distance<=8?pick(a,now,"FAN","40","RING","25","LIGHTNING","20")
            :distance<12?"LIGHTNING":null;
        if(kind==null)return false;
        switch(kind){
            case "LIGHTNING"->enemyCast(a,victim,"LIGHTNING",2,Math.PI,1200,200,1000,1.4,true,(int)Math.round((k.stage>=2?8:10)*scale),"lightning_cast");
            case "FAN"->enemyCast(a,victim,"FAN",6,Math.PI/4,800,200,800,1.2,true,(int)Math.round(6*scale),"fan_swipe");
            case "BASIC"->enemyCast(a,victim,"BASIC",3,Math.PI/3,300,200,400,1,false,(int)Math.round(2*scale),"basic_strike");
            case "RING"->enemyCast(a,victim,"RING",5.5,Math.PI,900,200,700,1.3,true,(int)Math.round(8*scale),"ring_burst");
            case "CLAW"->multiCast(a,victim,"CLAW","claw_combo",4.5,Math.PI/3,k.stage>=3?.9:.7,false,3,400,500,600,(long)Math.round(6000*scale));
            default->{}
        }
        return true;
    }
    private String pick(Actor a,long now,String... table){
        int total=0;List<String> kinds=new ArrayList<>();List<Integer> weights=new ArrayList<>();
        for(int i=0;i+1<table.length;i+=2)if(ready(a,table[i])){int weight=Integer.parseInt(table[i+1]);total+=weight;kinds.add(table[i]);weights.add(weight);}
        if(total==0)return null;
        int roll=b.random.nextInt(total);
        for(int i=0;i<kinds.size();i++){roll-=weights.get(i);if(roll<0)return kinds.get(i);}
        return kinds.getLast();
    }
    /** Reactive escape from melee pressure: reappear behind the victim, then strike with a shortened lead. */
    private void blink(Actor a,Actor victim,Keno k,long now){
        a.cooldowns.put("BLINK",now+12000L);
        stop(a);
        Vec3 look=victim.entity.getLookAngle();Vec3 flat=new Vec3(look.x,0,look.z);
        if(flat.lengthSqr()<.01)flat=new Vec3(0,0,1);
        Vec3 from=a.position(),destination=victim.position().subtract(flat.normalize().scale(6));
        a.entity.setPos(destination.x,victim.position().y,destination.z);
        k.followup=true;k.followupAt=now+400;
        b.afterCommit.add(()->{
            ((DungeonEnemy)a.entity).play("blink_step",400);
            b.level.sendParticles(ParticleTypes.ELECTRIC_SPARK,from.x,from.y+1,from.z,20,.6,1,.6,.02);
            b.level.sendParticles(ParticleTypes.ELECTRIC_SPARK,destination.x,destination.y+1,destination.z,20,.6,1,.6,.02);
        });
    }
    /** Multi-segment fury strikes: the hit check re-arms per segment; circles track the victim's live position. */
    private void multiCast(Actor source,Actor victim,String kind,String move,double radius,double angle,double coefficient,boolean magic,int segments,long windup,long segmentInterval,long recovery,long cooldown){
        stop(source);source.lastFight=victim.lastFight=now;source.cooldowns.put(kind,now+cooldown);
        var cast=new EnemyCast();
        cast.source=source;cast.victim=victim;cast.kind=kind;cast.radius=radius;cast.angle=angle;
        cast.coefficient=coefficient;cast.magic=magic;cast.origin=source.position();cast.point=cast.origin;
        Vec3 delta=victim.position().subtract(cast.origin);cast.direction=new Vec3(delta.x,0,delta.z).normalize();
        cast.release=now+windup;cast.segments=segments;cast.segmentInterval=segmentInterval;cast.nextSegmentAt=cast.release;
        cast.until=cast.release+(segments-1)*segmentInterval+200+recovery;
        enemyCasts.put(source,cast);
        b.effects.cast(source,magic?0:-1,now,windup+(segments-1)*segmentInterval+200);
        long total=cast.until-now;
        b.afterCommit.add(()->{((DungeonEnemy)source.entity).play(move,total);source.entity.swing(InteractionHand.MAIN_HAND);});
    }
    private void gainRage(Actor a,int amount){
        if(amount<=0||a.target.rage>=10)return;
        a.target.rage=Math.min(10,a.target.rage+amount);b.changed(a);
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
            if(cast.segments>1){
                while(now>=cast.nextSegmentAt&&cast.hitsDone<cast.segments){
                    cast.hit.clear();
                    if(cast.kind.equals("ULTIMATE"))cast.point=enemyHitPosition(cast.victim,cast.origin);
                    cast.hitsDone++;cast.nextSegmentAt+=cast.segmentInterval;
                }
                if(cast.hitsDone>0)resolve(cast,start,end,false);
            }
            else if(!cast.released||charge){
                resolve(cast,start,end,charge);
                cast.released=true;
            }
            if(now>=cast.until)enemyCasts.remove(source);
        }
    }
    /** One hit pass; fury strikes re-arm this per segment by clearing the hit set between passes. */
    private void resolve(EnemyCast cast,Vec3 start,Vec3 end,boolean charge){
        Actor source=cast.source;
        List<Actor> targets=new ArrayList<>(source.phase==0?b.friends(source.seat):b.friends());if(source.phase==2)targets.add(b.core);
        for(var victim:targets){
            if(cast.hit.contains(victim.entity.getUUID()))continue;
            Vec3 target=enemyHitPosition(victim,cast.origin);
            Vec3 delta=target.subtract(cast.point);double d=delta.horizontalDistance();
            boolean hit=charge?segmentDistance(target,start,end)<=cast.width*.5+.3:
                cast.kind.equals("CAGE")?Math.abs(delta.x)<=cast.radius&&Math.abs(delta.z)<=cast.radius:
                cast.kind.equals("LIGHTNING")||cast.kind.equals("DISCHARGE")||cast.kind.equals("RING")||cast.kind.equals("ULTIMATE")?d<=cast.radius:
                source.kind.equals("goblin_thrower")||!source.target.boss()&&!source.kind.contains("wyvern")?victim==cast.victim&&d<=cast.radius:
                d<=cast.radius&&(d<.01||new Vec3(delta.x,0,delta.z).normalize().dot(cast.direction)>=Math.cos(cast.angle));
            if(hit){
                cast.hit.add(victim.entity.getUUID());
                attack(source,victim,cast.coefficient,cast.magic,cast.magic?new int[]{0}:new int[0],false,0,null);
                if(cast.kind.equals("CAGE")&&!victim.core)stasis(victim,2);
                if(source.kind.equals("goblin_shaman"))gainRage(source,1);
            }
        }
        if(cast.magic)sparks(cast.point);
    }
    private void advance(Actor a,List<Vec3> route,double speed){
        int next=Math.min(route.size()-1,a.routeIndex+1);Vec3 point=route.get(next);
        // Navigation can stop off-centre for wider enemies, even at the exact target block.
        if(distanceSqr(a.position(),point)<2.25){
            a.routeIndex=next;
            if(a.routeIndex>=1&&a.phase==0){a.phase=1;a.target.damageImmune=true;b.changed(a);}
            if(next<route.size()-1)point=route.get(next+1);
        }
        move(a,point,speed,true);
    }
    private void move(Actor a,Vec3 point,double speed){
        move(a,point,speed,false);
    }
    private void move(Actor a,Vec3 point,double speed,boolean waypoint){
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
        mob.getNavigation().moveTo(point.x,point.y,point.z,waypoint?0:1,speed*(1-slow));
    }
    private void stop(Actor a){if(a.entity instanceof Mob mob)mob.getNavigation().stop();}
    private void look(Actor a,Vec3 point){if(a.entity instanceof Mob mob)mob.getLookControl().setLookAt(point.x,point.y+1,point.z);}
    void skillState(Actor actor,com.google.gson.JsonObject view,long time,boolean preparing){
        var unit=actor.unit;String mode;double progress,rate=0;
        if(!actor.deployed()){mode="备战";progress=0;}
        else if(!preparing&&actor.skill.active(time)){
            mode=actor.castUntil>time?"咏唱":"技能中";
            double duration=Math.max(1,actor.skill.duration());
            progress=(actor.skill.end()-time)/duration;
            rate=-1/duration;
            view.addProperty("skillEnd",actor.skill.end());
        }else if(unit.p("cooldown")>0){
            mode=unit.skillTrigger();
            double duration=Math.max(1,actor.skill.cooldownMillis(unit.p("cooldown")));
            long remaining=actor.skill.cooldownRemaining(time);
            progress=preparing?1:1-remaining/duration;
            if(!preparing&&remaining>0)rate=1/duration;
        }else if(unit.p("attackCycle")>0){
            mode=unit.skillTrigger();
            int cycle=(int)unit.p("attackCycle");
            progress=cycle>0?(actor.attacks%cycle)/(double)cycle:0;
        }else{mode=unit.skillTrigger();progress=0;}
        view.addProperty("skillMode",mode);view.addProperty("skill",Math.clamp(progress,0,1));view.addProperty("skillRate",rate);
    }
    private void slow(Actor a,double value,double seconds){if(a.slowUntil<=now||value>=a.slow){a.slow=value;a.slowUntil=now+(long)(seconds*1000);}}
    private void freeze(Actor a,double seconds){
        int index=ElementAccumulation.Kind.FREEZE.ordinal();a.target.life.elemental.until[index]=Math.max(a.target.life.elemental.until[index],now+(long)(seconds*1000));
        a.target.life.revision++;b.changed(a);b.afterCommit.add(()->a.entity.addEffect(new MobEffectInstance(CombatContentTypes.FREEZE,(int)Math.ceil(seconds*20),0,false,true)));
    }
    private void stasis(Actor a,double seconds){a.stasisUntil=Math.max(a.stasisUntil,now+(long)(seconds*1000));b.afterCommit.add(()->a.entity.addEffect(new MobEffectInstance(CombatContentTypes.STUN,(int)Math.ceil(seconds*20),0,false,true)));}
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
        if(victim.sanctuaryUntil>now)points*=ArkHealing.SANCTUARY_DAMAGE_TAKEN;
        if(victim.unit!=null&&victim.unit.id().equals(ANNA)&&victim.castUntil>now)points*=1-victim.unit.p("chantReduction");
        var receipt=LifeRules.apply(victim.target.life,points/victim.target.health(1),false);
        if(receipt.actual()<=0)return 0;double actual=receipt.actual()*victim.target.health(1);b.changed(victim);
        b.effects.hit(source,victim,actual,element,critical,impact,now);
        victim.lastFight=now;if(source!=null)source.lastFight=now;
        if(victim.enemy&&victim.target.life.dead&&source!=null&&source.unit!=null&&source.unit.id().equals(WEI)&&active(source.seat,"victoria"))
            capped(source,"victoriaKill","victoria",(int)source.unit.s("victoriaGain",source.star()),(int)source.unit.s("victoriaCap",source.star()));
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
        var change=ElementAccumulation.advance(victim.target.life.elemental,victim.target.life,ElementAccumulation.fractions(components,maximum),accepted,stats,victim.target.bossOrElite(),victim.enemy,now);
        victim.target.life.elemental=change.state();triggered(source,victim,change.triggered(),sourceStats);
        if(basic&&source.unit!=null&&ArkTraits.beast(source.seat.opening,b.rules).equals("ark_siliu")){
            var extra=new Stats();ElementAccumulation.addConversion(extra,-1,ElementAccumulation.Kind.FREEZE,1+winter);
            change=ElementAccumulation.advance(victim.target.life.elemental,victim.target.life,new double[]{main/maximum,0,0,0,0},accepted,extra,victim.target.bossOrElite(),victim.enemy,now);
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
            for(var a:affected)a.skill.accelerate(1000,now);
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
        double attack=(9_000+1_000*(b.wave-1))*b.towerMultiplier;
        for(int side:new int[]{-1,1}){
            Vec3 origin=Vec3.atBottomCenterOf(ArkWar.ORIGIN.offset(side*12,3,84));
            var target=b.enemies().stream().filter(a->a.phase==2&&distanceSqr(origin,a.position())<=16*16)
                .min(Comparator.comparingDouble(a->distanceSqr(a.position(),b.core.position()))).orElse(null);
            if(target==null)continue;
            fixed(b.core,target,attack,true,-1,false);
            Vec3 delta=target.position().add(0,.7,0).subtract(origin);int points=Math.max(1,(int)delta.length());
            b.afterCommit.add(()->{for(int i=0;i<=points;i++){Vec3 p=origin.add(delta.scale(i/(double)points));b.level.sendParticles(ParticleTypes.END_ROD,p.x,p.y,p.z,1,0,0,0,0);}});
        }
    }
}
