package cn.yumis.fight.industry;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class ManaWand extends Item {
    private static final String[] MODES={"设备供能：先选信标，再选设备","信标连网：依次选择两座信标，重复连接可断开","物品传送：先选容器接收面，再选传送水晶"};
    public ManaWand(Properties p){super(p);}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return useOn(context);}
    private CompoundTag data(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    @Override public InteractionResult use(Level level,Player p,InteractionHand hand){if(!level.isClientSide()&&p.isShiftKeyDown()){ItemStack stack=p.getItemInHand(hand);CompoundTag data=data(stack);int mode=(data.getIntOr("mode",0)+1)%3;data.putInt("mode",mode);data.remove("selected");stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));p.sendOverlayMessage(Component.literal(MODES[mode]));return InteractionResult.SUCCESS;}return InteractionResult.PASS;}
    @Override public InteractionResult useOn(UseOnContext c){
        Player player=c.getPlayer();if(player==null||!(c.getLevel() instanceof ServerLevel level))return InteractionResult.SUCCESS;if(!level.mayInteract(player,c.getClickedPos()))return InteractionResult.FAIL;
        ItemStack stack=c.getItemInHand();CompoundTag data=data(stack);int mode=data.getIntOr("mode",0);var world=IndustrialWorld.get(level);var raw=level.getBlockEntity(WindGeneratorPartBlock.base(level,c.getClickedPos()));
        try{
            if(raw instanceof MachineBlockEntity be){
                if(player.isShiftKeyDown()){if(mode==2&&be.kind()==DeviceKind.TRANSFER)be.targetDimension="";else if(mode==0)be.beacon="";data.remove("selected");be.setChanged();player.sendOverlayMessage(Component.literal("已清除当前绑定"));}
                else if(mode==0){if(be.kind()==DeviceKind.BEACON){data.putString("selected",be.identity.toString());data.putString("dimension",level.dimension().identifier().toString());player.sendOverlayMessage(Component.literal("已选信标，请点击设备"));}else{MachineBlockEntity core=world.loaded.get(data.getStringOr("selected",""));if(core==null)throw new IllegalArgumentException("请先选择已加载的有效信标");world.bind(core,be);player.sendOverlayMessage(Component.literal("设备供能绑定完成"));}}
                else if(mode==1){if(be.kind()!=DeviceKind.BEACON)throw new IllegalArgumentException("此模式请选择信标");String selected=data.getStringOr("selected","");if(selected.isEmpty()){data.putString("selected",be.identity.toString());player.sendOverlayMessage(Component.literal("请选择第二座信标"));}else{MachineBlockEntity first=world.loaded.get(selected);if(first==null)throw new IllegalArgumentException("第一座信标未加载");world.link(first,be);data.remove("selected");player.sendOverlayMessage(Component.literal("信标连线已切换"));}}
                else if(be.kind()==DeviceKind.TRANSFER){String dimension=data.getStringOr("dimension","");if(!dimension.equals(level.dimension().identifier().toString())||!data.contains("target"))throw new IllegalArgumentException("请先选择同维度目标容器");BlockPos target=BlockPos.of(data.getLongOr("target",0));double range=IndustrialRules.CURRENT.transferRanges[be.tier()-1];if(be.tier()<3&&be.getBlockPos().distSqr(target)>range*range)throw new IllegalArgumentException("目标超出当前档次距离");be.targetDimension=dimension;be.targetPosition=target.asLong();be.targetFace=Direction.byName(data.getStringOr("face","up"));be.setChanged();player.sendOverlayMessage(Component.literal("传送目标已绑定："+target.toShortString()));}
                else selectContainer(c,data,player);
            }else if(mode==2)selectContainer(c,data,player);else throw new IllegalArgumentException(MODES[mode]);
            stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));return InteractionResult.SUCCESS;
        }catch(IllegalArgumentException error){player.sendOverlayMessage(Component.literal(error.getMessage()));return InteractionResult.FAIL;}
    }
    private void selectContainer(UseOnContext c,CompoundTag data,Player p){if(c.getLevel().getCapability(Capabilities.Item.BLOCK,c.getClickedPos(),c.getClickedFace())==null)throw new IllegalArgumentException("此接收面没有标准物品接口");data.putString("dimension",c.getLevel().dimension().identifier().toString());data.putLong("target",c.getClickedPos().asLong());data.putString("face",c.getClickedFace().getName());p.sendOverlayMessage(Component.literal("已选目标容器，请点击传送水晶"));}
}
