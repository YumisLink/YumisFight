package cn.yumis.fight.modules.industry.platform;
import cn.yumis.fight.modules.industry.api.IndustrialRules;
import cn.yumis.fight.modules.industry.api.DeviceType;

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
    private static final String[] MODES={"设备供能：先选信标，再选设备","信标连网：依次选择两座信标，重复连接可断开","物品传送：先选容器接收面，再选传送水晶","液体供给：先选源出口，再选目标入口，最后点击供液水晶"};
    public ManaWand(Properties p){super(p);}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return useOn(context);}
    private CompoundTag data(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    @Override public InteractionResult use(Level level,Player p,InteractionHand hand){if(!level.isClientSide()&&p.isShiftKeyDown()){ItemStack stack=p.getItemInHand(hand);CompoundTag data=data(stack);int mode=(data.getIntOr("mode",0)+1)%MODES.length;data.putInt("mode",mode);data.remove("selected");data.remove("fluid_source");data.remove("fluid_target");stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));p.sendOverlayMessage(Component.literal(MODES[mode]));return InteractionResult.SUCCESS;}return InteractionResult.PASS;}
    @Override public InteractionResult useOn(UseOnContext c){
        Player player=c.getPlayer();if(player==null||!(c.getLevel() instanceof ServerLevel level))return InteractionResult.SUCCESS;if(!level.mayInteract(player,c.getClickedPos()))return InteractionResult.FAIL;
        ItemStack stack=c.getItemInHand();CompoundTag data=data(stack);int mode=data.getIntOr("mode",0);var world=IndustrialWorld.get(level);var raw=level.getBlockEntity(WindGeneratorPartBlock.base(level,c.getClickedPos()));
        try{
            if(mode==3){bindFluid(c,data);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));return InteractionResult.SUCCESS;}
            if(raw instanceof MachineBlockEntity be){
                if(player.isShiftKeyDown()){if(mode==2&&be.device().has(DeviceType.Capability.LOGISTICS))be.targetDimension="";else if(mode==0)be.beacon="";data.remove("selected");be.setChanged();player.sendOverlayMessage(Component.literal("已清除当前绑定"));}
                else if(mode==0){if(be.device().has(DeviceType.Capability.STORAGE_NETWORK)){data.putString("selected",be.identity.toString());data.putString("dimension",level.dimension().identifier().toString());player.sendOverlayMessage(Component.literal("已选信标，请点击设备"));}else{MachineBlockEntity core=world.loaded.get(data.getStringOr("selected",""));if(core==null)throw new IllegalArgumentException("请先选择已加载的有效信标");world.bind(core,be);player.sendOverlayMessage(Component.literal("设备供能绑定完成"));}}
                else if(mode==1){if(!be.device().has(DeviceType.Capability.STORAGE_NETWORK))throw new IllegalArgumentException("此模式请选择信标");String selected=data.getStringOr("selected","");if(selected.isEmpty()){data.putString("selected",be.identity.toString());player.sendOverlayMessage(Component.literal("请选择第二座信标"));}else{MachineBlockEntity first=world.loaded.get(selected);if(first==null)throw new IllegalArgumentException("第一座信标未加载");world.link(first,be);data.remove("selected");player.sendOverlayMessage(Component.literal("信标连线已切换"));}}
                else if(be.device().has(DeviceType.Capability.LOGISTICS)){String dimension=data.getStringOr("dimension","");if(!dimension.equals(level.dimension().identifier().toString())||!data.contains("target"))throw new IllegalArgumentException("请先选择同维度目标容器");BlockPos target=BlockPos.of(data.getLongOr("target",0));double range=IndustrialRules.current().transferRanges[be.tier()-1];if(be.tier()<3&&be.getBlockPos().distSqr(target)>range*range)throw new IllegalArgumentException("目标超出当前档次距离");be.targetDimension=dimension;be.targetPosition=target.asLong();be.targetFace=Direction.byName(data.getStringOr("face","up"));be.setChanged();player.sendOverlayMessage(Component.literal("传送目标已绑定："+target.toShortString()));}
                else selectContainer(c,data,player);
            }else if(mode==2)selectContainer(c,data,player);else throw new IllegalArgumentException(MODES[mode]);
            stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));return InteractionResult.SUCCESS;
        }catch(IllegalArgumentException error){player.sendOverlayMessage(Component.literal(error.getMessage()));return InteractionResult.FAIL;}
    }
    private void selectContainer(UseOnContext c,CompoundTag data,Player p){if(c.getLevel().getCapability(Capabilities.Item.BLOCK,c.getClickedPos(),c.getClickedFace())==null)throw new IllegalArgumentException("此接收面没有标准物品接口");data.putString("dimension",c.getLevel().dimension().identifier().toString());data.putLong("target",c.getClickedPos().asLong());data.putString("face",c.getClickedFace().getName());p.sendOverlayMessage(Component.literal("已选目标容器，请点击传送水晶"));}
    private void bindFluid(UseOnContext c,CompoundTag data){
        var level=c.getLevel();var player=c.getPlayer();String dimension=level.dimension().identifier().toString();
        if(level.getBlockEntity(c.getClickedPos()) instanceof MachineBlockEntity be&&be.device().has(DeviceType.Capability.FLUID_LOGISTICS)){
            if(player.isShiftKeyDown()){be.sourceDimension=be.targetDimension="";data.remove("fluid_source");data.remove("fluid_target");be.setChanged();player.sendOverlayMessage(Component.literal("供液绑定已清除"));return;}
            if(!dimension.equals(data.getStringOr("fluid_dimension",""))||!data.contains("fluid_source")||!data.contains("fluid_target"))throw new IllegalArgumentException(MODES[3]);
            BlockPos source=BlockPos.of(data.getLongOr("fluid_source",0)),target=BlockPos.of(data.getLongOr("fluid_target",0));double range=IndustrialRules.current().transferRanges[be.tier()-1];
            if(source.equals(target))throw new IllegalArgumentException("源与目标不能相同");
            if(be.tier()<3&&(be.getBlockPos().distSqr(source)>range*range||be.getBlockPos().distSqr(target)>range*range))throw new IllegalArgumentException("源或目标超出供液范围");
            be.sourceDimension=be.targetDimension=dimension;be.sourcePosition=source.asLong();be.targetPosition=target.asLong();be.sourceFace=Direction.byName(data.getStringOr("fluid_source_face","down"));be.targetFace=Direction.byName(data.getStringOr("fluid_target_face","up"));be.setChanged();data.remove("fluid_source");data.remove("fluid_target");player.sendOverlayMessage(Component.literal("供液源与目标已绑定"));return;
        }
        if(player.isShiftKeyDown()){data.remove("fluid_source");data.remove("fluid_target");player.sendOverlayMessage(Component.literal("已清除待绑定液体端口"));return;}
        if(level.getCapability(Capabilities.Fluid.BLOCK,c.getClickedPos(),c.getClickedFace())==null)throw new IllegalArgumentException("此面没有标准液体接口");
        if(!dimension.equals(data.getStringOr("fluid_dimension",""))){data.remove("fluid_source");data.remove("fluid_target");}
        data.putString("fluid_dimension",dimension);
        String key=data.contains("fluid_source")?"fluid_target":"fluid_source";data.putLong(key,c.getClickedPos().asLong());data.putString(key+"_face",c.getClickedFace().getName());
        player.sendOverlayMessage(Component.literal(key.equals("fluid_source")?"已选源出口，请选择目标入口":"已选目标入口，请点击供液水晶"));
    }
}
