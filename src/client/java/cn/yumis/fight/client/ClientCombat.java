package cn.yumis.fight.client;
import cn.yumis.fight.FightMod;
import cn.yumis.fight.api.SkillParameters;
import cn.yumis.fight.game.*;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;
import static cn.yumis.fight.rules.Models.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
/** All combat arithmetic and geometry live on the acting client. Server receipts own shared q. */
public final class ClientCombat {
    public static final ClientCombat INSTANCE=new ClientCombat();
    private final ClientState state=ClientState.INSTANCE;
    private final Random random=new Random();
    private final List<Wave> waves=new ArrayList<>();
    private final Map<String,JsonObject> bosses=new HashMap<>();
    private final Set<String> bossHit=new HashSet<>(),incomingRoots=new HashSet<>();
    private final Map<String,Long> environment=new HashMap<>();
    private final Map<String,Bleed> bleeds=new HashMap<>();
    private final ArrayDeque<Runnable> deferred=new ArrayDeque<>();
    public Cast cast;
    private final LinkedHashMap<String,Set<String>> cageHits=new LinkedHashMap<>();
    private long lastAttack,lastTick,lastRegen;
    private double regen;
    private String currentWeapon="";
    private record Bleed(JsonObject state,long next,int index){
    }
    public static final class Cast {
        public Skill skill;
        SkillParameters.Snapshot parameters;
        Stats stats;
        String weapon,id,dimension;
        public Vec3 origin,direction,center;
        long start,stageStart,window;
        int slot,segment=1,slashes,visualSequence=-1;
        long visualTrail;
        boolean charging,release,finisher;
        Set<UUID> hit=new HashSet<>();
    }
    private static final class Wave {
        Vec3 point,direction,origin;
        long started;
        int sequence=-1;
        double remaining,width,height,coefficient;
        Stats stats;
        public Skill skill;
        String id;
        boolean passive,ground;
        Set<UUID> hit=new HashSet<>();
    }
    private ClientCombat(){
    }
    public boolean validWeapon(){
        var p=Minecraft.getInstance().player;
        Gear g=p==null?null:GameItems.gear(p.getMainHandItem());
        return state.ready&&g!=null&&g.slot==Slot.WEAPON&&g.valid(state.progress);
    }
    public boolean locked(){
        return cast!=null;
    }
    public boolean invulnerable(){
        return cast!=null&&cast.skill==Skill.STORM;
    }
    public void combat(){
        state.resources.combatUntil=ClientState.now()+10000;
    }
    public Stats snapshot(){
        return SkillPreview.currentStats(state.stats,state.resources,ClientState.now());
    }
    public boolean press(int slot){
        if(!validWeapon())return false;
        var p=Minecraft.getInstance().player;
        long now=ClientState.now();
        if(p.isDeadOrDying()||p.hasEffect(FightMod.STUN))return true;
        if(cast!=null){
            if(cast.slot%8==slot%8&&(cast.skill==Skill.TRIPLE||cast.skill==Skill.DRAGON)&&now>=cast.stageStart+(cast.skill==Skill.TRIPLE?200:250)&&now<=cast.window&&cast.segment<cast.parameters.get("slashes",3)){
                cast.segment++;
                cast.stageStart=now;
                cast.direction=horizontal(p.getLookAngle());
                cast.hit.clear();
                cast.window=now+(cast.skill==Skill.TRIPLE?1200:1050);
                visual(cast,"SEGMENT");
            }
            return true;
        }
        String binding=state.progress.slots[slot];
        if(binding.isEmpty())return true;
        Skill skill=Skill.valueOf(binding);
        int rank=state.stats.effective(state.progress,skill);
        if(rank<=0||skill.passive())return true;
        if(state.resources.cooldowns.getOrDefault(skill.name(),0L)>now){
            state.message("技能冷却中");
            return true;
        }
        int mp=skill.mp(rank);
        if(!state.resources.training&&state.resources.mp<mp){
            state.message("MP不足");
            return true;
        }
        Stats stats=snapshot();
        double cooldown=SkillPreview.cooldown(skill,stats);
        if(!state.resources.training){
            state.resources.mp-=mp;
            state.resources.cooldowns.put(skill.name(),now+(long)(cooldown*1000));
        }
        combat();
        Cast c=new Cast();
        c.skill=skill;
        c.parameters=SkillParameters.forCast(p.getUUID(),skill,rank,stats.parameters);
        c.stats=stats;
        c.weapon=stats.weaponInstance;
        c.slot=slot;
        c.start=c.stageStart=now;
        c.id=UUID.randomUUID().toString();
        c.origin=p.position();
        c.direction=horizontal(p.getLookAngle());
        c.center=c.origin.add(c.direction.scale(3));
        c.dimension=p.level().dimension().identifier().toString();
        c.charging=skill==Skill.GHOST;
        c.window=now+(skill==Skill.TRIPLE?1200:skill==Skill.DRAGON?1050:0);
        cast=c;
        if(skill==Skill.OVERDRIVE){
            state.resources.buffs.put("OVERDRIVE",now+30000);
            state.resources.absorption.put("overdrivePower",.15*Math.pow(1.12,rank-1));
        }
        visual(c,"START");
        if(skill==Skill.TRIPLE||skill==Skill.DRAGON)visual(c,"SEGMENT");
        state.upload();
        return true;
    }
    public void release(int baseSlot){
        if(cast!=null&&cast.charging&&cast.slot%8==baseSlot)cast.release=true;
    }
    public void attack(){
        var mc=Minecraft.getInstance();
        if(!validWeapon()||cast!=null||ClientState.now()-lastAttack<650)return;
        lastAttack=ClientState.now();
        combat();
        var p=mc.player;
        if(mc.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof CageEntity cage){
            JsonObject j=new JsonObject();
            j.addProperty("cage",cage.root());
            j.addProperty("operation",UUID.randomUUID().toString());
            state.send("cageHit",j);
            return;
        }
        Stats stats=snapshot();
        Vec3 from=p.getEyePosition(),end=from.add(p.getLookAngle().scale(3));
        LivingEntity closest=null;
        double distance=Double.MAX_VALUE;
        for(LivingEntity e:enemies(new AABB(from,end).inflate(1))){
            Optional<Vec3> intersect=e.getBoundingBox().inflate(.2).clip(from,end);
            if(intersect.isPresent()&&visible(from,intersect.get())&&from.distanceToSqr(intersect.get())<distance){
                closest=e;
                distance=from.distanceToSqr(intersect.get());
            }
        }
        if(closest!=null){
            hit(closest,stats,null,1,true,false,.4,null);
            if(stats.weaponType.equals("BLUNT")&&stats.effective(state.progress,Skill.MASTERY)>0){
                Vec3 center=closest.position().add(0,1,0);
                for(LivingEntity e:enemies(closest.getBoundingBox().inflate(2)))if(e!=closest&&e.position().distanceToSqr(closest.position())<=4&&visible(center,e.getBoundingBox().getCenter()))hit(e,stats,null,1,true,false,.4,null);
            }
        }
        JsonObject visual=new JsonObject();
        visual.addProperty("caster",p.getStringUUID());
        visual.addProperty("duration",650);
        visual.addProperty("session",state.session);
        visual.addProperty("generation",state.own.generation);
        visual.addProperty("id",UUID.randomUUID().toString());
        visual.addProperty("kind","BASIC");
        visual.addProperty("phase","START");
        visual.addProperty("seed",random.nextLong());
        visual.addProperty("startedAt",state.serverNow());
        visual.addProperty("x",p.getX());
        visual.addProperty("y",p.getY());
        visual.addProperty("z",p.getZ());
        visual.addProperty("dx",p.getLookAngle().x);
        visual.addProperty("dy",p.getLookAngle().y);
        visual.addProperty("dz",p.getLookAngle().z);
        Effects.receive(visual);
        state.send("visual",visual);
        if(stats.weaponType.equals("SHORTSWORD")&&stats.effective(state.progress,Skill.MASTERY)>0)wave(p.position().add(0,.8,0),horizontal(p.getLookAngle()),8,3,2,stats,null,.2*mastery(stats),true,false);
    }
    public void tick(){
        var mc=Minecraft.getInstance();
        if(!state.ready||mc.player==null||mc.level==null)return;
        long now=ClientState.now();
        if(mc.isPaused()){
            lastTick=now;
            return;
        }
        double dt=lastTick==0?.05:Math.min(.25,(now-lastTick)/1000d);
        lastTick=now;
        while(!deferred.isEmpty())deferred.removeFirst().run();
        String weapon=validWeapon()?GameItems.gear(mc.player.getMainHandItem()).instance.toString():"";
        if(!weapon.equals(currentWeapon)){
            currentWeapon=weapon;
            if(!weapon.isEmpty())Effects.spark(mc.player.position().add(0,1,0),0xffedab);
        }
        if(cast!=null&&(!validWeapon()||!cast.weapon.equals(weapon)||mc.player.isDeadOrDying()||mc.player.hasEffect(FightMod.STUN)||!cast.dimension.equals(mc.level.dimension().identifier().toString())))cancel(false);
        if(cast!=null)advance(now,dt);
        for(Wave w:new ArrayList<>(waves))advance(w);
        boolean out=now>=state.resources.combatUntil;
        if(!state.own.dead){
            state.resources.mp=Math.min(state.stats.get(Stat.MP),state.resources.mp+(1+.04*state.stats.get(Stat.SPIRIT))*(out?3:1)*dt);
            if(out){
                state.resources.shield=Math.min(state.stats.get(Stat.SHIELD),state.resources.shield+.2*state.stats.get(Stat.SHIELD)*dt);
                if(state.predicted(state.own)<1){
                    regen+=.02*dt;
                    if(now-lastRegen>=100){
                        lastRegen=now;
                        state.report(state.own,regen,true,state.stats.get(Stat.HEALTH),0xffffff,null);
                        regen=0;
                    }
                }
                else regen=0;
            }
        }
        state.resources.buffs.entrySet().removeIf(e->e.getValue()<now);
        state.resources.absorptionUntil.entrySet().removeIf(e->{
            if(e.getValue()<now){
                state.resources.absorption.remove(e.getKey());return true;
            }
            return false;
        }
        );
        tickBoss(now);
        tickBleed(now);
        Effects.tick();
    }
    private void advance(long now,double dt){
        Cast c=cast;
        var p=Minecraft.getInstance().player;
        long elapsed=now-c.start,stage=now-c.stageStart;
        switch(c.skill){
            case GHOST->{
                if(c.release||elapsed>=3000){
                    visual(c,"RELEASE");
                    double charge=1+Math.min(1,elapsed/1000d);
                    slash(c,c.parameters.get("range",3),3,2,c.parameters.get("damage",1.6)*charge,.4,false);
                    cancel(true);
                }
            }
            case TRIPLE,DRAGON->{
                double duration=c.skill==Skill.TRIPLE?.2:.25,length=c.skill==Skill.TRIPLE?2.5:4;
                state.resources.buffs.put("movementProtection",now+500);
                if(stage<=duration*1000){
                    if(now-c.visualTrail>=100){
                        visual(c,"TRAIL");
                        c.visualTrail=now;
                    }
                    Vec3 before=p.position();
                    p.move(MoverType.SELF,c.direction.scale(length*dt/duration));
                    slash(c,1.5,2,2,c.parameters.get("damage",.8),.4,false);
                    if(p.position().distanceToSqr(before)<.00001&&stage>50)c.stageStart=now-(long)(duration*1000);
                }
                if(stage>=duration*1000&&c.hit.size()>=0&&c.slashes<c.segment){
                    c.slashes=c.segment;
                    if(c.stats.weaponType.equals("SHORTSWORD")&&c.skill==Skill.TRIPLE)passive(c);
                }
                if(now>c.window||c.segment>=c.parameters.get("slashes",3)&&stage>duration*1000)cancel(true);
            }
            case WAVE->{
                wave(p.position().add(0,.15,0),c.direction,c.parameters.get("range",10),2,1.5,c.stats,c.skill,c.parameters.get("damage",2),false,true);
                cancel(true);
            }
            case OVERDRIVE->{
                if(elapsed>=150)cancel(true);
            }
            case RISING->{
                if(now-c.visualTrail>=100){
                    visual(c,"TRAIL");
                    c.visualTrail=now;
                }
                state.resources.buffs.put("movementProtection",now+500);
                if(elapsed<300){
                    p.move(MoverType.SELF,c.direction.scale(4*dt/.3));
                    slash(c,1.5,2,2,c.parameters.get("damage",1.6),.5,false);
                }
                else if(!c.finisher){
                    c.finisher=true;
                    visual(c,"RELEASE");
                    c.hit.clear();
                    slash(c,2,2,3,c.parameters.get("finisher",2.4),.6,false);
                }
                if(elapsed>=700)cancel(true);
            }
            case DRAW->{
                if(elapsed>=400&&!c.finisher){
                    c.finisher=true;
                    visual(c,"RELEASE");
                    slash(c,c.parameters.get("range",5),10,3,c.parameters.get("damage",6.5),.4,true);
                    if(c.stats.weaponType.equals("SHORTSWORD"))passive(c);
                }
                if(elapsed>=800)cancel(true);
            }
            case DANCE->{
                SkillSchedule schedule=SkillSchedule.dance(c.parameters);
                while(c.slashes<schedule.due(elapsed)){
                    visual(c,"HIT");
                    c.hit.clear();
                    slash(c,c.parameters.get("range",4),3,2,c.parameters.get("damage",.7),.04,false);
                    if(c.stats.weaponType.equals("SHORTSWORD"))passive(c);
                    c.slashes++;
                }
                if(elapsed>=schedule.durationMillis()){
                    wave(p.position().add(0,.8,0),c.direction,8,3,2,c.stats,c.skill,c.parameters.get("finisher",5),false,false);
                    cancel(true);
                }
            }
            case STORM->{
                p.setPos(c.origin);
                p.setDeltaMovement(Vec3.ZERO);
                int total=(int)c.parameters.get("slashes",24);
                double duration=c.parameters.get("duration",8)*1000;
                int due=Math.min(total,Math.max(0,(int)((elapsed-duration*.125)/(duration*.75/total))+1));
                while(c.slashes<due){
                    c.hit.clear();
                    area(c,c.parameters.get("range",5),c.parameters.get("damage",1),true);
                    c.slashes++;
                }
                if(elapsed>=c.parameters.get("duration",8)*900&&!c.finisher){
                    c.finisher=true;
                    visual(c,"FINISH");
                    c.hit.clear();
                    area(c,c.parameters.get("range",5),c.parameters.get("finisher",8),true);
                }
                if(elapsed>=c.parameters.get("duration",8)*1000)cancel(true);
            }
            default->cancel(true);
        }
    }
    private void passive(Cast c){
        if(c.stats.effective(state.progress,Skill.MASTERY)>0)wave(Minecraft.getInstance().player.position().add(0,.8,0),c.direction,8,3,2,c.stats,c.skill,c.parameters.get("damage",1)*.2*mastery(c.stats),true,false);
    }
    private void slash(Cast c,double length,double width,double height,double coefficient,double knockback,boolean fan){
        var p=Minecraft.getInstance().player;
        Vec3 start=p.position().add(0,.8,0);
        strikeCages(new AABB(start,start.add(c.direction.scale(length))).inflate(width/2,height/2,width/2),start,c.id);
        for(LivingEntity e:enemies(p.getBoundingBox().inflate(length+1,height, length+1))){
            Vec3 offset=e.getBoundingBox().getCenter().subtract(start);
            double forward=offset.dot(c.direction),side=Math.abs(offset.x*c.direction.z-offset.z*c.direction.x);
            boolean inside=fan?offset.horizontalDistanceSqr()<=length*length&&forward>=-.3:forward>=-.5&&forward<=length+e.getBbWidth()*.5&&side<=width*.5+e.getBbWidth()*.5;
            if(inside&&Math.abs(offset.y)<=height*.5+e.getBbHeight()*.5&&visible(start,e.getBoundingBox().getCenter())&&!c.hit.contains(e.getUUID())){
                if(hit(e,c.stats,c.skill,coefficient,false,false,knockback,null))c.hit.add(e.getUUID());
            }
        }
    }
    private void strikeCages(AABB bounds,Vec3 from,String attack){
        var mc=Minecraft.getInstance();
        Set<String> hit=cageHits.computeIfAbsent(attack,key->new HashSet<>());
        for(CageEntity cage:mc.level.getEntitiesOfClass(CageEntity.class,bounds))if(visible(from,cage.position().add(0,1,0))&&hit.add(cage.root())){
            JsonObject j=new JsonObject();
            j.addProperty("cage",cage.root());
            j.addProperty("operation",attack);
            state.send("cageHit",j);
        }
        if(cageHits.size()>256)cageHits.remove(cageHits.keySet().iterator().next());
    }
    private void area(Cast c,double radius,double coefficient,boolean inward){
        strikeCages(new AABB(c.center.add(-radius,0,-radius),c.center.add(radius,4,radius)),c.center.add(0,1,0),c.id);
        for(LivingEntity e:enemies(new AABB(c.center.add(-radius,0,-radius),c.center.add(radius,4,radius))))if(e.position().subtract(c.center).horizontalDistanceSqr()<=radius*radius&&visible(c.center.add(0,1,0),e.getBoundingBox().getCenter()))hit(e,c.stats,c.skill,coefficient,false,false,.4,c.center);
    }
    private void wave(Vec3 start,Vec3 direction,double range,double width,double height,Stats stats,Skill skill,double coefficient,boolean passive,boolean ground){
        Wave w=new Wave();
        w.point=start;
        w.origin=start;
        w.started=ClientState.now();
        w.direction=direction;
        w.remaining=range;
        w.width=width;
        w.height=height;
        w.stats=stats;
        w.skill=skill;
        w.coefficient=coefficient;
        w.id=UUID.randomUUID().toString();
        w.passive=passive;
        w.ground=ground;
        waves.add(w);
        waveVisual(w,"START");
        waveVisual(w,"RELEASE");
    }
    private void advance(Wave w){
        var mc=Minecraft.getInstance();
        Vec3 next=w.point.add(w.direction.scale(Math.min(.75,w.remaining)));
        if(w.ground){
            BlockHitResult floor=mc.level.clip(new ClipContext(next.add(0,1.2,0),next.add(0,-1.2,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
            if(floor.getType()==HitResult.Type.MISS||Math.abs(floor.getLocation().y-w.point.y)>1.05){
                waveVisual(w,"END");
                waves.remove(w);
                return;
            }
            next=new Vec3(next.x,floor.getLocation().y+.1,next.z);
        }
        if(!visible(w.point.add(0,.2,0),next.add(0,.2,0))){
            waveVisual(w,"END");
            waves.remove(w);
            return;
        }
        Vec3 from=w.point;
        w.point=next;
        w.remaining-=.75;
        waveVisual(w,"TRAIL");
        for(LivingEntity e:enemies(new AABB(from,next).inflate(w.width/2,w.height/2,w.width/2)))if(!w.hit.contains(e.getUUID())&&visible(from.add(0,.3,0),e.getBoundingBox().getCenter())&&hit(e,w.stats,w.skill,w.coefficient,false,w.passive,.04,null))w.hit.add(e.getUUID());
        strikeCages(new AABB(from,next).inflate(w.width/2,w.height/2,w.width/2),from,w.id);
        if(w.remaining<=0){
            waveVisual(w,"END");
            waves.remove(w);
        }
    }
    private boolean hit(LivingEntity entity,Stats stats,Skill skill,double coefficient,boolean basic,boolean passive,double knockback,Vec3 center){
        return hit(entity,stats,skill,coefficient,basic,passive,knockback,center,null);
    }
    private boolean hit(LivingEntity entity,Stats stats,Skill skill,double coefficient,boolean basic,boolean passive,double knockback,Vec3 center,JsonObject nativeContext){
        Target target=state.context(entity);
        if(target==null||target.life.dead||state.predicted(target.life)<=0)return false;
        if(entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither&&wither.getInvulnerableTicks()>0)return false;
        int level=target.level(state.progress.level,state.dungeon==null?state.progress.sync:state.dungeon.sync);
        boolean magic=!state.progress.swordmaster&&(skill==Skill.GHOST||skill==Skill.WAVE);
        int[] elements=skill==Skill.GHOST?new int[]{
            3
        }
        :new int[0];
        double n=level-1;
        double defense=magic?20+5*n:30+8*n;
        double[] resist={
            0,0,0,0
        }
        ;
        CombatRules.Damage parts=CombatRules.calculate(stats,defense*(1-Math.clamp(stats.modifiers.getOrDefault("piercing",0d),0,1)),resist,magic,elements,coefficient,!passive,random);
        var resolved=CombatRules.skillDamage(parts,stats,skill,mastery(stats),basic,passive,advantage(state.progress.level,level));
        double damage=resolved.total();
        double main=resolved.main();
        double maximum=target.health(level);
        JsonObject extra=new JsonObject();
        JsonObject kb=new JsonObject();
        kb.addProperty("strength",knockback);
        if(center!=null){
            kb.addProperty("inward",true);
            kb.addProperty("centerX",center.x);
            kb.addProperty("centerZ",center.z);
        }
        if(nativeContext==null)extra.add("knockback",kb);
        else extra.add("nativeRoot",nativeContext.get("nativeRoot"));
        int rank=stats.effective(state.progress,Skill.MASTERY);
        long now=ClientState.now();
        if(!passive&&rank>0){
            double g=Math.pow(1.12,rank-1);
            JsonObject ailment=state.ailment(entity.getStringUUID(),"BLEED");
            if(stats.weaponType.equals("KATANA")){
                if(ailment!=null&&ailment.get("expires").getAsLong()>state.serverNow()&&random.nextDouble()<.05){
                    damage*=2*g;
                    main*=2*g;
                }
                if(random.nextDouble()<.1){
                    JsonObject a=new JsonObject();
                    a.addProperty("kind","BLEED");
                    a.addProperty("durationTicks",200);
                    a.addProperty("effect",UUID.randomUUID().toString());
                    a.addProperty("fraction",main/maximum*.05/10);
                    extra.add("ailment",a);
                }
            }
            if(stats.weaponType.equals("LIGHTSABER")){
                JsonObject shock=state.ailment(entity.getStringUUID(),"SHOCK");
                if(shock!=null&&shock.get("expires").getAsLong()>state.serverNow())damage+=(10+2*state.progress.level)*g*1000/(1000+20+5*n)*Math.max(0,1+stats.get(Stat.LIGHT)/120);
                if(random.nextDouble()<.2){
                    JsonObject a=new JsonObject();
                    a.addProperty("kind","SHOCK");
                    a.addProperty("durationTicks",100);
                    extra.add("ailment",a);
                }
            }
            if(stats.weaponType.equals("BLUNT")&&random.nextDouble()<.05){
                JsonObject a=new JsonObject();
                a.addProperty("kind","STUN");
                a.addProperty("durationTicks",(int)Math.ceil(10*g*(!target.kind.equals("NORMAL")?.5:1)));
                extra.add("ailment",a);
            }
        }
        combat();
        int color=parts.element()<0?0xffffff:new int[]{
            0xffed8d,0xff8a58,0x83dfff,0xc783ff
        }
        [parts.element()];
        if(nativeContext!=null){
            double scale=nativeContext.get("nativeScale").getAsDouble();
            damage*=scale;
            if(extra.has("ailment")&&extra.getAsJsonObject("ailment").has("fraction")){
                var ailment=extra.getAsJsonObject("ailment");
                ailment.addProperty("fraction",ailment.get("fraction").getAsDouble()*scale);
            }
        }
        state.report(target.life,damage/maximum,false,maximum,color,extra);
        return true;
    }
    private double mastery(Stats stats){
        return SkillPreview.mastery(state.progress,stats);
    }
    private List<LivingEntity> enemies(AABB bounds){
        var mc=Minecraft.getInstance();
        return mc.level.getEntitiesOfClass(LivingEntity.class,bounds,e->e.isAlive()&&!(e instanceof net.minecraft.world.entity.player.Player)&&(e instanceof Enemy||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon));
    }
    private boolean visible(Vec3 from,Vec3 to){
        var mc=Minecraft.getInstance();
        return mc.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player)).getType()==HitResult.Type.MISS;
    }
    private Vec3 horizontal(Vec3 v){
        Vec3 h=new Vec3(v.x,0,v.z);
        return h.lengthSqr()<.001?new Vec3(0,0,1):h.normalize();
    }
    public void cancel(boolean normal){
        if(cast==null)return;
        visual(cast,normal?"END":"CANCEL");
        cast=null;
        state.upload();
    }
    private void visual(Cast c,String phase){
        var p=Minecraft.getInstance().player;
        if(p==null)return;
        JsonObject j=visualEnvelope(c.id,c.skill.name(),phase,c.start,c.origin,p.position(),c.direction,++c.visualSequence);
        long duration=c.skill==Skill.STORM?(long)(c.parameters.get("duration",8)*1000):c.skill==Skill.GHOST?3800:c.skill==Skill.OVERDRIVE?30000:c.skill==Skill.DRAGON?6000:c.skill==Skill.TRIPLE?4500:c.skill==Skill.DANCE?(long)(c.parameters.get("duration",1.25)*1000+600):1800;
        j.addProperty("duration",duration);
        j.addProperty("rank",c.parameters.rank());
        j.addProperty("charge",Math.clamp((ClientState.now()-c.start)/1000d,0,1));
        boolean gold=state.resources.buffs.getOrDefault("OVERDRIVE",0L)>ClientState.now()&&(c.skill==Skill.TRIPLE||c.skill.ordinal()>=Skill.RISING.ordinal());
        j.addProperty("color",c.skill==Skill.GHOST?0x8E56D9:gold?0xFFD368:0xB4F2FF);
        Effects.receive(j);
        state.send("visual",j);
    }
    private JsonObject visualEnvelope(String id,String kind,String phase,long start,Vec3 origin,Vec3 point,Vec3 direction,int sequence){
        JsonObject j=new JsonObject();
        var p=Minecraft.getInstance().player;
        j.addProperty("caster",p.getStringUUID());
        j.addProperty("session",state.session);
        j.addProperty("generation",state.own.generation);
        j.addProperty("id",id);
        j.addProperty("kind",kind);
        j.addProperty("phase",phase);
        j.addProperty("sequence",sequence);
        j.addProperty("seed",id.hashCode());
        j.addProperty("startedAt",state.serverNow()-(ClientState.now()-start));
        j.addProperty("at",state.serverNow());
        j.addProperty("x",origin.x);
        j.addProperty("y",origin.y);
        j.addProperty("z",origin.z);
        j.addProperty("px",point.x);
        j.addProperty("py",point.y);
        j.addProperty("pz",point.z);
        j.addProperty("dx",direction.x);
        j.addProperty("dy",direction.y);
        j.addProperty("dz",direction.z);
        return j;
    }
    private void waveVisual(Wave wave,String phase){
        if(Minecraft.getInstance().player==null)return;
        JsonObject j=visualEnvelope(wave.id,wave.passive?"MASTERY_WAVE":wave.skill.name(),phase,wave.started,wave.origin,wave.point,wave.direction,++wave.sequence);
        j.addProperty("duration",1500);
        j.addProperty("color",wave.skill==Skill.WAVE?0xB4F2FF:0xFFD368);
        Effects.receive(j);
        state.send("visual",j);
    }
    public void incoming(JsonObject j){
        if(j.has("session")&&!j.get("session").getAsString().equals(state.session)||j.has("generation")&&j.get("generation").getAsLong()!=state.own.generation)return;
        if(!state.ready||state.own.dead||!incomingRoots.add(j.get("root").getAsString()))return;
        if(incomingRoots.size()>2048)incomingRoots.clear();
        combat();
        if(invulnerable()||Minecraft.getInstance().player.isSpectator()||Minecraft.getInstance().player.getAbilities().invulnerable&&!(j.has("bypassInvulnerability")&&j.get("bypassInvulnerability").getAsBoolean()))return;
        double fraction;
        if(j.has("attacker")){
            Target attacker=JSON.fromJson(j.get("attacker"),Target.class);
            int level=attacker.level(state.progress.level,state.dungeon==null?state.progress.sync:state.dungeon.sync);
            double points=attacker.attack(level);
            fraction=monsterDamage(points,false,false,level)/state.stats.get(Stat.HEALTH);
        }
        else{
            String source=j.get("source").getAsString();
            long delay=switch(source){
                case "lava","inFire","onFire","drown","inWall","outOfWorld","outsideBorder"->1000;
                case "cactus","sweetBerryBush","hotFloor","cramming","sting"->500;
                case "freeze"->2000;
                default->0;
            }
            ;
            long now=ClientState.now();
            if(delay>0&&environment.getOrDefault(source,0L)>now)return;
            environment.put(source,now+delay);
            fraction=switch(source){
                case "fall"->Math.max(0,(j.get("fall").getAsDouble()-3)*.05);
                case "lava","outOfWorld","outsideBorder"->.2;
                case "inFire","onFire","inWall","starve","cactus","sweetBerryBush","hotFloor","freeze","sting"->.05;
                case "drown","fallingBlock","anvil","fallingStalactite"->.1;
                case "lightningBolt"->.25;
                case "cramming","magic"->.3;
                case "wither","poison"->.05;
                case "stalagmite"->Math.max(0,(j.get("fall").getAsDouble()-1)*.1);
                case "flyIntoWall"->Math.max(0,Minecraft.getInstance().player.getDeltaMovement().horizontalDistance()*10-3)*.05;
                case "explosion","badRespawnPoint","fireworks"->explosion(j);
                case "arrow","mobProjectile"->Math.ceil(Math.max(0,j.has("speed")?j.get("speed").getAsDouble()*2:2))*.05;
                case "trident"->.4;
                case "thorns"->Math.max(0,j.get("points").getAsDouble())*.05;
                default->{
                    state.message("未适配的伤害来源："+source);
                    yield 0;
                }
            }
            ;
            if(source.equals("poison"))fraction=Math.min(fraction,Math.max(0,state.predicted(state.own)-.001));
            if(source.equals("inFire")||source.equals("onFire")||source.equals("lava")){
                if(Minecraft.getInstance().player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE))fraction=0;
            }
            if(source.equals("drown")&&Minecraft.getInstance().player.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING))fraction=0;
        }
        fraction*=NativeRules.receivedMultiplier(Minecraft.getInstance().player,j.get("source").getAsString(),j.has("projectile")&&j.get("projectile").getAsBoolean(),j.has("bypassEnchantments")&&j.get("bypassEnchantments").getAsBoolean(),j.has("bypassResistance")&&j.get("bypassResistance").getAsBoolean());
        JsonObject feedback=new JsonObject();
        if(j.has("nativeRoot"))feedback.add("nativeRoot",j.get("nativeRoot"));
        ownDamage(fraction*(j.has("nativeScale")?j.get("nativeScale").getAsDouble():1),feedback);
    }
    private double explosion(JsonObject j){
        if(!j.has("x")){
            state.message("未适配的爆炸：缺少原点");
            return 0;
        }
        Vec3 pos=new Vec3(j.get("x").getAsDouble(),j.get("y").getAsDouble(),j.get("z").getAsDouble());
        double f=1-Minecraft.getInstance().player.position().distanceTo(pos)/8;
        return f<=0?0:((f*f+f)*.5*7*8+1)*.05;
    }
    private double monsterDamage(double points,boolean magic,boolean light,int level){
        double defense=state.stats.get(magic?Stat.MAGICAL_DEFENSE:Stat.PHYSICAL_DEFENSE);
        double resist=light?state.stats.get(Stat.LIGHT_RESIST):Math.max(Math.max(state.stats.get(Stat.LIGHT_RESIST),state.stats.get(Stat.FIRE_RESIST)),Math.max(state.stats.get(Stat.ICE_RESIST),state.stats.get(Stat.DARK_RESIST)));
        double damage=points*1000/(1000+Math.max(0,defense))*Math.max(0,1-resist/120)*advantage(level,state.progress.level)*(state.progress.swordmaster?.35:.65);
        if(state.resources.buffs.getOrDefault("movementProtection",0L)>ClientState.now())damage*=.1;
        return damage;
    }
    private void ownDamage(double fraction){
        ownDamage(fraction,null);
    }
    private void ownDamage(double fraction,JsonObject feedback){
        if(fraction<=0||!Double.isFinite(fraction))return;
        double damage=fraction*state.stats.get(Stat.HEALTH);
        for(String key:new ArrayList<>(state.resources.absorption.keySet()))if(!key.equals("overdrivePower")){
            double value=state.resources.absorption.get(key),take=Math.min(value,damage);
            state.resources.absorption.put(key,value-take);
            damage-=take;
        }
        double absorb=Math.min(state.resources.shield,damage);
        state.resources.shield-=absorb;
        damage-=absorb;
        if(damage>0)state.report(state.own,damage/state.stats.get(Stat.HEALTH),false,state.stats.get(Stat.HEALTH),0xff5555,feedback);
        else state.upload();
    }
    public boolean nativeAttack(Entity raw){
        var mc=Minecraft.getInstance();
        LivingEntity target=raw instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragonPart part?part.parentMob:raw instanceof LivingEntity living?living:null;
        if(target==null||!(target instanceof Enemy||target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon))return false;
        var hand=mc.player.getMainHandItem();
        Gear custom=GameItems.gear(hand);
        if(custom!=null)return true;
        if(!hand.isEmpty()&&NativeRules.weapon(hand)&&NativeRules.level(hand)>state.progress.level)return true;
        double charge=mc.player.getAttackStrengthScale(.5f),coefficient=.2+.8*charge*charge;
        if(mc.player.fallDistance>0&&!mc.player.onGround()&&!mc.player.isInWater()&&!mc.player.isSprinting())coefficient*=1.5;
        if(GameItems.id(hand).equals("mace")&&mc.player.fallDistance>1.5)coefficient+=mc.player.fallDistance*4/Math.max(1,mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
        if(raw instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragonPart part&&!part.name.equals("head"))coefficient*=.25;
        hit(target,snapshot(),null,coefficient,true,true,.4,null);
        mc.player.resetAttackStrengthTicker();
        return true;
    }
    public void projectile(JsonObject j){
        if(!state.ready||state.own.dead||!j.get("session").getAsString().equals(state.session)||j.get("generation").getAsLong()!=state.own.generation||!incomingRoots.add(j.get("root").getAsString()))return;
        if(incomingRoots.size()>2048)incomingRoots.clear();
        if(j.has("source")&&j.get("source").getAsString().equals("thrown"))return;
        Target t=JSON.fromJson(j.get("target"),Target.class);
        state.targets.put(t.id,t);
        var mc=Minecraft.getInstance();
        var hand=j.has("weapon")?net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.parse(mc.player.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),j.get("weapon")).getOrThrow():net.minecraft.world.item.ItemStack.EMPTY;
        Gear custom=GameItems.gear(hand);
        if(custom!=null&&!custom.valid(state.progress)||custom==null&&NativeRules.weapon(hand)&&NativeRules.level(hand)>state.progress.level)return;
        var raw=mc.level.getEntity(t.entityId);
        if(raw instanceof LivingEntity living){
            Stats stats=SkillPreview.currentStats(state.projectileStats(hand),state.resources,ClientState.now());
            if(Boolean.getBoolean("ydnf.verify")){FeedbackProbe.weapon=GameItems.id(hand);FeedbackProbe.attack=stats.get(Stat.PHYSICAL_ATTACK);}
            String id=GameItems.id(hand);
            double coefficient=1;
            if(id.endsWith("_spear"))coefficient=Math.max(.5,mc.player.getDeltaMovement().subtract(living.getDeltaMovement()).length()*3);
            else if(Set.of("bow","crossbow").contains(id))coefficient=Math.clamp((j.has("speed")?j.get("speed").getAsDouble():3)/3,0,1);
            if(living instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither&&(wither.getInvulnerableTicks()>0||state.predicted(t.life)<=.5&&Set.of("bow","crossbow").contains(id)))return;
            hit(living,stats,null,coefficient,false,true,.4,null,j);
        }
    }
    public void boss(JsonObject j){
        String id=j.get("id").getAsString();
        if(bossHit.contains(id))return;
        bosses.put(id,j);
    }
    public void cancelBoss(String id){
        bosses.remove(id);
        bossHit.add(id);
    }
    private void tickBoss(long now){
        for(var entry:new ArrayList<>(bosses.entrySet())){
            JsonObject j=entry.getValue();
            Target source=JSON.fromJson(j.get("boss"),Target.class);
            if(!source.dimension.equals(Minecraft.getInstance().level.dimension().identifier().toString())||!source.instance.equals(state.own.instance)){
                bosses.remove(entry.getKey());
                continue;
            }
            long elapsed=state.serverNow()-j.get("startedAt").getAsLong();
            long windup=j.get("windupTicks").getAsLong()*50,active=j.get("activeTicks").getAsLong()*50;
            Vec3 center=new Vec3(j.get("x").getAsDouble(),j.get("y").getAsDouble(),j.get("z").getAsDouble());
            double radius=j.get("radius").getAsDouble();
            if(elapsed<windup){
                Effects.warning(center,radius,j.get("kind").getAsString().contains("CAGE")?0xffd75e:0xff6c60);
                continue;
            }
            if(elapsed>windup+active+1000){
                bosses.remove(entry.getKey());
                continue;
            }
            if(elapsed>=windup&&elapsed<=windup+active&&!bossHit.contains(entry.getKey())&&!j.get("kind").getAsString().equals("CAGE")){
                var p=Minecraft.getInstance().player;
                Vec3 offset=p.position().subtract(center),direction=new Vec3(j.get("dx").getAsDouble(),0,j.get("dz").getAsDouble());
                double half=j.get("halfAngle").getAsDouble();
                String shape=j.has("shape")?j.get("shape").getAsString():"FAN";
                double forward=offset.dot(direction),side=Math.abs(offset.x*direction.z-offset.z*direction.x);
                boolean geometry=shape.equals("SQUARE")?Math.abs(offset.x)<=radius+.3&&Math.abs(offset.z)<=radius+.3:shape.equals("RECT")?forward>=-.3&&forward<=radius+.3&&side<=(j.has("width")?j.get("width").getAsDouble():2)*.5+.3:offset.horizontalDistanceSqr()<=radius*radius&&(shape.equals("CIRCLE")||direction.lengthSqr()<.001||offset.lengthSqr()<.001||horizontal(offset).dot(direction.normalize())>=Math.cos(half));
                if(geometry&&Math.abs(offset.y)<3&&visible(center.add(0,1,0),p.getEyePosition())){
                    bossHit.add(entry.getKey());
                    combat();
                    if(!invulnerable()&&!p.getAbilities().invulnerable&&!p.isSpectator()){
                        Target boss=JSON.fromJson(j.get("boss"),Target.class);
                        int level=boss.level(state.progress.level,state.dungeon==null?state.progress.sync:state.dungeon.sync);
                        JsonObject feedback=new JsonObject();feedback.addProperty("feedbackEntity",boss.id.toString());
                        ownDamage(monsterDamage(boss.attack(level)*j.get("coefficient").getAsDouble(),j.get("magic").getAsBoolean(),j.get("light").getAsBoolean(),level)*NativeRules.receivedMultiplier(p,"bossAction",false,false,false)/state.stats.get(Stat.HEALTH),feedback);
                    }
                }
            }
        }
    }
    public void ailment(JsonObject j){
        if(!j.get("kind").getAsString().equals("BLEED")||!j.get("owner").getAsString().equals(Minecraft.getInstance().player.getStringUUID())||!j.get("session").getAsString().equals(state.session))return;
        String key=j.get("target").getAsString();
        Bleed old=bleeds.get(key);
        bleeds.put(key,new Bleed(j,old==null?ClientState.now()+Math.max(0,j.get("nextAt").getAsLong()-state.serverNow()):old.next,0));
    }
    private void tickBleed(long now){
        for(var entry:new ArrayList<>(bleeds.entrySet())){
            Bleed b=entry.getValue();
            JsonObject j=b.state;
            Target target=state.targets.get(UUID.fromString(j.get("target").getAsString()));
            if(target==null||target.life.dead||target.life.generation!=j.get("generation").getAsLong()||target.life.basis!=j.get("basis").getAsLong()||j.get("expires").getAsLong()<state.serverNow()||state.own.dead){
                bleeds.remove(entry.getKey());
                continue;
            }
            if(now>=b.next&&b.index<10){
                JsonObject extra=new JsonObject();
                JsonObject change=j.getAsJsonObject("change");
                extra.add("effect",change.get("effect"));
                extra.addProperty("index",b.index);
                state.report(target.life,change.get("fraction").getAsDouble(),false,target.health(target.level(state.progress.level,state.progress.sync)),0xe05360,extra);
                bleeds.put(entry.getKey(),new Bleed(j,now+1000,b.index+1));
            }
        }
    }
}
