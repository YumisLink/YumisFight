package cn.yumis.fight.modules.professions.platform;
import cn.yumis.fight.modules.professions.api.PotionTier;
import cn.yumis.fight.modules.professions.api.AlchemyPotions;
import net.minecraft.core.BlockPos;
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
    private final ItemStack[] inputs=new ItemStack[SLOTS];
    private final int[] cook=new int[SLOTS];
    private final int[] essence=new int[PotionTier.values().length];
    private ItemStack output=ItemStack.EMPTY;
    private int water;
    /** 点化后的药液：成品药剂 ID 与剩余毫桶数。 */
    private String brewId="";
    private int brewAmount;
    private String brewBottle=AlchemyPotions.BOTTLE_GLASS;
    public AlchemyCauldronBlockEntity(BlockPos pos,BlockState state){
        super(AlchemyContent.CAULDRON_BE.get(),pos,state);
        for(int i=0;i<SLOTS;i++)inputs[i]=ItemStack.EMPTY;
    }
    /** 投入一株草药；槽满、不是草药或没水时拒绝。 */
    public boolean insert(ItemStack stack){
        if(AlchemyRecipes.tierOf(stack)==null||water<=0)return false;
        for(int i=0;i<SLOTS;i++){
            if(inputs[i].isEmpty()){
                inputs[i]=stack.copyWithCount(1);
                stack.shrink(1);
                setChanged();
                return true;
            }
        }
        return false;
    }
    /** 水桶加水，最多 1000mb。 */
    public boolean fill(Player player,ItemStack bucket){
        if(water>=CAPACITY)return false;
        water=Math.min(CAPACITY,water+CAPACITY);
        if(!player.getAbilities().instabuild){
            bucket.shrink(1);
            ItemStack empty=new ItemStack(Items.BUCKET);
            if(!player.getInventory().add(empty))player.drop(empty,false);
        }
        sync();
        setChanged();
        return true;
    }
    /** 取成品；没有成品时取回一件投料。 */
    public boolean take(Player player){
        if(!output.isEmpty()){
            ItemStack result=output;
            output=ItemStack.EMPTY;
            if(!player.getInventory().add(result))player.drop(result,false);
            setChanged();
            return true;
        }
        for(int i=SLOTS-1;i>=0;i--){
            if(inputs[i].isEmpty())continue;
            ItemStack result=inputs[i].split(1);
            if(!player.getInventory().add(result))player.drop(result,false);
            cook[i]=0;
            setChanged();
            return true;
        }
        return false;
    }
    /** 加热只会把草药煮透；真正成药必须用点金棒点化。 */
    public void tick(){
        Level level=getLevel();
        if(level==null||level.isClientSide()||isRemoved())return;
        boolean heat=AlchemyCauldronBlock.heatSource(level.getBlockState(worldPosition.below()));
        BlockState state=getBlockState();
        if(state.getValue(AlchemyCauldronBlock.HEAT)!=heat)level.setBlock(worldPosition,state.setValue(AlchemyCauldronBlock.HEAT,heat),Block.UPDATE_CLIENTS);
        if(!heat||water<=0)return;
        for(int i=0;i<SLOTS;i++){
            if(inputs[i].isEmpty()){cook[i]=0;continue;}
            if(AlchemyRecipes.tierOf(inputs[i])==null){cook[i]=0;continue;}
            if(cook[i]<COOK_TICKS){cook[i]++;setChanged();}
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
        ItemStack potion=cn.yumis.fight.platform.ItemRegistry.item(crafted,1);
        if(!player.getInventory().add(potion))player.drop(potion,false);
        brewAmount-=AlchemyPotions.BOTTLE_MB;
        if(brewAmount<=0){brewAmount=0;brewId="";brewBottle=AlchemyPotions.BOTTLE_GLASS;}
        if(!player.getAbilities().instabuild){
            bottle.shrink(1);
        }
        if(player instanceof net.minecraft.server.level.ServerPlayer server)cn.yumis.fight.modules.professions.application.ProfessionEvents.brewed(server,crafted);
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
    private void sync(){
        Level level=getLevel();
        if(level==null||level.isClientSide())return;
        BlockState state=getBlockState();
        boolean hasWater=water>0;
        if(state.getValue(AlchemyCauldronBlock.WATER)!=hasWater)level.setBlock(worldPosition,state.setValue(AlchemyCauldronBlock.WATER,hasWater),Block.UPDATE_CLIENTS);
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
