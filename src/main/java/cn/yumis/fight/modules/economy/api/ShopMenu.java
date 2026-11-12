package cn.yumis.fight.modules.economy.api;

import cn.yumis.fight.modules.inventory.api.CheckpointMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import java.util.UUID;
import java.util.function.Supplier;

/** Display stock is never player-owned inventory, including creative/quick-craft gestures. */
public final class ShopMenu extends AbstractContainerMenu implements CheckpointMenu {
    private static Supplier<MenuType<ShopMenu>> type;
    public static void type(Supplier<MenuType<ShopMenu>> value){type=value;}
    public final UUID host;
    public final String shop;
    public final ShopDefinitions.Definition definition;
    private final SimpleContainer display=new SimpleContainer(54);
    public ShopMenu(int id,Inventory inventory,RegistryFriendlyByteBuf data){this(id,inventory,data.readUUID(),data.readUtf());}
    public ShopMenu(int id,Inventory inventory,UUID host,String shop){
        super(type.get(),id);this.host=host;this.shop=shop;definition=ShopDefinitions.current().require(shop);
        for(int i=0;i<54;i++){
            if(i<definition.offers().size())display.setItem(i,definition.offers().get(i).stack());
            addSlot(new Slot(display,i,8+(i%9)*18,18+(i/9)*18){
                @Override public boolean mayPlace(ItemStack stack){return false;}
                @Override public boolean mayPickup(Player player){return false;}
            });
        }
        addStandardInventorySlots(inventory,8,140);
    }
    public static void open(ServerPlayer player,UUID host,String shop){
        var d=ShopDefinitions.current().require(shop);
        player.openMenu(new SimpleMenuProvider((id,inventory,p)->new ShopMenu(id,inventory,host,shop),Component.literal(d.name())),data->{data.writeUUID(host);data.writeUtf(shop);});
    }
    @Override public boolean stillValid(Player player){return player.isAlive();}
    @Override public void clicked(int index,int button,ContainerInput input,Player player){if(index>=0&&index<54)return;super.clicked(index,button,input,player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<54||index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var stack=slot.getItem();var before=stack.copy();
        if(index<81){if(!moveItemStackTo(stack,81,90,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(stack,54,81,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();return before;
    }
}
