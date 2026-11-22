package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.dungeons.api.AwakeningTower;
import cn.yumis.fight.modules.dungeons.api.TreasureState;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.platform.ItemData;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.util.List;
import java.util.function.Supplier;

/** Admin commands owned by the dungeons module: the goblin kingdom entrance and awakening tower gates. */
public final class DungeonCommands {
    /** Walking-face height inside the compiled wild gate structures; authored as referenceY in tools/wild_buildings.json. */
    static final int GATE_FLOOR_OFFSET=3;
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS;
    private static final List<String> FACINGS=List.of("south","west","north","east");

    private DungeonCommands(){}

    public static void commands(LiteralArgumentBuilder<CommandSourceStack> root,Supplier<DungeonService> service){
        var portal=Commands.literal("portal").requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
        portal.executes(c->{openPortal(c.getSource(),service.get(),c.getSource().getPlayerOrException().blockPosition());return 1;});
        portal.then(Commands.argument("position",BlockPosArgument.blockPos()).executes(c->{openPortal(c.getSource(),service.get(),BlockPosArgument.getBlockPos(c,"position"));return 1;}));
        // The "treasure" literal merges into the existing ydnf treasure node; the permission lives on "portal" itself.
        root.then(Commands.literal("treasure").then(portal));

        var facing=Commands.argument("facing",StringArgumentType.word()).suggests((context,builder)->{FACINGS.forEach(builder::suggest);return builder.buildFuture();});
        var position=Commands.argument("position",BlockPosArgument.blockPos());
        var tower=Commands.argument("tower",StringArgumentType.word()).suggests((context,builder)->{for(var value:AwakeningTower.values())builder.suggest(value.template());return builder.buildFuture();});
        facing.executes(c->{placeGate(c.getSource(),tower(StringArgumentType.getString(c,"tower")),BlockPosArgument.getBlockPos(c,"position"),StringArgumentType.getString(c,"facing"));return 1;});
        position.executes(c->{placeGate(c.getSource(),tower(StringArgumentType.getString(c,"tower")),BlockPosArgument.getBlockPos(c,"position"),"south");return 1;});
        tower.executes(c->{placeGate(c.getSource(),tower(StringArgumentType.getString(c,"tower")),c.getSource().getPlayerOrException().blockPosition(),"south");return 1;});
        position.then(facing);
        tower.then(position);
        var gate=Commands.literal("gate").requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)).then(tower);
        root.then(Commands.literal("tower").requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)).then(gate));
    }

    static Rotation rotation(String facing){
        return switch(facing){
            case "south"->Rotation.NONE;
            case "west"->Rotation.CLOCKWISE_90;
            case "north"->Rotation.CLOCKWISE_180;
            case "east"->Rotation.COUNTERCLOCKWISE_90;
            default->throw new IllegalArgumentException("未知朝向，可用："+String.join("、",FACINGS));
        };
    }

    static AwakeningTower tower(String id){
        for(var tower:AwakeningTower.values())if(tower.template().equals(id))return tower;
        throw new IllegalArgumentException("未知觉醒塔："+id);
    }

    private static void openPortal(CommandSourceStack source,DungeonService service,BlockPos at){
        try{
            if(service.treasure==null)throw new IllegalStateException("挖宝服务尚未启用");
            TreasureState state=service.treasure.openPortal(source.getPlayerOrException(),at);
            source.sendSuccess(()->ModText.of("哥布林王国入口已生成：X:"+state.mapX+" Y:"+(int)state.mapY+" Z:"+state.mapZ+"，30分钟后关闭。"),true);
        }catch(Exception failure){source.sendFailure(ModText.of(failure.getMessage()));}
    }

    private static void placeGate(CommandSourceStack source,AwakeningTower tower,BlockPos at,String facing){
        try{
            ServerLevel level=source.getLevel();
            if(level.dimension()!=Level.OVERWORLD)throw new IllegalArgumentException("塔门只能生成在主世界");
            if(!level.getWorldBorder().isWithinBounds(at))throw new IllegalArgumentException("坐标超出世界边界，请换一个位置");
            // The given Y is the walking floor, the same level wild gates keep at the first free terrain height.
            BlockPos origin=new BlockPos(at.getX(),at.getY()-GATE_FLOOR_OFFSET,at.getZ());
            var id=Identifier.fromNamespaceAndPath(ItemData.NS,"wild_buildings/awakening_tower_"+tower.template());
            StructureTemplate template=level.getStructureManager().get(id).orElseThrow(()->new IllegalArgumentException("缺少塔门结构："+id));
            var settings=new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true).setRotation(rotation(facing));
            if(!template.placeInWorld(level,origin,origin,settings,level.getRandom(),FLAGS))throw new IllegalStateException("塔门放置失败："+tower.dungeon().name());
            source.sendSuccess(()->ModText.of(tower.dungeon().name()+"的塔门已放置，行走面在 X:"+at.getX()+" Y:"+at.getY()+" Z:"+at.getZ()+"，朝向"+facing+"；右键门前石碑即可进入试炼。"),false);
        }catch(Exception failure){source.sendFailure(ModText.of("塔门未生成: "+failure.getMessage()));}
    }
}
