package cn.yumis.fight.industry;

import cn.yumis.fight.rules.Models;
import cn.yumis.fight.rules.CurrencyRules;
import cn.yumis.fight.game.GameItems;
import cn.yumis.fight.server.ServerRuntime;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.fluid.*;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Persistent topology and loaded-only, server-authoritative industrial scheduling. */
public final class IndustrialWorld extends SavedData {
    public static final SavedDataType<IndustrialWorld> TYPE=new SavedDataType<>(IndustrialContent.id("industrial_networks"),IndustrialWorld::new,ChunkedStrings.CODEC.xmap(IndustrialWorld::decode,IndustrialWorld::encode));
    public static final class Node {public String dimension;public long position;public Set<String> links=new LinkedHashSet<>();Node(String d,long p){dimension=d;position=p;}}
    private final Map<String,Node> nodes=new LinkedHashMap<>();
    public final Map<String,MachineBlockEntity> loaded=new LinkedHashMap<>();
    private final Map<String,Map<Long,Set<MachineBlockEntity>>> chunks=new HashMap<>();
    private final Map<String,String> components=new HashMap<>();
    private final Map<String,Integer> cursors=new HashMap<>();
    private final Map<String,Reservation> reservations=new HashMap<>();
    private final Map<String,Totals> totals=new HashMap<>();
    private final Set<MachineBlockEntity> validations=new LinkedHashSet<>(),buildEvents=new HashSet<>();
    private boolean topologyDirty=true;
    private boolean needsRecovery=true;
    private record Environment(long time,boolean rain,boolean thunder,boolean skylight,boolean solar){}
    private final Map<ServerLevel,Environment> environments=new HashMap<>();
    private final ArrayDeque<Runnable> deferred=new ArrayDeque<>();
    public void defer(Runnable action){deferred.add(action);}
    private final Map<String,String> memberReasons=new HashMap<>();
    private void note(MachineBlockEntity be,String reason){rebuild();String id=be.kind()==DeviceKind.BEACON?be.identity.toString():be.beacon;String group=components.get(id);if(group==null)return;for(var entry:components.entrySet())if(entry.getValue().equals(group))memberReasons.put(entry.getKey(),reason);}
    private record Reservation(String device,long credit){}
    public static final class Totals {public long generated,consumed,overflow,lastGenerated,lastConsumed,lastOverflow;public int ticks;void finish(){if(++ticks>=20){lastGenerated=generated;lastConsumed=consumed;lastOverflow=overflow;generated=consumed=overflow=0;ticks=0;}}}
    public static IndustrialWorld get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);}
    private static IndustrialWorld decode(String json){IndustrialWorld w=new IndustrialWorld();JsonObject root=JsonParser.parseString(json).getAsJsonObject();if(root.has("nodes"))for(var e:root.getAsJsonObject("nodes").entrySet())w.nodes.put(e.getKey(),Models.JSON.fromJson(e.getValue(),Node.class));if(root.has("cursors"))for(var e:root.getAsJsonObject("cursors").entrySet())w.cursors.put(e.getKey(),e.getValue().getAsInt());return w;}
    private String encode(){JsonObject root=new JsonObject();root.add("nodes",Models.JSON.toJsonTree(nodes));root.add("cursors",Models.JSON.toJsonTree(cursors));return root.toString();}
    private String dimension(MachineBlockEntity be){return be.getLevel().dimension().identifier().toString();}
    public void register(MachineBlockEntity be){
        needsRecovery=true;
        if(be.getLevel()==null||be.getLevel().isClientSide())return;String key=be.identity.toString();MachineBlockEntity prior=loaded.get(key);
        if(prior!=null&&prior!=be&&!prior.getBlockPos().equals(be.getBlockPos())){be.identity=UUID.randomUUID();key=be.identity.toString();be.setChanged();}
        if(!be.registeredIdentity.isEmpty()&&!be.registeredIdentity.equals(key))loaded.remove(be.registeredIdentity,be);be.registeredIdentity=key;loaded.put(key,be);chunks.computeIfAbsent(dimension(be),d->new HashMap<>()).computeIfAbsent(chunk(be.getBlockPos()),c->new LinkedHashSet<>()).add(be);
        if(be.kind()==DeviceKind.BEACON){nodes.putIfAbsent(key,new Node(dimension(be),be.getBlockPos().asLong()));topologyDirty=true;setDirty();}
        if(be.kind()==DeviceKind.BEACON||be.kind()==DeviceKind.FORGE)validations.add(be);
        note(be,"区块加载：已恢复原有连接");
    }
    public void unload(MachineBlockEntity be){
        loaded.remove(be.identity.toString(),be);pendingFunctions.remove(be.identity.toString());reservations.entrySet().removeIf(e->e.getValue().device.equals(be.identity.toString()));var dimension=chunks.get(dimension(be));if(dimension!=null){var bucket=dimension.get(chunk(be.getBlockPos()));if(bucket!=null)bucket.remove(be);}validations.remove(be);buildEvents.remove(be);note(be,"区块卸载：该成员储能和容量暂不参与");
    }
    public void destroy(MachineBlockEntity be){
        unload(be);if(be.kind()==DeviceKind.BEACON){note(be,"核心拆除：仅本核心余能消散");String key=be.identity.toString();nodes.remove(key);for(Node n:nodes.values())n.links.remove(key);for(var device:loaded.values())if(device.beacon.equals(key)){device.beacon="";device.setChanged();}be.energy=0;topologyDirty=true;reservations.clear();setDirty();}
    }
    private static long chunk(BlockPos p){return net.minecraft.world.level.ChunkPos.pack(p.getX()>>4,p.getZ()>>4);}
    public List<MachineBlockEntity> nearby(ServerLevel level,BlockPos pos,int range){
        List<MachineBlockEntity> found=new ArrayList<>();Map<Long,Set<MachineBlockEntity>> index=chunks.get(level.dimension().identifier().toString());if(index==null)return found;
        for(int x=(pos.getX()-range)>>4;x<=(pos.getX()+range)>>4;x++)for(int z=(pos.getZ()-range)>>4;z<=(pos.getZ()+range)>>4;z++){var list=index.get(net.minecraft.world.level.ChunkPos.pack(x,z));if(list!=null)for(var be:list)if(!be.isRemoved())found.add(be);}return found;
    }
    public void changed(ServerLevel level,BlockPos pos,boolean build){for(var be:nearby(level,pos,3))if((be.kind()==DeviceKind.BEACON||be.kind()==DeviceKind.FORGE)&&be.getBlockPos().distSqr(pos)<=18){validations.add(be);if(build)buildEvents.add(be);}}
    public void chunkChanged(ServerLevel level){for(var be:loaded.values())if(be.getLevel()==level&&(be.kind()==DeviceKind.BEACON||be.kind()==DeviceKind.FORGE))validations.add(be);}
    public void placed(MachineBlockEntity be){
        register(be);if(be.kind()==DeviceKind.BEACON){validations.add(be);buildEvents.add(be);}else discover(be);
    }
    public void discover(MachineBlockEntity be){
        if(be.kind()==DeviceKind.BEACON||validBeacon(be)!=null)return;
        int range=Arrays.stream(IndustrialRules.CURRENT.beaconRadii).max().orElse(32);MachineBlockEntity best=null;double dist=Double.MAX_VALUE;
        for(var other:nearby((ServerLevel)be.getLevel(),be.getBlockPos(),range))if(other.kind()==DeviceKind.BEACON&&other.structureValid){double d=be.getBlockPos().distSqr(other.getBlockPos());if(d<=other.radius()*(double)other.radius()&&(d<dist||d==dist&&best!=null&&other.identity.compareTo(best.identity)<0)){best=other;dist=d;}}
        if(best!=null){be.beacon=best.identity.toString();be.setChanged();}
    }
    public MachineBlockEntity validBeacon(MachineBlockEntity be){MachineBlockEntity core=loaded.get(be.beacon);return core!=null&&core.kind()==DeviceKind.BEACON&&core.structureValid&&core.getLevel()==be.getLevel()&&core.getBlockPos().distSqr(be.getBlockPos())<=core.radius()*(double)core.radius()?core:null;}
    public void bind(MachineBlockEntity core,MachineBlockEntity device){if(core.kind()!=DeviceKind.BEACON||!core.structureValid||device.kind()==DeviceKind.BEACON||core.getLevel()!=device.getLevel()||core.getBlockPos().distSqr(device.getBlockPos())>core.radius()*(double)core.radius())throw new IllegalArgumentException("信标无效或设备超出覆盖范围");device.beacon=core.identity.toString();device.setChanged();}
    public void link(MachineBlockEntity a,MachineBlockEntity b){
        int range=Math.max(a.radius(),b.radius());if(a==b||a.kind()!=DeviceKind.BEACON||b.kind()!=DeviceKind.BEACON||!a.structureValid||!b.structureValid||a.getLevel()!=b.getLevel()||a.getBlockPos().distSqr(b.getBlockPos())>range*(double)range)throw new IllegalArgumentException("信标须有效、同维度且在较大半径内");
        Node left=nodes.get(a.identity.toString()),right=nodes.get(b.identity.toString());if(!left.links.remove(b.identity.toString())){left.links.add(b.identity.toString());right.links.add(a.identity.toString());}else right.links.remove(a.identity.toString());topologyDirty=true;reservations.clear();note(a,"权杖修改信标连接");note(b,"权杖修改信标连接");setDirty();
    }
    private void rebuild(){
        if(!topologyDirty)return;components.clear();for(String start:nodes.keySet())if(!components.containsKey(start)){
            Set<String> group=new LinkedHashSet<>();ArrayDeque<String> queue=new ArrayDeque<>();queue.add(start);while(!queue.isEmpty()){String id=queue.remove();Node n=nodes.get(id);if(n==null||!group.add(id))continue;queue.addAll(n.links);}String root=group.stream().min(String::compareTo).orElse(start);for(String id:group)components.put(id,root);
        }topologyDirty=false;
    }
    public Block structureMaterial(int tier){return IndustrialContent.ALL_BLOCKS.get(switch(tier){case 1->"energy_crystal_block_2";case 2->"";case 3->"titanium_block";case 4->"terra_block";default->"spacetime_block";})==null?Blocks.DIAMOND_BLOCK:IndustrialContent.ALL_BLOCKS.get(switch(tier){case 1->"energy_crystal_block_2";case 3->"titanium_block";case 4->"terra_block";default->"spacetime_block";}).get();}
    public List<BlockPos> missing(MachineBlockEntity be){
        ServerLevel level=(ServerLevel)be.getLevel();Block material=structureMaterial(be.tier());List<BlockPos> missing=new ArrayList<>();BlockPos pos=be.getBlockPos();
        if(be.kind()==DeviceKind.BEACON){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){BlockPos p=pos.offset(x,-1,z);if(!level.hasChunkAt(p)||!level.getBlockState(p).is(material))missing.add(p);}}
        else if(be.kind()==DeviceKind.FORGE){BlockPos center=pos.relative(be.getBlockState().getValue(MachineBlock.FACING).getOpposite()).above();for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){BlockPos p=center.offset(x,y,z);if(p.equals(pos))continue;boolean centerCell=x==0&&y==0&&z==0;if(!level.hasChunkAt(p)||(centerCell?!level.getBlockState(p).isAir():!level.getBlockState(p).is(material)))missing.add(p);}}
        return missing;
    }
    public static void loadRules(MinecraftServer server){try{var resource=server.getResourceManager().getResource(IndustrialContent.id("rules/industrial.json"));if(resource.isPresent())try(var reader=resource.get().openAsReader()){IndustrialRules.CURRENT=IndustrialRules.parse(JsonParser.parseReader(reader).toString());}var w=get(server.overworld());w.validations.addAll(w.loaded.values().stream().filter(b->b.kind()==DeviceKind.BEACON||b.kind()==DeviceKind.FORGE).toList());}catch(Exception error){System.err.println("[魔能工业] 规则重载失败，保留上一版："+error.getMessage());}}
    public static boolean fluidMatches(FluidResource resource,String id){if(id.startsWith("#"))return resource.toStack(1).is(TagKey.create(Registries.FLUID,Identifier.parse(id.substring(1))));return resource.is(BuiltInRegistries.FLUID.getValue(Identifier.parse(id)));}
    public static void tick(ServerRuntime runtime){get(runtime.server.overworld()).runTick(runtime.server);}
    private void runTick(MinecraftServer server){
        int count=deferred.size();for(int i=0;i<count;i++)deferred.removeFirst().run();
        if(needsRecovery&&ServerRuntime.ready()){ServerRuntime.get().inventory.recoverIndustrial();needsRecovery=false;}
        environments.clear();
        for(var be:new ArrayList<>(validations)){if(be.isRemoved())continue;boolean was=be.structureValid;be.structureValid=missing(be).isEmpty();if(be.structureValid&&!was&&buildEvents.contains(be)&&be.kind()==DeviceKind.BEACON)for(var device:nearby((ServerLevel)be.getLevel(),be.getBlockPos(),be.radius()))discover(device);if(was!=be.structureValid)note(be,be.structureValid?"结构修复：恢复原储能":"结构失效：该核心储能暂不可用");}validations.clear();buildEvents.clear();rebuild();
        Map<String,List<MachineBlockEntity>> cores=new LinkedHashMap<>(),devices=new LinkedHashMap<>();Map<String,Long> production=new HashMap<>();
        for(var be:new ArrayList<>(loaded.values()))if(!be.isRemoved()){
            be.tickTime=Fraction.of(128);be.consumed=0;boolean connected=validBeacon(be)!=null;
            if(be.kind()==DeviceKind.BEACON&&be.structureValid)cores.computeIfAbsent(components.get(be.identity.toString()),k->new ArrayList<>()).add(be);
            if(be.kind().generator()){long generated=generate(be);be.generated=generated;if(connected)production.merge(components.get(be.beacon),generated,Math::addExact);else be.overflow=generated;}
            else if(be.kind()!=DeviceKind.BEACON){if(connected)devices.computeIfAbsent(components.get(be.beacon),k->new ArrayList<>()).add(be);else if(be.kind().processing()&&be.running&&(be.kind()!=DeviceKind.FORGE||be.structureValid)){for(int round=0;round<128&&be.tickTime.positive();round++){prepare(be);if(be.batchRecipe.isEmpty())break;Fraction before=be.tickTime;long need=be.demand();be.advance(be.crystalSupply(need));if(before.equals(be.tickTime))break;}}}
            output(be);
        }
        for(var entry:cores.entrySet())settle(entry.getKey(),entry.getValue(),devices.getOrDefault(entry.getKey(),List.of()),production.getOrDefault(entry.getKey(),0L));
        for(var be:new ArrayList<>(loaded.values()))if(!be.isRemoved()){visual(be,be.kind()==DeviceKind.BEACON?be.structureValid:validBeacon(be)!=null);output(be);}
    }
    private void visual(MachineBlockEntity be,boolean connected){BlockState state=be.getBlockState();boolean active=be.kind().generator()?be.generated>0:be.consumed>0;if(state.getValue(MachineBlock.CONNECTED)!=connected||state.getValue(MachineBlock.ACTIVE)!=active)be.getLevel().setBlock(be.getBlockPos(),state.setValue(MachineBlock.CONNECTED,connected).setValue(MachineBlock.ACTIVE,active),2);}
    public List<RecipeHolder<IndustrialRecipe>> recipes(ServerLevel level,DeviceKind kind){return level.recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get()).stream().filter(h->h.value().machine().equals(kind.id)).sorted(Comparator.comparing(h->h.id().identifier().toString())).toList();}
    private void prepare(MachineBlockEntity be){
        if(!be.running||!be.tickTime.positive()){be.status="已停止";return;}if(be.kind()==DeviceKind.FORGE&&!be.structureValid){be.status="结构不完整：缺少 "+missing(be).size()+" 处";return;}if(!be.batchRecipe.isEmpty()){if(!be.canFinish())be.status="产物空间不足";return;}
        if(be.kind()==DeviceKind.MINER&&be.selected.isEmpty()){be.status="请选择生成配方";return;}
        ServerLevel level=(ServerLevel)be.getLevel();List<ItemStack> input=new ArrayList<>();for(int i=0;i<be.kind().inputs();i++)input.add(be.getItem((i+be.roundRobin)%Math.max(1,be.kind().inputs())));
        RecipeHolder<IndustrialRecipe> chosen=null;int[] plan=null;int bestRank=Integer.MAX_VALUE;
        for(var holder:recipes(level,be.kind())){IndustrialRecipe r=holder.value();if(r.minimumTier()>be.tier()||be.kind()==DeviceKind.CURRENCY&&r.minimumTier()!=be.tier())continue;if(be.kind()!=DeviceKind.CURRENCY&&!be.selected.isEmpty()&&!holder.id().identifier().toString().equals(be.selected))continue;int[] take=r.takePlan(input);if(take!=null&&fluidInputFits(be,r)){int rank=0;while(rank<take.length&&take[rank]==0)rank++;if(chosen==null||rank<bestRank){chosen=holder;plan=take;bestRank=rank;}}}
        if(chosen==null&&be.kind()==DeviceKind.FURNACE&&be.selected.isEmpty()){for(int i=0;i<3;i++){int slot=(i+be.roundRobin)%3;ItemStack stack=be.getItem(slot);if(stack.isEmpty())continue;SingleRecipeInput nativeInput=new SingleRecipeInput(stack);var nativeRecipe=level.recipeAccess().getRecipeFor(RecipeType.SMELTING,nativeInput,level);if(nativeRecipe.isPresent()){var recipe=nativeRecipe.get().value();IndustrialRecipe fallback=new IndustrialRecipe(be.kind().id,1,List.of(new IndustrialRecipe.Need(Ingredient.of(stack.getItem()),1)),recipe.assemble(nativeInput),recipe.cookingTime(),100,Optional.empty(),FluidStack.EMPTY);start(be,"vanilla:"+nativeRecipe.get().id().identifier(),fallback,new int[]{1},new int[]{slot});return;}}}
        if(chosen==null){be.status="缺少原料或可用配方";return;}IndustrialRecipe r=chosen.value();int[] indices=new int[input.size()];for(int i=0;i<indices.length;i++)indices[i]=(i+be.roundRobin)%Math.max(1,be.kind().inputs());if(be.kind()==DeviceKind.MINER&&be.selected.isEmpty())be.selected=chosen.id().identifier().toString();start(be,chosen.id().identifier().toString(),r,plan,indices);
    }
    private boolean fluidInputFits(MachineBlockEntity be,IndustrialRecipe r){return r.fluidInput().isEmpty()||r.fluidInput().get().test(be.fluids.getResource(0).toStack(be.fluids.getAmountAsInt(0)));}
    private void start(MachineBlockEntity be,String id,IndustrialRecipe r,int[] plan,int[] indices){
        ItemStack result=be.kind()==DeviceKind.CURRENCY?GameItems.item(CurrencyRules.rollGuaranteed(new Random(be.getLevel().getRandom().nextLong()),IndustrialRules.CURRENT.currencyPool()).id(),1):r.result().copy();
        be.batchResult=result;be.batchFluid=r.fluidResult().copy();if(!be.canFinish()&&be.kind()!=DeviceKind.CURRENCY){be.batchResult=ItemStack.EMPTY;be.batchFluid=FluidStack.EMPTY;be.status="产物空间不足";return;}
        try(var tx=Transaction.openRoot()){
            List<ItemStack> escrow=new ArrayList<>();for(int i=0;i<plan.length;i++)if(plan[i]>0){ItemResource resource=be.items.getResource(indices[i]);if(be.items.extract(indices[i],resource,plan[i],tx)!=plan[i])return;escrow.add(resource.toStack(plan[i]));}
            if(r.fluidInput().isPresent()){int amount=r.fluidInput().get().amount();if(be.fluids.extract(0,be.fluids.getResource(0),amount,tx)!=amount)return;}
            tx.commit();be.batchInputs=escrow;
        }
        be.batchRecipe=id;be.batchEnergy=r.energy();be.batchTicks=r.ticks();be.batchSpeed=be.kind()==DeviceKind.FURNACE?IndustrialRules.CURRENT.furnaceSpeeds[be.tier()-1]:be.kind()==DeviceKind.MIXER||be.kind()==DeviceKind.CURRENCY?1:1<<(be.tier()-r.minimumTier());be.work=Fraction.ZERO;be.paid=0;be.roundRobin=(be.roundRobin+1)%Math.max(1,be.kind().inputs());be.status="加工中";be.setChanged();
    }
    private static boolean skyVisible(ServerLevel level,BlockPos machine){var chunk=level.getChunkSource().getChunkNow(machine.getX()>>4,machine.getZ()>>4);return chunk!=null&&machine.getY()+1>=chunk.getSkyLightSources().getLowestSourceY(machine.getX()&15,machine.getZ()&15);}
    private long generate(MachineBlockEntity be){
        be.currentPower=0;
        if(!be.running){be.status="已停止";return 0;}ServerLevel level=(ServerLevel)be.getLevel();IndustrialRules rules=IndustrialRules.CURRENT;
        if(be.kind()==DeviceKind.SOLAR||be.kind()==DeviceKind.WIND){Environment env=environments.computeIfAbsent(level,l->new Environment(l.getDefaultClockTime(),l.isRaining(),l.isThundering(),l.dimensionType().hasSkyLight(),l.dimensionType().hasSkyLight()&&!l.dimensionType().hasFixedTime()&&l.dimensionType().defaultClock().isPresent()));boolean sky=skyVisible(level,be.getBlockPos());long power=0;if(be.kind()==DeviceKind.WIND){int y=be.getBlockPos().getY();if(sky&&env.skylight&&y>=rules.windBaseHeight)power=rules.windBasePower+(long)Math.floorDiv(y-rules.windBaseHeight,rules.windHeightStep)*rules.windPowerStep;}else{int state=EnergyMath.solar(env.time,env.rain,env.thunder,sky,env.solar,rules.solarPeriods);power=state==0?0:env.rain?rules.solarRainPower:state==2?rules.solarDayPower:rules.solarEdgePower;}be.currentPower=power;long whole=(power+be.powerRemainder)/20;be.powerRemainder=(int)((power+be.powerRemainder)%20);be.status=power>0?"发能 "+power+" 点/秒":"当前环境不满足发电条件";be.setChanged();return whole;}
        if(be.fuelRemaining<=0){
            var fuels=level.recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get()).stream().filter(h->h.value().machine().equals("fuel_"+be.kind().id)).toList();IndustrialRecipe fuel=null;for(var h:fuels)if(h.value().takePlan(List.of(be.getItem(MachineBlockEntity.FUEL)))!=null){fuel=h.value();break;}
            if(fuel==null){be.status="缺少燃料";return 0;}
            var g=rules.generators.get(be.kind().id);if(g.fluidPerSecond()>0&&(!fluidMatches(be.fluids.getResource(0),g.fluid())||be.fluids.getAmountAsInt(0)<(g.fluidPerSecond()+be.fluidRemainder)/20)){be.status="缺少必要流体";return 0;}
            int[] take=fuel.takePlan(List.of(be.getItem(MachineBlockEntity.FUEL)));be.removeItem(MachineBlockEntity.FUEL,take[0]);be.fuelTotal=be.fuelRemaining=fuel.ticks();be.fuelEnergy=fuel.energy();be.fuelFluid=g.fluid();be.fuelFluidRate=g.fluidPerSecond();
        }
        int fluid=(be.fuelFluidRate+be.fluidRemainder)/20;
        if(fluid>0){if(!fluidMatches(be.fluids.getResource(0),be.fuelFluid)||be.fluids.getAmountAsInt(0)<fluid){be.status="缺少必要流体，燃料已暂停";return 0;}try(var tx=Transaction.openRoot()){if(be.fluids.extract(0,be.fluids.getResource(0),fluid,tx)!=fluid)return 0;tx.commit();}}
        be.fluidRemainder=(be.fuelFluidRate+be.fluidRemainder)%20;int done=be.fuelTotal-be.fuelRemaining;long generated=EnergyMath.ceilProduct(be.fuelEnergy,done+1,be.fuelTotal)-EnergyMath.ceilProduct(be.fuelEnergy,done,be.fuelTotal);be.fuelRemaining--;be.currentPower=be.fuelEnergy*20/be.fuelTotal;be.status="发能中";be.setChanged();return generated;
    }
    public void requestRecovery(){needsRecovery=true;}
    private void settle(String group,List<MachineBlockEntity> cores,List<MachineBlockEntity> devices,long generated){
        long stored=0,capacity=Math.multiplyExact(IndustrialRules.CURRENT.beaconCapacity,cores.size());for(var core:cores)stored=Math.addExact(stored,core.energy);long available=Math.addExact(stored,generated),used=0;
        Reservation reservation=reservations.get(group);if(reservation!=null&&(!loaded.containsKey(reservation.device)||validBeacon(loaded.get(reservation.device))==null||!Objects.equals(components.get(loaded.get(reservation.device).beacon),group)))reservation=null;
        long held=reservation==null?0:reservation.credit;String owner=reservation==null?"":reservation.device;if(held>stored){held=0;owner="";}
        List<FunctionJob> jobs=new ArrayList<>();for(var be:devices)if(be.kind().functional())prepareFunctions(be,jobs);
        for(int round=0;round<128;round++){
            List<MachineBlockEntity> processors=devices.stream().filter(b->b.kind().processing()&&b.running&&(b.kind()!=DeviceKind.FORGE||b.structureValid)&&b.tickTime.positive()).toList();for(var be:processors)prepare(be);
            long[] wants=new long[processors.size()+jobs.size()];for(int i=0;i<processors.size();i++)wants[i]=processors.get(i).demand();for(int i=0;i<jobs.size();i++)wants[processors.size()+i]=Math.max(0,jobs.get(i).demand()-(jobs.get(i).be.identity.toString().equals(owner)?held:0));
            var allocation=EnergyMath.allocate(Math.max(0,available-used-held),wants,cursors.getOrDefault(group,0));cursors.put(group,allocation.nextCursor());boolean progress=false;
            for(int i=0;i<processors.size();i++){var be=processors.get(i);if(be.batchRecipe.isEmpty())continue;Fraction before=be.work;String previous=be.batchRecipe;long grant=allocation.grants()[i];long extra=be.crystalSupply(Math.max(0,wants[i]-grant));be.advance(grant+extra);used+=grant;if(grant+extra>0||!be.work.equals(before)||!be.batchRecipe.equals(previous))progress=true;}
            for(int i=0;i<jobs.size();i++){FunctionJob job=jobs.get(i);String key=job.be.identity.toString();long credit=key.equals(owner)?held:0,grant=allocation.grants()[processors.size()+i];long spent=job.run(grant+credit);if(spent<0){if(key.equals(owner)){held=0;owner="";}job.done=true;progress=true;continue;}used+=spent;if(spent>0){progress=true;job.be.consumed+=spent;}
                long rest=grant+credit-spent;if(rest>0&&!job.done&&(owner.isEmpty()||owner.equals(key))&&rest<=capacity){held=rest;owner=key;progress|=grant>0;}else if(key.equals(owner)){held=0;owner="";}
            }
            jobs.removeIf(j->j.done);if(!progress)break;
        }
        if(held>0)reservations.put(group,new Reservation(owner,held));else reservations.remove(group);
        long balance=available-used,spill=Math.max(0,balance-capacity);balance-=spill;long difference=balance-stored;
        for(var core:cores){if(difference>0){long n=Math.min(difference,IndustrialRules.CURRENT.beaconCapacity-core.energy);core.energy+=n;difference-=n;}else if(difference<0){long n=Math.min(-difference,core.energy);core.energy-=n;difference+=n;}core.setChanged();}
        Totals t=totals.computeIfAbsent(group,k->new Totals());t.generated+=generated;t.consumed+=used;t.overflow+=spill;t.finish();setDirty();
    }
    private void output(MachineBlockEntity be){if(!be.kind().processing())return;ServerLevel level=(ServerLevel)be.getLevel();BlockPos below=be.getBlockPos().below();if(!level.hasChunkAt(below))return;ResourceHandler<ItemResource> target=level.getCapability(Capabilities.Item.BLOCK,below,Direction.UP);if(target==null)return;for(int i=MachineBlockEntity.OUTPUT;i<MachineBlockEntity.OUTPUT_END;i++){ItemResource resource=be.items.getResource(i);int amount=be.items.getAmountAsInt(i);if(resource.isEmpty()||amount==0)continue;try(var tx=Transaction.openRoot()){int n=target.insert(resource,amount,tx);if(n>0&&be.items.extract(i,resource,n,tx)==n)tx.commit();}}}
    private abstract static class FunctionJob {final MachineBlockEntity be;boolean done;FunctionJob(MachineBlockEntity b){be=b;}abstract long demand();abstract long run(long budget);}
    private final Map<String,FunctionJob> pendingFunctions=new HashMap<>();
    private void prepareFunctions(MachineBlockEntity be,List<FunctionJob> jobs){
        if(!be.running)return;FunctionJob pending=pendingFunctions.get(be.identity.toString());if(pending!=null&&!pending.done){jobs.add(pending);return;}
        ServerLevel level=(ServerLevel)be.getLevel();IndustrialRules rules=IndustrialRules.CURRENT;
        if(be.kind()==DeviceKind.TRANSFER){if(Math.floorMod(level.getGameTime()+be.getBlockPos().asLong(),rules.transferIntervals[be.tier()-1])!=0)return;FunctionJob j=transfer(be);if(j!=null){pendingFunctions.put(be.identity.toString(),j);jobs.add(j);}}
        else {int width=rules.harvestSizes[be.tier()-1],area=width*width;int phase=be.harvestPhase%rules.harvestPeriod;int checks=area*(phase+1)/rules.harvestPeriod-area*phase/rules.harvestPeriod;be.harvestPhase=(phase+1)%rules.harvestPeriod;List<FunctionJob> ready=new ArrayList<>();for(int i=0;i<checks;i++){int at=Math.floorMod(be.harvestCursor++,area);BlockPos crop=be.getBlockPos().below().offset(at%width-width/2,0,at/width-width/2);FunctionJob j=harvest(be,crop);if(j!=null)ready.add(j);}if(!ready.isEmpty()){FunctionJob batch=new FunctionJob(be){long demand(){long n=0;for(var j:ready)if(!j.done)n=Math.addExact(n,j.demand());return n;}long run(long budget){long used=0;for(var j:ready)if(!j.done){long n=j.run(budget-used);if(n<0)j.done=true;else used+=n;}done=ready.stream().allMatch(j->j.done);return used==0&&done?-1:used;}};pendingFunctions.put(be.identity.toString(),batch);jobs.add(batch);}be.setChanged();}
    }
    private FunctionJob transfer(MachineBlockEntity be){
        ServerLevel level=(ServerLevel)be.getLevel();if(be.targetDimension.isEmpty()){be.status="尚未绑定目标容器";return null;}if(!be.targetDimension.equals(dimension(be))){be.status="目标不在同一维度";return null;}BlockPos sourcePos=be.getBlockPos().below(),target=BlockPos.of(be.targetPosition);if(!level.hasChunkAt(sourcePos)||!level.hasChunkAt(target)){be.status="目标区块未加载";return null;}int tier=be.tier()-1;double range=IndustrialRules.CURRENT.transferRanges[tier];if(be.tier()<3&&be.getBlockPos().distSqr(target)>range*range){be.status="目标超出传送距离";return null;}if(sourcePos.equals(target)||sameChest(level,sourcePos,target)){be.status="源与目标为同一容器";return null;}
        ResourceHandler<ItemResource> source=level.getCapability(Capabilities.Item.BLOCK,sourcePos,Direction.UP),destination=level.getCapability(Capabilities.Item.BLOCK,target,be.targetFace);if(source==null||destination==null||source==destination){be.status="源或目标容器不可用";return null;}
        var sourceEntity=level.getBlockEntity(sourcePos);var targetEntity=level.getBlockEntity(target);var sourceBlock=level.getBlockState(sourcePos).getBlock();var targetBlock=level.getBlockState(target).getBlock();
        return new FunctionJob(be){int left=IndustrialRules.CURRENT.transferAmounts[tier];final long cost=IndustrialRules.CURRENT.transferEnergy[tier];long demand(){return Math.multiplyExact(cost,left);}long run(long budget){if(!level.hasChunkAt(target)||!level.hasChunkAt(sourcePos)||level.getBlockEntity(target)!=targetEntity||level.getBlockEntity(sourcePos)!=sourceEntity||!level.getBlockState(target).is(targetBlock)||!level.getBlockState(sourcePos).is(sourceBlock)){done=true;return -1;}var currentSource=level.getCapability(Capabilities.Item.BLOCK,sourcePos,Direction.UP);var currentTarget=level.getCapability(Capabilities.Item.BLOCK,target,be.targetFace);if(currentSource==null||currentTarget==null){done=true;return -1;}boolean possible=false;try(var simulation=Transaction.openRoot()){for(int index=0;index<currentSource.size();index++){ItemResource item=currentSource.getResource(index);if(!item.isEmpty()&&currentSource.extract(index,item,1,simulation)==1&&currentTarget.insert(item,1,simulation)==1){possible=true;break;}}}if(!possible){done=true;be.status="源为空或目标拒绝接收";return -1;}int quota=cost==0?left:(int)Math.min(left,budget/cost);if(quota<=0){be.status="当前供能不足";return 0;}int moved=0;for(int slot=0;slot<currentSource.size()&&moved<quota;slot++){int index=(slot+be.roundRobin)%currentSource.size();ItemResource r=currentSource.getResource(index);if(r.isEmpty())continue;try(var tx=Transaction.openRoot()){int inserted=currentTarget.insert(r,Math.min(quota-moved,currentSource.getAmountAsInt(index)),tx);if(inserted>0&&currentSource.extract(index,r,inserted,tx)==inserted){tx.commit();moved+=inserted;}}}left-=moved;be.roundRobin=(be.roundRobin+1)%Math.max(1,currentSource.size());be.status=moved>0?"已传送 "+moved+" 件":"源为空或目标拒绝接收";if(left==0||moved==0)done=true;return moved*cost;}};
    }
    private boolean sameChest(ServerLevel level,BlockPos a,BlockPos b){BlockState state=level.getBlockState(a);return state.getBlock() instanceof ChestBlock&&state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE&&a.relative(ChestBlock.getConnectedDirection(state)).equals(b);}
    private FunctionJob harvest(MachineBlockEntity be,BlockPos pos){
        ServerLevel level=(ServerLevel)be.getLevel();BlockPos output=be.getBlockPos().below();if(!level.hasChunkAt(pos)||!level.hasChunkAt(output))return null;ResourceHandler<ItemResource> target=level.getCapability(Capabilities.Item.BLOCK,output,Direction.UP);if(target==null){be.status="下方没有可接收容器";return null;}
        BlockState state=level.getBlockState(pos);String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();if(!IndustrialRules.CURRENT.harvestCrops.contains(id))return null;IntegerProperty age=state.getProperties().stream().filter(p->p instanceof IntegerProperty&&p.getName().equals("age")).map(p->(IntegerProperty)p).findFirst().orElse(null);if(age==null||state.getValue(age)!=Collections.max(age.getPossibleValues()))return null;
        List<ItemStack> drops=Block.getDrops(state,level,pos,level.getBlockEntity(pos),null,ItemStack.EMPTY);BlockState reset=state.setValue(age,Collections.min(age.getPossibleValues()));long cost=IndustrialRules.CURRENT.harvestEnergy;
        var outputEntity=level.getBlockEntity(output);
        return new FunctionJob(be){long demand(){return cost;}long run(long budget){if(!level.hasChunkAt(pos)||!level.getBlockState(pos).equals(state)||!level.hasChunkAt(output)||level.getBlockEntity(output)!=outputEntity){done=true;return -1;}var receiver=level.getCapability(Capabilities.Item.BLOCK,output,Direction.UP);if(receiver==null){done=true;return -1;}try(var tx=Transaction.openRoot()){for(ItemStack s:drops)if(receiver.insert(ItemResource.of(s),s.getCount(),tx)!=s.getCount()){be.status="下方容器装不下完整掉落";return -1;}if(budget<cost){be.status="当前供能不足";return 0;}new SnapshotJournal<BlockState>(){protected BlockState createSnapshot(){return level.getBlockState(pos);}protected void revertToSnapshot(BlockState old){level.setBlock(pos,old,2);}protected void onRootCommit(BlockState old){level.sendBlockUpdated(pos,old,reset,3);}}.updateSnapshots(tx);level.setBlock(pos,reset,2);tx.commit();done=true;be.status="作物已收获并重置";return cost;}}};
    }
    public JsonObject info(MachineBlockEntity be){return info(be,false);}
    public JsonObject info(MachineBlockEntity be,boolean includeLines){
        JsonObject j=new JsonObject();j.addProperty("name",be.getDisplayName().getString());j.addProperty("status",be.status);j.addProperty("tier",be.tier());j.addProperty("kind",be.kind().id);j.addProperty("backup",be.backup);j.addProperty("running",be.running);j.addProperty("connected",validBeacon(be)!=null);j.addProperty("crystal",be.crystalRemaining);j.addProperty("energy",be.energy);j.addProperty("total_energy",be.batchEnergy);j.addProperty("paid",be.paid);j.addProperty("recipe",be.batchRecipe);j.addProperty("selected",be.selected);j.addProperty("progress",be.batchTicks==0?0:be.work.value()/((long)be.batchTicks*128/be.batchSpeed));j.addProperty("fuel",be.fuelRemaining);j.addProperty("fluid_in",be.fluids.getAmountAsInt(0));j.addProperty("fluid_out",be.fluids.getAmountAsInt(1));j.addProperty("target",be.targetDimension.isEmpty()?"未绑定":be.targetDimension+" "+BlockPos.of(be.targetPosition).toShortString());
        MachineBlockEntity core=be.kind()==DeviceKind.BEACON?be:validBeacon(be);if(core!=null){String group=components.get(core.identity.toString());long balance=0,capacity=0;for(var other:loaded.values())if(other.kind()==DeviceKind.BEACON&&other.structureValid&&Objects.equals(components.get(other.identity.toString()),group)){balance+=other.energy;capacity+=IndustrialRules.CURRENT.beaconCapacity;}j.addProperty("network_energy",balance);j.addProperty("network_capacity",capacity);Totals t=totals.get(group);if(t!=null){j.addProperty("generated",t.lastGenerated);j.addProperty("consumed",t.lastConsumed);j.addProperty("overflow",t.lastOverflow);}j.addProperty("member_change",memberReasons.getOrDefault(core.identity.toString(),"网络成员已恢复"));j.addProperty("radius",core.radius());}
        JsonArray list=new JsonArray();for(var r:recipes((ServerLevel)be.getLevel(),be.kind())){JsonObject entry=new JsonObject();entry.addProperty("id",r.id().identifier().toString());entry.addProperty("tier",r.value().minimumTier());entry.addProperty("ticks",r.value().ticks());entry.addProperty("energy",r.value().energy());entry.addProperty("ingredients",r.value().ingredients().stream().map(n->n.count()+" × "+n.ingredient().items().limit(3).map(h->new ItemStack(h.value()).getHoverName().getString()).reduce((a,b)->a+" / "+b).orElse("缺少兼容材料")).reduce((a,b)->a+"；"+b).orElse("无需物品原料")+r.value().fluidInput().map(f->"；流体 "+f.amount()+" mB").orElse(""));entry.addProperty("name",r.value().result().isEmpty()?(r.value().fluidResult().isEmpty()?"随机通货":r.value().fluidResult().getHoverName().getString()+" "+r.value().fluidResult().getAmount()+" mB"):r.value().result().getHoverName().getString()+" ×"+r.value().result().getCount());list.add(entry);}j.add("recipes",list);j.addProperty("show_lines",includeLines);JsonArray links=new JsonArray();if(includeLines&&be.kind()==DeviceKind.BEACON&&nodes.containsKey(be.identity.toString())){for(String id:nodes.get(be.identity.toString()).links){Node n=nodes.get(id);if(n!=null)links.add(n.position);}for(var device:loaded.values())if(device.beacon.equals(be.identity.toString())&&validBeacon(device)!=null)links.add(device.getBlockPos().asLong());}else if(includeLines&&validBeacon(be)!=null)links.add(validBeacon(be).getBlockPos().asLong());if(includeLines)j.add("links",links);
        JsonArray help=new JsonArray();help.add("工业魔能独立于角色MP；设备只在必要区块加载时运行。");
        if(be.kind().processing()){help.add("网络优先；水晶备用"+(be.backup?"已开启":"已关闭")+"，插槽仅接受"+(be.tier()*2-1)+"～"+be.tier()*2+"阶。");help.add("产物先存本机，再向下方容器输出；缺能或满仓保留批次。");help.add("当前批次耗能 "+be.batchEnergy+"，已扣 "+be.paid+"；处理能力由整机共享。");if(be.kind()==DeviceKind.CURRENCY)help.add("每轮必出1枚；使用当前工业奖池，不读取玩家掉宝，不再判5%。点击概率查看每种通货的实际权重。");}
        if(be.kind().generator()){j.addProperty("power",be.currentPower);help.add("实际产能 "+be.currentPower+" 点/秒；满网继续运行，多余产能浪费。");if(be.fuelTotal>0)help.add("本燃料总产能 "+be.fuelEnergy+"，燃烧 "+be.fuelTotal+" tick；流体 "+be.fuelFluidRate+" mB/秒。");if(be.kind()==DeviceKind.SOLAR||be.kind()==DeviceKind.WIND){ServerLevel l=(ServerLevel)be.getLevel();help.add("时间 "+Math.floorMod(l.getDefaultClockTime(),24000)+"；天气 "+(l.isThundering()?"雷暴":l.isRaining()?"雨":"晴")+"；顶部天空 "+(skyVisible(l,be.getBlockPos())?"可见":"被遮挡"));}}
        if(be.kind().generator()){
            if(be.kind()==DeviceKind.WIND)help.add("最低高度 "+IndustrialRules.CURRENT.windBaseHeight+"，基础 "+IndustrialRules.CURRENT.windBasePower+" 点/秒，每升 "+IndustrialRules.CURRENT.windHeightStep+" 格再加 "+IndustrialRules.CURRENT.windPowerStep+" 点/秒；必须露天且有天空光。");
            else if(be.kind()==DeviceKind.SOLAR)help.add("日照边界 "+java.util.Arrays.toString(IndustrialRules.CURRENT.solarPeriods)+"；白天 "+IndustrialRules.CURRENT.solarDayPower+"，晨昏 "+IndustrialRules.CURRENT.solarEdgePower+"，雨天 "+IndustrialRules.CURRENT.solarRainPower+" 点/秒，雷暴或夜间停机。");
            else {var rule=IndustrialRules.CURRENT.generators.get(be.kind().id);help.add("必要流体 "+(rule.fluid().isEmpty()?"无":rule.fluid()+" · "+rule.fluidPerSecond()+" mB/秒"));for(var holder:((ServerLevel)be.getLevel()).recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get())){var r=holder.value();if(r.machine().equals("fuel_"+be.kind().id))help.add(r.ingredients().stream().map(n->n.count()+" × "+n.ingredient().items().limit(2).map(h->new ItemStack(h.value()).getHoverName().getString()).reduce((a,b)->a+" / "+b).orElse("缺少兼容燃料")).reduce((a,b)->a+"；"+b).orElse("无燃料")+"："+r.ticks()+" tick，共 "+r.energy()+" 点");}}
        }
        if(be.kind()==DeviceKind.BEACON){help.add("九个同级材料组成3×3基座，核心放在上方中央。");help.add("三维半径 "+be.radius()+"，本核心容量 "+IndustrialRules.CURRENT.beaconCapacity+"；任意等级机器均可接入。");help.add("信标之间用权杖手动连接；中继卸载保留原网络关系。");}
        if(be.kind()==DeviceKind.FORGE){help.add("3×3×3：控制器位于底层正面中央，另需25块同级材料，中心留空。");help.add("下方容器位于结构外，可接收控制器产物。");}
        if(be.kind()==DeviceKind.TRANSFER){int t=be.tier()-1;help.add("每 "+IndustrialRules.CURRENT.transferIntervals[t]+" tick最多搬运 "+IndustrialRules.CURRENT.transferAmounts[t]+" 件，每件 "+IndustrialRules.CURRENT.transferEnergy[t]+" 点。");help.add("只支持同维度；目标不存在或未加载时暂停，原坐标恢复容器后继续。");help.add("当前目标："+j.get("target").getAsString());}
        if(be.kind()==DeviceKind.HARVEST){help.add("水平 "+IndustrialRules.CURRENT.harvestSizes[be.tier()-1]+"×"+IndustrialRules.CURRENT.harvestSizes[be.tier()-1]+"，每 "+IndustrialRules.CURRENT.harvestPeriod+" tick一轮，每株 "+IndustrialRules.CURRENT.harvestEnergy+" 点。");help.add("完整成熟掉落（含种子、副产物）入下方箱，植物保留并重置；不扣种子补种。");}
        if(be.kind()==DeviceKind.BEACON||be.kind()==DeviceKind.FORGE){JsonArray missing=new JsonArray();for(BlockPos p:missing(be))missing.add(p.asLong());j.add("missing",missing);j.addProperty("structure_material",new ItemStack(structureMaterial(be.tier())).getHoverName().getString());}
        if(be.kind()!=DeviceKind.CURRENCY&&!be.batchResult.isEmpty())j.add("preview",ItemStack.OPTIONAL_CODEC.encodeStart(be.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),be.batchResult).getOrThrow());
        if(be.kind()==DeviceKind.MIXER&&!be.batchFluid.isEmpty())j.add("preview",ItemStack.OPTIONAL_CODEC.encodeStart(be.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),IndustrialContent.stack("coolant_bucket",1)).getOrThrow());
        help.add("当前状态："+be.status);j.add("help",help);return j;
    }
}
