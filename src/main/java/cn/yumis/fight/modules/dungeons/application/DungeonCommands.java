package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.dungeons.api.AwakeningTower;
import cn.yumis.fight.modules.dungeons.api.RitualBossCatalog;
import cn.yumis.fight.modules.dungeons.api.TowerClimbRules;
import cn.yumis.fight.modules.dungeons.api.TreasureState;
import cn.yumis.fight.modules.dungeons.content.TreasureThemes;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.platform.ItemData;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.util.List;
import java.util.function.Supplier;

/** Admin commands owned by the dungeons module: goblin kingdom portal, awakening tower gates, and demon altar fast-summon. */
public final class DungeonCommands {
    /** Walking-face height after the wild ruin templates are compiled with terrain-retaining foundations. */
    static final int GATE_FLOOR_OFFSET=4;
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS;
    private static final List<String> FACINGS=List.of("south","west","north","east");

    private DungeonCommands(){}

    public static void commands(LiteralArgumentBuilder<CommandSourceStack> root,Supplier<DungeonService> service,Supplier<RitualEncounterService> rituals){
        var portal=Commands.literal("portal").requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
        portal.executes(c->{openPortal(c.getSource(),service.get(),c.getSource().getPlayerOrException().blockPosition());return 1;});
        portal.then(Commands.argument("position",BlockPosArgument.blockPos()).executes(c->{openPortal(c.getSource(),service.get(),BlockPosArgument.getBlockPos(c,"position"));return 1;}));

        var treasureThemeArg=Commands.argument("theme",StringArgumentType.word()).suggests((context,builder)->{
            for(var theme:TreasureThemes.ROTATION)builder.suggest(theme.id(),ModText.of(theme.displayName()));
            builder.suggest(TreasureThemes.PALACE_THEME,ModText.of("黄金王宫（第 5 区 · 吉尔伽美什）"));
            return builder.buildFuture();
        });
        var treasureEnterTheme=treasureThemeArg.executes(c->{enterTreasure(c.getSource(),service.get(),IntegerArgumentType.getInteger(c,"region"),StringArgumentType.getString(c,"theme"));return 1;});
        var treasureEnterRegion=Commands.argument("region",IntegerArgumentType.integer(1,5))
            .executes(c->{enterTreasure(c.getSource(),service.get(),IntegerArgumentType.getInteger(c,"region"),"");return 1;})
            .then(treasureEnterTheme);
        var treasureEnter=Commands.literal("enter")
            .requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
            .executes(c->{enterTreasure(c.getSource(),service.get(),1,"");return 1;})
            .then(treasureEnterRegion);

        var treasureNextTheme=Commands.argument("theme",StringArgumentType.word()).suggests((context,builder)->{
            for(var theme:TreasureThemes.ROTATION)builder.suggest(theme.id(),ModText.of(theme.displayName()));
            builder.suggest(TreasureThemes.PALACE_THEME,ModText.of("黄金王宫（第 5 区 · 吉尔伽美什）"));
            return builder.buildFuture();
        }).executes(c->{nextTreasure(c.getSource(),service.get(),StringArgumentType.getString(c,"theme"));return 1;});
        var treasureNext=Commands.literal("next")
            .requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
            .executes(c->{nextTreasure(c.getSource(),service.get(),"");return 1;})
            .then(treasureNextTheme);

        var treasureGuaranteeMode=Commands.argument("mode",StringArgumentType.word()).suggests((context,builder)->{
            builder.suggest("on",ModText.of("开启：走门 100% 直接进入下一层，主世界 100% 开门"));
            builder.suggest("rainbow",ModText.of("彩虹破门：走门 100% 触发彩虹破门救场进入下一层"));
            builder.suggest("off",ModText.of("关闭：恢复正常概率"));
            return builder.buildFuture();
        }).executes(c->{setTreasureGuarantee(c.getSource(),service.get(),StringArgumentType.getString(c,"mode"));return 1;});
        var treasureGuarantee=Commands.literal("guarantee")
            .requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
            .executes(c->{toggleTreasureGuarantee(c.getSource(),service.get());return 1;})
            .then(treasureGuaranteeMode);

        // The "treasure" literal merges into the existing ydnf treasure node; permissions live on each subcommand.
        root.then(Commands.literal("treasure").then(portal).then(treasureEnter).then(treasureNext).then(treasureGuarantee));

        var facing=Commands.argument("facing",StringArgumentType.word()).suggests((context,builder)->{FACINGS.forEach(builder::suggest);return builder.buildFuture();});
        var position=Commands.argument("position",BlockPosArgument.blockPos());
        var tower=Commands.argument("tower",StringArgumentType.word()).suggests((context,builder)->{for(var value:AwakeningTower.values())builder.suggest(value.template());return builder.buildFuture();});
        facing.executes(c->{placeGate(c.getSource(),tower(StringArgumentType.getString(c,"tower")),BlockPosArgument.getBlockPos(c,"position"),StringArgumentType.getString(c,"facing"));return 1;});
        position.executes(c->{placeGate(c.getSource(),tower(StringArgumentType.getString(c,"tower")),BlockPosArgument.getBlockPos(c,"position"),"south");return 1;});
        tower.executes(c->{placeGate(c.getSource(),tower(StringArgumentType.getString(c,"tower")),c.getSource().getPlayerOrException().blockPosition(),"south");return 1;});
        position.then(facing);
        tower.then(position);
        var gate=Commands.literal("gate").requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)).then(tower);
        var enterFloor=Commands.argument("floor",IntegerArgumentType.integer(1,TowerClimbRules.FLOOR_COUNT))
            .executes(c->{enterTower(c.getSource(),service.get(),tower(StringArgumentType.getString(c,"tower")),IntegerArgumentType.getInteger(c,"floor"));return 1;});
        var enterTower=Commands.argument("tower",StringArgumentType.word()).suggests((context,builder)->{for(var value:AwakeningTower.values())builder.suggest(value.template());return builder.buildFuture();})
            .executes(c->{enterTower(c.getSource(),service.get(),tower(StringArgumentType.getString(c,"tower")),TowerClimbRules.FLOOR_COUNT);return 1;})
            .then(enterFloor);
        var enter=Commands.literal("enter").requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)).then(enterTower);
        var floor=Commands.literal("floor").requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.argument("floor",IntegerArgumentType.integer(1,TowerClimbRules.FLOOR_COUNT))
                .executes(c->{jumpFloor(c.getSource(),service.get(),IntegerArgumentType.getInteger(c,"floor"));return 1;}));
        root.then(Commands.literal("tower").requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)).then(gate).then(enter).then(floor));

        var altarBoss=Commands.argument("boss",StringArgumentType.word())
            .suggests((context,builder)->{
                for(var boss:RitualBossCatalog.current().ordered())
                    builder.suggest(boss.id(),ModText.of(boss.name()+" (Lv."+boss.level()+")"));
                return builder.buildFuture();
            })
            .executes(c->summonAltar(c.getSource(),rituals.get(),StringArgumentType.getString(c,"boss"),null))
            .then(Commands.argument("position",BlockPosArgument.blockPos())
                .executes(c->summonAltar(c.getSource(),rituals.get(),StringArgumentType.getString(c,"boss"),BlockPosArgument.getBlockPos(c,"position"))));
        var altarSummon=Commands.literal("summon").then(altarBoss);
        var altarCancel=Commands.literal("cancel").executes(c->cancelAltar(c.getSource(),rituals.get()));
        root.then(Commands.literal("altar")
            .requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
            .then(altarSummon)
            .then(altarCancel));
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

    private static void enterTreasure(CommandSourceStack source,DungeonService service,int region,String theme){
        try{
            if(service.treasure==null)throw new IllegalStateException("挖宝服务尚未启用");
            TreasureState state=service.treasure.enterForTest(source.getPlayerOrException(),region,theme);
            source.sendSuccess(()->ModText.of("已进入挖宝秘境测试：第 "+state.region+" 区【"+TreasureThemes.displayName(state.theme)+"】。"),true);
        }catch(Exception failure){source.sendFailure(ModText.of("进入挖宝秘境失败："+failure.getMessage()));}
    }

    private static void nextTreasure(CommandSourceStack source,DungeonService service,String theme){
        try{
            if(service.treasure==null)throw new IllegalStateException("挖宝服务尚未启用");
            TreasureState state=service.treasure.forceNextRegion(source.getPlayerOrException(),theme);
            source.sendSuccess(()->ModText.of("已必定进入挖宝秘境下一层：第 "+state.region+" 区【"+TreasureThemes.displayName(state.theme)+"】。"),true);
        }catch(Exception failure){source.sendFailure(ModText.of("进入挖宝下一层失败："+failure.getMessage()));}
    }

    private static void toggleTreasureGuarantee(CommandSourceStack source,DungeonService service){
        try{
            if(service.treasure==null)throw new IllegalStateException("挖宝服务尚未启用");
            var next=service.treasure.portalGuarantee()==TreasureHuntService.PortalGuarantee.OFF
                ?TreasureHuntService.PortalGuarantee.DIRECT
                :TreasureHuntService.PortalGuarantee.OFF;
            service.treasure.setPortalGuarantee(next);
            source.sendSuccess(()->ModText.of(guaranteeMessage(next)),true);
        }catch(Exception failure){source.sendFailure(ModText.of(failure.getMessage()));}
    }

    private static void setTreasureGuarantee(CommandSourceStack source,DungeonService service,String modeText){
        try{
            if(service.treasure==null)throw new IllegalStateException("挖宝服务尚未启用");
            var mode=parseGuaranteeMode(modeText);
            service.treasure.setPortalGuarantee(mode);
            source.sendSuccess(()->ModText.of(guaranteeMessage(mode)),true);
        }catch(Exception failure){source.sendFailure(ModText.of(failure.getMessage()));}
    }

    static TreasureHuntService.PortalGuarantee parseGuaranteeMode(String modeText){
        return switch(modeText==null?"":modeText.trim().toLowerCase(java.util.Locale.ROOT)){
            case "on","true","direct","1"->TreasureHuntService.PortalGuarantee.DIRECT;
            case "rainbow","rescue"->TreasureHuntService.PortalGuarantee.RAINBOW;
            case "off","false","0"->TreasureHuntService.PortalGuarantee.OFF;
            default->throw new IllegalArgumentException("未知模式："+modeText+"（可用：on、rainbow、off）");
        };
    }

    private static String guaranteeMessage(TreasureHuntService.PortalGuarantee mode){
        return switch(mode){
            case DIRECT->"挖宝必定进入下一层：已开启（主世界 100% 开门，秘境走门 100% 直接进入下一层）。";
            case RAINBOW->"挖宝必定进入下一层：彩虹破门模式（主世界 100% 开门，秘境走门 100% 触发彩虹破门救场进入下一层）。";
            case OFF->"挖宝必定进入下一层：已关闭（恢复正常概率）。";
        };
    }

    private static void placeGate(CommandSourceStack source,AwakeningTower tower,BlockPos at,String facing){
        try{
            ServerLevel level=source.getLevel();
            if(level.dimension()!=Level.OVERWORLD)throw new IllegalArgumentException("塔门只能生成在主世界");
            if(!level.getWorldBorder().isWithinBounds(at))throw new IllegalArgumentException("坐标超出世界边界，请换一个位置");
            // The given Y is the walking height of the approach, as in wild generation.
            BlockPos origin=new BlockPos(at.getX(),at.getY()-GATE_FLOOR_OFFSET,at.getZ());
            var id=Identifier.fromNamespaceAndPath(ItemData.NS,"wild_buildings/awakening_tower_"+tower.template());
            StructureTemplate template=level.getStructureManager().get(id).orElseThrow(()->new IllegalArgumentException("缺少塔门结构："+id));
            var settings=new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true).setRotation(rotation(facing));
            if(!template.placeInWorld(level,origin,origin,settings,level.getRandom(),FLAGS))throw new IllegalStateException("塔门放置失败："+tower.dungeon().name());
            source.sendSuccess(()->ModText.of(tower.dungeon().name()+"入口遗迹已在 ("+at.getX()+", "+at.getY()+", "+at.getZ()+") 放置，朝向"+facing+"。右键中央方尖碑即可进入试炼。"),false);
        }catch(Exception failure){source.sendFailure(ModText.of("塔门未生成: "+failure.getMessage()));}
    }

    private static void enterTower(CommandSourceStack source,DungeonService service,AwakeningTower tower,int floor){
        try{
            service.enterTowerAt(source.getPlayerOrException(),tower,floor);
            source.sendSuccess(()->ModText.of("已进入"+tower.dungeon().name()+"第 "+floor+" 层（跳过本次选牌，检查点同步设为第 "+floor+" 层）。"),true);
        }catch(Exception failure){source.sendFailure(ModText.of("进入觉醒塔失败："+failure.getMessage()));}
    }

    private static void jumpFloor(CommandSourceStack source,DungeonService service,int floor){
        try{
            service.jumpTowerFloor(source.getPlayerOrException(),floor);
            source.sendSuccess(()->ModText.of("已切换至觉醒塔第 "+floor+" 层（检查点同步设为第 "+floor+" 层）。"),true);
        }catch(Exception failure){source.sendFailure(ModText.of("切换觉醒塔层数失败："+failure.getMessage()));}
    }

    private static int summonAltar(CommandSourceStack source,RitualEncounterService rituals,String bossId,BlockPos position){
        try{
            if(rituals==null)throw new IllegalStateException("魔王祭坛服务尚未启用");
            ServerPlayer player=source.getEntity() instanceof ServerPlayer found?found:null;
            BlockPos at=position!=null?position:source.getPlayerOrException().blockPosition();
            var run=rituals.quickSummon(source.getLevel(),at,position==null,player,bossId);
            var boss=rituals.catalog(run);
            source.sendSuccess(()->ModText.of(
                "已快速召唤魔王祭坛首领："+boss.name()+"（Lv."+boss.level()+"），战圈中心 ("
                    +run.altar.getX()+", "+run.altar.getY()+", "+run.altar.getZ()+")，半径 "+run.radius+" 格。"),true);
            return 1;
        }catch(Exception failure){
            source.sendFailure(ModText.of("魔王祭坛召唤失败："+failure.getMessage()));
            return 0;
        }
    }

    private static int cancelAltar(CommandSourceStack source,RitualEncounterService rituals){
        try{
            if(rituals==null)throw new IllegalStateException("魔王祭坛服务尚未启用");
            ServerPlayer player=source.getEntity() instanceof ServerPlayer found?found:null;
            var run=rituals.cancelAdmin(source.getLevel(),player);
            var boss=rituals.catalog(run);
            source.sendSuccess(()->ModText.of(
                "已结束魔王祭坛场次："+boss.name()+"（中心 "
                    +run.altar.getX()+", "+run.altar.getY()+", "+run.altar.getZ()+"）。"),true);
            return 1;
        }catch(Exception failure){
            source.sendFailure(ModText.of("结束魔王祭坛场次失败："+failure.getMessage()));
            return 0;
        }
    }
}
