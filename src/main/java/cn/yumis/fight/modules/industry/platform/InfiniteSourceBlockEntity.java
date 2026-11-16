package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.IndustrialPorts;
import cn.yumis.fight.modules.inventory.api.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.*;
import net.neoforged.neoforge.transfer.fluid.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;
import java.util.function.Supplier;

/** A persistent sample, never a stockpile: every successful extraction copies that sample. */
public final class InfiniteSourceBlockEntity extends BlockEntity implements PersistentContainer,WorldlyContainer {
    private UUID identity=UUID.randomUUID();
    private ItemResource itemSample=ItemResource.EMPTY;
    private FluidResource fluidSample=FluidResource.EMPTY;
    private final ResourceHandler<ItemResource> itemPort=new SourcePort<>(()->itemSample);
    private final ResourceHandler<FluidResource> fluidPort=new SourcePort<>(()->fluidSample);
    public InfiniteSourceBlockEntity(BlockPos pos,BlockState state){super(IndustrialContent.INFINITE_SOURCE_BE.get(),pos,state);}
    public boolean liquid(){return ((InfiniteSourceBlock)getBlockState().getBlock()).liquid;}
    public ItemStack sampleItem(){return itemSample.toStack(itemSample.isEmpty()?0:1);}
    public FluidStack sampleFluid(){return fluidSample.toStack(fluidSample.isEmpty()?0:1);}
    public ResourceHandler<ItemResource> items(){return liquid()?null:itemPort;}
    public ResourceHandler<FluidResource> fluids(){return liquid()?fluidPort:null;}
    private final class SourcePort<R extends Resource> implements ResourceHandler<R> {
        private final Supplier<R> sample;
        SourcePort(Supplier<R> sample){this.sample=sample;}
        public int size(){return 1;}
        public R getResource(int slot){if(slot!=0)throw new IndexOutOfBoundsException(slot);return sample.get();}
        public long getAmountAsLong(int slot){return slot==0&&!isRemoved()&&!sample.get().isEmpty()?Integer.MAX_VALUE:0;}
        public long getCapacityAsLong(int slot,R resource){return slot==0?Integer.MAX_VALUE:0;}
        public boolean isValid(int slot,R resource){return false;}
        public int insert(int slot,R resource,int amount,TransactionContext tx){return 0;}
        public int extract(int slot,R resource,int amount,TransactionContext tx){
            if(amount<0)throw new IllegalArgumentException("Negative extraction");
            Objects.requireNonNull(tx);
            // No mutable quantity exists, so simulation/rollback has no source-side effects.
            return slot==0&&!isRemoved()&&!resource.isEmpty()&&sample.get().equals(resource)?amount:0;
        }
    }
    @Override public String key(){return identity.toString();}
    @Override public boolean stillValid(Player player){return !isRemoved()&&level!=null&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this&&player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64;}
    private static String text(String key){return "industry."+IndustrialContent.NS+".infinite."+key;}
    private void showSample(ServerPlayer player){
        Component name=liquid()?(fluidSample.isEmpty()?null:sampleFluid().getHoverName()):(itemSample.isEmpty()?null:sampleItem().getHoverName());
        player.sendOverlayMessage(name==null?Component.translatable(text("empty")):Component.translatable(text("sample"),name));
    }
    public void interact(ServerPlayer player,InteractionHand hand){
        if(!stillValid(player))return;
        if(player.getItemInHand(hand).isEmpty()&&!player.isShiftKeyDown()&&(liquid()||itemSample.isEmpty())){showSample(player);return;}
        JsonObject intent=new JsonObject();intent.addProperty("source",key());intent.addProperty("hand",hand.name());intent.addProperty("clear",player.isShiftKeyDown());
        IndustrialPorts.inventory().transactMachine(player,"infinite-source/"+UUID.randomUUID(),intent,this,()->{
            if(!stillValid(player))throw new IllegalArgumentException("无限源已失效");
            ItemStack held=player.getItemInHand(hand);
            if(held.isEmpty()){
                if(player.isShiftKeyDown()){itemSample=ItemResource.EMPTY;fluidSample=FluidResource.EMPTY;setChanged();return;}
                if(!liquid()&&!itemSample.isEmpty()){
                    ItemStack output=itemSample.toStack(itemSample.getMaxStackSize());
                    if(InventoryPacking.rawRoom(player,output)<output.getCount())throw new IllegalArgumentException("背包空间不足，操作未执行");
                    InventoryPacking.insertRaw(player,output);
                }
                return;
            }
            if(!liquid()){itemSample=ItemResource.of(held);setChanged();return;}
            if(held.is(Items.BUCKET)){
                if(fluidSample.isEmpty())throw new IllegalArgumentException("请先用满液体桶设置液体");
                try(var tx=Transaction.openRoot()){
                    var handler=ItemAccess.forPlayerInteraction(player,hand).oneByOne().getCapability(Capabilities.Fluid.ITEM);
                    if(handler==null||handler.insert(fluidSample,1000,tx)!=1000)throw new IllegalArgumentException("该液体无法装入桶");
                    tx.commit();
                }
                return;
            }
            FluidStack contained=FluidUtil.getFirstStackContained(held);
            if(!(held.getItem() instanceof BucketItem)||contained.getAmount()<1000)throw new IllegalArgumentException("需要一满桶液体");
            FluidResource next=FluidResource.of(contained);
            if(!player.isCreative())try(var tx=Transaction.openRoot()){
                var handler=ItemAccess.forPlayerInteraction(player,hand).oneByOne().getCapability(Capabilities.Fluid.ITEM);
                if(handler==null||handler.extract(next,1000,tx)!=1000)throw new IllegalArgumentException("无法倒出这一桶液体");
                tx.commit();
            }
            fluidSample=next;setChanged();
        },Map.of());
    }
    // Vanilla hoppers see one renewable output slot; they cannot configure or deposit items.
    @Override public int getContainerSize(){return liquid()?0:1;}
    @Override public boolean isEmpty(){return liquid()||itemSample.isEmpty();}
    @Override public ItemStack getItem(int slot){return slot==0&&!liquid()&&!itemSample.isEmpty()?itemSample.toStack(itemSample.getMaxStackSize()):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){return slot==0&&!liquid()&&!itemSample.isEmpty()?itemSample.toStack(Math.min(Math.max(0,count),itemSample.getMaxStackSize())):ItemStack.EMPTY;}
    @Override public ItemStack removeItemNoUpdate(int slot){return getItem(slot);}
    @Override public void setItem(int slot,ItemStack stack){}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return false;}
    @Override public int[] getSlotsForFace(Direction side){return liquid()?new int[0]:new int[]{0};}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return false;}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return slot==0&&!liquid();}
    @Override public void clearContent(){itemSample=ItemResource.EMPTY;fluidSample=FluidResource.EMPTY;setChanged();}
    // Samples are templates. The ordinary block loot is the only drop on removal.
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){}
    @Override public void setChanged(){super.setChanged();if(level instanceof ServerLevel server&&!isRemoved())server.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    private void writeSamples(ValueOutput out){out.store("item_sample",ItemResource.OPTIONAL_CODEC,itemSample);out.store("fluid_sample",FluidResource.OPTIONAL_CODEC,fluidSample);}
    private void readSamples(ValueInput in){itemSample=liquid()?ItemResource.EMPTY:in.read("item_sample",ItemResource.OPTIONAL_CODEC).orElse(ItemResource.EMPTY);fluidSample=liquid()?in.read("fluid_sample",FluidResource.OPTIONAL_CODEC).orElse(FluidResource.EMPTY):FluidResource.EMPTY;}
    @Override protected void saveAdditional(ValueOutput out){super.saveAdditional(out);out.putInt("schema",1);out.putString("identity",key());writeSamples(out);}
    @Override protected void loadAdditional(ValueInput in){super.loadAdditional(in);if(in.getIntOr("schema",0)!=1)throw new IllegalArgumentException("不支持的无限源数据代际");identity=UUID.fromString(in.getStringOr("identity",identity.toString()));readSamples(in);}
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries){var out=TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,registries);writeSamples(out);return out.buildResult();}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override public void handleUpdateTag(ValueInput in){readSamples(in);}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ValueInput in){handleUpdateTag(in);}
    @Override public JsonElement transactionState(){JsonObject state=new JsonObject();state.addProperty("nbt",saveCustomOnly(level.registryAccess()).toString());return state;}
    @Override public void restoreTransactionState(JsonElement state){try{loadCustomOnly(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),net.minecraft.nbt.TagParser.parseCompoundFully(state.getAsJsonObject().get("nbt").getAsString())));setChanged();}catch(Exception failure){throw new IllegalStateException("无限源事务恢复失败",failure);}}
}
