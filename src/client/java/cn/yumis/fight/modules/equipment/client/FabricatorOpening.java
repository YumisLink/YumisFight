package cn.yumis.fight.modules.equipment.client;
import cn.yumis.fight.modules.equipment.api.client.EquipmentClient;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ScreenEvent;
/** The menu may receive packets while its screen waits for definitions and initial contents. */
public final class FabricatorOpening {
    private static FabricatorScreen pending;private static long started;
    private FabricatorOpening(){}
    public static void opening(ScreenEvent.Opening event){
        if(event.getNewScreen() instanceof FabricatorScreen screen&&(!EquipmentClient.current().ready()||!screen.getMenu().initialContentsReady)){
            pending=screen;started=net.minecraft.util.Util.getMillis();event.setCanceled(true);
            if(!EquipmentClient.current().ready())EquipmentClient.current().send("profile");
        }
    }
    public static void tick(){
        if(pending==null)return;var mc=Minecraft.getInstance();
        if(mc.player==null||mc.player.containerMenu!=pending.getMenu()){pending=null;return;}
        if(net.minecraft.util.Util.getMillis()-started>10000){pending=null;mc.player.closeContainer();EquipmentClient.current().message("制作器资料读取超时，请重试");return;}
        if(EquipmentClient.current().ready()&&pending.getMenu().initialContentsReady&&mc.gui.screen()==null){var screen=pending;pending=null;mc.gui.setScreen(screen);}
    }
}
