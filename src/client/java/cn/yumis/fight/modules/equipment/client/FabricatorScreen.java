package cn.yumis.fight.modules.equipment.client;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.equipment.api.client.EquipmentClient;

import cn.yumis.fight.modules.presentation.api.client.ThemedButton;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.modules.equipment.api.FabricatorMenu;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.equipment.api.MaterialCatalog;
import cn.yumis.fight.modules.equipment.api.ArmorType;
import cn.yumis.fight.modules.equipment.api.JewelryType;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.equipment.api.EquipmentRules;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.*;
public final class FabricatorScreen extends ThemedContainerScreen<FabricatorMenu> {
    private Slot slot=Slot.WEAPON;
    private Weapon weapon=Weapon.KATANA;
    private ArmorType armorType=ArmorType.UNTYPED;
    private JewelryType jewelryType=JewelryType.UNTYPED;
    private int level=1;
    private List<MaterialCatalog.Need> needs=List.of();
    public FabricatorScreen(FabricatorMenu menu,Inventory inv,Component title){
        super(menu,inv,title,176,208,ThemeDomain.MACHINE);
        inventoryLabelY=100;
    }
    @Override protected void init(){
        super.init();
        level=Math.max(slot.minimum(),Math.min(level,EquipmentClient.current().progress().level));
        controls();
    }
    private void controls(){
        clearWidgets();
        addRenderableWidget(Button.builder(Component.literal("制作"),b->craft()).bounds(leftPos+126,topPos+7,43,20).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal(slot==Slot.WEAPON?weapon.title():slot.armor()?armorType.title()+slot.title():slot.jewelry()?jewelryType.title()+slot.title():slot.title()),b->{
            minecraft.gui.pushScreenLayer(new EquipmentSelectionScreen(this,slot,weapon,armorType,jewelryType,level));
        }
        ).bounds(leftPos+33,topPos+7,88,20).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("−"),b->{
            level=Math.max(slot.minimum(),level-1);controls();
        }
        ).bounds(leftPos+33,topPos+31,20,18).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("+"),b->{
            level=Math.min(80,Math.min(EquipmentClient.current().progress().level,level+1));controls();
        }
        ).bounds(leftPos+101,topPos+31,20,18).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("分解主手"),b->{
            var j=EquipmentClient.current().operation("salvage");j.addProperty("inventorySlot",minecraft.player.getInventory().getSelectedSlot());EquipmentClient.current().send("salvage",j);
        }
        ).bounds(leftPos+124,topPos+31,48,18).build(ThemedButton::new));
        try{
            needs=MaterialCatalog.current().recipe(slot,level);
        }
        catch(IllegalArgumentException e){
            needs=List.of();
        }
    }
    void selectTemplate(Slot selected,Weapon selectedWeapon,ArmorType selectedArmor,JewelryType selectedJewelry){
        if(EquipmentClient.current().progress().level<selected.minimum())return;
        slot=selected;weapon=selectedWeapon;
        armorType=selectedArmor;jewelryType=selectedJewelry;
        level=Math.clamp(level,slot.minimum(),Math.min(80,EquipmentClient.current().progress().level));
        controls();
    }
    private void craft(){
        var s=EquipmentClient.current();
        s.upload();
        var j=s.operation("craft");
        j.addProperty("slot",slot.name());
        j.addProperty("weapon",weapon.name());
        j.addProperty("armorType",armorType.name());
        j.addProperty("jewelryType",jewelryType.name());
        j.addProperty("level",level);
        s.send("craft",j);
    }
    private ItemStack preview(MaterialCatalog.Need n){
        return n.regional()?EquipmentItems.material(n.id(),1):new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(n.id())));
    }
    private int held(MaterialCatalog.Need n){
        int total=0;
        for(int i=0;i<36;i++){
            ItemStack s=minecraft.player.getInventory().getItem(i);
            if(n.regional()?n.id().equals(EquipmentItems.material(s)):BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(n.id()))total+=s.getCount();
        }
        return total;
    }
    @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float a){
        g.fill(0,0,width,height,UiTheme.OVERLAY);
        UiTheme.panel(g,leftPos-5,topPos-5,(leftPos+181)-(leftPos-5),(topPos+208)-(topPos-5));

        for(int i=0;i<18;i++){
            int x=leftPos+7+(i%9)*18,y=topPos+55+(i/9)*21;
            UiTheme.slot(g,x+1,y+1);
            if(i<needs.size()){
                var n=needs.get(i);
                g.item(preview(n),x+1,y+1);
                g.text(UiFonts.font(),Integer.toString(n.count()),x+1,y+12,held(n)>=n.count()?UiTheme.SUCCESS:UiTheme.ERROR,false);
                if(mx>=x&&mx<x+18&&my>=y&&my<y+21){
                    ItemStack item=preview(n);
                    g.setComponentTooltipForNextFrame(UiFonts.font(),List.of(UiFonts.decorate(item.getHoverName()),UiFonts.decorate(Component.literal("需要 "+n.count()+" · 持有 "+held(n)+" · 缺少 "+Math.max(0,n.count()-held(n))))),mx,my,item);
                }
            }
        }
        var gear=EquipmentRules.create(slot,weapon,Rarity.COMMON,level,armorType,jewelryType,new Random(0));
        g.item(EquipmentItems.gear(gear),leftPos+10,topPos+14);
        if(mx>=leftPos+8&&mx<leftPos+28&&my>=topPos+12&&my<topPos+32){var preview=EquipmentItems.gear(gear);g.setComponentTooltipForNextFrame(UiFonts.font(),Screen.getTooltipFromItem(minecraft,preview),mx,my,preview);}
        g.text(UiFonts.font(),"Lv."+level,leftPos+59,topPos+36,UiTheme.GOLD,false);
        for(var s:menu.slots){
            int x=leftPos+s.x,y=topPos+s.y;
            UiTheme.slot(g,x,y);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int x,int y){
        g.text(UiFonts.font(),"悬停查看持有/缺少",64,101,UiTheme.MUTED,false);
        g.text(UiFonts.font(),UiFonts.decorate(playerInventoryTitle),8,101,UiTheme.TEXT,false);
    }
    @Override public boolean keyPressed(KeyEvent e){
        if(cn.yumis.fight.modules.presentation.api.client.MenuInput.MENU.matches(e)){
            onClose();
            cn.yumis.fight.modules.presentation.api.client.MenuInput.open();
            return true;
        }
        return super.keyPressed(e);
    }
    @Override public void onClose(){
        EquipmentClient.current().upload();
        super.onClose();
    }
}
