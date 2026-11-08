package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.Locale;

/** The owning client pays, times, cancels and releases its own chant. */
public final class ChantClient {
    private static SkillCast cast;
    private static Runnable release;
    private static String session="",job="",instance="";
    private static long generation,start,until;
    private ChantClient(){}
    public static boolean active(){return cast!=null;}
    public static SkillDefinition skill(){return cast==null?null:cast.skill;}
    public static double radius(){return cast==null?0:cast.parameters.get("radius",0);}
    public static double range(){return cast==null?0:cast.parameters.get("range",16);}
    public static void begin(SkillCast source,Runnable completion){
        clear();var state=SkillsClient.current();cast=source;release=completion;session=state.session();generation=state.own().generation;job=state.progress().jobId();instance=state.own().instance;
        start=ClientUi.now();until=start+Math.max(1,(long)(source.skill.chantSeconds*1000/source.speed));ChantRules.movement(Minecraft.getInstance().player,true);
    }
    public static boolean press(int slot){if(!active())return false;if(cast.skill.id.equals(SkillsClient.current().progress().slots[slot]))return true;cancel();return false;}
    public static void cancel(){if(!active())return;var source=cast;clear();CareerClient.INSTANCE.cancel(source);}
    public static void reset(){clear();CoronaBlur.clear();}
    public static void clear(){var p=Minecraft.getInstance().player;if(p!=null)ChantRules.movement(p,false);cast=null;release=null;}
    public static void tick(){
        if(!active())return;var mc=Minecraft.getInstance();var state=SkillsClient.current();var p=mc.player;
        var hand=p==null?null:cn.yumis.fight.modules.equipment.api.EquipmentItems.view(p.getMainHandItem());
        if(p==null||mc.level==null||!state.ready()||state.own().dead||!state.session().equals(session)||state.own().generation!=generation||!state.own().instance.equals(instance)||!state.progress().jobId().equals(job)||!mc.level.dimension().identifier().toString().equals(cast.dimension)||hand==null||!hand.instance().toString().equals(cast.weapon)||cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(p)){cancel();return;}
        if(ClientUi.now()<until)return;
        var source=cast;var completion=release;clear();source.start=source.stageStart=ClientUi.now();source.direction=p.getLookAngle().normalize();completion.run();
    }
    public static void render(GuiGraphicsExtractor g,int width,int height){
        if(!active())return;double progress=Math.clamp((ClientUi.now()-start)/(double)Math.max(1,until-start),0,1);
        String label=cast.skill.title+" · 咏唱 "+String.format(Locale.ROOT,"%.1f",Math.max(0,until-ClientUi.now())/1000d)+"秒";
        SkillCastBar.render(g,width,height,label,progress,cast.skill.aspect==SkillDefinition.Aspect.SUN?0xffffb35d:0xffb2ddff);
    }
}
