package cn.yumis.fight.bootstrap;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.ClientSettings;
import cn.yumis.fight.modules.presentation.api.client.ThemedButton;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.platform.ItemData;

import cn.yumis.fight.modules.presentation.api.PlayerPages;
import cn.yumis.fight.modules.presentation.api.RichText;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageRenderers;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
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
    private static final String SIDEBAR_RENAME="重命名";
    public final int page;
    private final ClientPagePanel customPanel;
    private final ClientSession state=ClientSession.INSTANCE;
    private record Line(String text,int x,int y,int color){
    }
    private final List<Line> lines=new ArrayList<>();
    private record StyledLine(net.minecraft.util.FormattedCharSequence text,int y,int color){}
    private final List<StyledLine> styledLines=new ArrayList<>();
    private record HoverArea(int x,int y,int width,int height,Hover renderer){}
    private final List<HoverArea> hoverAreas=new ArrayList<>();
    private record Card(int x,int y,int width,int height){
    }
    private record PageDrawing(int x,int y,int width,int height,Drawing drawing){}
    private record Icon(String path,int x,int y,int size){
    }
    private final List<Card> cards=new ArrayList<>();
    private final List<PageDrawing> drawings=new ArrayList<>();
    private final List<Icon> icons=new ArrayList<>();
    private record Picture(String path,int x,int y,int width,int height,float u0,float u1,float v0,float v1){}
    private record RightClick(int x,int y,int width,int height,Runnable action){}
    private final List<Picture> pictures=new ArrayList<>();
    private record ExperienceBar(int x,int y,int width,double progress){}
    private final List<ExperienceBar> experienceBars=new ArrayList<>();
    private final List<RightClick> rightClicks=new ArrayList<>();
    private List<PlayerPageSurface.Navigation> navigation=List.of();
    private List<PlayerPageSurface.SidebarRow> sidebarRows=List.of();
    private String sidebarTitle="";
    private boolean sidebarProfile=true;
    private int sidebarScroll;
    private final List<AbstractWidget> body=new ArrayList<>();
    private int sidebar,left,top,panelWidth,panelHeight,scroll,contentHeight;
    private EditBox input,preservedInput;
    private String activeInputKey;
    private final Map<AbstractWidget,Hover> widgetHovers=new LinkedHashMap<>();
    private final Map<String,Object> pageState=new HashMap<>();
    private int nextY;
    private boolean draggingScroll;private double scrollGrab;
    private long themeRevision=UiTheme.revision;
    public FightScreen(){
        this(-1);
    }
    public FightScreen(String pageId){this(pageIndex(pageId));}
    private static int pageIndex(String pageId){
        if(pageId==null)return -1;
        var pages=PlayerPages.all();
        for(int index=0;index<pages.size();index++){
            if(pages.get(index).id().equals(pageId))return index;
        }
        return -1;
    }
    public FightScreen(int page){
        super(Minecraft.getInstance(),UiFonts.font(),Component.literal(page<0?"地下城与战斗":PlayerPages.at(page).title()),ThemeDomain.MENU);
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
        if(font!=null&&!(input!=null&&input.isFocused())&&!(getFocused() instanceof ThemedSlider&&isDragging()))build();
    }
    private void build(){
        try(var palette=themeScope()){buildContent();}
    }
    private void buildContent(){
        EditBox keep = preservedInput;
        input=null;
        if(keep!=null){
            for(var child:new ArrayList<>(children()))if(child!=keep)removeWidget(child);
        }else{
            clearWidgets();
            activeInputKey=null;
        }
        body.clear();
        widgetHovers.clear();
        lines.clear();styledLines.clear();hoverAreas.clear();
        cards.clear();drawings.clear();
        icons.clear();sidebarRows=List.of();sidebarTitle="";sidebarProfile=true;
        pictures.clear();experienceBars.clear();rightClicks.clear();navigation=List.of();
        sidebar=page>=0&&customPanel==null&&panelWidth>=480?112:0;
        nextY=page<0?0:PAGE_TOP_PADDING;
        buttonFixed(page<0?"关闭":"返回",left+panelWidth-50,top+7,42,()->{
            state.upload();if(page<0)onClose();else minecraft.gui.setScreen(new FightScreen());
        }
        );
        if(!state.ready){
            if(keep!=null){removeWidget(keep);preservedInput=null;activeInputKey=null;}
            line("正在读取服务器角色档案…");
            contentHeight=40;
            return;
        }
        if(page<0)menu();else if(customPanel==null)PlayerPageRenderers.render(PlayerPages.at(page).id(),this);
        if(keep!=null&&input!=keep){removeWidget(keep);if(preservedInput==keep)preservedInput=null;}
        contentHeight=nextY+12;
        setScroll(scroll);
    }
    private int bodyTop(){return top+36;}
    private int bodyHeight(){return panelHeight-66;}
    private int contentLeft(){
        return left+sidebar+(sidebar>0?18:8);
    }
    public int contentWidth(){
        return panelWidth-sidebar-(sidebar>0?26:16);
    }
    static void navigate(String pageId){
        int index=pageIndex(pageId);
        var client=Minecraft.getInstance();
        if(index<0){
            ClientSession.INSTANCE.upload();
            client.gui.setScreen(new FightScreen());
        }else if(!(client.gui.screen() instanceof FightScreen screen&&screen.page==index)){
            openPage(index);
        }
    }
    private static void openPage(int index){
        var state=ClientSession.INSTANCE;
        var destination=PlayerPages.at(index);
        if(!destination.screen()&&!state.ready)return;
        state.upload();
        var client=Minecraft.getInstance();
        if(destination.screen())client.gui.setScreen(new FightScreen(index));
        else if(client.gui.screen()!=null)client.gui.setScreen(null);
        if(state.ready&&!destination.openCommand().isEmpty())state.send(destination.openCommand());
    }
    private void menu(){
        int cell=(panelWidth-20)/4;
        for(int i=0;i<PlayerPages.all().size();i++){
            int index=i,row=i/4,col=i%4;
            AbstractWidget b=Button.builder(Component.empty(),x->{
                openPage(index);
            }
            ).bounds(left+10+col*cell,bodyTop()+row*68-scroll,cell-5,60).build(ThemedButton::new);
            var destination=PlayerPages.at(index);
            String shortcut=MenuInput.shortcutLabel(destination.id());
            var tooltip=Component.literal(destination.title());
            if(!shortcut.isEmpty())tooltip.append("\n").append(shortcut);
            b.setTooltip(Tooltip.create(UiFonts.decorate(tooltip)));
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
    public void line(cn.yumis.fight.modules.presentation.api.RichText text,int color){for(var chars:font.split(text.component(),Math.max(1,contentWidth()-24))){styledLines.add(new StyledLine(chars,nextY,color));nextY+=12;}}
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
    @Override public void rowSlider(int min, int max, int value, java.util.function.IntFunction<String> label,
                                    java.util.function.IntConsumer change) {
        var slider = new ThemedSlider(contentLeft()+12, bodyTop()+nextY-scroll, contentWidth()-24,
            min, max, value, label, change);
        body.add(addRenderableWidget(slider));
        nextY += slider.getHeight()+4;
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
            w.visible=w.getY()<bodyTop()+bodyHeight()&&w.getY()+w.getHeight()>bodyTop();
            
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
        UiTheme.titleBar(g,left+6,top+6,panelWidth-12,24);
        g.text(font,title,left+14,top+13,UiTheme.TEXT,false);
        UiTheme.card(g,contentLeft(),bodyTop(),(contentLeft()+contentWidth())-(contentLeft()),(bodyTop()+bodyHeight())-(bodyTop()));
        UiTheme.statusBar(g,left+6,top+panelHeight-28,panelWidth-12,22);
        g.text(font,page<0?"I / Esc 关闭":"I 返回菜单 · Esc 关闭 · 滚轮浏览",left+14,top+panelHeight-20,UiTheme.MUTED,false);
        if(sidebar>0&&(navigation.isEmpty()||UiTheme.pixelStyle())){
            UiTheme.card(g,left+6,bodyTop(),sidebar+2,bodyHeight());
        }
        if(sidebar>0&&navigation.isEmpty()){
            int yy=bodyTop()+12;
            if(sidebarProfile&&(sidebarRows.isEmpty()||bodyHeight()>=240)){
            g.text(font,cn.yumis.fight.modules.character.api.JobCatalog.displayName(state.progress.jobId(),state.progress.level,state.progress.awakened()),left+14,yy,UiTheme.GOLD,false);
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
        for(var drawing:drawings){
            int x=contentLeft()+12+drawing.x,y=bodyTop()+drawing.y-scroll;
            if(y>=bodyTop()+bodyHeight()||y+drawing.height<=bodyTop())continue;
            g.enableScissor(x,y,x+drawing.width,y+drawing.height);
            try{drawing.drawing.render(g,x,y);}finally{g.disableScissor();}
        }
        if(input!=null&&input.visible){
            UiTheme.borderlessInput(g,input.getX()-4,input.getY()-4,input.getWidth()+8,18,input.isFocused());
        }
        for(AbstractWidget widget:body)if(widget.visible)widget.extractRenderState(g,mx,my,a);
        for(Icon icon:icons){
            int yy=bodyTop()+icon.y-scroll;
            g.blit(ClientBootstrap.id(icon.path),contentLeft()+12+icon.x,yy,contentLeft()+12+icon.x+icon.size,yy+icon.size,0,1,0,1);
        }
        for(Picture picture:pictures){int yy=bodyTop()+picture.y-scroll;g.blit(ClientBootstrap.id(picture.path),contentLeft()+12+picture.x,yy,contentLeft()+12+picture.x+picture.width,yy+picture.height,picture.u0,picture.u1,picture.v0,picture.v1);}
        for(var bar:experienceBars){int x=contentLeft()+12+bar.x,y=bodyTop()+bar.y-scroll;g.blit(Identifier.withDefaultNamespace("textures/gui/sprites/hud/experience_bar_background.png"),x,y,x+bar.width,y+5,0,1,0,1);int filled=(int)Math.floor(bar.width*bar.progress);if(filled>0)g.blit(Identifier.withDefaultNamespace("textures/gui/sprites/hud/experience_bar_progress.png"),x,y,x+filled,y+5,0,(float)bar.progress,0,1);}
        for(Line l:lines){
            int y=bodyTop()+l.y-scroll;
            if(y>=bodyTop()&&y+9<=bodyTop()+bodyHeight())g.text(font,page==0?cn.yumis.fight.modules.skills.client.CombatAttributeTooltip.numbers(l.text):Component.literal(l.text),contentLeft()+12+l.x,y,0xff000000|l.color,false);
        }
        for(var line:styledLines){int y=bodyTop()+line.y-scroll;if(y>=bodyTop()&&y+9<=bodyTop()+bodyHeight())RichTextDrawing.line(g,font,line.text,contentLeft()+12,y,0xff000000|line.color);}
        if(page<0&&state.ready){
            int cell=(panelWidth-20)/4;
            var pages=PlayerPages.all();
            for(int i=0;i<pages.size();i++){
                int x=left+10+(i%4)*cell,y=bodyTop()+(i/4)*68-scroll;
                if(y<bodyTop()||y+60>bodyTop()+bodyHeight())continue;
                var destination=pages.get(i);
                g.text(font,destination.title(),x+(cell-5-font.width(destination.title()))/2,y+6,UiTheme.TEXT,false);
                int center=x+(cell-5)/2;
                g.blit(Identifier.fromNamespaceAndPath(ItemData.NS,"textures/gui/menu/"+destination.icon()+".png"),center-14,y+19,center+14,y+47,0,1,0,1);
                String shortcut=MenuInput.shortcutLabel(destination.id());
                if(!shortcut.isEmpty()){
                    String label=truncateToWidth(shortcut,cell-15);
                    g.text(font,label,x+cell-10-font.width(label),y+56-font.lineHeight,UiTheme.MUTED,false);
                }
            }
        }
        g.disableScissor();
        for(var child:children())if(child instanceof AbstractWidget widget&&!body.contains(widget))widget.extractRenderState(g,mx,my,a);
        if(customPanel!=null&&state.ready)customPanel.render(g,new ClientPagePanel.Region(contentLeft()+4,bodyTop()+4,contentWidth()-8,bodyHeight()-8),mx,my,a);
        if(my>=bodyTop()&&my<bodyTop()+bodyHeight())for(var area:hoverAreas){int x=contentLeft()+12+area.x,y=bodyTop()+area.y-scroll;if(mx>=x&&mx<x+area.width&&my>=y&&my<y+area.height){area.renderer.render(g,mx,my);break;}}
        if(minecraft.gui.screen()==this)for(var row:widgetHovers.entrySet()){
            var widget=row.getKey();
            if(widget.visible&&(!body.contains(widget)||my>=bodyTop()&&my<bodyTop()+bodyHeight())&&mx>=widget.getX()&&mx<widget.getX()+widget.getWidth()&&my>=widget.getY()&&my<widget.getY()+widget.getHeight()){
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
        var row=sidebarRows.get(index);if(event.button()==1){if(row.rename()!=null)row.rename().run();}else if(row.rename()!=null&&event.x()>=sidebarRenameX()-4)row.rename().run();else if(row.select()!=null&&!row.selected())row.select().run();return true;
    }
    private void setScroll(int value){int old=scroll;scroll=Math.clamp(value,0,Math.max(0,contentHeight-bodyHeight()));for(AbstractWidget widget:body)widget.setY(widget.getY()+old-scroll);updateVisibility();}
    private int sidebarListTop(){return sidebarRows.isEmpty()||!sidebarProfile&&sidebarTitle.isEmpty()?bodyTop()+12:bodyTop()+(bodyHeight()<240?32:142);}
    private int sidebarListHeight(){return Math.max(0,bodyTop()+bodyHeight()-sidebarListTop()-6);}
    private boolean insideSidebar(double x,double y){return !sidebarRows.isEmpty()&&sidebar>0&&x>=left+8&&x<left+sidebar+6&&y>=sidebarListTop()&&y<bodyTop()+bodyHeight()-6;}
    private int sidebarRenameX(){return left+sidebar-5-font.width(SIDEBAR_RENAME);}
    private String truncateToWidth(String value,int width){return font.width(value)<=width?value:font.plainSubstrByWidth(value,Math.max(1,width-font.width("…")))+"…";}
    private void drawSidebarRows(GuiGraphicsExtractor g,int mx,int my){
        if(sidebarRows.isEmpty())return;int start=sidebarListTop(),height=sidebarListHeight();
        if(!sidebarTitle.isEmpty())g.text(font,sidebarTitle,left+13,start-17,UiTheme.GOLD,false);if(height<=0)return;
        sidebarScroll=Math.clamp(sidebarScroll,0,Math.max(0,sidebarRows.size()*34-height));
        g.enableScissor(left+8,start,left+sidebar+7,start+height);
        for(int i=0;i<sidebarRows.size();i++){var row=sidebarRows.get(i);int y=start+i*34-sidebarScroll;if(y+32<start||y>=start+height)continue;
            UiTheme.card(g,left+10,y,sidebar-8,31);if(row.selected())g.fill(left+10,y,left+12,y+31,UiTheme.ACCENT);
            int titleWidth=row.rename()==null?sidebar-40:sidebarRenameX()-(left+15)-4;
            if(row.titleColors().isEmpty())g.text(font,truncateToWidth(row.title(),titleWidth),left+15,y+5,row.select()==null?UiTheme.MUTED:row.selected()?UiTheme.ACCENT:UiTheme.TEXT,false);
            else for(var line:font.split(new RichText(List.of(new RichText.Span(truncateToWidth(row.title(),titleWidth),row.titleColors()))).component(),Math.max(1,titleWidth)))RichTextDrawing.line(g,font,line,left+15,y+5,UiTheme.TEXT,false);
            g.text(font,truncateToWidth(row.detail(),sidebar-24),left+15,y+18,UiTheme.MUTED,false);
            if(row.rename()!=null)g.text(font,SIDEBAR_RENAME,sidebarRenameX(),y+5,UiTheme.MUTED,false);
            if(insideSidebar(mx,my)&&my>=y&&my<y+31)g.setComponentTooltipForNextFrame(font,row.select()==null?List.of(Component.literal(row.title()),Component.literal(row.detail()),Component.literal("20级转职后解锁职业栏。")):List.of(Component.literal(row.title()),Component.literal(row.detail())),mx,my);
        }
        g.disableScissor();if(sidebarRows.size()*34>height){int thumb=Math.max(8,height*height/(sidebarRows.size()*34)),y=start+(height-thumb)*sidebarScroll/Math.max(1,sidebarRows.size()*34-height);g.fill(left+sidebar+4,y,left+sidebar+6,y+thumb,UiTheme.MUTED);}
    }
    @Override public void sidebarRows(String title,List<PlayerPageSurface.SidebarRow> rows){sidebarRows(title,rows,true);}
    @Override public void sidebarRows(String title,List<PlayerPageSurface.SidebarRow> rows,boolean profile){sidebarTitle=title;sidebarRows=List.copyOf(rows);sidebarProfile=profile;}
    private void dragScroll(double y){int thumb=Math.max(12,bodyHeight()*bodyHeight()/Math.max(1,contentHeight));setScroll((int)Math.round((y-bodyTop()-scrollGrab)*Math.max(0,contentHeight-bodyHeight())/Math.max(1,bodyHeight()-thumb)));}
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){if(draggingScroll){dragScroll(event.y());return true;}if(customPanel!=null&&customPanel.mouseDragged(event,dx,dy))return true;return super.mouseDragged(event,dx,dy);}
    @Override public boolean mouseReleased(MouseButtonEvent event){boolean was=draggingScroll;draggingScroll=false;if(customPanel!=null&&customPanel.mouseReleased(event))return true;return was||super.mouseReleased(event);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){
        if(closeWithPageShortcut(InputConstants.Type.MOUSE.getOrCreate(event.button())))return true;
        if(sidebarClick(event))return true;
        if(customPanel==null&&event.button()==0&&contentHeight>bodyHeight()&&event.x()>=left+panelWidth-14&&event.x()<=left+panelWidth&&event.y()>=bodyTop()&&event.y()<=bodyTop()+bodyHeight()){
            int thumb=Math.max(12,bodyHeight()*bodyHeight()/contentHeight);
            int at=bodyTop()+(bodyHeight()-thumb)*scroll/Math.max(1,contentHeight-bodyHeight());
            scrollGrab=event.y()>=at&&event.y()<at+thumb?event.y()-at:thumb/2d;
            draggingScroll=true;
            dragScroll(event.y());
            return true;
        }
        if(customPanel!=null&&customPanel.mouseClicked(event,twice))return true;
        if(event.button()==1&&event.y()>=bodyTop()&&event.y()<bodyTop()+bodyHeight())for(var area:rightClicks){
            int x=contentLeft()+12+area.x,y=bodyTop()+area.y-scroll;
            if(event.x()>=x&&event.x()<x+area.width&&event.y()>=y&&event.y()<y+area.height){
                area.action.run();
                return true;
            }
        }
        return clickWidgets(event,twice);
    }
    private boolean clickWidgets(MouseButtonEvent event,boolean twice){
        if(event.y()>=bodyTop()&&event.y()<bodyTop()+bodyHeight())return super.mouseClicked(event,twice);
        var visible=body.stream().filter(w->w.visible).toList();
        for(var widget:visible)widget.visible=false;
        try{return super.mouseClicked(event,twice);}finally{for(var widget:visible)widget.visible=true;}
    }
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
        if(!(getFocused() instanceof EditBox box&&box.isFocused())){
            if(MenuInput.MENU.matches(e)){
                state.upload();
                if(page<0)onClose();
                else minecraft.gui.setScreen(new FightScreen());
                return true;
            }
            var key=InputConstants.getKey(e);
            if(closeWithPageShortcut(key))return true;
            var destination=MenuInput.pageShortcut(key);
            if(destination!=null){
                MenuInput.open(destination);
                return true;
            }
        }
        return super.keyPressed(e);
    }
    private boolean closeWithPageShortcut(InputConstants.Key key){
        if(page<0||!MenuInput.matchesPageShortcut(PlayerPages.at(page).id(),key))return false;
        onClose();
        return true;
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
    public void drawing(int x,int y,int width,int height,Drawing drawing){drawings.add(new PageDrawing(x,y,width,height,drawing));}
    public void icon(String path,int x,int y,int size){icons.add(new Icon(path,x,y,size));}
    public void image(String path,int x,int y,int width,int height,float u0,float u1,float v0,float v1){pictures.add(new Picture(path,x,y,width,height,u0,u1,v0,v1));}
    public void experienceBar(int x,int y,int width,double progress){experienceBars.add(new ExperienceBar(x,y,width,Math.clamp(progress,0,1)));}
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
        int x=contentLeft()+12,y=bodyTop()+nextY-scroll,w=contentWidth()-24;
        EditBox box;
        if(preservedInput!=null&&key.equals(activeInputKey)){
            box=preservedInput;
            box.setX(x);box.setY(y);box.setWidth(w);box.setHeight(20);
            box.setMaxLength(maximum);
            if(UiTheme.pixelStyle()){
                box.setTextColor(UiTheme.TEXT);
                box.setHint(Component.literal(truncateToWidth(label,box.getWidth())));
            }
            if(!children().contains(box))addRenderableWidget(box);
        }else{
            box=new EditBox(font,x,y,w,20,Component.literal(label));
            box.setValue(remember(key,""));box.setMaxLength(maximum);box.setBordered(false);
            if(UiTheme.pixelStyle()){
                box.setTextColor(UiTheme.TEXT);
                box.setHint(Component.literal(truncateToWidth(label,box.getWidth())));
            }
            addRenderableWidget(box);
        }
        activeInputKey=key;
        final EditBox currentBox=box;
        box.setResponder(value->{
            rememberValue(key,value);
            preservedInput=currentBox;
            try{
                build();
            }finally{
                if(preservedInput==currentBox)preservedInput=null;
            }
        });
        input=box;body.add(box);nextY+=26;return box::getValue;
    }
    @SuppressWarnings("unchecked") public <T>T remember(String key,T fallback){return (T)pageState.getOrDefault(key,fallback);}
    public void rememberValue(String key,Object value){pageState.put(key,value);}
    public void rebuild(){build();}
    public void resize(){init();}
}
