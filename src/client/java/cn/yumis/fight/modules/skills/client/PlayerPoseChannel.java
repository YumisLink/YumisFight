package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.skills.api.SkillCast;
import cn.yumis.fight.modules.skills.api.SkillVisualRules;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Bridges active skill tracks to sampled bone poses and applies them to vanilla humanoid models. */
public final class PlayerPoseChannel {
    private static final String AVATAR="avatar/";
    private static final long RECOVERY=350;
    private static final long MAX_CLIP=40000;
    private static final class Action {
        final SkillCast cast;
        long animationEnd=Long.MAX_VALUE, recoveryEnd=Long.MAX_VALUE;
        Action(SkillCast cast){this.cast=cast;}
        long readyAt(){return Math.min(animationEnd,recoveryEnd);}
    }
    private record Playing(SkillCast cast,String id,String clip,String next,long lead,long started,long duration,double rate,boolean follow,Vec3 aim){}
    public record Sample(PlayerPoseAnimation.Frame frame,Vec3 lockedAim){}
    private static Action action;
    private static Playing playing;
    /** Verification surface: last sampled/posed frame seen this session, read by the opt-in driver status. */
    public static volatile String debugSample,debugApplied;
    private PlayerPoseChannel(){
    }
    static float yaw(Vec3 aim){
        return (float)Math.toDegrees(Math.atan2(-aim.x,aim.z));
    }

