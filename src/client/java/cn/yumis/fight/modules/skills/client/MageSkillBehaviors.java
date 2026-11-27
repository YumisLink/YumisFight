package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.combat.api.ProjectileSpawn;
import cn.yumis.fight.modules.attributes.api.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Player-owned mage projectiles and the snapshots used to create server world summons. */
final class MageSkillBehaviors {
    private static final Gson JSON=new Gson();
    private static final class Flight {SkillCast cast;Vec3 origin,end,position;long at;double duration;int hits,sequence;final Set<UUID> struck=new HashSet<>();}
    private record Fang(SkillCast cast,Vec3 origin,Vec3 direction,long at,long duration){}
    private record Throw(SkillCast cast,Vec3 lockedAim){}
    private static final List<Flight> flights=new ArrayList<>();
    private static final List<Fang> fangs=new ArrayList<>();
    private static final List<Throw> throwing=new ArrayList<>();
    private static final class SummonFlight {SkillCast cast;SummonMoves.Move move;String visual;Vec3 from,to,position;long at,duration;double arc,progress;int hits,sequence;final Set<UUID> struck=new HashSet<>();}
    private static final List<SummonFlight> summonFlights=new ArrayList<>();
    private record Contact(SkillCast cast,LivingEntity target,double coefficient,long until,String hold){}
    private static final List<Contact> contacts=new ArrayList<>();
    private static final Map< String,List<Vec3>> prepared=new HashMap<>();
    private MageSkillBehaviors(){}
    static void prepare(SkillDefinition skill){
        var stats=SkillController.INSTANCE.snapshot();
        if(SummonerSkills.KING.equals(skill.id)&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_KING_LASER.held(stats)&&SummonerClient.isKingAlive(SkillsClient.current().serverNow()))return;
        var reserved=new ArrayList<Vec3>();for(var kind:SummonerSkills.spawnKinds(skill.id,stats))reserved.add(summonPoint(kind,reserved.size(),reserved));if(!reserved.isEmpty())prepared.put(skill.id,reserved);
    }
    static void install(){
        MageClient.registerAction(MageSkills.STAR,c->{if(!WitchClient.tryStartImprovedStar(c))throwProjectile(c,null);});
        MageClient.register(MageSkills.JACK,c->{if(!ElementalistClient.tryStartJackBomb(c))projectile(c);});
        MageClient.registerAction(MageSkills.FANG,c->{
            var p=Minecraft.getInstance().player;
            Vec3 direction=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z).normalize();
            Vec3 from=p.position().add(0,.8,0);
            long windup=Math.max(1,(long)(c.parameters.get("fuseMillis",200)/c.speed));
            long duration=Math.clamp(windup,200,39600)+400;
            fangs.add(new Fang(c,from,direction,ClientUi.now()+windup,duration));
            var fangMotion=new JsonObject();
            fangMotion.addProperty("range",c.parameters.get("range",5.0));
            fangMotion.addProperty("width",c.parameters.get("width",1.5));
            fangMotion.addProperty("mode",BattleMageRules.morphMode(c.parameters));
            fangMotion.addProperty("twinFang",cn.yumis.fight.modules.dungeons.api.TowerBattleCard.BM_FANG_TWIN_SHRED.held(c.stats));
            fangMotion.addProperty("waveFang",cn.yumis.fight.modules.dungeons.api.TowerBattleCard.BM_FANG_WAVE.held(c.stats));
            fangMotion.addProperty("teana",c.stats.modifiers.getOrDefault("bmTeana",0.0)>0);
            MageClient.INSTANCE.visual(c,c.id,"START",from,direction,duration,0,fangMotion);
        });
        MageClient.register(MageSkills.HERDER,MageSkillBehaviors::summon);
        for(String id:SummonerSkills.ALL)if(!id.equals(SummonerSkills.MASTERY))MageClient.register(id,MageSkillBehaviors::summon);
    }
    static void throwProjectile(SkillCast cast,Vec3 lockedAim){
        if(lockedAim!=null){
            cast.direction=lockedAim;
            PlayerPoseChannel.lock(cast);
        }
        cast.scheduledHitAt=cast.start+PlayerPoseChannel.scaled(cast.parameters.get("throwMillis",200),cast.speed);
        throwing.add(new Throw(cast,lockedAim));
    }
    static void projectile(SkillCast c){
        projectile(c,null);
    }
    private static void projectile(SkillCast c,Vec3 lockedAim){
        var p=Minecraft.getInstance().player;c.direction=lockedAim==null?p.getLookAngle().normalize():lockedAim;Vec3 from=p.getEyePosition();double range=c.parameters.get("range",ProjectileSpawn.extendedRange(12));var spawn=ProjectileSpawn.inWorld(p,from,c.direction,range,.15,SkillController::canTarget);
        var f=new Flight();f.cast=c;f.origin=f.position=spawn.position();f.end=from.add(c.direction.scale(range));f.at=ClientUi.now();double projectileSpeed=Math.max(1,c.parameters.get("projectileSpeed",c.skill.projectile.speed()));f.duration=Math.max(1,f.origin.distanceTo(f.end)/projectileSpeed*1000);flights.add(f);
        JsonObject motion=new JsonObject();motion.addProperty("range",f.origin.distanceTo(f.end));motion.addProperty("millis",(long)Math.ceil(f.duration));motion.addProperty("velocity",projectileSpeed);motion.addProperty("arcHeight",0);
        MageClient.INSTANCE.visual(c,c.id,"START",f.origin,c.direction,(long)Math.ceil(f.duration)+c.skill.projectile.impactMillis(),0,motion);
    }
    private static Vec3 nearest(LivingEntity e,Vec3 p){var b=e.getBoundingBox();return new Vec3(Math.clamp(p.x,b.minX,b.maxX),Math.clamp(p.y,b.minY,b.maxY),Math.clamp(p.z,b.minZ,b.maxZ));}
    static void tick(){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;long now=ClientUi.now();
        tickSummons(now);
        for(var pending:new ArrayList<>(throwing)){
            var c=pending.cast();
            if(!MageClient.INSTANCE.valid(c)||cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(mc.player)){
                throwing.remove(pending);
                PlayerPoseChannel.cancel(c);
                MageClient.INSTANCE.finished(c);
            }else if(now>=c.scheduledHitAt){
                throwing.remove(pending);
                c.start=c.stageStart=now;
                projectile(c,pending.lockedAim());
                PlayerPoseChannel.release(c);
            }
        }
        for(var fang:new ArrayList<>(fangs))if(now>=fang.at){
            fangs.remove(fang);
            var c=fang.cast;
            if(!MageClient.INSTANCE.valid(c))continue;
            double fangRange=Math.max(.5,c.parameters.get("range",5)),fangWidth=Math.max(.5,c.parameters.get("width",1.5));
            var bounds=new AABB(fang.origin,fang.origin.add(fang.direction.scale(fangRange)))
                .inflate(Math.max(1,fangWidth/2+.25),1.5,Math.max(1,fangWidth/2+.25));
            for(var target:SkillController.INSTANCE.targets(bounds)){
                var delta=target.getBoundingBox().getCenter().subtract(fang.origin);
                double front=delta.dot(fang.direction),side=Math.abs(delta.x*fang.direction.z-delta.z*fang.direction.x);
                if(front>=-.5&&front<=fangRange+target.getBbWidth()*.5&&side<=fangWidth/2+target.getBbWidth()*.5
                    &&SkillController.INSTANCE.sight(fang.origin,target.getBoundingBox().getCenter()))
                    MageClient.INSTANCE.issue(c,target,c.parameters.get("damage",0),-2,"","");
            }
            MageClient.INSTANCE.visual(c,c.id,"RELEASE",fang.origin,fang.direction,fang.duration,1,null);
            PlayerPoseChannel.release(c);
            MageClient.INSTANCE.finished(c);
        }
        for(var f:new ArrayList<>(flights)){
            var c=f.cast;if(!MageClient.INSTANCE.valid(c)){flights.remove(f);continue;}
            double u=Math.clamp((now-f.at)/f.duration,0,1);Vec3 next=f.origin.lerp(f.end,u);var wall=mc.level.clip(new ClipContext(f.position,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));Vec3 end=wall.getType()==HitResult.Type.MISS?next:wall.getLocation(),from=f.position;
            var collided=SkillController.INSTANCE.targets(new AABB(from,end).inflate(.25)).stream().filter(e->!f.struck.contains(e.getUUID())&&SkillGeometry.intersects(e.getBoundingBox().inflate(.15),from,end)).sorted(Comparator.comparingDouble(e->e.distanceToSqr(from))).toList();boolean done=false;
            for(var target:collided){f.struck.add(target.getUUID());f.position=target.getBoundingBox().inflate(.15).clip(from,end).orElse(from);if(c.skill.projectile.explodes()){done=true;break;}MageClient.INSTANCE.issue(c,target,c.parameters.get("damage",0),-2,"","");if(++f.hits>=(int)Math.max(1,c.parameters.get("pierces",c.skill.projectile.pierces()))){done=true;break;}}
            if(!done)f.position=end;
            if(done||wall.getType()!=HitResult.Type.MISS||u>=1){if(c.skill.projectile.explodes())for(var target:SkillController.INSTANCE.targets(new AABB(f.position,f.position).inflate(c.parameters.get("radius",3)))){Vec3 contact=c.skill.projectile.boundingBoxBlast()?nearest(target,f.position):target.getBoundingBox().getCenter();if(contact.distanceToSqr(f.position)<=Math.pow(c.parameters.get("radius",3),2)&&SkillController.INSTANCE.sight(f.position.add(0,.1,0),contact))MageClient.INSTANCE.issue(c,target,c.parameters.get("damage",0),-2,"","");}MageClient.INSTANCE.visual(c,c.id,"FINISH",f.position,c.direction,(long)Math.ceil(f.duration)+c.skill.projectile.impactMillis(),++f.sequence,null);flights.remove(f);MageClient.INSTANCE.finished(c);}
        }
    }
    private static Vec3 summonPoint(SummonKind kind,int slot,List<Vec3> reserved){
        var p=Minecraft.getInstance().player;var entity=new SummonedCreature(SkillContentTypes.SUMMONS.get(kind).get(),p.level());
        for(int r=1;r<=5;r++)for(int i=0;i<16;i++){double angle=(i+slot)*Math.PI/8;Vec3 origin=p.position().add(Math.cos(angle)*r,1,Math.sin(angle)*r);var floor=p.level().clip(new ClipContext(origin,origin.add(0,-5,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(floor.getType()==HitResult.Type.MISS)continue;Vec3 point=floor.getLocation().add(0,.01,0);if(!p.level().hasChunkAt(BlockPos.containing(point))||!p.level().getWorldBorder().isWithinBounds(BlockPos.containing(point))||reserved.stream().anyMatch(v->v.distanceToSqr(point)<2))continue;var box=entity.getBoundingBox().move(point.subtract(entity.position()));if(p.level().noCollision(box)&&!p.level().containsAnyLiquid(box))return point;}
        throw new IllegalArgumentException("附近没有足够的安全召唤空间");
    }
    private static void summon(SkillCast c){
        var state=SkillsClient.current();var p=Minecraft.getInstance().player;var packet=new JsonObject();
        var look=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z);look=look.lengthSqr()<.01?new Vec3(0,0,1):look.normalize();Vec3 castPoint=p.position().add(0,1,0);
        packet.addProperty("session",state.session());packet.addProperty("generation",state.own().generation);packet.addProperty("id",c.id);packet.addProperty("skill",c.skill.id);packet.addProperty("rank",c.parameters.rank());packet.addProperty("level",state.progress().level);packet.addProperty("dimension",c.dimension);packet.addProperty("instance",c.instance);packet.addProperty("job",c.job);packet.addProperty("at",state.serverNow());
        if(c.skill.id.equals(SummonerSkills.FRENZY)){
            MageClient.INSTANCE.frenzyUntil=state.serverNow()+20000;packet.addProperty("until",MageClient.INSTANCE.frenzyUntil);packet.addProperty("power",.20+.03*(c.parameters.rank()-1));
            if(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_FRENZY_VOLLEY.held(c.stats)){
                packet.addProperty("volley",true);
                Vec3 aim=p.getLookAngle().normalize();
                packet.add("aim",JSON.toJsonTree(new double[]{aim.x,aim.y,aim.z}));
            }
            state.send("summonFrenzy",packet);MageClient.INSTANCE.visual(c,c.id,"START",castPoint,look,2000,0,null);MageClient.INSTANCE.finished(c);return;
        }
        if(c.skill.id.equals(SummonerSkills.KING)&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_KING_LASER.held(c.stats)&&SummonerClient.isKingAlive(state.serverNow())){
            Vec3 aim=p.getLookAngle().normalize();
            packet.add("aim",JSON.toJsonTree(new double[]{aim.x,aim.y,aim.z}));
            state.send("summonKingLaser",packet);
            MageClient.INSTANCE.visual(c,c.id,"START",castPoint,look,1200,0,null);
            MageClient.INSTANCE.finished(c);
            return;
        }
        if(c.skill.id.equals(SummonerSkills.SPIRITS)&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_SPIRITS_SACRIFICE.held(c.stats)){
            SummonerClient.detonateAllAliveSpirits(c);
        }
        JsonArray actors=new JsonArray();var reserved=new ArrayList<Vec3>();var points=prepared.remove(c.skill.id);int mastery=SkillLearning.effective(c.stats,state.progress(),SkillCatalog.require(SummonerSkills.MASTERY));
        double inheritHealth=c.stats.modifiers.getOrDefault("summonInherit/HEALTH",0d),inheritDefense=c.stats.modifiers.getOrDefault("summonInherit/DEFENSE",0d),inheritResist=c.stats.modifiers.getOrDefault("summonInherit/RESIST",0d),inheritShield=c.stats.modifiers.getOrDefault("summonInherit/SHIELD",0d);
        double damageProduct=1+c.stats.modifiers.getOrDefault("summonDamage",0d),durationProduct=1+c.stats.modifiers.getOrDefault("summonDuration",0d);
        var spawnList=SummonerSkills.spawnKinds(c.skill.id,c.stats);
        for(var kind:spawnList){
            Vec3 point=points==null||reserved.size()>=points.size()?summonPoint(kind,reserved.size(),reserved):points.get(reserved.size());reserved.add(point);var inherited=SummonerSkills.inherit(c.stats,kind,mastery);
            // 天赋召唤键位：继承项在类型比例后乘 (1+值)，伤害总增走独立乘区，时长乘 (1+值)。
            inherited.values.put(Stat.HEALTH,inherited.get(Stat.HEALTH)*(1+inheritHealth));
            for(var stat:List.of(Stat.PHYSICAL_DEFENSE,Stat.MAGICAL_DEFENSE))inherited.values.put(stat,inherited.get(stat)*(1+inheritDefense));
            for(var stat:List.of(Stat.LIGHT_RESIST,Stat.FIRE_RESIST,Stat.ICE_RESIST,Stat.DARK_RESIST,Stat.EVASION))inherited.values.put(stat,inherited.get(stat)*(1+inheritResist));
            inherited.values.put(Stat.SHIELD,inherited.get(Stat.SHIELD)*(1+inheritShield));
            if(damageProduct!=1)inherited.modifiers.merge("skillProduct",damageProduct,(a,b)->a*b);
            var effects=c.equipment.copy();effects.killRecovery=0;effects.heroSource="";effects.heroDuration=0;effects.goldenSource="";effects.imprintSource="";effects.manaShare=0;
            var actor=new JsonObject();actor.addProperty("id",UUID.randomUUID().toString());actor.addProperty("kind",kind.name());actor.add("position",JSON.toJsonTree(new double[]{point.x,point.y,point.z}));actor.add("stats",JSON.toJsonTree(inherited));actor.add("effects",JSON.toJsonTree(effects));actor.add("parameters",JSON.toJsonTree(c.parameters.values()));
            var melee=SummonRules.attack(inherited,c.parameters.get("damage",0));var stone=SummonRules.attack(inherited,c.parameters.get("stoneDamage",0));var meleeDamage=SkillPreview.damage(state.progress(),melee,c.skill,1,false,effects);var stoneDamage=SkillPreview.damage(state.progress(),stone,c.skill,1,false,effects);
            actor.add("damage",JSON.toJsonTree(Map.of("melee",meleeDamage,"stone",stoneDamage,"triple",stoneDamage.times(3))));actor.addProperty("health",inherited.get(Stat.HEALTH));actor.addProperty("shield",Math.max(0,inherited.get(Stat.SHIELD)));
            long at=state.serverNow()+(long)((kind==SummonKind.KASIJAS?1000:400)/c.speed);actor.addProperty("at",at);
            long until=SummonerSkills.permanentDuration(kind,c.stats,mastery)?SummonerSkills.PERMANENT_UNTIL:at+(long)((kind==SummonKind.KASIJAS?20000:120000)*durationProduct);
            actor.addProperty("until",until);actors.add(actor);
        }
        packet.add("actors",actors);state.send("summonSpawn",packet);
        // 召唤施法姿态走装饰视觉轨道；召唤兽本体与其招式另有渲染，此处只承载骨骼动画。
        MageClient.INSTANCE.visual(c,c.id,"START",castPoint,look,2000,0,null);MageClient.INSTANCE.finished(c);
    }
    static void action(JsonObject j){
        var state=SkillsClient.current();var mc=Minecraft.getInstance();var c=new SkillCast();c.id=c.summonSource=j.get("source").getAsString();c.session=j.get("session").getAsString();c.generation=j.get("generation").getAsLong();c.job=j.get("job").getAsString();c.dimension=j.get("dimension").getAsString();c.instance=j.get("instance").getAsString();c.skill=SkillCatalog.require(j.get("skill").getAsString());
        var values=new LinkedHashMap<String,Double>();j.getAsJsonObject("parameters").entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsDouble()));c.parameters=new SkillParameters.Snapshot(c.skill,j.get("rank").getAsInt(),values);
        var move=SummonMoves.require(j.get("move").getAsString());var base=JSON.fromJson(j.get("stats"),Stats.class);double talentElement=base.modifiers.getOrDefault("summonElement",-1d);int element=talentElement>=0?(int)talentElement:move.element();
        c.stats=SummonRules.attack(base,c.parameters.get(j.get("stone").getAsBoolean()?"stoneDamage":"damage",0)*j.get("share").getAsDouble());
        c.stats.modifiers.put("summonElement",(double)element);c.stats.modifier("final").mut+=j.get("frenzyPower").getAsDouble();
        if(move.id().equals("herder_cannon")&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_HERDER_CANNON.held(base)){
            cn.yumis.fight.modules.combat.api.ElementAccumulation.addConversion(c.stats,1,cn.yumis.fight.modules.combat.api.ElementAccumulation.Kind.IGNITE,cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_HERDER_CANNON.ratio("buildup"));
        }
        if(move.id().equals("ice_needle")&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_LOUISE_FROST.held(base)){
            cn.yumis.fight.modules.combat.api.ElementAccumulation.addConversion(c.stats,2,cn.yumis.fight.modules.combat.api.ElementAccumulation.Kind.FREEZE,cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_LOUISE_FROST.ratio("buildup"));
        }
        if(c.skill.id.equals(SummonerSkills.KING)&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_KING_SOVEREIGN.held(base)){
            int lower=j.has("lowerSpirits")?j.get("lowerSpirits").getAsInt():SummonerClient.lowerSpiritCount(state.serverNow());
            if(lower>0)c.stats.modifier("skillProduct").alls*=1d+lower*cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_KING_SOVEREIGN.ratio("damage");
        }
        boolean resonance=j.has("resonance")&&j.get("resonance").getAsBoolean()||SummonerClient.resonanceUntil>state.serverNow();
        if(resonance&&(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_KING_SOVEREIGN.held(base)||cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_KING_SOVEREIGN.held(SkillController.INSTANCE.snapshot()))){
            c.stats.modifiers.put("summonResonanceAppend",cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_KING_SOVEREIGN.ratio("bonus"));
        }
        Vec3 rawAxis=ProjectileSpawn.vector(j.getAsJsonArray("axis"));
        c.equipment=JSON.fromJson(j.get("effects"),cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot.class);c.summonSlow=move.slow();c.elementOverride=element;c.origin=ProjectileSpawn.vector(j.getAsJsonArray("origin"));c.center=c.origin;Vec3 aim=ProjectileSpawn.vector(j.getAsJsonArray("aim"));c.direction=(move.id().equals("king_mega_laser")&&rawAxis.lengthSqr()>=.01?rawAxis:rawAxis.multiply(1,0,1)).normalize();c.start=ClientUi.now()-Math.max(0,state.serverNow()-j.get("at").getAsLong());c.summonMove=move.id()+"_impact";
        var actor=mc.level.getEntity(j.get("actorEntity").getAsInt());if(actor!=null&&actor.getStringUUID().equals(j.get("actor").getAsString()))c.origin=actor.getEyePosition();
        if(j.has("warCry")&&j.get("warCry").getAsBoolean()){
            SummonerClient.onSandorWarCry(actor!=null?actor.position():c.origin,ClientUi.now());
        }
        if(move.id().equals("ice_needle")&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_LOUISE_FROST.held(base)){
            int fan=Math.max(1,cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_LOUISE_FROST.count("needles"));
            Vec3 baseDir=aim.subtract(c.origin);
            if(baseDir.lengthSqr()<1e-5)baseDir=c.direction.lengthSqr()<1e-5?new Vec3(0,0,1):c.direction;
            else baseDir=baseDir.normalize();
            double stepRad=Math.toRadians(10.0);
            int half=fan/2;
            Stats spearStats=c.stats.copy();
            spearStats.values.put(Stat.MAGICAL_ATTACK,spearStats.get(Stat.MAGICAL_ATTACK)*.70);
            for(int i=0;i<fan;i++){
                double angle=(i-half)*stepRad;
                double cos=Math.cos(angle),sin=Math.sin(angle);
                Vec3 dir=new Vec3(baseDir.x*cos-baseDir.z*sin,baseDir.y,baseDir.x*sin+baseDir.z*cos).normalize();
                SkillCast spear=copySummonCast(c,spearStats);
                launchSummonFlight(spear,move,j.get("id").getAsString()+"_"+i,spear.origin,spear.origin.add(dir.scale(move.reach())));
            }
            return;
        }
        if(move.projectile()){
            boolean meteor=move.shape().equals("meteor");
            Vec3 from=meteor?aim.add(0,ProjectileSpawn.extendedRange(6),0):c.origin;
            double distance=move.pierces()>1&&move.radius()==0?move.reach():Math.min(move.reach(),from.distanceTo(aim));
            Vec3 to=meteor?aim:from.add(aim.subtract(from).normalize().scale(distance));
            launchSummonFlight(c,move,j.get("id").getAsString(),from,to);
            return;
        }
        double reach=j.get("reach").getAsDouble();
        if(move.shape().equals("area")){
            double areaRadius=SummonerSkills.effectiveRadius(move,c.stats);
            summonArea(c,aim,areaRadius,move.id().equals("ice_pillar_tri"));
        }else if(move.shape().equals("melee")){
            String targetStr=j.get("target").getAsString();
            if(!targetStr.isEmpty()){
                var target=mc.level.getEntity(UUID.fromString(targetStr));
                if(target instanceof LivingEntity living){var context=state.context(living);if(context!=null&&context.life.generation==j.get("targetGeneration").getAsLong()&&living.position().distanceToSqr(c.origin.add(0,-(actor==null?1:actor.getEyeHeight()),0))<=move.range()*move.range()&&SkillController.INSTANCE.sight(c.origin,living.getBoundingBox().getCenter()))contact(c,living,1);}
            }
        }else{
            if(c.direction.lengthSqr()<.01)c.direction=actor==null?new Vec3(0,0,1):actor.getLookAngle().multiply(1,0,1).normalize();if(move.shape().equals("beam")){var wall=mc.level.clip(new ClipContext(c.origin,c.origin.add(c.direction.scale(reach)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));if(wall.getType()!=HitResult.Type.MISS)reach=Math.min(reach,c.origin.distanceTo(wall.getLocation()));}
            int count=0;for(var target:SkillController.INSTANCE.targets(new AABB(c.origin,c.origin).inflate(move.range()+2)).stream().sorted(Comparator.comparingDouble(t->t.distanceToSqr(c.origin))).toList()){Vec3 delta=target.getBoundingBox().getCenter().subtract(c.origin);double forward=delta.dot(c.direction);boolean inside=move.shape().equals("cone")?delta.length()<=reach+target.getBbWidth()/2&&delta.normalize().dot(c.direction)>=.5:forward>=0&&forward<=reach&&delta.subtract(c.direction.scale(forward)).length()<=move.width()/2+target.getBbWidth()/2;if(inside&&SkillController.INSTANCE.sight(c.origin,target.getBoundingBox().getCenter())){contact(c,target,1);if(++count>=move.pierces())break;}}
        }
        MageClient.INSTANCE.visual(c,j.get("id").getAsString(),"START",move.shape().equals("area")?aim:c.origin,c.direction,500,0,null);
    }
    private static SkillCast copySummonCast(SkillCast c,Stats stats){
        var copy=new SkillCast();copy.id=c.id;copy.summonSource=c.summonSource;copy.session=c.session;copy.generation=c.generation;copy.job=c.job;copy.dimension=c.dimension;copy.instance=c.instance;copy.skill=c.skill;copy.parameters=c.parameters;copy.stats=stats;copy.equipment=c.equipment;copy.summonSlow=c.summonSlow;copy.elementOverride=c.elementOverride;copy.origin=c.origin;copy.center=c.center;copy.direction=c.direction;copy.start=c.start;copy.summonMove=c.summonMove;return copy;
    }
    private static void launchSummonFlight(SkillCast c,SummonMoves.Move move,String visualId,Vec3 from,Vec3 to){
        var f=new SummonFlight();f.cast=c;f.move=move;f.visual=visualId;f.from=from;f.to=to;f.position=from;f.at=c.start;
        f.duration=Math.max(50,Math.round(from.distanceTo(to)/Math.max(1d,move.velocity())*1000));
        f.arc=move.id().equals("stone")||move.id().equals("triple_throw")?.5:move.id().equals("bomb")||move.id().equals("herder_cannon")?1:0;
        summonFlights.add(f);c.summonMove=move.id()+"_flight";
        var motion=new JsonObject();motion.addProperty("velocity",move.velocity());motion.addProperty("range",from.distanceTo(to));motion.addProperty("millis",f.duration);motion.addProperty("arcHeight",f.arc);
        Vec3 dir=to.subtract(from);dir=dir.lengthSqr()<1e-6?new Vec3(0,0,1):dir.normalize();
        MageClient.INSTANCE.visual(c,f.visual,"START",from,dir,f.duration+500,0,motion);
        TrainingCombat.hold(c.id,f.visual,ClientUi.now()+f.duration+5000);
    }
    static boolean contact(SkillCast c,LivingEntity target,double coefficient){
        if(SkillsClient.current().context(target)!=null)return SkillController.INSTANCE.hitCast(c,target,coefficient,0);String hold="contact/"+UUID.randomUUID();long until=ClientUi.now()+5000;contacts.add(new Contact(c,target,coefficient,until,hold));TrainingCombat.hold(c.id,hold,until);return true;
    }
    private static void summonArea(SkillCast c,Vec3 center,double radius,boolean frostShatter){
        for(var target:SkillController.INSTANCE.targets(new AABB(center,center).inflate(radius)))
            if(target.getBoundingBox().getCenter().distanceToSqr(center)<=radius*radius&&SkillController.INSTANCE.sight(center,target.getBoundingBox().getCenter())){
                contact(c,target,1);
                if(frostShatter)SummonerClient.onLouiseFrostHit(c,target,false);
            }
    }
    private static void tickSummons(long now){
        var mc=Minecraft.getInstance();for(var pending:new ArrayList<>(contacts)){if(!MageClient.INSTANCE.valid(pending.cast)||!pending.target.isAlive()||now>pending.until){contacts.remove(pending);TrainingCombat.release(pending.cast.id,pending.hold);continue;}if(SkillsClient.current().context(pending.target)!=null){SkillController.INSTANCE.hitCast(pending.cast,pending.target,pending.coefficient,0);contacts.remove(pending);TrainingCombat.release(pending.cast.id,pending.hold);}}
        for(var f:new ArrayList<>(summonFlights)){
            if(!MageClient.INSTANCE.valid(f.cast)){summonFlights.remove(f);TrainingCombat.release(f.cast.id,f.visual);continue;}var move=f.move;double u=Math.clamp((now-f.at)/(double)f.duration,0,1),previous=f.progress;f.progress=u;boolean done=false;int steps=Math.max(1,(int)Math.ceil(f.from.distanceTo(f.to)*(u-previous)/.25));
            double blastRadius=SummonerSkills.effectiveRadius(move,f.cast.stats);
            for(int step=1;step<=steps&&!done;step++){double t=previous+(u-previous)*step/steps;Vec3 next=f.from.lerp(f.to,t).add(0,4*f.arc*t*(1-t),0);var wall=mc.level.clip(new ClipContext(f.position,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));Vec3 end=wall.getType()==HitResult.Type.MISS?next:wall.getLocation(),from=f.position;
                for(var target:SkillController.INSTANCE.targets(new AABB(from,end).inflate(move.width())).stream().filter(x->!f.struck.contains(x.getUUID())&&SkillGeometry.intersects(x.getBoundingBox().inflate(move.width()/2),from,end)).sorted(Comparator.comparingDouble(x->x.distanceToSqr(from))).toList()){f.position=target.getBoundingBox().inflate(move.width()/2).clip(from,end).orElse(end);f.struck.add(target.getUUID());if(blastRadius>0){done=true;break;}contact(f.cast,target,1);if(move.id().equals("ice_needle"))SummonerClient.onLouiseFrostHit(f.cast,target,true);if(++f.hits>=move.pierces()){done=true;break;}}
                if(!done)f.position=end;done|=wall.getType()!=HitResult.Type.MISS;
            }
            if(done||u>=1){
                if(blastRadius>0){
                    summonArea(f.cast,f.position,blastRadius,false);
                    if(move.id().equals("meteor")&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_LOUISE_INFERNO.held(f.cast.stats)){
                        SummonerClient.spawnLouiseMagma(f.cast,f.position,blastRadius);
                    }
                }
                MageClient.INSTANCE.visual(f.cast,f.visual,"FINISH",f.position,f.to.subtract(f.from).normalize(),f.duration+500,++f.sequence,null);summonFlights.remove(f);TrainingCombat.release(f.cast.id,f.visual);
            }
        }
    }
    static void cancel(String id){flights.removeIf(f->f.cast.id.equals(id));fangs.removeIf(f->f.cast.id.equals(id));throwing.removeIf(t->t.cast().id.equals(id));}
    static void clear(){for(var contact:contacts)TrainingCombat.release(contact.cast.id,contact.hold);for(var flight:summonFlights)TrainingCombat.release(flight.cast.id,flight.visual);contacts.clear();summonFlights.clear();flights.clear();fangs.clear();throwing.clear();prepared.clear();}
}
