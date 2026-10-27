package cn.yumis.fight.industry;

import cn.yumis.fight.game.GameItems;
import cn.yumis.fight.rules.CurrencyRules;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.fluid.*;
import net.neoforged.neoforge.transfer.transaction.*;
import java.math.BigInteger;
import java.util.*;

public final class MachineBlockEntity extends BlockEntity implements Container,MenuProvider {
    public static final int OUTPUT=4,OUTPUT_END=13,CRYSTAL=13,FUEL=14,SIZE=15;
    public UUID identity=UUID.randomUUID();public String registeredIdentity="",beacon="",targetDimension="";public long targetPosition;public Direction targetFace=Direction.UP;
    public long energy,crystalRemaining;public boolean backup=true,running=true,structureValid;public String selected="",queued="",status="未连接信标";
    public String batchRecipe="";public long batchEnergy,paid;public int batchTicks,batchSpeed=1;public Fraction work=Fraction.ZERO;public Fraction tickTime=Fraction.of(128);public ItemStack batchResult=ItemStack.EMPTY;public FluidStack batchFluid=FluidStack.EMPTY;public List<ItemStack> batchInputs=new ArrayList<>();
    public int fuelTotal,fuelRemaining,fuelFluidRate;public long fuelEnergy,currentPower;public String fuelFluid="";public int fluidRemainder,powerRemainder,roundRobin,harvestCursor,harvestPhase;public long generated,consumed,overflow;
    public final InventoryStore items=new InventoryStore();
    public final FluidStacksResourceHandler fluids=new FluidStacksResourceHandler(2,IndustrialRules.CURRENT.fluidCapacity){
        @Override protected int getCapacity(int i,FluidResource resource){return IndustrialRules.CURRENT.fluidCapacity;}
        @Override public boolean isValid(int i,FluidResource resource){if(i==1)return kind().processing();if(kind().processing()&&level instanceof ServerLevel server)return server.recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get()).stream().map(h->h.value()).anyMatch(r->r.machine().equals(kind().id)&&r.minimumTier()<=tier()&&r.fluidInput().isPresent()&&r.fluidInput().get().ingredient().test(resource.toStack(1)));return acceptsFluid(resource);}
        @Override protected void onContentsChanged(int i,FluidStack previous){MachineBlockEntity.this.setChanged();}
    };
    public final class InventoryStore extends ItemStacksResourceHandler {
        InventoryStore(){super(SIZE);}
        public ItemStack stack(int i){return stacks.get(i);}
        void stack(int i,ItemStack s){ItemStack before=stacks.set(i,s);onContentsChanged(i,before);}
        @Override protected void onContentsChanged(int i,ItemStack previous){MachineBlockEntity.this.setChanged();}
    }
    public MachineBlockEntity(BlockPos pos,BlockState state){super(IndustrialContent.MACHINE_BE.get(),pos,state);}
    public DeviceKind kind(){return ((MachineBlock)getBlockState().getBlock()).initial.kind();}
    public int tier(){return getBlockState().getValue(MachineBlock.TIER);}
    public IndustrialContent.Spec spec(){return IndustrialContent.spec(kind(),tier());}
    public JsonObject transactionState(){JsonObject j=new JsonObject();j.addProperty("tier",tier());j.addProperty("nbt",saveCustomOnly(level.registryAccess()).toString());return j;}
    public void restoreTransactionState(JsonElement value){
        try{JsonObject j=value.getAsJsonObject();int tier=j.get("tier").getAsInt();if(tier<1||tier>5)throw new IllegalArgumentException("无效机器等级");if(tier()!=tier)level.setBlock(worldPosition,getBlockState().setValue(MachineBlock.TIER,tier),2);loadCustomOnly(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),net.minecraft.nbt.TagParser.parseCompoundFully(j.get("nbt").getAsString())));setChanged();IndustrialWorld.get((ServerLevel)level).changed((ServerLevel)level,worldPosition,false);}catch(Exception e){throw new IllegalStateException("工业事务恢复失败，记录已保留",e);}
    }
    public int radius(){return IndustrialRules.CURRENT.beaconRadii[tier()-1];}
    public boolean acceptsFluid(FluidResource r){String id=fuelRemaining>0?fuelFluid:IndustrialRules.CURRENT.generators.containsKey(kind().id)?IndustrialRules.CURRENT.generators.get(kind().id).fluid():"";return !id.isEmpty()&&IndustrialWorld.fluidMatches(r,id);}
    public ResourceHandler<ItemResource> itemsFor(Direction side){
        int[] slots=side==null?java.util.stream.IntStream.range(0,SIZE).toArray():side==Direction.DOWN?java.util.stream.IntStream.range(OUTPUT,OUTPUT_END).toArray():side==getBlockState().getValue(MachineBlock.FACING).getOpposite()?new int[]{kind().generator()?FUEL:CRYSTAL}:java.util.stream.IntStream.range(0,kind().inputs()).toArray();
        return new FilteredHandler<>(items,slots,(i,r)->side!=Direction.DOWN&&canPlaceItem(i,r.toStack(1)),(i,r)->side==Direction.DOWN||side==null&&i>=OUTPUT&&i<OUTPUT_END||i==CRYSTAL||i==FUEL);
    }
    public ResourceHandler<FluidResource> fluidsFor(Direction side){
        if(!kind().processing()&&!kind().generator())return null;
        int[] slots=kind().processing()&&side==null?new int[]{0,1}:kind().processing()&&side==Direction.DOWN?new int[]{1}:new int[]{0};return new FilteredHandler<>(fluids,slots,(i,r)->i==0&&fluids.isValid(i,r),(i,r)->i==1);
    }
    @Override public int getContainerSize(){return SIZE;}
    @Override public boolean isEmpty(){for(int i=0;i<SIZE;i++)if(!getItem(i).isEmpty())return false;return true;}
    @Override public ItemStack getItem(int i){return items.stack(i);}
    @Override public ItemStack removeItem(int i,int count){var taken=getItem(i).split(count);setChanged();return taken;}
    @Override public ItemStack removeItemNoUpdate(int i){var s=getItem(i);items.stack(i,ItemStack.EMPTY);return s;}
    @Override public void setItem(int i,ItemStack stack){items.stack(i,stack);}
    @Override public void clearContent(){for(int i=0;i<SIZE;i++)items.stack(i,ItemStack.EMPTY);}
    @Override public boolean stillValid(Player player){return !isRemoved()&&level!=null&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this&&player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64;}
    @Override public boolean canPlaceItem(int i,ItemStack s){
        if(i==CRYSTAL)return kind().processing()&&EnergyMath.accepts(tier(),IndustrialContent.crystalTier(s));
        if(i==FUEL)return kind().generator()&&kind()!=DeviceKind.SOLAR&&kind()!=DeviceKind.WIND;
        return i>=0&&i<kind().inputs()&&IndustrialContent.crystalTier(s)==0;
    }
    @Override public Component getDisplayName(){return Component.translatable(spec().title()).append(kind().graded||kind()==DeviceKind.MIXER?" · "+tier()+"级":"");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){return new IndustrialMenu(id,inv,this);}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel server)IndustrialWorld.get(server).register(this);}
    @Override public void onChunkUnloaded(){if(level instanceof ServerLevel server)IndustrialWorld.get(server).unload(this);super.onChunkUnloaded();}
    @Override public void setRemoved(){if(level instanceof ServerLevel server)IndustrialWorld.get(server).unload(this);super.setRemoved();}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level instanceof ServerLevel server)IndustrialWorld.get(server).destroy(this);}
    @Override protected void saveAdditional(ValueOutput out){
        super.saveAdditional(out);items.serialize(out.child("inventory"));fluids.serialize(out.child("fluids"));out.putString("identity",identity.toString());out.putString("beacon",beacon);out.putLong("energy",energy);out.putLong("crystal",crystalRemaining);out.putBoolean("backup",backup);out.putBoolean("running",running);out.putString("selected",selected);out.putString("queued",queued);out.putString("target_dimension",targetDimension);out.putLong("target_position",targetPosition);out.putString("target_face",targetFace.getName());
        out.putString("batch_recipe",batchRecipe);out.putLong("batch_energy",batchEnergy);out.putLong("paid",paid);out.putInt("batch_ticks",batchTicks);out.putInt("batch_speed",batchSpeed);out.putString("work",work.toString());out.store("batch_result",ItemStack.OPTIONAL_CODEC,batchResult);out.store("batch_fluid",FluidStack.OPTIONAL_CODEC,batchFluid);out.store("batch_inputs",ItemStack.CODEC.listOf(),batchInputs);
        out.putInt("fuel_total",fuelTotal);out.putInt("fuel_remaining",fuelRemaining);out.putLong("fuel_energy",fuelEnergy);out.putInt("fuel_fluid_rate",fuelFluidRate);out.putString("fuel_fluid",fuelFluid);out.putInt("fluid_remainder",fluidRemainder);out.putInt("power_remainder",powerRemainder);out.putInt("round_robin",roundRobin);out.putInt("harvest_cursor",harvestCursor);out.putInt("harvest_phase",harvestPhase);
    }
    @Override protected void loadAdditional(ValueInput in){
        super.loadAdditional(in);items.deserialize(in.childOrEmpty("inventory"));fluids.deserialize(in.childOrEmpty("fluids"));identity=UUID.fromString(in.getStringOr("identity",identity.toString()));beacon=in.getStringOr("beacon","");energy=in.getLongOr("energy",0);crystalRemaining=in.getLongOr("crystal",0);backup=in.getBooleanOr("backup",true);running=in.getBooleanOr("running",true);selected=in.getStringOr("selected","");queued=in.getStringOr("queued","");targetDimension=in.getStringOr("target_dimension","");targetPosition=in.getLongOr("target_position",0);targetFace=Direction.byName(in.getStringOr("target_face","up"));if(targetFace==null)targetFace=Direction.UP;
        batchRecipe=in.getStringOr("batch_recipe","");batchEnergy=in.getLongOr("batch_energy",0);paid=in.getLongOr("paid",0);batchTicks=in.getIntOr("batch_ticks",0);batchSpeed=in.getIntOr("batch_speed",1);work=Fraction.parse(in.getStringOr("work","0"));batchResult=in.read("batch_result",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);batchFluid=in.read("batch_fluid",FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);batchInputs=new ArrayList<>(in.read("batch_inputs",ItemStack.CODEC.listOf()).orElse(List.of()));
        fuelTotal=in.getIntOr("fuel_total",0);fuelRemaining=in.getIntOr("fuel_remaining",0);fuelEnergy=in.getLongOr("fuel_energy",0);fuelFluidRate=in.getIntOr("fuel_fluid_rate",0);fuelFluid=in.getStringOr("fuel_fluid","");fluidRemainder=in.getIntOr("fluid_remainder",0);powerRemainder=in.getIntOr("power_remainder",0);roundRobin=in.getIntOr("round_robin",0);harvestCursor=in.getIntOr("harvest_cursor",0);harvestPhase=in.getIntOr("harvest_phase",0);
    }
    public long demand(){
        if(batchRecipe.isEmpty()||batchTicks<=0||!tickTime.positive()||!canFinish())return 0;
        long duration=(long)batchTicks*128/batchSpeed;
        return ProcessingWork.demand(batchEnergy,duration,work,paid,tickTime);
    }
    public long crystalSupply(long needed){
        if(!backup||needed<=0)return 0;
        long supplied=0;
        while(supplied<needed){if(crystalRemaining==0){ItemStack s=getItem(CRYSTAL);int t=IndustrialContent.crystalTier(s);if(!EnergyMath.accepts(tier(),t))break;crystalRemaining=EnergyMath.crystal(t);s.shrink(1);}long take=Math.min(needed-supplied,crystalRemaining);crystalRemaining-=take;supplied+=take;}
        if(supplied>0)setChanged();return supplied;
    }
    public boolean canFinish(){
        try(var tx=Transaction.openRoot()){
            if(!batchResult.isEmpty()&&insertOutput(batchResult,tx)!=batchResult.getCount())return false;
            return batchFluid.isEmpty()||fluids.insert(1,FluidResource.of(batchFluid),batchFluid.getAmount(),tx)==batchFluid.getAmount();
        }
    }
    public int insertOutput(ItemStack s,TransactionContext tx){int n=0;for(int i=OUTPUT;i<OUTPUT_END&&n<s.getCount();i++)n+=items.insert(i,ItemResource.of(s),s.getCount()-n,tx);return n;}
    public void advance(long supplied){
        if(batchRecipe.isEmpty()||!canFinish())return;
        long beforePaid=paid;var step=ProcessingWork.advance(batchEnergy,(long)batchTicks*128/batchSpeed,work,paid,tickTime,supplied);work=step.work();tickTime=step.timeLeft();paid=step.paid();consumed+=paid-beforePaid;
        status=step.finished()?"加工完成":paid==beforePaid&&batchEnergy>paid?"当前供能不足，进度已保留":"加工中";
        if(step.finished()){try(var tx=Transaction.openRoot()){if(!batchResult.isEmpty()&&insertOutput(batchResult,tx)!=batchResult.getCount())return;if(!batchFluid.isEmpty()&&fluids.insert(1,FluidResource.of(batchFluid),batchFluid.getAmount(),tx)!=batchFluid.getAmount())return;tx.commit();}batchRecipe="";batchResult=ItemStack.EMPTY;batchFluid=FluidStack.EMPTY;batchInputs.clear();work=Fraction.ZERO;paid=0;if(!queued.isEmpty()){selected=queued.equals("@auto")?"":queued;queued="";}}
        setChanged();
    }
}
