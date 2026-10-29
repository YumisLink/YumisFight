package cn.yumis.fight.modules.equipment.client;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.equipment.api.client.EquipmentClient;

import cn.yumis.fight.modules.presentation.api.client.ThemedButton;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.equipment.api.EquipmentTemplates;
import cn.yumis.fight.modules.equipment.api.ArmorType;
import cn.yumis.fight.modules.equipment.api.JewelryType;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.equipment.api.EquipmentRules;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Template selection is a screen layer: the server-side fabricator remains open. */
public final class EquipmentSelectionScreen extends ThemedScreen {
    private final net.minecraft.client.gui.Font font=UiFonts.font();
    private record Entry(Slot slot,Weapon weapon,ItemStack preview,Button button,int row){}
    private final FabricatorScreen parent;
    private final Slot selectedSlot;
    private final Weapon selectedWeapon;
    private final ArmorType selectedArmor;
    private final JewelryType selectedJewelry;
    private final int level;
    private final List<Entry> entries=new ArrayList<>();
    private int left,top,panelWidth,panelHeight,bodyTop,bodyHeight,columns,scroll,contentHeight;
    EquipmentSelectionScreen(FabricatorScreen parent,Slot slot,Weapon weapon,ArmorType armor,JewelryType jewelry,int level){
        super(Component.literal("选择制作装备"),ThemeDomain.MACHINE);this.parent=parent;this.selectedSlot=slot;this.selectedWeapon=weapon;this.selectedArmor=armor;this.selectedJewelry=jewelry;this.level=level;
    }
    @Override protected void init(){
        entries.clear();
        panelWidth=Math.min(width-16,440);panelHeight=Math.min(height-16,340);
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;bodyTop=top+34;bodyHeight=panelHeight-44;
        columns=panelWidth>=360?3:panelWidth>=235?2:1;
        addRenderableWidget(Button.builder(UiFonts.decorate(Component.literal("返回")),b->onClose()).bounds(left+panelWidth-52,top+7,44,20).build(ThemedButton::new));
        for(var template:EquipmentTemplates.creative())add(template.slot,template.weapon,template.armorType,template.jewelryType);
        contentHeight=((entries.size()+columns-1)/columns)*54;
        scroll=Math.clamp(scroll,0,Math.max(0,contentHeight-bodyHeight));position();
    }
    private void add(Slot slot,Weapon weapon,ArmorType armor,JewelryType jewelry){
        int index=entries.size(),row=index/columns,cell=(panelWidth-20)/columns;
        String name=slot==Slot.WEAPON?weapon.title():slot.armor()?armor.title()+slot.title():slot.jewelry()?jewelry.title()+slot.title():slot.title();
        var button=Button.builder(UiFonts.decorate(Component.literal(name)),b->{parent.selectTemplate(slot,weapon,armor,jewelry);onClose();}).bounds(left+8+(index%columns)*cell,bodyTop+row*54-scroll,cell-5,48).build(ThemedButton::new);
        ((ThemedButton)button).textInset=30;
        button.active=EquipmentClient.current().progress().level>=slot.minimum();
        ((ThemedButton)button).selected=slot==selectedSlot&&(slot!=Slot.WEAPON||weapon==selectedWeapon)&&armor==selectedArmor&&jewelry==selectedJewelry;
        int previewLevel=Math.max(slot.minimum(),Math.min(level,EquipmentClient.current().progress().level));
        ItemStack preview=EquipmentItems.gear(EquipmentRules.create(slot,weapon,Rarity.COMMON,previewLevel,armor,jewelry,new Random(0)));
        entries.add(new Entry(slot,weapon,preview,addRenderableWidget(button),row));
    }
    private void position(){
        for(Entry e:entries){e.button.setY(bodyTop+e.row*54-scroll);e.button.visible=e.button.getY()>=bodyTop&&e.button.getY()+48<=bodyTop+bodyHeight;}
    }
    @Override protected void drawBackground(GuiGraphicsExtractor g,int x,int y,float delta){
        g.fill(0,0,width,height,UiTheme.OVERLAY);UiTheme.panel(g,left,top,(left+panelWidth)-(left),(top+panelHeight)-(top));
        g.text(font,title,left+12,top+13,UiTheme.TEXT,false);
    }
    @Override protected void drawContents(GuiGraphicsExtractor g,int x,int y,float delta){
        super.drawContents(g,x,y,delta);
        for(Entry e:entries)if(e.button.visible){
            int bx=e.button.getX(),by=e.button.getY();g.item(e.preview,bx+7,by+8);
            g.text(font,e.button.active?"普通 · Lv."+EquipmentItems.gear(e.preview).level:"Lv."+e.slot.minimum()+" 解锁",bx+7,by+34,e.button.active?UiTheme.MUTED:UiTheme.ERROR,false);
            if(x>=bx&&x<bx+e.button.getWidth()&&y>=by&&y<by+48){
                var lines=new ArrayList<>(Screen.getTooltipFromItem(minecraft,e.preview));
                if(!e.button.active)lines.add(Component.literal("角色达到 Lv."+e.slot.minimum()+" 后可制作").withColor(UiTheme.ERROR));
                g.setComponentTooltipForNextFrame(font,lines.stream().map(UiFonts::decorate).toList(),x,y,e.preview);
            }
        }
        if(contentHeight>bodyHeight){int h=Math.max(12,bodyHeight*bodyHeight/contentHeight),ybar=bodyTop+(bodyHeight-h)*scroll/(contentHeight-bodyHeight);g.fill(left+panelWidth-5,ybar,left+panelWidth-2,ybar+h,UiTheme.ACCENT);}
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.clamp(scroll-(int)(dy*54),0,Math.max(0,contentHeight-bodyHeight));position();return true;}
    @Override public void tick(){if(minecraft.player==null||minecraft.player.containerMenu!=parent.getMenu())minecraft.gui.setScreen(null);}
    @Override public void onClose(){minecraft.gui.popScreenLayer();}
    @Override public boolean isPauseScreen(){return false;}
}
