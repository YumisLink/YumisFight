package cn.yumis.fight.modules.industry.platform;
import cn.yumis.fight.modules.industry.api.IndustrialRules;

import cn.yumis.fight.modules.industry.api.Fraction;
import cn.yumis.fight.modules.industry.api.EnergyMath;
import cn.yumis.fight.modules.industry.api.ChunkedStrings;
import cn.yumis.fight.modules.industry.api.DeviceType;


import cn.yumis.fight.modules.industry.api.IndustrialPorts;
import com.google.gson.*;

import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;

import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;

import net.minecraft.world.level.saveddata.*;

import net.neoforged.neoforge.transfer.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.fluid.*;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Persistent topology and loaded-only, server-authoritative industrial scheduling. */
public final class IndustrialWorld extends SavedData {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    public static final SavedDataType<IndustrialWorld> TYPE=new SavedDataType<>(IndustrialContent.id("industrial_networks_modules_v1"),IndustrialWorld::new,ChunkedStrings.CODEC.xmap(IndustrialWorld::decode,IndustrialWorld::encode));
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
    private final IndustrialGeneration generation=new IndustrialGeneration();
    private final ArrayDeque<Runnable> deferred=new ArrayDeque<>();
    public void defer(Runnable action){deferred.add(action);}
    private final Map<String,String> memberReasons=new HashMap<>();
    private void note(MachineBlockEntity be,String reason){rebuild();String id=be.device().has(DeviceType.Capability.STORAGE_NETWORK)?be.identity.toString():be.beacon;String group=components.get(id);if(group==null)return;for(var entry:components.entrySet())if(entry.getValue().equals(group))memberReasons.put(entry.getKey(),reason);}
    private record Reservation(String device,long credit){}
    public static final class Totals {public long generated,consumed,overflow,lastGenerated,lastConsumed,lastOverflow;public int ticks;void finish(){if(++ticks>=20){lastGenerated=generated;lastConsumed=consumed;lastOverflow=overflow;generated=consumed=overflow=0;ticks=0;}}}
    public static IndustrialWorld get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);}
    private static IndustrialWorld decode(String json){IndustrialWorld w=new IndustrialWorld();JsonObject root=JsonParser.parseString(json).getAsJsonObject();if(root.has("nodes"))for(var e:root.getAsJsonObject("nodes").entrySet())w.nodes.put(e.getKey(),JSON.fromJson(e.getValue(),Node.class));if(root.has("cursors"))for(var e:root.getAsJsonObject("cursors").entrySet())w.cursors.put(e.getKey(),e.getValue().getAsInt());return w;}
    private String encode(){JsonObject root=new JsonObject();root.add("nodes",JSON.toJsonTree(nodes));root.add("cursors",JSON.toJsonTree(cursors));return root.toString();}
    private String dimension(MachineBlockEntity be){return be.getLevel().dimension().identifier().toString();}
    public void register(MachineBlockEntity be){
        needsRecovery=true;
        if(be.getLevel()==null||be.getLevel().isClientSide())return;String key=be.identity.toString();MachineBlockEntity prior=loaded.get(key);
        if(prior!=null&&prior!=be&&!prior.getBlockPos().equals(be.getBlockPos())){be.identity=UUID.randomUUID();key=be.identity.toString();be.setChanged();}
        if(!be.registeredIdentity.isEmpty()&&!be.registeredIdentity.equals(key))loaded.remove(be.registeredIdentity,be);be.registeredIdentity=key;loaded.put(key,be);chunks.computeIfAbsent(dimension(be),d->new HashMap<>()).computeIfAbsent(chunk(be.getBlockPos()),c->new LinkedHashSet<>()).add(be);
        if(be.device().has(DeviceType.Capability.STORAGE_NETWORK)){nodes.putIfAbsent(key,new Node(dimension(be),be.getBlockPos().asLong()));topologyDirty=true;setDirty();}
        if(be.device().needsStructure())validations.add(be);
        note(be,"区块加载：已恢复原有连接");
    }
    public void unload(MachineBlockEntity be){
        loaded.remove(be.identity.toString(),be);functions.unload(be.identity.toString());reservations.entrySet().removeIf(e->e.getValue().device.equals(be.identity.toString()));var dimension=chunks.get(dimension(be));if(dimension!=null){var bucket=dimension.get(chunk(be.getBlockPos()));if(bucket!=null)bucket.remove(be);}validations.remove(be);buildEvents.remove(be);note(be,"区块卸载：该成员储能和容量暂不参与");
    }
    public void destroy(MachineBlockEntity be){
        unload(be);if(be.device().has(DeviceType.Capability.STORAGE_NETWORK)){note(be,"核心拆除：仅本核心余能消散");String key=be.identity.toString();nodes.remove(key);for(Node n:nodes.values())n.links.remove(key);for(var device:loaded.values())if(device.beacon.equals(key)){device.beacon="";device.setChanged();}be.energy=0;topologyDirty=true;reservations.clear();setDirty();}
    }
    private static long chunk(BlockPos p){return net.minecraft.world.level.ChunkPos.pack(p.getX()>>4,p.getZ()>>4);}
    public List<MachineBlockEntity> nearby(ServerLevel level,BlockPos pos,int range){
        List<MachineBlockEntity> found=new ArrayList<>();Map<Long,Set<MachineBlockEntity>> index=chunks.get(level.dimension().identifier().toString());if(index==null)return found;
        for(int x=(pos.getX()-range)>>4;x<=(pos.getX()+range)>>4;x++)for(int z=(pos.getZ()-range)>>4;z<=(pos.getZ()+range)>>4;z++){var list=index.get(net.minecraft.world.level.ChunkPos.pack(x,z));if(list!=null)for(var be:list)if(!be.isRemoved())found.add(be);}return found;
    }
    public void changed(ServerLevel level,BlockPos pos,boolean build){for(var be:nearby(level,pos,3))if((be.device().needsStructure())&&be.getBlockPos().distSqr(pos)<=18){validations.add(be);if(build)buildEvents.add(be);}}
    public void chunkChanged(ServerLevel level){for(var be:loaded.values())if(be.getLevel()==level&&(be.device().needsStructure()))validations.add(be);}
    public void placed(MachineBlockEntity be){
        register(be);if(be.device().has(DeviceType.Capability.STORAGE_NETWORK)){validations.add(be);buildEvents.add(be);}else discover(be);
    }
    public void discover(MachineBlockEntity be){
        if(be.device().has(DeviceType.Capability.STORAGE_NETWORK)||validBeacon(be)!=null)return;
        int range=Arrays.stream(IndustrialRules.current().beaconRadii).max().orElse(32);MachineBlockEntity best=null;double dist=Double.MAX_VALUE;
        for(var other:nearby((ServerLevel)be.getLevel(),be.getBlockPos(),range))if(other.device().has(DeviceType.Capability.STORAGE_NETWORK)&&other.structureValid){double d=be.getBlockPos().distSqr(other.getBlockPos());if(d<=other.radius()*(double)other.radius()&&(d<dist||d==dist&&best!=null&&other.identity.compareTo(best.identity)<0)){best=other;dist=d;}}
        if(best!=null){be.beacon=best.identity.toString();be.setChanged();}
    }
    public MachineBlockEntity validBeacon(MachineBlockEntity be){MachineBlockEntity core=loaded.get(be.beacon);return core!=null&&core.device().has(DeviceType.Capability.STORAGE_NETWORK)&&core.structureValid&&core.getLevel()==be.getLevel()&&core.getBlockPos().distSqr(be.getBlockPos())<=core.radius()*(double)core.radius()?core:null;}
    public void bind(MachineBlockEntity core,MachineBlockEntity device){if(!core.device().has(DeviceType.Capability.STORAGE_NETWORK)||!core.structureValid||device.device().has(DeviceType.Capability.STORAGE_NETWORK)||core.getLevel()!=device.getLevel()||core.getBlockPos().distSqr(device.getBlockPos())>core.radius()*(double)core.radius())throw new IllegalArgumentException("信标无效或设备超出覆盖范围");device.beacon=core.identity.toString();device.setChanged();}
    public void link(MachineBlockEntity a,MachineBlockEntity b){
        int range=Math.max(a.radius(),b.radius());if(a==b||!a.device().has(DeviceType.Capability.STORAGE_NETWORK)||!b.device().has(DeviceType.Capability.STORAGE_NETWORK)||!a.structureValid||!b.structureValid||a.getLevel()!=b.getLevel()||a.getBlockPos().distSqr(b.getBlockPos())>range*(double)range)throw new IllegalArgumentException("信标须有效、同维度且在较大半径内");
        Node left=nodes.get(a.identity.toString()),right=nodes.get(b.identity.toString());if(!left.links.remove(b.identity.toString())){left.links.add(b.identity.toString());right.links.add(a.identity.toString());}else right.links.remove(a.identity.toString());topologyDirty=true;reservations.clear();note(a,"权杖修改信标连接");note(b,"权杖修改信标连接");setDirty();
    }
    private void rebuild(){
        if(!topologyDirty)return;components.clear();for(String start:nodes.keySet())if(!components.containsKey(start)){
            Set<String> group=new LinkedHashSet<>();ArrayDeque<String> queue=new ArrayDeque<>();queue.add(start);while(!queue.isEmpty()){String id=queue.remove();Node n=nodes.get(id);if(n==null||!group.add(id))continue;queue.addAll(n.links);}String root=group.stream().min(String::compareTo).orElse(start);for(String id:group)components.put(id,root);
        }topologyDirty=false;
    }
    /** Loaded topology only: previewing a connection never loads a remote chunk. */
    public JsonObject connectionPreview(MachineBlockEntity selected){
        rebuild();JsonObject packet=new JsonObject();packet.addProperty("dimension",dimension(selected));packet.addProperty("expires",System.currentTimeMillis()+10000);JsonArray beams=new JsonArray();Set<String> unique=new HashSet<>();
        java.util.function.BiConsumer<BlockPos,BlockPos> add=(from,to)->{long a=from.asLong(),b=to.asLong();String key=Math.min(a,b)+"/"+Math.max(a,b);if(a!=b&&unique.add(key)){JsonObject line=new JsonObject();line.addProperty("from",a);line.addProperty("to",b);beams.add(line);}};
        if(selected.device().has(DeviceType.Capability.STORAGE_NETWORK)){
            Node node=nodes.get(selected.identity.toString());
            if(node!=null)for(String link:node.links){var other=loaded.get(link);if(other!=null&&other.getLevel()==selected.getLevel())add.accept(selected.getBlockPos(),other.getBlockPos());}
            for(var device:loaded.values())if(device.getLevel()==selected.getLevel()&&device.beacon.equals(selected.identity.toString())&&validBeacon(device)!=null)add.accept(selected.getBlockPos(),device.getBlockPos());
        }else {var core=validBeacon(selected);if(core!=null)add.accept(selected.getBlockPos(),core.getBlockPos());}
        if(selected.device().has(DeviceType.Capability.LOGISTICS)&&selected.targetDimension.equals(dimension(selected))&&selected.getLevel().hasChunkAt(BlockPos.of(selected.targetPosition)))add.accept(selected.getBlockPos(),BlockPos.of(selected.targetPosition));
        packet.add("beams",beams);return packet;
    }

    public static IndustrialRules readRules(MinecraftServer server)throws java.io.IOException {
        var resource=server.getResourceManager().getResource(IndustrialContent.id("rules/industrial.json"));
        if(resource.isPresent())try(var reader=resource.get().openAsReader()){return IndustrialRules.parse(JsonParser.parseReader(reader).toString());}
        return IndustrialRules.current();
    }
    public static void rulesPublished(MinecraftServer server){
        var world=get(server.overworld());world.validations.addAll(world.loaded.values().stream().filter(b->b.device().needsStructure()).toList());
    }

    public static void tick(MinecraftServer server){get(server.overworld()).runTick(server);}
    private void runTick(MinecraftServer server){
        int count=deferred.size();for(int i=0;i<count;i++)deferred.removeFirst().run();
        if(needsRecovery&&IndustrialPorts.ready()){IndustrialPorts.recover();needsRecovery=false;}
        generation.beginTick();
        for(var be:new ArrayList<>(validations)){if(be.isRemoved())continue;boolean was=be.structureValid;be.structureValid=IndustrialStructures.missing(be).isEmpty();if(be.structureValid&&!was&&buildEvents.contains(be)&&be.device().has(DeviceType.Capability.STORAGE_NETWORK))for(var device:nearby((ServerLevel)be.getLevel(),be.getBlockPos(),be.radius()))discover(device);if(was!=be.structureValid)note(be,be.structureValid?"结构修复：恢复原储能":"结构失效：该核心储能暂不可用");}validations.clear();buildEvents.clear();rebuild();
        Map<String,List<MachineBlockEntity>> cores=new LinkedHashMap<>(),devices=new LinkedHashMap<>();Map<String,Long> production=new HashMap<>();
        for(var be:new ArrayList<>(loaded.values()))if(!be.isRemoved()){
            be.tickTime=Fraction.of(128);be.workThisTick=0;be.consumed=0;boolean connected=validBeacon(be)!=null;
            if(be.device().has(DeviceType.Capability.STORAGE_NETWORK)&&be.structureValid)cores.computeIfAbsent(components.get(be.identity.toString()),k->new ArrayList<>()).add(be);
            if(be.device().generator()){long generated=generation.generate(be);be.generated=generated;if(connected)production.merge(components.get(be.beacon),generated,Math::addExact);else be.overflow=generated;}
            else if(!be.device().has(DeviceType.Capability.STORAGE_NETWORK)){if(connected)devices.computeIfAbsent(components.get(be.beacon),k->new ArrayList<>()).add(be);else if(be.device().processing()&&be.running&&(!be.device().needsStructure()||be.structureValid)){for(int round=0;round<128&&be.tickTime.positive();round++){IndustrialProcessing.prepare(be);if(be.batchRecipe.isEmpty())break;Fraction before=be.tickTime;long need=be.demand();be.advance(be.crystalSupply(need));if(before.equals(be.tickTime))break;}}}
            IndustrialProcessing.output(be);
        }
        for(var entry:cores.entrySet())settle(entry.getKey(),entry.getValue(),devices.getOrDefault(entry.getKey(),List.of()),production.getOrDefault(entry.getKey(),0L));
        for(var be:new ArrayList<>(loaded.values()))if(!be.isRemoved()){visual(be,be.device().has(DeviceType.Capability.STORAGE_NETWORK)?be.structureValid:validBeacon(be)!=null);IndustrialProcessing.output(be);}
    }
    private void visual(MachineBlockEntity be,boolean connected){BlockState state=be.getBlockState();boolean active=be.device().generator()?be.generated>0:be.consumed>0;if(state.getValue(MachineBlock.CONNECTED)!=connected||state.getValue(MachineBlock.ACTIVE)!=active)be.getLevel().setBlock(be.getBlockPos(),state.setValue(MachineBlock.CONNECTED,connected).setValue(MachineBlock.ACTIVE,active),2);}

    public void requestRecovery(){needsRecovery=true;}
    private void settle(String group,List<MachineBlockEntity> cores,List<MachineBlockEntity> devices,long generated){
        long stored=0,capacity=Math.multiplyExact(IndustrialRules.current().beaconCapacity,cores.size());for(var core:cores)stored=Math.addExact(stored,core.energy);long available=Math.addExact(stored,generated),used=0;
        Reservation reservation=reservations.get(group);if(reservation!=null&&(!loaded.containsKey(reservation.device)||validBeacon(loaded.get(reservation.device))==null||!Objects.equals(components.get(loaded.get(reservation.device).beacon),group)))reservation=null;
        long held=reservation==null?0:reservation.credit;String owner=reservation==null?"":reservation.device;if(held>stored){held=0;owner="";}
        List<IndustrialFunctions.FunctionJob> jobs=new ArrayList<>();for(var be:devices)if(be.device().functional())functions.prepare(be,jobs);
        for(int round=0;round<128;round++){
            List<MachineBlockEntity> processors=devices.stream().filter(b->b.device().processing()&&b.running&&(!b.device().needsStructure()||b.structureValid)&&b.tickTime.positive()).toList();for(var be:processors)IndustrialProcessing.prepare(be);
            long[] wants=new long[processors.size()+jobs.size()];for(int i=0;i<processors.size();i++)wants[i]=processors.get(i).demand();for(int i=0;i<jobs.size();i++)wants[processors.size()+i]=Math.max(0,jobs.get(i).demand()-(jobs.get(i).be.identity.toString().equals(owner)?held:0));
            var allocation=EnergyMath.allocate(Math.max(0,available-used-held),wants,cursors.getOrDefault(group,0));cursors.put(group,allocation.nextCursor());boolean progress=false;
            for(int i=0;i<processors.size();i++){var be=processors.get(i);if(be.batchRecipe.isEmpty())continue;Fraction before=be.work;String previous=be.batchRecipe;long grant=allocation.grants()[i];long extra=be.crystalSupply(Math.max(0,wants[i]-grant));be.advance(grant+extra);used+=grant;if(grant+extra>0||!be.work.equals(before)||!be.batchRecipe.equals(previous))progress=true;}
            for(int i=0;i<jobs.size();i++){IndustrialFunctions.FunctionJob job=jobs.get(i);String key=job.be.identity.toString();long credit=key.equals(owner)?held:0,grant=allocation.grants()[processors.size()+i];long spent=job.run(grant+credit);if(spent<0){if(key.equals(owner)){held=0;owner="";}job.done=true;progress=true;continue;}used+=spent;if(spent>0){progress=true;job.be.consumed+=spent;}
                long rest=grant+credit-spent;if(rest>0&&!job.done&&(owner.isEmpty()||owner.equals(key))&&rest<=capacity){held=rest;owner=key;progress|=grant>0;}else if(key.equals(owner)){held=0;owner="";}
            }
            jobs.removeIf(j->j.done);if(!progress)break;
        }
        if(held>0)reservations.put(group,new Reservation(owner,held));else reservations.remove(group);
        long balance=available-used,spill=Math.max(0,balance-capacity);balance-=spill;long difference=balance-stored;
        for(var core:cores){if(difference>0){long n=Math.min(difference,IndustrialRules.current().beaconCapacity-core.energy);core.energy+=n;difference-=n;}else if(difference<0){long n=Math.min(-difference,core.energy);core.energy-=n;difference+=n;}core.setChanged();}
        Totals t=totals.computeIfAbsent(group,k->new Totals());t.generated+=generated;t.consumed+=used;t.overflow+=spill;t.finish();setDirty();
    }

    private final IndustrialFunctions functions=new IndustrialFunctions();

    public JsonObject info(MachineBlockEntity be){return info(be,false);}
    public JsonObject info(MachineBlockEntity be,boolean includeLines){
        JsonObject j=new JsonObject();j.addProperty("name",be.getDisplayName().getString());j.addProperty("status",be.running?be.status:"已停止");j.addProperty("tier",be.tier());j.addProperty("kind",be.device().id);j.addProperty("running",be.running);j.addProperty("connected",validBeacon(be)!=null);j.addProperty("crystal",be.crystalRemaining);j.addProperty("energy",be.energy);j.addProperty("total_energy",be.batchEnergy);j.addProperty("paid",be.paid);j.addProperty("recipe",be.batchRecipe);j.addProperty("selected",be.selected);j.addProperty("progress",be.batchTicks==0?0:be.work.value()/((long)be.batchTicks*128/be.batchSpeed));j.addProperty("fuel",be.fuelRemaining);j.addProperty("fluid_in",be.fluids.getAmountAsInt(0));j.addProperty("fluid_out",be.fluids.getAmountAsInt(1));j.addProperty("target",be.targetDimension.isEmpty()?"未绑定":be.targetDimension+" "+BlockPos.of(be.targetPosition).toShortString());
        j.addProperty("batch_sequence",be.batchSequence);j.addProperty("server_tick",be.getLevel().getGameTime());
        double duration=be.batchTicks<=0?0:(double)be.batchTicks*128/be.batchSpeed;
        j.addProperty("progress_rate",duration==0||!be.running||!be.canFinish()||be.batchRecipe.isEmpty()?0:be.workThisTick/duration);
        j.addProperty("fluid_capacity",IndustrialRules.current().fluidCapacity);
        for(int tank=0;tank<2;tank++){var fluid=be.fluids.getResource(tank);String prefix=tank==0?"fluid_in":"fluid_out";j.addProperty(prefix+"_id",fluid.isEmpty()?"":BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString());j.addProperty(prefix+"_name",fluid.isEmpty()?"空":fluid.toStack(1).getHoverName().getString());}
        MachineBlockEntity core=be.device().has(DeviceType.Capability.STORAGE_NETWORK)?be:validBeacon(be);if(core!=null){String group=components.get(core.identity.toString());long balance=0,capacity=0;for(var other:loaded.values())if(other.device().has(DeviceType.Capability.STORAGE_NETWORK)&&other.structureValid&&Objects.equals(components.get(other.identity.toString()),group)){balance+=other.energy;capacity+=IndustrialRules.current().beaconCapacity;}j.addProperty("network_energy",balance);j.addProperty("network_capacity",capacity);Totals t=totals.get(group);if(t!=null){j.addProperty("generated",t.lastGenerated);j.addProperty("consumed",t.lastConsumed);j.addProperty("overflow",t.lastOverflow);}j.addProperty("member_change",memberReasons.getOrDefault(core.identity.toString(),"网络成员已恢复"));j.addProperty("radius",core.radius());}
        JsonArray list=new JsonArray();for(var r:IndustrialProcessing.recipes((ServerLevel)be.getLevel(),be.device())){JsonObject entry=new JsonObject();entry.addProperty("id",r.id().identifier().toString());entry.addProperty("tier",r.value().minimumTier());entry.addProperty("ticks",r.value().ticks());entry.addProperty("energy",r.value().energy());entry.addProperty("ingredients",r.value().ingredients().stream().map(n->n.count()+" × "+n.ingredient().items().limit(3).map(h->new ItemStack(h.value()).getHoverName().getString()).reduce((a,b)->a+" / "+b).orElse("缺少兼容材料")).reduce((a,b)->a+"；"+b).orElse("无需物品原料")+r.value().fluidInput().map(f->"；流体 "+f.amount()+" mB").orElse(""));entry.addProperty("name",r.value().result().isEmpty()?(r.value().fluidResult().isEmpty()?"随机通货":r.value().fluidResult().getHoverName().getString()+" "+r.value().fluidResult().getAmount()+" mB"):r.value().result().getHoverName().getString()+" ×"+r.value().result().getCount());list.add(entry);}j.add("recipes",list);j.addProperty("show_lines",includeLines);JsonArray links=new JsonArray();if(includeLines&&be.device().has(DeviceType.Capability.STORAGE_NETWORK)&&nodes.containsKey(be.identity.toString())){for(String id:nodes.get(be.identity.toString()).links){Node n=nodes.get(id);if(n!=null)links.add(n.position);}for(var device:loaded.values())if(device.beacon.equals(be.identity.toString())&&validBeacon(device)!=null)links.add(device.getBlockPos().asLong());}else if(includeLines&&validBeacon(be)!=null)links.add(validBeacon(be).getBlockPos().asLong());if(includeLines)j.add("links",links);
        return IndustrialDeviceInfo.enrich(be,j);
    }
}
