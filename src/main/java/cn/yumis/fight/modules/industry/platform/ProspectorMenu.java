package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.network.Network;
import com.google.gson.JsonObject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class ProspectorMenu extends AbstractContainerMenu {
    public final int tier;public JsonObject info=new JsonObject();
    private int x,z,ticks;private ServerPlayer owner;
    public ProspectorMenu(int id,Inventory inventory,RegistryFriendlyByteBuf buf){this(id,inventory,buf.readInt());}
    public ProspectorMenu(int id,Inventory inventory,int tier){super(IndustrialContent.PROSPECTOR_MENU.get(),id);this.tier=tier;if(inventory.player instanceof ServerPlayer server)owner=server;x=inventory.player.blockPosition().getX()>>4;z=inventory.player.blockPosition().getZ()>>4;}
    @Override public boolean stillValid(Player player){return Math.max(ProspectorItem.tier(player.getMainHandItem()),ProspectorItem.tier(player.getOffhandItem()))>=tier;}
    @Override public ItemStack quickMoveStack(Player p,int i){return ItemStack.EMPTY;}
    @Override public boolean clickMenuButton(Player player,int button){
        if(!(player instanceof ServerPlayer server)||!stillValid(player))return false;
        if(button==0){try{IndustrialDeposits.get(server.level()).start(server,tier);x=player.blockPosition().getX()>>4;z=player.blockPosition().getZ()>>4;}catch(IllegalArgumentException e){server.sendOverlayMessage(net.minecraft.network.chat.Component.literal(e.getMessage()));}}
        else if(button==1)x-=9;else if(button==2)x+=9;else if(button==3)z-=9;else if(button==4)z+=9;
        else if(button==5){x=player.blockPosition().getX()>>4;z=player.blockPosition().getZ()>>4;}else return false;
        sync(server);return true;
    }
    private void sync(ServerPlayer player){info=IndustrialDeposits.get(player.level()).map(player,x,z,tier);info.addProperty("menu",containerId);Network.send(player,"industrial_survey",info);}
    @Override public void broadcastChanges(){super.broadcastChanges();if(owner!=null&&++ticks%5==1)sync(owner);}
}
