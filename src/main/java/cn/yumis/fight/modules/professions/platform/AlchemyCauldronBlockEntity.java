package cn.yumis.fight.modules.professions.platform;
import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import cn.yumis.fight.modules.professions.api.PotionTier;
import cn.yumis.fight.modules.professions.api.AlchemyPotions;
import cn.yumis.fight.platform.ItemRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
/**
 * 炼金锅方块实体：参考 Iron's Spells 的炼金锅。
 * 4 个投料槽、1000mb 液体、每件材料煮 100 刻，煮化后积累同档精华，2 份同档精华产出一瓶该档药剂。
 */
public final class AlchemyCauldronBlockEntity extends BlockEntity {
    public static final int SLOTS=4;
    public static final int CAPACITY=1000;
    public static final int COOK_TICKS=100;
    public static final int COST_PER_HERB=250;
    public static final int ESSENCE_PER_POTION=2;
    /** 煮药期间向客户端同步的最小间隔（5 ticks = 250ms，严格遵守 ≤4Hz）。 */
    public static final int SYNC_INTERVAL_TICKS=5;
    private static InventoryOperations inventory;
    private final ItemStack[] inputs=new ItemStack[SLOTS];
    private final int[] cook=new int[SLOTS];
    private final int[] essence=new int[PotionTier.values().length];
    private ItemStack output=ItemStack.EMPTY;
    private int water;
    /** 点化后的药液：成品药剂 ID 与剩余毫桶数。 */
    private String brewId="";
    private int brewAmount;
    private String brewBottle=AlchemyPotions.BOTTLE_GLASS;
    private int syncCooldown;
    private boolean syncDirty;
    public AlchemyCauldronBlockEntity(BlockPos pos,BlockState state){
        super(AlchemyContent.CAULDRON_BE.get(),pos,state);
        for(int i=0;i<SLOTS;i++)inputs[i]=ItemStack.EMPTY;
    }
    public static void installInventory(InventoryOperations operations){inventory=operations;}
    private record StateBackup(ItemStack[] inputs,int[] cook,ItemStack output,int water,String brewId,int brewAmount,String brewBottle){}
    private StateBackup backupState(){
        ItemStack[] inCopy=new ItemStack[SLOTS];
        for(int i=0;i<SLOTS;i++)inCopy[i]=inputs[i].copy();
        return new StateBackup(inCopy,cook.clone(),output.copy(),water,brewId,brewAmount,brewBottle);
    }
    private void restoreState(StateBackup backup){
        for(int i=0;i<SLOTS;i++){inputs[i]=backup.inputs()[i];cook[i]=backup.cook()[i];}
        output=backup.output();water=backup.water();brewId=backup.brewId();brewAmount=backup.brewAmount();brewBottle=backup.brewBottle();
    }
    private boolean runWithPlayerInventory(Player player,String action,java.util.function.BiConsumer<ServerPlayer,Map<String,JsonElement>> serverMutation,Runnable fallbackMutation){
        if(player instanceof ServerPlayer serverPlayer&&inventory!=null){
            StateBackup backup=backupState();
            JsonObject intent=new JsonObject();
            intent.addProperty("kind","alchemy/"+action);
            intent.addProperty("pos",worldPosition.asLong());
            Map<String,JsonElement> changes=new LinkedHashMap<>();
            boolean committed=inventory.transact(serverPlayer,"alchemy-"+action+"/"+UUID.randomUUID(),intent,()->serverMutation.accept(serverPlayer,changes),changes);
            if(!committed){restoreState(backup);return false;}
            return true;
        }
        fallbackMutation.run();
        return true;
    }
    /** 投入一株草药；槽满、不是草药或没水时拒绝。 */
    public boolean insert(ItemStack stack){
        return insert(null,stack);
    }
    public boolean insert(Player player,ItemStack stack){
        if(AlchemyRecipes.tierOf(stack)==null||water<=0)return false;
        int slot=-1;
        for(int i=0;i<SLOTS;i++)if(inputs[i].isEmpty()){slot=i;break;}
        if(slot<0)return false;
        final int targetSlot=slot;
        boolean ok=runWithPlayerInventory(player,"insert",(sp,changes)->{
            inputs[targetSlot]=stack.copyWithCount(1);
            cook[targetSlot]=0;
            stack.shrink(1);
        },()->{
            inputs[targetSlot]=stack.copyWithCount(1);
            cook[targetSlot]=0;
            stack.shrink(1);
        });
        if(!ok)return false;
        sync();
        setChanged();
        return true;
    }
    /** 水桶加水，最多 1000mb。 */
    public boolean fill(Player player,ItemStack bucket){
        if(water>=CAPACITY)return false;
        boolean ok=runWithPlayerInventory(player,"fill",(sp,changes)->{
            water=Math.min(CAPACITY,water+CAPACITY);
            if(!sp.getAbilities().instabuild){
                bucket.shrink(1);
                inventory.deliverOrStash(sp,new ItemStack(Items.BUCKET),changes);
            }
        },()->{
            water=Math.min(CAPACITY,water+CAPACITY);
            if(!player.getAbilities().instabuild){
                bucket.shrink(1);
                ItemStack empty=new ItemStack(Items.BUCKET);
                if(!player.getInventory().add(empty))player.drop(empty,false);
            }
        });
        if(!ok)return false;
        sync();
        setChanged();
        return true;
    }
    /** 取成品；没有成品时取回一件投料。 */
    public boolean take(Player player){
        if(!output.isEmpty()){
            boolean ok=runWithPlayerInventory(player,"take",(sp,changes)->{
                ItemStack result=output;
                output=ItemStack.EMPTY;
                inventory.deliverOrStash(sp,result,changes);
            },()->{
                ItemStack result=output;
                output=ItemStack.EMPTY;
                if(!player.getInventory().add(result))player.drop(result,false);
            });
            if(!ok)return false;
            sync();
            setChanged();
            return true;
        }
        for(int i=SLOTS-1;i>=0;i--){
            if(inputs[i].isEmpty())continue;
            final int slot=i;
            boolean ok=runWithPlayerInventory(player,"take",(sp,changes)->{
                ItemStack result=inputs[slot].split(1);
                cook[slot]=0;
                inventory.deliverOrStash(sp,result,changes);
            },()->{
                ItemStack result=inputs[slot].split(1);
                cook[slot]=0;
                if(!player.getInventory().add(result))player.drop(result,false);
            });
            if(!ok)return false;
            sync();
            setChanged();
            return true;
        }
        return false;
    }
    /** 加热只会把草药煮透；真正成药必须用点金棒点化。 */
    public void tick(){
        Level level=getLevel();
        if(level==null||level.isClientSide()||isRemoved())return;
        if(syncCooldown>0)syncCooldown--;
        boolean heat=AlchemyCauldronBlock.heatSource(level.getBlockState(worldPosition.below()));
        BlockState state=getBlockState();
        if(state.getValue(AlchemyCauldronBlock.HEAT)!=heat)level.setBlock(worldPosition,state.setValue(AlchemyCauldronBlock.HEAT,heat),Block.UPDATE_CLIENTS);
        if(heat&&water>0){
            for(int i=0;i<SLOTS;i++){
                if(inputs[i].isEmpty()){cook[i]=0;continue;}
                if(AlchemyRecipes.tierOf(inputs[i])==null){cook[i]=0;continue;}
                if(cook[i]<COOK_TICKS){cook[i]++;setChanged();syncDirty=true;}
            }
        }
        if(syncDirty&&syncCooldown<=0){
            syncDirty=false;
            syncCooldown=SYNC_INTERVAL_TICKS;
            sendClientUpdate();
        }
    }
    /** 点金棒点化：按投料组合匹配药液配方，需要满桶水（1000mb），产出 4 瓶份药液。 */
    public boolean transform(Player player){
        Level level=getLevel();
        if(level==null||level.isClientSide())return false;
        if(!brewId.isEmpty()||water<CAPACITY||!AlchemyCauldronBlock.heatSource(level.getBlockState(worldPosition.below())))return false;
        java.util.Map<String,Integer> herbs=new java.util.LinkedHashMap<>();
        for(int slot=0;slot<SLOTS;slot++){
            ItemStack stack=inputs[slot];
            if(stack.isEmpty())continue;
            if(cook[slot]<COOK_TICKS)return false;
            String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if(!id.startsWith("herb_"))continue;
            herbs.merge(id,1,Integer::sum);
        }
        String potion=AlchemyPotions.find(herbs);
        if(potion==null)return false;
        brewId=potion;
        brewBottle=AlchemyPotions.bottleFor(potion);
        brewAmount=CAPACITY;
        water=0;
        for(int i=0;i<SLOTS;i++){
            if(inputs[i].isEmpty())continue;
            if(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(inputs[i].getItem()).getPath().startsWith("herb_")){
                inputs[i]=ItemStack.EMPTY;
                cook[i]=0;
            }
        }
        sync();
        setChanged();
        return true;
    }
    /** 用对应瓶子取药液：每 250mb 一瓶，满桶可装 4 瓶。 */
    public boolean bottle(Player player,ItemStack bottle){
        if(brewId.isEmpty()||brewAmount<AlchemyPotions.BOTTLE_MB)return false;
        String held=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(bottle.getItem()).toString();
        String required=brewBottle.equals(AlchemyPotions.BOTTLE_GLASS)?AlchemyPotions.BOTTLE_GLASS:"yumis_dungeon_and_fight:"+brewBottle;
        if(!held.equals(required))return false;
        String crafted=brewId;
        boolean ok=runWithPlayerInventory(player,"bottle",(sp,changes)->{
            ItemStack potion=ItemRegistry.item(crafted,1);
            if(!sp.getAbilities().instabuild)bottle.shrink(1);
            inventory.deliverOrStash(sp,potion,changes);
            brewAmount-=AlchemyPotions.BOTTLE_MB;
            if(brewAmount<=0){brewAmount=0;brewId="";brewBottle=AlchemyPotions.BOTTLE_GLASS;}
        },()->{
            ItemStack potion=ItemRegistry.item(crafted,1);
            if(!player.getInventory().add(potion))player.drop(potion,false);
            brewAmount-=AlchemyPotions.BOTTLE_MB;
            if(brewAmount<=0){brewAmount=0;brewId="";brewBottle=AlchemyPotions.BOTTLE_GLASS;}
            if(!player.getAbilities().instabuild)bottle.shrink(1);
        });
        if(!ok)return false;
        if(player instanceof ServerPlayer server)cn.yumis.fight.modules.professions.application.ProfessionEvents.brewed(server,crafted);
        sync();
        setChanged();
        return true;
    }
    /** 方块被破坏时把投料与成品掉出来。 */
    public void drops(){
        Level level=getLevel();
        if(level==null)return;
        SimpleContainer container=new SimpleContainer(SLOTS+1);
        for(int i=0;i<SLOTS;i++)container.setItem(i,inputs[i]);
        container.setItem(SLOTS,output);
        Containers.dropContents(level,worldPosition,container);
        for(int i=0;i<SLOTS;i++)inputs[i]=ItemStack.EMPTY;
        output=ItemStack.EMPTY;
    }
    /** ActionBar 与 Jade 共用的炼金锅状态文本。 */
    public Component statusText(boolean heated){
        if(!brewId.isEmpty()&&brewAmount>0){
            int left=brewAmount/AlchemyPotions.BOTTLE_MB;
            int total=CAPACITY/AlchemyPotions.BOTTLE_MB;
            String potionName=ItemRegistry.item(brewId,1).getHoverName().getString();
            String bottleName=brewBottle.equals(AlchemyPotions.BOTTLE_GLASS)?"玻璃瓶":ItemRegistry.item(brewBottle,1).getHoverName().getString();
            return Component.literal(potionName+" · 剩余 "+left+"/"+total+" 瓶（需"+bottleName+"右击盛取）");
        }
        if(!output.isEmpty()){
            return Component.literal("成品："+output.getHoverName().getString()+"（空手右击取出）");
        }
        StringBuilder text=new StringBuilder("水量 ").append(water).append("/").append(CAPACITY).append("mB · ").append(heated?"热源已就绪":"缺少底部热源");
        int count=0,cooked=0,progressSum=0;
        String firstHerb="";
        boolean mixed=false;
        for(int i=0;i<SLOTS;i++){
            if(inputs[i].isEmpty())continue;
            count++;
            String name=inputs[i].getHoverName().getString();
            if(firstHerb.isEmpty())firstHerb=name;
            else if(!firstHerb.equals(name))mixed=true;
            int c=Math.clamp(cook[i],0,COOK_TICKS);
            progressSum+=c;
            if(c>=COOK_TICKS)cooked++;
        }
        if(count>0){
            text.append(" · ").append(mixed?"混合草药":firstHerb).append(" ").append(count).append("株");
            if(cooked==count)text.append(" · 已煮透，请用点金棒点化");
            else text.append(" · 煮制 ").append((int)Math.round(progressSum*100d/(count*COOK_TICKS))).append("%");
        }
        return Component.literal(text.toString());
    }
    private void sync(){
        Level level=getLevel();
        if(level==null||level.isClientSide())return;
        BlockState state=getBlockState();
        boolean hasWater=water>0;
        if(state.getValue(AlchemyCauldronBlock.WATER)!=hasWater)level.setBlock(worldPosition,state.setValue(AlchemyCauldronBlock.WATER,hasWater),Block.UPDATE_CLIENTS);
        sendClientUpdate();
    }
    private void sendClientUpdate(){
        Level level=getLevel();
        if(level==null||level.isClientSide())return;
        BlockState state=getBlockState();
        level.sendBlockUpdated(worldPosition,state,state,Block.UPDATE_CLIENTS);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
        return saveWithoutMetadata(registries);
    }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket(){
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override protected void saveAdditional(ValueOutput out){
        super.saveAdditional(out);
        out.putInt("alchemy_schema",1);
        for(int i=0;i<SLOTS;i++){out.store("in"+i,ItemStack.OPTIONAL_CODEC,inputs[i]);out.putInt("cook"+i,cook[i]);}
        out.store("output",ItemStack.OPTIONAL_CODEC,output);
        out.putInt("water",water);
        out.putString("brew",brewId);
        out.putString("brewBottle",brewBottle);
        out.putInt("brewAmount",brewAmount);
        for(int i=0;i<essence.length;i++)out.putInt("essence"+i,essence[i]);
    }
    @Override protected void loadAdditional(ValueInput in){
        super.loadAdditional(in);
        if(in.getIntOr("alchemy_schema",0)!=1){
            for(int i=0;i<SLOTS;i++){inputs[i]=ItemStack.EMPTY;cook[i]=0;}
            for(int i=0;i<essence.length;i++)essence[i]=0;
            output=ItemStack.EMPTY;water=0;return;
        }
        for(int i=0;i<SLOTS;i++){inputs[i]=in.read("in"+i,ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);cook[i]=in.getIntOr("cook"+i,0);}
        output=in.read("output",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        water=in.getIntOr("water",0);
        brewId=in.getStringOr("brew","");
        brewBottle=in.getStringOr("brewBottle",AlchemyPotions.BOTTLE_GLASS);
        brewAmount=in.getIntOr("brewAmount",0);
        for(int i=0;i<essence.length;i++)essence[i]=in.getIntOr("essence"+i,0);
    }
}