    public static boolean coolingDown(long now){
        return action!=null&&now<action.readyAt();
    }
    public static boolean canYield(SkillCast cast,long now){
        return action!=null&&action.cast==cast&&action.recoveryEnd<Long.MAX_VALUE&&now>=action.readyAt();
    }
    public static void clear(){
        action=null;
        playing=null;
    }
    private static String avatarClip(String id){
        String variant=id.contains(":")?id.substring(id.indexOf(':')+1):id;
        String player=AVATAR+variant;
        return PlayerPoseLibrary.find(player)!=null?player:id;
    }
    public static long length(String id){
        var clip=PlayerPoseLibrary.find(avatarClip(id));
        return clip==null?0:clip.duration;
    }
    public static long scaled(double millis,double speed){
        return Math.max(1,Math.round(millis/Math.max(.01,speed)));
    }
    public static void begin(SkillCast cast){
        action=new Action(cast);
        String terminal=cast.skill.id+"_finish";
        String id=cast.terminal&&length(terminal)>0?terminal:cast.skill.id;
        long length=length(id);
        if(length>0)show(cast,id,scaled(length,cast.speed),cast.speed,cast.direction,true);
        else stop();
    }
    /** Operators with known phase durations use the same clock for pose sampling and input recovery. */
    public static void play(SkillCast cast,String id,long duration,Vec3 aim,boolean follow){
        playAt(cast,id,duration,aim,follow,ClientUi.now());
    }
    static void playAt(SkillCast cast,String id,long duration,Vec3 aim,boolean follow,long start){
        long length=length(id);
        if(length>0)show(cast,id,duration,length/(double)Math.max(1,duration),aim,follow,start);
    }
    public static void loop(SkillCast cast,String id,long duration,Vec3 aim,boolean follow){
        loop(cast,id,duration,aim,follow,cast.speed);
    }
    static void loop(SkillCast cast,String id,long duration,Vec3 aim,boolean follow,double speed){
        if(length(id)>0)show(cast,id,duration,speed,aim,follow);
    }
    private static void show(SkillCast cast,String id,long duration,double rate,Vec3 aim,boolean follow){
        show(cast,id,duration,rate,aim,follow,ClientUi.now());
    }
    private static void show(SkillCast cast,String id,long duration,double rate,Vec3 aim,boolean follow,long start){
        duration=Math.clamp(duration,1,MAX_CLIP);
        if(action!=null&&action.cast==cast){
            action.animationEnd=start+duration;
            action.recoveryEnd=Long.MAX_VALUE;
        }
        if(ClientUi.now()<start+duration)
            replace(new Playing(cast,UUID.randomUUID().toString(),avatarClip(id),"",0,start,duration,rate,follow,aim));
    }
    static void chant(SkillCast cast,long duration,long lead){
        String id=cast.skill.id+"_chant";
        if(length(id)==0)id="chant";
        long length=length(id);
        if(length==0)return;
        String next=cast.skill.id+"_prepare";
        long transition=length(next)>0?Math.min(lead,duration):0;
        replace(new Playing(cast,UUID.randomUUID().toString(),avatarClip(id),
            transition>0?avatarClip(next):"",transition,ClientUi.now(),Math.clamp(duration,1,MAX_CLIP),
            length/(double)Math.max(1,duration-transition),true,cast.direction));
    }
    public static boolean basic(Stats stats,boolean left,int strikes,Vec3 direction){
        String id=strikes>1?"attack_double":left?"attack_left":"attack";
        long length=length(id);
        if(length==0)return false;
        double speed=Math.max(.01,stats.get(Stat.CAST_SPEED));
        show(null,id,scaled(length,speed),speed,direction,false);
        return true;
    }
    public static void release(SkillCast cast){
        if(action!=null&&action.cast==cast&&action.recoveryEnd==Long.MAX_VALUE){
            double attackSpeed=Math.max(.01,1+cast.stats.get(Stat.ATTACK_SPEED)/100);
            action.recoveryEnd=ClientUi.now()+scaled(RECOVERY,attackSpeed);
        }
        lock(cast);
    }
    public static void lock(SkillCast cast){
        var active=playing;
        if(active==null||active.cast()!=cast||!active.follow()||ClientUi.now()>=active.started()+active.duration())return;
        active=new Playing(cast,active.id(),active.clip(),active.next(),active.lead(),active.started(),active.duration(),active.rate(),false,cast.direction);
        playing=active;
        publish(active,"RELEASE");
    }
    public static void cancel(SkillCast cast){
        if(action!=null&&action.cast==cast)action=null;
        if(playing!=null&&playing.cast()==cast)stop();
    }
    public static void cancelBasic(){
        if(playing!=null&&playing.cast()==null)stop();
    }
    private static void stop(){
        var previous=playing;
        playing=null;
        if(previous!=null&&ClientUi.now()<previous.started()+previous.duration())publish(previous,"CANCEL");
    }
    private static void replace(Playing next){
        stop();
        playing=next;
        publish(next,"START");
    }
    private static void publish(Playing clip,String phase){
        var player=Minecraft.getInstance().player;
        var state=SkillsClient.current();
        if(player==null||!state.ready()||state.own().dead)return;
        var cast=clip.cast();
        if(cast!=null&&(!state.session().equals(cast.session)||state.own().generation!=cast.generation
            ||!state.own().instance.equals(cast.instance)||!player.level().dimension().identifier().toString().equals(cast.dimension)))return;
        long now=state.serverNow();
        var data=new JsonObject();
        data.addProperty("kind",SkillVisualRules.PLAYER_POSE);
        data.addProperty("caster",player.getStringUUID());
        data.addProperty("session",state.session());
        data.addProperty("generation",state.own().generation);
        data.addProperty("instance",state.own().instance);
        data.addProperty("dimension",player.level().dimension().identifier().toString());
        data.addProperty("id",clip.id());
        data.addProperty("phase",phase);
        data.addProperty("sequence",phase.equals("START")?0:1);
        data.addProperty("seed",clip.id().hashCode());
        data.addProperty("startedAt",now-Math.max(0,ClientUi.now()-clip.started()));
        data.addProperty("at",now);
        data.addProperty("serverNow",now);
        data.addProperty("duration",clip.duration());
        data.addProperty("castSpeed",clip.rate());
        data.addProperty("cosmeticOnly",true);
        data.addProperty("poseClip",clip.clip());
        data.addProperty("poseNext",clip.next());
        data.addProperty("poseLead",clip.lead());
        data.addProperty("poseFollow",clip.follow());
        if(cast!=null)data.addProperty("poseOrb",SkillVisualRegistry.handler(cast.skill.id).handOrbColor());
        Vec3 origin=player.position();
        Vec3 dir=clip.aim()==null?player.getLookAngle():clip.aim();
        data.addProperty("x",origin.x);
        data.addProperty("y",origin.y);
        data.addProperty("z",origin.z);
        data.addProperty("dx",dir.x);
        data.addProperty("dy",dir.y);
        data.addProperty("dz",dir.z);
        Effects.receive(data);
        state.send("visual",data);
    }
    static Sample sample(SkillVfx.Track track,long time){
        long age=time-track.start.startedAt();
        if(track.poseClip.isEmpty()||age<0||age>=track.start.duration())return null;
        String id=track.poseClip;
        long transition=track.start.duration()-track.poseLead;
        var clip=PlayerPoseLibrary.find(id);
        if(clip==null)return null;
        long scaled=(long)(age*track.speed);
        if(!track.poseNext.isEmpty()&&track.poseLead>0&&age>=transition){
            id=track.poseNext;
            var next=PlayerPoseLibrary.find(id);
            if(next!=null){
                clip=next;
                scaled=Math.round((age-transition)*clip.duration/(double)track.poseLead);
            }
        }
        var frame=clip.frame(Math.min(scaled,clip.loop?Long.MAX_VALUE:clip.duration));
        // Stock documents also serve champions. Their independent torso and root channels
        // do not drive a player. Avatar-specific clips may move the entire model at the root.
        frame.mask&=~(1<<2);
        if(!id.startsWith(AVATAR))frame.mask&=~1;
        debugSample=id+" age="+age;
        Vec3 aim=SkillVfx.vector(track.last.direction());
        return new Sample(frame,track.poseFollow||aim.lengthSqr()<.0001?null:aim);
    }
    static void heldModel(Player player,AvatarRenderState state,PlayerPoseAnimation.Frame frame){
        if(frame.heldModel.isEmpty())return;
        var model=new ItemStack(Items.NETHERITE_SWORD);
        model.set(DataComponents.ITEM_MODEL,Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight",frame.heldModel));
        boolean left=player.getMainArm()==HumanoidArm.LEFT;
        Minecraft.getInstance().getItemModelResolver().updateForLiving(state.getMainHandItemState(),model,
            left?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,player);
        if(left)state.leftHandItemStack=model;
        else state.rightHandItemStack=model;
    }
    /** Null keeps the native pose; the clock matches the visual track age (startedAt + cast speed). */
    public static PlayerPoseAnimation.Frame pose(SkillVfx.Track track,long time){
        String id=SkillVisualRegistry.handler(track.start.skill()).poseAnimation(track,time);
        if(id==null)return null;
        PlayerPoseAnimation animation=PlayerPoseLibrary.find(id);
        if(animation==null)return null;
        long age=time-track.start.startedAt();
        if(age<0)return null;
        long scaled=(long)(age*track.speed);
        // Buff tracks outlive their one-shot document; once it ends the vanilla pose takes over again.
        if(!animation.loop&&scaled>=animation.duration)return null;
        PlayerPoseAnimation.Frame frame=animation.frame(scaled);
        debugSample=id+" age="+age;
        return frame;
    }
    /** Called at humanoid setupAnim tail: declared bones replace vanilla rotations, offsets add on top. */
    public static void apply(HumanoidModel<?> model,HumanoidRenderState state){
        if(!(state instanceof PlayerPoseCarrier carrier))return;
        PlayerPoseAnimation.Frame frame=carrier.ydnf$skillPose();
        if(frame==null)return;
        if(state instanceof AvatarRenderState)model.body.resetPose();
        debugApplied="mask="+frame.mask;
        ModelPart[] parts={model.root(),model.head,model.body,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg};
        for(int i=0;i<parts.length;i++){
            if((frame.mask&(1<<i))==0)continue;
            ModelPart part=parts[i];
            int base=i*PlayerPoseAnimation.AXES;
            part.xRot=(float)Math.toRadians(frame.values[base]);
            part.yRot=(float)Math.toRadians(frame.values[base+1]);
            part.zRot=(float)Math.toRadians(frame.values[base+2]);
            part.x+=frame.values[base+3];
            part.y+=frame.values[base+4];
            part.z+=frame.values[base+5];
        }
    }
}
