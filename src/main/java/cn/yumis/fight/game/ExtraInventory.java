package cn.yumis.fight.game;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import cn.yumis.fight.rules.Rules.Slot;
import cn.yumis.fight.rules.Models.Progress;
import cn.yumis.fight.rules.Models.Gear;
import java.util.*;
import java.util.function.Function;
public final class ExtraInventory extends SimpleContainer {
    public static Function<Player,Progress> CLIENT_PROGRESS=p->new Progress();
    public static Function<Player,Progress> PROGRESS=p->CLIENT_PROGRESS.apply(p);
    public static final Slot[] SLOTS={
        Slot.BRACELET,Slot.NECKLACE,Slot.RING,Slot.SUB_EQUIPMENT,Slot.MAGIC_STONE,Slot.EARRING
    }
    ;
    public final Player player;
    public final SimpleContainer bag=new SimpleContainer(54);
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
    public void layout(List<String> next){
        if(next.size()>64)throw new IllegalArgumentException("槽数超过容量");
        for(int i=0;i<64;i++)super.setItem(i,net.minecraft.world.item.ItemStack.EMPTY);
        groups=new ArrayList<>(next);
        page=Math.clamp(page,0,Math.max(0,(groups.size()-1)/6));
    }
    @Override public boolean canPlaceItem(int index,net.minecraft.world.item.ItemStack stack){
        if(index<0||index>=groups.size()||!EquipmentLayout.CURRENT.accepts(groups.get(index),stack))return false;
        Gear g=GameItems.gear(stack);
        return g==null||g.valid(PROGRESS.apply(player));
    }
}
