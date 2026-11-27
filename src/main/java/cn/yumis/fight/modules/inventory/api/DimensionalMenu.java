package cn.yumis.fight.modules.inventory.api;

import cn.yumis.fight.mixin.SlotPositionAccessor;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Storage slots keep their menu indices when the viewport, search or category tab changes. */
public final class DimensionalMenu extends AbstractContainerMenu implements CheckpointMenu {
    public static final int BAG_SLOTS=416, LEFT_X=8, PLAYER_Y=25, CATEGORY_Y=147, BAG_X=184, BAG_Y=47, SLOT_SIZE=18;
    private static final int BAG_SCROLL=1000, CATEGORY_SCROLL=2000, TAB=3000, VIEWPORT=10000, MAX_DIMENSION=48, CATEGORY_COLUMNS=9;
    public static final int UPGRADE=3, SORT=4, CATEGORIZE=5;
    private static final CategoryBags.Kind[] KINDS=CategoryBags.Kind.values();
    public final ExtraInventory extra;
    private final int playerStart;
    private final Set<Integer> visible=new HashSet<>();
    private List<Integer> matches=List.of(), view=List.of();
    public String query="";
    public JsonObject preview=new JsonObject();
    private String lastPreview="";
    private int columns=9, rows=6, used, categoryOffset, laidOutPage=-1, laidOutTier=-1;
    private long seenContents=-1;
    private boolean dirty=true;
    private CategoryBags.Kind category=CategoryBags.Kind.CURRENCY;
    private CategoryBags.Stock selectedMaterial;

