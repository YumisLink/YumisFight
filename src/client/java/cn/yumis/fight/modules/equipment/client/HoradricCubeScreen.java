package cn.yumis.fight.modules.equipment.client;

import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.presentation.api.client.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Familiar crafting-table layout; previews never put an uncommitted result in a real slot. */
public final class HoradricCubeScreen extends ThemedContainerScreen<HoradricCubeMenu> {
    private CraftingEssences.Recipe selected=CraftingEssences.recipes().getFirst();
    public HoradricCubeScreen(HoradricCubeMenu menu,Inventory inventory,Component title){super(menu,inventory,title,176,166,ThemeDomain.MACHINE);inventoryLabelY=72;}
    @Override protected void init(){
        super.init();
        addRenderableWidget(Button.builder(Component.literal("配方"),b->minecraft.gui.pushScreenLayer(new Recipes(this))).bounds(leftPos+134,topPos+2,34,14).build(ThemedButton::new));
    }
    @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.blit(Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png"),leftPos,topPos,leftPos+176,topPos+166,0,176f/256,0,166f/256);
        var match=menu.initialContentsReady?CraftingEssences.match(menu.grid()):null;
        var recipe=match==null?selected:match;
        if(match==null){
            var material=CraftingEssences.ingredient(recipe);
            for(int index:new int[]{1,3,5,7})if(menu.getSlot(index+1).getItem().isEmpty()){var slot=menu.getSlot(index+1);g.item(material,leftPos+slot.x,topPos+slot.y);g.fill(leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0x88c6c6c6);}
            if(menu.getSlot(5).getItem().isEmpty())g.item(cn.yumis.fight.platform.ItemRegistry.item("exalted_stone",1),leftPos+48,topPos+35);
            if(recipe.essence().paper()&&menu.getSlot(1).getItem().isEmpty())g.item(new ItemStack(net.minecraft.world.item.Items.PAPER),leftPos+30,topPos+17);
        }
        var output=cn.yumis.fight.platform.ItemRegistry.item(recipe.essence().id(),1);
        g.item(output,leftPos+124,topPos+35);
        if(match==null)g.fill(leftPos+124,topPos+35,leftPos+140,topPos+51,0x88c6c6c6);
        g.text(UiFonts.font(),match==null?"配方预览":"点击制作",leftPos+119,topPos+57,0xff404040,false);
    }
    @Override protected void drawContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.drawContents(g,mx,my,delta);
        var match=menu.initialContentsReady?CraftingEssences.match(menu.grid()):null;
        if(mx>=leftPos+123&&mx<leftPos+141&&my>=topPos+34&&my<topPos+52){
            var recipe=match==null?selected:match;var output=cn.yumis.fight.platform.ItemRegistry.item(recipe.essence().id(),1);
            var lines=new ArrayList<>(Screen.getTooltipFromItem(minecraft,output));
            lines.add(Component.literal(match==null?"按预览摆放材料；有开孔、宝石、锁定、工艺或腐化的武器不能作材料":"左键合成1枚到手中；Shift点击批量合成到背包"));
            g.setComponentTooltipForNextFrame(UiFonts.font(),lines,mx,my,output);
        }else if(match==null){
            for(int i=0;i<9;i++){var slot=menu.getSlot(i+1);if(!slot.getItem().isEmpty()||mx<leftPos+slot.x||mx>=leftPos+slot.x+16||my<topPos+slot.y||my>=topPos+slot.y+16)continue;
                ItemStack ingredient=i==4?cn.yumis.fight.platform.ItemRegistry.item("exalted_stone",1):i==0&&selected.essence().paper()?new ItemStack(net.minecraft.world.item.Items.PAPER):i==1||i==3||i==5||i==7?CraftingEssences.ingredient(selected):ItemStack.EMPTY;
                if(!ingredient.isEmpty())g.setComponentTooltipForNextFrame(UiFonts.font(),Screen.getTooltipFromItem(minecraft,ingredient),mx,my,ingredient);
            }
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){
        if(event.button()==0&&event.x()>=leftPos+123&&event.x()<leftPos+141&&event.y()>=topPos+34&&event.y()<topPos+52){
            if(!menu.initialContentsReady||!ClientUi.current().ready())return true;
            if(CraftingEssences.match(menu.grid())==null){ClientUi.current().message("材料、排列或装备保护条件不符合配方");return true;}
            var ui=ClientUi.current();ui.upload();var request=ui.operation("cubeCraft");request.addProperty("menu",menu.containerId);
            request.addProperty("toInventory",InputConstants.isKeyDown(minecraft.getWindow(),GLFW.GLFW_KEY_LEFT_SHIFT)||InputConstants.isKeyDown(minecraft.getWindow(),GLFW.GLFW_KEY_RIGHT_SHIFT));
            ui.send("cubeCraft",request);return true;
        }
        return super.mouseClicked(event,twice);
    }
    private static final class Recipes extends ThemedScreen {
        private final HoradricCubeScreen parent;
        private final List<CraftingEssences.Recipe> recipes=CraftingEssences.recipes();
        private int page,left,top,rows,panelHeight;
        Recipes(HoradricCubeScreen parent){super(Component.literal("赫拉迪姆方块配方"),ThemeDomain.MACHINE);this.parent=parent;}
        @Override protected void init(){
            panelHeight=Math.min(height-16,350);left=(width-300)/2;top=(height-panelHeight)/2;rows=Math.max(1,(panelHeight-68)/24);page=Math.clamp(page,0,(recipes.size()-1)/rows);clearWidgets();
            addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(left+248,top+7,44,20).build(ThemedButton::new));
            for(int i=page*rows;i<Math.min(recipes.size(),(page+1)*rows);i++){
                var recipe=recipes.get(i);String label=recipe.essence().name()+(recipe.weapon()==null?"":" · 四把"+recipe.weapon().title());
                addRenderableWidget(Button.builder(Component.literal(label),b->{parent.selected=recipe;onClose();}).bounds(left+8,top+35+(i-page*rows)*24,284,21).build(ThemedButton::new));
            }
            addRenderableWidget(Button.builder(Component.literal("上一页"),b->{page--;init();}).bounds(left+8,top+panelHeight-27,64,20).build(ThemedButton::new)).active=page>0;
            addRenderableWidget(Button.builder(Component.literal("下一页"),b->{page++;init();}).bounds(left+228,top+panelHeight-27,64,20).build(ThemedButton::new)).active=(page+1)*rows<recipes.size();
        }
        @Override protected void drawBackground(GuiGraphicsExtractor g,int x,int y,float delta){g.fill(0,0,width,height,UiTheme.OVERLAY);UiTheme.panel(g,left,top,300,panelHeight);g.text(UiFonts.font(),title,left+10,top+13,UiTheme.TEXT,false);}
        @Override public void tick(){if(minecraft.player==null||minecraft.player.containerMenu!=parent.getMenu())minecraft.gui.setScreen(null);}
        @Override public void onClose(){minecraft.gui.popScreenLayer();}
        @Override public boolean isPauseScreen(){return false;}
    }
}
