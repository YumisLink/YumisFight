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
    public MachineSlots slots(){return device().slots(tier());}
    private final List<MachineLane> productionLines=createLines();
    private List<MachineLane> createLines(){List<MachineLane> result=new ArrayList<>();for(int i=0;i<device().maximumLines();i++)result.add(new MachineLane(this,i));return result;}
    public int lineCount(){return device().lineCount(tier());}
    public List<MachineLane> lanes(){return Collections.unmodifiableList(productionLines.subList(0,lineCount()));}
    public MachineLane lane(int index){if(index<0||index>=lineCount())throw new IllegalArgumentException("产线未解锁");return productionLines.get(index);}
    public int fluidTank(int line,int local){return line*device().fluidTanks()+local;}
    public int fluidTankCount(){return device().maximumLines()*device().fluidTanks();}
    public UUID identity=UUID.randomUUID();
    public String registeredIdentity="",beacon="";
    private boolean restoringPlugins;
    public final List<CrystalRoute> routes=new ArrayList<>();
    public int routeCursor;
    public Fraction functionTime=Fraction.ZERO;public long functionCredit,functionCost;
    final IndustrialFluidWork.Cycle pumpCycle=new IndustrialFluidWork.Cycle();
    public final net.minecraft.world.SimpleContainer plugins=new net.minecraft.world.SimpleContainer(3){
        @Override public int getMaxStackSize(){return 64;}
        @Override public boolean canPlaceItem(int slot,ItemStack stack){return pluginAllowed(stack);}
        @Override public void setItem(int slot,ItemStack stack){validatePluginChange(slot,stack);super.setItem(slot,stack);pluginsChanged();}
        @Override public ItemStack removeItem(int slot,int count){ItemStack next=getItem(slot).copy();next.shrink(count);validatePluginChange(slot,next);var result=super.removeItem(slot,count);pluginsChanged();return result;}
        @Override public ItemStack removeItemNoUpdate(int slot){return removeItem(slot,getItem(slot).getCount());}
    };
    public long energy,crystalRemaining;public boolean running=true,structureValid;
    public String status="未连接信标";
    public int fuelTotal,fuelRemaining,fuelFluidRate;
    public long fuelEnergy,currentPower;public String fuelFluid="";
    public int fluidRemainder,powerRemainder,harvestCursor;
    public long generated,consumed,overflow;
    public boolean workedThisTick;
    private FluidStack visibleTankFluid=FluidStack.EMPTY;
    private long visibleTankAmount,visibleTankCapacity;
    private boolean tankDisplayDirty;
    private long lastTankDisplay=Long.MIN_VALUE;
    public long visibleTankAmount(){return level instanceof ServerLevel?fluids.getAmountAsLong(0):visibleTankAmount;}
    public long visibleTankCapacity(){return level instanceof ServerLevel?fluidCapacity():visibleTankCapacity;}
    public final InventoryStore items=new InventoryStore();
    public final CurrencyUpgrader currencyUpgrade=device().currencyUpgrader()?new CurrencyUpgrader(this):null;
    public long fluidCapacity(){return Math.multiplyExact(baseFluidCapacity(),capacityMultiplier());}
    private long baseFluidCapacity(){return device().has(DeviceType.Capability.FLUID_STORAGE)?IndustrialRules.current().tankCapacities[tier()-1]:IndustrialRules.current().fluidCapacity;}
    private void pluginsChanged(){setChanged();if(!restoringPlugins&&device().has(DeviceType.Capability.FLUID_STORAGE)&&level instanceof ServerLevel server)server.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);}
    public int pluginCount(MachinePlugins.Effect effect){int count=0;for(int i=0;i<3;i++){var stack=plugins.getItem(i);var definition=pluginDefinition(stack);if(definition!=null&&definition.accepts(device())&&definition.effect()==effect)count+=stack.getCount();}return count;}
    public long capacityMultiplier(){return MachinePlugins.powerOfTwo(pluginCount(MachinePlugins.Effect.STORAGE));}
    public long energyMultiplier(){return MachinePlugins.powerOfTwo(pluginCount(MachinePlugins.Effect.SPEED));}
    public Fraction pluginSpeed(){return MachinePlugins.speed(pluginCount(MachinePlugins.Effect.SPEED));}
    public int routeLimit(){return new int[]{1,3,16}[tier()-1];}
    public static MachinePlugins.Definition pluginDefinition(ItemStack stack){if(stack.isEmpty())return null;var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());return id.getNamespace().equals(IndustrialContent.NS)?MachinePlugins.find(id.getPath()):null;}
    public boolean pluginAllowed(ItemStack stack){var definition=pluginDefinition(stack);return definition!=null&&definition.accepts(device())&&stack.getCount()<=definition.installedStackLimit();}
    public void validatePluginChange(int slot,ItemStack next){
        if(!next.isEmpty()&&!pluginAllowed(next))throw new IllegalArgumentException("该插件不适用此机器或超过机内堆叠上限");
        if(restoringPlugins||level==null||level.isClientSide())return;
        int storage=pluginCount(MachinePlugins.Effect.STORAGE);var before=pluginDefinition(plugins.getItem(slot));var after=pluginDefinition(next);
        if(before!=null&&before.effect()==MachinePlugins.Effect.STORAGE)storage-=plugins.getItem(slot).getCount();
        if(after!=null&&after.effect()==MachinePlugins.Effect.STORAGE)storage+=next.getCount();
        long multiplier=MachinePlugins.powerOfTwo(storage);
        if(items!=null)for(int i=0;i<items.size();i++){var resource=items.getResource(i);long base=resource.isEmpty()?64:resource.getMaxStackSize();if(!slots().input(i)&&!slots().output(i)||base<=1)continue;if(items.getAmountAsLong(i)>Math.multiplyExact(base,multiplier))throw new IllegalArgumentException("请先取出槽位"+(i+1)+"的超额物品");}
        if(fluids!=null)for(int i=0;i<fluids.size();i++)if(fluids.getAmountAsLong(i)>Math.multiplyExact(baseFluidCapacity(),multiplier))throw new IllegalArgumentException("请先排出液槽"+(i+1)+"的超额液体");
    }
    public long storageCapacity(){return device().has(DeviceType.Capability.STORAGE_NETWORK)?IndustrialRules.current().beaconCapacity:device().has(DeviceType.Capability.ENERGY_STORAGE)?IndustrialRules.current().accumulatorCapacities[tier()-1]:0;}
    public final FluidStore fluids=new FluidStore();
    public final class FluidStore extends IndustrialResourceStore<FluidResource> {
        FluidStore(){super(fluidTankCount(),FluidResource.EMPTY,FluidResource.OPTIONAL_CODEC);}
        public long getCapacityAsLong(int slot,FluidResource resource){return fluidCapacity();}
        public boolean isValid(int slot,FluidResource resource){return acceptsTank(slot,resource);}
        public long reservedAmount(int slot){long n=0;for(var line:productionLines)n+=line.reservedFluid(slot);return n;}
        public long availableAmount(int slot){return Math.max(0,getAmountAsLong(slot)-reservedAmount(slot));}
        @Override public int extract(int slot,FluidResource resource,int amount,TransactionContext tx){return super.extract(slot,resource,(int)Math.min(amount,availableAmount(slot)),tx);}
        int extractReserved(MachineLane owner,int slot,FluidResource resource,int amount,TransactionContext tx){
            if(owner.machine!=MachineBlockEntity.this||slot/device().fluidTanks()!=owner.index||amount<0||amount>owner.reservedFluid(slot))return 0;
            return super.extract(slot,resource,amount,tx);
        }
        protected void changed(int slot){MachineBlockEntity.this.setChanged();if(device().has(DeviceType.Capability.FLUID_STORAGE))tankDisplayDirty=true;}
        public void empty(int slot){set(slot,FluidResource.EMPTY,0);}
    }
    private boolean acceptsTank(int tank,FluidResource resource){
        if(resource.isEmpty())return false;
        if(device().has(DeviceType.Capability.FLUID_STORAGE))return true;
        int line=tank/device().fluidTanks();if(line>=lineCount())return false;
        tank%=device().fluidTanks();
        if(automationRecipeId(line).isEmpty()&&device().has(DeviceType.Capability.DUAL_FLUID)&&tank<device().fluidInputs()&&fluids!=null&&fluids.getResource(fluidTank(line,tank)).isEmpty()){
            for(int other=0;other<device().fluidInputs();other++)if(other!=tank&&fluids.getResource(fluidTank(line,other)).equals(resource))return false;
        }
        if(tank==device().fluidOutput())return device().processing()||device().has(DeviceType.Capability.PUMP);
        if(device().has(DeviceType.Capability.MINING))return tank==0&&(IndustrialFluids.matches(resource,"minecraft:water")||IndustrialFluids.matches(resource,IndustrialContent.NS+":coolant"));
        if(device().processing()&&level instanceof ServerLevel server){
            String selected=automationRecipeId(line);if(selected.isEmpty()&&device().has(DeviceType.Capability.REQUIRE_RECIPE))return false;
            for(var holder:IndustrialProcessing.recipes(server,device())){
                var r=holder.value();
                if(r.minimumTier()>tier()||!selected.isEmpty()&&!selected.equals(holder.id().identifier().toString()))continue;
                if(!selected.isEmpty())return tank<r.fluidInputs().size()&&r.fluidInputs().get(tank).ingredient().test(resource.toStack(1));
                if(r.fluidInputs().stream().anyMatch(input->input.ingredient().test(resource.toStack(1))))return true;
            }
            return false;
        }
        return tank==0&&acceptsFluid(resource);
    }
    public final class InventoryStore extends IndustrialResourceStore<ItemResource> {
        InventoryStore(){super(slots().size(),ItemResource.EMPTY,ItemResource.OPTIONAL_CODEC);}
        public ItemStack stack(int slot){return getResource(slot).toStack(getAmountAsInt(slot));}
        void stack(int slot,ItemStack stack){set(slot,ItemResource.of(stack),stack.getCount());}
        public long getCapacityAsLong(int slot,ItemResource resource){long base=resource.isEmpty()?64:resource.getMaxStackSize();return base>1&&(slots().input(slot)||slots().output(slot))?Math.multiplyExact(base,capacityMultiplier()):base;}
        public boolean isValid(int slot,ItemResource resource){return !resource.isEmpty();}
        protected void changed(int slot){MachineBlockEntity.this.setChanged();if(currencyUpgrade!=null)currencyUpgrade.changed(slot);}
    }
    public MachineBlockEntity(BlockPos pos,BlockState state){super(IndustrialContent.MACHINE_BE.get(),pos,state);}
    public DeviceType device(){return ((MachineBlock)getBlockState().getBlock()).initial.device();}
    public int tier(){return getBlockState().getValue(MachineBlock.TIER);}
    public IndustrialContent.Spec spec(){return IndustrialContent.spec(device(),tier());}
    /** Client display uses only the native tank update, never an inventory/batch snapshot. */
    public FluidStack visibleTankFluid(){return level instanceof ServerLevel?fluids.getResource(0).toStack(fluids.getResource(0).isEmpty()?0:1):visibleTankFluid;}
    public void syncTankDisplay(){if(tankDisplayDirty&&level instanceof ServerLevel server&&(lastTankDisplay==Long.MIN_VALUE||server.getGameTime()-lastTankDisplay>=5)){lastTankDisplay=server.getGameTime();tankDisplayDirty=false;server.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);}}
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries){
        var out=TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,registries);
        if(device().has(DeviceType.Capability.FLUID_STORAGE))out.store("tank_display",FluidStack.OPTIONAL_CODEC,fluids.getResource(0).toStack(fluids.getResource(0).isEmpty()?0:1));
        out.putLong("tank_amount",fluids.getAmountAsLong(0));out.putLong("tank_capacity",fluidCapacity());return out.buildResult();
    }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return device().has(DeviceType.Capability.FLUID_STORAGE)?net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this):null;}
    @Override public void handleUpdateTag(ValueInput input){visibleTankFluid=input.read("tank_display",FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);visibleTankAmount=input.getLongOr("tank_amount",0);visibleTankCapacity=input.getLongOr("tank_capacity",1);}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ValueInput input){handleUpdateTag(input);}
    public JsonObject transactionState(){JsonObject j=new JsonObject();j.addProperty("tier",tier());j.addProperty("nbt",saveCustomOnly(level.registryAccess()).toString());return j;}
    public void restoreTransactionState(JsonElement value){
        try{JsonObject j=value.getAsJsonObject();int tier=j.get("tier").getAsInt();if(tier<device().minimumTier()||tier>device().maximumTier())throw new IllegalArgumentException("无效机器等级");if(tier()!=tier)level.setBlock(worldPosition,getBlockState().setValue(MachineBlock.TIER,tier),2);loadCustomOnly(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),net.minecraft.nbt.TagParser.parseCompoundFully(j.get("nbt").getAsString())));setChanged();IndustrialWorld.get((ServerLevel)level).changed((ServerLevel)level,worldPosition);}catch(Exception e){throw new IllegalStateException("工业事务恢复失败，记录已保留",e);}
    }
    public int radius(){return IndustrialRules.current().beaconRadii[tier()-1];}
    public boolean acceptsFluid(FluidResource r){String id=fuelRemaining>0?fuelFluid:IndustrialRules.current().generators.containsKey(device().id)?IndustrialRules.current().generators.get(device().id).fluid():"";return !id.isEmpty()&&IndustrialFluids.matches(r,id);}
    public ResourceHandler<ItemResource> itemsFor(Direction side){
        var layout=slots();int[] indices=side==null?java.util.stream.IntStream.range(0,layout.size()).toArray():getSlotsForFace(side);
        boolean balance=device().has(DeviceType.Capability.PARALLEL)&&side!=Direction.DOWN&&side!=getBlockState().getValue(MachineBlock.FACING).getOpposite();
        return new FilteredHandler<>(items,indices,(i,r)->side!=Direction.DOWN&&canPlaceItem(i,r.toStack(1))&&acceptsAutomationItem(i,r.toStack(1)),(i,r)->slots().output(i)&&device().outputs()||i==layout.crystal()||i==layout.fuel(),balance?this::inputLoad:null,lineCount());
    }
    private long inputLoad(int slot){
        int line=slots().lineOf(slot);if(line<0)return Long.MAX_VALUE;
        long count=0;for(int input=0;input<device().inputs();input++)count+=items.getAmountAsLong(slots().inputStart(line)+input);return count;
    }
    @Override public int[] getSlotsForFace(Direction side){
        var layout=slots();
        if(side==Direction.DOWN)return device().outputs()?layout.outputSlots():new int[0];
        if(side==getBlockState().getValue(MachineBlock.FACING).getOpposite())return new int[]{device().generator()?layout.fuel():layout.crystal()};
        return java.util.Arrays.stream(layout.inputSlots()).boxed().sorted(java.util.Comparator.comparingLong(this::inputLoad)).mapToInt(Integer::intValue).toArray();
    }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return side!=Direction.DOWN&&canPlaceItem(slot,stack)&&acceptsAutomationItem(slot,stack);}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return side==Direction.DOWN?slots().output(slot)&&device().outputs():slot==slots().crystal()||slot==slots().fuel();}
    private String automationRecipeId(int line){
        if(!device().selectedRecipe())return "";var state=lane(line);String id=state.queued.isEmpty()?state.selected:state.queued;return id.equals("@auto")?"":id;
    }
    private boolean acceptsAutomationItem(int slot,ItemStack stack){
        if(!(level instanceof ServerLevel server))return true;
        var layout=slots();
        if(slot==layout.fuel()&&device().has(DeviceType.Capability.FUEL))return server.recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get()).stream().filter(h->h.value().machine().equals("fuel_"+device().id)).flatMap(h->h.value().ingredients().stream()).anyMatch(n->n.ingredient().test(stack));
        if(!layout.input(slot)||device().currencyUpgrader()||device().has(DeviceType.Capability.SMELTING))return true;
        String selected=automationRecipeId(layout.lineOf(slot));
        if(!selected.isEmpty())return IndustrialProcessing.recipes(server,device()).stream().filter(h->h.value().minimumTier()<=tier()&&h.id().identifier().toString().equals(selected)).anyMatch(h->{int input=layout.offset(slot);return input<h.value().ingredients().size()&&h.value().ingredients().get(input).ingredient().test(stack);});
        if(device().has(DeviceType.Capability.REQUIRE_RECIPE))return false;
        return IndustrialProcessing.recipes(server,device()).stream().filter(h->h.value().minimumTier()<=tier()).flatMap(h->h.value().ingredients().stream()).anyMatch(n->n.ingredient().test(stack));
    }
    public ResourceHandler<FluidResource> fluidsFor(Direction side){
        if(device().has(DeviceType.Capability.FLUID_STORAGE))return fluids;
        if(!device().has(DeviceType.Capability.FLUID_PROCESSING)&&!device().has(DeviceType.Capability.FLUID_FUEL))return null;
        int output=device().fluidOutput(),stride=device().fluidTanks();
        boolean inputSide=side!=Direction.DOWN||device().has(DeviceType.Capability.FLUID_INPUT_ALL_SIDES);
        int[] indices=java.util.stream.IntStream.range(0,lineCount()*stride).filter(i->side==null||(inputSide?i%stride<device().fluidInputs():i%stride==output)).toArray();
        return new FilteredHandler<>(fluids,indices,(i,r)->i%stride<output&&fluids.isValid(i,r),(i,r)->i%stride==output,device().has(DeviceType.Capability.PARALLEL)&&side!=Direction.DOWN?i->fluids.getAmountAsLong(i):null,lineCount());
    }
    @Override public int getContainerSize(){return slots().size();}
    @Override public boolean isEmpty(){for(int i=0;i<getContainerSize();i++)if(!getItem(i).isEmpty())return false;return true;}
    @Override public ItemStack getItem(int i){return items.stack(i);}
    @Override public ItemStack removeItem(int i,int count){int taken=(int)Math.min(Math.max(0,count),items.getAmountAsLong(i));var resource=items.getResource(i);items.set(i,resource,items.getAmountAsLong(i)-taken);return resource.toStack(taken);}
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
        for(int i=0;i<getContainerSize();i++){
            var resource=items.getResource(i);long quantity=items.getAmountAsLong(i);
            if(quantity>4096&&level instanceof ServerLevel server){IndustrialWorld.get(server).queueSpill(server,worldPosition,resource,quantity);items.set(i,net.neoforged.neoforge.transfer.item.ItemResource.EMPTY,0);}
            else while(items.getAmountAsLong(i)>0)drops.add(removeItem(i,resource.getMaxStackSize()));
        }
        for(int i=0;i<3;i++){var stack=plugins.getItem(i);if(!stack.isEmpty())drops.add(stack.copy());}plugins.clearContent();
        if(currencyUpgrade!=null)drops.addAll(currencyUpgrade.breakContents());
        pumpCycle.clear();functionCredit=0;
        for(var line:productionLines){if(!line.batchRecipe.isEmpty())drops.addAll(line.refundableInputs());line.clearBatch();line.selected=line.queued="";}
        energy=crystalRemaining=0;fuelRemaining=fuelTotal=0;beacon="";routes.clear();
        for(int i=0;i<fluidTankCount();i++)fluids.empty(i);
        return drops;
    }
    @Override protected void saveAdditional(ValueOutput out){
        if(currencyUpgrade!=null)currencyUpgrade.save(out);super.saveAdditional(out);out.putInt("module_schema",4);
        out.store("plugins",ItemStack.OPTIONAL_CODEC.listOf(),java.util.stream.IntStream.range(0,3).mapToObj(plugins::getItem).toList());out.store("routes",CrystalRoute.CODEC.listOf(),routes);out.putInt("route_cursor",routeCursor);out.putString("function_time",functionTime.toString());out.putLong("function_credit",functionCredit);out.putLong("function_cost",functionCost);
        items.serialize(out.child("inventory"));fluids.serialize(out.child("fluids"));out.putString("identity",identity.toString());out.putString("beacon",beacon);out.putLong("energy",energy);out.putLong("crystal",crystalRemaining);out.putBoolean("running",running);
        for(var line:productionLines)line.save(out.child("line_"+line.index));
        pumpCycle.save(out.child("pump_cycle"));
        out.putInt("fuel_total",fuelTotal);out.putInt("fuel_remaining",fuelRemaining);out.putLong("fuel_energy",fuelEnergy);out.putInt("fuel_fluid_rate",fuelFluidRate);out.putString("fuel_fluid",fuelFluid);out.putInt("fluid_remainder",fluidRemainder);out.putInt("power_remainder",powerRemainder);out.putInt("harvest_cursor",harvestCursor);
    }
    @Override protected void loadAdditional(ValueInput in){
        if(currencyUpgrade!=null)currencyUpgrade.beginLoad();super.loadAdditional(in);
        if(in.getIntOr("module_schema",0)!=4){in=in.childOrEmpty("discarded_old_module_data");identity=UUID.randomUUID();}
        items.deserialize(in.childOrEmpty("inventory"));fluids.deserialize(in.childOrEmpty("fluids"));identity=UUID.fromString(in.getStringOr("identity",identity.toString()));beacon=in.getStringOr("beacon","");energy=in.getLongOr("energy",0);crystalRemaining=in.getLongOr("crystal",0);running=in.getBooleanOr("running",true);
        var installed=in.read("plugins",ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        // Inventory has already been loaded; expanding plugins must be restored before allowing a shrink.
        restoringPlugins=true;try{for(int i=0;i<3;i++)plugins.setItem(i,i<installed.size()?installed.get(i):ItemStack.EMPTY);}finally{restoringPlugins=false;}
        routes.clear();routes.addAll(in.read("routes",CrystalRoute.CODEC.listOf()).orElse(List.of()));routeCursor=in.getIntOr("route_cursor",0);functionTime=Fraction.parse(in.getStringOr("function_time","0/1"));functionCredit=in.getLongOr("function_credit",0);functionCost=in.getLongOr("function_cost",0);
        for(var line:productionLines)line.load(in.childOrEmpty("line_"+line.index));
        pumpCycle.load(in.childOrEmpty("pump_cycle"));
        fuelTotal=in.getIntOr("fuel_total",0);fuelRemaining=in.getIntOr("fuel_remaining",0);fuelEnergy=in.getLongOr("fuel_energy",0);fuelFluidRate=in.getIntOr("fuel_fluid_rate",0);fuelFluid=in.getStringOr("fuel_fluid","");fluidRemainder=in.getIntOr("fluid_remainder",0);powerRemainder=in.getIntOr("power_remainder",0);harvestCursor=in.getIntOr("harvest_cursor",0);
        if(currencyUpgrade!=null)currencyUpgrade.load(in);
    }
    public long crystalSupply(long needed){
        if(needed<=0||device().has(DeviceType.Capability.GRID_ONLY))return 0;long supplied=0;
        while(supplied<needed){if(crystalRemaining==0){ItemStack s=getItem(slots().crystal());int t=IndustrialContent.crystalTier(s);if(device().currencyUpgrader()?t<1||t>5:!EnergyMath.accepts(tier(),t))break;crystalRemaining=EnergyMath.crystal(t);removeItem(slots().crystal(),1);}long take=Math.min(needed-supplied,crystalRemaining);crystalRemaining-=take;supplied+=take;}
        if(supplied>0)setChanged();return supplied;
    }
}
