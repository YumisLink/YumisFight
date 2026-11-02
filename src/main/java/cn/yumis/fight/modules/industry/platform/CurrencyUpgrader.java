package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.economy.api.CurrencyRules;
import cn.yumis.fight.modules.industry.api.Fraction;
import cn.yumis.fight.platform.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Event-driven conversion windows and a persisted, partially paid attempt. */
public final class CurrencyUpgrader {
    public static final long CAPACITY=1_000_000;
    private enum State {IDLE,QUEUED,OUTPUT,ENERGY}
    private final MachineBlockEntity be;
    private State state=State.IDLE;
    private long needed,paid,sequence,lastTick;
    private Fraction delay=Fraction.ZERO;
    private final long[] counts=new long[9];
    private final String[] ids=new String[9];
    private boolean loading,ready,pending;
    private ItemStack result=ItemStack.EMPTY;
    private List<ItemStack> inputs=new ArrayList<>();
    CurrencyUpgrader(MachineBlockEntity be){this.be=be;Arrays.fill(ids,"");}
    public void changed(int slot){
        if(loading||!ready||!(be.getLevel() instanceof ServerLevel))return;
        var layout=be.slots();var stack=be.getItem(slot);
        if(layout.input(slot)){String id=stack.isEmpty()?"":ItemData.id(stack);long count=be.items.getAmountAsLong(slot);boolean inserted=!stack.isEmpty()&&be.canPlaceItem(slot,stack)&&(!ids[slot].equals(id)||count>counts[slot]);ids[slot]=id;counts[slot]=count;if(inserted)wake();}
        else if(layout.output(slot)&&state==State.OUTPUT)wake();
    }
    public void loaded(){ready=true;for(int i=0;i<9;i++){var stack=be.getItem(i);counts[i]=be.items.getAmountAsLong(i);ids[i]=stack.isEmpty()?"":ItemData.id(stack);}if(state==State.QUEUED||pending){IndustrialWorld.get((ServerLevel)be.getLevel()).wakeUpgrade(be);}}
    public void beginLoad(){loading=true;}
    public void wake(){
        if(!(be.getLevel() instanceof ServerLevel level)||be.isRemoved())return;
        if(state!=State.QUEUED){delay=Fraction.of(20).divide(be.pluginSpeed());lastTick=level.getGameTime();}
        state=State.QUEUED;be.status="等待处理窗口";IndustrialWorld.get(level).wakeUpgrade(be);be.setChanged();
    }
    public boolean due(){long now=be.getLevel().getGameTime();delay=delay.subtract(Fraction.of(Math.max(0,now-lastTick)));lastTick=now;return !delay.positive();}
    public long charge(long offered){
        long take=0;
        if(pending&&paid<needed){long part=Math.min(Math.max(0,offered),needed-paid);paid+=part;take+=part;offered-=part;if(paid==needed){state=State.QUEUED;delay=Fraction.ZERO;lastTick=be.getLevel().getGameTime();IndustrialWorld.get((ServerLevel)be.getLevel()).wakeUpgrade(be);}}
        long buffered=Math.min(Math.max(0,offered),CAPACITY-be.energy);be.energy+=buffered;take+=buffered;if(take>0)be.setChanged();return take;
    }
    public double progress(){return pending&&needed>0?paid/(double)needed:0;}
    public boolean pending(){return pending;}
    public void finishPaid(){if(pending&&paid==needed)process();}
    public long demand(){return !be.running?0:pending?Math.max(0,needed-paid):Math.max(0,CAPACITY-be.energy);}
    public boolean outputFull(){for(int i=be.slots().outputStart(0);i<be.slots().outputEnd(0);i++)if(be.getItem(i).isEmpty())return false;return true;}
    private int tier(ItemStack stack){return stack.isEmpty()||!ItemData.own(stack)?-1:CurrencyRules.upgradeTier(ItemData.id(stack));}
    private boolean finish(){
        if(paid<needed){state=State.ENERGY;be.status="等待供能，已保留本次合成";return false;}
        if(!result.isEmpty())try(var tx=Transaction.openRoot()){if(be.lane(0).insertOutput(result,tx)!=result.getCount()){state=State.OUTPUT;be.status="等待产物空间";return false;}tx.commit();}
        pending=false;inputs.clear();result=ItemStack.EMPTY;needed=paid=0;sequence++;be.setChanged();return true;
    }
    public void process(){
        if(!be.running){state=State.IDLE;be.status="已停止";return;}
        if(pending&&!finish())return;
        state=State.IDLE;long attempts=0;
        for(int rarity=0;rarity<4;rarity++)while(true){
            long total=0;for(int i=0;i<9;i++)if(tier(be.getItem(i))==rarity)total=Math.addExact(total,be.items.getAmountAsLong(i));if(total<4)break;
            if(outputFull()){state=State.OUTPUT;be.status="输出9格均已占用，合成休眠";be.setChanged();return;}
            var results=CurrencyRules.upgradeResults(rarity);if(results.isEmpty())throw new IllegalStateException("下一稀有度没有自然通货");
            long cost=Math.multiplyExact(CurrencyRules.upgradeCost(rarity),be.energyMultiplier());List<ItemStack> escrow=new ArrayList<>();
            try(var tx=Transaction.openRoot()){
                int left=4;for(int slot=0;slot<9&&left>0;slot++)if(tier(be.getItem(slot))==rarity){int n=(int)Math.min(left,be.items.getAmountAsLong(slot));var resource=be.items.getResource(slot);if(be.items.extract(slot,resource,n,tx)!=n)throw new IllegalStateException("原料已变化");escrow.add(resource.toStack(n));left-=n;}
                if(left!=0)throw new IllegalStateException("原料不足");tx.commit();
            }
            inputs=escrow;result=be.getLevel().getRandom().nextBoolean()?ItemRegistry.item(results.get(be.getLevel().getRandom().nextInt(results.size())).id(),1):ItemStack.EMPTY;
            needed=cost;paid=Math.min(cost,be.energy);be.energy-=paid;pending=true;be.setChanged();
            if(!finish())return;attempts++;
        }
        be.status=attempts>0?"本轮尝试 "+attempts+" 组，合成休眠":"同稀有度原料不足4枚，合成休眠";be.setChanged();
    }
    public List<ItemStack> breakContents(){var result=inputs.stream().map(ItemStack::copy).toList();inputs.clear();pending=false;paid=needed=0;return result;}
    public void save(ValueOutput out){out.putString("upgrade_state",state.name());out.putLong("upgrade_needed",needed);out.putLong("upgrade_paid",paid);out.putLong("upgrade_sequence",sequence);out.putLong("upgrade_last",lastTick);out.putString("upgrade_delay",delay.toString());out.putBoolean("upgrade_pending",pending);out.store("upgrade_result",ItemStack.OPTIONAL_CODEC,result);out.store("upgrade_inputs",ItemStack.CODEC.listOf(),inputs);}
    public void load(ValueInput in){
        loading=true;try{state=State.valueOf(in.getStringOr("upgrade_state","IDLE"));needed=in.getLongOr("upgrade_needed",0);paid=in.getLongOr("upgrade_paid",0);sequence=in.getLongOr("upgrade_sequence",0);lastTick=in.getLongOr("upgrade_last",0);delay=Fraction.parse(in.getStringOr("upgrade_delay","0/1"));pending=in.getBooleanOr("upgrade_pending",false);result=in.read("upgrade_result",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);inputs=new ArrayList<>(in.read("upgrade_inputs",ItemStack.CODEC.listOf()).orElse(List.of()));}finally{loading=false;}
        if(ready&&be.getLevel() instanceof ServerLevel level){IndustrialWorld.get(level).quietUpgrade(be);if(state==State.QUEUED||pending)IndustrialWorld.get(level).wakeUpgrade(be);}
    }
}
