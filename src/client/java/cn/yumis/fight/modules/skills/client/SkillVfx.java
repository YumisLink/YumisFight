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
        boolean released;
        long terminal;
        int soundPhase=-1;
        double speed=1;
    }

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
        return SkillsClient.current().serverNow();
    }
    public void register(){
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::extract);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::submit);
    }
    public record Sword(ItemStackRenderState model,Vec3 position,float yaw){
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
        if(time>event.startedAt()+event.duration()+1500||event.at()>time+5000)return;
        Track track=tracks.get(event.cast());
        if(track!=null&&event.sequence()<=track.sequence)return;
        receivedEvents++;
        if(event.phase()==SkillVisualEvent.Phase.CANCEL||event.phase()==SkillVisualEvent.Phase.END){
            Track ending=tracks.get(event.cast());
            if(ending!=null&&!SkillVisualRegistry.handler(event.skill()).immediateEnd()&&event.phase()==SkillVisualEvent.Phase.END){
                ending.released=true;
                if(!SkillVisualRegistry.handler(event.skill()).persistentEnd(event))ending.terminal=time;
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
        if(event.phase()!=SkillVisualEvent.Phase.TRAIL){
            track.flashes.add(event);
            while(track.flashes.size()>32)track.flashes.removeFirst();
        }
        if(time-event.at()<200)SkillVisualRegistry.handler(event.skill()).play(event);
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
            if(actor==null||!actor.isAlive()||time>t.start.startedAt()+t.start.duration()+500||t.terminal>0&&time>t.terminal+500){
                if(entry.getKey().equals(cameraCast))endCamera(false);
                iterator.remove();
                closed.put(entry.getKey(),time+45000);
                continue;
            }
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
        LevelRenderContext context=new LevelRenderContext(levelEvent.getRenderState(),new ArrayList<>());
        if(context.levelState().cameraRenderState==null)return;
        long before=System.nanoTime(),time=now();
        Vec3 camera=context.levelState().cameraRenderState.pos;
        List<Quad> quads=new ArrayList<>();
        CareerSkillVisuals.actors(quads,time);
        for(Track t:tracks.values()){
            var event=t.start;
            long age=time-event.startedAt();
            if(age<0||age>event.duration()+500)continue;
            age=(long)(age*t.speed);
            SkillVisualRegistry.handler(event.skill()).render(context,t,age,time,quads,camera);
            CareerSkillVisuals.transformed(t,age,quads);
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
            pose.mulPose(Axis.ZP.rotationDegrees(-135));
            pose.scale(2.4f,2.4f,2.4f);
            sword.model().submit(pose,event.getSubmitNodeCollector(),0xF000F0,OverlayTexture.NO_OVERLAY,0);
            pose.popPose();
        }
        WorldQuads.submit(event,frame.quads());
    }

    public CameraPose camera(){var track=tracks.get(cameraCast);return track==null?null:SkillVisualRegistry.handler(track.start.skill()).camera(track,now());}
    public void animate(Player player,EntityRenderState state){for(var track:tracks.values())if(track.start.caster().equals(player.getUUID()))SkillVisualRegistry.handler(track.start.skill()).animate(track,player,state,now());}
}
