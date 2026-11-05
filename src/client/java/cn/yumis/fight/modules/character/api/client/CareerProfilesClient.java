package cn.yumis.fight.modules.character.api.client;

import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.presentation.api.client.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Server profile summaries and character-owned controls; physical keys remain local. */
public final class CareerProfilesClient {
    private static JsonObject view;
    private CareerProfilesClient(){}
    public static void clear(){view=null;}
    public static long revision(){return view==null?-1:view.get("revision").getAsLong();}
    public static String active(){return view==null?"":view.get("activeId").getAsString();}
    public static boolean receive(JsonObject value){boolean changed=view!=null&&!active().equals(value.get("activeId").getAsString());view=value.deepCopy();return changed;}
    public static void context(JsonObject request){if(view!=null&&!request.has("profileId")){request.addProperty("profileId",active());request.addProperty("profileRevision",revision());}}
    public static boolean occupied(String job){if(view==null)return false;for(var value:view.getAsJsonArray("entries"))if(value.getAsJsonObject().get("job").getAsString().equals(job))return true;return false;}
    private static void select(String id){var ui=ClientUi.current();ui.upload();var request=ui.operation("switchProfile");request.addProperty("target",id);ui.send("progress",request);}
    private static void selectSlot(int slot){var ui=ClientUi.current();ui.upload();var request=ui.operation("openProfileSlot");request.addProperty("slot",slot);ui.send("progress",request);}
    public static void render(PlayerPageSurface page){
        if(view==null)return;
        var rows=new ArrayList<PlayerPageSurface.SidebarRow>();var entries=view.getAsJsonArray("entries");
        for(var value:entries){var row=value.getAsJsonObject();String id=row.get("id").getAsString(),name=row.get("name").getAsString();String detail=JobCatalog.require(row.get("job").getAsString()).title()+" Lv."+row.get("level").getAsInt();boolean current=id.equals(active());rows.add(new PlayerPageSurface.SidebarRow(name,detail,current,()->select(id),()->Minecraft.getInstance().gui.setScreen(new RenameScreen(page.screen(),id,name))));}
        int capacity=view.get("capacity").getAsInt();boolean unlocked=view.has("unlocked")&&view.get("unlocked").getAsBoolean();
        if(unlocked){for(int i=rows.size();i<capacity;i++){int slot=i;rows.add(new PlayerPageSurface.SidebarRow("职业配置"+(i+1),"未选择职业 Lv.20",false,()->selectSlot(slot),null));}}
        else rows.add(new PlayerPageSurface.SidebarRow("职业栏系统","20级转职后解锁",false,null,null));
        String title=unlocked?"职业栏 "+capacity+"栏":"职业栏系统";
        if(page.hasSidebar())page.sidebarRows(title,List.copyOf(rows));
        else{
            page.line(title,UiTheme.GOLD);
            for(var row:rows){int y=page.cursor();if(row.select()==null)page.line(row.title()+"："+row.detail(),UiTheme.MUTED);else{var button=page.button((row.selected()?"当前 · ":"")+row.title()+" · "+row.detail(),0,y,Math.max(80,page.contentWidth()-82),row.select());button.active=!row.selected();if(row.rename()!=null)page.button("改名",page.contentWidth()-76,y,52,row.rename());page.cursor(y+24);}}
            page.gap();
        }
        page.line("切换需副本、训练外脱战，结束施法与全部技能冷却。",UiTheme.MUTED);
        if(view.has("blockedReason")&&!view.get("blockedReason").getAsString().isEmpty())page.line("当前限制："+view.get("blockedReason").getAsString(),UiTheme.MUTED);
    }
    private static final class RenameScreen extends ThemedScreen {
        private final Screen parent;private final String name;private final JsonObject request;private EditBox box;
        RenameScreen(Screen parent,String id,String name){super(Component.literal("重命名职业配置"),ThemeDomain.MENU);this.parent=parent;this.name=name;request=ClientUi.current().operation("renameProfile");request.addProperty("target",id);}
        @Override protected void init(){
            box=new EditBox(font,width/2-110,height/2-12,220,20,Component.literal("配置名称"));box.setMaxLength(32);box.setValue(name);addRenderableWidget(box);setInitialFocus(box);
            addRenderableWidget(Button.builder(Component.literal("保存"),b->{request.addProperty("name",box.getValue());ClientUi.current().send("progress",request);minecraft.gui.setScreen(parent);}).bounds(width/2-110,height/2+26,104,20).build(ThemedButton::new));
            addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(width/2+6,height/2+26,104,20).build(ThemedButton::new));
        }
        @Override protected void drawContents(GuiGraphicsExtractor g,int x,int y,float delta){UiTheme.panel(g,width/2-124,height/2-62,248,124);g.text(font,title,width/2-110,height/2-46,UiTheme.GOLD,false);g.text(font,"1～16个字符",width/2-110,height/2+12,UiTheme.MUTED,false);super.drawContents(g,x,y,delta);}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
    }
}
