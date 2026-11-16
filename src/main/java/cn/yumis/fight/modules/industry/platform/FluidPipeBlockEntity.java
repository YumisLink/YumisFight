package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.FluidPipes;
import com.mojang.serialization.Codec;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Each member persists its proportional share, including when its chunk is asleep. */
public final class FluidPipeBlockEntity extends BlockEntity {
    public enum Port { INPUT("送入机器"),EXTRACT("从机器抽取"),CLOSED("断开");public final String label;Port(String label){this.label=label;} }
    private int linked,closed,machines;
    private boolean initialized,released;
    private final int[] modes=new int[6],used=new int[6];
    private long budgetTick=Long.MIN_VALUE,lastSync=Long.MIN_VALUE;
    private FluidResource visibleFluid=FluidResource.EMPTY;
    private long visibleAmount,visibleCapacity;
    FluidPipeNetwork network;
    final IndustrialResourceStore<FluidResource> contents=new IndustrialResourceStore<>(1,FluidResource.EMPTY,FluidResource.OPTIONAL_CODEC){
        public long getCapacityAsLong(int slot,FluidResource resource){return grade().capacity();}
        public boolean isValid(int slot,FluidResource resource){return !resource.isEmpty();}
        protected void changed(int slot){FluidPipeBlockEntity.this.setChanged();}
    };
    private final class Budget extends SnapshotJournal<int[]> {
        protected int[] createSnapshot(){return used.clone();}
        protected void revertToSnapshot(int[] snapshot){System.arraycopy(snapshot,0,used,0,6);}
        void touch(TransactionContext tx){updateSnapshots(tx);}
    }
    private final Budget budget=new Budget();
    public FluidPipeBlockEntity(BlockPos pos,BlockState state){super(IndustrialContent.FLUID_PIPE_BE.get(),pos,state);}
    public FluidPipes.Grade grade(){return FluidPipes.grade(((FluidPipeBlock)getBlockState().getBlock()).grade);}
    public Port mode(Direction side){return (closed&FluidPipeBlock.bit(side))!=0?Port.CLOSED:Port.values()[modes[side.ordinal()]];}
    public int machineMask(){return machines;}
    public FluidStack displayFluid(){return visibleFluid.isEmpty()?FluidStack.EMPTY:visibleFluid.toStack(1);}
    public long displayAmount(){return visibleAmount;}
    public long displayCapacity(){return visibleCapacity;}
    boolean linked(Direction side){int bit=FluidPipeBlock.bit(side);return (linked&bit)!=0&&(closed&bit)==0;}
    boolean available(){return !released&&!isRemoved();}
    FluidPipeBlockEntity neighbor(Direction side){var pos=worldPosition.relative(side);return level!=null&&level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe&&pipe.available()?pipe:null;}
    FluidPipeNetwork network(){if(network==null)network=FluidPipeNetwork.collect(this);return network;}
    void invalidateNetwork(){if(network!=null)network.invalidate();}
    ResourceHandler<FluidResource> machine(Direction side){
        var pos=worldPosition.relative(side);if(!level.hasChunkAt(pos)||level.getBlockEntity(pos) instanceof FluidPipeBlockEntity)return null;
        // This module's own machines expose both logical fluid roles to configured pipe ports.
        if(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)return machine.fluidsForPipe(mode(side)==Port.EXTRACT);
        return level.getCapability(Capabilities.Fluid.BLOCK,pos,side.getOpposite());
    }
    public void initialize(Player player){
        if(initialized||!(level instanceof ServerLevel))return;initialized=true;boolean conflict=false;
        for(Direction side:Direction.values()){
            var other=neighbor(side);if(other==null)continue;int bit=FluidPipeBlock.bit(side),opposite=FluidPipeBlock.bit(side.getOpposite());
            if((closed&bit)!=0||(other.closed&opposite)!=0)continue;
            if(!network().compatible(other.network())){closed|=bit;other.closed|=opposite;other.setChanged();conflict=true;continue;}
            invalidateNetwork();other.invalidateNetwork();linked|=bit;other.linked|=opposite;other.setChanged();other.refreshShape();
        }
        refreshShape();setChanged();if(conflict&&player!=null)player.sendOverlayMessage(Component.literal("液体不同，交界处已保持断开；之后需用权杖手动接通"));
    }
    public void configure(Player player,Direction side,boolean shift){
        var pos=worldPosition.relative(side);if(!level.mayInteract(player,pos))throw new IllegalArgumentException("不能修改该连接");
        var other=neighbor(side);int bit=FluidPipeBlock.bit(side),opposite=FluidPipeBlock.bit(side.getOpposite());
        if(other!=null){
            if(shift){player.sendOverlayMessage(Component.literal("管道交界处用普通右键断开／恢复"));return;}
            boolean disconnect=linked(side)&&other.linked(side.getOpposite());
            if(!disconnect&&!network().compatible(other.network()))throw new IllegalArgumentException("两侧液体不同，不能接通");
            invalidateNetwork();other.invalidateNetwork();
            if(disconnect){linked&=~bit;other.linked&=~opposite;closed|=bit;other.closed|=opposite;}
            else{closed&=~bit;other.closed&=~opposite;linked|=bit;other.linked|=opposite;}
            other.setChanged();other.refreshShape();setChanged();refreshShape();
            player.sendOverlayMessage(Component.literal(disconnect?"管道连接已断开":"管道连接已恢复"));return;
        }
        if(machine(side)==null)throw new IllegalArgumentException("请瞄准机器连接臂，或相邻管道的交界方向");
        if(!shift){player.sendOverlayMessage(Component.literal("Shift右键连接臂：送入机器 → 从机器抽取 → 断开"));return;}
        modes[side.ordinal()]=(mode(side).ordinal()+1)%3;
        if(modes[side.ordinal()]==Port.CLOSED.ordinal())closed|=bit;else closed&=~bit;
        setChanged();refreshShape();sync(true);level.invalidateCapabilities(worldPosition);
        player.sendOverlayMessage(Component.literal(FluidPipeBlock.directionName(side)+" · "+mode(side).label));
    }
    void refreshShape(){
        if(!(level instanceof ServerLevel)||released)return;int mask=0,nextMachines=0;
        for(Direction side:Direction.values()){
            int bit=FluidPipeBlock.bit(side);var other=neighbor(side);
            if(other!=null){if(linked(side)&&other.linked(side.getOpposite()))mask|=bit;}
            else if(machine(side)!=null){if(mode(side)!=Port.CLOSED)mask|=bit;nextMachines|=bit;}
            // Preserve the last appearance across a sleeping chunk boundary.
            else if(!level.hasChunkAt(worldPosition.relative(side))&&linked(side))mask|=bit;
        }
        boolean markerChanged=machines!=nextMachines;machines=nextMachines;
        BlockState state=getBlockState(),next=state;for(Direction side:Direction.values())next=next.setValue(FluidPipeBlock.SIDES[side.ordinal()],(mask&FluidPipeBlock.bit(side))!=0);
        if(!next.equals(state))level.setBlock(worldPosition,next,Block.UPDATE_CLIENTS);
        if(markerChanged)sync(true);
    }
    private void resetBudget(){long now=level.getGameTime();if(budgetTick!=now){Arrays.fill(used,0);budgetTick=now;}}
    int remaining(Direction side){resetBudget();return grade().flow()-used[side.ordinal()];}
    void spend(Direction side,int amount,TransactionContext tx){budget.touch(tx);used[side.ordinal()]+=amount;}
    public ResourceHandler<FluidResource> automation(Direction side){
        if(side==null)return null;
        return new ResourceHandler<>(){
            public int size(){return 1;}
            private boolean access(boolean insert){return available()&&level instanceof ServerLevel&&neighbor(side)==null&&mode(side)==(insert?Port.EXTRACT:Port.INPUT);}
            public FluidResource getResource(int slot){return slot==0&&level instanceof ServerLevel?network().resource():FluidResource.EMPTY;}
            public long getAmountAsLong(int slot){return slot==0&&level instanceof ServerLevel?network().amount():0;}
            public long getCapacityAsLong(int slot,FluidResource resource){return isValid(slot,resource)?network().capacity:0;}
            public boolean isValid(int slot,FluidResource resource){return slot==0&&access(true)&&network().accepts(resource);}
            public int insert(int slot,FluidResource resource,int amount,TransactionContext tx){
                if(amount<0)throw new IllegalArgumentException("负流量");if(!isValid(slot,resource))return 0;
                int moved=network().insert(resource,Math.min(amount,remaining(side)),tx);if(moved>0)spend(side,moved,tx);return moved;
            }
            public int extract(int slot,FluidResource resource,int amount,TransactionContext tx){
                if(amount<0)throw new IllegalArgumentException("负流量");if(slot!=0||!access(false))return 0;
                int moved=network().extract(resource,Math.min(amount,remaining(side)),tx);if(moved>0)spend(side,moved,tx);return moved;
            }
        };
    }
    public static void tick(Level level,BlockPos pos,BlockState state,FluidPipeBlockEntity pipe){
        if(!(level instanceof ServerLevel)||!pipe.available())return;if(!pipe.initialized)pipe.initialize(null);
        pipe.network().tick();
    }
    void sync(boolean force){
        if(!(level instanceof ServerLevel)||released)return;
        var net=network();FluidResource fluid=net.resource();long amount=net.amount();
        sync(fluid,amount,net.capacity,force);
    }
    void sync(FluidResource fluid,long amount,long capacity,boolean force){
        if(!force&&lastSync!=Long.MIN_VALUE&&level.getGameTime()-lastSync<4)return;
        if(force||!fluid.equals(visibleFluid)||amount!=visibleAmount||capacity!=visibleCapacity){visibleFluid=fluid;visibleAmount=amount;visibleCapacity=capacity;lastSync=level.getGameTime();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    }
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel){for(Direction side:Direction.values()){var other=neighbor(side);if(other!=null)other.invalidateNetwork();}refreshShape();}}
    private void unload(){if(level instanceof ServerLevel)invalidateNetwork();}
    @Override public void onChunkUnloaded(){unload();super.onChunkUnloaded();}
    @Override public void setRemoved(){unload();super.setRemoved();}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){
        if(released||!(level instanceof ServerLevel))return;var net=network();net.remove(this);released=true;
        for(Direction side:Direction.values()){var other=neighbor(side);if(other!=null){other.linked&=~FluidPipeBlock.bit(side.getOpposite());other.setChanged();other.refreshShape();}}
    }
    @Override protected void saveAdditional(ValueOutput out){super.saveAdditional(out);out.putInt("pipe_schema",1);out.putBoolean("initialized",initialized);out.putInt("linked",linked);out.putInt("closed",closed);out.store("modes",Codec.INT.listOf(),Arrays.stream(modes).boxed().toList());contents.serialize(out.child("fluid"));}
    @Override protected void loadAdditional(ValueInput in){
        super.loadAdditional(in);if(in.getIntOr("pipe_schema",0)!=1)return;
        initialized=in.getBooleanOr("initialized",true);linked=in.getIntOr("linked",0)&63;closed=in.getIntOr("closed",0)&63;
        var list=in.read("modes",Codec.INT.listOf()).orElse(List.of());for(int i=0;i<6;i++)modes[i]=i<list.size()?Math.floorMod(list.get(i),3):0;
        contents.deserialize(in.childOrEmpty("fluid"));
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
        var out=TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,registries);out.putInt("machines",machines);out.putInt("linked",linked);out.putInt("closed",closed);out.store("modes",Codec.INT.listOf(),Arrays.stream(modes).boxed().toList());
        out.store("display_fluid",FluidResource.OPTIONAL_CODEC,visibleFluid);out.putLong("amount",visibleAmount);out.putLong("capacity",visibleCapacity);return out.buildResult();
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void handleUpdateTag(ValueInput in){machines=in.getIntOr("machines",0);linked=in.getIntOr("linked",0);closed=in.getIntOr("closed",0);var list=in.read("modes",Codec.INT.listOf()).orElse(List.of());for(int i=0;i<6;i++)modes[i]=i<list.size()?Math.floorMod(list.get(i),3):0;visibleFluid=in.read("display_fluid",FluidResource.OPTIONAL_CODEC).orElse(FluidResource.EMPTY);visibleAmount=in.getLongOr("amount",0);visibleCapacity=in.getLongOr("capacity",0);}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ValueInput in){handleUpdateTag(in);}
    public List<String> information(Direction side){return List.of(Component.translatable("block."+IndustrialContent.NS+"."+grade().id()).getString(),(visibleFluid.isEmpty()?"空管网":visibleFluid.toStack(1).getHoverName().getString())+" · "+visibleAmount+" / "+visibleCapacity+" mB",FluidPipeBlock.directionName(side)+" · "+((machines&FluidPipeBlock.bit(side))!=0?mode(side).label:linked(side)?"管道已连接":"管道未连接")+" · "+grade().flow()+" mB/t","管道交界：右键 · 机器连接臂：Shift右键");}
}
