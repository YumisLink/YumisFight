package cn.yumis.fight.modules.skills.api.client;

import cn.yumis.fight.modules.dungeons.api.CageEntity;
import cn.yumis.fight.modules.equipment.api.NativeEquipmentRules;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.Slot;
import static cn.yumis.fight.modules.combat.api.LevelAdvantage.advantage;
import cn.yumis.fight.modules.skills.client.Effects;
import cn.yumis.fight.modules.skills.api.SkillPreview;
import cn.yumis.fight.modules.combat.api.CombatTiming;
import cn.yumis.fight.modules.combat.api.CombatRules;
import cn.yumis.fight.modules.skills.api.WeaponSkills;
import cn.yumis.fight.modules.equipment.api.WeaponTraits;
import cn.yumis.fight.modules.skills.api.SkillDefinition;
import cn.yumis.fight.modules.skills.api.SkillCatalog;
import cn.yumis.fight.modules.skills.api.SkillLearning;

import cn.yumis.fight.modules.skills.api.SkillCast;
import cn.yumis.fight.modules.skills.api.SkillPresentation;
import cn.yumis.fight.modules.skills.client.SkillBehaviors;
import cn.yumis.fight.modules.skills.api.SkillParameters;

import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.dungeons.api.Target;

import static cn.yumis.fight.modules.combat.api.LifeRules.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
/** All combat arithmetic and geometry live on the acting client. Server receipts own shared q. */
public final class SkillController {
    private SkillCast hitSource;
    private cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot hitEquipment;
    private JsonObject projectileContext;
    private final cn.yumis.fight.modules.skills.client.StaffBolts staffBolts=new cn.yumis.fight.modules.skills.client.StaffBolts();
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    public static final SkillController INSTANCE=new SkillController();
    private SkillsClient state(){return SkillsClient.current();}
    private final Random random=new Random();
    private final List<Wave> waves=new ArrayList<>();
    private final Map<String,JsonObject> bosses=new HashMap<>();
    private final Set<String> bossHit=new HashSet<>(),incomingRoots=new HashSet<>();
    private final Map<String,Long> environment=new HashMap<>();
    private final Map<String,Bleed> bleeds=new HashMap<>();
    private final ArrayDeque<Runnable> deferred=new ArrayDeque<>();
    public SkillCast cast;
    private final LinkedHashMap<String,Set<String>> cageHits=new LinkedHashMap<>();
    private long lastAttack,lastTick,lastRegen;
    private double regen;
    private String currentWeapon="";
    private record Bleed(JsonObject state,long next,int index){
    }

