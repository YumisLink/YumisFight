package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillInputs;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import java.util.Locale;
import java.util.function.Consumer;

/** The owning client pays, times, cancels and releases its own chant. */
public final class ChantClient {
    private static SkillCast cast;
    private static Consumer<Boolean> release;
    private static int boundSlot = -1;
    private static boolean keyReleased;
    private static boolean trackedByKey;
    private static String session="",job="",instance="";
    private static long generation,start,until;
    private ChantClient(){}
    public static boolean active(){return cast!=null;}
    public static SkillDefinition skill(){return cast==null?null:cast.skill;}
    static SkillCast cast(){return cast;}
    static double progress(long now){return cast==null?0:Math.clamp((now-start)/(double)Math.max(1,until-start),0,1);}
    public static double radius(){return cast==null?0:cast.parameters.get("radius",0);}
    public static double range(){return cast==null?0:cast.parameters.get("range",16);}
    public static void begin(SkillCast source,Runnable completion){
        double seconds=SunMoonSkills.contains(source.skill.id)?SunMoonRules.chantSeconds(source.skill,source.stats):source.skill.chantSeconds;
        boolean astralStep=SunMoonSkills.contains(source.skill.id)&&cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUNMOON_ASTRAL_STEP.held(source.stats);
        double keepMove=astralStep?cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUNMOON_ASTRAL_STEP.ratio("move"):.2;
        begin(source,PlayerPoseChannel.scaled(seconds*1000,SunMoonRules.chantSpeed(source.stats)),false,keepMove,-1,released->completion.run());
    }
    static void begin(SkillCast source,long duration,boolean canMove,Runnable completion){
        begin(source,duration,canMove,canMove?1d:.2,-1,released->completion.run());
    }
    static void begin(SkillCast source,long duration,boolean canMove,int slot,Consumer<Boolean> completion){
        begin(source,duration,canMove,canMove?1d:.2,slot,completion);
    }
    static void begin(SkillCast source,long duration,boolean canMove,double moveKeepRatio,int slot,Consumer<Boolean> completion){
        clear();var state=SkillsClient.current();cast=source;cast.phase=cn.yumis.fight.modules.combat.api.ActionPhase.CHANTING;release=completion;
        boundSlot=slot;keyReleased=false;trackedByKey=slot>=0&&SkillInputs.SKILLS[slot%8].isDown();
        session=state.session();generation=state.own().generation;job=state.progress().jobId();instance=state.own().instance;
        start=ClientUi.now();
        until=start+Math.max(1L,duration);
        PlayerPoseChannel.chant(source,until-start,SunMoonSkills.contains(source.skill.id)?350:0);
        var p=Minecraft.getInstance().player;
        if(p!=null)ChantRules.movement(p,!canMove,moveKeepRatio);
    }
    public static boolean press(int slot){
        if(!active())return false;
        if(cast.skill.id.equals(SkillsClient.current().progress().slots[slot])){
            if(boundSlot>=0&&slot>=0&&slot%8==boundSlot%8){
                keyReleased=false;
                trackedByKey=SkillInputs.SKILLS[slot%8].isDown();
            }
            return true;
        }
        cancel();return false;
    }
    public static void released(int baseSlot){
        if(!active())return;
        if(boundSlot>=0&&boundSlot%8==baseSlot){
            keyReleased=true;
            trackedByKey=false;
        }
    }
    public static void cancel(){if(!active())return;var source=cast;source.phase=cn.yumis.fight.modules.combat.api.ActionPhase.ABANDONED;clear();PlayerPoseChannel.cancel(source);CareerClient.INSTANCE.cancel(source);}
    public static void reset(){clear();CoronaBlur.clear();}
    public static void clear(){var p=Minecraft.getInstance().player;if(p!=null)ChantRules.movement(p,false);cast=null;release=null;boundSlot=-1;keyReleased=false;trackedByKey=false;}
    public static void tick(){
        if(!active())return;var mc=Minecraft.getInstance();var state=SkillsClient.current();var p=mc.player;
        var hand=p==null?null:cn.yumis.fight.modules.equipment.api.EquipmentItems.view(p.getMainHandItem());
        if(p==null||mc.level==null||!state.ready()||state.own().dead||p.isDeadOrDying()||!state.session().equals(session)||state.own().generation!=generation||!state.own().instance.equals(instance)||!state.progress().jobId().equals(job)||!mc.level.dimension().identifier().toString().equals(cast.dimension)||hand==null||!hand.instance().toString().equals(cast.weapon)||cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(p)||cn.yumis.fight.modules.dungeons.api.client.SealBossState.isFrozen()){cancel();return;}
        if(trackedByKey&&boundSlot>=0&&!SkillInputs.SKILLS[boundSlot%8].isDown()){
            keyReleased=true;
            trackedByKey=false;
        }
        if(ClientUi.now()<until)return;
        var source=cast;var completion=release;boolean released=keyReleased;clear();
        source.start=source.stageStart=ClientUi.now();
        Vec3 look=p.getLookAngle();
        source.direction=look.lengthSqr()<1e-6?new Vec3(0,0,1):look.normalize();
        completion.accept(released);
    }
    public static void render(GuiGraphicsExtractor g,int width,int height){
        if(!active())return;long now=ClientUi.now();double progress=progress(now);
        String title=ElementalistSkills.contains(cast.skill.id)?ElementalistRules.skillTitle(cast.skill,cast.parameters):cast.skill.title;
        String label=title+" · 咏唱 "+String.format(Locale.ROOT,"%.1f",Math.max(0,until-now)/1000d)+"秒";
        SkillCastBar.render(g,width,height,label,progress,cast.skill.aspect==SkillDefinition.Aspect.SUN?0xffffb35d:0xffb2ddff);
    }
}
