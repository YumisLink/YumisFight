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
    private static JsonObject motion;private static long last;private static boolean previousGravity;
    private DemonHunterClient(){}
    private static SkillsClient state(){return SkillsClient.current();}
    public static void registerResource(){
        cn.yumis.fight.modules.skills.api.client.PrimaryResource.register(cn.yumis.fight.modules.character.api.JobCatalog.DEMON_HUNTER,
            ()->new cn.yumis.fight.modules.skills.api.client.PrimaryResource.View("怒气",CareerClient.INSTANCE.view.demonHunter.fury,DemonHunterRules.MAX_FURY,DemonHunterRules.COLOR,
                List.of("命中产生怒气，技能按各自说明消耗怒气。","脱战后每秒减少10点；怒气上限100，不受魔力与魔力恢复属性影响。")));
    }
    public static boolean active(){return motion!=null;}
    public static boolean blocksMovement(){return motion!=null&&!skill().equals(DemonHunterSkills.CHAOS);}
    public static boolean invulnerable(){return motion!=null&&(skill().equals(DemonHunterSkills.DANCE)||skill().equals(DemonHunterSkills.META)||motion.get("phase").getAsString().equals("COMBO"));}
    private static String skill(){return motion==null?"":motion.get("skill").getAsString();}
    public static boolean sight(){var mc=Minecraft.getInstance();return mc.player!=null&&state().ready()&&!state().own().dead&&DemonHunterRules.sight(state().stats(),state().progress());}
    public static boolean beforeRequest(SkillDefinition skill){
        var p=Minecraft.getInstance().player;if(motion==null){if(p!=null)previousGravity=p.isNoGravity();return true;}
        if(!skill().equals(DemonHunterSkills.BEAM))return false;if(skill.id.equals(DemonHunterSkills.BEAM))return false;cancelChannel();return true;
    }
    public static boolean cancelChannel(){if(motion==null||!skill().equals(DemonHunterSkills.BEAM))return false;var j=new JsonObject();j.addProperty("id",motion.get("id").getAsString());j.addProperty("session",state().session());ClientUi.current().send("demonCancel",j);clear();return true;}
    public static void receive(JsonObject value){
        if(!state().ready()||!state().session().equals(value.get("session").getAsString())||state().own().generation!=value.get("generation").getAsLong())return;
        if(value.get("skill").getAsString().equals(DemonHunterSkills.AURA))return;
        if(value.get("phase").getAsString().equals("END")){if(motion!=null&&motion.get("id").getAsString().equals(value.get("id").getAsString())){var p=Minecraft.getInstance().player;if(p!=null)p.setPos(vector(value.getAsJsonArray("to")));clear();}return;}
        motion=value.deepCopy();last=state().serverNow();
        var p=Minecraft.getInstance().player;if(p!=null&&gravity())p.setNoGravity(true);
    }
    private static boolean gravity(){return motion!=null&&Set.of(DemonHunterSkills.DANCE,DemonHunterSkills.META,DemonHunterSkills.RUSH,DemonHunterSkills.HUNT).contains(skill());}
    public static void clear(){var p=Minecraft.getInstance().player;if(p!=null&&gravity()){p.setNoGravity(previousGravity);p.fallDistance=0;}motion=null;last=0;}
    public static void tick(){
        if(motion==null)return;var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.level==null||!state().ready()||state().own().dead||!state().session().equals(motion.get("session").getAsString())||state().own().generation!=motion.get("generation").getAsLong()||!state().own().instance.equals(motion.get("instance").getAsString())||!state().progress().jobId().equals(motion.get("job").getAsString())||!mc.level.dimension().identifier().toString().equals(motion.get("dimension").getAsString())){clear();return;}
        long now=state().serverNow(),at=motion.get("at").getAsLong(),duration=motion.get("duration").getAsLong();if(now>at+duration+1800){clear();return;}double dt=Math.clamp((now-last)/1000d,0,.2);last=now;
        String phase=motion.get("phase").getAsString();
        if(phase.equals("BEAM")){var v=p.getDeltaMovement();p.setDeltaMovement(0,v.y,0);return;}
        if(skill().equals(DemonHunterSkills.CHAOS))return;
        if(phase.equals("CHARGE")){p.move(MoverType.SELF,p.getLookAngle().normalize().scale(10*dt));}
        else if(phase.equals("COMBO")){var entity=mc.level.getEntity(motion.get("targetEntity").getAsInt());if(entity!=null&&entity.getStringUUID().equals(motion.get("target").getAsString())){Vec3 delta=entity.position().subtract(p.position());if(delta.length()>2)p.move(MoverType.SELF,delta.normalize().scale(Math.min(delta.length()-2,10*dt)));}}
        else {Vec3 from=vector(motion.getAsJsonArray("from")),to=vector(motion.getAsJsonArray("to"));double u=Math.clamp((now-at)/(double)duration,0,1),arc=motion.get("arc").getAsDouble();Vec3 desired=from.lerp(to,u).add(0,4*arc*u*(1-u),0);p.move(MoverType.SELF,desired.subtract(p.position()));}
        p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
    }
    public static int hud(GuiGraphicsExtractor g,int x,int y){
        if(!state().progress().baseJob().equals(cn.yumis.fight.modules.character.api.JobCatalog.DEMON_HUNTER))return y;
        x=Math.clamp(x,80,g.guiWidth()-78);var s=CareerClient.INSTANCE.view.demonHunter;long now=state().serverNow();var font=UiFonts.font();
        if(s.auraUntil>now){g.text(font,"献祭光环 "+NumberText.number((s.auraUntil-now)/1000d)+"秒",x-69,y,0xffb3fa76,true);y+=18;}
        if(s.metamorphosisUntil>now){g.text(font,"恶魔变形 "+NumberText.number((s.metamorphosisUntil-now)/1000d)+"秒",x-69,y,0xffd5ff9c,true);y+=18;}
        if(motion!=null&&skill().equals(DemonHunterSkills.BEAM)){g.text(font,"眼棱 "+NumberText.number(Math.max(0,5000-(now-motion.get("at").getAsLong()))/1000d)+"秒",x-69,y,0xffceff83,true);y+=18;}return y;
    }
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
}
