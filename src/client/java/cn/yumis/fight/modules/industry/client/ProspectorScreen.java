package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.api.MineralCatalog;
import cn.yumis.fight.modules.industry.platform.ProspectorMenu;
import cn.yumis.fight.modules.presentation.api.client.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

/** Discovery only; the server sends no higher-grade deposit information. */
public final class ProspectorScreen extends ThemedContainerScreen<ProspectorMenu> {
    private final List<String> filters=new ArrayList<>();
    private int filter,selected=40,detailPage,cell=20,side=210,panelWidth=408,panelHeight=292;
    public ProspectorScreen(ProspectorMenu menu,Inventory inventory,Component title){super(menu,inventory,title,408,292,ThemeDomain.MACHINE);filters.add("");filters.addAll(MineralCatalog.defaults().keySet());}
    @Override protected void init(){
        panelWidth=Math.min(408,width-12);panelHeight=Math.min(292,height-12);
        cell=Math.max(12,Math.min(22,(panelHeight-88)/9));side=24+cell*9;
        super.init();leftPos=(width-panelWidth)/2;topPos=(height-panelHeight)/2;button("扫描",12,30,48,0);button("定位",64,30,48,5);
        button("←",12,panelHeight-25,26,1);button("→",41,panelHeight-25,26,2);button("↑",70,panelHeight-25,26,3);button("↓",99,panelHeight-25,26,4);
        addRenderableWidget(Button.builder(Component.literal("矿物筛选"),b->{filter=(filter+1)%filters.size();detailPage=0;}).bounds(leftPos+side,topPos+30,panelWidth-side-12,20).build(IndustrialUi.IndustrialButton::new));
        addRenderableWidget(Button.builder(Component.literal("上页"),b->detailPage=Math.max(0,detailPage-1)).bounds(leftPos+side,topPos+panelHeight-25,42,20).build(IndustrialUi.IndustrialButton::new));
        addRenderableWidget(Button.builder(Component.literal("下页"),b->detailPage=Math.min(18,detailPage+1)).bounds(leftPos+panelWidth-54,topPos+panelHeight-25,42,20).build(IndustrialUi.IndustrialButton::new));
        for(int i=0;i<81;i++){final int at=i;addRenderableWidget(Button.builder(Component.empty(),b->{selected=at;detailPage=0;}).bounds(leftPos+12+(i%9)*cell,topPos+55+(i/9)*cell,cell-1,cell-1).build(IndustrialUi.IndustrialButton::new));}
    }
    private void button(String text,int x,int y,int w,int id){addRenderableWidget(Button.builder(Component.literal(text),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id)).bounds(leftPos+x,topPos+y,w,20).build(IndustrialUi.IndustrialButton::new));}
    private static Component mineral(String id){return Component.translatable("mineral.yumis_dungeon_and_fight."+id);}
    private JsonArray cells(){return menu.info.has("cells")?menu.info.getAsJsonArray("cells"):new JsonArray();}
    @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float alpha){g.fill(0,0,width,height,UiTheme.OVERLAY);IndustrialUi.panel(g,leftPos,topPos,panelWidth,panelHeight);}
    @Override protected void drawContents(GuiGraphicsExtractor g,int mx,int my,float alpha){
        super.drawContents(g,mx,my,alpha);JsonArray cells=cells();String wanted=filters.get(filter);
        for(int i=0;i<cells.size();i++){
            JsonObject entry=cells.get(i).getAsJsonObject(),minerals=entry.getAsJsonObject("minerals");int known=entry.get("known").getAsInt();boolean match=wanted.isEmpty()?!minerals.isEmpty():minerals.has(wanted);
            int richness=wanted.isEmpty()?minerals.entrySet().stream().mapToInt(e->e.getValue().getAsInt()).max().orElse(0):match?minerals.get(wanted).getAsInt():0;
            int x=leftPos+12+(i%9)*cell,y=topPos+55+(i/9)*cell;
            g.fill(x+2,y+2,x+cell-3,y+cell-3,known==0?0xff29313d:!match?0xff505461:richness==35?0xff8b623f:richness==135?0xff7455a7:0xff287a75);
            if(i==selected){g.fill(x+1,y+1,x+cell-2,y+3,UiTheme.GOLD);g.fill(x+1,y+cell-4,x+cell-2,y+cell-2,UiTheme.GOLD);}
            String count=known==0?"?":Integer.toString(minerals.size());g.text(UiFonts.font(),count,x+(cell-UiFonts.font().width(count))/2,y+(cell-7)/2,UiTheme.TEXT,false);
            if(mx>=x&&mx<x+cell-1&&my>=y&&my<y+cell-1){List<Component> help=new ArrayList<>();help.add(Component.literal("区块 "+entry.get("x").getAsInt()+", "+entry.get("z").getAsInt()));help.add(Component.literal(known==0?"尚未扫描":known<5?"已识别至 "+known+" 级；未显示矿物可能需要更高等级":"已完成最高级识别"));for(var ore:minerals.entrySet())help.add(mineral(ore.getKey()).copy().append(" · "+ore.getValue().getAsInt()+"%"));g.setComponentTooltipForNextFrame(UiFonts.font(),help,mx,my);}
        }
        g.text(UiFonts.font(),"区块探矿 · "+menu.tier+"级",leftPos+12,topPos+12,UiTheme.TEXT,false);
        long cooldown=menu.info.has("cooldown")?menu.info.get("cooldown").getAsLong():0;boolean scanning=menu.info.has("scanning")&&menu.info.get("scanning").getAsBoolean();
        g.text(UiFonts.font(),scanning?"扫描中，可移动":cooldown>0?"冷却 "+String.format(Locale.ROOT,"%.1f秒",cooldown/20.0):"就绪",leftPos+side,topPos+12,UiTheme.GOLD,false);
        int panel=leftPos+side;g.text(UiFonts.font(),wanted.isEmpty()?"筛选：全部":"筛选："+mineral(wanted).getString(),panel,topPos+55,UiTheme.ACCENT,false);
        if(selected<cells.size()){
            var entry=cells.get(selected).getAsJsonObject();int x=entry.get("x").getAsInt(),z=entry.get("z").getAsInt();
            g.text(UiFonts.font(),"区块 "+x+", "+z,panel,topPos+69,UiTheme.TEXT,false);g.text(UiFonts.font(),"X "+(x*16)+"  Z "+(z*16),panel,topPos+82,UiTheme.MUTED,false);
            var matches=entry.getAsJsonObject("minerals").entrySet().stream().filter(e->wanted.isEmpty()||wanted.equals(e.getKey())).toList();
            int lines=Math.max(1,(panelHeight-132)/11),pages=Math.max(1,(matches.size()+lines-1)/lines);detailPage=Math.min(detailPage,pages-1);
            for(int i=detailPage*lines;i<Math.min(matches.size(),(detailPage+1)*lines);i++){
                var ore=matches.get(i);int richness=ore.getValue().getAsInt();String text=mineral(ore.getKey()).getString()+" · "+(richness==35?"贫":richness==135?"富":"普")+richness+"%";
                g.text(UiFonts.font(),font.plainSubstrByWidth(text,panelWidth-side-12),panel,topPos+99+(i-detailPage*lines)*11,UiTheme.TEXT,false);
            }
            if(matches.isEmpty())g.text(UiFonts.font(),entry.get("known").getAsInt()==0?"尚未扫描":"当前无匹配矿物",panel,topPos+99,UiTheme.MUTED,false);
            g.text(UiFonts.font(),(detailPage+1)+" / "+pages,panel+48,topPos+panelHeight-19,UiTheme.MUTED,false);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){}
}
