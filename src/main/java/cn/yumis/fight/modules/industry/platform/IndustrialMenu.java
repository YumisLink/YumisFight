package cn.yumis.fight.modules.industry.platform;
import net.minecraft.world.inventory.Slot;
import cn.yumis.fight.modules.industry.api.DeviceType;

import cn.yumis.fight.network.Network;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class IndustrialMenu extends AbstractContainerMenu {
    public final MachineBlockEntity machine;
    public JsonObject info=new JsonObject();
    private int broadcasts;
    public boolean showLines,showPlugins;
    private final Set<Integer> dragSlots=new LinkedHashSet<>();private int dragKind=-1;
    public int playerStart(){return machine.getContainerSize()+3;}
    public int viewedLine;
    public MachineLane line(){return machine.lane(Math.clamp(viewedLine,0,machine.lineCount()-1));}
    public void receiveInfo(JsonObject snapshot){info=snapshot;if(snapshot.has("line_index"))viewedLine=Math.clamp(snapshot.get("line_index").getAsInt(),0,machine.lineCount()-1);if(snapshot.has("slot_amounts")&&(machine.getLevel()==null||machine.getLevel().isClientSide())){var amounts=snapshot.getAsJsonArray("slot_amounts");for(int i=0;i<machine.items.size()&&i<amounts.size();i++){var resource=machine.items.getResource(i);if(!resource.isEmpty()||amounts.get(i).getAsLong()==0)machine.items.set(i,resource,amounts.get(i).getAsLong());}}}
    private static final Map<String,JsonObject> INITIAL=new LinkedHashMap<>();
    public static void initial(String token,JsonObject snapshot){INITIAL.put(token,snapshot);while(INITIAL.size()>8)INITIAL.remove(INITIAL.keySet().iterator().next());}
    public IndustrialMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){this(id,inv,find(inv,buf));JsonObject snapshot=INITIAL.remove(buf.readUtf());if(snapshot==null)throw new IllegalStateException("缺少设备首屏快照");receiveInfo(snapshot.getAsJsonObject("info"));var data=snapshot.getAsJsonArray("slots");var codec=inv.player.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);for(int i=0;i<slots.size();i++){var cell=data.get(i).getAsJsonObject();var sample=ItemStack.OPTIONAL_CODEC.parse(codec,cell.get("sample")).getOrThrow();if(i<machine.getContainerSize())machine.items.set(i,net.neoforged.neoforge.transfer.item.ItemResource.of(sample),cell.get("amount").getAsLong());else slots.get(i).set(sample);}}
    public static void writeOpen(RegistryFriendlyByteBuf buf,MachineBlockEntity be,String token){buf.writeBlockPos(be.getBlockPos());buf.writeUtf(be.spec().id());buf.writeEnum(be.getBlockState().getValue(MachineBlock.FACING));buf.writeUtf(token);}
    private static MachineBlockEntity find(Inventory inv,RegistryFriendlyByteBuf buf){
        BlockPos pos=buf.readBlockPos();String spec=buf.readUtf();var facing=buf.readEnum(net.minecraft.core.Direction.class);
        if(inv.player.level().getBlockEntity(pos) instanceof MachineBlockEntity be&&be.spec().id().equals(spec)&&be.getBlockState().getValue(MachineBlock.FACING)==facing)return be;
        // A server can open a machine immediately after teleport, before its chunk reaches this client.
        return new MachineBlockEntity(pos,IndustrialContent.ALL_BLOCKS.get(spec).get().defaultBlockState().setValue(MachineBlock.FACING,facing));
    }
    public IndustrialMenu(int id,Inventory inventory,MachineBlockEntity be){
        super(IndustrialContent.MACHINE_MENU.get(),id);machine=be;
        var layout=be.slots();boolean upgrader=be.device().currencyUpgrader();
        for(int i=0;i<layout.size();i++){
            final int slot=i;int offset=layout.offset(i);boolean input=offset>=0&&offset<be.device().inputs(),output=offset>=layout.outputOffset()&&offset<layout.outputOffset()+layout.outputs();int x=input?16+(offset%(upgrader?3:2))*18:output?184+((offset-layout.outputOffset())%3)*18:upgrader?118:64;int y=input?53+(offset/(upgrader?3:2))*18:output?53+((offset-layout.outputOffset())/3)*18:i==layout.crystal()?87:53;
            addSlot(new Slot(be,i,x,y){
                @Override public ItemStack getItem(){var resource=be.items.getResource(slot);return resource.toStack(resource.isEmpty()?0:1);}
                @Override public boolean isActive(){var current=be.slots();return current.lineOf(slot)==viewedLine&&(current.input(slot)||current.output(slot)&&be.device().outputs())||slot==current.crystal()&&be.device().outputs()&&!be.device().has(DeviceType.Capability.GRID_ONLY)||slot==current.fuel()&&be.device().has(DeviceType.Capability.FUEL);}
                @Override public boolean mayPlace(ItemStack s){return isActive()&&be.canPlaceItem(slot,s);}
                @Override public boolean mayPickup(Player p){return isActive();}
                @Override public void setChanged(){super.setChanged();if(be.currencyUpgrade!=null)be.currencyUpgrade.changed(slot);}
            });
        }
        for(int i=0;i<3;i++){final int plugin=i;addSlot(new Slot(be.plugins,i,286,54+i*22){
            @Override public boolean isActive(){return showPlugins;}
            @Override public int getMaxStackSize(ItemStack stack){var definition=MachineBlockEntity.pluginDefinition(stack);return definition==null?0:definition.installedStackLimit();}
            @Override public boolean mayPlace(ItemStack stack){return isActive()&&MachineBlockEntity.pluginDefinition(stack)!=null&&MachineBlockEntity.pluginDefinition(stack).accepts(be.device());}
            @Override public boolean mayPickup(Player player){try{be.validatePluginChange(plugin,ItemStack.EMPTY);return isActive();}catch(IllegalArgumentException e){return false;}}
        });}
        addStandardInventorySlots(inventory,47,153);
    }
    @Override public boolean stillValid(Player p){return machine.stillValid(p);}
    private long amount(int index){return index<machine.getContainerSize()?machine.items.getAmountAsLong(index):slots.get(index).getItem().getCount();}
    private long capacity(int index,ItemStack sample){return index<machine.getContainerSize()?machine.items.getCapacityAsLong(index,net.neoforged.neoforge.transfer.item.ItemResource.of(sample)):slots.get(index).getMaxStackSize(sample);}
    private ItemStack take(int index,int count){if(index<machine.getContainerSize())return machine.removeItem(index,count);return slots.get(index).remove(count);}
    private int put(int index,ItemStack sample,int count){
        Slot slot=slots.get(index);if(!slot.isActive()||!slot.mayPlace(sample))return 0;ItemStack present=slot.getItem();
        if(!present.isEmpty()&&!ItemStack.isSameItemSameComponents(present,sample))return 0;
        int n=(int)Math.min(count,Math.max(0,capacity(index,sample)-amount(index)));if(n==0)return 0;
        if(index<machine.getContainerSize()){var resource=net.neoforged.neoforge.transfer.item.ItemResource.of(sample);machine.items.set(index,resource,Math.addExact(amount(index),n));}
        else {var next=sample.copyWithCount(Math.toIntExact(amount(index)+n));if(index<playerStart())machine.validatePluginChange(index-machine.getContainerSize(),next);slot.set(next);}return n;
    }
    private void pickup(int index,int button,Player player){
        Slot slot=slots.get(index);if(!slot.isActive())return;ItemStack carried=getCarried(),present=slot.getItem();
        if(carried.isEmpty()){
            if(present.isEmpty())return;int limit=present.getMaxStackSize();int n=(int)Math.min(amount(index),button==1?(Math.min(amount(index),limit)+1)/2:limit);
            setCarried(take(index,n));
        }else if(present.isEmpty()||ItemStack.isSameItemSameComponents(present,carried)){
            int inserted=put(index,carried,button==1?1:carried.getCount());carried.shrink(inserted);setCarried(carried);
        }else if(slot.mayPlace(carried)&&amount(index)<=present.getMaxStackSize()&&carried.getCount()<=capacity(index,carried)){
            if(index<playerStart()&&index>=machine.getContainerSize())machine.validatePluginChange(index-machine.getContainerSize(),carried);
            var old=take(index,(int)amount(index));put(index,carried,carried.getCount());setCarried(old);
        }
    }
    @Override public void clicked(int index,int button,ContainerInput input,Player player){
        try{
            if(input==ContainerInput.QUICK_CRAFT){
                int header=button&3;if(header==0){dragSlots.clear();dragKind=(button>>2)&3;}
                else if(header==1&&index>=0&&index<slots.size()&&!getCarried().isEmpty()&&slots.get(index).isActive()&&slots.get(index).mayPlace(getCarried()))dragSlots.add(index);
                else if(header==2){ItemStack carried=getCarried();int each=dragKind==1?1:dragKind==2&&player.isCreative()?carried.getMaxStackSize():carried.getCount()/Math.max(1,dragSlots.size());for(int slot:dragSlots){int n=put(slot,carried,dragKind==2&&player.isCreative()?each:Math.min(each,carried.getCount()));if(dragKind!=2||!player.isCreative())carried.shrink(n);}setCarried(carried);dragSlots.clear();dragKind=-1;}return;
            }
            if(input==ContainerInput.PICKUP_ALL&&!getCarried().isEmpty()){
                ItemStack carried=getCarried();for(int at=0;at<slots.size()&&carried.getCount()<carried.getMaxStackSize();at++){int slot=button==0?at:slots.size()-1-at;var candidate=slots.get(slot);if(candidate.isActive()&&ItemStack.isSameItemSameComponents(candidate.getItem(),carried)){var got=take(slot,(int)Math.min(amount(slot),carried.getMaxStackSize()-carried.getCount()));carried.grow(got.getCount());}}setCarried(carried);return;
            }
            if(index>=0&&index<playerStart()){
                if(!slots.get(index).isActive())return;
                if(input==ContainerInput.PICKUP)pickup(index,button,player);
                else if(input==ContainerInput.QUICK_MOVE)quickMoveStack(player,index);
                else if(input==ContainerInput.THROW){var sample=slots.get(index).getItem();if(!sample.isEmpty())player.drop(take(index,button==0?1:sample.getMaxStackSize()),true);}
                else if(input==ContainerInput.SWAP&&(button>=0&&button<9||button==40)){
                    var incoming=player.getInventory().getItem(button);var old=slots.get(index).getItem();
                    if(incoming.isEmpty()){player.getInventory().setItem(button,take(index,(int)Math.min(old.getMaxStackSize(),amount(index))));broadcastChanges();return;}
                    if(amount(index)>old.getMaxStackSize()||!incoming.isEmpty()&&(!slots.get(index).mayPlace(incoming)||incoming.getCount()>capacity(index,incoming)))return;
                    if(index>=machine.getContainerSize())machine.validatePluginChange(index-machine.getContainerSize(),incoming);
                    if(index>=machine.getContainerSize()){var outgoing=slots.get(index).getItem().copy();slots.get(index).set(incoming.copy());player.getInventory().setItem(button,outgoing);}else {var outgoing=take(index,(int)amount(index));put(index,incoming,incoming.getCount());player.getInventory().setItem(button,outgoing);}
                }else if(input==ContainerInput.CLONE&&player.isCreative()&&getCarried().isEmpty()){var sample=slots.get(index).getItem();if(!sample.isEmpty())setCarried(sample.copyWithCount(sample.getMaxStackSize()));}
                broadcastChanges();return;
            }
            super.clicked(index,button,input,player);
        }catch(IllegalArgumentException error){if(player instanceof ServerPlayer server)server.sendOverlayMessage(net.minecraft.network.chat.Component.literal(error.getMessage()));broadcastChanges();}
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size()||!slots.get(index).isActive())return ItemStack.EMPTY;ItemStack source=slots.get(index).getItem();if(source.isEmpty())return ItemStack.EMPTY;ItemStack original=source.copy();
        if(index<playerStart()){
            while(amount(index)>0){ItemStack normal=source.copyWithCount((int)Math.min(source.getMaxStackSize(),amount(index)));int before=normal.getCount();
                if(index>=machine.getContainerSize())machine.validatePluginChange(index-machine.getContainerSize(),ItemStack.EMPTY);
                if(!moveItemStackTo(normal,playerStart(),slots.size(),true))break;int moved=before-normal.getCount();if(moved==0)break;take(index,moved);}
        }else{
            var definition=MachineBlockEntity.pluginDefinition(source);
            if(definition!=null){if(!showPlugins||!definition.accepts(machine.device()))return ItemStack.EMPTY;for(int i=machine.getContainerSize();i<playerStart()&&!source.isEmpty();i++)source.shrink(put(i,source,source.getCount()));}
            else {
                int crystal=machine.slots().crystal();if(machine.canPlaceItem(crystal,source))source.shrink(put(crystal,source,source.getCount()));
                for(int i=0;i<machine.getContainerSize()&&!source.isEmpty();i++)source.shrink(put(i,source,source.getCount()));
            }
            slots.get(index).set(source);
        }return original;
    }
    public void routeAction(ServerPlayer player,JsonObject request){
        if(!stillValid(player)||player.containerMenu!=this||request.get("menu").getAsInt()!=containerId)return;
        String id=request.get("route").getAsString();var route=machine.routes.stream().filter(value->value.id.equals(id)).findFirst().orElse(null);if(route==null)return;
        String action=request.get("action").getAsString();if(action.equals("delete")){machine.functionCredit=Math.addExact(machine.functionCredit,route.credit);machine.routes.remove(route);machine.setChanged();}else if(action.equals("source")||action.equals("target"))Network.send(player,"industrial_lines",IndustrialWorld.get(player.level()).routePreview(machine,route,action.equals("target")));
    }
    @Override public boolean clickMenuButton(Player p,int button){
        if(!(p instanceof ServerPlayer server)||!stillValid(p))return false;viewedLine=Math.clamp(viewedLine,0,machine.lineCount()-1);
        if(button==6){showPlugins=!showPlugins;return true;}
        if(button==0){if(!machine.device().processing()&&!machine.device().generator()&&!machine.device().functional()&&!machine.device().currencyUpgrader())return false;machine.running=!machine.running;if(machine.running&&machine.currencyUpgrade!=null)machine.currencyUpgrade.wake();}
        else if(button==1)return false;
        else if(button==2)return false;
        else if(button==3){showLines=false;Network.send(server,"industrial_lines",IndustrialWorld.get(server.level()).connectionPreview(machine));}
        else if(button>=20&&button<20+machine.lineCount()){
            viewedLine=button-20;info=IndustrialWorld.get(server.level()).info(machine,viewedLine,showLines);info.addProperty("menu",containerId);Network.send(server,"industrial_ui",info);return true;
        }
        else if(button==4){if(!machine.device().selectedRecipe())return false;if(line().batchRecipe.isEmpty())line().selected="";else line().queued="@auto";}
        else if(button>=70&&button<=72){int local=button-70,tank=line().fluidTank(local);if(local>=machine.device().fluidTanks()||machine.fluidsFor(null)==null)return false;try(var tx=net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()){while(machine.fluids.availableAmount(tank)>0){int amount=(int)Math.min(Integer.MAX_VALUE,machine.fluids.availableAmount(tank));if(machine.fluids.extract(tank,machine.fluids.getResource(tank),amount,tx)==0)break;}tx.commit();}}
        else if(button>=100){if(!machine.device().selectedRecipe())return false;var recipes=IndustrialProcessing.choices(machine);int at=button-100;if(at<0||at>=recipes.size())return false;var selected=recipes.get(at);if(selected.value().minimumTier()>machine.tier())throw new IllegalArgumentException("需要"+selected.value().minimumTier()+"级机器");String id=selected.id().identifier().toString();if(!info.has("recipes")||at>=info.getAsJsonArray("recipes").size()||!id.equals(info.getAsJsonArray("recipes").get(at).getAsJsonObject().get("id").getAsString()))return false;if(line().batchRecipe.isEmpty())line().selected=id;else line().queued=id;}
        else return false;machine.setChanged();return true;
    }
    @Override public void broadcastChanges(){super.broadcastChanges();if(machine.getLevel() instanceof net.minecraft.server.level.ServerLevel server&&++broadcasts%5==1){viewedLine=Math.clamp(viewedLine,0,machine.lineCount()-1);info=IndustrialWorld.get(server).info(machine,viewedLine,showLines);info.addProperty("menu",containerId);for(var player:server.getServer().getPlayerList().getPlayers())if(player.containerMenu==this)Network.send(player,"industrial_ui",info);}}
}
