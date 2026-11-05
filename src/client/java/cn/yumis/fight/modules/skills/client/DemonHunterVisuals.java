package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.BasicAttackVisuals;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import cn.yumis.fight.modules.presentation.api.client.EffectParticles;
import net.minecraft.client.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.*;
import java.util.*;
import static cn.yumis.fight.modules.skills.client.SkillVfx.vector;

/** Fel ribbons and translucent demon silhouettes. Geometry never determines combat results. */
final class DemonHunterVisuals {
    private static final int GREEN=0x87ec39,PALE=0xd6ff90;
    private static final ContextKey<Boolean> HIDDEN=new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","demon_dance_hidden"));
    private record DanceScene(DemonHunterChoreography.Scene scene,long at){}
    private static final Map<SkillVfx.Track,DanceScene> DANCES=new WeakHashMap<>();
    private record HuntCombo(double duration,int slashes,long at){}
    private record TrailPoint(Vec3 point,long at){}
    private static final Map<SkillVfx.Track,HuntCombo> COMBOS=new WeakHashMap<>();
    private static final Map<SkillVfx.Track,ArrayDeque<TrailPoint>> TRAILS=new WeakHashMap<>();
    private static final long TRAIL_MILLIS=350;
    private DemonHunterVisuals(){}
    static void install(){
        for(String id:DemonHunterSkills.ALL)SkillVisualRegistry.register(id,new Handler(id));
        SkillVisualRegistry.register(DemonHunterSkills.BASIC_ATTACK,new Handler(DemonHunterSkills.BASIC_ATTACK));
        NeoForge.EVENT_BUS.addListener(DemonHunterVisuals::beforePlayerRender);
        BasicAttackVisuals.register("WARGLAIVE",true,(visual,slash,interval)->{
            visual.addProperty("kind",DemonHunterSkills.BASIC_ATTACK);
            visual.addProperty("sequence",slash);
            visual.addProperty("duration",Math.max(1,(long)Math.ceil(interval)));
        });
    }
    private static void beforePlayerRender(RenderPlayerEvent.Pre<?> event){if(Boolean.TRUE.equals(event.getRenderState().getRenderData(HIDDEN)))event.setCanceled(true);}
    private static boolean own(SkillVfx.Track t){var p=Minecraft.getInstance().player;return p!=null&&t.start.caster().equals(p.getUUID());}
    private static double danceAge(SkillVfx.Track t,DanceScene dance,long time){return own(t)?DemonHunterClient.danceAge(t.start.cast()):time-dance.at;}
    private static boolean hidden(SkillVfx.Track t,DanceScene dance,long time){double age=danceAge(t,dance,time);return !t.released&&t.terminal==0&&age>=0&&age<dance.scene.timing().duration()&&!dance.scene.timing().visible(age);}
    private static boolean hidden(UUID actor,long time){for(var entry:DANCES.entrySet())if(entry.getKey().start.caster().equals(actor)&&hidden(entry.getKey(),entry.getValue(),time))return true;return false;}
    private static void trail(SkillVfx.Track t,long time){
        var points=TRAILS.computeIfAbsent(t,k->new ArrayDeque<>());while(!points.isEmpty()&&time-points.getFirst().at>TRAIL_MILLIS)points.removeFirst();
        boolean charge=own(t)?DemonHunterClient.charging(t.start.cast()):!t.released&&!COMBOS.containsKey(t)&&time-t.start.startedAt()<DemonHunterChoreography.HUNT_DURATION;
        var level=Minecraft.getInstance().level;var actor=level==null?null:level.getPlayerByUUID(t.start.caster());if(!charge||actor==null||!actor.isAlive())return;
        Vec3 point=actor.position().add(0,.8,0);if(!points.isEmpty()&&points.getLast().point.distanceToSqr(point)>64)points.clear();
        if(points.isEmpty()||points.getLast().point.distanceToSqr(point)>.0025)points.addLast(new TrailPoint(point,time));
    }
    private record Swing(InteractionHand hand,float progress){}
    private static final class Handler implements SkillVisualRegistry.Handler {
        private final String id;Handler(String id){this.id=id;}
        public int defaultColor(){return GREEN;}
        public boolean cinematic(){return id.equals(DemonHunterSkills.DANCE);}
        public boolean restoreCamera(){return cinematic();}
        public boolean blocksTurning(){return cinematic();}
        public boolean blocksHotbar(){return cinematic()||id.equals(DemonHunterSkills.META)||id.equals(DemonHunterSkills.HUNT);}
        public CameraType cameraType(SkillVfx.Track t,long time){return CameraType.THIRD_PERSON_BACK;}
        public boolean motion(SkillVfx.Track t,JsonObject value){
            if(id.equals(DemonHunterSkills.DANCE)&&value.has("dancePaths")){DANCES.put(t,new DanceScene(DemonHunterChoreography.Scene.read(value),value.get("at").getAsLong()));return true;}
            if(id.equals(DemonHunterSkills.HUNT)&&value.has("huntComboDuration")){double duration=value.get("huntComboDuration").getAsDouble();int slashes=value.get("huntComboSlashes").getAsInt();if(Double.isFinite(duration)&&duration>0&&slashes>0&&slashes<=4096)COMBOS.put(t,new HuntCombo(duration,slashes,value.get("at").getAsLong()));return true;}
            return false;
        }
        public void tick(SkillVfx.Track t,long time){
            if(id.equals(DemonHunterSkills.HUNT))trail(t,time);
            if(id.equals(DemonHunterSkills.DANCE)){
                var scene=DANCES.get(t);if(scene!=null){double age=danceAge(t,scene,time);if(age>=scene.scene.timing().duration()||own(t)&&age<0)Effects.VFX.completeCamera(t.start.cast());}
            }
            if(id.equals(DemonHunterSkills.BASIC_ATTACK)){
                long age=time-t.start.startedAt();int phase=age<0?-1:age>=t.start.duration()?2:(int)(age/(t.start.duration()/2d));
                if(phase==1&&t.soundPhase<1){var mc=Minecraft.getInstance();var actor=mc.level==null?null:mc.level.getPlayerByUUID(t.start.caster());if(actor!=null)mc.level.playLocalSound(actor.getX(),actor.getY(),actor.getZ(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.65f,1.25f,false);}
                t.soundPhase=Math.max(t.soundPhase,phase);
            }
        }
        public SkillVfx.CameraPose camera(SkillVfx.Track t,long time){
            if(!cinematic())return null;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return null;
            Vec3 origin=vector(t.start.origin()),direction=vector(t.start.direction()).multiply(1,0,1).normalize();if(direction.lengthSqr()<.01)direction=new Vec3(0,0,1);
            Vec3 side=new Vec3(-direction.z,0,direction.x),target=origin.add(0,2,0),position=origin.subtract(direction.scale(10)).add(side.scale(5)).add(0,7,0);
            var wall=mc.level.clip(new net.minecraft.world.level.ClipContext(target,position,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
            if(wall.getType()!=HitResult.Type.MISS)position=wall.getLocation().add(target.subtract(position).normalize().scale(.25));Vec3 look=target.subtract(position);
            return new SkillVfx.CameraPose(position,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
        }
        private Swing swing(SkillVfx.Track t,Player player,long time){
            if(!id.equals(DemonHunterSkills.BASIC_ATTACK)&&!id.equals(DemonHunterSkills.CHAOS)&&!id.equals(DemonHunterSkills.HUNT))return null;
            var gear=EquipmentItems.view(player.getMainHandItem());
            if(!player.isAlive()||!player.getOffhandItem().isEmpty()||gear==null||gear.weapon()!=Weapon.WARGLAIVE||t.start.phase()!=SkillVisualEvent.Phase.START)return null;
            double elapsed=time-t.start.startedAt(),length=t.start.duration();int count=2;
            if(id.equals(DemonHunterSkills.HUNT)){var combo=COMBOS.get(t);if(combo==null||t.released)return null;elapsed=own(t)?DemonHunterClient.huntComboAge(t.start.cast()):time-combo.at;length=combo.duration;count=combo.slashes;}
            if(elapsed<0||elapsed>=length)return null;
            length/=count;int slash=Math.min(count-1,(int)(elapsed/length));elapsed-=slash*length;
            return new Swing((slash&1)==0?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND,(float)Math.clamp(elapsed/length,0,1));
        }
        public Float handAttack(SkillVfx.Track t,Player player,InteractionHand hand,long time){var swing=swing(t,player,time);return swing==null?null:swing.hand()==hand?swing.progress():0f;}
        public void animate(SkillVfx.Track t,Player player,net.minecraft.client.renderer.entity.state.EntityRenderState raw,long time){
            if(!(raw instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState s))return;
            var swing=swing(t,player,time);
            if(swing!=null){s.attackTime=swing.progress();s.attackArm=swing.hand()==InteractionHand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();}
            else if(id.equals(DemonHunterSkills.DANCE)){
                var dance=DANCES.get(t);if(dance==null||t.released||t.terminal>0)return;double age=danceAge(t,dance,time);
                if(age<0||age>=dance.scene.timing().duration())return;boolean hide=hidden(t,dance,time);s.setRenderData(HIDDEN,hide);
                Vec3 point=DemonHunterChoreography.safePosition(player,player.position(),dance.scene.point(age)),heading=dance.scene.direction(age);s.x=point.x;s.y=point.y;s.z=point.z;
                s.walkAnimationSpeed=0;s.attackTime=(float)dance.scene.progress(age);s.yRot=0;s.xRot=0;
                if(heading.horizontalDistanceSqr()>.001)s.bodyRot=(float)Math.toDegrees(Math.atan2(-heading.x,heading.z));
                s.shadowRadius=0;s.shadowPieces.clear();
                if(hide){s.displayFireAnimation=false;s.nameTag=null;s.scoreText=null;}
            }
            else return;
            s.rightArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;s.leftArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
        }
        public void play(SkillVisualEvent e){
            if(id.equals(DemonHunterSkills.BASIC_ATTACK)&&e.phase()==SkillVisualEvent.Phase.START){var mc=Minecraft.getInstance();Vec3 p=vector(e.position());if(mc.level!=null)mc.level.playLocalSound(p.x,p.y,p.z,SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.65f,1.15f,false);}
            if(e.phase()==SkillVisualEvent.Phase.FINISH&&(id.equals(DemonHunterSkills.DANCE)||id.equals(DemonHunterSkills.META))){Vec3 p=vector(e.position());for(int i=0;i<28;i++){double angle=i*Math.PI/14;EffectParticles.dust(p.add(Math.cos(angle)*(1+i%3),.1+(i%4)*.1,Math.sin(angle)*(1+i%3)),0xa9a18b,.8f);}}
        }
        public void render(SkillVfx.LevelRenderContext context,SkillVfx.Track t,long age,long time,List<Quad> q,Vec3 camera){
            var mc=Minecraft.getInstance();var actor=mc.level==null?null:mc.level.getPlayerByUUID(t.start.caster());Vec3 p=vector(t.last.position()),d=vector(t.last.direction()).normalize();
            if(id.equals(DemonHunterSkills.BASIC_ATTACK)){
                if(age<0||age>=t.start.duration())return;
                double half=t.start.duration()/2d;int slash=(int)(age/half);double progress=(age-slash*half)/half;
                if(actor!=null){p=PlayerVisualAnchor.position(actor);d=actor.getLookAngle().normalize();}
                Vec3 side=new Vec3(-d.z,0,d.x);if(side.lengthSqr()<.01)side=new Vec3(1,0,0);
                int sign=((slash&1)==0?1:-1)*(actor!=null&&actor.getMainArm()==HumanoidArm.LEFT?-1:1);
                crescent(q,p.add(0,1,0),side.normalize().scale(sign),new Vec3(0,1,0),1.6,.15,t.start.color(),(int)(220*Math.sin(Math.PI*progress)));return;
            }
            if(id.equals(DemonHunterSkills.BEAM)){
                if(actor==mc.player)age=DemonHunterClient.beamAge();
                if(actor==null||t.released||age<DemonHunterChoreography.BEAM_RISE||age>=DemonHunterChoreography.BEAM_DURATION)return;
                Vec3 from=actor.getEyePosition(),dir=actor.getLookAngle().normalize(),end=from.add(dir.scale(16));var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(from,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,actor));if(hit.getType()!=HitResult.Type.MISS)end=hit.getLocation();
                if(actor==mc.player&&mc.options.getCameraType().isFirstPerson()){double length=from.distanceTo(end);if(length<=.5)return;from=from.add(dir.scale(.5));}
                tube(q,from,end,1,GREEN,150);tube(q,from,end,.25,PALE,235);
                double length=from.distanceTo(end);for(double distance=.35+(age%180)/180d;distance<length;distance+=1.5)beamRing(q,from.add(dir.scale(distance)),dir,.88,.08,PALE,210);return;
            }
            if(id.equals(DemonHunterSkills.GLAIVE)){Vec3 right=new Vec3(-d.z,0,d.x);double a=age/70d;Vec3 u=right.scale(Math.cos(a)).add(0,Math.sin(a),0),v=right.scale(-Math.sin(a)).add(0,Math.cos(a),0);crescent(q,p,u,v,.65,.12,GREEN,230);crescent(q,p,u.scale(-1),v,.65,.12,PALE,230);return;}
            if(id.equals(DemonHunterSkills.META)&&actor!=null&&!t.released){Vec3 center=PlayerVisualAnchor.position(actor);for(int i=0;i<6;i++){double a=time/200d+i*Math.PI/3;Vec3 mote=center.add(Math.cos(a)*.7,.3+(i*.3+time/700d)%1.7,Math.sin(a)*.7);beam(q,mote,mote.add(0,.18,0),.05,PALE,190);}}
            if(id.equals(DemonHunterSkills.DANCE)){Vec3 center=vector(t.start.origin());ring(q,center.add(0,.04,0),3,.08,GREEN,170);for(int i=0;i<12;i++){double a=i*Math.PI/6;beam(q,center.add(Math.cos(a)*2.75,.05,Math.sin(a)*2.75),center.add(Math.cos(a)*3,.05,Math.sin(a)*3),.025,PALE,180);}}
            if(id.equals(DemonHunterSkills.HUNT)){
                var points=TRAILS.get(t);if(points!=null){TrailPoint previous=null;for(var point:points){double fade=Math.clamp(1-(time-point.at)/(double)TRAIL_MILLIS,0,1);if(previous!=null&&fade>0){beam(q,previous.point,point.point,.06+.24*fade,GREEN,(int)(210*fade));beam(q,previous.point,point.point,.02+.07*fade,PALE,(int)(245*fade));}previous=point;}}
            }
            if(id.equals(DemonHunterSkills.RUSH)&&actor!=null){Vec3 here=actor.position().add(0,.8,0);beam(q,here,here.subtract(actor.getLookAngle().scale(2.5)),.22,GREEN,100);}
            for(var flash:t.flashes){long n=time-flash.at();if(n<0||n>450||flash.phase()==SkillVisualEvent.Phase.START)continue;double fade=1-n/450d;Vec3 c=vector(flash.position()),forward=vector(flash.direction()).normalize(),side=new Vec3(-forward.z,0,forward.x);if(side.lengthSqr()<.01)side=new Vec3(1,0,0);
                if(id.equals(DemonHunterSkills.CHAOS))side=side.scale(((flash.sequence()&1)==1?1:-1)*(actor!=null&&actor.getMainArm()==HumanoidArm.LEFT?-1:1));
                if(id.equals(DemonHunterSkills.HUNT)&&flash.sequence()>=2)side=side.scale(((flash.sequence()&1)==0?1:-1)*(actor!=null&&actor.getMainArm()==HumanoidArm.LEFT?-1:1));
                if(flash.phase()==SkillVisualEvent.Phase.FINISH){ring(q,c.add(0,.08,0),Math.min(id.equals(DemonHunterSkills.DANCE)?3:4,.3+n/65d),.2,GREEN,(int)(220*fade));}
                else crescent(q,c.add(0,1,0),side,new Vec3(0,1,0),id.equals(DemonHunterSkills.DANCE)?3:1.6,.15,GREEN,(int)(220*fade));
            }
        }
    }
    static void actors(List<Quad> q,long now){
        var mc=Minecraft.getInstance();if(mc.level==null)return;long visualNow=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();for(var state:CareerClient.INSTANCE.view.actors){var p=mc.level.getPlayerByUUID(UUID.fromString(state.owner));if(p==null||hidden(p.getUUID(),visualNow))continue;Vec3 center=PlayerVisualAnchor.position(p);
            if(state.auraUntil>now){ring(q,center.add(0,.06,0),4,.07,GREEN,100);for(int i=0;i<18;i++){double a=i*Math.PI/9+now/1400d,r=3.4+.3*Math.sin(now/270d+i);Vec3 from=center.add(Math.cos(a)*r,.03,Math.sin(a)*r);beam(q,from,from.add(Math.sin(a)*.15,.45+.25*Math.sin(i+now/120d),Math.cos(a)*.15),.055,GREEN,150);}}
            if(state.metamorphosisUntil<=now)continue;double a=Math.toRadians(p.yBodyRot);Vec3 side=new Vec3(Math.cos(a),0,Math.sin(a)),back=new Vec3(Math.sin(a),0,-Math.cos(a));Vec3 root=center.add(0,1.4,0).add(back.scale(.15));
            for(int sign:new int[]{-1,1}){Vec3 tip=root.add(side.scale(sign*(2+.12*Math.sin(now/230d)))).add(0,.9,0).add(back.scale(.65)),low=root.add(side.scale(sign*1.3)).add(0,-.75,0).add(back.scale(.55)),mid=root.add(side.scale(sign*.9)).add(0,.5,0);q.add(new Quad(root,mid,tip,low,0x315a2c,100));q.add(new Quad(low,tip,mid,root,0x315a2c,100));beam(q,root,tip,.035,GREEN,190);beam(q,tip,low,.025,PALE,150);Vec3 horn=center.add(side.scale(sign*.21)).add(0,1.85,0),bend=horn.add(side.scale(sign*.22)).add(0,.35,0).add(back.scale(.1)),end=bend.add(side.scale(-sign*.06)).add(0,.3,0);beam(q,horn,bend,.09,0x253c2b,230);beam(q,bend,end,.045,GREEN,230);}
            for(int i=0;i<5;i++){double v=now/450d+i*Math.PI*2/5;Vec3 here=center.add(Math.cos(v)*.65,.3+(now/500d+i*.4)%1.7,Math.sin(v)*.65);beam(q,here,here.add(0,.15,0),.035,PALE,170);}
        }
    }
    private static Vec3 beamSide(Vec3 direction){Vec3 side=direction.cross(new Vec3(0,1,0));return (side.lengthSqr()<.001?direction.cross(new Vec3(1,0,0)):side).normalize();}
    private static void tube(List<Quad> q,Vec3 from,Vec3 to,double radius,int color,int alpha){
        Vec3 direction=to.subtract(from).normalize(),side=beamSide(direction),up=direction.cross(side).normalize();
        for(int i=0;i<16;i++){double a=i*Math.PI/8,b=(i+1)*Math.PI/8;Vec3 u=side.scale(Math.cos(a)*radius).add(up.scale(Math.sin(a)*radius)),v=side.scale(Math.cos(b)*radius).add(up.scale(Math.sin(b)*radius));q.add(new Quad(from.add(u),to.add(u),to.add(v),from.add(v),color,alpha));q.add(new Quad(from.add(v),to.add(v),to.add(u),from.add(u),color,alpha));}
    }
    private static void beamRing(List<Quad> q,Vec3 center,Vec3 direction,double radius,double width,int color,int alpha){
        Vec3 side=beamSide(direction),up=direction.cross(side).normalize();
        for(int i=0;i<24;i++){double a=i*Math.PI/12,b=(i+1)*Math.PI/12;Vec3 u=side.scale(Math.cos(a)).add(up.scale(Math.sin(a))),v=side.scale(Math.cos(b)).add(up.scale(Math.sin(b)));q.add(new Quad(center.add(u.scale(radius)),center.add(v.scale(radius)),center.add(v.scale(radius-width)),center.add(u.scale(radius-width)),color,alpha));}
    }
    private static void beam(List<Quad> q,Vec3 a,Vec3 b,double w,int color,int alpha){Vec3 d=b.subtract(a),side=new Vec3(-d.z,0,d.x);if(side.lengthSqr()<1e-6)side=new Vec3(1,0,0);side=side.normalize().scale(w);q.add(new Quad(a.add(side),b.add(side),b.subtract(side),a.subtract(side),color,alpha));q.add(new Quad(a.add(0,w,0),b.add(0,w,0),b.add(0,-w,0),a.add(0,-w,0),color,alpha));}
    private static void ring(List<Quad> q,Vec3 p,double r,double w,int color,int alpha){for(int i=0;i<48;i++){double a=i*Math.PI/24,b=(i+1)*Math.PI/24;Vec3 u=new Vec3(Math.cos(a),0,Math.sin(a)),v=new Vec3(Math.cos(b),0,Math.sin(b));q.add(new Quad(p.add(u.scale(r)),p.add(v.scale(r)),p.add(v.scale(r-w)),p.add(u.scale(r-w)),color,alpha));}}
    private static void crescent(List<Quad> q,Vec3 p,Vec3 u,Vec3 v,double r,double w,int color,int alpha){for(int i=0;i<24;i++){double a=-2.5+i*5d/24,b=a+5d/24;Vec3 x=u.scale(Math.cos(a)).add(v.scale(Math.sin(a))),y=u.scale(Math.cos(b)).add(v.scale(Math.sin(b)));q.add(new Quad(p.add(x.scale(r)),p.add(y.scale(r)),p.add(y.scale(r-w)),p.add(x.scale(r-w)),color,alpha));}}
}
