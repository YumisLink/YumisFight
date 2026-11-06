package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.platform.ItemData;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import java.util.*;
import java.util.function.Supplier;

/** Item introduction, the public pedestal screen and live enrollment/run information. */
public final class WaystoneClient {
    private static final Gson JSON=new Gson();
    private static final Map<String,JsonObject> tables=new LinkedHashMap<>();
    private static JsonObject enrollment;
    private static Supplier<Dungeon> current=()->null;
    private WaystoneClient(){}
    public static void register(IEventBus bus,Supplier<Dungeon> dungeon){current=dungeon;NeoForge.EVENT_BUS.addListener(WaystoneClient::tooltip);bus.addListener((RegisterGuiLayersEvent e)->e.registerAboveAll(Identifier.fromNamespaceAndPath(ItemData.NS,"waystone"),WaystoneClient::hud));}
    private static boolean self(JsonObject state){var p=Minecraft.getInstance().player;if(p==null)return false;return state.getAsJsonArray("members").asList().stream().anyMatch(m->m.getAsJsonObject().get("id").getAsString().equals(p.getStringUUID()));}
    public static void receive(JsonObject value){
        String id=value.get("table").getAsString();if(value.has("removed")){tables.remove(id);if(enrollment!=null&&enrollment.get("table").getAsString().equals(id))enrollment=null;if(Minecraft.getInstance().gui.screen() instanceof PedestalScreen screen&&screen.id.equals(id))screen.onClose();return;}
        tables.put(id,value);if(self(value))enrollment=value;else if(enrollment!=null&&enrollment.get("table").getAsString().equals(id))enrollment=null;
        var mc=Minecraft.getInstance();if(value.get("open").getAsBoolean()&&!(mc.gui.screen() instanceof PedestalScreen screen&&screen.id.equals(id)))mc.gui.pushScreenLayer(new PedestalScreen(id));else if(mc.gui.screen() instanceof PedestalScreen screen&&screen.id.equals(id))screen.refresh();
    }
    public static void started(){enrollment=null;if(Minecraft.getInstance().gui.screen() instanceof PedestalScreen screen)screen.onClose();}
    public static void clear(){enrollment=null;tables.clear();}
    public static void enrollment(PlayerPageSurface page){if(enrollment!=null){var value=stone(enrollment);if(value!=null)page.line("已报名引路台 · 地狱 +"+value.tier+" · "+map(value));String id=enrollment.get("table").getAsString();page.rowButton("退出引路台报名",()->send(id,"leave"));page.gap();}}
    private static void send(String id,String action){var state=tables.get(id);if(state==null)return;var request=ClientUi.current().operation("waystone-table");request.addProperty("table",id);request.addProperty("action",action);request.addProperty("revision",state.get("revision").getAsLong());ClientUi.current().send("waystoneTable",request);}
    private static Waystone stone(JsonObject state){return state==null||!state.has("stone")||state.get("stone").isJsonNull()?null:JSON.fromJson(state.get("stone"),Waystone.class);}
    private static String map(Waystone stone){return DungeonCatalog.current().dungeon(stone.dungeon).name();}
    private static Component line(String text,int color){return UiFonts.decorate(Component.literal(text).withStyle(s->s.withColor(color).withItalic(false)));}
    private static void tooltip(ItemTooltipEvent event){
        if(!ItemData.own(event.getItemStack())||!ItemData.id(event.getItemStack()).equals("waystone"))return;
        var out=event.getToolTip();try{var stone=Waystone.read(event.getItemStack());if(stone==null)return;out.clear();out.add(line("引路石 +"+stone.tier,stone.rarity.color()));out.add(line(stone.rarity.title()+" · "+stone.modifiers.size()+"条副本词条",stone.rarity.color()));out.add(line("目的地："+map(stone),0x9aa9be));out.add(line("怪物等级：Lv."+WaystoneRules.current().monsterLevel(stone.tier),0x9aa9be));out.add(line("──────── 副本词条 ────────",0x78869c));if(stone.modifiers.isEmpty())out.add(line("无副本词条",0x9aa9be));for(var m:stone.modifiers)out.add(line(Waystone.modifierText(m),0xc8a8f0));out.add(line("──────── 挑战收益 ────────",0x78869c));for(var text:stone.rewardText())out.add(line(text,0xb7e6a1));out.add(line("──────── 使用说明 ────────",0x78869c));out.add(line("放入引路台，报名并准备后开启挑战。",0x9aa9be));out.add(line("战斗开始时消耗；完成速度决定返石阶数。",0x9aa9be));}catch(RuntimeException failure){out.add(line("引路石数据无效："+failure.getMessage(),0xff7777));}
    }
    public static void hud(GuiGraphicsExtractor g,DeltaTracker delta){
        var mc=Minecraft.getInstance();if(mc.player==null||!ClientUi.current().ready()||mc.gui.hud.isHidden()||mc.gui.screen() instanceof PedestalScreen)return;Dungeon d=current.get();Waystone value;String heading,timing="";
        if(d!=null&&d.waystone!=null){var run=d.waystone;value=run.stone;heading="地狱 +"+value.tier+" · Lv."+run.monsterLevel;if(run.settled)timing="挑战结束 · 返石 +"+run.resultingTier;else{long left=run.rules.timeLimitsMillis[2]-run.elapsed(ClientUi.current().serverNow());timing=(left>=0?"剩余 ":"超时 ")+time(Math.abs(left))+" · 死亡罚时 "+time(run.penaltyMillis);}}
        else if(enrollment!=null){value=stone(enrollment);if(value==null)return;heading="已报名引路台 · 地狱 +"+value.tier;long deadline=enrollment.get("deadline").getAsLong();timing=deadline>0?"开战倒数 "+Math.max(0,(deadline-ClientUi.current().serverNow()+999)/1000)+"秒":"等待全部准备";}
        else return;
        int x=8,y=Math.max(8,Math.min(54,g.guiHeight()-214)),w=Math.max(80,g.guiWidth()-16);var font=UiFonts.font();g.text(font,heading,x,y,value.rarity.color()|0xff000000,true);y+=12;g.text(font,timing,x,y,0xffeeeeee,true);y+=12;
        for(var m:value.modifiers){g.text(font,font.plainSubstrByWidth(Waystone.modifierText(m),w),x,y,0xffc8a8f0,true);y+=11;}
        for(String text:value.rewardText()){g.text(font,text,x,y,0xffb7e6a1,true);y+=11;}
    }
    private static String change(int value){return value>=0?"升"+value+"阶":"降"+(-value)+"阶";}
    private static String time(long millis){long seconds=millis/1000;return String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
    private static final class PedestalScreen extends ThemedScreen {
        private final String id;
        private int scroll,panelWidth,panelHeight;
        PedestalScreen(String id){super(Component.literal("引路台"),ThemeDomain.MENU);this.id=id;}
        void refresh(){rebuildWidgets();}
        private JsonObject state(){return tables.get(id);}
        @Override protected void init(){panelWidth=Math.min(540,width-16);panelHeight=Math.min(350,height-16);int x=(width-panelWidth)/2,y=(height-panelHeight)/2;JsonObject state=state();if(state==null)return;boolean joined=self(state),ready=false;var p=Minecraft.getInstance().player;if(p!=null)for(var raw:state.getAsJsonArray("members")){var m=raw.getAsJsonObject();if(m.get("id").getAsString().equals(p.getStringUUID()))ready=m.get("ready").getAsBoolean();}int bw=(panelWidth-40)/5,bottom=y+panelHeight-51;String[] labels={"插入主手石","取下石头",joined?"退出报名":"加入挑战",ready?"取消准备":"准备","开始战斗"};String[] actions={"insert","take",joined?"leave":"join","ready","start"};for(int i=0;i<labels.length;i++){String action=actions[i];var button=addRenderableWidget(Button.builder(Component.literal(labels[i]),b->send(id,action)).bounds(x+12+i*(bw+4),bottom,bw,21).build(ThemedButton::new));if(i==3||i==4)button.active=joined;}
            addRenderableWidget(Button.builder(Component.literal("关闭"),b->onClose()).bounds(x+panelWidth-66,y+panelHeight-26,54,19).build(ThemedButton::new));
        }
        @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float alpha){g.fill(0,0,width,height,UiTheme.OVERLAY);UiTheme.panel(g,(width-panelWidth)/2,(height-panelHeight)/2,panelWidth,panelHeight);}
        @Override protected void drawContents(GuiGraphicsExtractor g,int mx,int my,float alpha){
            int x=(width-panelWidth)/2,y=(height-panelHeight)/2,leftWidth=(panelWidth-32)*2/3;var font=UiFonts.font();var state=state();g.text(font,"引路台",x+12,y+10,UiTheme.TEXT,false);if(state!=null){var stone=stone(state);g.text(font,"报名 "+state.getAsJsonArray("members").size()+" / "+WaystoneRules.current().maximumPlayers,x+panelWidth-100,y+10,UiTheme.MUTED,false);var lines=new ArrayList<String>();if(stone==null)lines.add("请将引路石拿在主手，再点击插入。");else{g.item(stone.item(),x+12,y+29);g.text(font,"引路石 +"+stone.tier+" · "+stone.rarity.title(),x+34,y+33,stone.rarity.color()|0xff000000,false);lines.add(map(stone)+" · 怪物 Lv."+WaystoneRules.current().monsterLevel(stone.tier));lines.add("副本词条 · "+stone.modifiers.size()+"条");for(var m:stone.modifiers)lines.add(Waystone.modifierText(m));lines.add("挑战收益");lines.addAll(stone.rewardText());var rules=WaystoneRules.current();lines.add("≤"+time(rules.timeLimitsMillis[0])+" "+change(rules.tierChanges[0])+" · ≤"+time(rules.timeLimitsMillis[1])+" "+change(rules.tierChanges[1]));lines.add("≤"+time(rules.timeLimitsMillis[2])+" "+change(rules.tierChanges[2])+" · 超时或未通关"+change(rules.tierChanges[3]));lines.add("每次死亡罚时"+Waystone.number(rules.deathPenaltyMillis/1000d)+"秒；最低返还+1。");}
                int top=y+54,bottom=y+panelHeight-60,visible=Math.max(1,(bottom-top)/13);scroll=Math.clamp(scroll,0,Math.max(0,lines.size()-visible));g.enableScissor(x+10,top,x+12+leftWidth,bottom);for(int i=0;i<visible&&i+scroll<lines.size();i++)g.text(font,font.plainSubstrByWidth(lines.get(i+scroll),leftWidth-6),x+12,top+i*13,UiTheme.TEXT,false);g.disableScissor();int right=x+leftWidth+24,index=0,rowHeight=Math.min(25,Math.max(11,(panelHeight-100)/Math.max(1,state.getAsJsonArray("members").size())));for(var raw:state.getAsJsonArray("members")){var m=raw.getAsJsonObject();boolean ready=m.get("ready").getAsBoolean();String owner=m.get("id").getAsString().equals(state.get("owner").getAsString())?"★ ":"";g.text(font,font.plainSubstrByWidth((ready?"✓ ":"○ ")+owner+m.get("name").getAsString(),panelWidth-leftWidth-38),right,y+36+index*rowHeight,ready?0xffb7e6a1:UiTheme.MUTED,false);index++;}
                long deadline=state.get("deadline").getAsLong();if(deadline>0)g.text(font,"倒数 "+Math.max(0,(deadline-ClientUi.current().serverNow()+999)/1000)+"秒",x+12,y+panelHeight-22,UiTheme.TEXT,false);else if(lines.size()>visible)g.text(font,"滚轮查看全部词条与收益",x+12,y+panelHeight-22,UiTheme.MUTED,false);
            }super.drawContents(g,mx,my,alpha);
        }
        @Override public boolean mouseScrolled(double x,double y,double sx,double sy){scroll=Math.max(0,scroll-(int)Math.signum(sy));return true;}
        @Override public void onClose(){send(id,"close");Minecraft.getInstance().gui.popScreenLayer();}
        @Override public boolean isPauseScreen(){return false;}
    }
}
