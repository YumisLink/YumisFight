package cn.yumis.fight.modules.inventory.client;

import cn.yumis.fight.mixin.ContainerScreenSizeAccessor;
import cn.yumis.fight.modules.inventory.api.CategoryBags;
import cn.yumis.fight.modules.inventory.api.DimensionalMenu;
import cn.yumis.fight.modules.inventory.api.StorageRules;
import cn.yumis.fight.modules.presentation.api.client.*;
import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.*;
import java.util.function.Consumer;

import static cn.yumis.fight.modules.inventory.api.DimensionalMenu.*;

/** Split view of personal inventory, category storage and the ordinary dimensional chest. */
public final class DimensionalScreen extends ThemedContainerScreen<DimensionalMenu> {
    private static final Gson JSON=new Gson();
    private static final int CATEGORY_BAR=170;
    private static Runnable sale=()->{};
    private static Consumer<CategoryBags.Kind> shop=kind->{};
    private final Map<CategoryBags.Kind,Button> tabs=new EnumMap<>(CategoryBags.Kind.class);
    private final Map<Integer,String> names=new HashMap<>(),ids=new HashMap<>();
    private final Map<Integer,Object> nameComponents=new HashMap<>();
    private EditBox search;
    private Button upgrade,autoStore,onlyWhenFull,materials,purchase;
    private String upgradePreview="",lastFilter="";
    private long lastContents=-1,queryChangedAt;
    private int lastCapacity=-1;
    private Object language;
    private boolean selectingMaterials;
    private Boolean draggingCategory;

