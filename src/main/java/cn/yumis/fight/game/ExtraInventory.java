package cn.yumis.fight.game;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import cn.yumis.fight.rules.Rules.Slot;
import cn.yumis.fight.rules.Models.Progress;
import cn.yumis.fight.rules.Models.Gear;
import java.util.*;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
public final class ExtraInventory extends SimpleContainer {
    public static Function<Player,Progress> CLIENT_PROGRESS=p->new Progress();
    public static Function<Player,Progress> PROGRESS=p->CLIENT_PROGRESS.apply(p);
    public static final Slot[] SLOTS={
        Slot.BRACELET,Slot.NECKLACE,Slot.RING,Slot.SUB_EQUIPMENT,Slot.MAGIC_STONE,Slot.EARRING
    }
    ;
    public final Player player;
    public final SimpleContainer bag=new SimpleContainer(416);
    public final SimpleContainer bagOverflow=new SimpleContainer(46);
    public int bagSchema=3,bagTier=1,bagPage;
    public int bagLimit(){return bagSchema==0?54:cn.yumis.fight.industry.StorageRules.capacity(bagTier);}
    public List<String> groups=new ArrayList<>();
    public int page;
    public ExtraInventory(Player p){
        super(64);
        player=p;
        for(Slot s:SLOTS)groups.add(GameItems.NS+":"+s.name().toLowerCase(Locale.ROOT));
    }
    @Override public int getContainerSize(){
        return groups.size();
    }
    private static String curiosId(String group){
        return "ydnf_"+group.substring(group.indexOf(':')+1);
    }
    private ICuriosItemHandler curios(){
        var handler=CuriosApi.getCuriosInventoryOrNull(player);
        if(handler==null&&!player.level().isClientSide())throw new IllegalStateException("Curios 饰品背包尚未就绪");
        return handler;
    }
    private int occurrence(int index){
        String group=groups.get(index);int result=0;
        for(int i=0;i<index;i++)if(groups.get(i).equals(group))result++;
        return result;
    }
    private ICurioStacksHandler handler(int index){
        if(index<0||index>=groups.size())return null;
        var inventory=curios();
        return inventory==null?null:inventory.getStacksHandler(curiosId(groups.get(index))).orElse(null);
    }
    @Override public ItemStack getItem(int index){
        var handler=handler(index);
        int occurrence=index>=0&&index<groups.size()?occurrence(index):-1;
        return handler!=null&&occurrence<handler.getStacks().getSlots()?handler.getStacks().getStackInSlot(occurrence):ItemStack.EMPTY;
    }
    @Override public void setItem(int index,ItemStack stack){
        var handler=handler(index);
        int occurrence=index>=0&&index<groups.size()?occurrence(index):-1;
        if(handler==null||occurrence>=handler.getStacks().getSlots()){
            if(!stack.isEmpty())throw new IllegalStateException("Curios 饰品槽缺失："+(index>=0&&index<groups.size()?groups.get(index):index));
            return;
        }
        handler.getStacks().setStackInSlot(occurrence,stack);
    }
    @Override public void clearContent(){
        for(int i=0;i<groups.size();i++)setItem(i,ItemStack.EMPTY);
        super.clearContent();
    }
    public void layout(List<String> next){
        if(next.size()>64)throw new IllegalArgumentException("槽数超过容量");
        var inventory=curios();
        if(inventory!=null&&!player.level().isClientSide()){
            for(Slot slot:SLOTS){
                String name=slot.name().toLowerCase(Locale.ROOT);
                String id=curiosId(name);
                Identifier modifier=Identifier.fromNamespaceAndPath(GameItems.NS,"slot_bonus_"+name);
                int count=(int)next.stream().filter(group->group.substring(group.indexOf(':')+1).equals(name)).count();
                var slotHandler=inventory.getStacksHandler(id).orElse(null);
                if(slotHandler==null)throw new IllegalStateException("Curios 饰品槽未注册："+id);
                double current=slotHandler.getPermanentModifiers().stream().filter(value->value.id().equals(modifier)).mapToDouble(AttributeModifier::amount).findFirst().orElse(0);
                if(current==count-1)continue;
                inventory.removeSlotModifier(id,modifier);
                if(count>1)inventory.addPermanentSlotModifier(id,modifier,count-1,AttributeModifier.Operation.ADD_VALUE);
            }
        }
        groups=new ArrayList<>(next);
        page=Math.clamp(page,0,Math.max(0,(groups.size()-1)/6));
    }
    @Override public boolean canPlaceItem(int index,net.minecraft.world.item.ItemStack stack){
        if(index<0||index>=groups.size()||!EquipmentLayout.CURRENT.accepts(groups.get(index),stack))return false;
        var handler=handler(index);
        if(handler==null||occurrence(index)>=handler.getStacks().getSlots()||!handler.getStacks().isItemValid(occurrence(index),stack))return false;
        Gear g=GameItems.gear(stack);
        return g==null||g.valid(PROGRESS.apply(player));
    }
}
