package cn.yumis.fight.industry;

import cn.yumis.fight.game.*;
import cn.yumis.fight.network.Network;
import cn.yumis.fight.server.ServerRuntime;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class IndustrialMenu extends AbstractContainerMenu {
    public final MachineBlockEntity machine;
    public JsonObject info=new JsonObject();
    private int broadcasts;
    public boolean showLines;
    public IndustrialMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){this(id,inv,find(inv,buf));}
    public static void writeOpen(RegistryFriendlyByteBuf buf,MachineBlockEntity be){buf.writeBlockPos(be.getBlockPos());buf.writeUtf(be.spec().id());buf.writeEnum(be.getBlockState().getValue(MachineBlock.FACING));}
    private static MachineBlockEntity find(Inventory inv,RegistryFriendlyByteBuf buf){
        BlockPos pos=buf.readBlockPos();String spec=buf.readUtf();var facing=buf.readEnum(net.minecraft.core.Direction.class);
        if(inv.player.level().getBlockEntity(pos) instanceof MachineBlockEntity be&&be.spec().id().equals(spec)&&be.getBlockState().getValue(MachineBlock.FACING)==facing)return be;
        // A server can open a machine immediately after teleport, before its chunk reaches this client.
        return new MachineBlockEntity(pos,IndustrialContent.ALL_BLOCKS.get(spec).get().defaultBlockState().setValue(MachineBlock.FACING,facing));
    }
    public IndustrialMenu(int id,Inventory inventory,MachineBlockEntity be){
        super(IndustrialContent.MACHINE_MENU.get(),id);machine=be;
        for(int i=0;i<MachineBlockEntity.SIZE;i++){
            final int slot=i;int x=i<4?16+(i%2)*18:i<13?184+((i-4)%3)*18:i==13?64:64;int y=i<4?53+(i/2)*18:i<13?53+((i-4)/3)*18:i==13?87:53;
            addSlot(new Slot(be,i,x,y){
                @Override public boolean isActive(){return slot<4?slot<be.kind().inputs():slot<13?be.kind().processing():slot==13?be.kind().processing():be.kind().generator()&&be.kind()!=DeviceKind.SOLAR&&be.kind()!=DeviceKind.WIND;}
                @Override public boolean mayPlace(ItemStack s){return isActive()&&be.canPlaceItem(slot,s);}
                @Override public boolean mayPickup(Player p){return isActive();}
            });
        }
        addStandardInventorySlots(inventory,47,153);
    }
    @Override public boolean stillValid(Player p){return machine.stillValid(p);}
    @Override public void clicked(int slot,int button,ContainerInput input,Player p){if(slot>=0&&slot<15&&!slots.get(slot).isActive())return;if(slot==MachineBlockEntity.CRYSTAL&&!getCarried().isEmpty()&&!machine.canPlaceItem(slot,getCarried())){if(p instanceof ServerPlayer server)server.sendOverlayMessage(net.minecraft.network.chat.Component.literal("本机仅接受第"+(machine.tier()*2-1)+"至第"+machine.tier()*2+"阶魔能水晶"));return;}super.clicked(slot,button,input,p);}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(p))return ItemStack.EMPTY;ItemStack stack=slot.getItem(),copy=stack.copy();
        if(index<15){if(!moveItemStackTo(stack,15,slots.size(),true))return ItemStack.EMPTY;}
        else {boolean moved=false;for(int i=0;i<15&&!stack.isEmpty();i++)if(slots.get(i).mayPlace(stack))moved|=moveItemStackTo(stack,i,i+1,false);if(!moved)return ItemStack.EMPTY;}
        slot.set(stack.isEmpty()?ItemStack.EMPTY:stack);slot.setChanged();return copy;
    }
    @Override public boolean clickMenuButton(Player p,int button){
        if(!(p instanceof ServerPlayer server)||!stillValid(p))return false;
        if(button==0)machine.running=!machine.running;
        else if(button==1)return false;
        else if(button==2)return false;
        else if(button==3){showLines=false;Network.send(server,"industrial_lines",IndustrialWorld.get(server.level()).connectionPreview(machine));}
        else if(button==4){if(machine.kind()!=DeviceKind.MINER)return false;if(machine.batchRecipe.isEmpty())machine.selected="";else machine.queued="@auto";}
        else if(button>=100){if(machine.kind()!=DeviceKind.MINER)return false;var recipes=IndustrialWorld.get(server.level()).recipes(server.level(),machine.kind());int at=button-100;if(at<0||at>=recipes.size())return false;var selected=recipes.get(at);if(selected.value().minimumTier()>machine.tier())throw new IllegalArgumentException("需要"+selected.value().minimumTier()+"级机器");String id=selected.id().identifier().toString();if(machine.batchRecipe.isEmpty())machine.selected=id;else machine.queued=id;}
        else return false;machine.setChanged();return true;
    }
    @Override public void broadcastChanges(){super.broadcastChanges();if(machine.getLevel() instanceof net.minecraft.server.level.ServerLevel server&&++broadcasts%5==1){info=IndustrialWorld.get(server).info(machine,showLines);info.addProperty("menu",containerId);for(var player:server.getServer().getPlayerList().getPlayers())if(player.containerMenu==this)Network.send(player,"industrial_ui",info);}}
}
