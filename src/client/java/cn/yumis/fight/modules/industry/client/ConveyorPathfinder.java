package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.platform.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Incremental A*: preserve selected endpoint facings and the incoming port at every turn and ramp. */
final class ConveyorPathfinder {
    private record Key(BlockPos pos,Direction incoming,int dy,ConveyorBlock.Slope previousSlope){}
    private record Cost(int steps,int turns) implements Comparable<Cost>{
        public int compareTo(Cost other){int c=Integer.compare(steps,other.steps);return c!=0?c:Integer.compare(turns,other.turns);}
    }
    private record Node(BlockPos pos,Direction incoming,int dy,BlockState previousState,Cost cost,Node parent,long order){}
    private final Level level;
    private final ConveyorBlock block;
    private final BlockPos start,end;
    private final Direction startFacing,endFacing;
    private final Map<BlockPos,BlockState> snapshot=new HashMap<>();
    private final Map<Key,Cost> best=new HashMap<>();
    private final PriorityQueue<Node> open;
    private long serial;
    private int expanded;
    private boolean done;
    private String error;
    private List<ConveyorRoute.Step> result;
    ConveyorPathfinder(Level level,BlockPos start,BlockPos end,ConveyorBlock block,Direction startFacing,Direction endFacing){
        this.level=level;this.start=start;this.end=end;this.block=block;this.startFacing=startFacing;this.endFacing=endFacing;
        open=new PriorityQueue<>(Comparator.<Node>comparingInt(n->n.cost.steps+distance(n.pos))
            .thenComparingInt(n->n.cost.turns).thenComparingInt(n->distance(n.pos)).thenComparingLong(Node::order));
        if(start.equals(end)||distance(start)>=ConveyorRoute.MAX_LENGTH){fail("请选择不同的起终点，单次路线最多256段");return;}
        if(!usable(start)||!usable(end)){fail("起点或终点被占用、缺少承托或尚未加载");return;}
        Node first=new Node(start,null,0,null,new Cost(1,0),null,serial++);open.add(first);best.put(key(first),first.cost);
    }
    private int distance(BlockPos pos){return Math.max(Math.abs(pos.getX()-end.getX())+Math.abs(pos.getZ()-end.getZ()),Math.abs(pos.getY()-end.getY()));}
    private BlockState state(BlockPos pos){return snapshot.computeIfAbsent(pos,p->ConveyorRoute.state(level,p));}
    private boolean usable(BlockPos pos){
        if(!ConveyorConnections.loaded(level,pos))return false;
        return (pos.equals(start)||pos.equals(end))&&ConveyorConnections.belt(state(pos))||ConveyorRoute.available(level,pos);
    }
    private Key key(Node n){return new Key(n.pos,n.incoming,n.dy,n.previousState==null?ConveyorBlock.Slope.FLAT:n.previousState.getValue(ConveyorBlock.SLOPE));}
    private BlockState current(Node node,BlockPos next,Direction outgoing){
        BlockState raw=state(node.pos);
        if(!ConveyorConnections.belt(raw)){
            Direction facing=node.pos.equals(start)?startFacing:node.pos.equals(end)?endFacing:outgoing;
            raw=block.defaultBlockState().setValue(ConveyorBlock.FACING,facing);
        }
        if(next!=null&&raw.getValue(ConveyorBlock.FACING)!=outgoing)return null;
        final BlockState self=raw;
        BlockState adjusted=ConveyorConnections.adjusted(node.pos,raw,p->{
            if(p.equals(node.pos))return self;
            if(next!=null&&p.equals(next))return block.defaultBlockState();
            for(Node cursor=node;cursor.parent!=null;cursor=cursor.parent)if(p.equals(cursor.parent.pos))return cursor.previousState;
            return state(p);
        });
        if(node.parent!=null&&!ConveyorConnections.connects(node.parent.pos,node.previousState,node.pos,adjusted))return null;
        return adjusted;
    }
    private boolean visited(Node node,BlockPos pos){for(Node n=node;n!=null;n=n.parent)if(n.pos.equals(pos))return true;return false;}
    private boolean historyValid(Node node,BlockPos next,BlockState current){
        // A later, raised segment must not silently tilt an earlier part of this same route.
        for(Node cursor=node;cursor.parent!=null;cursor=cursor.parent){
            BlockPos pos=cursor.parent.pos;
            if(next.getY()!=pos.getY()+1||Math.abs(next.getX()-pos.getX())+Math.abs(next.getZ()-pos.getZ())>1)continue;
            BlockState before=cursor.previousState;
            BlockState after=ConveyorConnections.adjusted(pos,before,p->{
                if(p.equals(next))return block.defaultBlockState();if(p.equals(node.pos))return current;
                for(Node n=node;n.parent!=null;n=n.parent)if(p.equals(n.parent.pos))return n.previousState;
                return state(p);
            });
            if(after.getValue(ConveyorBlock.SLOPE)!=before.getValue(ConveyorBlock.SLOPE)){
                if(cursor.parent.parent!=null||ConveyorConnections.edge(after,after.getValue(ConveyorBlock.FACING))!=ConveyorConnections.edge(before,before.getValue(ConveyorBlock.FACING)))return false;
            }
        }
        return true;
    }
    private List<ConveyorRoute.Step> finish(Node node,BlockState last){
        List<ConveyorRoute.Step> steps=new ArrayList<>();steps.add(new ConveyorRoute.Step(node.pos,last.getValue(ConveyorBlock.FACING)));
        for(Node cursor=node;cursor.parent!=null;cursor=cursor.parent)steps.add(new ConveyorRoute.Step(cursor.parent.pos,cursor.previousState.getValue(ConveyorBlock.FACING)));
        Collections.reverse(steps);return steps;
    }
    void advance(){
        if(done)return;
        long until=System.nanoTime()+2_000_000;int work=0;
        while(!open.isEmpty()&&work++<128&&System.nanoTime()<until){
            Node node=open.remove();if(!node.cost.equals(best.get(key(node))))continue;
            if(++expanded>65536){fail("寻路范围过大，请缩短距离后分段铺设");return;}
            if(node.pos.equals(end)){
                BlockState last=current(node,null,node.incoming);
                if(last!=null)try{var steps=finish(node,last);ConveyorRoute.plan(level,steps,block);result=steps;done=true;return;}catch(IllegalArgumentException invalid){/* Continue searching other legal port approaches. */}
                continue;
            }
            for(Direction facing:Direction.Plane.HORIZONTAL)for(int dy:new int[]{0,1,-1}){
                BlockPos next=node.pos.relative(facing).offset(0,dy,0);
                int length=node.cost.steps+1;if(length+distance(next)>ConveyorRoute.MAX_LENGTH||!usable(next)||visited(node,next))continue;
                BlockState current=current(node,next,facing);if(current==null||!historyValid(node,next,current))continue;
                int height=node.pos.getY()+ConveyorConnections.edge(current,facing);
                if(height<next.getY()||height>next.getY()+1)continue;
                Cost cost=new Cost(length,node.cost.turns+(node.incoming!=null&&node.incoming!=facing?1:0));
                Node candidate=new Node(next,facing,dy,current,cost,node,serial++);Key key=key(candidate);Cost old=best.get(key);
                if(old==null||cost.compareTo(old)<0){best.put(key,cost);open.add(candidate);}
            }
        }
        if(open.isEmpty())fail("当前起终点朝向下，没有256段以内且有承托的可用路线");
    }
    private void fail(String message){done=true;error=message;}
    boolean done(){return done;}
    String error(){return error;}
    List<ConveyorRoute.Step> result(){return result;}
}
