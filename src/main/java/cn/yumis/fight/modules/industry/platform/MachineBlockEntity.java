package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.fluid.*;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Industry-owned inventory, tanks and immutable-at-start batch snapshot. */
public final class MachineBlockEntity extends BlockEntity implements cn.yumis.fight.modules.inventory.api.PersistentContainer,WorldlyContainer,MenuProvider {
    @Override public String key(){return identity.toString();}
    @Override public void requestRecovery(){if(level instanceof ServerLevel server)IndustrialWorld.get(server).requestRecovery();}
    public MachineSlots slots(){return device().slots;}
    public UUID identity=UUID.randomUUID();
    public String registeredIdentity="",beacon="",targetDimension="";
    public long targetPosition,sourcePosition;public String sourceDimension="";public Direction targetFace=Direction.UP,sourceFace=Direction.DOWN;
    public long energy,crystalRemaining;public boolean running=true,structureValid;
    public String selected="",queued="",status="未连接信标",batchRecipe="";
    public long batchEnergy,paid,batchSequence;public double workThisTick;
    public Fraction batchDuration=Fraction.ZERO,work=Fraction.ZERO,tickTime=Fraction.of(128);
    public List<ItemStack> batchResults=new ArrayList<>(),batchInputs=new ArrayList<>();
    public FluidStack batchFluid=FluidStack.EMPTY;
    public String batchMiningFluid="";public int batchFluidRate;public long batchFluidPaid;
    public int fuelTotal,fuelRemaining,fuelFluidRate;
    public long fuelEnergy,currentPower;public String fuelFluid="";
    public int fluidRemainder,powerRemainder,roundRobin,harvestCursor,harvestPhase;
    public long generated,consumed,overflow;
    private FluidStack visibleTankFluid=FluidStack.EMPTY;
    public final InventoryStore items=new InventoryStore();
    public final CurrencyUpgrader currencyUpgrade=device().currencyUpgrader()?new CurrencyUpgrader(this):null;
    public int fluidCapacity(){return device().has(DeviceType.Capability.FLUID_STORAGE)?IndustrialRules.current().tankCapacities[tier()-1]:IndustrialRules.current().fluidCapacity;}
    public long storageCapacity(){return device().has(DeviceType.Capability.STORAGE_NETWORK)?IndustrialRules.current().beaconCapacity:device().has(DeviceType.Capability.ENERGY_STORAGE)?IndustrialRules.current().accumulatorCapacities[tier()-1]:0;}
    public final FluidStacksResourceHandler fluids=new FluidStacksResourceHandler(device().fluidTanks(),IndustrialRules.current().fluidCapacity){
        @Override protected int getCapacity(int i,FluidResource resource){return fluidCapacity();}
        @Override public boolean isValid(int i,FluidResource resource){return acceptsTank(i,resource);}
        @Override protected void onContentsChanged(int i,FluidStack previous){
            MachineBlockEntity.this.setChanged();
            if(device().has(DeviceType.Capability.FLUID_STORAGE)&&level instanceof ServerLevel server)
                server.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    };
    private boolean acceptsTank(int tank,FluidResource resource){
        if(resource.isEmpty())return false;
        if(device().has(DeviceType.Capability.FLUID_STORAGE))return true;
        if(tank==device().fluidOutput())return device().processing()||device().has(DeviceType.Capability.PUMP);
        if(device().has(DeviceType.Capability.MINING))return tank==0&&(IndustrialFluids.matches(resource,"minecraft:water")||IndustrialFluids.matches(resource,IndustrialContent.NS+":coolant"));
        if(device().processing()&&level instanceof ServerLevel server){
            for(var holder:IndustrialProcessing.recipes(server,device())){
                var r=holder.value();
                if(r.minimumTier()>tier()||!selected.isEmpty()&&!selected.equals(holder.id().identifier().toString()))continue;
                if(tank<r.fluidInputs().size()&&r.fluidInputs().get(tank).ingredient().test(resource.toStack(1)))return true;
            }
            return false;
        }
        return tank==0&&acceptsFluid(resource);
    }
    public final class InventoryStore extends ItemStacksResourceHandler {
        InventoryStore(){super(slots().size());}
        public ItemStack stack(int i){return stacks.get(i);}
        void stack(int i,ItemStack s){ItemStack before=stacks.set(i,s);onContentsChanged(i,before);}
        @Override protected void onContentsChanged(int i,ItemStack previous){MachineBlockEntity.this.setChanged();if(currencyUpgrade!=null)currencyUpgrade.changed(i);}
    }
    public MachineBlockEntity(BlockPos pos,BlockState state){super(IndustrialContent.MACHINE_BE.get(),pos,state);}
    public DeviceType device(){return ((MachineBlock)getBlockState().getBlock()).initial.device();}
    public int tier(){return getBlockState().getValue(MachineBlock.TIER);}
    public IndustrialContent.Spec spec(){return IndustrialContent.spec(device(),tier());}
    /** Client display uses only the native tank update, never an inventory/batch snapshot. */
    public FluidStack visibleTankFluid(){return level instanceof ServerLevel?fluids.getResource(0).toStack(fluids.getAmountAsInt(0)):visibleTankFluid;}
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries){
        var out=TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,registries);
        if(device().has(DeviceType.Capability.FLUID_STORAGE))out.store("tank_display",FluidStack.OPTIONAL_CODEC,fluids.getResource(0).toStack(fluids.getAmountAsInt(0)));
        return out.buildResult();
    }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return device().has(DeviceType.Capability.FLUID_STORAGE)?net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this):null;}
    @Override public void handleUpdateTag(ValueInput input){visibleTankFluid=input.read("tank_display",FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ValueInput input){handleUpdateTag(input);}
    public JsonObject transactionState(){JsonObject j=new JsonObject();j.addProperty("tier",tier());j.addProperty("nbt",saveCustomOnly(level.registryAccess()).toString());return j;}
    public void restoreTransactionState(JsonElement value){
        try{JsonObject j=value.getAsJsonObject();int tier=j.get("tier").getAsInt();if(tier<device().minimumTier()||tier>device().maximumTier())throw new IllegalArgumentException("无效机器等级");if(tier()!=tier)level.setBlock(worldPosition,getBlockState().setValue(MachineBlock.TIER,tier),2);loadCustomOnly(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),net.minecraft.nbt.TagParser.parseCompoundFully(j.get("nbt").getAsString())));setChanged();IndustrialWorld.get((ServerLevel)level).changed((ServerLevel)level,worldPosition);}catch(Exception e){throw new IllegalStateException("工业事务恢复失败，记录已保留",e);}
    }
    public int radius(){return IndustrialRules.current().beaconRadii[tier()-1];}
    public boolean acceptsFluid(FluidResource r){String id=fuelRemaining>0?fuelFluid:IndustrialRules.current().generators.containsKey(device().id)?IndustrialRules.current().generators.get(device().id).fluid():"";return !id.isEmpty()&&IndustrialFluids.matches(r,id);}
    public ResourceHandler<ItemResource> itemsFor(Direction side){
        var layout=slots();int[] indices=side==null?java.util.stream.IntStream.range(0,layout.size()).toArray():getSlotsForFace(side);
        return new FilteredHandler<>(items,indices,(i,r)->side!=Direction.DOWN&&canPlaceItem(i,r.toStack(1))&&acceptsAutomationInput(i,r.toStack(1)),(i,r)->layout.output(i)&&device().outputs()||i==layout.crystal()||i==layout.fuel());
    }
    @Override public int[] getSlotsForFace(Direction side){
        var layout=slots();
        if(side==Direction.DOWN)return device().outputs()?java.util.stream.IntStream.range(layout.outputStart(),layout.outputEnd()).toArray():new int[0];
        if(side==getBlockState().getValue(MachineBlock.FACING).getOpposite())return new int[]{device().generator()?layout.fuel():layout.crystal()};
        return java.util.stream.IntStream.range(0,device().inputs()).toArray();
    }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return side!=Direction.DOWN&&canPlaceItem(slot,stack)&&acceptsAutomationInput(slot,stack);}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return side==Direction.DOWN?slots().output(slot)&&device().outputs():slot==slots().crystal()||slot==slots().fuel();}
    private boolean acceptsAutomationInput(int slot,ItemStack stack){
        if(!slots().input(slot)||!device().selectedRecipe())return true;
        if(selected.isEmpty())return !device().has(DeviceType.Capability.REQUIRE_RECIPE);
        if(!(level instanceof ServerLevel server))return false;
        var id=net.minecraft.resources.Identifier.tryParse(selected);if(id==null)return false;
        var holder=server.recipeAccess().recipeMap().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,id));
        if(holder==null||!(holder.value() instanceof IndustrialRecipe recipe)||!recipe.machine().equals(device().id)||recipe.minimumTier()>tier())return false;
        return slot<recipe.ingredients().size()&&recipe.ingredients().get(slot).ingredient().test(stack);
    }
    public ResourceHandler<FluidResource> fluidsFor(Direction side){
        if(device().has(DeviceType.Capability.FLUID_STORAGE))return fluids;
        if(!device().has(DeviceType.Capability.FLUID_PROCESSING)&&!device().has(DeviceType.Capability.FLUID_FUEL))return null;
        int output=device().fluidOutput();
        int[] indices=side==null?java.util.stream.IntStream.range(0,device().fluidTanks()).toArray():side==Direction.DOWN?new int[]{output}:java.util.stream.IntStream.range(0,device().fluidInputs()).toArray();
        return new FilteredHandler<>(fluids,indices,(i,r)->i<output&&fluids.isValid(i,r),(i,r)->i==output);
    }
    @Override public int getContainerSize(){return slots().size();}
    @Override public boolean isEmpty(){for(int i=0;i<getContainerSize();i++)if(!getItem(i).isEmpty())return false;return true;}
    @Override public ItemStack getItem(int i){return items.stack(i);}
    @Override public ItemStack removeItem(int i,int count){var taken=getItem(i).split(count);setChanged();if(currencyUpgrade!=null)currencyUpgrade.changed(i);return taken;}
    @Override public ItemStack removeItemNoUpdate(int i){var s=getItem(i);items.stack(i,ItemStack.EMPTY);return s;}
    @Override public void setItem(int i,ItemStack stack){items.stack(i,stack);}
    @Override public void clearContent(){for(int i=0;i<getContainerSize();i++)items.stack(i,ItemStack.EMPTY);}
    @Override public boolean stillValid(Player player){return !isRemoved()&&level!=null&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this&&player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64;}
    @Override public boolean canPlaceItem(int i,ItemStack s){
        if(i==slots().crystal())return !device().has(DeviceType.Capability.GRID_ONLY)&&(device().currencyUpgrader()?IndustrialContent.crystalTier(s)>=1&&IndustrialContent.crystalTier(s)<=5:device().processing()&&EnergyMath.accepts(tier(),IndustrialContent.crystalTier(s)));
        if(i==slots().fuel())return device().has(DeviceType.Capability.FUEL);
        if(device().currencyUpgrader())return slots().input(i)&&cn.yumis.fight.modules.economy.api.CurrencyRules.upgradeTier(cn.yumis.fight.platform.ItemData.own(s)?cn.yumis.fight.platform.ItemData.id(s):"")>=0;
        return slots().input(i)&&(IndustrialContent.crystalTier(s)==0||device()==DeviceType.FORGE||device()==DeviceType.REACTOR);
    }
    @Override public Component getDisplayName(){return Component.translatable(spec().title());}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){return new IndustrialMenu(id,inv,this);}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel server){if(device().has(DeviceType.Capability.WIND))WindGeneratorPartBlock.ensure(server,worldPosition);IndustrialWorld.get(server).register(this);}}
    @Override public void onChunkUnloaded(){if(level instanceof ServerLevel server)IndustrialWorld.get(server).unload(this);super.onChunkUnloaded();}
    @Override public void setRemoved(){if(level instanceof ServerLevel server)IndustrialWorld.get(server).unload(this);super.setRemoved();}
    private boolean contentsDropped;
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level instanceof ServerLevel server){for(ItemStack stack:takeBreakContents())net.minecraft.world.level.block.Block.popResource(server,pos,stack);IndustrialWorld.get(server).destroy(this);}}
    public List<ItemStack> takeBreakContents(){
        if(contentsDropped)return List.of();contentsDropped=true;List<ItemStack> drops=new ArrayList<>();
        for(int i=0;i<getContainerSize();i++){ItemStack stack=removeItemNoUpdate(i);if(!stack.isEmpty())drops.add(stack);}
        if(!batchRecipe.isEmpty())for(ItemStack stack:batchInputs)if(!stack.isEmpty())drops.add(stack.copy());
        clearBatch();batchEnergy=paid=energy=crystalRemaining=0;fuelRemaining=fuelTotal=0;beacon="";targetDimension="";targetPosition=0;selected=queued="";
        try(var tx=Transaction.openRoot()){for(int i=0;i<device().fluidTanks();i++){int amount=fluids.getAmountAsInt(i);if(amount>0)fluids.extract(i,fluids.getResource(i),amount,tx);}tx.commit();}
        return drops;
    }
    private void clearBatch(){batchInputs.clear();batchRecipe="";batchResults=new ArrayList<>();batchFluid=FluidStack.EMPTY;batchMiningFluid="";batchFluidRate=0;batchFluidPaid=0;work=Fraction.ZERO;paid=0;batchDuration=Fraction.ZERO;}
    @Override protected void saveAdditional(ValueOutput out){
        if(currencyUpgrade!=null)currencyUpgrade.save(out);super.saveAdditional(out);out.putInt("module_schema",2);
        items.serialize(out.child("inventory"));fluids.serialize(out.child("fluids"));out.putString("identity",identity.toString());out.putString("beacon",beacon);out.putLong("energy",energy);out.putLong("crystal",crystalRemaining);out.putBoolean("running",running);out.putString("selected",selected);out.putString("queued",queued);out.putString("target_dimension",targetDimension);out.putLong("target_position",targetPosition);out.putString("target_face",targetFace.getName());out.putString("source_dimension",sourceDimension);out.putLong("source_position",sourcePosition);out.putString("source_face",sourceFace.getName());
        out.putLong("batch_sequence",batchSequence);out.putString("batch_recipe",batchRecipe);out.putLong("batch_energy",batchEnergy);out.putLong("paid",paid);out.putString("batch_duration",batchDuration.toString());out.putString("work",work.toString());out.store("batch_results",ItemStack.CODEC.listOf(),batchResults);out.store("batch_fluid",FluidStack.OPTIONAL_CODEC,batchFluid);out.store("batch_inputs",ItemStack.CODEC.listOf(),batchInputs);
        out.putString("batch_mining_fluid",batchMiningFluid);out.putInt("batch_fluid_rate",batchFluidRate);out.putLong("batch_fluid_paid",batchFluidPaid);
        out.putInt("fuel_total",fuelTotal);out.putInt("fuel_remaining",fuelRemaining);out.putLong("fuel_energy",fuelEnergy);out.putInt("fuel_fluid_rate",fuelFluidRate);out.putString("fuel_fluid",fuelFluid);out.putInt("fluid_remainder",fluidRemainder);out.putInt("power_remainder",powerRemainder);out.putInt("round_robin",roundRobin);out.putInt("harvest_cursor",harvestCursor);out.putInt("harvest_phase",harvestPhase);
    }
    @Override protected void loadAdditional(ValueInput in){
        if(currencyUpgrade!=null)currencyUpgrade.beginLoad();super.loadAdditional(in);
        if(in.getIntOr("module_schema",0)!=2){in=in.childOrEmpty("discarded_old_module_data");identity=UUID.randomUUID();}
        items.deserialize(in.childOrEmpty("inventory"));fluids.deserialize(in.childOrEmpty("fluids"));identity=UUID.fromString(in.getStringOr("identity",identity.toString()));beacon=in.getStringOr("beacon","");energy=in.getLongOr("energy",0);crystalRemaining=in.getLongOr("crystal",0);running=in.getBooleanOr("running",true);selected=in.getStringOr("selected","");queued=in.getStringOr("queued","");targetDimension=in.getStringOr("target_dimension","");targetPosition=in.getLongOr("target_position",0);targetFace=Direction.byName(in.getStringOr("target_face","up"));if(targetFace==null)targetFace=Direction.UP;sourceDimension=in.getStringOr("source_dimension","");sourcePosition=in.getLongOr("source_position",0);sourceFace=Direction.byName(in.getStringOr("source_face","down"));if(sourceFace==null)sourceFace=Direction.DOWN;
        batchSequence=in.getLongOr("batch_sequence",0);batchRecipe=in.getStringOr("batch_recipe","");batchEnergy=in.getLongOr("batch_energy",0);paid=in.getLongOr("paid",0);batchDuration=Fraction.parse(in.getStringOr("batch_duration","0"));work=Fraction.parse(in.getStringOr("work","0"));batchResults=new ArrayList<>(in.read("batch_results",ItemStack.CODEC.listOf()).orElse(List.of()));batchFluid=in.read("batch_fluid",FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);batchInputs=new ArrayList<>(in.read("batch_inputs",ItemStack.CODEC.listOf()).orElse(List.of()));
        batchMiningFluid=in.getStringOr("batch_mining_fluid","");batchFluidRate=in.getIntOr("batch_fluid_rate",0);batchFluidPaid=in.getLongOr("batch_fluid_paid",0);
        fuelTotal=in.getIntOr("fuel_total",0);fuelRemaining=in.getIntOr("fuel_remaining",0);fuelEnergy=in.getLongOr("fuel_energy",0);fuelFluidRate=in.getIntOr("fuel_fluid_rate",0);fuelFluid=in.getStringOr("fuel_fluid","");fluidRemainder=in.getIntOr("fluid_remainder",0);powerRemainder=in.getIntOr("power_remainder",0);roundRobin=in.getIntOr("round_robin",0);harvestCursor=in.getIntOr("harvest_cursor",0);harvestPhase=in.getIntOr("harvest_phase",0);
        if(currencyUpgrade!=null)currencyUpgrade.load(in);
    }
    private Fraction usableTime(){
        if(batchFluidRate==0)return tickTime;
        // Integer mB are paid ahead of fractional work. Emptying the tank must not
        // strand the remaining cooling time already paid by the current batch.
        long available=IndustrialFluids.matches(fluids.getResource(0),batchMiningFluid)?fluids.getAmountAsInt(0):0;
        Fraction end=Fraction.of(batchFluidPaid+available).multiply(2560).divide(batchFluidRate);
        return end.compareTo(work)<=0?Fraction.ZERO:tickTime.min(end.subtract(work));
    }
    public long demand(){if(batchRecipe.isEmpty()||!batchDuration.positive()||!tickTime.positive()||!canFinish())return 0;return ProcessingWork.demand(batchEnergy,batchDuration,work,paid,usableTime());}
    public long crystalSupply(long needed){
        if(needed<=0||device().has(DeviceType.Capability.GRID_ONLY))return 0;long supplied=0;
        while(supplied<needed){if(crystalRemaining==0){ItemStack s=getItem(slots().crystal());int t=IndustrialContent.crystalTier(s);if(device().currencyUpgrader()?t<1||t>5:!EnergyMath.accepts(tier(),t))break;crystalRemaining=EnergyMath.crystal(t);s.shrink(1);}long take=Math.min(needed-supplied,crystalRemaining);crystalRemaining-=take;supplied+=take;}
        if(supplied>0)setChanged();return supplied;
    }
    public boolean canFinish(){try(var tx=Transaction.openRoot()){for(ItemStack result:batchResults)if(insertOutput(result,tx)!=result.getCount())return false;return batchFluid.isEmpty()||fluids.insert(device().fluidOutput(),FluidResource.of(batchFluid),batchFluid.getAmount(),tx)==batchFluid.getAmount();}}
    public int insertOutput(ItemStack s,TransactionContext tx){int n=0;for(int i=slots().outputStart();i<slots().outputEnd()&&n<s.getCount();i++)n+=items.insert(i,ItemResource.of(s),s.getCount()-n,tx);return n;}
    public void advance(long supplied){
        if(batchRecipe.isEmpty()||!canFinish())return;
        Fraction time=usableTime();if(!time.positive()){status="批次冷却液不足，进度已保留";return;}
        Fraction before=work;long beforePaid=paid;var step=ProcessingWork.advance(batchEnergy,batchDuration,work,paid,time,supplied);
        long fluidDue=batchFluidRate==0?0:step.work().multiply(batchFluidRate).divide(2560).ceil()-batchFluidPaid;
        try(var tx=Transaction.openRoot()){
            if(fluidDue>0&&fluids.extract(0,fluids.getResource(0),(int)fluidDue,tx)!=fluidDue)return;
            if(step.finished()){for(ItemStack result:batchResults)if(insertOutput(result,tx)!=result.getCount())return;if(!batchFluid.isEmpty()&&fluids.insert(device().fluidOutput(),FluidResource.of(batchFluid),batchFluid.getAmount(),tx)!=batchFluid.getAmount())return;}
            tx.commit();
        }
        work=step.work();tickTime=tickTime.subtract(work.subtract(before));paid=step.paid();batchFluidPaid+=fluidDue;consumed+=paid-beforePaid;workThisTick+=Math.max(0,work.subtract(before).value());
        status=step.finished()?"加工完成":paid==beforePaid&&batchEnergy>paid?"当前供能不足，进度已保留":"加工中";
        if(step.finished()){clearBatch();if(device().selectedRecipe()&&!queued.isEmpty())selected=queued.equals("@auto")?"":queued;queued="";}
        setChanged();
    }
}
