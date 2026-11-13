package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.IndustrialPorts;
import cn.yumis.fight.modules.inventory.api.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.transfer.*;
import net.neoforged.neoforge.transfer.item.*;
import java.util.*;

/** Input may hold any item; only output is extractable through automated capabilities. */
public final class DisplayStandBlockEntity extends BlockEntity implements PersistentContainer,WorldlyContainer {
    private UUID identity=UUID.randomUUID();
    private boolean contentsDropped;
    public final Store items=new Store();
    public final class Store extends IndustrialResourceStore<ItemResource> {
        Store(){super(2,ItemResource.EMPTY,ItemResource.OPTIONAL_CODEC);}
        public long getCapacityAsLong(int slot,ItemResource resource){return resource.isEmpty()?64:resource.getMaxStackSize();}
        public boolean isValid(int slot,ItemResource resource){return !resource.isEmpty();}
        protected void changed(int slot){DisplayStandBlockEntity.this.setChanged();}
    }
    public DisplayStandBlockEntity(BlockPos pos,BlockState state){super(IndustrialContent.DISPLAY_STAND_BE.get(),pos,state);}
    public ResourceHandler<ItemResource> automation(){
        // The insert-only slot must appear empty to source scanners such as transfer crystals.
        var ports=new FilteredHandler<>(items,new int[]{0,1},(slot,resource)->slot==0,(slot,resource)->slot==1,null,1);
        return new ResourceHandler<>(){
            public int size(){return ports.size();}
            public ItemResource getResource(int slot){return slot==0?ItemResource.EMPTY:ports.getResource(slot);}
            public long getAmountAsLong(int slot){return slot==0?0:ports.getAmountAsLong(slot);}
            public long getCapacityAsLong(int slot,ItemResource resource){return ports.getCapacityAsLong(slot,resource);}
            public boolean isValid(int slot,ItemResource resource){return ports.isValid(slot,resource);}
            public int insert(int slot,ItemResource resource,int amount,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){return ports.insert(slot,resource,amount,tx);}
            public int extract(int slot,ItemResource resource,int amount,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){return ports.extract(slot,resource,amount,tx);}
        };
    }
    @Override public String key(){return identity.toString();}
    @Override public void requestRecovery(){if(level instanceof ServerLevel server)IndustrialWorld.get(server).requestRecovery();}
    @Override public void onLoad(){super.onLoad();requestRecovery();}
    @Override public int getContainerSize(){return 2;}
    @Override public boolean isEmpty(){return getItem(0).isEmpty()&&getItem(1).isEmpty();}
    @Override public ItemStack getItem(int slot){return items.getResource(slot).toStack(items.getAmountAsInt(slot));}
    @Override public void setItem(int slot,ItemStack stack){items.set(slot,ItemResource.of(stack),stack.getCount());}
    @Override public ItemStack removeItem(int slot,int count){int n=Math.min(Math.max(0,count),items.getAmountAsInt(slot));ItemStack result=items.getResource(slot).toStack(n);items.set(slot,items.getResource(slot),items.getAmountAsInt(slot)-n);return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){return removeItem(slot,items.getAmountAsInt(slot));}
    @Override public void clearContent(){setItem(0,ItemStack.EMPTY);setItem(1,ItemStack.EMPTY);}
    @Override public boolean stillValid(Player player){return !isRemoved()&&level!=null&&level.getBlockEntity(worldPosition)==this&&player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot==0;}
    @Override public int[] getSlotsForFace(Direction side){return new int[]{0,1};}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return slot==0;}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return slot==1;}
    @Override public void setChanged(){super.setChanged();if(level instanceof ServerLevel server&&!isRemoved())server.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    private static void receive(ServerPlayer player,ItemStack stack){InventoryPacking.insertRaw(player,stack);if(!stack.isEmpty())throw new IllegalArgumentException("背包空间不足，操作未执行");}
    public void interact(ServerPlayer player,InteractionHand hand){
        if(!stillValid(player))return;
        JsonObject intent=new JsonObject();intent.addProperty("stand",key());intent.addProperty("hand",hand.name());intent.addProperty("sneaking",player.isShiftKeyDown());
        IndustrialPorts.inventory().transactMachine(player,"display-stand/"+UUID.randomUUID(),intent,this,()->{
            if(!stillValid(player))throw new IllegalArgumentException("展示台已失效");
            ItemStack held=player.getItemInHand(hand),input=getItem(0),output=getItem(1);
            if(held.isEmpty()){
                int slot=player.isShiftKeyDown()?(!output.isEmpty()?1:0):(!input.isEmpty()?0:1);
                ItemStack take=removeItemNoUpdate(slot);receive(player,take);return;
            }
            if(input.isEmpty()||ItemStack.isSameItemSameComponents(input,held)){
                int placed=Math.min(held.getCount(),held.getMaxStackSize()-input.getCount());
                if(placed>0){if(input.isEmpty())input=held.copyWithCount(placed);else input.grow(placed);held.shrink(placed);setItem(0,input);}
            }else{
                ItemStack replacement=held.split(Math.min(held.getCount(),held.getMaxStackSize()));setItem(0,replacement);receive(player,input);
            }
            setItem(1,ItemStack.EMPTY);receive(player,output);player.getInventory().setChanged();
        },Map.of());
    }
    @Override protected void saveAdditional(ValueOutput out){super.saveAdditional(out);out.putInt("schema",1);out.putString("identity",key());items.serialize(out.child("inventory"));}
    @Override protected void loadAdditional(ValueInput input){super.loadAdditional(input);if(input.getIntOr("schema",0)!=1)throw new IllegalArgumentException("不支持的展示台数据代际");identity=UUID.fromString(input.getStringOr("identity",identity.toString()));items.deserialize(input.childOrEmpty("inventory"));}
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries){var out=TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,registries);out.store("display",ItemStack.OPTIONAL_CODEC.listOf(),List.of(getItem(0),getItem(1)));return out.buildResult();}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override public void handleUpdateTag(ValueInput input){var stacks=input.read("display",ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());for(int i=0;i<2;i++)setItem(i,i<stacks.size()?stacks.get(i):ItemStack.EMPTY);}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ValueInput input){handleUpdateTag(input);}
    @Override public JsonElement transactionState(){JsonObject state=new JsonObject();state.addProperty("nbt",saveCustomOnly(level.registryAccess()).toString());return state;}
    @Override public void restoreTransactionState(JsonElement state){try{loadCustomOnly(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),net.minecraft.nbt.TagParser.parseCompoundFully(state.getAsJsonObject().get("nbt").getAsString())));setChanged();}catch(Exception failure){throw new IllegalStateException("展示台事务恢复失败",failure);}}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(contentsDropped||!(level instanceof ServerLevel server))return;contentsDropped=true;for(int i=0;i<2;i++){var stack=removeItemNoUpdate(i);if(!stack.isEmpty())Block.popResource(server,pos,stack);}}
}
