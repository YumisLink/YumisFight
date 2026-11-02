package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.api.MineralCatalog;
import cn.yumis.fight.modules.industry.platform.ProspectorMenu;
import cn.yumis.fight.modules.presentation.api.client.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

/** A pannable mineral map. Panning reads discoveries and never initiates a survey. */
public final class ProspectorScreen extends ThemedContainerScreen<ProspectorMenu> {
    private final List<String> minerals=new ArrayList<>();private final Set<String> selectedMinerals=new LinkedHashSet<>();
    private int cell=20,side=210,panelWidth=420,panelHeight=292,centerX,centerZ,selectedX,selectedZ,filterScroll,detailScroll;
    private double offsetX,offsetZ;private boolean dragging,dropdown;private long sentAt;private int sentX=Integer.MIN_VALUE,sentZ;
    public ProspectorScreen(ProspectorMenu menu,Inventory inventory,Component title){super(menu,inventory,title,420,292,ThemeDomain.MACHINE);minerals.addAll(MineralCatalog.defaults().keySet().stream().filter(id->!MineralCatalog.universal(id)).toList());selectedMinerals.addAll(minerals);centerX=selectedX=inventory.player.blockPosition().getX()>>4;centerZ=selectedZ=inventory.player.blockPosition().getZ()>>4;}
    @Override protected void init(){
        panelWidth=Math.min(440,width-12);panelHeight=Math.min(310,height-12);cell=Math.max(10,Math.min(24,Math.min((panelHeight-65)/9,(panelWidth-150)/9)));side=24+9*cell;
        super.init();leftPos=(width-panelWidth)/2;topPos=(height-panelHeight)/2;
        addRenderableWidget(Button.builder(Component.literal("矿物筛选 ▾"),button->dropdown=!dropdown).bounds(leftPos+side,topPos+30,panelWidth-side-12,20).build(IndustrialUi.IndustrialButton::new));
    }
    private static String mineral(String id){return Component.translatable("mineral.yumis_dungeon_and_fight."+id).getString();}
    private static String richness(int amount){return amount==35?"贫瘠":amount==135?"富集":"普通";}
    private JsonArray cells(){return menu.info.has("cells")?menu.info.getAsJsonArray("cells"):new JsonArray();}
    private int mapX(){return leftPos+12;}private int mapY(){return topPos+54;}private boolean inMap(double x,double y){return x>=mapX()&&x<mapX()+cell*9&&y>=mapY()&&y<mapY()+cell*9;}
    private int popupRows(){return Math.min(8,Math.max(1,(panelHeight-92)/18));}
    private void sendPan(){long now=net.minecraft.util.Util.getMillis();if(now-sentAt<90||sentX==centerX&&sentZ==centerZ)return;sentAt=now;sentX=centerX;sentZ=centerZ;var request=new JsonObject();request.addProperty("menu",menu.containerId);request.addProperty("x",centerX);request.addProperty("z",centerZ);ClientUi.current().send("prospectorPan",request);}
    @Override protected void containerTick(){super.containerTick();sendPan();}
    @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float alpha){g.fill(0,0,width,height,UiTheme.OVERLAY);UiTheme.panel(g,leftPos,topPos,panelWidth,panelHeight);}
    @Override protected void drawContents(GuiGraphicsExtractor g,int mx,int my,float alpha){
        super.drawContents(g,mx,my,alpha);var font=UiFonts.font();g.text(font,"区块探矿 · "+menu.tier+"级",leftPos+12,topPos+12,UiTheme.TEXT,false);g.text(font,"拖动浏览已探区块",leftPos+12,topPos+35,UiTheme.MUTED,false);
        UiTheme.surface(g,mapX()-1,mapY()-1,cell*9+2,cell*9+2,3,0xff182128,0xff7b8792);g.enableScissor(mapX(),mapY(),mapX()+cell*9,mapY()+cell*9);
        JsonObject detail=null;
        for(var raw:cells()){
            var entry=raw.getAsJsonObject();int chunkX=entry.get("x").getAsInt(),chunkZ=entry.get("z").getAsInt(),known=entry.get("known").getAsInt();var ores=entry.getAsJsonObject("minerals");
            int x=mapX()+(chunkX-centerX+4)*cell+(int)offsetX,y=mapY()+(chunkZ-centerZ+4)*cell+(int)offsetZ;
            g.fill(x,y,x+cell-1,y+cell-1,known==0?0xff1c252d:0xff354b44);int icon=0;
            for(var ore:ores.entrySet())if(selectedMinerals.contains(ore.getKey())){int color=0xff000000|((ore.getKey().hashCode()&0x7f7f7f)|0x707070);int dot=Math.max(2,cell/5);g.fill(x+3+(icon%3)*(dot+1),y+3+(icon/3)*(dot+1),x+3+(icon%3)*(dot+1)+dot,y+3+(icon/3)*(dot+1)+dot,color);if(++icon>=6)break;}
            if(chunkX==selectedX&&chunkZ==selectedZ){detail=entry;g.fill(x,y,x+cell,y+1,UiTheme.GOLD);g.fill(x,y+cell-1,x+cell,y+cell,UiTheme.GOLD);g.fill(x,y,x+1,y+cell,UiTheme.GOLD);g.fill(x+cell-1,y,x+cell,y+cell,UiTheme.GOLD);}
            if(!dropdown&&inMap(mx,my)&&mx>=x&&mx<x+cell&&my>=y&&my<y+cell){var help=new ArrayList<Component>();help.add(Component.literal("区块 "+chunkX+", "+chunkZ));help.add(Component.literal(known==0?"尚未扫描":"已识别至 "+known+" 级"));int nameWidth=ores.entrySet().stream().filter(ore->selectedMinerals.contains(ore.getKey())).mapToInt(ore->font.width(mineral(ore.getKey()))).max().orElse(0);for(var ore:ores.entrySet())if(selectedMinerals.contains(ore.getKey())){String name=mineral(ore.getKey());help.add(Component.literal(name+" ".repeat(2+Math.max(0,(nameWidth-font.width(name))/Math.max(1,font.width(" "))))+richness(ore.getValue().getAsInt())));}g.setComponentTooltipForNextFrame(font,help,mx,my);}
        }
        if(minecraft.player!=null){double px=minecraft.player.getX()/16d,pz=minecraft.player.getZ()/16d;int x=mapX()+(int)((px-centerX+4)*cell+offsetX),y=mapY()+(int)((pz-centerZ+4)*cell+offsetZ)-5;for(int row=0;row<13;row++){int half=(int)Math.ceil(3*(1-row/13d));g.fill(x-half,y+row,x+half+1,y+row+1,0xff3c99ff);}}
        g.disableScissor();int x=leftPos+side,w=panelWidth-side-12;long cooldown=menu.info.has("cooldown")?menu.info.get("cooldown").getAsLong():0;boolean scanning=menu.info.has("scanning")&&menu.info.get("scanning").getAsBoolean();g.text(font,scanning?"搜索中":cooldown>0?"搜索冷却中":"搜索就绪",x,topPos+12,UiTheme.GOLD,false);
        g.text(font,"已选 "+selectedMinerals.size()+" 种矿物",x,topPos+59,UiTheme.ACCENT,false);g.text(font,"区块 "+selectedX+", "+selectedZ,x,topPos+75,UiTheme.TEXT,false);
        if(detail!=null){var matching=detail.getAsJsonObject("minerals").entrySet().stream().filter(e->selectedMinerals.contains(e.getKey())).toList();int rows=Math.max(1,(panelHeight-115)/14);detailScroll=Math.clamp(detailScroll,0,Math.max(0,matching.size()-rows));int nameWidth=matching.stream().mapToInt(e->font.width(mineral(e.getKey()))).max().orElse(0),column=Math.min(nameWidth+8,Math.max(20,w-30));for(int i=0;i<rows&&i+detailScroll<matching.size();i++){var ore=matching.get(i+detailScroll);g.text(font,font.plainSubstrByWidth(mineral(ore.getKey()),column-5),x,topPos+97+i*14,UiTheme.TEXT,false);g.text(font,richness(ore.getValue().getAsInt()),x+column,topPos+97+i*14,UiTheme.TEXT,false);}if(matching.isEmpty())g.text(font,detail.get("known").getAsInt()==0?"尚未扫描":"无匹配矿物",x,topPos+97,UiTheme.MUTED,false);}
        if(dropdown){int rows=popupRows(),y=topPos+52;UiTheme.panel(g,x,y,w,rows*18+26);filterScroll=Math.clamp(filterScroll,0,Math.max(0,minerals.size()-rows));for(int i=0;i<rows&&i+filterScroll<minerals.size();i++){String id=minerals.get(i+filterScroll);String label=(selectedMinerals.contains(id)?"☑ ":"☐ ")+mineral(id);g.text(font,font.plainSubstrByWidth(label,w-12),x+5,y+5+i*18,UiTheme.TEXT,false);}g.text(font,"全选",x+5,y+rows*18+7,UiTheme.ACCENT,false);g.text(font,"取消全部",x+w-54,y+rows*18+7,UiTheme.ACCENT,false);}
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){
        if(event.button()!=0)return super.mouseClicked(event,twice);
        if(dropdown){int x=leftPos+side,y=topPos+52,w=panelWidth-side-12,rows=popupRows();if(event.x()>=x&&event.x()<x+w&&event.y()>=y&&event.y()<y+rows*18+26){int row=(int)(event.y()-y)/18;if(row<rows&&row+filterScroll<minerals.size()){String id=minerals.get(row+filterScroll);if(!selectedMinerals.remove(id))selectedMinerals.add(id);detailScroll=0;}else if(row>=rows){if(event.x()<x+w/2){selectedMinerals.clear();selectedMinerals.addAll(minerals);}else selectedMinerals.clear();}return true;}dropdown=false;}
        if(inMap(event.x(),event.y())){dragging=true;selectedX=centerX-4+(int)Math.floor((event.x()-mapX()-offsetX)/cell);selectedZ=centerZ-4+(int)Math.floor((event.y()-mapY()-offsetZ)/cell);detailScroll=0;return true;}return super.mouseClicked(event,twice);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){if(!dragging)return super.mouseDragged(event,dx,dy);offsetX+=dx;offsetZ+=dy;int sx=(int)(offsetX/cell),sz=(int)(offsetZ/cell);centerX=Math.clamp(centerX-sx,-1875000,1875000);centerZ=Math.clamp(centerZ-sz,-1875000,1875000);offsetX-=sx*cell;offsetZ-=sz*cell;sendPan();return true;}
    @Override public boolean mouseReleased(MouseButtonEvent event){boolean old=dragging;dragging=false;sendPan();return old||super.mouseReleased(event);}
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){if(dropdown){filterScroll=Math.clamp(filterScroll-(int)Math.signum(sy),0,Math.max(0,minerals.size()-popupRows()));return true;}if(x>=leftPos+side){detailScroll=Math.max(0,detailScroll-(int)Math.signum(sy));return true;}return super.mouseScrolled(x,y,sx,sy);}
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mx,int my){}
}
