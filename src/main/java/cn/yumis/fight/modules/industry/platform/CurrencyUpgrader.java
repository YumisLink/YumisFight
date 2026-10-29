package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.economy.api.CurrencyRules;
import cn.yumis.fight.platform.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Only notifications enqueue composition work; idle machines never poll their inputs. */
public final class CurrencyUpgrader {
    public static final long CAPACITY=1_000_000;
    private enum State {IDLE,QUEUED,OUTPUT,ENERGY}
    private final MachineBlockEntity be;
    private State state=State.IDLE;
    private long needed,next,sequence;
    private final int[] counts=new int[9];
    private final String[] ids=new String[9];
    private boolean loading;
    private boolean ready;
    CurrencyUpgrader(MachineBlockEntity be){this.be=be;Arrays.fill(ids,"");}
    public void changed(int slot){
        if(loading||!ready||!(be.getLevel() instanceof ServerLevel))return;
        var layout=be.slots();var stack=be.getItem(slot);
        if(layout.input(slot)){String id=stack.isEmpty()?"":ItemData.id(stack);boolean inserted=!stack.isEmpty()&&be.canPlaceItem(slot,stack)&&(!ids[slot].equals(id)||stack.getCount()>counts[slot]);ids[slot]=id;counts[slot]=stack.getCount();if(inserted)wake();}
        else if(layout.output(slot)&&state==State.OUTPUT&&stack.isEmpty())wake();
    }
    public void loaded(){ready=true;for(int i=0;i<9;i++){var stack=be.getItem(i);counts[i]=stack.getCount();ids[i]=stack.isEmpty()?"":ItemData.id(stack);}if(state==State.QUEUED||state==State.ENERGY&&be.energy>=needed){state=State.IDLE;next=0;wake();}}
    public void beginLoad(){loading=true;}
    public void wake(){
        if(!(be.getLevel() instanceof ServerLevel level)||be.isRemoved())return;
        if(state!=State.QUEUED)next=Math.max(next,level.getGameTime()+20);state=State.QUEUED;be.status="等待下一处理窗口";IndustrialWorld.get(level).wakeUpgrade(be);be.setChanged();
    }
    public boolean due(){return be.getLevel()!=null&&be.getLevel().getGameTime()>=next;}
    public long charge(long offered){long take=Math.min(Math.max(0,offered),Math.max(0,CAPACITY-be.energy));if(take>0){be.energy+=take;be.setChanged();if(state==State.ENERGY&&be.energy>=needed)wake();}return take;}
    public long demand(){return be.running?Math.max(0,CAPACITY-be.energy):0;}
    public boolean outputFull(){for(int i=be.slots().outputStart();i<be.slots().outputEnd();i++)if(be.getItem(i).isEmpty())return false;return true;}
    private int tier(ItemStack stack){return stack.isEmpty()||!ItemData.own(stack)?-1:CurrencyRules.upgradeTier(ItemData.id(stack));}
    public void process(){
        if(!be.running){state=State.IDLE;be.status="已停止";return;}
        state=State.IDLE;next=be.getLevel().getGameTime()+20;int attempts=0;int[] totals=new int[4];for(int i=0;i<9;i++){int t=tier(be.getItem(i));if(t>=0)totals[t]+=be.getItem(i).getCount();}
        for(int t=0;t<4;t++)while(totals[t]>=4){
            if(outputFull()){state=State.OUTPUT;be.status="输出9格均已占用，合成休眠";be.setChanged();return;}
            long cost=CurrencyRules.upgradeCost(t);if(be.energy<cost){state=State.ENERGY;needed=cost;be.status="缺少魔能，需要 "+cost+"；合成休眠";be.setChanged();return;}
            var results=CurrencyRules.upgradeResults(t);if(results.isEmpty())throw new IllegalStateException("下一稀有度没有自然通货");
            boolean success=be.getLevel().getRandom().nextBoolean();ItemStack result=success?ItemRegistry.item(results.get(be.getLevel().getRandom().nextInt(results.size())).id(),1):ItemStack.EMPTY;
            try(var tx=Transaction.openRoot()){
                new SnapshotJournal<long[]>(){protected long[] createSnapshot(){return new long[]{be.energy,sequence};}protected void revertToSnapshot(long[] old){be.energy=old[0];sequence=old[1];}protected void onRootCommit(long[] old){be.setChanged();}}.updateSnapshots(tx);
                int left=4;for(int slot=0;slot<9&&left>0;slot++)if(tier(be.getItem(slot))==t){int take=Math.min(left,be.items.getAmountAsInt(slot));int removed=be.items.extract(slot,be.items.getResource(slot),take,tx);if(removed!=take)throw new IllegalStateException("通货原料已变化");left-=take;}
                if(left!=0||!result.isEmpty()&&be.insertOutput(result,tx)!=1)throw new IllegalStateException("通货转化空间已变化");be.energy-=cost;sequence++;tx.commit();
            }
            be.consumed+=cost;totals[t]-=4;attempts++;
        }
        be.status=attempts>0?"本轮尝试 "+attempts+" 组，合成休眠":"同稀有度原料不足4枚，合成休眠";be.setChanged();
    }
    public void save(ValueOutput out){out.putString("upgrade_state",state.name());out.putLong("upgrade_needed",needed);out.putLong("upgrade_sequence",sequence);out.putLong("upgrade_next",next);}
    public void load(ValueInput in){
        loading=true;
        try{String saved=in.getStringOr("upgrade_state","IDLE");try{state=State.valueOf(saved);}catch(IllegalArgumentException invalid){state=State.IDLE;}needed=in.getLongOr("upgrade_needed",0);sequence=in.getLongOr("upgrade_sequence",0);be.energy=Math.clamp(be.energy,0,CAPACITY);next=in.getLongOr("upgrade_next",0);for(int i=0;i<9;i++){var stack=be.getItem(i);counts[i]=stack.getCount();ids[i]=stack.isEmpty()?"":ItemData.id(stack);}}
        finally{loading=false;}
        if(ready&&be.getLevel() instanceof ServerLevel level){var world=IndustrialWorld.get(level);world.quietUpgrade(be);if(state==State.QUEUED)world.wakeUpgrade(be);else if(state==State.ENERGY&&be.energy>=needed)wake();}
    }
}
