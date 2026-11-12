package cn.yumis.fight.modules.npc.application;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.npc.api.*;
import cn.yumis.fight.modules.presentation.api.ModText;
import com.google.gson.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

public final class NpcService {
    private final Journal journal;
    public NpcService(Journal journal){this.journal=journal;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("npc","npcAction",(p,j)->{
        var entity=p.level().getEntity(UUID.fromString(j.get("entity").getAsString()));
        if(entity instanceof LivingEntity living&&living.isAlive())NpcInteractionComponent.execute(p,living,j.get("action").getAsString());
    });}
    private static boolean standing(ServerLevel level,Vec3 pos){
        var below=BlockPos.containing(pos.add(0,-.01,0));
        return level.getBlockState(below).isFaceSturdy(level,below,Direction.UP)&&level.noCollision(new AABB(pos.x-.3,pos.y,pos.z-.3,pos.x+.3,pos.y+1.8,pos.z+.3));
    }
    private NpcEntity create(ServerLevel level,String definition,String skin,Vec3 pos,float yaw,UUID id){
        if(!standing(level,pos))throw new IllegalArgumentException("NPC位置需要实心承托和两格净空");
        var entity=NpcContentTypes.NPC.get().create(level,EntitySpawnReason.COMMAND);if(entity==null)throw new IllegalStateException("无法创建NPC");
        entity.setUUID(id);entity.setPos(pos);entity.setYRot(yaw);entity.setYHeadRot(yaw);entity.yBodyRot=yaw;entity.initialize(definition,skin);
        if(!level.addFreshEntity(entity))throw new IllegalStateException("NPC创建被取消");return entity;
    }
    public int group(ServerLevel level,String group,List<NpcSpawns.Anchor> anchors,List<String> definitions,long seed){
        if(definitions.stream().allMatch(id->!journal.get("npcSpawn/"+group+"/"+id).isJsonNull()))return 0;
        var points=new ArrayList<>(anchors.stream().filter(a->standing(level,a.position())).toList());
        if(points.size()<definitions.size())throw new IllegalArgumentException("NPC投放组可用经营位不足："+group);
        var skins=new ArrayList<>(NpcDefinitions.current().skins.keySet());if(skins.size()<definitions.size())throw new IllegalArgumentException("NPC皮肤池不足以无重复投放");
        var random=new Random(seed);Collections.shuffle(points,random);Collections.shuffle(skins,random);var types=new ArrayList<>(definitions);Collections.shuffle(types,random);int count=0;
        for(int i=0;i<types.size();i++){
            String definition=types.get(i),key="npcSpawn/"+group+"/"+definition;
            if(!journal.get(key).isJsonNull())continue;
            UUID id=UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
            if(level.getEntity(id)==null){var anchor=points.get(i);create(level,definition,skins.get(i),anchor.position(),anchor.yaw(),id);}
            try{journal.put(key,new JsonPrimitive(id.toString()));}catch(Exception failure){throw new IllegalStateException("NPC投放记录未完成",failure);}count++;
        }
        return count;
    }
    public static void commands(LiteralArgumentBuilder<CommandSourceStack> root,Supplier<NpcService> service){
        var npc=Commands.literal("npc").requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
        var definition=Commands.argument("definition",StringArgumentType.word()).suggests((context,builder)->{NpcDefinitions.current().npcs.keySet().forEach(builder::suggest);return builder.buildFuture();});
        definition.executes(context->spawn(context.getSource(),service.get(),StringArgumentType.getString(context,"definition"),context.getSource().getPosition()));
        definition.then(Commands.argument("position",Vec3Argument.vec3()).executes(context->spawn(context.getSource(),service.get(),StringArgumentType.getString(context,"definition"),Vec3Argument.getVec3(context,"position"))));
        npc.then(Commands.literal("spawn").then(definition));
        npc.then(Commands.literal("attach").then(Commands.argument("entity",EntityArgument.entity()).then(Commands.argument("definition",StringArgumentType.word()).suggests((context,builder)->{NpcDefinitions.current().npcs.keySet().forEach(builder::suggest);return builder.buildFuture();}).executes(context->{
            var entity=EntityArgument.getEntity(context,"entity");if(!(entity instanceof LivingEntity living))throw new IllegalArgumentException("仅可挂载到生物");NpcInteractionComponent.attach(living,StringArgumentType.getString(context,"definition"));context.getSource().sendSuccess(()->ModText.of("已挂载NPC交互"),true);return 1;
        }))));
        npc.then(Commands.literal("detach").then(Commands.argument("entity",EntityArgument.entity()).executes(context->{var entity=EntityArgument.getEntity(context,"entity");if(entity instanceof LivingEntity living)NpcInteractionComponent.detach(living);return 1;})));
        npc.then(Commands.literal("remove").then(Commands.argument("entity",EntityArgument.entity()).executes(context->{var entity=EntityArgument.getEntity(context,"entity");if(!(entity instanceof NpcEntity))throw new IllegalArgumentException("仅可删除NPC实体");entity.discard();return 1;})));
        root.then(npc);
    }
    private static int spawn(CommandSourceStack source,NpcService service,String definition,Vec3 pos){
        NpcDefinitions.current().require(definition);var skins=new ArrayList<>(NpcDefinitions.current().skins.keySet());
        service.create(source.getLevel(),definition,skins.get(source.getLevel().getRandom().nextInt(skins.size())),pos,0,UUID.randomUUID());source.sendSuccess(()->ModText.of("已生成"+NpcDefinitions.current().require(definition).name()),true);return 1;
    }
}
