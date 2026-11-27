// Mesh geometry and choreography ported from the user-owned YDNF reference; NeoForge render extraction is native.
package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;

import cn.yumis.fight.modules.skills.api.SkillVisualEvent;
import cn.yumis.fight.modules.skills.api.SkillVisualRules;

import cn.yumis.fight.modules.presentation.api.client.WorldQuads;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.NearEffectRendering;
import cn.yumis.fight.modules.presentation.api.client.EffectModels;
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
        long terminal,retirement,maxDuration;
        int soundPhase=-1;
        double speed=1;
        String poseClip="",poseNext="";
        long poseLead;
        boolean poseFollow;
        int poseOrbColor;
        Motion motion;
        String summonMove="";
        Vec3 followedPoint;
        boolean followedTargetLost;
    }
    private record Motion(Vec3 origin,Vec3 direction,double velocity,double range,long started,long millis,boolean ground,int targetEntity,UUID target,double arcHeight,boolean targetFeet){}

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
    /** Tracks a non-player caster owns; lets champions ride the same pose pipeline. */
    public List<Track> tracksFor(UUID caster){
        List<Track> found=new ArrayList<>();
        for(Track track:tracks.values())if(track.start.caster().equals(caster))found.add(track);
        return found;
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
    private record Frame(List<Quad> quads,List<SpriteQuad> sprites,List<Sword> swords,List<PlayerAfterimages.RenderItem> afterimages,boolean nearFade){
    }
    public record LevelRenderContext(net.minecraft.client.renderer.state.level.LevelRenderState levelState,List<Sword> swords,List<SpriteQuad> sprites){
    }
    private static final net.minecraft.util.context.ContextKey<Frame> FRAME=new net.minecraft.util.context.ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","skill_meshes"));
    void accept(SkillVisualEvent event,double speed,String summonMove){
        var mc=Minecraft.getInstance();
        if(!event.valid()||mc.level==null||!mc.level.dimension().identifier().toString().equals(event.dimension())||closed.containsKey(event.cast()))return;
        var own=SkillsClient.current().own();
        if(mc.player!=null&&event.caster().equals(mc.player.getUUID())&&(own==null||own.generation!=event.generation()||!Objects.equals(SkillsClient.current().session(),event.session().toString())))return;
        long time=now();
        Track track=tracks.get(event.cast());
        boolean terminating=event.phase()==SkillVisualEvent.Phase.CANCEL||event.phase()==SkillVisualEvent.Phase.END;
        long effectiveDuration=track==null?event.duration():Math.max(track.maxDuration,Math.max(track.start.duration(),event.duration()));
        if(event.at()>time+5000||!(terminating&&track!=null)&&time>(SkillVisualRegistry.handler(event.skill()).refreshable()?event.at()+5000:event.startedAt()+effectiveDuration+1500))return;
        if(track!=null&&event.sequence()<=track.sequence)return;
        receivedEvents++;
        if(event.phase()==SkillVisualEvent.Phase.CANCEL||event.phase()==SkillVisualEvent.Phase.END){
            if(event.cast().equals(cameraCast))endCamera(event.phase()==SkillVisualEvent.Phase.END);
            Track ending=tracks.get(event.cast());
            if(ending!=null&&!SkillVisualRegistry.handler(event.skill()).immediateEnd()&&event.phase()==SkillVisualEvent.Phase.END){
                ending.released=true;
                if(!SkillVisualRegistry.handler(event.skill()).persistentEnd(event)){if(ending.terminal<=0)ending.terminal=event.at();ending.retirement=Math.max(ending.retirement,event.at()+500);}
            }
            else tracks.remove(event.cast());
            if(event.phase()==SkillVisualEvent.Phase.CANCEL||!SkillVisualRegistry.handler(event.skill()).persistentEnd(event))closed.put(event.cast(),time+45000);
            return;
        }
        if(track==null){
            if(tracks.size()>=2048)return;
            track=new Track();
            track.start=event;
            tracks.put(event.cast(),track);
        }
        if(event.phase()==SkillVisualEvent.Phase.START)track.start=event;
        track.maxDuration=Math.max(track.maxDuration,event.duration());
        track.speed=Double.isFinite(speed)&&speed>0?speed:1;
        track.last=event;
        track.sequence=event.sequence();
        if(!summonMove.isEmpty()&&summonMove.length()<=32
            &&cn.yumis.fight.modules.skills.api.SummonMoves.find(summonMove.replace("_flight","").replace("_impact",""))!=null)
            track.summonMove=summonMove;
        if(event.phase()==SkillVisualEvent.Phase.RELEASE)track.released=true;
        if(event.phase()==SkillVisualEvent.Phase.FINISH){track.terminal=event.at();track.retirement=SkillVisualRegistry.handler(event.skill()).finishedUntil(track,event);}
        if(event.phase()!=SkillVisualEvent.Phase.TRAIL){
            track.flashes.add(event);
            while(track.flashes.size()>32)track.flashes.removeFirst();
        }
        if(!event.skill().equals(SkillVisualRules.PLAYER_POSE)&&time-event.at()<200)SkillVisualRegistry.handler(event.skill()).play(track,event);
    }
    void poseClip(UUID cast,int sequence,String clip,String next,long lead,boolean follow,int orbColor){
        Track track=tracks.get(cast);
        if(track==null||track.sequence!=sequence||!track.start.skill().equals(SkillVisualRules.PLAYER_POSE))return;
        if(!validClip(clip)||(!next.isEmpty()&&!validClip(next)))return;
        track.poseClip=clip;
        track.poseNext=next;
        track.poseLead=Math.clamp(lead,0,track.start.duration());
        track.poseFollow=follow;
        track.poseOrbColor=orbColor&0xffffff;
    }
    private static boolean validClip(String id){
        return id.length()<=100&&id.matches("(?:[a-z0-9_]+:)?[a-z0-9_]+(?:/[a-z0-9_]+)?");
    }
    /** Semantic motion parameters are sent once at release/segment/lock; frames never wait for trail packets. */
    public void motion(UUID cast,int sequence,com.google.gson.JsonObject value){
        Track track=tracks.get(cast);if(track==null||track.sequence!=sequence||value==null)return;
        var handler=SkillVisualRegistry.handler(track.start.skill());
        if(handler.motion(track,value)){
            if(track.last.phase()==SkillVisualEvent.Phase.FINISH)track.retirement=Math.max(track.retirement,handler.finishedUntil(track,track.last));
            return;
        }
        var event=track.last;double velocity=value.has("velocity")?value.get("velocity").getAsDouble():0,range=value.has("range")?value.get("range").getAsDouble():0;
        long millis=value.has("millis")?value.get("millis").getAsLong():0;int targetEntity=value.has("targetEntity")?value.get("targetEntity").getAsInt():-1;
        UUID target=value.has("target")?UUID.fromString(value.get("target").getAsString()):null;
        if(!Double.isFinite(velocity)||velocity<0||velocity>4096||!Double.isFinite(range)||range<0||range>4096||millis<0||millis>40000)return;
        double arc=value.has("arcHeight")?value.get("arcHeight").getAsDouble():0;if(!Double.isFinite(arc)||arc<0||arc>128)return;
        Vec3 origin=value.has("px")?new Vec3(value.get("px").getAsDouble(),value.get("py").getAsDouble(),value.get("pz").getAsDouble()):vector(event.position());
        Vec3 direction=value.has("dx")?new Vec3(value.get("dx").getAsDouble(),value.get("dy").getAsDouble(),value.get("dz").getAsDouble()):vector(event.direction());
        long started=value.has("at")?value.get("at").getAsLong():event.at();
        if(!Double.isFinite(origin.x)||!Double.isFinite(origin.y)||!Double.isFinite(origin.z)||!Double.isFinite(direction.x)||!Double.isFinite(direction.y)||!Double.isFinite(direction.z))return;
        track.motion=new Motion(origin,direction,velocity,range,started,millis,value.has("ground")&&value.get("ground").getAsBoolean(),targetEntity,target,arc,value.has("targetFeet")&&value.get("targetFeet").getAsBoolean());track.followedPoint=origin;track.followedTargetLost=false;
        if(value.has("released")&&value.get("released").getAsBoolean())track.released=true;
    }
    private SkillVisualEvent sample(Track track,long time){
        Motion motion=track.motion;if(motion==null||track.last.phase()==SkillVisualEvent.Phase.FINISH)return track.last;var mc=Minecraft.getInstance();long at=track.terminal>0?Math.min(time,track.terminal):time;
        Vec3 p=motion.origin;
        if(motion.target!=null&&mc.level!=null){var entity=mc.level.getEntity(motion.targetEntity);if(motion.targetFeet){if(!track.followedTargetLost&&entity!=null&&entity.isAlive()&&entity.getUUID().equals(motion.target))track.followedPoint=entity.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));else track.followedTargetLost=true;p=track.followedPoint;}else if(entity!=null&&entity.getUUID().equals(motion.target))p=entity.getBoundingBox().getCenter();}
        else{
            double elapsed=Math.max(0,at-motion.started),distance=motion.velocity*Math.min(motion.millis>0?motion.millis:40000,elapsed)/1000d;p=p.add(motion.direction.scale(Math.min(motion.range,distance)));if(motion.arcHeight>0&&motion.millis>0){double u=Math.clamp(elapsed/motion.millis,0,1);p=p.add(0,4*motion.arcHeight*u*(1-u),0);}
            if(mc.level!=null&&mc.player!=null){
                Vec3 previous=motion.origin;int segments=motion.arcHeight>0?Math.max(1,(int)Math.ceil(motion.origin.distanceTo(p)/.5)):1;
                for(int i=1;i<=segments;i++){double fraction=(double)i/segments,travel=Math.min(motion.range,distance)*fraction;Vec3 next=motion.origin.add(motion.direction.scale(travel));if(motion.arcHeight>0&&motion.millis>0){double u=Math.clamp(elapsed/motion.millis,0,1)*fraction;next=next.add(0,4*motion.arcHeight*u*(1-u),0);}var wall=mc.level.clip(new net.minecraft.world.level.ClipContext(previous,next,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));if(wall.getType()!=HitResult.Type.MISS){p=wall.getLocation();break;}previous=next;}
                if(motion.ground){var floor=mc.level.clip(new net.minecraft.world.level.ClipContext(p.add(0,1.2,0),p.add(0,-1.5,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));if(floor.getType()!=HitResult.Type.MISS)p=new Vec3(p.x,floor.getLocation().y+.15,p.z);}}
        }
        var e=track.last;var phase=e.phase()==SkillVisualEvent.Phase.FINISH?e.phase():SkillVisualEvent.Phase.TRAIL;long eventAt=phase==SkillVisualEvent.Phase.FINISH?e.at():at;
        return new SkillVisualEvent(e.caster(),e.session(),e.cast(),e.generation(),e.dimension(),e.instance(),e.skill(),phase,e.sequence(),e.startedAt(),eventAt,e.duration(),e.seed(),e.origin(),new SkillVisualEvent.Point(p.x,p.y,p.z),e.direction(),e.charge(),e.color(),e.forwardOffset(),e.horizontalScale(),e.sweep(),e.weaponType(),e.slashAngle(),e.dual());
    }
    public void tick(){
        long time=now();
        var mc=Minecraft.getInstance();
        closed.values().removeIf(until->until<time);
        while(closed.size()>4096)closed.remove(closed.keySet().iterator().next());
        for(var iterator=tracks.entrySet().iterator();iterator.hasNext();){
            var entry=iterator.next();
            Track t=entry.getValue();
            net.minecraft.world.entity.Entity actor=mc.level==null?null:mc.level.getEntity(t.start.caster());
            boolean wrongWorld=mc.level==null||!mc.level.dimension().identifier().toString().equals(t.start.dimension());
            long baseDeadline=SkillVisualRegistry.handler(t.start.skill()).refreshable()?t.last.at()+5000:t.start.startedAt()+Math.max(t.maxDuration,Math.max(t.start.duration(),t.last.duration()))+500;
            long deadline=t.retirement>0?Math.max(baseDeadline,t.retirement):baseDeadline;
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
    public boolean blocksTurning(){
        var t=tracks.get(cameraCast);
        if(t!=null&&SkillVisualRegistry.handler(t.start.skill()).blocksTurning())return true;
        var activeCast=cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.cast;
        if(activeCast!=null&&SkillVisualRegistry.handler(activeCast.skill.id).blocksTurning())return true;
        String dh=DemonHunterClient.activeSkillId();
        return !dh.isEmpty()&&SkillVisualRegistry.handler(dh).blocksTurning();
    }
    public boolean blocksHotbar(){
        if(OracleClient.INSTANCE.locked())return true;
        var t=tracks.get(cameraCast);
        if(t!=null&&SkillVisualRegistry.handler(t.start.skill()).blocksHotbar())return true;
        var activeCast=cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.cast;
        if(activeCast!=null&&SkillVisualRegistry.handler(activeCast.skill.id).blocksHotbar())return true;
        String dh=DemonHunterClient.activeSkillId();
        return !dh.isEmpty()&&SkillVisualRegistry.handler(dh).blocksHotbar();
    }
    public void beginCamera(UUID cast){
        if(!tracks.containsKey(cast))return;
        if(cameraCast!=null)endCamera(false);
        previousCamera=Minecraft.getInstance().options.getCameraType();
        cameraCast=cast;
        Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }
    public void endCamera(boolean normal){
        if(cameraCast==null)return;
        var track=tracks.get(cameraCast);boolean restore=track!=null&&SkillVisualRegistry.handler(track.start.skill()).restoreCamera();
        Minecraft.getInstance().options.setCameraType(normal&&!restore?CameraType.FIRST_PERSON:previousCamera);
        cameraCast=null;
        previousCamera=null;
    }
    public void completeCamera(UUID cast){if(cast.equals(cameraCast))endCamera(true);}
    public void clear(){
        endCamera(false);
        tracks.clear();
        closed.clear();
        PlayerAfterimages.clear();
        BerserkerVisuals.clear();
        AsuraVisuals.clear();
        SoulBenderVisuals.clear();
        SwordmasterVisuals.clear();
        OracleSkillVisuals.clear();
        SunMoonSkillVisuals.clear();
        ElementalistVisuals.clear();
        BattleMageVisuals.clear();
        WitchVisuals.clear();
    }
    public record CameraPose(Vec3 position,float yaw,float pitch){
    }

    private void extract(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent levelEvent){
        var mc=Minecraft.getInstance();
        List<SpriteQuad> sprites=new ArrayList<>();
        LevelRenderContext context=new LevelRenderContext(levelEvent.getRenderState(),new ArrayList<>(),sprites);
        if(context.levelState().cameraRenderState==null)return;
        long before=System.nanoTime(),time=now();
        Vec3 camera=context.levelState().cameraRenderState.pos;
        List<Quad> quads=new ArrayList<>();SummonAmbientVisuals.extract(quads,sprites,camera);
        BerserkerVisuals.actors(quads,sprites,camera,SkillsClient.current().serverNow(),time);
        AsuraVisuals.actors(sprites,camera,SkillsClient.current().serverNow(),time);
        SoulBenderVisuals.actors(sprites,camera,SkillsClient.current().serverNow(),time);
        SwordmasterVisuals.actors(sprites,camera,SkillsClient.current().serverNow(),time);
        OracleSkillVisuals.actors(sprites,camera,SkillsClient.current().serverNow(),time);
        SunMoonSkillVisuals.actors(sprites,camera,SkillsClient.current().serverNow(),time);
        ElementalistVisuals.actors(sprites,camera,SkillsClient.current().serverNow(),time);
        BattleMageVisuals.actors(sprites,camera,SkillsClient.current().serverNow(),time);
        WitchVisuals.actors(sprites,camera,SkillsClient.current().serverNow(),time);
        DemonHunterVisuals.actors(quads,SkillsClient.current().serverNow());
        for(Track t:tracks.values()){
            var event=t.start;
            if(mc.level==null||mc.level.getEntity(event.caster())==null)continue;
            if(event.skill().equals(SkillVisualRules.PLAYER_POSE)){
                if(t.poseOrbColor!=0&&mc.level.getEntity(event.caster()) instanceof Player player){
                    SunMoonSkillVisuals.handOrb(t,player,time,sprites,camera);
                }
                continue;
            }
            long age=time-event.startedAt();
            long maxLife=Math.max(t.maxDuration,Math.max(event.duration(),t.last.duration()))+500;
            if(age<0||!SkillVisualRegistry.handler(event.skill()).refreshable()&&age>maxLife&&(t.retirement<=0||time>t.retirement))continue;
            age=(long)(age*t.speed);
            var last=t.last;t.last=sample(t,time);
            try{SkillVisualRegistry.handler(event.skill()).render(context,t,age,time,quads,camera);AsuraVisuals.renderEyeBasic(t,age,sprites,camera);}finally{t.last=last;}
        }
        var afterimages=PlayerAfterimages.extract(time);
        context.levelState().setRenderData(FRAME,new Frame(List.copyOf(quads),List.copyOf(sprites),List.copyOf(context.swords()),afterimages,NearEffectRendering.enabled(levelEvent.getCamera())));
        geometryFrames++;
        geometryNanos+=System.nanoTime()-before;
        renderedQuads+=quads.size()+sprites.size();
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
            EffectModels.submit(sword.model(),pose,event.getSubmitNodeCollector(),0xF000F0,OverlayTexture.NO_OVERLAY,frame.nearFade());
            pose.popPose();
        }
        PlayerAfterimages.submit(event,frame.afterimages(),frame.nearFade());
        WorldQuads.submit(event,frame.quads(),frame.nearFade());
        EffectSprites.submit(event,frame.sprites(),frame.nearFade());
    }

    public CameraPose camera(){var track=tracks.get(cameraCast);return track==null?null:SkillVisualRegistry.handler(track.start.skill()).camera(track,now());}
    PlayerPoseChannel.Sample playerPose(UUID caster,long time){
        PlayerPoseChannel.Sample sample=null;
        long newest=Long.MIN_VALUE;
        for(var track:tracks.values()){
            if(!track.start.caster().equals(caster)||track.start.startedAt()<newest)continue;
            var candidate=PlayerPoseChannel.sample(track,time);
            if(candidate!=null){
                sample=candidate;
                newest=track.start.startedAt();
            }
        }
        return sample;
    }
    public record OffhandBlade(ItemStack item,float attack,float equip){}
    BuiltinSkillVisuals.OffhandSlash offhandSlash(UUID caster,long time){
        if(caster==null)return null;
        SkillVisualEvent latestOffhand=null;
        long latestOffhandAge=0;
        long latestMainStart=Long.MIN_VALUE;
        int activeOffhand=0;
        for(Track t:tracks.values()){
            var e=t.start;
            if(!e.caster().equals(caster)||!"ATTACK".equals(e.skill())||e.phase()!=SkillVisualEvent.Phase.START||!e.dual()||e.sweep())continue;
            long age=(long)((time-e.startedAt())*t.speed);
            if(age<0)continue;
            if(!BuiltinSkillVisuals.isDualOffhandStrike(e)){
                if(e.startedAt()>latestMainStart)latestMainStart=e.startedAt();
                continue;
            }
            if(BuiltinSkillVisuals.sampleOffhandSlash(e,age,false)==null)continue;
            activeOffhand++;
            if(latestOffhand==null||e.startedAt()>=latestOffhand.startedAt()){
                latestOffhand=e;
                latestOffhandAge=age;
            }
        }
        if(latestOffhand==null||latestMainStart>latestOffhand.startedAt())return null;
        return BuiltinSkillVisuals.sampleOffhandSlash(latestOffhand,latestOffhandAge,activeOffhand>1);
    }
    boolean hasActiveOffhandBlade(UUID caster,long time){
        return offhandSlash(caster,time)!=null;
    }
    public OffhandBlade offhandBlade(Player player){
        return offhandBlade(player,now());
    }
    private OffhandBlade offhandBlade(Player player,long time){
        if(player==null||!player.isAlive()||!player.getOffhandItem().isEmpty())return null;
        var sample=offhandSlash(player.getUUID(),time);
        if(sample==null)return null;
        return new OffhandBlade(BuiltinSkillVisuals.condensedBladeStack(player,sample.blade()),sample.swingProgress(),sample.equipOffset());
    }
    public void animate(Player player,EntityRenderState state){
        long time=now();
        var pose=playerPose(player.getUUID(),time);
        boolean broomGlide=pose==null&&WitchClient.isGlidingPlayer(player.getUUID());
        PlayerPoseAnimation.Frame glideFrame=null;
        if(broomGlide){
            var clip=PlayerPoseLibrary.find("avatar/witch_broom_glide");
            if(clip!=null)glideFrame=clip.frame(time,player.getUUID().hashCode());
        }
        if(state instanceof PlayerPoseCarrier carrier)carrier.ydnf$skillPose(pose!=null?pose.frame():glideFrame);
        if(state instanceof AvatarRenderState avatar){
            float head=avatar.bodyRot+avatar.yRot;
            for(var track:tracks.values())if(track.start.caster().equals(player.getUUID())){
                SkillVisualRegistry.handler(track.start.skill()).decorateAvatar(track,player,avatar,time);
            }
            var blade=offhandBlade(player,time);
            if(blade!=null)BuiltinSkillVisuals.decorateOffhandBlade(player,avatar,blade);
            if(glideFrame!=null){
                avatar.isFallFlying=false;
                avatar.isCrouching=false;
                avatar.walkAnimationSpeed=0;
                Vec3 vel=player.getDeltaMovement();
                Vec3 heading=vel.horizontalDistanceSqr()>0.01?vel:player.getLookAngle();
                avatar.bodyRot=PlayerPoseChannel.yaw(heading);
                avatar.yRot=net.minecraft.util.Mth.wrapDegrees(head-avatar.bodyRot);
                return;
            }
            if(pose==null)return;
            var aim=pose.lockedAim();
            avatar.bodyRot=aim==null?head:PlayerPoseChannel.yaw(aim);
            avatar.yRot=net.minecraft.util.Mth.wrapDegrees(head-avatar.bodyRot);
            avatar.attackTime=0;
            avatar.isCrouching=false;
            PlayerPoseChannel.heldModel(player,avatar,pose.frame());
        }
    }
}
