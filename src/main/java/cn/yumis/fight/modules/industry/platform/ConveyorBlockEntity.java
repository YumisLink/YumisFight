package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.economy.api.WorldItemTransfers;
import cn.yumis.fight.modules.industry.api.IndustrialPorts;
import cn.yumis.fight.modules.inventory.api.*;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Two spatial batches, not two freely addressable inventory slots. */
public final class ConveyorBlockEntity extends BlockEntity implements PersistentContainer,WorldlyContainer {
    private static final double STEP=.1,GAP=.5,EPS=1e-7;
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private record Saved(String id,ItemStack stack,Direction entry,double progress,boolean down,boolean moving) {
        static final Codec<Saved> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("id").forGetter(Saved::id),ItemStack.CODEC.fieldOf("stack").forGetter(Saved::stack),Direction.CODEC.fieldOf("entry").forGetter(Saved::entry),Codec.DOUBLE.fieldOf("progress").forGetter(Saved::progress),Codec.BOOL.fieldOf("down").forGetter(Saved::down),Codec.BOOL.fieldOf("moving").forGetter(Saved::moving)).apply(i,Saved::new));
    }
    private static final class Batch {
        final UUID id;ItemStack stack;Direction entry;double progress;boolean down,moving=true;long advancedAt=Long.MIN_VALUE;
        Batch(UUID id,ItemStack stack,Direction entry,double progress){this.id=id;this.stack=stack.copy();this.entry=entry;this.progress=progress;}
        Batch copy(){var b=new Batch(id,stack,entry,progress);b.down=down;b.moving=moving;b.advancedAt=advancedAt;return b;}
        Saved save(){return new Saved(id.toString(),stack.copy(),entry,progress,down,moving);}
    }
    public record Visual(UUID id,ItemStack stack,Direction entry,double progress){}
    private UUID identity=UUID.randomUUID();
    private Direction direction;
    private final List<Batch> batches=new ArrayList<>(2);
    private boolean released,retrying,contentDirty=true,sideCursor;
    private int sourceCursor;
    private long lastTick=Long.MIN_VALUE,displayTick,lastSync=Long.MIN_VALUE,retryAt;
    private final QueueJournal queue=new QueueJournal();
    private final class QueueJournal extends SnapshotJournal<List<Batch>> {
        void touch(TransactionContext transaction){updateSnapshots(transaction);}
        @Override protected List<Batch> createSnapshot(){return batches.stream().map(Batch::copy).toList();}
        @Override protected void revertToSnapshot(List<Batch> snapshot){batches.clear();snapshot.forEach(b->batches.add(b.copy()));}
        @Override protected void onRootCommit(List<Batch> previous){dirty();}
    }
    public ConveyorBlockEntity(BlockPos pos,BlockState state){super(IndustrialContent.CONVEYOR_BE.get(),pos,state);direction=state.getValue(ConveyorBlock.FACING);}
    public Direction direction(){return direction;}
    public int speed(){return ((ConveyorBlock)getBlockState().getBlock()).speed;}
    public double step(){return STEP*speed();}
    public BlockPos downstream(){return ConveyorConnections.downstream(level,worldPosition,getBlockState());}
    public boolean hopper(){return getBlockState().getBlock() instanceof ConveyorBlock block&&block.hopper;}
    private static int limit(ItemStack stack){return Math.min(16,stack.getMaxStackSize());}
    private static boolean same(ItemStack a,ItemStack b){return ItemStack.isSameItemSameComponents(a,b);}
    private void dirty(){super.setChanged();contentDirty=true;}
    @Override public void setChanged(){dirty();}
    @Override public String key(){return identity.toString();}
    /** Identifies an already placed section when retrying a partly completed construction. */
    public void constructionIdentity(UUID id){if(!batches.isEmpty()||released)throw new IllegalStateException("传送带已经投入使用");identity=id;dirty();}
    @Override public void onLoad(){super.onLoad();setBlockState(level.getBlockState(worldPosition));}
    private boolean ready(){
        if(released||isRemoved()||!IndustrialPorts.ready()||!WorldItemTransfers.ready()||level.getGameTime()<retryAt)return false;
        retrying=false;
        return true;
    }
    private void failed(RuntimeException failure){retrying=true;retryAt=level.getGameTime()+20;LOG.warn("传送带暂停，稍后重试物品事务：{} {}",worldPosition,key(),failure);}
    public static void tick(Level level,BlockPos pos,BlockState state,ConveyorBlockEntity root){
        if(!(level instanceof ServerLevel)||root.lastTick==level.getGameTime())return;
        long now=level.getGameTime();var belts=new ArrayList<ConveyorBlockEntity>();var work=new ArrayDeque<ConveyorBlockEntity>();work.add(root);
        // One local, loaded component is advanced in phases, independent of native BE tick order.
        while(!work.isEmpty()){
            var belt=work.removeFirst();if(belt.lastTick==now)continue;belt.lastTick=now;
            try{
                belt.setBlockState(level.getBlockState(belt.worldPosition));
                if(!belt.ready())continue;belts.add(belt);
                for(Direction side:Direction.Plane.HORIZONTAL)for(int dy=-1;dy<=1;dy++){var adjacent=belt.worldPosition.relative(side).offset(0,dy,0);if(ConveyorConnections.loaded(level,adjacent)&&level.getBlockEntity(adjacent) instanceof ConveyorBlockEntity next&&next.lastTick!=now)work.add(next);}
            }catch(RuntimeException failure){belt.failed(failure);}
        }
        int phases=belts.stream().mapToInt(ConveyorBlockEntity::speed).max().orElse(1);
        for(int phase=0;phase<phases;phase++)advanceComponent(level,belts,now*3+phase,phases);
        for(var belt:belts)try{belt.pickup();belt.sync(now);}catch(RuntimeException failure){belt.failed(failure);}
    }
    private static void advanceComponent(Level level,List<ConveyorBlockEntity> belts,long now,int phases){
        Map<Batch,Double> next=new IdentityHashMap<>();
        for(var belt:belts)for(var batch:belt.batches)next.put(batch,Math.min(1,batch.progress+(batch.advancedAt==now?0:belt.step()/phases)));
        var constraints=new ArrayDeque<>(belts);Set<ConveyorBlockEntity> queued=new HashSet<>(belts);
        while(!constraints.isEmpty()){
            var belt=constraints.removeFirst();queued.remove(belt);boolean changed=false;
            for(int i=0;i<belt.batches.size();i++){
                Batch batch=belt.batches.get(i);double ceiling=i==0?1:next.get(belt.batches.get(i-1))-GAP;
                if(i==0){var front=belt.downstream();
                    if(!level.hasChunkAt(front))ceiling=Math.min(ceiling,1-GAP);
                    else if(level.getBlockEntity(front) instanceof ConveyorBlockEntity target&&!target.batches.isEmpty()){var tail=target.batches.getLast();ceiling=Math.min(ceiling,1+next.getOrDefault(tail,tail.progress)-GAP);}
                }
                double value=Math.max(batch.progress,Math.min(next.get(batch),ceiling));
                if(value<next.get(batch)-EPS){next.put(batch,value);changed=true;}
            }
            if(changed)for(Direction side:belt.inputs()){var upstream=belt.upstream(side);if(upstream!=null&&!upstream.batches.isEmpty()&&next.containsKey(upstream.batches.getFirst())&&queued.add(upstream))constraints.add(upstream);}
        }
        for(var belt:belts)for(var batch:belt.batches){double value=next.get(batch);boolean moving=value>batch.progress+EPS;if(batch.moving!=moving)belt.contentDirty=true;batch.moving=moving;batch.progress=value;batch.advancedAt=now;}
        for(var belt:belts)try{belt.advance(now);}catch(RuntimeException failure){belt.failed(failure);}
        var intake=new ArrayDeque<>(belts);
        while(!intake.isEmpty()){var belt=intake.removeFirst();try{var source=belt.intake(now);if(source!=null)intake.add(source);}catch(RuntimeException failure){belt.failed(failure);}}
    }
    private Direction[] inputs(){return new Direction[]{direction.getOpposite(),sideCursor?direction.getClockWise():direction.getCounterClockWise(),sideCursor?direction.getCounterClockWise():direction.getClockWise()};}
    private ConveyorBlockEntity upstream(Direction side){
        BlockPos position=ConveyorConnections.upstream(level,worldPosition,getBlockState(),side);if(position==null)return null;
        return level.getBlockEntity(position) instanceof ConveyorBlockEntity belt&&belt.direction==side.getOpposite()?belt:null;
    }
    private void advance(long now){
        mergeContact();
        if(hopper())for(Batch batch:new ArrayList<>(batches))if(!batch.down&&batch.progress>=.5-EPS){
            batch.down=true;dirty();BlockPos below=worldPosition.below();
            if(level.hasChunkAt(below))transfer(batch,level.getCapability(Capabilities.Item.BLOCK,below,Direction.UP));
        }
        if(!batches.isEmpty()&&batches.getFirst().progress>=1-EPS)output();
        super.setChanged();
    }
    private void mergeContact(){
        if(batches.size()!=2)return;Batch front=batches.get(0),back=batches.get(1);
        if(front.progress-back.progress>GAP+EPS||!same(front.stack,back.stack))return;
        int moved=Math.min(limit(front.stack)-front.stack.getCount(),back.stack.getCount());
        if(moved>0){front.stack.grow(moved);back.stack.shrink(moved);if(back.stack.isEmpty())batches.remove(1);dirty();}
    }
    private int transfer(Batch batch,ResourceHandler<ItemResource> target){
        if(target==null)return 0;int moved;
        try(var transaction=Transaction.openRoot()){
            moved=target.insert(ItemResource.of(batch.stack),batch.stack.getCount(),transaction);
            if(moved>0){queue.touch(transaction);batch.stack.shrink(moved);if(batch.stack.isEmpty())batches.remove(batch);}
            transaction.commit();
        }
        return moved;
    }
    private void output(){
        BlockPos targetPos=downstream();if(!level.hasChunkAt(targetPos))return;
        if(level.getBlockEntity(targetPos) instanceof ConveyorBlockEntity)return;
        var target=level.getCapability(Capabilities.Item.BLOCK,targetPos,direction.getOpposite());Batch batch=batches.getFirst();
        if(target!=null){transfer(batch,target);return;}
        if(level.getBlockEntity(targetPos) instanceof Container||!level.getBlockState(targetPos).getCollisionShape(level,targetPos).isEmpty())return;
        Vec3 position=new Vec3(worldPosition.getX()+.5+direction.getStepX()*.72,worldPosition.getY()+ConveyorConnections.edge(getBlockState(),direction)+ConveyorGeometry.HEIGHT+.15,worldPosition.getZ()+.5+direction.getStepZ()*.72);
        Vec3 velocity=new Vec3(direction.getStepX()*.1,.025,direction.getStepZ()*.1);
        WorldItemTransfers.spill(this,"belt-output/"+batch.id,List.of(new WorldItemTransfers.Emission(batch.stack,position,velocity)),()->{batches.remove(batch);dirty();});
    }
    private ConveyorBlockEntity intake(long now){
        if(released||retrying)return null;
        for(Direction side:inputs()){
            ConveyorBlockEntity source=upstream(side);
            if(source!=null){
                if(source.retrying||source.released||source.batches.isEmpty())continue;
                Batch offered=source.batches.getFirst();double contact=1;
                if(!batches.isEmpty()){Batch tail=batches.getLast();if(same(tail.stack,offered.stack)&&tail.stack.getCount()<limit(tail.stack)&&tail.progress<=GAP+EPS)contact=1+tail.progress-GAP;}
                if(offered.progress+EPS<contact)continue;
                int amount;try(var transaction=Transaction.openRoot()){
                    queue.touch(transaction);source.queue.touch(transaction);amount=insert(offered.stack,side,0,offered.id,now);
                    if(amount>0){offered.stack.shrink(amount);if(offered.stack.isEmpty())source.batches.remove(offered);transaction.commit();}
                }
                if(amount>0){if(side!=direction.getOpposite())sideCursor=!sideCursor;return source;}
            }else if(side==direction.getOpposite()){
                BlockPos from=worldPosition.relative(side).above(ConveyorConnections.edge(getBlockState(),side));if(!level.hasChunkAt(from)||level.getBlockEntity(from) instanceof ConveyorBlockEntity)continue;
                var sourcePort=level.getCapability(Capabilities.Item.BLOCK,from,side.getOpposite());if(sourcePort==null)continue;
                long moved;try(var transaction=Transaction.openRoot()){moved=CrystalTransfers.move(sourcePort,automation(side),16,sourceCursor,transaction);if(moved>0)transaction.commit();}
                if(moved>0){sourceCursor=(sourceCursor+1)%Math.max(1,sourcePort.size());dirty();break;}
            }
        }
        return null;
    }
    private int mergeIndex(ItemStack stack,double progress){
        for(int i=0;i<batches.size();i++){var batch=batches.get(i);if(Math.abs(batch.progress-progress)<=GAP+EPS&&same(stack,batch.stack)&&batch.stack.getCount()<limit(stack))return i;}
        return -1;
    }
    private boolean space(double progress){return batches.size()<2&&batches.stream().allMatch(b->Math.abs(b.progress-progress)>=GAP-EPS);}
    private boolean canInsert(ItemStack stack,double progress){return !released&&!retrying&&!stack.isEmpty()&&(mergeIndex(stack,progress)>=0||space(progress));}
    private int insert(ItemStack stack,Direction entry,double progress,UUID batchId,long now){
        if(!canInsert(stack,progress))return 0;int merge=mergeIndex(stack,progress);
        if(merge>=0){Batch batch=batches.get(merge);int amount=Math.min(stack.getCount(),limit(stack)-batch.stack.getCount());batch.stack.grow(amount);dirty();return amount;}
        int amount=Math.min(stack.getCount(),limit(stack));Batch batch=new Batch(batchId,stack.copyWithCount(amount),entry,progress);batch.advancedAt=now;batch.down=progress>.5+EPS;
        batches.add(batch);batches.sort(Comparator.comparingDouble((Batch b)->b.progress).reversed());dirty();return amount;
    }
    private void pickup(){
        if(!WorldItemTransfers.ready()||released||retrying)return;
        var area=new AABB(worldPosition.getX(),worldPosition.getY()+ConveyorGeometry.HEIGHT-.04,worldPosition.getZ(),worldPosition.getX()+1,worldPosition.getY()+ConveyorGeometry.HEIGHT+.4+(getBlockState().getValue(ConveyorBlock.SLOPE)==ConveyorBlock.Slope.FLAT?0:1),worldPosition.getZ()+1);
        for(var entity:level.getEntitiesOfClass(ItemEntity.class,area,ItemEntity::isAlive)){
            double surface=worldPosition.getY()+ConveyorGeometry.HEIGHT+ConveyorConnections.lift(getBlockState(),entity.getX()-worldPosition.getX(),entity.getZ()-worldPosition.getZ());
            if(entity.getY()<surface-.08||entity.getY()>surface+.4)continue;
            double progress=Math.clamp((entity.getX()-worldPosition.getX()-.5)*direction.getStepX()+(entity.getZ()-worldPosition.getZ()-.5)*direction.getStepZ()+.5,0,1);
            if(!canInsert(entity.getItem(),progress))continue;
            WorldItemTransfers.collect(entity,this,stack->insert(stack,direction.getOpposite(),progress,UUID.randomUUID(),level.getGameTime()));
            if(retrying)break;
        }
    }
    public ResourceHandler<ItemResource> automation(Direction side){
        return new ResourceHandler<>(){
            private boolean input(){return side!=Direction.DOWN&&(side==null||!side.getAxis().isHorizontal()||ConveyorConnections.accepts(getBlockState(),side))&&!released&&!retrying;}
            private boolean output(){return (side==null||side==direction||side==Direction.DOWN)&&!released&&!retrying&&!batches.isEmpty()&&batches.getFirst().progress>=1-EPS;}
            private int insertionSlot(ItemStack stack){int merge=mergeIndex(stack,0);return merge>=0?merge:space(0)?batches.size():-1;}
            public int size(){return 2;}
            public ItemResource getResource(int slot){return output()&&slot==0?ItemResource.of(batches.getFirst().stack):ItemResource.EMPTY;}
            public long getAmountAsLong(int slot){return output()&&slot==0?batches.getFirst().stack.getCount():0;}
            public long getCapacityAsLong(int slot,ItemResource resource){return resource.isEmpty()?16:Math.min(16,resource.getMaxStackSize());}
            public boolean isValid(int slot,ItemResource resource){return input()&&!resource.isEmpty()&&slot==insertionSlot(resource.toStack(1));}
            public int insert(int slot,ItemResource resource,int amount,TransactionContext transaction){
                if(amount<0||resource.isEmpty())throw new IllegalArgumentException("无效传送带输入");
                var stack=resource.toStack(Math.min(amount,16));if(!input()||slot!=insertionSlot(stack)||!canInsert(stack,0))return 0;
                queue.touch(transaction);return ConveyorBlockEntity.this.insert(stack,side!=null&&side.getAxis().isHorizontal()?side:direction.getOpposite(),0,UUID.randomUUID(),level.getGameTime());
            }
            public int extract(int slot,ItemResource resource,int amount,TransactionContext transaction){
                if(amount<0||resource.isEmpty())throw new IllegalArgumentException("无效传送带取出");
                if(!output()||slot!=0||!ItemResource.of(batches.getFirst().stack).equals(resource))return 0;
                queue.touch(transaction);var batch=batches.getFirst();int taken=Math.min(amount,batch.stack.getCount());batch.stack.shrink(taken);if(batch.stack.isEmpty())batches.removeFirst();return taken;
            }
        };
    }
    public void interact(ServerPlayer player,InteractionHand hand){
        if(!stillValid(player)||!ready()||player.isSpectator()||!player.isAlive())return;
        JsonObject intent=new JsonObject();intent.addProperty("belt",key());intent.addProperty("hand",hand.name());intent.addProperty("rotate",player.isShiftKeyDown());
        IndustrialPorts.inventory().transactMachine(player,"conveyor/"+UUID.randomUUID(),intent,this,()->{
            if(!stillValid(player))throw new IllegalArgumentException("传送带已失效");ItemStack held=player.getItemInHand(hand);
            if(held.isEmpty()&&player.isShiftKeyDown()){direction=direction.getClockWise();for(var batch:batches)batch.entry=batch.entry.getClockWise();dirty();return;}
            if(held.isEmpty()){
                if(batches.isEmpty())return;var batch=batches.getFirst();var taken=batch.stack.copy();InventoryPacking.insertRaw(player,taken);
                batch.stack.setCount(taken.getCount());if(batch.stack.isEmpty())batches.removeFirst();dirty();
            }else{int accepted=insert(held,direction.getOpposite(),0,UUID.randomUUID(),level.getGameTime());held.shrink(accepted);}
            player.getInventory().setChanged();
        },Map.of());
        sync(level.getGameTime());
    }
    public void releaseContents(){
        if(released)return;
        if(batches.isEmpty()){released=true;dirty();return;}
        var emissions=batches.stream().map(batch->new WorldItemTransfers.Emission(batch.stack,new Vec3(worldPosition.getX()+.5,worldPosition.getY()+ConveyorGeometry.HEIGHT+ConveyorConnections.lift(getBlockState(),.5,.5)+.15,worldPosition.getZ()+.5),new Vec3(0,.1,0))).toList();
        WorldItemTransfers.spill(this,"belt-remove",emissions,()->{batches.clear();released=true;dirty();});
    }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level instanceof ServerLevel)releaseContents();}
    @Override public int getContainerSize(){return 2;}
    @Override public boolean isEmpty(){return batches.isEmpty();}
    @Override public ItemStack getItem(int slot){return slot>=0&&slot<batches.size()?batches.get(slot).stack.copy():ItemStack.EMPTY;}
    @Override public void setItem(int slot,ItemStack stack){
        if(slot<0||slot>=2||!stack.isEmpty()&&stack.getCount()>limit(stack))throw new IllegalArgumentException("传送带批次超出容量");
        if(stack.isEmpty()){if(slot<batches.size())batches.remove(slot);}else if(slot<batches.size())batches.get(slot).stack=stack.copy();else if(slot==batches.size()&&space(0))batches.add(new Batch(UUID.randomUUID(),stack,direction.getOpposite(),0));else throw new IllegalArgumentException("传送带入口没有空间");dirty();
    }
    @Override public ItemStack removeItem(int slot,int amount){
        if(slot<0||slot>=batches.size()||amount<=0)return ItemStack.EMPTY;var batch=batches.get(slot);var taken=batch.stack.split(amount);if(batch.stack.isEmpty())batches.remove(slot);dirty();return taken;
    }
    @Override public ItemStack removeItemNoUpdate(int slot){return removeItem(slot,16);}
    @Override public void clearContent(){batches.clear();dirty();}
    @Override public boolean stillValid(net.minecraft.world.entity.player.Player player){return !isRemoved()&&level!=null&&level.getBlockEntity(worldPosition)==this&&player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return !stack.isEmpty()&&canInsert(stack,0);}
    @Override public int[] getSlotsForFace(Direction side){return new int[]{0,1};}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return side!=Direction.DOWN&&(!side.getAxis().isHorizontal()||ConveyorConnections.accepts(getBlockState(),side))&&canPlaceItem(slot,stack);}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return slot==0&&(side==direction||side==Direction.DOWN)&&!batches.isEmpty()&&batches.getFirst().progress>=1-EPS;}
    private void write(ValueOutput output){
        output.putInt("schema",1);output.putString("identity",key());output.putString("direction",direction.getSerializedName());output.putBoolean("released",released);output.putBoolean("side_cursor",sideCursor);output.putInt("source_cursor",sourceCursor);
        output.store("batches",Saved.CODEC.listOf(),batches.stream().map(Batch::save).toList());
    }
    private void read(ValueInput input){
        if(input.getIntOr("schema",0)!=1)throw new IllegalArgumentException("不支持的传送带数据代际");
        identity=UUID.fromString(input.getStringOr("identity",""));direction=Direction.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,new JsonPrimitive(input.getStringOr("direction",""))).getOrThrow();
        if(!direction.getAxis().isHorizontal())throw new IllegalArgumentException("传送带出口必须水平");
        released=input.getBooleanOr("released",false);sideCursor=input.getBooleanOr("side_cursor",false);sourceCursor=input.getIntOr("source_cursor",0);
        var saved=input.read("batches",Saved.CODEC.listOf()).orElseThrow(()->new IllegalArgumentException("缺少或无效的传送带批次数据"));if(saved.size()>2)throw new IllegalArgumentException("传送带批次超过两组");
        List<Batch> loaded=new ArrayList<>();Set<UUID> identities=new HashSet<>();
        for(var data:saved){
            if(data.stack.isEmpty()||data.stack.getCount()>limit(data.stack)||!Double.isFinite(data.progress)||data.progress<0||data.progress>1||!data.entry.getAxis().isHorizontal()||data.entry==direction)throw new IllegalArgumentException("无效传送带批次");
            var batch=new Batch(UUID.fromString(data.id),data.stack,data.entry,data.progress);if(!identities.add(batch.id))throw new IllegalArgumentException("传送带批次身份重复");
            batch.down=data.down;batch.moving=data.moving;batch.advancedAt=Long.MIN_VALUE;loaded.add(batch);
        }
        loaded.sort(Comparator.comparingDouble((Batch b)->b.progress).reversed());
        if(released&&!loaded.isEmpty())throw new IllegalArgumentException("已拆除的传送带不能保留批次");
        if(loaded.size()==2&&loaded.get(0).progress-loaded.get(1).progress<GAP-EPS)throw new IllegalArgumentException("传送带批次间距不足");
        batches.clear();batches.addAll(loaded);contentDirty=true;
    }
    @Override protected void saveAdditional(ValueOutput output){super.saveAdditional(output);write(output);}
    @Override protected void loadAdditional(ValueInput input){super.loadAdditional(input);read(input);}
    @Override public JsonElement transactionState(){var state=new JsonObject();state.addProperty("nbt",saveCustomOnly(level.registryAccess()).toString());return state;}
    @Override public void restoreTransactionState(JsonElement state){
        try{loadCustomOnly(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),TagParser.parseCompoundFully(state.getAsJsonObject().get("nbt").getAsString())));
            if(level.getBlockState(worldPosition).getBlock() instanceof ConveyorBlock&&getBlockState().getValue(ConveyorBlock.FACING)!=direction)level.setBlock(worldPosition,getBlockState().setValue(ConveyorBlock.FACING,direction),Block.UPDATE_ALL);dirty();
        }catch(Exception failure){throw new IllegalStateException("传送带事务恢复失败",failure);}
    }
    private void sync(long now){
        if(!(level instanceof ServerLevel server))return;
        if(getBlockState().getValue(ConveyorBlock.FACING)!=direction){level.setBlock(worldPosition,getBlockState().setValue(ConveyorBlock.FACING,direction),Block.UPDATE_ALL);ConveyorConnections.refresh(level,worldPosition);}
        if(contentDirty||lastSync==Long.MIN_VALUE||now-lastSync>=20){displayTick=now;server.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);contentDirty=false;lastSync=now;}
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var output=TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,registries);write(output);output.putLong("display_tick",level.getGameTime());return output.buildResult();}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override public void handleUpdateTag(ValueInput input){read(input);displayTick=input.getLongOr("display_tick",level.getGameTime());}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ValueInput input){handleUpdateTag(input);}
    public List<Visual> visuals(float partial){
        double elapsed=level.isClientSide()?Math.clamp(level.getGameTime()+partial-displayTick,0,20):0;List<Visual> result=new ArrayList<>(2);double ahead=1+GAP;
        for(Batch batch:batches){double progress=Math.max(batch.progress,Math.min(ahead-GAP,Math.min(1,batch.progress+(batch.moving?elapsed*step():0))));result.add(new Visual(batch.id,batch.stack.copy(),batch.entry,progress));ahead=progress;}
        return result;
    }
    public static Vec3 path(Direction entry,Direction exit,double progress){
        if(progress<=.5)return new Vec3(.5+entry.getStepX()*(.5-progress),ConveyorGeometry.HEIGHT,.5+entry.getStepZ()*(.5-progress));
        return new Vec3(.5+exit.getStepX()*(progress-.5),ConveyorGeometry.HEIGHT,.5+exit.getStepZ()*(progress-.5));
    }
}
