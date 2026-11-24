package cn.yumis.fight.modules.industry.application;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.industry.platform.*;
import cn.yumis.fight.modules.inventory.api.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.*;
import java.util.function.*;

/** Inventory and the complete construction intent share the existing WAL commit. */
public final class ConveyorConstructionService {
    private static final String PREFIX="conveyorBuild/";
    private static final String REFUND_PREFIX="conveyorRefund/";
    public static final long BLOCKED_TIMEOUT_MILLIS=30_000L;
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private final Journal journal;
    private final InventoryOperations inventory;
    private final MinecraftServer server;
    private final Function<ServerPlayer,String> session;
    private final BiConsumer<ServerPlayer,JsonObject> response;
    private final BiConsumer<ServerPlayer,String> message;
    private final Set<String> pending=new LinkedHashSet<>();
    private final Map<String,Set<BlockPos>> reservations=new HashMap<>();
    private final Set<String> pendingIdentities=new HashSet<>();
    private final Map<String,Long> blockedSince=new HashMap<>();
    private long retry;
    public ConveyorConstructionService(Journal journal,InventoryOperations inventory,MinecraftServer server,Function<ServerPlayer,String> session,BiConsumer<ServerPlayer,JsonObject> response,BiConsumer<ServerPlayer,String> message){
        this.journal=journal;this.inventory=inventory;this.server=server;this.session=session;this.response=response;this.message=message;
        recoverOrphaned();
    }
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("industry","conveyorBuild",this::request);}
    private void reply(ServerPlayer player,JsonObject request,boolean success,boolean waiting,String message){
        JsonObject reply=new JsonObject();reply.add("operation",request.get("operation"));reply.add("session",request.get("session"));
        reply.addProperty("success",success);reply.addProperty("pending",waiting);reply.addProperty("message",message);response.accept(player,reply);
    }
    private void request(ServerPlayer player,JsonObject request){
        String key=null;
        try{
            String operation=UUID.fromString(request.get("operation").getAsString()).toString();key=PREFIX+player.getUUID()+"/"+operation;
            if(!session.apply(player).equals(request.get("session").getAsString()))throw new IllegalArgumentException("会话已失效，请重新选点");
            JsonElement saved=journal.get(key);
            if(saved instanceof JsonObject record){
                if(!record.get("intent").equals(request))throw new IllegalArgumentException("建造操作号与原请求不一致");
                if(!record.get("done").getAsBoolean())project(key,record);
                reply(player,request,true,false,"传送带已铺设，共"+record.getAsJsonArray("cells").size()+"段");return;
            }
            if(!player.isAlive()||player.isSpectator()||!player.getAbilities().mayBuild||player.containerMenu!=player.inventoryMenu||inventory.locked(player.getUUID()))throw new IllegalArgumentException("请关闭容器后再铺设传送带");
            ServerLevel level=player.level();
            if(!level.dimension().identifier().toString().equals(request.get("dimension").getAsString()))throw new IllegalArgumentException("起终点不在当前维度");
            InteractionHand hand=InteractionHand.valueOf(request.get("hand").getAsString());ItemStack held=player.getItemInHand(hand);
            if(!(held.getItem() instanceof BlockItem item)||!(item.getBlock() instanceof ConveyorBlock block)||!BuiltInRegistries.BLOCK.getKey(block).toString().equals(request.get("block").getAsString()))throw new IllegalArgumentException("请手持要铺设的传送带");
            JsonArray route=request.getAsJsonArray("route");
            if(route.size()<2||route.size()>ConveyorRoute.MAX_LENGTH)throw new IllegalArgumentException("单次路线最多256段");
            List<ConveyorRoute.Step> steps=new ArrayList<>();
            for(JsonElement value:route){JsonObject cell=value.getAsJsonObject();Direction facing=Direction.byName(cell.get("facing").getAsString());if(facing==null)throw new IllegalArgumentException("无效的传送带方向");steps.add(new ConveyorRoute.Step(new BlockPos(cell.get("x").getAsInt(),cell.get("y").getAsInt(),cell.get("z").getAsInt()),facing));}
            if(!player.isWithinBlockInteractionRange(steps.getLast().pos(),1))throw new IllegalArgumentException("终点距离过远，请走近后重新选点");
            ConveyorRoute.Plan plan=ConveyorRoute.plan(level,steps,block);
            for(var entry:plan.states().entrySet()){
                BlockPos pos=entry.getKey();
                if(reserved(level,pos))throw new IllegalArgumentException("该位置的传送带建造正在恢复，请稍后重试");
                if(!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,Direction.UP,held))throw new IllegalArgumentException("没有该路线的建造权限");
                if(plan.fresh().contains(pos)&&!level.isUnobstructed(entry.getValue(),pos,CollisionContext.empty()))throw new IllegalArgumentException("路线被实体占用，请稍后重试");
            }
            int count=plan.fresh().size();
            if(!player.isCreative()&&available(player,item)<count)throw new IllegalArgumentException("传送带材料不足，需要"+count+"个");
            JsonObject record=new JsonObject();record.add("intent",request.deepCopy());record.addProperty("dimension",level.dimension().identifier().toString());record.addProperty("done",false);record.addProperty("creative",player.isCreative());
            JsonArray cells=new JsonArray();
            for(BlockPos pos:plan.fresh()){
                JsonObject cell=new JsonObject();cell.addProperty("pos",pos.asLong());cell.addProperty("identity",UUID.randomUUID().toString());
                cell.add("before",BlockState.CODEC.encodeStart(JsonOps.INSTANCE,level.getBlockState(pos)).getOrThrow());
                cell.add("after",BlockState.CODEC.encodeStart(JsonOps.INSTANCE,plan.states().get(pos)).getOrThrow());cells.add(cell);
            }
            record.add("cells",cells);
            inventory.transact(player,"conveyor-build/"+operation,request,()->{if(!player.isCreative())consume(player,item,count);},Map.of(key,record));
            // Keep a committed placement available for retries in this running session.
            if(!(journal.get(key) instanceof JsonObject committed))throw new IllegalStateException("建造未能提交，请重试");
            pending.add(key);reserve();project(key,committed);reply(player,request,true,false,"传送带已铺设，共"+count+"段");
        }catch(Exception failure){
            JsonElement saved=key==null?JsonNull.INSTANCE:journal.get(key);
            boolean committed=saved instanceof JsonObject record&&record.get("intent").equals(request)&&!record.get("done").getAsBoolean();if(committed){pending.add(key);reserve();}
            if(committed)LOG.warn("传送带建造已提交，等待世界投影恢复：{}",key,failure);
            reply(player,request,false,committed,committed?"建造已保存，正在恢复放置":failure.getMessage()==null?"传送带建造失败，请重试":failure.getMessage());
        }
    }
    public static int available(ServerPlayer player,Item item){
        int count=0;
        for(int i=0;i<36;i++)if(player.getInventory().getItem(i).is(item))count+=player.getInventory().getItem(i).getCount();
        if(player.getOffhandItem().is(item))count+=player.getOffhandItem().getCount();
        var extra=MenuHooks.extra(player);
        for(int i=0;i<extra.bagLimit();i++)if(extra.bag.getItem(i).is(item))count+=extra.bag.getItem(i).getCount();
        return count;
    }
    public static void consume(ServerPlayer player,Item item,int count){
        if(available(player,item)<count)throw new IllegalArgumentException("传送带材料不足，需要"+count+"个");
        int left=count;
        int mainHave=0;
        for(int i=0;i<36;i++)if(player.getInventory().getItem(i).is(item))mainHave+=player.getInventory().getItem(i).getCount();
        int mainTake=Math.min(left,mainHave);
        if(mainTake>0){InventoryItems.consume(player,item,mainTake);left-=mainTake;}
        if(left>0&&player.getOffhandItem().is(item)){
            int offTake=Math.min(left,player.getOffhandItem().getCount());
            player.getOffhandItem().shrink(offTake);left-=offTake;
        }
        if(left>0){
            var extra=MenuHooks.extra(player);
            for(int i=0;i<extra.bagLimit()&&left>0;i++){
                ItemStack stack=extra.bag.getItem(i);
                if(!stack.is(item))continue;
                int take=Math.min(left,stack.getCount());
                stack.shrink(take);
                if(stack.isEmpty())extra.bag.setItem(i,ItemStack.EMPTY);
                left-=take;
            }
            extra.bag.setChanged();
        }
        if(left>0)throw new IllegalArgumentException("传送带材料不足，需要"+count+"个");
    }
    private ServerLevel level(JsonObject record){
        ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(record.get("dimension").getAsString())));
        if(level==null)throw new IllegalStateException("建造维度不可用");return level;
    }
    private void project(String key,JsonObject record)throws Exception{
        if(record.get("done").getAsBoolean()){pending.remove(key);reserve();return;}
        ServerLevel level=level(record);JsonArray cells=record.getAsJsonArray("cells");
        // Pending placement only loads chunks already named by its committed plan.
        for(JsonElement value:cells){BlockPos pos=BlockPos.of(value.getAsJsonObject().get("pos").getAsLong());level.getChunk(pos.getX()>>4,pos.getZ()>>4);}
        for(JsonElement value:cells){
            JsonObject cell=value.getAsJsonObject();BlockPos pos=BlockPos.of(cell.get("pos").getAsLong());BlockState old=BlockState.CODEC.parse(JsonOps.INSTANCE,cell.get("before")).getOrThrow();
            if(owned(level,pos,cell))continue;
            if(level.getBlockState(pos)!=old||level.getBlockEntity(pos)!=null)throw new IllegalStateException("建造恢复位置已有其他方块，已停止覆盖："+pos);
        }
        for(JsonElement value:cells){
            JsonObject cell=value.getAsJsonObject();BlockPos pos=BlockPos.of(cell.get("pos").getAsLong());if(owned(level,pos,cell))continue;
            BlockState state=BlockState.CODEC.parse(JsonOps.INSTANCE,cell.get("after")).getOrThrow();
            if(!level.setBlock(pos,state,Block.UPDATE_CLIENTS)||!(level.getBlockEntity(pos) instanceof ConveyorBlockEntity belt))throw new IllegalStateException("传送带放置尚未完成："+pos);
            belt.constructionIdentity(UUID.fromString(cell.get("identity").getAsString()));
        }
        for(JsonElement value:cells)ConveyorConnections.refresh(level,BlockPos.of(value.getAsJsonObject().get("pos").getAsLong()));
        record.addProperty("done",true);journal.put(key,record);pending.remove(key);blockedSince.remove(key);reserve();
    }
    private void recoverOrphaned(){
        for(String key:journal.keys(PREFIX)){
            JsonElement raw=journal.get(key);
            if(!(raw instanceof JsonObject record)||record.get("done").getAsBoolean())continue;
            try{
                abortAndRefund(key,record,true);
            }catch(Exception failure){
                LOG.warn("重启恢复遗留传送带建造单失败：{}",key,failure);
            }
        }
    }
    private int unplacedCount(JsonObject record){
        JsonArray cells=record.getAsJsonArray("cells");
        ServerLevel level=server==null?null:server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(record.get("dimension").getAsString())));
        if(level==null)return cells.size();
        int remaining=0;
        for(JsonElement value:cells){
            JsonObject cell=value.getAsJsonObject();
            BlockPos pos=BlockPos.of(cell.get("pos").getAsLong());
            if(!owned(level,pos,cell))remaining++;
        }
        return remaining;
    }
    private void abortAndRefund(String key,JsonObject record,boolean startup)throws Exception{
        int remaining=unplacedCount(record);
        boolean creative=record.has("creative")&&record.get("creative").getAsBoolean();
        String[] parts=key.substring(PREFIX.length()).split("/",2);
        UUID builder=parts.length>=1?UUID.fromString(parts[0]):null;
        String operation=parts.length>=2?parts[1]:UUID.randomUUID().toString();
        String blockId=record.getAsJsonObject("intent").get("block").getAsString();
        record.addProperty("done",true);
        var writes=new LinkedHashMap<String,JsonElement>();
        writes.put(key,record);
        if(!creative&&remaining>0&&builder!=null){
            JsonObject refund=new JsonObject();
            refund.addProperty("block",blockId);
            refund.addProperty("count",remaining);
            refund.addProperty("startup",startup);
            writes.put(REFUND_PREFIX+builder+"/"+operation,refund);
        }
        journal.transaction(writes);
        pending.remove(key);
        blockedSince.remove(key);
        reserve();
        if(!startup&&builder!=null){
            ServerPlayer online=server.getPlayerList().getPlayer(builder);
            if(online!=null&&!online.hasDisconnected())deliverRefunds(online);
        }
    }
    public void deliverRefunds(ServerPlayer player){
        String prefix=REFUND_PREFIX+player.getUUID()+"/";
        for(String key:journal.keys(prefix)){
            JsonElement raw=journal.get(key);
            if(!(raw instanceof JsonObject refund))continue;
            String blockId=refund.get("block").getAsString();
            int count=refund.get("count").getAsInt();
            if(count<=0){try{journal.put(key,JsonNull.INSTANCE);}catch(Exception ignored){}continue;}
            Block block=BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId));
            Item item=block.asItem();
            if(item==Items.AIR){try{journal.put(key,JsonNull.INSTANCE);}catch(Exception ignored){}continue;}
            String op=key.substring(prefix.length());
            var edits=new LinkedHashMap<String,JsonElement>();
            edits.put(key,JsonNull.INSTANCE);
            var intent=new JsonObject();intent.addProperty("conveyorRefund",op);intent.addProperty("count",count);
            try{
                boolean committed=inventory.transact(player,"conveyor-refund/"+op,intent,()->{
                    int left=count;
                    while(left>0){
                        int chunk=Math.min(left,item.getDefaultMaxStackSize());
                        ItemStack stack=new ItemStack(item,chunk);
                        inventory.deliverOrStash(player,stack,edits);
                        left-=chunk;
                    }
                },edits);
                if(committed)message.accept(player,"部分路线受阻，已退还剩余 "+count+" 个传送带");
            }catch(Exception failure){
                LOG.warn("退还传送带材料失败：{}",key,failure);
            }
        }
    }
    private static boolean owned(ServerLevel level,BlockPos pos,JsonObject cell){
        if(!(level.getBlockEntity(pos) instanceof ConveyorBlockEntity belt)||!belt.key().equals(cell.get("identity").getAsString()))return false;
        BlockState expected=BlockState.CODEC.parse(JsonOps.INSTANCE,cell.get("after")).getOrThrow(),actual=level.getBlockState(pos);
        return actual.getBlock()==expected.getBlock()&&actual.getValue(ConveyorBlock.FACING)==expected.getValue(ConveyorBlock.FACING);
    }
    private void reserve(){
        reservations.clear();
        pendingIdentities.clear();
        for(String key:pending){
            JsonElement raw=journal.get(key);
            if(!(raw instanceof JsonObject record))continue;
            Set<BlockPos> positions=reservations.computeIfAbsent(record.get("dimension").getAsString(),ignored->new HashSet<>());
            for(JsonElement value:record.getAsJsonObject("intent").getAsJsonArray("route")){
                JsonObject cell=value.getAsJsonObject();BlockPos pos=new BlockPos(cell.get("x").getAsInt(),cell.get("y").getAsInt(),cell.get("z").getAsInt());
                positions.add(pos);positions.add(pos.below());positions.add(pos.above());
            }
            if(record.has("cells"))for(JsonElement value:record.getAsJsonArray("cells"))pendingIdentities.add(value.getAsJsonObject().get("identity").getAsString());
        }
    }
    private boolean reserved(LevelAccessor level,BlockPos pos){
        return level instanceof ServerLevel serverLevel&&serverLevel.getServer()==server
            &&reservations.getOrDefault(serverLevel.dimension().identifier().toString(),Set.of()).contains(pos);
    }
    /** Only protect already-placed conveyor belts of an unfinished build; allow players to clear obstructing blocks. */
    public boolean blocksBreak(LevelAccessor level,BlockPos pos){
        if(!reserved(level,pos))return false;
        return level.getBlockEntity(pos) instanceof ConveyorBlockEntity belt&&pendingIdentities.contains(belt.key());
    }
    /** An unfinished committed plan cannot be dismantled and then replayed as free replacement blocks. */
    public static void registerGuards(Supplier<ConveyorConstructionService> active){
        var bus=net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        bus.addListener((net.neoforged.neoforge.event.level.block.BreakBlockEvent event)->{
            var service=active.get();if(service!=null&&service.blocksBreak(event.getLevel(),event.getPos())){event.setCanceled(true);event.setNotifyClient(true);event.getPlayer().sendOverlayMessage(net.minecraft.network.chat.Component.literal("这段传送带正在完成建造，请稍后重试"));}
        });
        bus.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event)->{
            var service=active.get();if(service!=null&&service.blocksBreak(event.getLevel(),event.getPos())){event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);}
        });
        bus.addListener((net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event)->{
            var service=active.get();if(service==null)return;
            boolean blocked=event instanceof net.neoforged.neoforge.event.level.BlockEvent.EntityMultiPlaceEvent multi
                ?multi.getReplacedBlockSnapshots().stream().anyMatch(snapshot->service.reserved(event.getLevel(),snapshot.getPos())):service.reserved(event.getLevel(),event.getPos());
            if(blocked)event.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.event.level.BlockEvent.FluidPlaceBlockEvent event)->{var service=active.get();if(service!=null&&service.reserved(event.getLevel(),event.getPos()))event.setCanceled(true);});
        bus.addListener((net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event)->{var service=active.get();if(service!=null)event.getAffectedBlocks().removeIf(pos->service.reserved(event.getLevel(),pos));});
        bus.addListener((net.neoforged.neoforge.event.level.PistonEvent.Pre event)->{
            var service=active.get();if(service==null||service.reservations.isEmpty())return;
            var resolver=event.getStructureHelper();if(resolver==null||!resolver.resolve())return;
            Direction movement=event.getPistonMoveType().isExtend?event.getDirection():event.getDirection().getOpposite();
            if(service.reserved(event.getLevel(),event.getPos().relative(event.getDirection()))||resolver.getToPush().stream().anyMatch(pos->service.reserved(event.getLevel(),pos)||service.reserved(event.getLevel(),pos.relative(movement)))||resolver.getToDestroy().stream().anyMatch(pos->service.reserved(event.getLevel(),pos)))event.setCanceled(true);
        });
    }
    private void retryPending(){
        long now=System.currentTimeMillis();
        for(String key:List.copyOf(pending)){
            JsonElement raw=journal.get(key);
            if(!(raw instanceof JsonObject record)){pending.remove(key);blockedSince.remove(key);reserve();continue;}
            try{
                project(key,record);
            }catch(Exception failure){
                long since=blockedSince.computeIfAbsent(key,ignored->now);
                if(now-since>=BLOCKED_TIMEOUT_MILLIS){
                    try{abortAndRefund(key,record,false);}
                    catch(Exception abortFailure){LOG.warn("传送带建造超时退料失败：{}",key,abortFailure);}
                }else{
                    LOG.warn("传送带建造恢复未完成：{}",key,failure);
                }
            }
        }
    }
    public void tick(){if(!pending.isEmpty()&&server.getTickCount()>=retry){retry=server.getTickCount()+20;retryPending();}}
}
