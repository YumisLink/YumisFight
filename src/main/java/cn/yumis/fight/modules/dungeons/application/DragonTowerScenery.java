package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.content.DragonTower;
import com.mojang.math.Transformation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Native decorative displays, with instance ownership and three-dimensional proximity. */
final class DragonTowerScenery {
    private static final String OWNER="ydnf_dragon_scenery";
    static boolean inCell(Entity entity,int cell){return entity.getPersistentData().getBooleanOr(OWNER,false)&&entity.getPersistentData().getIntOr("ydnf_scene_cell",Integer.MIN_VALUE)==cell;}
    static void clear(ServerLevel world,int cell){
        var stale=new ArrayList<Entity>();
        for(Entity entity:world.getAllEntities())if(inCell(entity,cell))stale.add(entity);
        stale.forEach(Entity::discard);
    }
    static void place(ServerLevel world,Dungeon dungeon){
        clear(world,dungeon.cell);
        try{
            var ops=world.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            for(var prop:DragonTower.relics()){
                var tag=new CompoundTag();
                tag.put("item",ItemStack.CODEC.encodeStart(ops,new ItemStack(prop.item())).getOrThrow());
                tag.putString("item_display","fixed");
                var rotation=new Quaternionf().rotateXYZ((float)Math.toRadians(prop.pitch()),(float)Math.toRadians(prop.yaw()),(float)Math.toRadians(prop.roll()));
                tag.put("transformation",Transformation.EXTENDED_CODEC.encodeStart(ops,new Transformation(new Vector3f(),rotation,new Vector3f(prop.scale()),new Quaternionf())).getOrThrow());
                tag.putFloat("view_range",1);tag.putFloat("width",6);tag.putFloat("height",8);
                tag.putFloat("shadow_radius",0);tag.putBoolean("Invulnerable",true);tag.putBoolean("NoGravity",true);
                Entity entity=EntityType.create(EntityTypes.ITEM_DISPLAY,TagValueInput.create(ProblemReporter.DISCARDING,world.registryAccess(),tag),world,EntitySpawnReason.COMMAND)
                        .orElseThrow(()->new IllegalStateException("无法创建龙塔遗物："+prop.id()));
                var p=prop.position();entity.setPos(dungeon.cell*512+p.x+.5,80+p.y,p.z+.5);
                entity.getPersistentData().putBoolean(OWNER,true);
                entity.getPersistentData().putInt("ydnf_scene_cell",dungeon.cell);
                entity.getPersistentData().putString("ydnf_scene_instance",dungeon.id.toString());
                entity.getPersistentData().putString("ydnf_scene_layout",DragonTower.VERSION);
                entity.getPersistentData().putString("ydnf_scene_prop",prop.id());
                if(!world.addFreshEntity(entity))throw new IllegalStateException("龙塔遗物放置失败："+prop.id());
            }
        }catch(RuntimeException failure){clear(world,dungeon.cell);throw failure;}
    }
    static void ambience(ServerLevel world,Dungeon dungeon,List<ServerPlayer> players){
        for(var floor:DragonTower.floors()){
            if(floor.index()<3)continue;
            var p=floor.at(-6,3,-8).add(dungeon.cell*512+.5,80,.5);
            if(players.stream().noneMatch(player->player.distanceToSqr(p)<36*36))continue;
            world.sendParticles(ParticleTypes.ENCHANT,p.x,p.y,p.z,3,1.1,.6,1.1,0);
            if(floor.index()>=5)world.sendParticles(ParticleTypes.END_ROD,p.x+2,p.y+3,p.z-2,2,1.5,1,1.5,.005);
        }
    }
    private DragonTowerScenery(){}
}