    public DimensionalScreen(DimensionalMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title,334,198,ClientSettings.bagUsesMenuTheme()?ThemeDomain.MENU:ThemeDomain.MACHINE);
    }

    public static void links(Runnable openSale,Consumer<CategoryBags.Kind> openProduct){
        sale=Objects.requireNonNull(openSale);
        shop=Objects.requireNonNull(openProduct);
    }

    @Override protected void init(){
        int panelWidth=Math.min(width-6,Math.max(334,width*4/5));
        int panelHeight=Math.min(height-6,Math.max(196,height*4/5));
        var size=(ContainerScreenSizeAccessor)(Object)this;
        size.ydnf$width(panelWidth);
        size.ydnf$height(panelHeight);
        int columns=Math.clamp((panelWidth-BAG_X-14)/SLOT_SIZE,1,48);
        int rows=Math.clamp((panelHeight-BAG_Y-28)/SLOT_SIZE,1,48);
        menu.resize(columns,rows);
        super.init();
        command(viewportButton(columns,rows));

        int right=panelWidth-8;
        int searchEnd=right-42;
        search=addRenderableWidget(new EditBox(UiFonts.font(),leftPos+BAG_X,topPos+24,Math.max(20,searchEnd-BAG_X-3),18,Component.literal("搜索物品")));
        search.setMaxLength(64);
        search.setHint(Component.literal("搜索物品…"));
        search.setValue(menu.query);
        search.setResponder(text->queryChangedAt=ClientUi.now()+150);
        button("整理",searchEnd,24,42,()->command(SORT)).setTooltip(Tooltip.create(Component.literal("整理右侧次元箱")));

        tabs.clear();
        for(var kind:CategoryBags.Kind.values()){
            tabs.put(kind,button(kind.title,LEFT_X+54*kind.ordinal(),104,51,()->{
                menu.category(kind);
                command(tabButton(kind));
                refreshCategory();
            }));
        }
        materials=button("袋内用料",LEFT_X,125,162,()->{
            selectingMaterials=!selectingMaterials;
            menu.selectMaterial(-1);
            refreshCategory();
        });
        purchase=button("前往商城",LEFT_X,125,162,()->{
            var kind=menu.category();
            onClose();
            shop.accept(kind);
        });
        int footer=panelHeight-23;
        autoStore=button("",LEFT_X,footer,78,()->{
            ClientSettings.bagAutoStore=!ClientSettings.bagAutoStore;
            ClientSettings.save();
            refreshOptions();
        });
        onlyWhenFull=button("",90,footer,79,()->{
            ClientSettings.bagStoreOnlyWhenFull=!ClientSettings.bagStoreOnlyWhenFull;
            ClientSettings.save();
            refreshOptions();
        });
        autoStore.setTooltip(Tooltip.create(Component.literal("优先自动收纳到已开通的分类背包")));
        onlyWhenFull.setTooltip(Tooltip.create(Component.literal("控制普通物品何时进入右侧次元箱")));
        int rightButton=Math.max(1,(right-BAG_X-6)/3);
        button(rightButton>=60?"一键归类":"归类",BAG_X,footer,rightButton,()->command(CATEGORIZE))
            .setTooltip(Tooltip.create(Component.literal("把主背包和右侧次元箱内能归类的物品移入已开通的分类背包")));
        button(rightButton>=56?"出售物品":"出售",BAG_X+rightButton+3,footer,rightButton,sale)
            .setTooltip(Tooltip.create(Component.literal("出售物品")));
        upgrade=button(menu.extra.bagTier>=16?"最高档":"升级",BAG_X+2*(rightButton+3),footer,rightButton,()->command(UPGRADE));
        upgradePreview="";
        refreshUpgrade();
        refreshOptions();
        refreshCategory();
        lastContents=-1;
    }

    private void command(int button){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,button);}
    private Button button(String label,int x,int y,int width,Runnable action){
        return addRenderableWidget(Button.builder(Component.literal(label),ignored->action.run())
            .bounds(leftPos+x,topPos+y,width,18).build(ThemedButton::new));
    }

    private void refreshCategory(){
        var kind=menu.category();
        var bags=menu.extra.categories;
        for(var entry:tabs.entrySet()){
            var tab=entry.getKey();
            entry.getValue().active=tab!=kind;
            entry.getValue().setTooltip(Tooltip.create(Component.literal(tab.title+"背包"+(bags.unlocked(tab)?"":" · 未开通"))));
        }
        materials.visible=bags.unlocked(kind);
        purchase.visible=!materials.visible;
        if(purchase.visible)purchase.setMessage(Component.literal("购买"+kind.title+"背包"));
        if(materials.visible){
            var selected=menu.selectedMaterial();
            materials.setMessage(selected==null?Component.literal(selectingMaterials?"选择要使用的物品 · 取消":"袋内用料")
                :Component.literal("已选 "+selected.item().getHoverName().getString()+" · 取消"));
            materials.setTooltip(Tooltip.create(Component.literal("选择袋内物品后，右键目标装备进行加工")));
        }
    }

    private void refreshOptions(){
        autoStore.setMessage(Component.literal("自动收纳 "+(ClientSettings.bagAutoStore?"开":"关")));
        onlyWhenFull.setMessage(Component.literal("满包才收 "+(ClientSettings.bagStoreOnlyWhenFull?"开":"关")));
        onlyWhenFull.active=ClientSettings.bagAutoStore;
    }

    private void filter(){
        String query=search.getValue();
        int capacity=menu.extra.bagLimit();
        long revision=menu.contentsRevision();
        Object currentLanguage=net.minecraft.locale.Language.getInstance();
        if(language!=currentLanguage){
            language=currentLanguage;
            names.clear();
            ids.clear();
            nameComponents.clear();
            lastContents=-1;
        }
        if(query.equals(lastFilter)&&lastContents==revision&&lastCapacity==capacity)return;
        lastContents=revision;
        lastCapacity=capacity;
        lastFilter=query;
        if(query.isEmpty()&&menu.query.isEmpty())return;
        String normalized=query.toLowerCase(Locale.ROOT);
        List<Integer> indices=new ArrayList<>();
        for(int i=0;i<capacity&&!query.isEmpty();i++){
            var stack=menu.slots.get(i).getItem();
            if(stack.isEmpty()){nameComponents.remove(i);names.remove(i);ids.remove(i);continue;}
            Object components=stack.getComponentsPatch();
            String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if(!Objects.equals(nameComponents.get(i),components)||!id.equals(ids.get(i))){
                nameComponents.put(i,components);
                ids.put(i,id);
                names.put(i,stack.getHoverName().getString().toLowerCase(Locale.ROOT));
            }
            if(names.get(i).contains(normalized)||id.contains(normalized))indices.add(i);
        }
        if(query.equals(menu.query)&&menu.matches(indices))return;
        menu.filter(query,indices);
        JsonObject request=new JsonObject();
        request.addProperty("menu",menu.containerId);
        request.addProperty("query",query);
        request.add("indices",JSON.toJsonTree(indices));
        ClientUi.current().send("bagView",request);
    }

    private void refreshUpgrade(){
        String text=menu.preview.has("requirements")?menu.preview.get("requirements").getAsString():"材料信息未就绪";
        if(!text.equals(upgradePreview)){upgradePreview=text;upgrade.setTooltip(Tooltip.create(Component.literal(text)));}
        upgrade.active=menu.extra.bagTier<16&&menu.preview.has("available")&&menu.preview.get("available").getAsBoolean();
    }

    @Override protected void containerTick(){
        super.containerTick();
        refreshUpgrade();
        refreshCategory();
        if(ClientUi.now()>=queryChangedAt)filter();
        menu.layout();
    }

    @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float alpha){
        UiTheme.check();
        g.fill(0,0,width,height,UiTheme.OVERLAY);
        UiTheme.panel(g,leftPos,topPos,imageWidth,imageHeight);
        if(UiTheme.pixelStyle())UiTheme.titleBar(g,leftPos+4,topPos+4,imageWidth-8,16);
        for(int i=0;i<menu.slots.size();i++){
            var slot=menu.slots.get(i);
            if(!slot.isActive())continue;
            int x=leftPos+slot.x,y=topPos+slot.y;
            UiTheme.slot(g,x,y);
            if(menu.kind(i)==CategoryBags.Kind.CURRENCY&&!slot.hasItem()){
                g.item(CategoryBags.currency(slot.getContainerSlot()),x,y);
                g.fill(x,y,x+16,y+16,0xa0202428);
            }
        }
        drawScrollbar(g,false);
        drawScrollbar(g,true);
        if(!menu.query.isEmpty()&&menu.totalRows()==0)
            g.text(UiFonts.font(),"没有匹配物品",leftPos+BAG_X+4,topPos+BAG_Y+8,UiTheme.MUTED,false);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g,int x,int y){
        var font=UiFonts.font();
        g.text(font,"玩家背包",LEFT_X,8,UiTheme.TEXT,false);
        String usage=menu.usedSlots()+"/"+menu.extra.bagLimit()+" 格 · "+menu.extra.bagTier+"阶";
        String name=StorageRules.NAMES[menu.extra.bagTier-1];
        int right=imageWidth-8-font.width(usage);
        if(right>BAG_X+font.width(name)+3)g.text(font,name,BAG_X,8,UiTheme.TEXT,false);
        g.text(font,usage,Math.max(BAG_X,right),8,UiTheme.MUTED,false);
    }

    @Override protected void drawContents(GuiGraphicsExtractor g,int mx,int my,float alpha){
        super.drawContents(g,mx,my,alpha);
        g.nextStratum();
        var selected=menu.selectedMaterial();
        var font=UiFonts.font();
        for(int i=0;i<menu.slots.size();i++){
            var kind=menu.kind(i);
            var slot=menu.slots.get(i);
            if(kind==null||!slot.isActive())continue;
            int x=leftPos+slot.x,y=topPos+slot.y;
            if(selected!=null&&selected.kind()==kind&&selected.slot()==slot.getContainerSlot())g.fill(x,y,x+16,y+16,0x6670c7e5);
            if(kind!=CategoryBags.Kind.CURRENCY)continue;
            long count=menu.extra.categories.quantity(slot.getContainerSlot());
            if(count>0){
                String display=amount(count);
                int textWidth=font.width(display);
                float scale=Math.min(1f,15f/Math.max(1,textWidth));
                g.pose().pushMatrix();
                g.pose().translate(x+15,y+16);
                g.pose().scale(scale,scale);
                g.text(font,display,-textWidth,-font.lineHeight+1,0xffffffff,true);
                g.pose().popMatrix();
            }
            if(mx>=x&&mx<x+16&&my>=y&&my<y+16){
                var currency=CategoryBags.currency(slot.getContainerSlot());
                var lines=new ArrayList<>(Screen.getTooltipFromItem(minecraft,currency));
                lines.add(Component.literal("已收纳 "+count+" 个"));
                g.setComponentTooltipForNextFrame(font,lines,mx,my,currency);
            }
        }
    }

    private static String amount(long count){
        if(count<1000)return Long.toString(count);
        String[] units={"k","m","b","t","q","Q"};
        int unit=0;
        while(count>=1_000_000&&unit<units.length-1){count/=1000;unit++;}
        return count/1000+units[unit];
    }

    private int barX(boolean category){return category?CATEGORY_BAR:BAG_X+menu.columns()*SLOT_SIZE;}
    private int barY(boolean category){return category?CATEGORY_Y:BAG_Y;}
    private int barHeight(boolean category){return (category?menu.categoryRows():menu.rows())*SLOT_SIZE;}
    private int maximum(boolean category){return category?menu.categoryMaxScroll():menu.maxScroll();}
    private int scrollRow(boolean category){return category?menu.categoryScroll():menu.scrollRow();}
    private int thumb(boolean category){
        int rows=category?menu.categoryRows():menu.rows();
        int total=category?menu.categoryTotalRows():menu.totalRows();
        return Math.max(8,barHeight(category)*rows/Math.max(1,total));
    }
    private void drawScrollbar(GuiGraphicsExtractor g,boolean category){
        int max=maximum(category);
        if(max==0)return;
        int h=barHeight(category),thumb=thumb(category);
        UiTheme.scrollbar(g,leftPos+barX(category),topPos+barY(category)-1,h,thumb,(h-thumb)*scrollRow(category)/max);
    }
    private void scroll(boolean category,int row){
        int next=Math.clamp(row,0,maximum(category));
        if(next==scrollRow(category))return;
        if(category){menu.scrollCategory(next);command(categoryScrollButton(next));}
        else{menu.scrollTo(next);command(bagScrollButton(next));}
    }
    private boolean onScrollbar(boolean category,double x,double y){
        int left=leftPos+barX(category),top=topPos+barY(category);
        return maximum(category)>0&&x>=left&&x<left+10&&y>=top&&y<top+barHeight(category);
    }
    private void drag(boolean category,double mouseY){
        int thumb=thumb(category);
        double fraction=(mouseY-topPos-barY(category)-thumb/2d)/Math.max(1,barHeight(category)-thumb);
        scroll(category,(int)Math.round(Math.clamp(fraction,0,1)*maximum(category)));
    }

    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){
        if(y>=topPos+CATEGORY_Y&&y<topPos+CATEGORY_Y+barHeight(true)&&x>=leftPos+LEFT_X&&x<leftPos+BAG_X){
            scroll(true,menu.categoryScroll()-(int)Math.signum(sy));return true;
        }
        if(y>=topPos+BAG_Y&&y<topPos+BAG_Y+barHeight(false)&&x>=leftPos+BAG_X&&x<leftPos+imageWidth){
            scroll(false,menu.scrollRow()-(int)Math.signum(sy));return true;
        }
        return super.mouseScrolled(x,y,sx,sy);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){
        if(closeWithBagShortcut(InputConstants.Type.MOUSE.getOrCreate(event.button())))return true;
        if(event.button()==0&&(onScrollbar(true,event.x(),event.y())||onScrollbar(false,event.x(),event.y()))){
            draggingCategory=onScrollbar(true,event.x(),event.y());
            drag(draggingCategory,event.y());
            return true;
        }
        if(event.button()==0&&selectingMaterials&&menu.getCarried().isEmpty()){
            for(int i=menu.categoryStart(menu.category()),end=i+CategoryBags.size(menu.category());i<end;i++){
                var slot=menu.slots.get(i);
                double x=event.x()-leftPos-slot.x,y=event.y()-topPos-slot.y;
                if(slot.isActive()&&x>=-1&&x<17&&y>=-1&&y<17){
                    menu.selectMaterial(i);
                    refreshCategory();
                    return true;
                }
            }
        }
        return super.mouseClicked(event,twice);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(draggingCategory!=null){drag(draggingCategory,event.y());return true;}
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){
        if(draggingCategory!=null){draggingCategory=null;return true;}
        return super.mouseReleased(event);
    }
    @Override public boolean keyPressed(KeyEvent event){
        if(search.isFocused())return super.keyPressed(event);
        if(MenuInput.MENU.matches(event)){onClose();MenuInput.open();return true;}
        if(closeWithBagShortcut(InputConstants.getKey(event)))return true;
        return super.keyPressed(event);
    }
    private boolean closeWithBagShortcut(InputConstants.Key key){
        if(!MenuInput.matchesPageShortcut("bag",key))return false;
        onClose();
        return true;
    }
}
