package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.inventory.api.PersistentContainer;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import java.util.UUID;

/** One durable stone slot. Readiness is ephemeral; the stone participates in inventory WAL. */
public final class WaystonePedestalBlockEntity extends BlockEntity implements PersistentContainer,WorldlyContainer {
    private UUID identity=UUID.randomUUID();
    private ItemStack stone=ItemStack.EMPTY;
    public UUID inserter;
    public WaystonePedestalBlockEntity(BlockPos pos,BlockState state){super(DungeonContentTypes.WAYSTONE_PEDESTAL_ENTITY.get(),pos,state);}
    @Override public String key(){return identity.toString();}
    @Override public int getContainerSize(){return 1;}
    @Override public boolean isEmpty(){return stone.isEmpty();}
    @Override public ItemStack getItem(int slot){return slot==0?stone:ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){if(slot!=0)return ItemStack.EMPTY;ItemStack result=stone.split(count);setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){if(slot!=0)return ItemStack.EMPTY;var result=stone;stone=ItemStack.EMPTY;return result;}
    @Override public void setItem(int slot,ItemStack value){if(slot!=0)throw new IllegalArgumentException("引路台只有一个石槽");if(!value.isEmpty()&&(value.getCount()!=1||Waystone.read(value)==null))throw new IllegalArgumentException("引路台仅接受完整引路石");stone=value;setChanged();}
    @Override public int getMaxStackSize(){return 1;}
    @Override public boolean stillValid(Player p){return !isRemoved()&&p.level()==level&&p.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64;}
    @Override public void clearContent(){stone=ItemStack.EMPTY;inserter=null;setChanged();}
    @Override public int[] getSlotsForFace(Direction direction){return new int[0];}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction direction){return false;}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction direction){return false;}
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide()){requestRecovery();display();}}
    public void display(){if(level==null||level.isClientSide()||isRemoved())return;var item=Waystone.read(stone);int value=item==null?0:item.rarity.ordinal()+1;BlockState state=getBlockState();if(state.getValue(WaystonePedestalBlock.STONE)!=value)level.setBlock(worldPosition,state.setValue(WaystonePedestalBlock.STONE,value),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);}
    @Override protected void saveAdditional(ValueOutput out){super.saveAdditional(out);out.putInt("waystone_schema",1);out.putString("identity",identity.toString());out.putString("inserter",inserter==null?"":inserter.toString());out.store("stone",ItemStack.OPTIONAL_CODEC,stone);}
    @Override protected void loadAdditional(ValueInput in){super.loadAdditional(in);if(in.getIntOr("waystone_schema",0)!=1){stone=ItemStack.EMPTY;identity=UUID.randomUUID();inserter=null;return;}identity=UUID.fromString(in.getStringOr("identity",identity.toString()));String player=in.getStringOr("inserter","");inserter=player.isEmpty()?null:UUID.fromString(player);stone=in.read("stone",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);}
    @Override public JsonElement transactionState(){var value=new JsonObject();value.addProperty("nbt",saveCustomOnly(level.registryAccess()).toString());return value;}
    @Override public void restoreTransactionState(JsonElement value){try{loadCustomOnly(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),net.minecraft.nbt.TagParser.parseCompoundFully(value.getAsJsonObject().get("nbt").getAsString())));setChanged();}catch(Exception e){throw new IllegalStateException("引路台事务恢复失败",e);}}
    @Override public void requestRecovery(){WaystoneActions.recover();}
}
