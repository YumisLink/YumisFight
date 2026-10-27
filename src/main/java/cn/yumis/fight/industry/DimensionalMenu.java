package cn.yumis.fight.industry;

import cn.yumis.fight.game.*;
import cn.yumis.fight.network.Network;
import cn.yumis.fight.server.ServerRuntime;
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
public final class DimensionalMenu extends AbstractContainerMenu {
    public static final int COLUMNS=9,BAG_Y=47,BAG_SLOTS=416,OVERFLOW_START=416,PLAYER_START=462;
    public final ExtraInventory extra;
    public int rows;
    public boolean overflow;
    public int overflowPage;
    public String query="";
    private List<Integer> matches=List.of(),view=List.of();
    private final Set<Integer> visible=new HashSet<>();
    public JsonObject preview=new JsonObject();
    private String lastPreview="";
    public DimensionalMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){this(id,inv,buf.readVarInt(),buf.readVarInt(),buf.readBoolean());}
    public DimensionalMenu(int id,Inventory inv,int tier,int row,boolean overflow){
        super(IndustrialContent.STORAGE_MENU.get(),id);extra=MenuHooks.extra(inv.player);extra.bagTier=tier;extra.bagPage=row;this.overflow=overflow;rows=Math.min(8,((overflow?46:extra.bagLimit())+8)/9);
        for(int i=0;i<PLAYER_START;i++){
            final int logical=i;var container=i<OVERFLOW_START?extra.bag:extra.bagOverflow;int source=i<OVERFLOW_START?i:i-OVERFLOW_START;
            addSlot(new Slot(container,source,8+(source%9)*18,BAG_Y+(source/9)*18){
                @Override public boolean isActive(){return visible.contains(logical);}
                @Override public boolean mayPlace(ItemStack stack){return isActive()&&!DimensionalMenu.this.overflow;}
                @Override public boolean mayPickup(Player p){return isActive();}
            });
        }
        addStandardInventorySlots(inv,8,70+rows*18);
        addDataSlots(new ContainerData(){
            public int get(int i){return switch(i){case 0->extra.bagTier;case 1->extra.bagPage;case 2->DimensionalMenu.this.overflow?1:0;case 3->overflowPage;default->rows;};}
            public void set(int i,int v){switch(i){case 0->extra.bagTier=v;case 1->extra.bagPage=v;case 2->DimensionalMenu.this.overflow=v!=0;case 3->overflowPage=v;default->rows=Math.clamp(v,1,8);}layout();}
            public int getCount(){return 5;}
        });
        layout();scrollTo(scrollRow());
    }
    public static void open(ServerPlayer p){open(p,false);}
    private static void open(ServerPlayer p,boolean overflow){
        var e=MenuHooks.extra(p);if(e.bagSchema>0&&e.bagSchema<3){e.bagPage=StorageRules.migrateRow(e.bagSchema,e.bagPage);e.bagSchema=3;}
        p.openMenu(new SimpleMenuProvider((id,inv,owner)->new DimensionalMenu(id,inv,e.bagTier,e.bagPage,overflow),Component.literal(StorageRules.NAMES[e.bagTier-1])),buf->{buf.writeVarInt(e.bagTier);buf.writeVarInt(e.bagPage);buf.writeBoolean(overflow);});ServerRuntime.get().inventory.bag(p,true);
    }
    public int scrollRow(){return overflow?overflowPage:extra.bagPage;}
    public int totalRows(){return (view.size()+8)/9;}
    public int maxScroll(){return Math.max(0,totalRows()-rows);}
    private void rebuildView(){
        List<Integer> next=new ArrayList<>();int start=overflow?OVERFLOW_START:0,limit=overflow?46:extra.bagLimit();
        if(query.isEmpty())for(int i=0;i<limit;i++)next.add(start+i);
        else for(int logical:matches)if(logical>=start&&logical<start+limit&&!slots.get(logical).getItem().isEmpty())next.add(logical);
        view=List.copyOf(next);
    }
    public void filter(String text,List<Integer> indices){
        if(text.length()>64||indices.size()>PLAYER_START)throw new IllegalArgumentException("搜索条件过长");
        Set<Integer> unique=new LinkedHashSet<>();for(int index:indices){if(index<0||index>=PLAYER_START)throw new IllegalArgumentException("搜索槽位无效");unique.add(index);}
        query=text;matches=List.copyOf(unique);if(overflow)overflowPage=0;else extra.bagPage=0;layout();
    }
    public void scrollTo(int row){rebuildView();if(overflow)overflowPage=Math.clamp(row,0,maxScroll());else extra.bagPage=Math.clamp(row,0,maxScroll());layout();}
    public void resizeRows(int count){rows=Math.clamp(count,1,8);scrollTo(scrollRow());}
    public void layout(){
        rebuildView();visible.clear();int offset=scrollRow()*9;
        for(int i=0;i<PLAYER_START;i++){var slot=(cn.yumis.fight.mixin.SlotPositionAccessor)slots.get(i);slot.ydnf$x(-2000);slot.ydnf$y(-2000);}
        for(int i=offset;i<Math.min(view.size(),offset+rows*9);i++){int logical=view.get(i),position=i-offset;var slot=(cn.yumis.fight.mixin.SlotPositionAccessor)slots.get(logical);slot.ydnf$x(8+(position%9)*18);slot.ydnf$y(BAG_Y+(position/9)*18);visible.add(logical);}
        for(int i=PLAYER_START;i<slots.size();i++){int relative=i-PLAYER_START;var slot=(cn.yumis.fight.mixin.SlotPositionAccessor)slots.get(i);slot.ydnf$x(8+(relative%9)*18);slot.ydnf$y(70+rows*18+(relative<27?relative/9*18:58));}
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
        ItemStack stack=slot.getItem(),before=stack.copy();if(index<PLAYER_START){if(!moveItemStackTo(stack,PLAYER_START,slots.size(),false))return ItemStack.EMPTY;}else{if(overflow)return ItemStack.EMPTY;intoBag(stack);}
        if(stack.getCount()==before.getCount())return ItemStack.EMPTY;slot.set(stack.isEmpty()?ItemStack.EMPTY:stack);slot.setChanged();return before;
    }
    private void sort(){
        List<ItemStack> merged=new ArrayList<>();for(int i=0;i<extra.bagLimit();i++){ItemStack remaining=extra.bag.getItem(i).copy();if(remaining.isEmpty())continue;for(ItemStack target:merged)if(ItemStack.isSameItemSameComponents(target,remaining)){int count=Math.min(remaining.getCount(),Math.max(0,target.getMaxStackSize()-target.getCount()));target.grow(count);remaining.shrink(count);}if(!remaining.isEmpty())merged.add(remaining);}
        merged.sort(Comparator.comparing((ItemStack s)->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).thenComparing(s->s.getComponents().toString()));
        if(merged.size()>extra.bagLimit())throw new IllegalStateException("整理结果超出容量");for(int i=0;i<extra.bagLimit();i++)extra.bag.setItem(i,i<merged.size()?merged.get(i):ItemStack.EMPTY);scrollTo(0);
    }
    public int overflowUsed(){int n=0;for(int i=0;i<46;i++)if(!extra.bagOverflow.getItem(i).isEmpty())n++;return n;}
    @Override public boolean clickMenuButton(Player p,int button){
        if(!(p instanceof ServerPlayer server)||!stillValid(p))return false;
        if(button>=1000&&button<=1008){resizeRows(button-1000);return true;}
        if(button>=100&&button<200){scrollTo(button-100);return true;}
        if(button==0||button==1){scrollTo(scrollRow()+(button==0?-1:1));return true;}
        if(button==2){boolean mode=!overflow;IndustrialWorld.get(server.level()).defer(()->{if(server.containerMenu==this)open(server,mode);});return true;}
        if(button==4&&!overflow){sort();return true;}
        if(button!=3||extra.bagTier>=16||overflow)return false;
        IndustrialRecipe recipe=upgradeRecipe(server);if(recipe==null)throw new IllegalArgumentException("当前数据包没有下一档储物升级配方");
        List<ItemStack> all=new ArrayList<>();for(int i=0;i<36;i++)all.add(p.getInventory().getItem(i));for(int i=0;i<extra.bagLimit();i++)all.add(extra.bag.getItem(i));int[] plan=recipe.takePlan(all);if(plan==null)throw new IllegalArgumentException("储物升级材料不足");
        for(int i=0;i<plan.length;i++)if(plan[i]>0){if(i<36)p.getInventory().removeItem(i,plan[i]);else extra.bag.removeItem(i-36,plan[i]);}
        extra.bagTier++;IndustrialWorld.get(server.level()).defer(()->{if(server.containerMenu==this)open(server);});return true;
    }
    private IndustrialRecipe upgradeRecipe(ServerPlayer p){return p.level().recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get()).stream().map(h->h.value()).filter(r->r.machine().equals("storage_upgrade_"+(extra.bagTier+1))).findFirst().orElse(null);}
    @Override public void broadcastChanges(){
        layout();super.broadcastChanges();if(!(extra.player instanceof ServerPlayer p))return;
        JsonObject info=new JsonObject();IndustrialRecipe recipe=extra.bagTier<16?upgradeRecipe(p):null;info.addProperty("requirements",recipe==null?"已达最高档":recipe.ingredients().stream().map(n->n.count()+" × "+n.ingredient().items().findFirst().map(h->new ItemStack(h.value()).getHoverName().getString()).orElse("缺少兼容材料")).reduce((a,b)->a+"；"+b).orElse("无需材料"));String value=info.toString();if(!value.equals(lastPreview)){lastPreview=value;Network.send(p,"storage_ui",info);}
    }
}
