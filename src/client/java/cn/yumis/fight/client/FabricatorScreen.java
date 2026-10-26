package cn.yumis.fight.client;
import cn.yumis.fight.game.*;
import cn.yumis.fight.rules.*;
import static cn.yumis.fight.rules.Rules.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.*;
public final class FabricatorScreen extends AbstractContainerScreen<FabricatorMenu> {
    private Slot slot=Slot.WEAPON;
    private Weapon weapon=Weapon.KATANA;
    private int level=1;
    private List<Catalog.Need> needs=List.of();
    public FabricatorScreen(FabricatorMenu menu,Inventory inv,Component title){
        super(menu,inv,title,176,208);
        inventoryLabelY=100;
    }
    @Override protected void init(){
        super.init();
        level=Math.max(slot.minimum(),Math.min(level,ClientState.INSTANCE.progress.level));
        controls();
    }
    private void controls(){
        clearWidgets();
        addRenderableWidget(Button.builder(Component.literal("制作"),b->craft()).bounds(leftPos+126,topPos+7,43,20).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal(slot==Slot.WEAPON?weapon.title():slot.title()),b->{
            if(slot==Slot.WEAPON&&weapon.ordinal()<4)weapon=Weapon.values()[weapon.ordinal()+1];else{
                slot=Slot.values()[(slot.ordinal()+1)%Slot.values().length];weapon=Weapon.KATANA;
            }
            level=Math.max(slot.minimum(),level);controls();
        }
        ).bounds(leftPos+33,topPos+7,88,20).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("−"),b->{
            level=Math.max(slot.minimum(),level-1);controls();
        }
        ).bounds(leftPos+33,topPos+31,20,18).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("+"),b->{
            level=Math.min(80,Math.min(ClientState.INSTANCE.progress.level,level+1));controls();
        }
        ).bounds(leftPos+101,topPos+31,20,18).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("分解主手"),b->{
            var j=ClientState.INSTANCE.operation("salvage");j.addProperty("inventorySlot",minecraft.player.getInventory().getSelectedSlot());ClientState.INSTANCE.send("salvage",j);
        }
        ).bounds(leftPos+124,topPos+31,48,18).build(ThemedButton::new));
        try{
            needs=Catalog.CURRENT.recipe(slot,level);
        }
        catch(IllegalArgumentException e){
            needs=List.of();
        }
    }
    private void craft(){
        var s=ClientState.INSTANCE;
        s.upload();
        var j=s.operation("craft");
        j.addProperty("slot",slot.name());
        j.addProperty("weapon",weapon.name());
        j.addProperty("level",level);
        s.send("craft",j);
    }
    private ItemStack preview(Catalog.Need n){
        return n.regional()?GameItems.material(n.id(),1):new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(n.id())));
    }
    private int held(Catalog.Need n){
        int total=0;
        for(int i=0;i<36;i++){
            ItemStack s=minecraft.player.getInventory().getItem(i);
            if(n.regional()?n.id().equals(GameItems.material(s)):BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(n.id()))total+=s.getCount();
        }
        return total;
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float a){
        g.fill(0,0,width,height,UiTheme.OVERLAY);
        g.fill(leftPos-5,topPos-5,leftPos+181,topPos+208,UiTheme.PANEL);
        g.fill(0,0,width,height,UiTheme.OVERLAY);
        g.fill(leftPos-5,topPos-5,leftPos+181,topPos-3,UiTheme.ACCENT);
        for(int i=0;i<18;i++){
            int x=leftPos+7+(i%9)*18,y=topPos+55+(i/9)*21;
            g.fill(x,y,x+18,y+19,UiTheme.CARD);
            g.fill(x+1,y+1,x+17,y+17,UiTheme.BUTTON);
            if(i<needs.size()){
                var n=needs.get(i);
                g.item(preview(n),x+1,y+1);
                g.text(font,Integer.toString(n.count()),x+1,y+12,held(n)>=n.count()?0xffb7f8bc:0xffff8b8b);
                if(mx>=x&&mx<x+18&&my>=y&&my<y+21){
                    ItemStack item=preview(n);
                    g.setComponentTooltipForNextFrame(font,List.of(item.getHoverName(),Component.literal("需要 "+n.count()+" · 持有 "+held(n)+" · 缺少 "+Math.max(0,n.count()-held(n)))),mx,my,item);
                }
            }
        }
        var gear=EquipmentRules.create(slot,weapon,Rarity.COMMON,level,new Random(0));
        g.item(GameItems.gear(gear),leftPos+10,topPos+14);
        g.text(font,"Lv."+level,leftPos+59,topPos+36,0xfff4dba7);
        for(var s:menu.slots){
            int x=leftPos+s.x,y=topPos+s.y;
            g.fill(x-1,y-1,x+17,y+17,UiTheme.CARD);
            g.fill(x,y,x+16,y+16,UiTheme.BUTTON);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int x,int y){
        g.text(font,"悬停查看持有/缺少",64,101,0xff9aa9be);
        g.text(font,playerInventoryTitle,8,101,0xffd9dce5);
    }
    @Override public boolean keyPressed(KeyEvent e){
        if(ClientBootstrap.MENU.matches(e)){
            onClose();
            minecraft.gui.setScreen(new FightScreen());
            return true;
        }
        return super.keyPressed(e);
    }
    @Override public void onClose(){
        ClientState.INSTANCE.upload();
        super.onClose();
    }
}
