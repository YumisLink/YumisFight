package cn.yumis.fight.industry;

import cn.yumis.fight.game.*;
import cn.yumis.fight.network.Network;
import cn.yumis.fight.server.ServerRuntime;
import com.google.gson.JsonObject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Fixed logical indices; a page only controls which real slots can be clicked. */
public final class DimensionalMenu extends AbstractContainerMenu {
    public static final int BAG_SLOTS=416,OVERFLOW_START=416,PLAYER_START=462;
    public final ExtraInventory extra;
    public final int rows;
    public boolean overflow;
    public int overflowPage;
    public JsonObject preview=new JsonObject();
    private String lastPreview="";
    public DimensionalMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){this(id,inv,buf.readVarInt(),buf.readVarInt(),buf.readBoolean());}
    public DimensionalMenu(int id,Inventory inv,int tier,int page,boolean overflow){
        super(IndustrialContent.STORAGE_MENU.get(),id);extra=MenuHooks.extra(inv.player);extra.bagTier=tier;extra.bagPage=page;this.overflow=overflow;rows=overflow?6:Math.min(8,(StorageRules.capacity(tier)+7)/8);
        for(int i=0;i<416;i++){
            final int bagIndex=i;addSlot(new Slot(extra.bag,i,8+(i%8)*18,30+((i%64)/8)*18){
                @Override public boolean isActive(){return !DimensionalMenu.this.overflow&&bagIndex<extra.bagLimit()&&bagIndex/64==extra.bagPage;}
                @Override public boolean mayPlace(ItemStack stack){return isActive();}
                @Override public boolean mayPickup(Player p){return isActive();}
            });
        }
        for(int i=0;i<46;i++){
            final int overflowIndex=i;addSlot(new Slot(extra.bagOverflow,i,8+(i%8)*18,30+((i%64)/8)*18){
                @Override public boolean isActive(){return DimensionalMenu.this.overflow&&overflowIndex/64==overflowPage;}
                @Override public boolean mayPlace(ItemStack stack){return false;}
                @Override public boolean mayPickup(Player p){return isActive();}
            });
        }
        addStandardInventorySlots(inv,0,52+rows*18);
        addDataSlots(new ContainerData(){
            public int get(int i){return switch(i){case 0->extra.bagTier;case 1->extra.bagPage;case 2->DimensionalMenu.this.overflow?1:0;default->overflowPage;};}
            public void set(int i,int v){switch(i){case 0->extra.bagTier=v;case 1->extra.bagPage=v;case 2->DimensionalMenu.this.overflow=v!=0;default->overflowPage=v;}}
            public int getCount(){return 4;}
        });
    }
    public static void open(ServerPlayer p){
        open(p,false);
    }
    private static void open(ServerPlayer p,boolean overflow){
        var e=MenuHooks.extra(p);p.openMenu(new SimpleMenuProvider((id,inv,owner)->new DimensionalMenu(id,inv,e.bagTier,e.bagPage,overflow),Component.literal(StorageRules.NAMES[e.bagTier-1])),buf->{buf.writeVarInt(e.bagTier);buf.writeVarInt(e.bagPage);buf.writeBoolean(overflow);});
        ServerRuntime.get().inventory.bag(p,true);
    }
    @Override public boolean stillValid(Player p){return p.isAlive();}
    @Override public void clicked(int index,int button,ContainerInput input,Player p){
        if(index>=0&&index<PLAYER_START&&!slots.get(index).isActive())return;
        super.clicked(index,button,input,p);
    }
    private void intoBag(ItemStack stack){
        for(int pass=0;pass<2;pass++)for(int i=0;i<extra.bagLimit()&&!stack.isEmpty();i++){
            ItemStack target=extra.bag.getItem(i);if(pass==0&&!target.isEmpty()&&ItemStack.isSameItemSameComponents(target,stack)){int n=Math.min(stack.getCount(),Math.max(0,target.getMaxStackSize()-target.getCount()));target.grow(n);stack.shrink(n);extra.bag.setChanged();}
            else if(pass==1&&target.isEmpty()){int n=Math.min(stack.getCount(),stack.getMaxStackSize());extra.bag.setItem(i,stack.split(n));}
        }
    }
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(p))return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),before=stack.copy();
        if(index<PLAYER_START){if(!moveItemStackTo(stack,PLAYER_START,slots.size(),false))return ItemStack.EMPTY;}
        else {if(overflow)return ItemStack.EMPTY;intoBag(stack);}
        if(stack.getCount()==before.getCount())return ItemStack.EMPTY;slot.set(stack.isEmpty()?ItemStack.EMPTY:stack);slot.setChanged();return before;
    }
    public int overflowUsed(){int n=0;for(int i=0;i<extra.bagOverflow.getContainerSize();i++)if(!extra.bagOverflow.getItem(i).isEmpty())n++;return n;}
    @Override public boolean clickMenuButton(Player p,int button){
        if(!(p instanceof ServerPlayer server)||!stillValid(p))return false;
        if(button==0||button==1){if(overflow)overflowPage=0;else extra.bagPage=Math.clamp(extra.bagPage+(button==0?-1:1),0,StorageRules.pages(extra.bagTier)-1);return true;}
        if(button==2){boolean mode=!overflow;IndustrialWorld.get(server.level()).defer(()->{if(server.containerMenu==this)open(server,mode);});return true;}
        if(button!=3||extra.bagTier>=16||overflow)return false;
        IndustrialRecipe recipe=upgradeRecipe(server);if(recipe==null)throw new IllegalArgumentException("当前数据包没有下一档储物升级配方");
        List<ItemStack> all=new ArrayList<>();for(int i=0;i<36;i++)all.add(p.getInventory().getItem(i));for(int i=0;i<extra.bagLimit();i++)all.add(extra.bag.getItem(i));
        int[] plan=recipe.takePlan(all);if(plan==null)throw new IllegalArgumentException("储物升级材料不足");
        for(int i=0;i<plan.length;i++)if(plan[i]>0){if(i<36)p.getInventory().removeItem(i,plan[i]);else extra.bag.removeItem(i-36,plan[i]);}
        extra.bagTier++;IndustrialWorld.get(server.level()).defer(()->{if(server.containerMenu==this)open(server);});return true;
    }
    private IndustrialRecipe upgradeRecipe(ServerPlayer p){return p.level().recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get()).stream().map(h->h.value()).filter(r->r.machine().equals("storage_upgrade_"+(extra.bagTier+1))).findFirst().orElse(null);}
    @Override public void broadcastChanges(){
        super.broadcastChanges();if(!(extra.player instanceof ServerPlayer p))return;
        JsonObject info=new JsonObject();IndustrialRecipe recipe=extra.bagTier<16?upgradeRecipe(p):null;info.addProperty("requirements",recipe==null?"已达最高档":recipe.ingredients().stream().map(n->n.count()+" × "+n.ingredient().items().findFirst().map(h->new ItemStack(h.value()).getHoverName().getString()).orElse("缺少兼容材料")).reduce((a,b)->a+"；"+b).orElse("无需材料"));
        String value=info.toString();if(!value.equals(lastPreview)){lastPreview=value;Network.send(p,"storage_ui",info);}
    }
}
