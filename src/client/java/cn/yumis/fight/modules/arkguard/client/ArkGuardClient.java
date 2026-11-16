package cn.yumis.fight.modules.arkguard.client;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.arkguard.api.ArkGuardRing;
import cn.yumis.fight.modules.presentation.api.client.*;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

/** Personal menu state only; the shared roster is projected by the scene's pillar. */
public final class ArkGuardClient {
    private static boolean waiting,participating,returnAvailable;
    private static String queriedSession="";
    private static PlayerPageSurface menu;
    private ArkGuardClient(){}
    public static void register(CommandRouter<Void,JsonObject> messages){
        messages.register("arkguard","arkGuard",(context,value)->{
            if(!ClientUi.current().session().equals(value.get("session").getAsString()))return;
            boolean entered=waiting&&value.get("inside").getAsBoolean();
            waiting=value.get("waiting").getAsBoolean();participating=value.get("participating").getAsBoolean();
            returnAvailable=value.get("returnAvailable").getAsBoolean();
            var mc=Minecraft.getInstance();
            if(entered)mc.gui.setScreen(null);
            else if(menu!=null&&mc.gui.screen()==menu.screen())menu.rebuild();
        });
    }
    public static void clear(){waiting=false;participating=false;returnAvailable=false;queriedSession="";menu=null;}
    public static void render(PlayerPageSurface page,boolean canEnter){
        menu=page;
        var ui=ClientUi.current();
        if(ui.ready()&&!ui.session().equals(queriedSession)){queriedSession=ui.session();ui.send("arkGuard",ui.operation("status"));}
        var mc=Minecraft.getInstance();boolean inside=mc.player!=null&&ArkGuardRing.contains(mc.player.level(),mc.player.blockPosition());
        page.line("其他玩法",UiTheme.GOLD);page.gap();
        int top=page.cursor();page.card(0,top,page.contentWidth()-5,112);
        page.text("方舟守卫圈",12,top+12,UiTheme.GOLD);
        text(page,"共享战场 · 不限入场人数",top+33,UiTheme.TEXT);
        text(page,inside?"本人："+(participating?"已参加":"未参加"):returnAvailable?"可返回进入方舟前的位置。":waiting?"正在准备方舟地图……":"进入后，与中央引导员交谈选择参加。",top+52,UiTheme.MUTED);
        if(inside||returnAvailable){
            page.button(inside?"离开方舟守卫圈":"返回进入前的位置",12,top+77,Math.min(160,page.contentWidth()-24),()->{
                if(!inside&&!canEnter){ui.message("请先结束当前战斗或等待复活。");return;}
                request("leave");
            });
        }else if(waiting){
            page.button("取消等候",12,top+77,Math.min(160,page.contentWidth()-24),()->{waiting=false;request("leave");page.rebuild();});
        }else{
            var button=page.button("进入方舟守卫圈",12,top+77,Math.min(160,page.contentWidth()-24),()->{
                if(!canEnter){ClientUi.current().message("请先结束当前战斗或等待复活。");return;}
                waiting=true;request("enter");page.rebuild();
            });button.active=!waiting&&ClientUi.current().ready();
        }
        page.cursor(top+120);
    }
    private static void text(PlayerPageSurface page,String value,int y,int color){page.text(UiFonts.font().plainSubstrByWidth(value,Math.max(1,page.contentWidth()-24)),12,y,color);}
    private static void request(String kind){
        var ui=ClientUi.current();ui.upload();ui.send("arkGuard",ui.operation(kind));
        if(kind.equals("leave"))Minecraft.getInstance().gui.setScreen(null);
    }
}
