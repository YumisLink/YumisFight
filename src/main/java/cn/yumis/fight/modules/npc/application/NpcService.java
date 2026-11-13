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
    private record Conversation(ServerPlayer player,UUID entity,long until) {}
    private final Map<UUID,Conversation> conversations=new HashMap<>();
    public NpcService(Journal journal){this.journal=journal;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("npc","npcAction",(p,j)->{
        var entity=p.level().getEntity(UUID.fromString(j.get("entity").getAsString()));
        if(entity instanceof LivingEntity living&&living.isAlive())NpcInteractionComponent.execute(p,living,j.get("action").getAsString());
    });router.register("npc","npcDialogue",(p,j)->{
        if(!j.get("open").getAsBoolean()){conversations.remove(p.getUUID());return;}
        var entity=p.level().getEntity(UUID.fromString(j.get("entity").getAsString()));
        if(!(entity instanceof LivingEntity living)||!living.isAlive()||NpcInteractionComponent.query(living)==null||p.distanceToSqr(living)>64)return;
        conversations.put(p.getUUID(),new Conversation(p,living.getUUID(),System.currentTimeMillis()+5000));
    });}
    public boolean conversing(NpcEntity entity){
        long now=System.currentTimeMillis();
        conversations.values().removeIf(c->c.until()<now||c.player().hasDisconnected()||!c.player().isAlive()||c.player().level()!=entity.level()&&c.entity().equals(entity.getUUID()));
        return conversations.values().stream().anyMatch(c->c.entity().equals(entity.getUUID())&&c.player().level()==entity.level()&&c.player().distanceToSqr(entity)<=64);
    }
    private String spawnKey(String group,String definition){return "npcSpawn/"+(NpcDefinitions.current().require(definition).unique()?"unique":group)+"/"+definition;}
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
        if(definitions.stream().allMatch(id->!journal.get(spawnKey(group,id)).isJsonNull()))return 0;
        var points=new ArrayList<>(anchors.stream().filter(a->standing(level,a.position())).toList());
        if(points.size()<definitions.size())throw new IllegalArgumentException("NPC投放组可用经营位不足："+group);
        var skins=new ArrayList<>(NpcDefinitions.current().randomSkins());long randomNeeded=definitions.stream().filter(id->{var skin=NpcDefinitions.current().require(id).skin();return skin==null||skin.isBlank();}).count();if(skins.size()<randomNeeded)throw new IllegalArgumentException("NPC皮肤池不足以无重复投放");
        var random=new Random(seed);Collections.shuffle(points,random);Collections.shuffle(skins,random);var types=new ArrayList<>(definitions);Collections.shuffle(types,random);int count=0;
        int skinIndex=0;for(int i=0;i<types.size();i++){
            String definition=types.get(i),key=spawnKey(group,definition);var fixed=NpcDefinitions.current().require(definition).skin();String skin=fixed==null||fixed.isBlank()?skins.get(skinIndex++):fixed;
            if(!journal.get(key).isJsonNull())continue;
            UUID id=UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
            if(level.getEntity(id)==null){var anchor=points.get(i);create(level,definition,skin,anchor.position(),anchor.yaw(),id);}
            try{journal.put(key,new JsonPrimitive(id.toString()));}catch(Exception failure){throw new IllegalStateException("NPC投放记录未完成",failure);}count++;
        }
        return count;
    }
    /** Only called for newly created worlds, after configured map anchors have had first choice. */
    public void starterWorld(ServerLevel level){
        if(!journal.get(spawnKey("starter","elaina")).isJsonNull())return;
        var origin=level.getRespawnData().pos();
        for(int radius=2;radius<=64;radius+=2)for(int side=0;side<16;side++){
            double angle=side*Math.PI/8;int x=origin.getX()+(int)Math.round(Math.cos(angle)*radius),z=origin.getZ()+(int)Math.round(Math.sin(angle)*radius);
            int y=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);var feet=new BlockPos(x,y,z);var pos=Vec3.atBottomCenterOf(feet);
            if(!standing(level,pos)||!level.getFluidState(feet).isEmpty()||!level.getFluidState(feet.below()).isEmpty())continue;
            var body=level.getBlockState(feet);if(body.is(net.minecraft.tags.BlockTags.FIRE)||body.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)||body.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)||level.getBlockState(feet.above()).is(net.minecraft.tags.BlockTags.FIRE))continue;
            var floor=level.getBlockState(feet.below());if(floor.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)||floor.is(net.minecraft.world.level.block.Blocks.CACTUS)||floor.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)||floor.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE))continue;
            group(level,"starter",List.of(new NpcSpawns.Anchor("elaina",pos,0)),List.of("elaina"),level.getSeed());return;
        }
        throw new IllegalStateException("公共出生点附近未找到伊蕾娜的安全投放位置");
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
        var selected=NpcDefinitions.current().require(definition);var skins=NpcDefinitions.current().randomSkins();String skin=selected.skin()==null||selected.skin().isBlank()?skins.get(source.getLevel().getRandom().nextInt(skins.size())):selected.skin();
        service.create(source.getLevel(),definition,skin,pos,0,UUID.randomUUID());source.sendSuccess(()->ModText.of("已生成"+selected.name()),true);return 1;
    }
}
