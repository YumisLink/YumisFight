package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.presentation.api.client.*;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.*;

/** A confirmed chant only controls presentation/input; the server releases its effect. */
public final class ChantClient {
    private static String id="",session="",dimension="",job="",weapon="";
    private static long generation,start,until;
    private static SkillDefinition skill;private static double radius,range;
    private static final Set<String> closed=new LinkedHashSet<>();
    private ChantClient(){}
    public static boolean active(){return !id.isEmpty();}
    public static SkillDefinition skill(){return skill;}
    public static double radius(){return radius;}
    public static double range(){return range;}
    public static void begin(JsonObject ack){
        if(!ack.has("chantUntil")||closed.contains(ack.get("id").getAsString()))return;clear();var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        id=ack.get("id").getAsString();session=ack.get("session").getAsString();generation=ack.get("generation").getAsLong();skill=SkillCatalog.require(ack.get("skill").getAsString());
        start=ClientUi.now();until=start+Math.max(1,ack.get("chantUntil").getAsLong()-ack.get("serverNow").getAsLong());dimension=mc.level.dimension().identifier().toString();job=SkillsClient.current().progress().jobId();
        weapon=ack.getAsJsonObject("stats").has("weaponInstance")?ack.getAsJsonObject("stats").get("weaponInstance").getAsString():"";
        var parameters=ack.getAsJsonObject("parameters");radius=parameters.has("radius")?parameters.get("radius").getAsDouble():0;range=parameters.has("range")?parameters.get("range").getAsDouble():16;
        ChantRules.movement(mc.player,true);
    }
    public static void state(JsonObject message){
        var state=SkillsClient.current();if(!message.get("session").getAsString().equals(state.session())||message.get("generation").getAsLong()!=state.own().generation)return;
        String finished=message.get("id").getAsString();closed.add(finished);if(closed.size()>128)closed.remove(closed.iterator().next());if(id.equals(finished))clear();
    }
    public static boolean press(int pressedSlot){if(!active())return false;if(skill.id.equals(SkillsClient.current().progress().slots[pressedSlot]))return true;cancel();return false;}
    public static void cancel(){if(!active())return;var request=new JsonObject();request.addProperty("id",id);request.addProperty("session",session);request.addProperty("generation",generation);closed.add(id);SkillsClient.current().send("chantCancel",request);clear();}
    public static void reset(){clear();closed.clear();CoronaBlur.clear();}
    public static void clear(){var player=Minecraft.getInstance().player;if(player!=null)ChantRules.movement(player,false);id="";skill=null;}
    public static void tick(){
        if(!active())return;var mc=Minecraft.getInstance();var state=SkillsClient.current();
        if(mc.player==null||mc.level==null){clear();return;}
        String held=cn.yumis.fight.modules.equipment.api.EquipmentItems.view(mc.player.getMainHandItem())==null?"":cn.yumis.fight.modules.equipment.api.EquipmentItems.view(mc.player.getMainHandItem()).instance().toString();
        if(!state.session().equals(session)||state.own().generation!=generation||state.own().dead||!state.progress().jobId().equals(job)||!mc.level.dimension().identifier().toString().equals(dimension)||!held.equals(weapon)||mc.player.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN)||ClientUi.now()>until+3000)cancel();
    }
    public static void render(GuiGraphicsExtractor g,int width,int height){
        if(!active()||skill==null)return;var player=Minecraft.getInstance().player;
        int extra=player==null?0:(player.isUnderWater()||player.getAirSupply()<player.getMaxAirSupply()?10:0)+(player.isPassenger()?10:0);
        int w=Math.min(170,width-16),x=(width-w)/2,y=Math.max(6,height-162-extra);double progress=Math.clamp((ClientUi.now()-start)/(double)Math.max(1,until-start),0,1);
        UiTheme.hud(g,x,y,w,23);g.fill(x+4,y+17,x+w-4,y+20,0x80485870);g.fill(x+4,y+17,x+4+(int)((w-8)*progress),y+20,skill.aspect==SkillDefinition.Aspect.SUN?0xffffb35d:0xffb2ddff);
        String label=skill.title+" · 咏唱 "+String.format(Locale.ROOT,"%.1f",Math.max(0,until-ClientUi.now())/1000d)+"秒";g.text(UiFonts.font(),label,x+(w-UiFonts.font().width(label))/2,y+4,UiTheme.HUD_TEXT,true);
    }
}
