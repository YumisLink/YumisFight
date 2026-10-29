package cn.yumis.fight.modules.inventory.api;
import net.minecraft.world.inventory.Slot;

import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Stable storage indices; only their viewport positions change. */
public class DimensionalMenu extends AbstractContainerMenu implements cn.yumis.fight.modules.inventory.api.CheckpointMenu {
    public static final int COLUMNS=9,BAG_Y=47,BAG_SLOTS=416,PLAYER_START=416;
    public final ExtraInventory extra;
    public int rows;
    public String query="";
    private List<Integer> matches=List.of(),view=List.of();
    private final Set<Integer> visible=new HashSet<>();
    public JsonObject preview=new JsonObject();
    private String lastPreview="";
    private long seenContents=-1,viewRevision,layoutRevision;
    private boolean viewDirty=true;
    private int viewLimit=-1,viewUsed,lastRows=-1,lastScroll=-1;
    private long laidOutView=-1;
    public long contentsRevision(){return extra.bag.revision();}
    public long layoutRevision(){return layoutRevision;}
    public int usedSlots(){rebuildView();return viewUsed;}
    public DimensionalMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){this(id,inv,buf.readVarInt(),buf.readVarInt());preview=JsonParser.parseString(buf.readUtf()).getAsJsonObject();}
    public DimensionalMenu(int id,Inventory inv,int tier,int row){
        super(InventoryMenuPorts.type(),id);extra=MenuHooks.extra(inv.player);extra.bagTier=tier;extra.bagPage=row;rows=Math.min(8,(extra.bagLimit()+8)/9);
        for(int i=0;i<PLAYER_START;i++){
            final int logical=i;
            addSlot(new Slot(extra.bag,i,8+(i%9)*18,BAG_Y+(i/9)*18){
                @Override public boolean isActive(){return visible.contains(logical);}
                @Override public boolean mayPlace(ItemStack stack){return isActive();}
                @Override public boolean mayPickup(Player p){return isActive();}
            });
        }
        addStandardInventorySlots(inv,8,70+rows*18);
        addDataSlots(new ContainerData(){
            public int get(int i){return switch(i){case 0->extra.bagTier;case 1->extra.bagPage;default->rows;};}
            public void set(int i,int v){switch(i){case 0->extra.bagTier=v;case 1->extra.bagPage=v;default->rows=Math.clamp(v,1,8);}layout();}
            public int getCount(){return 3;}
        });
        layout();scrollTo(scrollRow());
    }
    public static void open(ServerPlayer p){
        var e=MenuHooks.extra(p);
        JsonObject initial=preview(p,e.bagTier);
        p.openMenu(new SimpleMenuProvider((id,inv,owner)->new DimensionalMenu(id,inv,e.bagTier,e.bagPage),Component.literal(StorageRules.NAMES[e.bagTier-1])),buf->{buf.writeVarInt(e.bagTier);buf.writeVarInt(e.bagPage);buf.writeUtf(initial.toString());});InventoryMenuPorts.opened(p,true);
    }
    private static JsonObject preview(ServerPlayer p,int tier){JsonObject info=new JsonObject();var recipe=tier<16?InventoryMenuPorts.upgrade(p,tier+1):null;info.addProperty("tier",tier);info.addProperty("requirements",tier>=16?"已达最高档":recipe==null?"当前数据包没有下一档储物升级配方":recipe.requirements());info.addProperty("available",recipe!=null);return info;}
    public int scrollRow(){return extra.bagPage;}
    public int totalRows(){return (view.size()+8)/9;}
    public int maxScroll(){return Math.max(0,totalRows()-rows);}
    public boolean matches(List<Integer> indices){return matches.equals(indices);}
    private void rebuildView(){
        int limit=extra.bagLimit();
        long revision=contentsRevision();if(seenContents!=revision){seenContents=revision;viewDirty=true;}
        if(!viewDirty&&viewLimit==limit)return;
        viewDirty=false;viewLimit=limit;
        List<Integer> next=new ArrayList<>();int start=0;
        if(query.isEmpty())for(int i=0;i<limit;i++)next.add(start+i);
        else for(int logical:matches)if(logical>=start&&logical<start+limit&&!slots.get(logical).getItem().isEmpty())next.add(logical);
        List<Integer> frozen=List.copyOf(next);if(!view.equals(frozen)){view=frozen;viewRevision++;}
        viewUsed=0;for(int i=0;i<extra.bagLimit();i++)if(!extra.bag.getItem(i).isEmpty())viewUsed++;
    }
    public void filter(String text,List<Integer> indices){
        if(text.length()>64||indices.size()>PLAYER_START)throw new IllegalArgumentException("搜索条件过长");
        Set<Integer> unique=new LinkedHashSet<>();for(int index:indices){if(index<0||index>=PLAYER_START)throw new IllegalArgumentException("搜索槽位无效");unique.add(index);}
        List<Integer> next=List.copyOf(unique);if(query.equals(text)&&matches.equals(next))return;
        boolean changed=!query.equals(text);query=text;matches=next;viewDirty=true;
        if(changed)extra.bagPage=0;layout();
    }
    public void scrollTo(int row){rebuildView();extra.bagPage=Math.clamp(row,0,maxScroll());layout();}
    public void resizeRows(int count){rows=Math.clamp(count,1,8);scrollTo(scrollRow());}
    public void layout(){
        rebuildView();int current=Math.clamp(scrollRow(),0,maxScroll());extra.bagPage=current;
        if(lastRows==rows&&lastScroll==current&&laidOutView==viewRevision)return;
        for(int logical:visible){var slot=(cn.yumis.fight.mixin.SlotPositionAccessor)slots.get(logical);slot.ydnf$x(-2000);slot.ydnf$y(-2000);}
        visible.clear();int offset=current*9;
        for(int i=offset;i<Math.min(view.size(),offset+rows*9);i++){int logical=view.get(i),position=i-offset;var slot=(cn.yumis.fight.mixin.SlotPositionAccessor)slots.get(logical);slot.ydnf$x(8+(position%9)*18);slot.ydnf$y(BAG_Y+(position/9)*18);visible.add(logical);}
        if(lastRows!=rows)for(int i=PLAYER_START;i<slots.size();i++){int relative=i-PLAYER_START;var slot=(cn.yumis.fight.mixin.SlotPositionAccessor)slots.get(i);slot.ydnf$x(8+(relative%9)*18);slot.ydnf$y(70+rows*18+(relative<27?relative/9*18:58));}
        lastRows=rows;lastScroll=current;laidOutView=viewRevision;layoutRevision++;
    }
    @Override public boolean stillValid(Player p){return p.isAlive();}
    @Override public void clicked(int index,int button,ContainerInput input,Player p){layout();if(index>=0&&index<PLAYER_START&&!visible.contains(index))return;super.clicked(index,button,input,p);}
    private void intoBag(ItemStack stack){
        for(int pass=0;pass<2;pass++)for(int i=0;i<extra.bagLimit()&&!stack.isEmpty();i++){
            ItemStack target=extra.bag.getItem(i);if(pass==0&&!target.isEmpty()&&ItemStack.isSameItemSameComponents(target,stack)){int n=Math.min(stack.getCount(),Math.max(0,target.getMaxStackSize()-target.getCount()));target.grow(n);stack.shrink(n);extra.bag.setChanged();}
            else if(pass==1&&target.isEmpty()){int n=Math.min(stack.getCount(),stack.getMaxStackSize());extra.bag.setItem(i,stack.split(n));}
        }
    }
    @Override public ItemStack quickMoveStack(Player p,int index){
        layout();if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(p))return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),before=stack.copy();if(index<PLAYER_START){if(!moveItemStackTo(stack,PLAYER_START,slots.size(),false))return ItemStack.EMPTY;}else{intoBag(stack);}
        if(stack.getCount()==before.getCount())return ItemStack.EMPTY;slot.set(stack.isEmpty()?ItemStack.EMPTY:stack);slot.setChanged();return before;
    }
    private void sort(){
        List<ItemStack> merged=new ArrayList<>();for(int i=0;i<extra.bagLimit();i++){ItemStack remaining=extra.bag.getItem(i).copy();if(remaining.isEmpty())continue;for(ItemStack target:merged)if(ItemStack.isSameItemSameComponents(target,remaining)){int count=Math.min(remaining.getCount(),Math.max(0,target.getMaxStackSize()-target.getCount()));target.grow(count);remaining.shrink(count);}if(!remaining.isEmpty())merged.add(remaining);}
        merged.sort(Comparator.comparing((ItemStack s)->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).thenComparing(s->s.getComponents().toString()));
        if(merged.size()>extra.bagLimit())throw new IllegalStateException("整理结果超出容量");for(int i=0;i<extra.bagLimit();i++)extra.bag.setItem(i,i<merged.size()?merged.get(i):ItemStack.EMPTY);scrollTo(0);
    }
    @Override public boolean clickMenuButton(Player p,int button){
        if(!(p instanceof ServerPlayer server)||!stillValid(p))return false;
        if(button>=1000&&button<=1008){resizeRows(button-1000);return true;}
        if(button>=100&&button<200){scrollTo(button-100);return true;}
        if(button==0||button==1){scrollTo(scrollRow()+(button==0?-1:1));return true;}
        if(button==4){sort();return true;}
        if(button!=3||extra.bagTier>=16)return false;
        InventoryMenuPorts.Upgrade recipe=upgradeRecipe(server);if(recipe==null)throw new IllegalArgumentException("当前数据包没有下一档储物升级配方");
        List<ItemStack> all=new ArrayList<>();for(int i=0;i<36;i++)all.add(p.getInventory().getItem(i));for(int i=0;i<extra.bagLimit();i++)all.add(extra.bag.getItem(i));int[] plan=recipe.takePlan(all);if(plan==null)throw new IllegalArgumentException("储物升级材料不足");
        for(int i=0;i<plan.length;i++)if(plan[i]>0){if(i<36)p.getInventory().removeItem(i,plan[i]);else extra.bag.removeItem(i-36,plan[i]);}
        extra.bagTier++;InventoryMenuPorts.defer(server,()->{if(server.containerMenu==this)open(server);});return true;
    }
    private InventoryMenuPorts.Upgrade upgradeRecipe(ServerPlayer p){return InventoryMenuPorts.upgrade(p,extra.bagTier+1);}
    @Override public void broadcastChanges(){
        layout();super.broadcastChanges();if(!(extra.player instanceof ServerPlayer p))return;
        JsonObject info=preview(p,extra.bagTier);info.addProperty("menu",containerId);String value=info.toString();if(!value.equals(lastPreview)){lastPreview=value;Network.send(p,"storage_ui",info);}
    }
}
