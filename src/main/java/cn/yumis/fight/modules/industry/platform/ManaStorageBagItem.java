package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.industry.api.EnergyMath;
import cn.yumis.fight.modules.industry.api.IndustrialPorts;
import cn.yumis.fight.modules.inventory.api.InventoryIntake;
import cn.yumis.fight.modules.inventory.api.InventoryPacking;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Function;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Portable industrial energy. The bag and received/withdrawn crystals share one inventory commit. */
public final class ManaStorageBagItem extends Item {
    private static final String DATA="mana_storage_bag";
    private static final int SCHEMA=1;
    public static final String GUIDE="Shift＋滚轮切换等级；右键取1个；Shift＋右键取64个";
    public record State(long energy,int tier){public long available(){return energy/EnergyMath.crystal(tier);}}
    public ManaStorageBagItem(Properties properties){super(properties);}

    /** Unknown or damaged data is preserved and refused, never reset to a new empty bag. */
    public static State state(ItemStack stack){
        if(!(stack.getItem() instanceof ManaStorageBagItem))return null;
        CompoundTag root=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!root.contains(DATA))return new State(0,1);
        CompoundTag data=root.getCompoundOrEmpty(DATA);
        long energy=data.getLongOr("energy",-1);
        int tier=data.getIntOr("tier",-1);
        return data.getIntOr("schema",-1)==SCHEMA&&energy>=0&&tier>=1&&tier<=10?new State(energy,tier):null;
    }
    private static void write(ItemStack bag,State state){
        CustomData.update(DataComponents.CUSTOM_DATA,bag,root->{
            CompoundTag data=new CompoundTag();data.putInt("schema",SCHEMA);data.putLong("energy",state.energy());data.putInt("tier",state.tier());root.put(DATA,data);
        });
    }
    private static List<ItemStack> bags(Player player){
        List<ItemStack> bags=new ArrayList<>();
        addBag(bags,player.getMainHandItem());addBag(bags,player.getOffhandItem());
        for(int i=0;i<36;i++)addBag(bags,player.getInventory().getItem(i));
        return bags;
    }
    private static void addBag(List<ItemStack> bags,ItemStack stack){
        if(state(stack)!=null&&bags.stream().noneMatch(existing->existing==stack))bags.add(stack);
    }
    public static void registerIntake(){
        InventoryIntake.register(IndustrialContent.NS+":mana_storage_bag",new InventoryIntake.Receiver(){
            public boolean accepts(ItemStack stack){int tier=IndustrialContent.crystalTier(stack);return tier>=1&&tier<=10;}
            public int capacity(ServerPlayer player,ItemStack stack){
                long value=EnergyMath.crystal(IndustrialContent.crystalTier(stack)),room=0;
                for(var bag:bags(player)){
                    long count=(Long.MAX_VALUE-state(bag).energy())/value;
                    room+=Math.min(Integer.MAX_VALUE-room,count);
                    if(room==Integer.MAX_VALUE)break;
                }
                return (int)room;
            }
            public void receive(ServerPlayer player,ItemStack incoming){
                long value=EnergyMath.crystal(IndustrialContent.crystalTier(incoming));
                for(var bag:bags(player)){
                    State current=state(bag);
                    int take=(int)Math.min(incoming.getCount(),(Long.MAX_VALUE-current.energy())/value);
                    if(take<=0)continue;
                    write(bag,new State(Math.addExact(current.energy(),Math.multiplyExact(value,take)),current.tier()));
                    incoming.shrink(take);
                    if(incoming.isEmpty())break;
                }
            }
        });
    }
    public static List<Component> information(ItemStack bag){
        State state=state(bag);
        if(state==null)return List.of(Component.literal("储物袋数据异常，原数据已保留").withColor(0xff7777));
        return List.of(Component.literal("目前魔能："+group(state.energy())).withColor(0x8fe4f5),
            Component.literal("当前"+state.tier()+"阶水晶可取出："+group(state.available())+"个").withColor(0xffd778),
            Component.literal(GUIDE).withColor(0xaab7c9));
    }
    private static String group(long value){return String.format(Locale.ROOT,"%,d",value);}
    private static void usable(ServerPlayer player,ItemStack bag){
        if(!IndustrialPorts.ready()||IndustrialPorts.inventory().locked(player.getUUID()))throw new IllegalArgumentException("背包正在处理中，请稍后重试");
        if(!player.isAlive()||player.isSpectator()||player.containerMenu!=player.inventoryMenu)throw new IllegalArgumentException("请关闭容器后使用储物袋");
        if(state(bag)==null)throw new IllegalArgumentException("储物袋数据异常，原数据已保留");
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(player instanceof ServerPlayer server){
            try{withdraw(server,hand,player.isShiftKeyDown()?64:1);}
            catch(RuntimeException failure){server.sendOverlayMessage(Component.literal(failure.getMessage()));}
        }
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){
        return context.getPlayer()==null?InteractionResult.PASS:use(context.getLevel(),context.getPlayer(),context.getHand());
    }
    @Override public InteractionResult useOn(UseOnContext context){return onItemUseFirst(context.getItemInHand(),context);}
    private static void withdraw(ServerPlayer player,InteractionHand hand,int count){
        ItemStack bag=player.getItemInHand(hand);usable(player,bag);
        State current=state(bag);
        if(current.available()<count)throw new IllegalArgumentException("魔能不足，当前"+current.tier()+"阶水晶可取出"+group(current.available())+"个");
        ItemStack crystals=IndustrialContent.stack("energy_crystal_"+current.tier(),count);
        if(InventoryPacking.rawRoom(player,crystals)<count)throw new IllegalArgumentException("背包空间不足，需要放下"+count+"个水晶");
        JsonObject intent=new JsonObject();intent.addProperty("hand",hand.name());intent.addProperty("tier",current.tier());intent.addProperty("count",count);
        IndustrialPorts.inventory().transact(player,"mana-bag-withdraw/"+UUID.randomUUID(),intent,()->{
            write(bag,new State(current.energy()-Math.multiplyExact(EnergyMath.crystal(current.tier()),count),current.tier()));
            InventoryPacking.insertRaw(player,crystals);
            if(!crystals.isEmpty())throw new IllegalStateException("背包空间发生变化，请重试");
        },Map.of());
    }
    public static void register(CommandRouter<ServerPlayer,JsonObject> router,Function<ServerPlayer,String> session){
        router.register("industry","manaBagTier",(player,request)->{
            if(!session.apply(player).equals(request.get("session").getAsString())||!player.isShiftKeyDown())throw new IllegalArgumentException("请手持储物袋并按住Shift切换");
            InteractionHand hand=InteractionHand.valueOf(request.get("hand").getAsString());
            ItemStack bag=player.getItemInHand(hand);usable(player,bag);
            int direction=request.get("direction").getAsInt();
            if(direction!=-1&&direction!=1)throw new IllegalArgumentException("无效的切换方向");
            String operation=UUID.fromString(request.get("operation").getAsString()).toString();
            State current=state(bag);int tier=Math.floorMod(current.tier()-1+direction,10)+1;
            JsonObject intent=new JsonObject();intent.addProperty("hand",hand.name());intent.addProperty("direction",direction);
            IndustrialPorts.inventory().transact(player,"mana-bag-tier/"+operation,intent,()->write(bag,new State(current.energy(),tier)),Map.of());
        });
    }
}
