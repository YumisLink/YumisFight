package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.*;
import cn.yumis.fight.network.Network;
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
import java.util.*;

/** Two explicit interaction modes. Selection state belongs to the held wand, never to the world. */
public final class ManaWand extends Item {
    public ManaWand(Properties properties){super(properties);}
    public static CompoundTag data(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private static void save(ItemStack stack,CompoundTag data){stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));}
    private static CompoundTag reset(int mode){var next=new CompoundTag();next.putInt("wand_mode",mode);return next;}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return useOn(context);}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(!level.isClientSide()){var stack=player.getItemInHand(hand);var state=data(stack);int mode=Math.floorMod(state.getIntOr("wand_mode",0),2);if(player.isShiftKeyDown())mode=1-mode;save(stack,reset(mode));player.sendOverlayMessage(Component.literal(player.isShiftKeyDown()?(mode==0?"移动":"联网"):"已取消当前选择"));}return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();if(player==null||!(context.getLevel() instanceof ServerLevel level))return InteractionResult.SUCCESS;
        if(!level.mayInteract(player,context.getClickedPos()))return InteractionResult.FAIL;var stack=context.getItemInHand();var state=data(stack);int mode=Math.floorMod(state.getIntOr("wand_mode",0),2);
        String dimension=level.dimension().identifier().toString();if(!dimension.equals(state.getStringOr("dimension",dimension)))state=reset(mode);state.putString("dimension",dimension);
        try{if(mode==0)move(context,state);else network(context,state);save(stack,state);return InteractionResult.SUCCESS;}
        catch(IllegalArgumentException error){player.sendOverlayMessage(Component.literal(error.getMessage()));return InteractionResult.FAIL;}
    }
    private static void move(UseOnContext context,CompoundTag state){
        var level=(ServerLevel)context.getLevel();var player=context.getPlayer();var position=ArtificialStarPartBlock.base(level,WindGeneratorPartBlock.base(level,context.getClickedPos()));var raw=level.getBlockEntity(position);
        if(raw instanceof MachineBlockEntity crystal&&(crystal.device().has(DeviceType.Capability.LOGISTICS)||crystal.device().has(DeviceType.Capability.FLUID_LOGISTICS))){state.putString("crystal",crystal.identity.toString());state.putLong("crystal_pos",position.asLong());state.remove("source");player.sendOverlayMessage(Component.literal("已绑定水晶，请选择来源容器（"+crystal.routes.size()+"/"+crystal.routeLimit()+"）"));return;}
        var crystal=IndustrialWorld.get(level).loaded.get(state.getStringOr("crystal",""));if(crystal==null||crystal.getLevel()!=level)throw new IllegalArgumentException("请先右键绑定一个移物或供液水晶");
        if(crystal.routes.size()>=crystal.routeLimit())throw new IllegalArgumentException("配对已满，请在水晶面板删除一对后再绑定");
        boolean source=!state.contains("source"),fluid=crystal.device().has(DeviceType.Capability.FLUID_LOGISTICS);Direction face=CrystalTransfers.portFace(level,position,context.getClickedFace(),source,fluid);
        var fluidPort=fluid?level.getCapability(Capabilities.Fluid.BLOCK,position,face):null;var itemPort=fluid?null:level.getCapability(Capabilities.Item.BLOCK,position,face);boolean valid=fluid?fluidPort!=null&&fluidPort.size()>0:itemPort!=null&&itemPort.size()>0;if(!valid)throw new IllegalArgumentException((source?"来源":"目标")+"没有可用的"+(fluid?"液体":"物品")+(source?"输出口":"输入口"));
        double range=IndustrialRules.current().transferRanges[crystal.tier()-1];if(crystal.tier()<3&&crystal.getBlockPos().distSqr(position)>range*range)throw new IllegalArgumentException("容器超出水晶传输范围");
        if(source){state.putLong("source",position.asLong());state.putString("source_face",face.getName());player.sendOverlayMessage(Component.literal("已选择来源"+(fluid?"出液口":"物品输出口")+"，请右键目标容器"));return;}
        BlockPos from=BlockPos.of(state.getLongOr("source",0));if(!level.hasChunkAt(from)||!level.mayInteract(player,from))throw new IllegalArgumentException("来源容器不可用");
        if(CrystalTransfers.sameContainer(level,from,position))throw new IllegalArgumentException("来源和目标不能是同一容器");
        var route=new CrystalRoute(level.dimension().identifier().toString(),from.asLong(),position.asLong(),Direction.byName(state.getStringOr("source_face","down")),face);
        if(crystal.routes.stream().anyMatch(r->r.same(route)))throw new IllegalArgumentException("这对容器已经绑定");
        crystal.routes.add(route);crystal.setChanged();state.remove("source");player.sendOverlayMessage(Component.literal("配对完成（"+crystal.routes.size()+"/"+crystal.routeLimit()+"），可继续选择下一对来源"));
    }
    private static void network(UseOnContext context,CompoundTag state){
        var level=(ServerLevel)context.getLevel();var player=context.getPlayer();var raw=level.getBlockEntity(ArtificialStarPartBlock.base(level,WindGeneratorPartBlock.base(level,context.getClickedPos())));if(!(raw instanceof MachineBlockEntity second))return;
        var world=IndustrialWorld.get(level);String selected=state.getStringOr("first","");
        if(selected.isEmpty()){state.putString("first",second.identity.toString());state.putLong("first_pos",second.getBlockPos().asLong());player.sendOverlayMessage(Component.literal("已选择方块，请选择信标或设备"));return;}
        var first=world.loaded.get(selected);if(first==null||first.getLevel()!=level)throw new IllegalArgumentException("第一次选择的方块已卸载，请点击空气取消");
        boolean a=first.device().has(DeviceType.Capability.STORAGE_NETWORK),b=second.device().has(DeviceType.Capability.STORAGE_NETWORK);
        if(a&&b)world.link(first,second);else if(a!=b)world.bind(a?first:second,a?second:first);else return;
        state.remove("first");state.remove("first_pos");Network.send((net.minecraft.server.level.ServerPlayer)player,"industrial_lines",world.connectionPreview(second));player.sendOverlayMessage(Component.literal("联网关系已更新"));
    }
    public static List<String> information(ItemStack stack){var state=data(stack);boolean network=state.getIntOr("wand_mode",0)==1;
        var lines=new ArrayList<String>();lines.add("魔力传输权杖 · "+(network?"联网":"移动"));
        lines.add(network?state.contains("first_pos")?"已选 "+BlockPos.of(state.getLongOr("first_pos",0)).toShortString()+" → 请选择下一方块":"依次选择两座信标，或信标与机器":!state.contains("crystal_pos")?"先右键水晶，再选择来源 → 目标":state.contains("source")?"来源 "+BlockPos.of(state.getLongOr("source",0)).toShortString()+" → 请选择目标":"水晶 "+BlockPos.of(state.getLongOr("crystal_pos",0)).toShortString()+" · 请选择来源");
        lines.add("Shift右键空气切换 · 右键空气取消选择");return lines;
    }
    public static List<BlockPos> selections(ItemStack stack,String dimension){var state=data(stack);if(!dimension.equals(state.getStringOr("dimension","")))return List.of();var result=new ArrayList<BlockPos>();for(String key:List.of("crystal_pos","source","first_pos"))if(state.contains(key))result.add(BlockPos.of(state.getLongOr(key,0)));return result;}
}
