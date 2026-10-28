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

/** Display-only movement resource. No client resource value is ever sent to the server. */
public final class CloudMovement {
    private static int stamina=Stamina.MAXIMUM;
    private static long fullAt;
    private static Boolean lastOpen;
    private static String lastSession="";
    private static long lastGeneration=-1;
    private CloudMovement(){}
    public static void clear(){stamina=Stamina.MAXIMUM;fullAt=0;lastOpen=null;lastSession="";lastGeneration=-1;}
    public static int stamina(){return stamina;}
    private static JsonObject intent(){
        var state=SkillsClient.current();var j=state.operation("cloudLeap");
        j.addProperty("session",state.session());j.addProperty("generation",state.own().generation);
        return j;
    }
    public static void screen(boolean open){
        if(!SkillsClient.current().ready())return;
        var j=intent();j.addProperty("open",open);SkillsClient.current().send("movementUi",j);
        lastOpen=open;lastSession=SkillsClient.current().session();lastGeneration=SkillsClient.current().own().generation;
    }
    public static void tick(){
        var state=SkillsClient.current();
        if(!state.ready())return;
        boolean open=Minecraft.getInstance().gui.screen()!=null;
        // ScreenEvent.Opening is deliberately not fired when Gui.setScreen(null) closes a screen.
        if(lastOpen==null||lastOpen!=open||!lastSession.equals(state.session())||lastGeneration!=state.own().generation)screen(open);
    }
    public static void receive(JsonObject j){
        var state=SkillsClient.current();
        if(!j.get("session").getAsString().equals(state.session())||j.get("generation").getAsLong()!=state.own().generation)return;
        int next=Math.clamp(j.get("value").getAsInt(),0,Stamina.MAXIMUM);
        if(next==Stamina.MAXIMUM&&stamina<Stamina.MAXIMUM)fullAt=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        stamina=next;
    }
    public static boolean key(KeyEvent event,int action){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.gui.screen()!=null||com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(),GLFW.GLFW_KEY_F3))return false;
        if(!cn.yumis.fight.modules.skills.api.client.SkillInputs.LEAP.matches(event))return false;
        cn.yumis.fight.modules.skills.api.client.SkillInputs.LEAP.setDown(action!=GLFW.GLFW_RELEASE);
        if(action!=GLFW.GLFW_PRESS)return true;
        var state=SkillsClient.current();
        if(!state.ready()||mc.player==null||mc.level==null||mc.gui.screen()!=null||SkillController.INSTANCE.locked()
            ||mc.player.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN)||!mc.player.isAlive()||mc.player.isPassenger()||mc.player.isSleeping()
            ||mc.player.isSpectator()||mc.player.getAbilities().flying)return true;
        var j=intent();j.addProperty("vertical",mc.options.keyJump.isDown());
        j.addProperty("dimension",mc.level.dimension().identifier().toString());
        state.send("cloudLeap",j);
        return true;
    }
    public static void render(GuiGraphicsExtractor g,DeltaTracker delta){
        var mc=Minecraft.getInstance();
        if(!SkillsClient.current().ready()||mc.player==null||mc.gui.hud.isHidden()||mc.gui.screen()!=null&&!(mc.gui.screen() instanceof ChatScreen))return;
        if(stamina==Stamina.MAXIMUM&&(fullAt==0||cn.yumis.fight.modules.presentation.api.client.ClientUi.now()-fullAt>2000))return;
        int extra=(mc.player.isUnderWater()||mc.player.getAirSupply()<mc.player.getMaxAirSupply()?10:0)+(mc.player.isPassenger()?10:0);
        int x=g.guiWidth()/2-43,y=Math.max(4,g.guiHeight()-111-extra),width=86;
        g.fill(x,y,x+width,y+11,UiTheme.PANEL);
        g.fill(x+1,y+1,x+1+(width-2)*stamina/Stamina.MAXIMUM,y+10,0xff69a879);
        g.text(UiFonts.font(),"体力 "+stamina+" / 100",x+3,y+1,UiTheme.TEXT,false);
    }
}
