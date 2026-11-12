package cn.yumis.fight.modules.mapgeneration.api;

import cn.yumis.fight.modules.mapgeneration.application.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import java.util.*;

/** Public application entrypoints. Map geometry and content are resource data, not village branches. */
public final class MapGeneration {
    private static final Map<MinecraftServer,GenerationService> SERVICES=new WeakHashMap<>();
    private MapGeneration() {}
    public static void registerConfig(net.neoforged.fml.ModContainer container) {MapGenerationConfig.register(container);}
    private static GenerationService service(MinecraftServer server) {return SERVICES.computeIfAbsent(server,GenerationService::new);}
    public static void spawnCreated(LevelAccessor accessor) {
        if(accessor instanceof ServerLevel level&&level.dimension()==Level.OVERWORLD)GenerationStore.newWorld(level.getServer());
    }
    public static void started(MinecraftServer server) {
        if(!GenerationStore.consumeNewWorld(server))return;
        try {
            if(!MapGenerationConfig.generateStarterVillage()) {
                LogUtils.getLogger().info("ydcore.toml 已关闭新世界出生新手村生成");return;
            }
            service(server).generate(MapGenerationConfig.starterVillageBlueprint(),server.overworld(),server.overworld().getRespawnData().pos(),true,s->{});
        }
        catch(Exception e){LogUtils.getLogger().error("新世界地图生成未完成：{}",e.getMessage(),e);}
    }
    public static void tick(MinecraftServer server) {var current=SERVICES.get(server);if(current!=null)current.tick();}
    public static void stopped(MinecraftServer server) {var current=SERVICES.remove(server);if(current!=null)current.close();}
    public static String audit(MinecraftServer server) {return service(server).audit();}
    public static String inspect(MinecraftServer server) {return service(server).inspect();}
    public static boolean blocksHostileSpawn(net.minecraft.world.entity.Entity entity,Level level) {
        return level instanceof ServerLevel serverLevel&&GenerationService.hostile(entity)&&service(serverLevel.getServer()).blocksHostiles(serverLevel,entity.blockPosition().getX(),entity.blockPosition().getZ());
    }
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        var root=Commands.literal("map").requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
        for(String action:List.of("plan","generate")) {
            var id=Commands.argument("blueprint",IdentifierArgument.id());
            id.executes(c->execute(c.getSource(),action,IdentifierArgument.getId(c,"blueprint"),c.getSource().getPlayerOrException().blockPosition()));
            id.then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(c->execute(c.getSource(),action,IdentifierArgument.getId(c,"blueprint"),BlockPosArgument.getBlockPos(c,"origin"))));
            root.then(Commands.literal(action).then(id));
        }
        root.then(Commands.literal("audit").executes(c->{c.getSource().sendSuccess(()->Component.literal(audit(c.getSource().getServer())),false);return 1;}));
        return root;
    }
    private static int execute(CommandSourceStack source,String action,Identifier id,BlockPos origin) {
        try {
            var service=service(source.getServer());
            if(action.equals("plan")) {String result=service.plan(id,source.getLevel(),origin).summary();source.sendSuccess(()->Component.literal(result),false);}
            else {service.generate(id,source.getLevel(),origin,false,s->source.sendSuccess(()->Component.literal(s),false));source.sendSuccess(()->Component.literal("地图结构已放置，正在完成原生光照检查。"),false);}
            return 1;
        } catch(Exception e) {LogUtils.getLogger().error("地图生成失败",e);source.sendFailure(Component.literal("地图未完成: "+e.getMessage()));return 0;}
    }
}
