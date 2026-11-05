package cn.yumis.fight.bootstrap;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.ClientSettings;
import cn.yumis.fight.modules.presentation.api.client.ThemedButton;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.platform.ItemData;

import cn.yumis.fight.modules.presentation.api.PlayerPages;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageRenderers;

import com.google.gson.*;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.*;
/** Separate, scrollable pages. Text keeps Minecraft's native size at every GUI scale. */
public final class FightScreen extends ThemedScreen implements cn.yumis.fight.modules.presentation.api.client.PlayerPageSurface {
    private static final int PAGE_TOP_PADDING=12;
    public final int page;
    private final ClientPagePanel customPanel;
    private final ClientSession state=ClientSession.INSTANCE;
    private record Line(String text,int x,int y,int color){
    }
    private final List<Line> lines=new ArrayList<>();
    private record HoverArea(int x,int y,int width,int height,Hover renderer){}
    private final List<HoverArea> hoverAreas=new ArrayList<>();
    private record Card(int x,int y,int width,int height){
    }
    private record Icon(String path,int x,int y,int size){
    }
    private final List<Card> cards=new ArrayList<>();
    private final List<Icon> icons=new ArrayList<>();
    private record Picture(String path,int x,int y,int width,int height,float u0,float u1,float v0,float v1){}
    private record RightClick(int x,int y,int width,int height,Runnable action){}
    private final List<Picture> pictures=new ArrayList<>();
    private final List<RightClick> rightClicks=new ArrayList<>();
    private List<PlayerPageSurface.Navigation> navigation=List.of();
    private List<PlayerPageSurface.SidebarRow> sidebarRows=List.of();
    private String sidebarTitle="";
    private int sidebarScroll;
    private final List<AbstractWidget> body=new ArrayList<>();
    private int sidebar,left,top,panelWidth,panelHeight,scroll,contentHeight;
    private EditBox input;
    private final Map<AbstractWidget,Hover> widgetHovers=new LinkedHashMap<>();
    private final Map<String,Object> pageState=new HashMap<>();
    private int nextY;
    private boolean draggingScroll;private double scrollGrab;
    private long themeRevision=UiTheme.revision;
    public FightScreen(){
        this(-1);
    }
    public FightScreen(String pageId){this(pageId==null?-1:java.util.stream.IntStream.range(0,PlayerPages.all().size()).filter(i->PlayerPages.at(i).id().equals(pageId)).findFirst().orElse(-1));}
    public FightScreen(int page){
        super(net.minecraft.client.Minecraft.getInstance(),UiFonts.font(),Component.literal(page<0?"地下城与战斗":PlayerPages.at(page).title()),ThemeDomain.MENU);
        this.page=page;
        customPanel=page<0?null:PlayerPageRenderers.panel(PlayerPages.at(page).id());
    }
    @Override protected void init(){
        UiTheme.check();
        boolean small=ClientSettings.panel.equals("small");
        panelWidth=page<0?Math.min(width-12,380):Math.min(width-12,small?760:ClientSettings.panel.equals("large")?1000:900);
        panelHeight=page<0?Math.min(height-12,294):Math.min(height-12,small?Math.round(height*.8f):ClientSettings.panel.equals("large")?660:600);
        if(page>=0&&small)panelWidth=Math.min(panelWidth,Math.round(width*.8f));
        left=(width-panelWidth)/2;
        top=(height-panelHeight)/2;
        sidebar=page>=0&&customPanel==null&&panelWidth>=480?112:0;
        build();
    }
    public void refresh(){
        if(font!=null&&!(input!=null&&input.isFocused()))build();
    }
    private void build(){
        try(var palette=themeScope()){buildContent();}
    }
    private void buildContent(){
        input=null;
        clearWidgets();
        body.clear();
        widgetHovers.clear();
        lines.clear();hoverAreas.clear();
        cards.clear();
        icons.clear();sidebarRows=List.of();sidebarTitle="";
        pictures.clear();rightClicks.clear();navigation=List.of();
        sidebar=page>=0&&customPanel==null&&panelWidth>=480?112:0;
        nextY=page<0?0:PAGE_TOP_PADDING;
        buttonFixed(page<0?"关闭":"返回",left+panelWidth-50,top+7,42,()->{
            state.upload();if(page<0)onClose();else minecraft.gui.setScreen(new FightScreen());
        }
        );
        if(page>=0){
            var pages=PlayerPages.all();var tabs=java.util.stream.IntStream.range(0,pages.size()).filter(i->pages.get(i).screen()).toArray();
            int cell=(panelWidth-16)/tabs.length;
            for(int index=0;index<tabs.length;index++){
                final int target=tabs[index];
                var tab=Button.builder(Component.literal(PlayerPages.at(target).tab()),button->{
                    openPage(target);
                }
                ).bounds(left+8+index*cell,top+35,cell-3,23).build(ThemedButton::new);
                ((ThemedButton)tab).selected=page==target;
                addRenderableWidget(tab);
            }
        }
        if(!state.ready){
            line("正在读取服务器角色档案…");
            contentHeight=40;
            return;
        }
        if(page<0)menu();else if(customPanel==null)PlayerPageRenderers.render(PlayerPages.at(page).id(),this);
        contentHeight=nextY+12;
        scroll=Math.clamp(scroll,0,Math.max(0,contentHeight-bodyHeight()));
        updateVisibility();
    }
    private int bodyTop(){
        return top+(page<0?36:68);
    }
    private int bodyHeight(){
        return panelHeight-(page<0?66:98);
    }
    private int contentLeft(){
        return left+sidebar+(sidebar>0?18:8);
    }
    public int contentWidth(){
        return panelWidth-sidebar-(sidebar>0?26:16);
    }
    private void openPage(int index){
        state.upload();var destination=PlayerPages.at(index);
        if(destination.screen())minecraft.gui.setScreen(new FightScreen(index));else onClose();
        if(!destination.openCommand().isEmpty())state.send(destination.openCommand());
    }
    private void menu(){
        int cell=(panelWidth-20)/4;
        for(int i=0;i<PlayerPages.all().size();i++){
            int index=i,row=i/4,col=i%4;
            AbstractWidget b=Button.builder(Component.empty(),x->{
                openPage(index);
            }
            ).bounds(left+10+col*cell,bodyTop()+row*68-scroll,cell-5,60).build(ThemedButton::new);
            b.setTooltip(Tooltip.create(UiFonts.decorate(Component.literal(PlayerPages.at(i).title()))));
            body.add(addRenderableWidget(b));
        }
        nextY=((PlayerPages.all().size()+3)/4)*68;
    }
    public void line(String text){
        line(text,UiTheme.TEXT);
    }
    public void line(String text,int color){
        for(var chars:font.split(Component.literal(text),contentWidth()-24)){
            lines.add(new Line(charsToString(chars),0,nextY,color));
            nextY+=12;
        }
    }
    private static String charsToString(net.minecraft.util.FormattedCharSequence seq){
        StringBuilder b=new StringBuilder();
        seq.accept((i,s,c)->{
            b.appendCodePoint(c);return true;
        }
        );
        return b.toString();
    }
    public void gap(){
        nextY+=8;
    }
    public void rowButton(String label,Runnable action){
        button(label,0,nextY,contentWidth()-24,action);
        nextY+=24;
    }
    public AbstractWidget button(String label,int x,int y,int w,Runnable action){
        var b=Button.builder(Component.literal(label),ignored->action.run()).bounds(contentLeft()+12+x,bodyTop()+y-scroll,w,20).build(ThemedButton::new);
        body.add(addRenderableWidget(b));return b;
    }
    private void buttonFixed(String label,int x,int y,int w,Runnable action){
        addRenderableWidget(Button.builder(Component.literal(label),ignored->action.run()).bounds(x,y,w,20).build(ThemedButton::new));
    }
    private void updateVisibility(){
        for(AbstractWidget w:body){
            w.visible=w.getY()>=bodyTop()&&w.getY()+w.getHeight()<=bodyTop()+bodyHeight();
            
        }
    }
    @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float a){
        try(var palette=themeScope()){drawBackground(g);}
    }
    private void drawBackground(GuiGraphicsExtractor g){
        g.fill(0,0,width,height,UiTheme.OVERLAY);
        UiTheme.panel(g,left,top,(left+panelWidth)-(left),(top+panelHeight)-(top));

    }
    @Override protected void drawContents(GuiGraphicsExtractor g,int mx,int my,float a){
        UiTheme.card(g,left+6,top+6,(left+panelWidth-6)-(left+6),(top+30)-(top+6));
        g.text(font,title,left+14,top+13,UiTheme.TEXT,false);
        UiTheme.card(g,contentLeft(),bodyTop(),(contentLeft()+contentWidth())-(contentLeft()),(bodyTop()+bodyHeight())-(bodyTop()));
        UiTheme.card(g,left+6,top+panelHeight-28,(left+panelWidth-6)-(left+6),(top+panelHeight-6)-(top+panelHeight-28));
        g.text(font,page<0?"I / Esc 关闭":"I 返回菜单 · Esc 关闭 · 滚轮浏览",left+14,top+panelHeight-20,UiTheme.MUTED,false);
        if(sidebar>0&&navigation.isEmpty()){
            UiTheme.card(g,left+6,bodyTop(),(left+sidebar+8)-(left+6),(bodyTop()+bodyHeight())-(bodyTop()));
            int yy=bodyTop()+12;
            if(sidebarRows.isEmpty()||bodyHeight()>=240){
            g.text(font,cn.yumis.fight.modules.character.api.JobCatalog.require(state.progress.jobId()).title(),left+14,yy,UiTheme.GOLD,false);
            g.text(font,"战斗等级 "+state.progress.level,left+14,yy+21,UiTheme.TEXT,false);
            g.text(font,"可用 SP  "+state.progress.sp,left+14,yy+44,UiTheme.GOLD,false);
            g.text(font,"自由点  "+state.progress.free(),left+14,yy+65,UiTheme.ACCENT,false);
            g.text(font,ClientSession.now()<state.resources.combatUntil?"战斗中":"脱战",left+14,yy+86,UiTheme.MUTED,false);
            }
            drawSidebarRows(g,mx,my);
        }
        g.enableScissor(left+5,bodyTop(),left+panelWidth-5,bodyTop()+bodyHeight());
        for(Card card:cards){
            int yy=bodyTop()+card.y-scroll;
            UiTheme.card(g,contentLeft()+12+card.x,yy,(contentLeft()+12+card.x+card.width)-(contentLeft()+12+card.x),(yy+card.height)-(yy));
        }
        for(AbstractWidget widget:body)if(widget.visible)widget.extractRenderState(g,mx,my,a);
        for(Icon icon:icons){
            int yy=bodyTop()+icon.y-scroll;
            g.blit(ClientBootstrap.id(icon.path),contentLeft()+12+icon.x,yy,contentLeft()+12+icon.x+icon.size,yy+icon.size,0,1,0,1);
        }
        for(Picture picture:pictures){int yy=bodyTop()+picture.y-scroll;g.blit(ClientBootstrap.id(picture.path),contentLeft()+12+picture.x,yy,contentLeft()+12+picture.x+picture.width,yy+picture.height,picture.u0,picture.u1,picture.v0,picture.v1);}
        for(Line l:lines){
            int y=bodyTop()+l.y-scroll;
            if(y>=bodyTop()&&y+9<=bodyTop()+bodyHeight())g.text(font,page==0?cn.yumis.fight.modules.skills.client.CombatAttributeTooltip.numbers(l.text):Component.literal(l.text),contentLeft()+12+l.x,y,0xff000000|l.color,false);
        }
        if(page<0&&state.ready){
            int cell=(panelWidth-20)/4;
            for(int i=0;i<PlayerPages.all().size();i++){
                int x=left+10+(i%4)*cell,y=bodyTop()+(i/4)*68-scroll;
                if(y<bodyTop()||y+60>bodyTop()+bodyHeight())continue;
                g.text(font,PlayerPages.at(i).title(),x+(cell-5-font.width(PlayerPages.at(i).title()))/2,y+6,UiTheme.TEXT,false);
                g.blit(Identifier.fromNamespaceAndPath(ItemData.NS,"textures/gui/menu/"+PlayerPages.at(i).icon()+".png"),x+(cell-37)/2,y+21,x+(cell+27)/2,y+53,0,1,0,1);
            }
        }
        g.disableScissor();
        for(var child:children())if(child instanceof AbstractWidget widget&&!body.contains(widget))widget.extractRenderState(g,mx,my,a);
        if(customPanel!=null&&state.ready)customPanel.render(g,new ClientPagePanel.Region(contentLeft()+4,bodyTop()+4,contentWidth()-8,bodyHeight()-8),mx,my,a);
        if(my>=bodyTop()&&my<bodyTop()+bodyHeight())for(var area:hoverAreas){int x=contentLeft()+12+area.x,y=bodyTop()+area.y-scroll;if(mx>=x&&mx<x+area.width&&my>=y&&my<y+area.height){area.renderer.render(g,mx,my);break;}}
        if(minecraft.gui.screen()==this)for(var row:widgetHovers.entrySet()){
            var widget=row.getKey();
            if(widget.visible&&mx>=widget.getX()&&mx<widget.getX()+widget.getWidth()&&my>=widget.getY()&&my<widget.getY()+widget.getHeight()){
                row.getValue().render(g,mx,my);
                break;
            }
        }
        if(contentHeight>bodyHeight()){
            int bar=Math.max(12,bodyHeight()*bodyHeight()/contentHeight),y=bodyTop()+(bodyHeight()-bar)*scroll/Math.max(1,contentHeight-bodyHeight());
            UiTheme.scrollbar(g,left+panelWidth-12,bodyTop(),bodyHeight(),bar,y-bodyTop());
        }
        if(state.ready)Hud.gold(g,state);
    }
    private boolean sidebarClick(MouseButtonEvent event){
        if(!insideSidebar(event.x(),event.y())||(event.button()!=0&&event.button()!=1))return false;
        int index=((int)event.y()-sidebarListTop()+sidebarScroll)/34;if(index<0||index>=sidebarRows.size())return true;
        var row=sidebarRows.get(index);if(event.button()==1||event.x()>=left+sidebar-20){if(row.rename()!=null)row.rename().run();}else if(row.select()!=null&&!row.selected())row.select().run();return true;
    }
    private void setScroll(int value){int old=scroll;scroll=Math.clamp(value,0,Math.max(0,contentHeight-bodyHeight()));for(AbstractWidget widget:body)widget.setY(widget.getY()+old-scroll);updateVisibility();}
    private int sidebarListTop(){return bodyTop()+(bodyHeight()<240?32:142);}
    private int sidebarListHeight(){return Math.max(0,bodyTop()+bodyHeight()-sidebarListTop()-6);}
    private boolean insideSidebar(double x,double y){return !sidebarRows.isEmpty()&&sidebar>0&&x>=left+8&&x<left+sidebar+6&&y>=sidebarListTop()&&y<bodyTop()+bodyHeight()-6;}
    private String shortSidebar(String value,int width){return font.width(value)<=width?value:font.plainSubstrByWidth(value,Math.max(1,width-font.width("…")))+"…";}
    private void drawSidebarRows(GuiGraphicsExtractor g,int mx,int my){
        if(sidebarRows.isEmpty())return;int start=sidebarListTop(),height=sidebarListHeight();
        g.text(font,sidebarTitle,left+13,start-17,UiTheme.GOLD,false);if(height<=0)return;
        sidebarScroll=Math.clamp(sidebarScroll,0,Math.max(0,sidebarRows.size()*34-height));
        g.enableScissor(left+8,start,left+sidebar+7,start+height);
        for(int i=0;i<sidebarRows.size();i++){var row=sidebarRows.get(i);int y=start+i*34-sidebarScroll;if(y+32<start||y>=start+height)continue;
            UiTheme.card(g,left+10,y,sidebar-8,31);if(row.selected())g.fill(left+10,y,left+12,y+31,UiTheme.ACCENT);
            g.text(font,shortSidebar(row.title(),sidebar-40),left+15,y+5,row.select()==null?UiTheme.MUTED:row.selected()?UiTheme.ACCENT:UiTheme.TEXT,false);
            g.text(font,shortSidebar(row.detail(),sidebar-24),left+15,y+18,UiTheme.MUTED,false);
            if(row.rename()!=null)g.text(font,"改",left+sidebar-15,y+5,UiTheme.MUTED,false);
            if(insideSidebar(mx,my)&&my>=y&&my<y+31)g.setComponentTooltipForNextFrame(font,List.of(Component.literal(row.title()),Component.literal(row.detail()),Component.literal(row.select()==null?"20级转职后解锁职业栏。":"左键切换 · 右键或点击“改”重命名")),mx,my);
        }
        g.disableScissor();if(sidebarRows.size()*34>height){int thumb=Math.max(8,height*height/(sidebarRows.size()*34)),y=start+(height-thumb)*sidebarScroll/Math.max(1,sidebarRows.size()*34-height);g.fill(left+sidebar+4,y,left+sidebar+6,y+thumb,UiTheme.MUTED);}
    }
    @Override public void sidebarRows(String title,List<PlayerPageSurface.SidebarRow> rows){sidebarTitle=title;sidebarRows=List.copyOf(rows);}
    private void dragScroll(double y){int thumb=Math.max(12,bodyHeight()*bodyHeight()/Math.max(1,contentHeight));setScroll((int)Math.round((y-bodyTop()-scrollGrab)*Math.max(0,contentHeight-bodyHeight())/Math.max(1,bodyHeight()-thumb)));}
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){if(draggingScroll){dragScroll(event.y());return true;}if(customPanel!=null&&customPanel.mouseDragged(event,dx,dy))return true;return super.mouseDragged(event,dx,dy);}
    @Override public boolean mouseReleased(MouseButtonEvent event){boolean was=draggingScroll;draggingScroll=false;if(customPanel!=null&&customPanel.mouseReleased(event))return true;return was||super.mouseReleased(event);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){if(sidebarClick(event))return true;if(customPanel==null&&event.button()==0&&contentHeight>bodyHeight()&&event.x()>=left+panelWidth-14&&event.x()<=left+panelWidth&&event.y()>=bodyTop()&&event.y()<=bodyTop()+bodyHeight()){int thumb=Math.max(12,bodyHeight()*bodyHeight()/contentHeight),at=bodyTop()+(bodyHeight()-thumb)*scroll/Math.max(1,contentHeight-bodyHeight());scrollGrab=event.y()>=at&&event.y()<at+thumb?event.y()-at:thumb/2d;draggingScroll=true;dragScroll(event.y());return true;}if(customPanel!=null&&customPanel.mouseClicked(event,twice))return true;if(event.button()==1&&event.y()>=bodyTop()&&event.y()<bodyTop()+bodyHeight())for(var area:rightClicks){int x=contentLeft()+12+area.x,y=bodyTop()+area.y-scroll;if(event.x()>=x&&event.x()<x+area.width&&event.y()>=y&&event.y()<y+area.height){area.action.run();return true;}}return super.mouseClicked(event,twice);}
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){
        if(insideSidebar(x,y)){sidebarScroll=Math.clamp(sidebarScroll-(int)(sy*34),0,Math.max(0,sidebarRows.size()*34-sidebarListHeight()));return true;}
        if(customPanel!=null&&customPanel.mouseScrolled(x,y,sx,sy))return true;
        int old=scroll;
        scroll=Math.clamp(scroll-(int)(sy*24),0,Math.max(0,contentHeight-bodyHeight()));
        if(old!=scroll){
            for(AbstractWidget w:body)w.setY(w.getY()+old-scroll);
            updateVisibility();
        }
        return true;
    }
    @Override public boolean keyPressed(KeyEvent e){
        if(customPanel!=null&&customPanel.keyPressed(e))return true;
        if(e.isEscape()){
            onClose();
            return true;
        }
        if(!(getFocused() instanceof EditBox box&&box.isFocused())&&cn.yumis.fight.modules.presentation.api.client.MenuInput.MENU.matches(e)){
            state.upload();
            if(page<0)onClose();
            else minecraft.gui.setScreen(new FightScreen());
            return true;
        }
        return super.keyPressed(e);
    }
    @Override public void onClose(){
        state.upload();
        minecraft.gui.setScreen(null);
    }
    @Override public void tick(){UiTheme.check();if(customPanel!=null&&state.ready)customPanel.tick();if(themeRevision!=UiTheme.revision){themeRevision=UiTheme.revision;build();}}
    @Override public void removed(){if(customPanel!=null)customPanel.close();super.removed();}
    @Override public boolean isPauseScreen(){
        return false;
    }
    public Screen screen(){return this;}
    public int windowWidth(){return width;}
    public int cursor(){return nextY;}
    public void cursor(int position){nextY=position;}
    public void text(String text,int x,int y,int color){lines.add(new Line(text,x,y,color));}
    public void card(int x,int y,int width,int height){cards.add(new Card(x,y,width,height));}
    public void icon(String path,int x,int y,int size){icons.add(new Icon(path,x,y,size));}
    public void image(String path,int x,int y,int width,int height,float u0,float u1,float v0,float v1){pictures.add(new Picture(path,x,y,width,height,u0,u1,v0,v1));}
    public boolean hasSidebar(){return sidebar>0;}
    public void rightClick(int x,int y,int width,int height,Runnable action){rightClicks.add(new RightClick(x,y,width,height,action));}
    public void navigation(List<PlayerPageSurface.Navigation> items,String selected,java.util.function.Consumer<String> select){
        navigation=List.copyOf(items);sidebar=panelWidth>=480?88:0;
        int index=0,columns=Math.max(1,(contentWidth()-24)/76),base=nextY;
        for(var item:items){int xx=sidebar>0?left+8:contentLeft()+12+(index%columns)*(contentWidth()-24)/columns,yy=sidebar>0?bodyTop()+8+index*25:bodyTop()+base+(index/columns)*24-scroll;int w=sidebar>0?84:(contentWidth()-24)/columns-4;
            var b=Button.builder(Component.literal(item.title()),ignored->select.accept(item.id())).bounds(xx,yy,w,21).build(ThemedButton::new);((ThemedButton)b).selected=item.id().equals(selected);addRenderableWidget(b);if(sidebar==0)body.add(b);index++;
        }
        if(sidebar==0)nextY=base+((items.size()+columns-1)/columns)*24+8;
    }
    public void hover(AbstractWidget widget,Hover renderer){widgetHovers.put(widget,renderer);}
    public void hover(int x,int y,int width,int height,Hover renderer){hoverAreas.add(new HoverArea(x,y,width,height,renderer));}
    public java.util.function.Supplier<String> textInput(String key,String label,int maximum){
        var box=new EditBox(font,contentLeft()+12,bodyTop()+nextY-scroll,contentWidth()-24,20,Component.literal(label));
        box.setValue(remember(key,""));box.setMaxLength(maximum);box.setBordered(false);box.setResponder(value->rememberValue(key,value));
        box.setResponder(value->{rememberValue(key,value);boolean focus=box.isFocused();int caret=box.getCursorPosition();build();if(focus&&input!=null){setFocused(input);input.setFocused(true);input.setCursorPosition(caret);}});input=box;body.add(addRenderableWidget(box));nextY+=26;return box::getValue;
    }
    @SuppressWarnings("unchecked") public <T>T remember(String key,T fallback){return (T)pageState.getOrDefault(key,fallback);}
    public void rememberValue(String key,Object value){pageState.put(key,value);}
    public void rebuild(){build();}
    public void resize(){init();}
}
