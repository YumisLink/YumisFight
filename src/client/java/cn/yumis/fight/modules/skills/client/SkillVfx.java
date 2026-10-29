// Mesh geometry and choreography ported from the user-owned YDNF reference; NeoForge render extraction is native.
package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;

import cn.yumis.fight.modules.skills.api.SkillVisualEvent;

import cn.yumis.fight.modules.presentation.api.client.WorldQuads;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.client.*;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.renderer.item.ItemStackRenderState;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;

import net.minecraft.world.phys.*;
import com.mojang.math.Axis;
import java.util.*;
/** All geometry, audio, sword instances and camera state are client-only and disposable. */
public final class SkillVfx {
    public static final class Track {
        SkillVisualEvent start,last;
        int sequence=-1;
        final List<SkillVisualEvent> flashes=new ArrayList<>();
        ItemStack[] swords;
        ItemStackRenderState[] models;
        List<GhostSwordsmanWeaponVisuals.Inclination> inclinations;
        boolean released;
        long terminal,retirement;
        int soundPhase=-1;
        double speed=1;
        Motion motion;
    }
    private record Motion(Vec3 origin,Vec3 direction,double velocity,double range,long started,long millis,boolean ground,int targetEntity,UUID target,double arcHeight){}

    private final Map<UUID,Track> tracks=new LinkedHashMap<>();
    private final Map<UUID,Long> closed=new LinkedHashMap<>();
    private UUID cameraCast;
    private CameraType previousCamera;
    private long geometryFrames,geometryNanos,renderedQuads,receivedEvents;
    public int activeCount(){
        return tracks.size();
    }
    public long[] metrics(){
        return new long[]{
            geometryFrames,geometryNanos,renderedQuads,receivedEvents
        }
        ;
    }
    public List<SkillVisualEvent> activeEvents(){
        return tracks.values().stream().map(t->t.start).toList();
    }
    public static Vec3 vector(SkillVisualEvent.Point point){
        return new Vec3(point.x(),point.y(),point.z());
    }
    private long now(){
        return cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
    }
    public void register(){
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::extract);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::submit);
    }
    public record Sword(ItemStackRenderState model,Vec3 position,float yaw,GhostSwordsmanWeaponVisuals.Inclination inclination){
    }
    private record Frame(List<Quad> quads,List<Sword> swords){
    }
    public record LevelRenderContext(net.minecraft.client.renderer.state.level.LevelRenderState levelState,List<Sword> swords){
    }
    private static final net.minecraft.util.context.ContextKey<Frame> FRAME=new net.minecraft.util.context.ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","skill_meshes"));
    public void accept(SkillVisualEvent event){
        accept(event,1);
    }
    public void accept(SkillVisualEvent event,double speed){
        var mc=Minecraft.getInstance();
        if(!event.valid()||mc.level==null||!mc.level.dimension().identifier().toString().equals(event.dimension())||closed.containsKey(event.cast()))return;
        var own=SkillsClient.current().own();
        if(mc.player!=null&&event.caster().equals(mc.player.getUUID())&&(own==null||own.generation!=event.generation()||!Objects.equals(SkillsClient.current().session(),event.session().toString())))return;
        long time=now();
        if(time>(SkillVisualRegistry.handler(event.skill()).refreshable()?event.at()+5000:event.startedAt()+event.duration()+1500)||event.at()>time+5000)return;
        Track track=tracks.get(event.cast());
        if(track!=null&&event.sequence()<=track.sequence)return;
        receivedEvents++;
        if(event.phase()==SkillVisualEvent.Phase.CANCEL||event.phase()==SkillVisualEvent.Phase.END){
            Track ending=tracks.get(event.cast());
            if(ending!=null&&!SkillVisualRegistry.handler(event.skill()).immediateEnd()&&event.phase()==SkillVisualEvent.Phase.END){
                ending.released=true;
                if(!SkillVisualRegistry.handler(event.skill()).persistentEnd(event)){ending.terminal=event.at();ending.retirement=Math.max(ending.retirement,event.at()+500);}
            }
            else tracks.remove(event.cast());
            if(event.phase()==SkillVisualEvent.Phase.CANCEL||!SkillVisualRegistry.handler(event.skill()).persistentEnd(event))closed.put(event.cast(),time+45000);
            if(event.cast().equals(cameraCast))endCamera(event.phase()==SkillVisualEvent.Phase.END);
            return;
        }
        if(track==null){
            if(tracks.size()>=2048)return;
            track=new Track();
            track.start=event;
            tracks.put(event.cast(),track);
        }
        if(event.phase()==SkillVisualEvent.Phase.START)track.start=event;
        track.speed=Double.isFinite(speed)&&speed>0?speed:1;
        track.last=event;
        track.sequence=event.sequence();
        if(event.phase()==SkillVisualEvent.Phase.RELEASE)track.released=true;
        if(event.phase()==SkillVisualEvent.Phase.FINISH){track.terminal=event.at();track.retirement=SkillVisualRegistry.handler(event.skill()).finishedUntil(track,event);}
        if(event.phase()!=SkillVisualEvent.Phase.TRAIL){
            track.flashes.add(event);
            while(track.flashes.size()>32)track.flashes.removeFirst();
        }
        if(time-event.at()<200)SkillVisualRegistry.handler(event.skill()).play(event);
    }
    /** Semantic motion parameters are sent once at release/segment/lock; frames never wait for trail packets. */
    public void motion(UUID cast,int sequence,com.google.gson.JsonObject value){
        Track track=tracks.get(cast);if(track==null||track.sequence!=sequence||value==null)return;
        var event=track.last;double velocity=value.has("velocity")?value.get("velocity").getAsDouble():0,range=value.has("range")?value.get("range").getAsDouble():0;
        long millis=value.has("millis")?value.get("millis").getAsLong():0;int targetEntity=value.has("targetEntity")?value.get("targetEntity").getAsInt():-1;
        UUID target=value.has("target")?UUID.fromString(value.get("target").getAsString()):null;
        if(!Double.isFinite(velocity)||velocity<0||velocity>4096||!Double.isFinite(range)||range<0||range>4096||millis<0||millis>40000)return;
        double arc=value.has("arcHeight")?value.get("arcHeight").getAsDouble():0;if(!Double.isFinite(arc)||arc<0||arc>128)return;
        Vec3 origin=value.has("px")?new Vec3(value.get("px").getAsDouble(),value.get("py").getAsDouble(),value.get("pz").getAsDouble()):vector(event.position());
        Vec3 direction=value.has("dx")?new Vec3(value.get("dx").getAsDouble(),value.get("dy").getAsDouble(),value.get("dz").getAsDouble()):vector(event.direction());
        long started=value.has("at")?value.get("at").getAsLong():event.at();
        if(!Double.isFinite(origin.x)||!Double.isFinite(origin.y)||!Double.isFinite(origin.z)||!Double.isFinite(direction.x)||!Double.isFinite(direction.y)||!Double.isFinite(direction.z))return;
        track.motion=new Motion(origin,direction,velocity,range,started,millis,value.has("ground")&&value.get("ground").getAsBoolean(),targetEntity,target,arc);
        if(value.has("released")&&value.get("released").getAsBoolean())track.released=true;
    }
    private SkillVisualEvent sample(Track track,long time){
        Motion motion=track.motion;if(motion==null||track.last.phase()==SkillVisualEvent.Phase.FINISH)return track.last;var mc=Minecraft.getInstance();long at=track.terminal>0?Math.min(time,track.terminal):time;
        Vec3 p=motion.origin;
        if(motion.target!=null&&mc.level!=null){var entity=mc.level.getEntity(motion.targetEntity);if(entity!=null&&entity.getUUID().equals(motion.target))p=entity.getBoundingBox().getCenter();}
        else{
            double elapsed=Math.max(0,at-motion.started),distance=motion.velocity*Math.min(motion.millis>0?motion.millis:40000,elapsed)/1000d;p=p.add(motion.direction.scale(Math.min(motion.range,distance)));if(motion.arcHeight>0&&motion.millis>0){double u=Math.clamp(elapsed/motion.millis,0,1);p=p.add(0,4*motion.arcHeight*u*(1-u),0);}
            if(mc.level!=null&&mc.player!=null){
                Vec3 previous=motion.origin;int segments=motion.arcHeight>0?Math.max(1,(int)Math.ceil(motion.origin.distanceTo(p)/.5)):1;
                for(int i=1;i<=segments;i++){double fraction=(double)i/segments,travel=Math.min(motion.range,distance)*fraction;Vec3 next=motion.origin.add(motion.direction.scale(travel));if(motion.arcHeight>0&&motion.millis>0){double u=Math.clamp(elapsed/motion.millis,0,1)*fraction;next=next.add(0,4*motion.arcHeight*u*(1-u),0);}var wall=mc.level.clip(new net.minecraft.world.level.ClipContext(previous,next,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));if(wall.getType()!=HitResult.Type.MISS){p=wall.getLocation();break;}previous=next;}
                if(motion.ground){var floor=mc.level.clip(new net.minecraft.world.level.ClipContext(p.add(0,1.2,0),p.add(0,-1.5,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));if(floor.getType()!=HitResult.Type.MISS)p=new Vec3(p.x,floor.getLocation().y+.15,p.z);}}
        }
        var e=track.last;var phase=e.phase()==SkillVisualEvent.Phase.FINISH?e.phase():SkillVisualEvent.Phase.TRAIL;long eventAt=phase==SkillVisualEvent.Phase.FINISH?e.at():at;
        return new SkillVisualEvent(e.caster(),e.session(),e.cast(),e.generation(),e.dimension(),e.instance(),e.skill(),phase,e.sequence(),e.startedAt(),eventAt,e.duration(),e.seed(),e.origin(),new SkillVisualEvent.Point(p.x,p.y,p.z),e.direction(),e.charge(),e.color(),e.forwardOffset(),e.horizontalScale(),e.sweep());
    }
    public void tick(){
        long time=now();
        var mc=Minecraft.getInstance();
        closed.values().removeIf(until->until<time);
        while(closed.size()>4096)closed.remove(closed.keySet().iterator().next());
        for(var iterator=tracks.entrySet().iterator();iterator.hasNext();){
            var entry=iterator.next();
            Track t=entry.getValue();
            Player actor=mc.level==null?null:mc.level.getPlayerByUUID(t.start.caster());
            boolean wrongWorld=mc.level==null||!mc.level.dimension().identifier().toString().equals(t.start.dimension());
            long deadline=SkillVisualRegistry.handler(t.start.skill()).refreshable()?t.last.at()+5000:t.start.startedAt()+Math.max(t.start.duration(),t.last.duration())+500;
            if(wrongWorld||actor!=null&&!actor.isAlive()||time>deadline||t.retirement>0&&time>t.retirement){
                if(entry.getKey().equals(cameraCast))endCamera(false);
                iterator.remove();
                closed.put(entry.getKey(),time+45000);
                continue;
            }
            // Tracking replay can arrive before the vanilla spawn packet. Retain progress
            // while the actor is absent, including a brief leave/re-enter of tracking range.
            if(actor==null)continue;
            SkillVisualRegistry.handler(t.start.skill()).tick(t,time);
        }
        if(cameraCast!=null){
            var t=tracks.get(cameraCast);
            if(t==null)endCamera(false);
            else mc.options.setCameraType(SkillVisualRegistry.handler(t.start.skill()).cameraType(t,time));
        }
    }
    public void beginCamera(UUID cast){
        if(cameraCast!=null)endCamera(false);
        previousCamera=Minecraft.getInstance().options.getCameraType();
        cameraCast=cast;
        Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }
    public void endCamera(boolean normal){
        if(cameraCast==null)return;
        Minecraft.getInstance().options.setCameraType(normal?CameraType.FIRST_PERSON:previousCamera);
        cameraCast=null;
        previousCamera=null;
    }
    public void clear(){
        endCamera(false);
        tracks.clear();
        closed.clear();
    }
    public record CameraPose(Vec3 position,float yaw,float pitch){
    }

    private void extract(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent levelEvent){
        var mc=Minecraft.getInstance();
        LevelRenderContext context=new LevelRenderContext(levelEvent.getRenderState(),new ArrayList<>());
        if(context.levelState().cameraRenderState==null)return;
        long before=System.nanoTime(),time=now();
        Vec3 camera=context.levelState().cameraRenderState.pos;
        List<Quad> quads=new ArrayList<>();
        CareerSkillVisuals.actors(quads,SkillsClient.current().serverNow());
        for(Track t:tracks.values()){
            var event=t.start;
            if(mc.level==null||mc.level.getPlayerByUUID(event.caster())==null)continue;
            long age=time-event.startedAt();
            if(age<0||age>Math.max(event.duration(),t.last.duration())+500)continue;
            age=(long)(age*t.speed);
            var last=t.last;t.last=sample(t,time);
            try{SkillVisualRegistry.handler(event.skill()).render(context,t,age,time,quads,camera);CareerSkillVisuals.transformed(t,age,quads);}finally{t.last=last;}
        }
        context.levelState().setRenderData(FRAME,new Frame(List.copyOf(quads),List.copyOf(context.swords())));
        geometryFrames++;
        geometryNanos+=System.nanoTime()-before;
        renderedQuads+=quads.size();
    }
    private void submit(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent event){
        Frame frame=event.getLevelRenderState().getRenderData(FRAME);
        if(frame==null||event.getLevelRenderState().cameraRenderState==null)return;
        Vec3 camera=event.getLevelRenderState().cameraRenderState.pos;
        var pose=event.getPoseStack();
        for(Sword sword:frame.swords()){
            Vec3 p=sword.position();
            pose.pushPose();
            pose.translate(p.x-camera.x,p.y-camera.y+.65,p.z-camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(sword.yaw()));
            var inclination=sword.inclination();double azimuth=Math.toRadians(inclination.azimuth());
            pose.mulPose(new org.joml.Quaternionf().rotationAxis((float)Math.toRadians(inclination.degrees()),(float)Math.cos(azimuth),0,(float)Math.sin(azimuth)));
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.scale(1.2f,1.2f,1.2f);
            sword.model().submit(pose,event.getSubmitNodeCollector(),0xF000F0,OverlayTexture.NO_OVERLAY,0);
            pose.popPose();
        }
        WorldQuads.submit(event,frame.quads());
    }

    public CameraPose camera(){var track=tracks.get(cameraCast);return track==null?null:SkillVisualRegistry.handler(track.start.skill()).camera(track,now());}
    public void animate(Player player,EntityRenderState state){for(var track:tracks.values())if(track.start.caster().equals(player.getUUID()))SkillVisualRegistry.handler(track.start.skill()).animate(track,player,state,now());}
}
