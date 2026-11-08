package cn.yumis.fight.modules.skills.api.client;

import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.modules.skills.api.Stamina;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/** Local stamina, input, native velocity and fall protection. */
public final class CloudMovement {
    private static Stamina pool=new Stamina();
    private static long fullAt,generation=-1;
    private static String session="",dimension="";
    private static boolean uiOpen;
    private CloudMovement(){}
    public static void clear(){pool=new Stamina();fullAt=0;session=dimension="";generation=-1;uiOpen=false;}
    public static int stamina(){return pool.value;}
    public static void screen(boolean open){uiOpen=open;}
    public static void tick(){
        var state=SkillsClient.current();var p=Minecraft.getInstance().player;if(!state.ready()||p==null)return;
        String current=p.level().dimension().identifier().toString();
        if(!session.equals(state.session())||generation!=state.own().generation||!dimension.equals(current)){clear();session=state.session();generation=state.own().generation;dimension=current;}
        uiOpen=Minecraft.getInstance().gui.screen()!=null;int previous=pool.value;
        pool.recover(p.tickCount,p.onGround(),p.isCreative()||state.resources().training);
        if(previous<Stamina.MAXIMUM&&pool.value==Stamina.MAXIMUM)fullAt=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
    }
    public static boolean key(KeyEvent event,int action){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.gui.screen()!=null||com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(),GLFW.GLFW_KEY_F3))return false;
        if(!cn.yumis.fight.modules.skills.api.client.SkillInputs.LEAP.matches(event))return false;
        cn.yumis.fight.modules.skills.api.client.SkillInputs.LEAP.setDown(action!=GLFW.GLFW_RELEASE);
        if(action!=GLFW.GLFW_PRESS)return true;
        var state=SkillsClient.current();
        if(!state.ready()||mc.player==null||mc.level==null||mc.gui.screen()!=null||SkillController.INSTANCE.locked()
            ||cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(mc.player)||!mc.player.isAlive()||mc.player.isPassenger()||mc.player.isSleeping()
            ||mc.player.isSpectator()||mc.player.getAbilities().flying)return true;
        if(uiOpen||cn.yumis.fight.modules.skills.client.DemonHunterClient.blocksMovement()||cn.yumis.fight.modules.skills.client.OracleClient.INSTANCE.locked())return true;
        if(!pool.ready(mc.player.tickCount))return true;
        if(!pool.use(mc.player.tickCount,mc.player.isCreative()||state.resources().training)){state.message("体力不足（需要20）");return true;}
        double yaw=Math.toRadians(mc.player.getYRot());
        var velocity=mc.options.keyJump.isDown()?new net.minecraft.world.phys.Vec3(0,1.1,0):new net.minecraft.world.phys.Vec3(-Math.sin(yaw)*1.08,.49,Math.cos(yaw)*1.08);
        mc.player.stopFallFlying();mc.player.setDeltaMovement(velocity);mc.player.setOnGround(false);mc.player.resetFallDistance();
        mc.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(cn.yumis.fight.modules.combat.api.CombatContentTypes.CLOUD_STEP,Stamina.PROTECTION,0,false,false,true));
        return true;
    }
    public static void render(GuiGraphicsExtractor g,DeltaTracker delta){
        var mc=Minecraft.getInstance();
        if(!SkillsClient.current().ready()||mc.player==null||mc.gui.hud.isHidden()||mc.gui.screen()!=null&&!(mc.gui.screen() instanceof ChatScreen))return;
        if(pool.value==Stamina.MAXIMUM&&(fullAt==0||cn.yumis.fight.modules.presentation.api.client.ClientUi.now()-fullAt>2000))return;
        int extra=(mc.player.isUnderWater()||mc.player.getAirSupply()<mc.player.getMaxAirSupply()?10:0)+(mc.player.isPassenger()?10:0);
        int x=g.guiWidth()/2-43,y=Math.max(4,g.guiHeight()-111-extra),width=86;
        g.fill(x,y,x+width,y+11,UiTheme.PANEL);
        g.fill(x+1,y+1,x+1+(width-2)*pool.value/Stamina.MAXIMUM,y+10,0xff69a879);
        g.text(UiFonts.font(),"体力 "+pool.value+" / 100",x+3,y+1,UiTheme.TEXT,false);
    }
}
