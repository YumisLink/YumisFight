package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.equipment.api.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Disposable prediction of server-approved movement; damage uses the existing contact receipt client. */
public final class DemonHunterClient {
    private static JsonObject motion;private static DemonHunterChoreography.Scene dance;private static int danceIndex,danceStage=-1;private static long last;private static boolean previousGravity,gravityHeld,chargeStopped;
    private DemonHunterClient(){}
    private static SkillsClient state(){return SkillsClient.current();}
    public static void registerResource(){
        cn.yumis.fight.modules.skills.api.client.PrimaryResource.register(cn.yumis.fight.modules.character.api.JobCatalog.DEMON_HUNTER,
            ()->new cn.yumis.fight.modules.skills.api.client.PrimaryResource.View("怒气",CareerClient.INSTANCE.view.demonHunter.fury,DemonHunterRules.MAX_FURY,DemonHunterRules.COLOR,
                List.of("攻击命中及眼棱引导产生怒气，技能按各自说明消耗怒气。","脱战后每秒减少10点；怒气上限100，不受魔力与魔力恢复属性影响。")));
    }
    public static boolean active(){return motion!=null;}
    public static boolean blocksMovement(){return motion!=null&&!skill().equals(DemonHunterSkills.CHAOS);}
    public static boolean invulnerable(){return motion!=null&&(skill().equals(DemonHunterSkills.DANCE)||skill().equals(DemonHunterSkills.META)||motion.get("phase").getAsString().equals("COMBO"));}
    private static boolean beamActive(){return contextValid()&&skill().equals(DemonHunterSkills.BEAM)&&state().serverNow()<motion.get("at").getAsLong()+DemonHunterChoreography.BEAM_DURATION;}
    public static long beamAge(){return beamActive()?state().serverNow()-motion.get("at").getAsLong():-1;}
    public static double careerDamageTaken(double normal){return beamActive()?.20:normal;}
    public static double danceAge(UUID cast){return contextValid()&&skill().equals(DemonHunterSkills.DANCE)&&motion.get("id").getAsString().equals(cast.toString())?state().serverNow()-motion.get("at").getAsLong():-1;}
    public static double huntComboAge(UUID cast){return contextValid()&&skill().equals(DemonHunterSkills.HUNT)&&motion.get("phase").getAsString().equals("COMBO")&&motion.get("id").getAsString().equals(cast.toString())?state().serverNow()-motion.get("at").getAsLong():-1;}
    public static boolean charging(UUID cast){return contextValid()&&!chargeStopped&&skill().equals(DemonHunterSkills.HUNT)&&motion.get("phase").getAsString().equals("CHARGE")&&motion.get("id").getAsString().equals(cast.toString());}
    private static String skill(){return motion==null?"":motion.get("skill").getAsString();}
    public static boolean sight(){var mc=Minecraft.getInstance();return mc.player!=null&&state().ready()&&!state().own().dead&&DemonHunterRules.sight(state().stats(),state().progress());}
    public static boolean beforeRequest(SkillDefinition skill){
        if(motion==null)return true;
        if(!skill().equals(DemonHunterSkills.BEAM))return false;if(skill.id.equals(DemonHunterSkills.BEAM))return false;cancelChannel();return true;
    }
    public static boolean cancelChannel(){if(motion==null||!skill().equals(DemonHunterSkills.BEAM))return false;var j=new JsonObject();j.addProperty("id",motion.get("id").getAsString());j.addProperty("session",state().session());ClientUi.current().send("demonCancel",j);clear();return true;}
    public static void receive(JsonObject value){
        if(!state().ready()||!state().session().equals(value.get("session").getAsString())||state().own().generation!=value.get("generation").getAsLong())return;
        if(value.get("skill").getAsString().equals(DemonHunterSkills.AURA))return;
        if(value.get("phase").getAsString().equals("END")){if(motion!=null&&motion.get("id").getAsString().equals(value.get("id").getAsString())){var p=Minecraft.getInstance().player;if(p!=null&&!skill().equals(DemonHunterSkills.HUNT))p.setPos(vector(value.getAsJsonArray("to")));clear();}return;}
        var p=Minecraft.getInstance().player;
        if(motion==null||!motion.get("id").getAsString().equals(value.get("id").getAsString())){clear();cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.cancelBasicAttacks();previousGravity=value.get("previousGravity").getAsBoolean();}
        if(p!=null&&motion!=null&&motion.get("phase").getAsString().equals("CHARGE"))p.setDeltaMovement(Vec3.ZERO);
        motion=value.deepCopy();last=state().serverNow();
        dance=value.has("dance")?DemonHunterChoreography.Scene.read(value.getAsJsonObject("dance")):null;danceIndex=0;danceStage=-1;chargeStopped=false;
        if(p!=null&&gravity()){gravityHeld=true;p.setNoGravity(true);}
    }
    private static boolean gravity(){return motion!=null&&Set.of(DemonHunterSkills.BEAM,DemonHunterSkills.DANCE,DemonHunterSkills.META,DemonHunterSkills.RUSH,DemonHunterSkills.HUNT).contains(skill());}
    public static void clear(){var p=Minecraft.getInstance().player;if(motion!=null&&skill().equals(DemonHunterSkills.DANCE))Effects.VFX.completeCamera(UUID.fromString(motion.get("id").getAsString()));if(p!=null&&gravityHeld){p.setDeltaMovement(Vec3.ZERO);p.setNoGravity(previousGravity);p.fallDistance=0;}motion=null;dance=null;danceIndex=0;danceStage=-1;last=0;gravityHeld=false;chargeStopped=false;}
    private static void dancePosition(Vec3 desired){var p=Minecraft.getInstance().player;p.setPos(DemonHunterChoreography.safePosition(p,p.position(),desired));}
    private static void danceMovement(long age){
        var timing=dance.timing();
        while(danceIndex<timing.slashes()&&age>=timing.slashHitAt(danceIndex)){
            var path=dance.paths().get(danceIndex);if(danceStage!=danceIndex)dancePosition(path.from());dancePosition(path.to());danceStage=danceIndex++;
        }
        int active=timing.slashIndex(age);
        if(active>=0){if(danceStage!=active){dancePosition(dance.paths().get(active).from());danceStage=active;}dancePosition(dance.point(age));}
        else if(age>=timing.fallAt()){if(danceStage!=timing.slashes()){dancePosition(dance.fall().from());danceStage=timing.slashes();}dancePosition(dance.point(age));}
    }
    private static boolean contextValid(){
        var mc=Minecraft.getInstance();var p=mc.player;
        return motion!=null&&p!=null&&mc.level!=null&&state().ready()&&!state().own().dead&&!p.isDeadOrDying()&&state().session().equals(motion.get("session").getAsString())&&state().own().generation==motion.get("generation").getAsLong()&&state().own().instance.equals(motion.get("instance").getAsString())&&state().progress().jobId().equals(motion.get("job").getAsString())&&mc.level.dimension().identifier().toString().equals(motion.get("dimension").getAsString());
    }
    /** Called immediately before native travel. Native movement and its normal packet run exactly once. */
    public static Vec3 chargeVelocity(){
        if(motion==null||!motion.get("phase").getAsString().equals("CHARGE"))return null;
        var p=Minecraft.getInstance().player;
        if(!contextValid()){clear();return null;}
        if(chargeStopped||state().serverNow()>=motion.get("at").getAsLong()+motion.get("duration").getAsLong()){
            if(!chargeStopped){p.setDeltaMovement(Vec3.ZERO);p.setNoGravity(previousGravity);gravityHeld=false;chargeStopped=true;}
            return null;
        }
        p.stopFallFlying();p.fallDistance=0;return p.getLookAngle().normalize().scale(DemonHunterChoreography.HUNT_SPEED/20);
    }
    public static void tick(){
        if(motion==null)return;var mc=Minecraft.getInstance();var p=mc.player;if(!contextValid()){clear();return;}
        long now=state().serverNow(),at=motion.get("at").getAsLong(),duration=motion.get("duration").getAsLong();if(now>at+duration+1800){clear();return;}double dt=Math.clamp((now-last)/1000d,0,.2);last=now;
        String phase=motion.get("phase").getAsString();
        if(phase.equals("BEAM")){Vec3 from=vector(motion.getAsJsonArray("from")),to=vector(motion.getAsJsonArray("to"));p.move(MoverType.SELF,from.lerp(to,Math.clamp((now-at)/(double)DemonHunterChoreography.BEAM_RISE,0,1)).subtract(p.position()));p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;if(now>=at+DemonHunterChoreography.BEAM_DURATION)clear();return;}
        if(skill().equals(DemonHunterSkills.CHAOS))return;
        if(phase.equals("CHARGE")){chargeVelocity();return;}
        else if(dance!=null){danceMovement(now-at);if(now>=at+dance.timing().duration()){clear();return;}}
        else if(phase.equals("COMBO")){var entity=mc.level.getEntity(motion.get("targetEntity").getAsInt());if(entity!=null&&entity.getStringUUID().equals(motion.get("target").getAsString())){Vec3 delta=entity.position().subtract(p.position());if(delta.length()>2)p.move(MoverType.SELF,delta.normalize().scale(Math.min(delta.length()-2,10*dt)));}}
        else {Vec3 from=vector(motion.getAsJsonArray("from")),to=vector(motion.getAsJsonArray("to"));double u=Math.clamp((now-at)/(double)duration,0,1),arc=motion.get("arc").getAsDouble();Vec3 desired=from.lerp(to,u).add(0,4*arc*u*(1-u),0);p.move(MoverType.SELF,desired.subtract(p.position()));}
        p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
    }
    public static int hud(GuiGraphicsExtractor g,int x,int y){
        if(!state().progress().baseJob().equals(cn.yumis.fight.modules.character.api.JobCatalog.DEMON_HUNTER))return y;
        x=Math.clamp(x,80,g.guiWidth()-78);var s=CareerClient.INSTANCE.view.demonHunter;long now=state().serverNow();var font=UiFonts.font();
        if(s.auraUntil>now){CareerHud.timer(g,SkillCatalog.require(DemonHunterSkills.AURA),x,y,s.auraUntil-now,6000,0xffb3fa76);y+=68;}
        if(s.metamorphosisUntil>now){g.text(font,"恶魔变形 "+NumberText.number((s.metamorphosisUntil-now)/1000d)+"秒",x-69,y,0xffd5ff9c,true);y+=18;}
        long beamAge=beamAge();if(beamAge>=0){long left=Math.max(0,DemonHunterChoreography.BEAM_DURATION-beamAge);SkillCastBar.render(g,g.guiWidth(),g.guiHeight(),"眼棱 · 引导 "+String.format(Locale.ROOT,"%.1f",left/1000d)+"秒",left/(double)DemonHunterChoreography.BEAM_DURATION,0xffb3fa76);}return y;
    }
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
}