    private static final class Wave {
        SkillCast source;
        Vec3 point,direction,origin;
        long started;
        int sequence=-1;
        double remaining,width,height,coefficient;
        Stats stats;
        long generation;
        String dimension;
        public SkillDefinition skill;
        String id;
        boolean passive,ground;
        Set<UUID> hit=new HashSet<>();
    }
    private SkillController(){
    }
    public void verificationSeed(long seed){if(!Boolean.getBoolean("ydnf.verify"))throw new IllegalStateException("Verification is disabled");random.setSeed(seed);}
    public boolean validWeapon(){
        var p=Minecraft.getInstance().player;
        Gear g=p==null?null:EquipmentItems.gear(p.getMainHandItem());
        return state().ready()&&g!=null&&g.slot==Slot.WEAPON&&g.valid(state().progress(),cn.yumis.fight.modules.equipment.api.EquipmentEligibility.allowance(Minecraft.getInstance().player));
    }
    public boolean locked(){
        return cast!=null;
    }
    public boolean invulnerable(){
        return cast!=null&&(SkillPresentation.invulnerable(cast.skill)||cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.invulnerable(cast));
    }
    public Stats snapshot(){
        return cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.snapshot(SkillPreview.currentStats(state().stats(),state().resources(),cn.yumis.fight.modules.presentation.api.client.ClientUi.now()));
    }
    public boolean press(int slot){
        if(!state().ready())return false;
        String initial=state().progress().slots[slot];
        if(!validWeapon()&&(!SkillCatalog.contains(initial)||!SkillCatalog.require(initial).equipmentGranted))return false;
        var p=Minecraft.getInstance().player;
        long now=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        if(p.isDeadOrDying()||p.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN))return true;
        if(cast!=null){
            if(cast.slot%8==slot%8&&SkillPresentation.segmented(cast.skill)&&now>=cast.stageStart+SkillPresentation.segmentDelay(cast.skill)/cast.speed&&now<=cast.window&&cast.segment<cast.parameters.get("slashes",3)){
                // Complete the previous segment before an input arriving between movement ticks.
                advance(now,0);
                if(cast==null)return true;
                cast.segment++;
                cast.stageStart=now;
                cast.moved=0;
                cast.direction=horizontal(p.getLookAngle());
                cast.hit.clear();
                cast.window=now+(long)(SkillPresentation.segmentWindow(cast.skill)/cast.speed);
                visual(cast,"SEGMENT");
            }
            return true;
        }
        String binding=state().progress().slots[slot];
        if(binding.isEmpty())return true;
        if(!SkillCatalog.contains(binding)){state().message("该技能内容尚未加载，快捷槽已保留");return false;}
        SkillDefinition skill=SkillCatalog.require(binding);
        int rank=SkillLearning.effective(state().stats(),state().progress(),skill);
        if(rank<=0||skill.passive())return true;
        cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.request(skill,slot);return true;
    }
    public void release(int baseSlot){
        if(cast!=null&&cast.charging&&cast.slot%8==baseSlot)cast.release=true;
    }
    public void begin(SkillCast c){
        if(cast!=null)cancel(false);secondSlash=null;cast=c;
        SkillBehaviors.start(skillContext,c,cn.yumis.fight.modules.presentation.api.client.ClientUi.now());visual(c,"START");
    }
    public List<LivingEntity> targets(AABB box){return enemies(box);}
    public boolean sight(Vec3 from,Vec3 to){return visible(from,to);}
    public void stopVisual(SkillCast c){visual(c,"CANCEL");}
    private String basicCycle="";
    private int basicSlash;
    private record SecondSlash(long at,String cycle,String weapon,long generation,String dimension,Stats stats,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot effects){}
    private SecondSlash secondSlash;
    public void attack(){
        var mc=Minecraft.getInstance();
        if(!validWeapon()||cast!=null||cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.waiting()||mc.player.isDeadOrDying()||mc.player.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN)||cn.yumis.fight.modules.presentation.api.client.ClientUi.now()<lastAttack)return;
        Stats stats=snapshot();double interval=WeaponSkills.interval(state().progress(),stats);
        if(cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.eye()){stats.modifier("final").mut+=.2;stats.modifiers.put("careerEye",1d);}
        lastAttack=cn.yumis.fight.modules.presentation.api.client.ClientUi.now()+(long)Math.ceil(interval);
        String cycle=UUID.randomUUID().toString();JsonObject request=new JsonObject();request.addProperty("id",cycle);state().send("basicStart",request);
        if(mc.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof CageEntity cage){
            JsonObject j=new JsonObject();j.addProperty("cage",cage.root());j.addProperty("operation",UUID.randomUUID().toString());state().send("cageHit",j);mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);return;
        }
        basicStrike(stats,cycle,0,interval,state().resources().equipment);
        cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.basic(stats);
        if(WeaponSkills.mastery(state().progress(),stats)&&stats.weaponType.equals("LIGHTSABER")||cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.frenzy())secondSlash=new SecondSlash(cn.yumis.fight.modules.presentation.api.client.ClientUi.now()+(long)(interval/2),cycle,stats.weaponInstance,state().own().generation,mc.level.dimension().identifier().toString(),stats,state().resources().equipment);
    }
    private void basicStrike(Stats stats,String cycle,int index,double interval,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot equipment){
        var previousEquipment=hitEquipment;hitEquipment=equipment;try{
        var mc=Minecraft.getInstance();var p=mc.player;
        if(WeaponTraits.basic(stats.weaponType).projectileSpeed()>0){staffBolts.fire(state(),stats,equipment,cycle,index);p.swing(net.minecraft.world.InteractionHand.MAIN_HAND);return;}
        basicCycle=cycle;basicSlash=index;
        boolean mastery=WeaponSkills.mastery(state().progress(),stats),great=mastery&&stats.weaponType.equals("GREATSWORD");
        Vec3 from=p.getEyePosition(),direction=horizontal(p.getLookAngle());double reach=great?6:WeaponTraits.sword(stats.weaponType)?4.5:3;
        double coefficient=stats.modifiers.getOrDefault("careerFrenzy",0d)>0?.65:great?1.5:mastery&&stats.weaponType.equals("LIGHTSABER")?.75:1;
        if(great){
            for(LivingEntity e:enemies(p.getBoundingBox().inflate(reach,1,reach))){Vec3 delta=e.getBoundingBox().getCenter().subtract(from);if(delta.horizontalDistanceSqr()<=reach*reach&&Math.abs(delta.y)<=2&&horizontal(delta).dot(direction)>=Math.cos(Math.PI/4)&&visible(from,e.getBoundingBox().getCenter()))hit(e,stats,null,coefficient,true,false,.4,null);}
        }else{
            Vec3 end=from.add(p.getLookAngle().scale(reach));LivingEntity closest=null;double distance=Double.MAX_VALUE;
            for(LivingEntity e:enemies(new AABB(from,end).inflate(1))){var intersection=e.getBoundingBox().inflate(.2).clip(from,end);if(intersection.isPresent()&&visible(from,intersection.get())&&from.distanceToSqr(intersection.get())<distance){closest=e;distance=from.distanceToSqr(intersection.get());}}
            if(closest!=null){hit(closest,stats,null,coefficient,true,false,.4,null);if(mastery&&stats.weaponType.equals("BLUNT")){Vec3 center=closest.position().add(0,1,0);for(LivingEntity e:enemies(closest.getBoundingBox().inflate(2)))if(e!=closest&&e.position().distanceToSqr(closest.position())<=4&&visible(center,e.getBoundingBox().getCenter()))hit(e,stats,null,1,true,false,.4,null);}}
        }
        basicCycle="";p.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        JsonObject visual=visualEnvelope(UUID.nameUUIDFromBytes((cycle+"/"+index).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),"ATTACK","START",cn.yumis.fight.modules.presentation.api.client.ClientUi.now(),p.position(),p.position(),p.getLookAngle(),0);visual.addProperty("color",cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.basicColor(stats));visual.addProperty("duration",Math.max(650,(long)(interval/(mastery&&stats.weaponType.equals("LIGHTSABER")?2:1))));visual.addProperty("reach",reach);visual.addProperty("sweep",great);visual.addProperty("horizontalScale",great?1:WeaponTraits.sword(stats.weaponType)?1.5:1);Effects.receive(visual);state().send("visual",visual);
        if(stats.weaponType.equals("SHORTSWORD")&&mastery)wave(p.position().add(0,.8,0),direction,8,3,2,stats,null,.2*mastery(stats),true,false);
        }finally{hitEquipment=previousEquipment;}
    }
    public void tick(){
        var mc=Minecraft.getInstance();
        if(!state().ready()||mc.player==null||mc.level==null)return;
        long now=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        if(mc.isPaused()){
            lastTick=now;
            return;
        }
        double dt=lastTick==0?.05:Math.min(.25,(now-lastTick)/1000d);
        lastTick=now;
        while(!deferred.isEmpty())deferred.removeFirst().run();
        String weapon=validWeapon()?EquipmentItems.gear(mc.player.getMainHandItem()).instance.toString():"";
        if(!weapon.equals(currentWeapon)){
            currentWeapon=weapon;
            if(!weapon.isEmpty())Effects.spark(mc.player.position().add(0,1,0),0xffedab);
        }
        if(cast!=null&&(!validWeapon()||!cast.weapon.equals(weapon)||mc.player.isDeadOrDying()||mc.player.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN)||!cast.dimension.equals(mc.level.dimension().identifier().toString())))cancel(false);
        if(secondSlash!=null){var next=secondSlash;if(cast!=null||!validWeapon()||!next.weapon.equals(weapon)||next.generation!=state().own().generation||mc.player.isDeadOrDying()||mc.player.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN)||!next.dimension.equals(mc.level.dimension().identifier().toString()))secondSlash=null;else if(now>=next.at){secondSlash=null;basicStrike(next.stats,next.cycle,1,WeaponSkills.interval(state().progress(),next.stats),next.effects);}}
        if(mc.gui.screen()==null&&mc.options.keyAttack.isDown()&&(mc.hitResult==null||mc.hitResult.getType()!=HitResult.Type.BLOCK))attack();
        if(cast!=null)advance(now,dt);
        cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.tick(skillContext);
        for(Wave w:new ArrayList<>(waves))advance(w);
        staffBolts.tick(state(),this::projectileBasicHit);
        boolean out=now>=state().resources().combatUntil;
        if(!state().own().dead){
            state().resources().mp=Math.min(state().stats().get(Stat.MP),state().resources().mp+(1+.04*state().stats().get(Stat.SPIRIT))*state().stats().get(Stat.MANA_REGENERATION)*(out?3:1)*dt);
            if(CombatTiming.shieldReady(state().resources(),now))state().resources().shield=Math.min(state().stats().get(Stat.SHIELD),state().resources().shield+.2*state().stats().get(Stat.SHIELD)*dt);
            if(out){
                if(state().predicted(state().own())<1){
                    regen+=.02*dt;
                    if(now-lastRegen>=100){
                        lastRegen=now;
                        state().report(state().own(),regen,true,snapshot().get(Stat.HEALTH),0xffffff,null);
                        regen=0;
                    }
                }
                else regen=0;
            }
        }
        state().resources().buffs.entrySet().removeIf(e->e.getValue()<now);
        state().resources().absorptionUntil.entrySet().removeIf(e->{
            if(e.getValue()<now){
                state().resources().absorption.remove(e.getKey());return true;
            }
            return false;
        }
        );
        tickBoss(now);
        tickBleed(now);
        Effects.tick();
    }
    private final SkillBehaviors.Context skillContext=new SkillBehaviors.Context(){
        public net.minecraft.client.player.LocalPlayer player(){return Minecraft.getInstance().player;}
        public void buff(String name,long expires){state().resources().buffs.put(name,expires);}
        public void absorption(String name,double value){state().resources().absorption.put(name,value);}
        public void visual(SkillCast c,String phase){SkillController.this.visual(c,phase);}
        public void slash(SkillCast c,double length,double width,double height,double coefficient,double knockback,boolean fan){var previous=hitSource;hitSource=c;try{SkillController.this.slash(c,length,width,height,coefficient,knockback,fan);}finally{hitSource=previous;}}
        public void wave(Vec3 origin,Vec3 direction,double range,double width,double height,Stats stats,SkillDefinition skill,double coefficient,boolean passive,boolean ground){SkillController.this.wave(origin,direction,range,width,height,stats,skill,coefficient,passive,ground);}
        public void area(SkillCast c,double radius,double coefficient,boolean inward){var previous=hitSource;hitSource=c;try{SkillController.this.area(c,radius,coefficient,inward);}finally{hitSource=previous;}}
        public boolean hit(SkillCast c,LivingEntity target,double coefficient){var previous=hitSource;hitSource=c;try{return SkillController.this.hit(target,c.stats,c.skill,coefficient,false,c.dot,0,null);}finally{hitSource=previous;}}
        public void passive(SkillCast c){SkillController.this.passive(c);}
        public void cancel(boolean normal){SkillController.this.cancel(normal);}
    };
    private void advance(long now,double dt){SkillBehaviors.advance(skillContext,cast,now);}

    private void passive(SkillCast c){
        if(SkillLearning.effective(c.stats,state().progress(),SkillCatalog.MASTERY)>0)wave(Minecraft.getInstance().player.position().add(0,.8,0),c.direction,8,3,2,c.stats,c.skill,c.parameters.get("damage",1)*.2*mastery(c.stats),true,false);
    }
    private void slash(SkillCast c,double length,double width,double height,double coefficient,double knockback,boolean fan){
        double scale=c.parameters.get("meleeScale",1);length*=scale;width*=scale;
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
            state().send("cageHit",j);
        }
        if(cageHits.size()>256)cageHits.remove(cageHits.keySet().iterator().next());
    }
    private void area(SkillCast c,double radius,double coefficient,boolean inward){
        strikeCages(new AABB(c.center.add(-radius,0,-radius),c.center.add(radius,4,radius)),c.center.add(0,1,0),c.id);
        for(LivingEntity e:enemies(new AABB(c.center.add(-radius,0,-radius),c.center.add(radius,4,radius))))if(e.position().subtract(c.center).horizontalDistanceSqr()<=radius*radius&&visible(c.center.add(0,1,0),e.getBoundingBox().getCenter()))hit(e,c.stats,c.skill,coefficient,false,false,inward?.4:0,inward?c.center:null);
    }
    private void wave(Vec3 start,Vec3 direction,double range,double width,double height,Stats stats,SkillDefinition skill,double coefficient,boolean passive,boolean ground){
        Wave w=new Wave();
        if(cast!=null&&cast.skill==skill){w.source=new SkillCast();w.source.id=cast.id;w.source.skill=cast.skill;w.source.hitIndex=cast.hitIndex;w.source.controlSeconds=cast.controlSeconds;w.source.dot=cast.dot;w.source.elementOverride=cast.elementOverride;w.source.equipment=cast.equipment;}
        w.point=start;
        w.origin=start;
        w.started=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        w.direction=direction;
        w.remaining=range;
        w.width=width;
        w.height=height;
        w.stats=stats;
        w.generation=state().own().generation;w.dimension=Minecraft.getInstance().level.dimension().identifier().toString();
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
        if(state().own().dead||w.generation!=state().own().generation||!w.dimension.equals(mc.level.dimension().identifier().toString())){waveVisual(w,"END");waves.remove(w);return;}
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
        var previousHit=hitSource;hitSource=w.source;
        for(LivingEntity e:enemies(new AABB(from,next).inflate(w.width/2,w.height/2,w.width/2)))if(!w.hit.contains(e.getUUID())&&visible(from.add(0,.3,0),e.getBoundingBox().getCenter())&&hit(e,w.stats,w.skill,w.coefficient,false,w.passive,.04,null))w.hit.add(e.getUUID());
        hitSource=previousHit;
        strikeCages(new AABB(from,next).inflate(w.width/2,w.height/2,w.width/2),from,w.id);
        if(w.remaining<=0){
            waveVisual(w,"END");
            waves.remove(w);
        }
    }
    private boolean hit(LivingEntity entity,Stats stats,SkillDefinition skill,double coefficient,boolean basic,boolean passive,double knockback,Vec3 center){
        return hit(entity,stats,skill,coefficient,basic,passive,knockback,center,null);
    }
    private boolean hit(LivingEntity entity,Stats stats,SkillDefinition skill,double coefficient,boolean basic,boolean passive,double knockback,Vec3 center,JsonObject nativeContext){
        Target target=state().context(entity);
        if(target==null||target.life.dead||state().predicted(target.life)<=0)return false;
        if(entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither&&wither.getInvulnerableTicks()>0)return false;
        int level=target.level(state().progress().level,state().encounterSync());
        boolean magic=nativeContext==null&&(basic?WeaponTraits.basic(stats.weaponType).magic():cn.yumis.fight.modules.skills.api.CareerRules.magic(skill,state().progress()));
        var equipment=hitSource!=null&&hitSource.equipment!=null?hitSource.equipment:hitEquipment!=null?hitEquipment:state().resources().equipment;
        int[] elements=cn.yumis.fight.modules.skills.api.SkillPreviewRules.elements(skill);
        boolean dot=hitSource!=null&&hitSource.dot;
        if(dot){stats=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(stats),Stats.class);stats.modifiers.put("crit",0d);}
        if(hitSource!=null&&hitSource.elementOverride>=-1)elements=hitSource.elementOverride<0?new int[0]:new int[]{hitSource.elementOverride};
        if(nativeContext==null&&basic&&SkillLearning.effective(stats,state().progress(),SkillCatalog.require(cn.yumis.fight.modules.skills.api.CareerSkills.KEIGA))>0)elements=new int[]{3};
        double n=level-1;
        double defense=target.defense(level,magic);
        if(cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.view.green(entity.getX(),entity.getY(),entity.getZ(),state().serverNow()))defense*=.85;
        double[] resist=cn.yumis.fight.modules.combat.api.EquipmentDamage.resistances(equipment,target.resistances());
        boolean mainAllowed=cn.yumis.fight.modules.combat.api.EquipmentDamage.allowed(equipment,elements);
        elements=cn.yumis.fight.modules.combat.api.EquipmentDamage.elements(equipment,elements);
        CombatRules.Damage parts=CombatRules.calculate(stats,defense*(1-Math.clamp(stats.modifiers.getOrDefault("piercing",0d),0,1)),resist,magic,elements,basic&&!passive?1:coefficient,!passive&&!dot,random);
        if(!mainAllowed)parts=new CombatRules.Damage(0,parts.extra(),parts.element());
        if(basic&&!passive)parts=new CombatRules.Damage(parts.main()*coefficient,parts.extra()*coefficient,parts.element());
        var resolved=cn.yumis.fight.modules.skills.api.SkillDamageRules.skillDamage(parts,stats,skill!=null&&skill.basicSkill(),mastery(stats),basic,passive,advantage(state().progress().level,level));
        double damage=resolved.total();
        double main=resolved.main();
        double maximum=target.health(level);
        JsonObject extra=new JsonObject();
        cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.decorate(hitSource,entity,extra);
        if(basic&&!passive&&!basicCycle.isEmpty()){extra.addProperty("basicCycle",basicCycle);extra.addProperty("basicSlash",basicSlash);}
        JsonObject kb=new JsonObject();
        kb.addProperty("strength",target.kind.equals("BOSS")?0:knockback);
        if(center!=null){
            kb.addProperty("inward",true);
            kb.addProperty("centerX",center.x);
            kb.addProperty("centerZ",center.z);
        }
        if(nativeContext==null)extra.add("knockback",kb);
        else extra.add("nativeRoot",nativeContext.get("nativeRoot"));
        int rank=SkillLearning.effective(stats,state().progress(),SkillCatalog.MASTERY);
        long now=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        if(!passive&&rank>0){
            double g=Math.pow(1.12,rank-1);
            JsonObject ailment=state().ailment(entity.getStringUUID(),"BLEED");
            if(stats.weaponType.equals("KATANA")){
                if(ailment!=null&&ailment.get("expires").getAsLong()>state().serverNow()&&random.nextDouble()<.05){
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
                JsonObject shock=state().ailment(entity.getStringUUID(),"SHOCK");
                if(shock!=null&&shock.get("expires").getAsLong()>state().serverNow()&&cn.yumis.fight.modules.combat.api.EquipmentDamage.allowed(equipment,new int[]{0}))damage+=(10+2*state().progress().level)*g*1000/(1000+20+5*n)*Math.max(0,1+stats.get(Stat.LIGHT)/120);
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
        if(!dot)damage+=cn.yumis.fight.modules.combat.api.EquipmentDamage.append(damage,stats,equipment,defense*(1-Math.clamp(stats.modifiers.getOrDefault("piercing",0d),0,1)),resist);
        if(projectileContext!=null)projectileContext.entrySet().forEach(entry->extra.add(entry.getKey(),entry.getValue()));
        state().report(target.life,damage/maximum,false,maximum,color,extra);
        return true;
    }
    private boolean projectileBasicHit(LivingEntity entity,Stats stats,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot effects,String cycle,int index,Vec3 point){
        var previous=hitEquipment;String oldCycle=basicCycle;int oldSlash=basicSlash;hitEquipment=effects;basicCycle=cycle;basicSlash=index;projectileContext=new JsonObject();JsonArray position=new JsonArray();position.add(point.x);position.add(point.y);position.add(point.z);projectileContext.add("projectilePoint",position);
        try{return hit(entity,stats,null,1,true,false,0,null);}finally{hitEquipment=previous;basicCycle=oldCycle;basicSlash=oldSlash;projectileContext=null;}
    }
    private double mastery(Stats stats){
        return SkillPreview.mastery(state().progress(),stats);
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
        if(!normal)secondSlash=null;
        if(cast==null)return;
        if(!normal)cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.cancel(cast);
        visual(cast,normal?"END":"CANCEL");
        cast=null;
        state().upload();
    }
    private void visual(SkillCast c,String phase){
        var p=Minecraft.getInstance().player;
        if(p==null)return;
        JsonObject j=visualEnvelope(c.id,c.skill.id,phase,c.start,c.origin,p.position(),c.direction,++c.visualSequence);
        if(cn.yumis.fight.modules.skills.api.CareerSkills.contains(c.skill.id)){
            j.addProperty("terminal",c.terminal);j.addProperty("sweep",c.terminal);
            if(c.skill.id.equals(cn.yumis.fight.modules.skills.api.CareerSkills.FIELD)){j.addProperty("x",c.center.x);j.addProperty("y",c.center.y);j.addProperty("z",c.center.z);j.addProperty("px",c.center.x);j.addProperty("py",c.center.y);j.addProperty("pz",c.center.z);}
            if(c.skill.id.equals(cn.yumis.fight.modules.skills.api.CareerSkills.ORB)){j.addProperty("px",c.center.x);j.addProperty("py",c.center.y+.8);j.addProperty("pz",c.center.z);}
            if(c.skill.id.equals(cn.yumis.fight.modules.skills.api.CareerSkills.BLADE)&&phase.equals("FINISH")){j.addProperty("px",c.center.x);j.addProperty("py",c.center.y);j.addProperty("pz",c.center.z);}
            if(c.finisher&&(c.skill.id.equals(cn.yumis.fight.modules.skills.api.CareerSkills.CRASH)||c.skill.id.equals(cn.yumis.fight.modules.skills.api.CareerSkills.ERUPTION))){j.addProperty("px",c.center.x);j.addProperty("py",c.center.y);j.addProperty("pz",c.center.z);}
        }
        j.addProperty("duration",SkillPresentation.duration(c));
        j.addProperty("castSpeed",SkillPresentation.visualSpeed(c));
        j.addProperty("rank",c.parameters.rank());
        j.addProperty("horizontalScale",c.parameters.get("meleeScale",1));
        j.addProperty("charge",Math.clamp((cn.yumis.fight.modules.presentation.api.client.ClientUi.now()-c.start)*c.speed/1000d,0,1));
        if(c.skill.id.equals(cn.yumis.fight.modules.skills.api.CareerSkills.FIELD))j.addProperty("charge",Math.min(1,c.parameters.rank()/15d));
        if(c.skill.id.equals(cn.yumis.fight.modules.skills.api.CareerSkills.SWORD))j.addProperty("charge",Math.min(1,c.parameters.get("blood",0)/10));
        j.addProperty("color",cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.visualColor(c.stats,SkillPresentation.color(c.skill,state().resources().buffs.getOrDefault("yumis_dungeon_and_fight:overdrive",0L)>cn.yumis.fight.modules.presentation.api.client.ClientUi.now())));
        Effects.receive(j);
        state().send("visual",j);
    }
    private JsonObject visualEnvelope(String id,String kind,String phase,long start,Vec3 origin,Vec3 point,Vec3 direction,int sequence){
        JsonObject j=new JsonObject();
        var p=Minecraft.getInstance().player;
        j.addProperty("caster",p.getStringUUID());
        j.addProperty("session",state().session());
        j.addProperty("generation",state().own().generation);
        j.addProperty("id",id);
        j.addProperty("kind",kind);
        j.addProperty("phase",phase);
        j.addProperty("sequence",sequence);
        j.addProperty("seed",id.hashCode());
        j.addProperty("startedAt",state().serverNow()-(cn.yumis.fight.modules.presentation.api.client.ClientUi.now()-start));
        j.addProperty("at",state().serverNow());
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
        JsonObject j=visualEnvelope(wave.id,wave.passive?"MASTERY_WAVE":wave.skill.id,phase,wave.started,wave.origin,wave.point,wave.direction,++wave.sequence);
        j.addProperty("duration",1500);
        j.addProperty("color",cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.visualColor(wave.stats,wave.skill==SkillCatalog.WAVE?0xB4F2FF:0xFFD368));
        Effects.receive(j);
        state().send("visual",j);
    }
    public void incoming(JsonObject j){
        if(j.has("session")&&!j.get("session").getAsString().equals(state().session())||j.has("generation")&&j.get("generation").getAsLong()!=state().own().generation)return;
        if(!state().ready()||state().own().dead||!incomingRoots.add(j.get("root").getAsString()))return;
        if(incomingRoots.size()>2048)incomingRoots.clear();
        if(invulnerable()||Minecraft.getInstance().player.isSpectator()||Minecraft.getInstance().player.getAbilities().invulnerable&&!(j.has("bypassInvulnerability")&&j.get("bypassInvulnerability").getAsBoolean())){nativeOutcome(j,"IMMUNE");return;}
        double fraction;
        if(j.has("attacker")){
            Target attacker=JSON.fromJson(j.get("attacker"),Target.class);
            int level=attacker.level(state().progress().level,state().encounterSync());
            double points=attacker.attack(level);
            fraction=monsterDamage(points,false,false,level)/snapshot().get(Stat.HEALTH);
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
            long now=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
            if(delay>0&&environment.getOrDefault(source,0L)>now){nativeOutcome(j,"IMMUNE");return;}
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
                    state().message("未适配的伤害来源："+source);
                    yield 0;
                }
            }
            ;
            if(source.equals("poison"))fraction=Math.min(fraction,Math.max(0,state().predicted(state().own())-.001));
            if(source.equals("inFire")||source.equals("onFire")||source.equals("lava")){
                if(Minecraft.getInstance().player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE))fraction=0;
            }
            if(source.equals("drown")&&Minecraft.getInstance().player.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING))fraction=0;
        }
        fraction*=NativeEquipmentRules.receivedMultiplier(Minecraft.getInstance().player,j.get("source").getAsString(),j.has("projectile")&&j.get("projectile").getAsBoolean(),j.has("bypassEnchantments")&&j.get("bypassEnchantments").getAsBoolean(),j.has("bypassResistance")&&j.get("bypassResistance").getAsBoolean());
        fraction*=j.has("nativeScale")?j.get("nativeScale").getAsDouble():1;
        if(fraction<=0){nativeOutcome(j,"IMMUNE");return;}
        if(j.has("directAttack")&&j.get("directAttack").getAsBoolean()&&CombatRules.evades(state().stats().get(Stat.EVASION),random::nextDouble)){nativeOutcome(j,"DODGE");return;}
        JsonObject feedback=new JsonObject();
        if(j.has("nativeRoot"))feedback.add("nativeRoot",j.get("nativeRoot"));
        ownDamage(fraction,feedback);
    }
    private void nativeOutcome(JsonObject source,String outcome){
        if(!source.has("nativeRoot"))return;
        JsonObject feedback=new JsonObject();feedback.add("nativeRoot",source.get("nativeRoot"));feedback.addProperty("outcome",outcome);
        state().report(state().own(),0,false,snapshot().get(Stat.HEALTH),0xff5555,feedback);
    }
    private double explosion(JsonObject j){
        if(!j.has("x")){
            state().message("未适配的爆炸：缺少原点");
            return 0;
        }
        Vec3 pos=new Vec3(j.get("x").getAsDouble(),j.get("y").getAsDouble(),j.get("z").getAsDouble());
        double f=1-Minecraft.getInstance().player.position().distanceTo(pos)/8;
        return f<=0?0:((f*f+f)*.5*7*8+1)*.05;
    }
    private double monsterDamage(double points,boolean magic,boolean light,int level){
        Stats current=snapshot();double defense=current.get(magic?Stat.MAGICAL_DEFENSE:Stat.PHYSICAL_DEFENSE);
        double resist=light?current.get(Stat.LIGHT_RESIST):Math.max(Math.max(current.get(Stat.LIGHT_RESIST),current.get(Stat.FIRE_RESIST)),Math.max(current.get(Stat.ICE_RESIST),current.get(Stat.DARK_RESIST)));
        if(!light)resist=Math.max(0,resist);
        double damage=points*1000/(1000+Math.max(0,defense))*Math.max(0,1-resist/120)*advantage(level,state().progress().level)*cn.yumis.fight.modules.skills.api.CareerRules.monsterTaken(state().progress(),current);
        if(state().resources().buffs.getOrDefault("movementProtection",0L)>cn.yumis.fight.modules.presentation.api.client.ClientUi.now())damage*=.1;
        return damage;
    }
    private void ownDamage(double fraction){
        ownDamage(fraction,null);
    }
    private void ownDamage(double fraction,JsonObject feedback){
        if(fraction<=0||!Double.isFinite(fraction))return;
        double damage=fraction*snapshot().get(Stat.HEALTH);
        if(feedback==null)feedback=new JsonObject();
        feedback.addProperty("outcome","HIT");feedback.addProperty("damageTaken",damage);
        for(String key:new ArrayList<>(state().resources().absorption.keySet()))if(!key.equals("overdrivePower")){
            double value=state().resources().absorption.get(key),take=Math.min(value,damage);
            state().resources().absorption.put(key,value-take);
            damage-=take;
        }
        double absorb=Math.min(state().resources().shield,damage);
        state().resources().shield-=absorb;
        damage-=absorb;
        double mana=Math.min(state().resources().mp,damage*Math.clamp(state().resources().equipment.manaShare,0,1));state().resources().mp-=mana;damage-=mana;
        if(damage>0)state().report(state().own(),damage/snapshot().get(Stat.HEALTH),false,snapshot().get(Stat.HEALTH),0xff5555,feedback);
        else if(feedback.has("nativeRoot")||feedback.has("feedbackEntity"))state().report(state().own(),0,false,snapshot().get(Stat.HEALTH),0xff5555,feedback);
        else state().upload();
    }
    public boolean nativeAttack(Entity raw){
        var mc=Minecraft.getInstance();
        LivingEntity target=raw instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragonPart part?part.parentMob:raw instanceof LivingEntity living?living:null;
        if(target==null||!(target instanceof Enemy||target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon))return false;
        var hand=mc.player.getMainHandItem();
        Gear custom=EquipmentItems.gear(hand);
        if(custom!=null)return true;
        if(!hand.isEmpty()&&NativeEquipmentRules.weapon(hand)&&NativeEquipmentRules.level(hand)>state().progress().level)return true;
        double charge=mc.player.getAttackStrengthScale(.5f),coefficient=.2+.8*charge*charge;
        if(mc.player.fallDistance>0&&!mc.player.onGround()&&!mc.player.isInWater()&&!mc.player.isSprinting())coefficient*=1.5;
        if(ItemData.id(hand).equals("mace")&&mc.player.fallDistance>1.5)coefficient+=mc.player.fallDistance*4/Math.max(1,mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
        if(raw instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragonPart part&&!part.name.equals("head"))coefficient*=.25;
        hit(target,snapshot(),null,coefficient,true,true,.4,null);
        mc.player.resetAttackStrengthTicker();
        return true;
    }
    public void projectile(JsonObject j){
        if(!state().ready()||state().own().dead||!j.get("session").getAsString().equals(state().session())||j.get("generation").getAsLong()!=state().own().generation||!incomingRoots.add(j.get("root").getAsString()))return;
        if(incomingRoots.size()>2048)incomingRoots.clear();
        if(j.has("source")&&j.get("source").getAsString().equals("thrown"))return;
        Target t=JSON.fromJson(j.get("target"),Target.class);
        state().targets().put(t.id,t);
        var mc=Minecraft.getInstance();
        var hand=j.has("weapon")?net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.parse(mc.player.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),j.get("weapon")).getOrThrow():net.minecraft.world.item.ItemStack.EMPTY;
        Gear custom=EquipmentItems.gear(hand);
        if(custom!=null&&!custom.valid(state().progress(),cn.yumis.fight.modules.equipment.api.EquipmentEligibility.allowance(Minecraft.getInstance().player))||custom==null&&NativeEquipmentRules.weapon(hand)&&NativeEquipmentRules.level(hand)>state().progress().level)return;
        var raw=mc.level.getEntity(t.entityId);
        if(raw instanceof LivingEntity living){
            Stats stats=SkillPreview.currentStats(state().projectileStats(hand),state().resources(),cn.yumis.fight.modules.presentation.api.client.ClientUi.now());
            SkillDiagnostics.attack.accept(ItemData.id(hand),stats.get(Stat.PHYSICAL_ATTACK));
            String id=ItemData.id(hand);
            double coefficient=1;
            if(id.endsWith("_spear"))coefficient=Math.max(.5,mc.player.getDeltaMovement().subtract(living.getDeltaMovement()).length()*3);
            else if(Set.of("bow","crossbow").contains(id))coefficient=Math.clamp((j.has("speed")?j.get("speed").getAsDouble():3)/3,0,1);
            if(living instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither&&(wither.getInvulnerableTicks()>0||state().predicted(t.life)<=.5&&Set.of("bow","crossbow").contains(id)))return;
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
            if(!source.dimension.equals(Minecraft.getInstance().level.dimension().identifier().toString())||!source.instance.equals(state().own().instance)){
                bosses.remove(entry.getKey());
                continue;
            }
            long elapsed=state().serverNow()-j.get("startedAt").getAsLong();
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
                    if(!invulnerable()&&!p.getAbilities().invulnerable&&!p.isSpectator()&&!CombatRules.evades(state().stats().get(Stat.EVASION),random::nextDouble)){
                        Target boss=JSON.fromJson(j.get("boss"),Target.class);
                        int level=boss.level(state().progress().level,state().encounterSync());
                        JsonObject feedback=new JsonObject();feedback.addProperty("feedbackEntity",boss.id.toString());
                        ownDamage(monsterDamage(boss.attack(level)*j.get("coefficient").getAsDouble(),j.get("magic").getAsBoolean(),j.get("light").getAsBoolean(),level)*NativeEquipmentRules.receivedMultiplier(p,"bossAction",false,false,false)/snapshot().get(Stat.HEALTH),feedback);
                    }
                }
            }
        }
    }
    public void ailment(JsonObject j){
        if(!j.get("kind").getAsString().equals("BLEED")||!j.get("owner").getAsString().equals(Minecraft.getInstance().player.getStringUUID())||!j.get("session").getAsString().equals(state().session()))return;
        String key=j.get("target").getAsString();
        Bleed old=bleeds.get(key);
        bleeds.put(key,new Bleed(j,old==null?cn.yumis.fight.modules.presentation.api.client.ClientUi.now()+Math.max(0,j.get("nextAt").getAsLong()-state().serverNow()):old.next,0));
    }
    private void tickBleed(long now){
        for(var entry:new ArrayList<>(bleeds.entrySet())){
            Bleed b=entry.getValue();
            JsonObject j=b.state;
            Target target=state().targets().get(UUID.fromString(j.get("target").getAsString()));
            if(target==null||target.life.dead||target.life.generation!=j.get("generation").getAsLong()||target.life.basis!=j.get("basis").getAsLong()||j.get("expires").getAsLong()<state().serverNow()||state().own().dead){
                bleeds.remove(entry.getKey());
                continue;
            }
            if(now>=b.next&&b.index<10){
                JsonObject extra=new JsonObject();
                JsonObject change=j.getAsJsonObject("change");
                extra.add("effect",change.get("effect"));
                extra.addProperty("index",b.index);
                state().report(target.life,change.get("fraction").getAsDouble(),false,target.health(target.level(state().progress().level,state().encounterSync())),0xe05360,extra);
                bleeds.put(entry.getKey(),new Bleed(j,now+1000,b.index+1));
            }
        }
    }
}