    public DimensionalMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){
        this(id,inv,buf.readVarInt(),buf.readVarInt());
        loadPreview(JsonParser.parseString(buf.readUtf()).getAsJsonObject());
    }

    public DimensionalMenu(int id,Inventory inv,int tier,int row){
        super(InventoryMenuPorts.type(),id);
        extra=MenuHooks.extra(inv.player);
        extra.bagTier=tier;
        extra.bagPage=row;
        for(int i=0;i<BAG_SLOTS;i++)addStorageSlot(extra.bag,i,null);
        for(var kind:KINDS){
            var container=extra.categories.container(kind);
            for(int i=0;i<CategoryBags.size(kind);i++)addStorageSlot(container,i,kind);
        }
        playerStart=slots.size();
        addStandardInventorySlots(inv,LEFT_X,PLAYER_Y);
        layout();
    }

    private void addStorageSlot(TrackedContainer container,int index,CategoryBags.Kind kind){
        int logical=slots.size();
        addSlot(new Slot(container,index,-2000,-2000){
            @Override public boolean isActive(){return visible.contains(logical);}
            @Override public boolean mayPlace(ItemStack stack){
                return isActive()&&(kind==null||kind!=CategoryBags.Kind.CURRENCY&&CategoryBags.kind(stack)==kind);
            }
            @Override public boolean mayPickup(Player player){return isActive()&&kind!=CategoryBags.Kind.CURRENCY;}
        });
    }

    public static void open(ServerPlayer player){
        var extra=MenuHooks.extra(player);
        JsonObject initial=preview(player,extra);
        player.openMenu(new SimpleMenuProvider((id,inv,owner)->new DimensionalMenu(id,inv,extra.bagTier,extra.bagPage),
            Component.literal(StorageRules.NAMES[extra.bagTier-1])),buf->{
                buf.writeVarInt(extra.bagTier);
                buf.writeVarInt(extra.bagPage);
                buf.writeUtf(initial.toString());
            });
        InventoryMenuPorts.opened(player,true);
    }

    private static JsonObject preview(ServerPlayer player,ExtraInventory extra){
        int tier=extra.bagTier;
        var recipe=tier<16?InventoryMenuPorts.upgrade(player,tier+1):null;
        var value=new JsonObject();
        value.addProperty("tier",tier);
        value.addProperty("requirements",tier>=16?"已达最高档":recipe==null?"当前数据包没有下一档储物升级配方":recipe.requirements());
        value.addProperty("available",recipe!=null);
        value.add("categories",extra.categories.summary());
        return value;
    }

    public void loadPreview(JsonObject value){
        preview=value;
        int unlocks=extra.categories.unlocks();
        extra.categories.loadSummary(value.getAsJsonObject("categories"));
        if(unlocks!=extra.categories.unlocks())dirty=true;
        layout();
    }

    public long contentsRevision(){return extra.bag.revision();}
    public int usedSlots(){rebuildView();return used;}
    public int rows(){return rows;}
    public int playerStart(){return playerStart;}
    public int columns(){return columns;}
    public int scrollRow(){return Math.clamp(extra.bagPage/columns,0,maxScroll());}
    public int totalRows(){return (view.size()+columns-1)/columns;}
    public int maxScroll(){return Math.max(0,totalRows()-rows);}
    public int categoryRows(){return Math.max(1,rows-6);}
    public CategoryBags.Kind category(){return category;}
    public int categoryScroll(){return categoryOffset;}
    public int categoryTotalRows(){return extra.categories.unlocked(category)?(CategoryBags.size(category)+CATEGORY_COLUMNS-1)/CATEGORY_COLUMNS:0;}
    public int categoryMaxScroll(){return Math.max(0,categoryTotalRows()-categoryRows());}
    public CategoryBags.Kind kind(int index){
        if(index<BAG_SLOTS||index>=playerStart)return null;
        int slot=index-BAG_SLOTS;
        for(var kind:KINDS){
            if(slot<CategoryBags.size(kind))return kind;
            slot-=CategoryBags.size(kind);
        }
        return null;
    }
    public int categoryStart(CategoryBags.Kind kind){
        int start=BAG_SLOTS;
        for(var previous:KINDS){
            if(previous==kind)break;
            start+=CategoryBags.size(previous);
        }
        return start;
    }

    public CategoryBags.Stock selectedMaterial(){
        if(selectedMaterial==null||!getCarried().isEmpty())return null;
        var selection=selectedMaterial;
        int index=categoryStart(selection.kind())+selection.slot();
        var slot=slots.get(index);
        if(!slot.isActive()||!ItemStack.isSameItemSameComponents(selection.item(),slot.getItem())){
            selectMaterial(-1);
            return null;
        }
        long quantity=selection.kind()==CategoryBags.Kind.CURRENCY?extra.categories.quantity(selection.slot()):slot.getItem().getCount();
        return quantity>0?new CategoryBags.Stock(selection.kind(),selection.slot(),slot.getItem().copy(),quantity):null;
    }

    public void selectMaterial(int index){
        var kind=kind(index);
        selectedMaterial=null;
        if(kind!=null&&slots.get(index).isActive()&&slots.get(index).hasItem()){
            var slot=slots.get(index);
            selectedMaterial=new CategoryBags.Stock(kind,slot.getContainerSlot(),slot.getItem().copyWithCount(1),0);
        }
    }

    public boolean matches(List<Integer> indices){return matches.equals(indices);}
    private void rebuildView(){
        int limit=extra.bagLimit();
        long revision=contentsRevision();
        if(seenContents==revision&&laidOutTier==extra.bagTier&&laidOutPage>=0&&!dirty)return;
        seenContents=revision;
        List<Integer> next=new ArrayList<>();
        if(query.isEmpty())for(int i=0;i<limit;i++)next.add(i);
        else for(int index:matches)if(index<limit&&!slots.get(index).getItem().isEmpty())next.add(index);
        if(!view.equals(next)){view=List.copyOf(next);dirty=true;}
        used=0;
        for(int i=0;i<limit;i++)if(!extra.bag.getItem(i).isEmpty())used++;
    }

    public void filter(String text,List<Integer> indices){
        if(text.length()>64||indices.size()>BAG_SLOTS)throw new IllegalArgumentException("搜索条件过长");
        var unique=new LinkedHashSet<Integer>();
        for(int index:indices){
            if(index<0||index>=BAG_SLOTS)throw new IllegalArgumentException("搜索槽位无效");
            unique.add(index);
        }
        List<Integer> next=List.copyOf(unique);
        if(query.equals(text)&&matches.equals(next))return;
        if(!query.equals(text))extra.bagPage=0;
        query=text;
        matches=next;
        dirty=true;
        layout();
    }

    public void scrollTo(int row){extra.bagPage=Math.clamp(row,0,maxScroll())*columns;layout();}
    public void scrollCategory(int row){
        int next=Math.clamp(row,0,categoryMaxScroll());
        if(next==categoryOffset)return;
        categoryOffset=next;
        dirty=true;
        layout();
    }
    public void category(CategoryBags.Kind value){
        if(value==category)return;
        category=value;
        categoryOffset=0;
        selectMaterial(-1);
        dirty=true;
        layout();
    }
    public void resize(int columns,int rows){
        this.columns=Math.clamp(columns,1,MAX_DIMENSION);
        this.rows=Math.clamp(rows,1,MAX_DIMENSION);
        categoryOffset=Math.min(categoryOffset,categoryMaxScroll());
        dirty=true;
        layout();
    }

    public static int viewportButton(int columns,int rows){return VIEWPORT+columns*100+rows;}
    public static int bagScrollButton(int row){return BAG_SCROLL+row;}
    public static int categoryScrollButton(int row){return CATEGORY_SCROLL+row;}
    public static int tabButton(CategoryBags.Kind kind){return TAB+kind.ordinal();}
    public static boolean viewButton(int id){return id>=BAG_SCROLL;}
    private void position(int index,int x,int y){
        var slot=(SlotPositionAccessor)slots.get(index);
        slot.ydnf$x(x);
        slot.ydnf$y(y);
    }

    public void layout(){
        rebuildView();
        int current=scrollRow();
        if(!dirty&&laidOutPage==current&&laidOutTier==extra.bagTier)return;
        for(int logical:visible)position(logical,-2000,-2000);
        visible.clear();
        int offset=current*columns;
        for(int i=offset;i<Math.min(view.size(),offset+rows*columns);i++){
            int logical=view.get(i),position=i-offset;
            position(logical,BAG_X+(position%columns)*SLOT_SIZE,BAG_Y+(position/columns)*SLOT_SIZE);
            visible.add(logical);
        }
        if(extra.categories.unlocked(category)){
            int start=categoryStart(category),first=categoryOffset*CATEGORY_COLUMNS;
            for(int i=first;i<Math.min(CategoryBags.size(category),first+categoryRows()*CATEGORY_COLUMNS);i++){
                int position=i-first;
                position(start+i,LEFT_X+(position%CATEGORY_COLUMNS)*SLOT_SIZE,CATEGORY_Y+(position/CATEGORY_COLUMNS)*SLOT_SIZE);
                visible.add(start+i);
            }
        }
        laidOutPage=current;
        laidOutTier=extra.bagTier;
        dirty=false;
    }

    @Override public boolean stillValid(Player player){return player.isAlive();}

    @Override public void clicked(int index,int button,ContainerInput input,Player player){
        layout();
        if(index>=0&&index<playerStart&&!visible.contains(index))return;
        if(kind(index)==CategoryBags.Kind.CURRENCY){
            if(!player.level().isClientSide())clickCurrency(slots.get(index).getContainerSlot(),button,input,player);
            return;
        }
        super.clicked(index,button,input,player);
    }

    private void clickCurrency(int slot,int button,ContainerInput input,Player player){
        if(input!=ContainerInput.PICKUP&&input!=ContainerInput.QUICK_MOVE||button<0||button>1)return;
        var categories=extra.categories;
        var cursor=getCarried();
        var item=CategoryBags.currency(slot);
        long available=categories.quantity(slot);
        if(input==ContainerInput.PICKUP&&!cursor.isEmpty()){
            if(CategoryBags.kind(cursor)!=CategoryBags.Kind.CURRENCY||!ItemStack.isSameItemSameComponents(cursor,item))return;
            int deposited=(int)Math.min(button==1?1:cursor.getCount(),Long.MAX_VALUE-available);
            if(deposited>0){categories.quantity(slot,available+deposited);setCarried(cursor.copyWithCount(cursor.getCount()-deposited));}
            return;
        }
        int amount=(int)Math.min(available,button==1&&input==ContainerInput.PICKUP?1:item.getMaxStackSize());
        if(amount==0)return;
        var taken=item.copyWithCount(amount);
        if(input==ContainerInput.QUICK_MOVE)InventoryPacking.insertRaw(player,taken);
        else setCarried(taken);
        categories.quantity(slot,available-(input==ContainerInput.QUICK_MOVE?amount-taken.getCount():amount));
    }

    @Override public ItemStack quickMoveStack(Player player,int index){
        layout();
        if(index<0||index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);
        if(!slot.hasItem()||!slot.mayPickup(player))return ItemStack.EMPTY;
        var stack=slot.getItem().copy();
        var before=stack.copy();
        if(index<playerStart){
            if(!moveItemStackTo(stack,playerStart,slots.size(),false))return ItemStack.EMPTY;
        }else if(CategoryBags.kind(stack)==category&&extra.categories.unlocked(category)){
            if(player.level().isClientSide())return ItemStack.EMPTY;
            extra.categories.insert(stack);
        }else{
            InventoryPacking.insertBag(player,stack);
        }
        if(stack.getCount()==before.getCount())return ItemStack.EMPTY;
        slot.set(stack.isEmpty()?ItemStack.EMPTY:stack);
        slot.setChanged();
        return before;
    }

    private void categorize(Player player){
        for(int i=9;i<36;i++)categorize(player.getInventory(),i);
        for(int i=0;i<extra.bagLimit();i++)categorize(extra.bag,i);
        layout();
    }

    private void categorize(Container container,int slot){
        var before=container.getItem(slot);
        if(before.isEmpty())return;
        var remaining=before.copy();
        extra.categories.insert(remaining);
        if(remaining.getCount()!=before.getCount())container.setItem(slot,remaining);
    }

    private void sort(){
        List<ItemStack> merged=new ArrayList<>();
        for(int i=0;i<extra.bagLimit();i++){
            ItemStack remaining=extra.bag.getItem(i).copy();
            if(remaining.isEmpty())continue;
            for(ItemStack target:merged)if(ItemStack.isSameItemSameComponents(target,remaining)){
                int count=Math.min(remaining.getCount(),Math.max(0,target.getMaxStackSize()-target.getCount()));
                target.grow(count);
                remaining.shrink(count);
            }
            if(!remaining.isEmpty())merged.add(remaining);
        }
        merged.sort(Comparator.comparing((ItemStack s)->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
            .thenComparing(s->s.getComponents().toString()));
        for(int i=0;i<extra.bagLimit();i++)extra.bag.setItem(i,i<merged.size()?merged.get(i):ItemStack.EMPTY);
        scrollTo(0);
    }

    @Override public boolean clickMenuButton(Player player,int button){
        if(!(player instanceof ServerPlayer server)||!stillValid(player))return false;
        if(button>=VIEWPORT&&button<VIEWPORT+4900){resize((button-VIEWPORT)/100,button%100);return true;}
        if(button>=TAB&&button<TAB+KINDS.length){category(KINDS[button-TAB]);return true;}
        if(button>=CATEGORY_SCROLL&&button<TAB){scrollCategory(button-CATEGORY_SCROLL);return true;}
        if(button>=BAG_SCROLL&&button<CATEGORY_SCROLL){scrollTo(button-BAG_SCROLL);return true;}
        if(button==SORT){sort();return true;}
        if(button==CATEGORIZE){categorize(player);return true;}
        if(button!=UPGRADE||extra.bagTier>=16)return false;
        var recipe=InventoryMenuPorts.upgrade(server,extra.bagTier+1);
        if(recipe==null)throw new IllegalArgumentException("当前数据包没有下一档储物升级配方");
        var stock=extra.categories.stock();
        var all=new ArrayList<ItemStack>();
        for(int i=0;i<36;i++)all.add(player.getInventory().getItem(i));
        for(var entry:stock)all.add(entry.item().copyWithCount((int)Math.min(Integer.MAX_VALUE,entry.quantity())));
        for(int i=0;i<extra.bagLimit();i++)all.add(extra.bag.getItem(i));
        int[] plan=recipe.takePlan(all);
        if(plan==null)throw new IllegalArgumentException("储物升级材料不足");
        for(int i=0;i<plan.length;i++){
            int count=plan[i];
            if(count==0)continue;
            if(i<36)player.getInventory().removeItem(i,count);
            else if(i>=36+stock.size())extra.bag.removeItem(i-36-stock.size(),count);
            else{
                var entry=stock.get(i-36);
                if(entry.kind()==CategoryBags.Kind.CURRENCY)extra.categories.quantity(entry.slot(),entry.quantity()-count);
                else extra.categories.container(entry.kind()).removeItem(entry.slot(),count);
            }
        }
        extra.bagTier++;
        InventoryMenuPorts.defer(server,()->{if(server.containerMenu==this)open(server);});
        return true;
    }

    @Override public void broadcastChanges(){
        layout();
        super.broadcastChanges();
        if(!(extra.player instanceof ServerPlayer player))return;
        JsonObject info=preview(player,extra);
        info.addProperty("menu",containerId);
        String value=info.toString();
        if(!value.equals(lastPreview)){lastPreview=value;Network.send(player,"storage_ui",info);}
    }
}
