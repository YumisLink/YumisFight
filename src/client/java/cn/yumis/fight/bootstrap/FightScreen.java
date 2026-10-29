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
    private final List<AbstractWidget> body=new ArrayList<>();
    private int sidebar,left,top,panelWidth,panelHeight,scroll,contentHeight;
    private EditBox input;
    private final Map<AbstractWidget,Hover> widgetHovers=new LinkedHashMap<>();
    private final Map<String,Object> pageState=new HashMap<>();
    private int nextY;
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
        icons.clear();
        nextY=0;
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
        if(sidebar>0){
            UiTheme.card(g,left+6,bodyTop(),(left+sidebar+8)-(left+6),(bodyTop()+bodyHeight())-(bodyTop()));
            int yy=bodyTop()+12;
            g.text(font,state.progress.hasJob(cn.yumis.fight.modules.character.api.JobCatalog.SWORDMASTER)?"剑魂":"鬼剑士",left+14,yy,UiTheme.GOLD,false);
            g.text(font,"战斗等级 "+state.progress.level,left+14,yy+21,UiTheme.TEXT,false);
            g.text(font,"可用 SP  "+state.progress.sp,left+14,yy+44,UiTheme.GOLD,false);
            g.text(font,"自由点  "+state.progress.free(),left+14,yy+65,UiTheme.ACCENT,false);
            g.text(font,ClientSession.now()<state.resources.combatUntil?"战斗中":"脱战",left+14,yy+94,UiTheme.MUTED,false);
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
            g.fill(left+panelWidth-6,y,left+panelWidth-3,y+bar,UiTheme.ACCENT);
        }
        if(state.ready)Hud.gold(g,state);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){if(customPanel!=null&&customPanel.mouseClicked(event,twice))return true;return super.mouseClicked(event,twice);}
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){
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
    public void hover(AbstractWidget widget,Hover renderer){widgetHovers.put(widget,renderer);}
    public void hover(int x,int y,int width,int height,Hover renderer){hoverAreas.add(new HoverArea(x,y,width,height,renderer));}
    public java.util.function.Supplier<String> textInput(String key,String label,int maximum){
        var box=new EditBox(font,contentLeft()+12,bodyTop()+nextY-scroll,contentWidth()-24,20,Component.literal(label));
        box.setValue(remember(key,""));box.setMaxLength(maximum);box.setBordered(false);box.setResponder(value->rememberValue(key,value));
        input=box;body.add(addRenderableWidget(box));nextY+=26;return box::getValue;
    }
    @SuppressWarnings("unchecked") public <T>T remember(String key,T fallback){return (T)pageState.getOrDefault(key,fallback);}
    public void rememberValue(String key,Object value){pageState.put(key,value);}
    public void rebuild(){build();}
    public void resize(){init();}
}
