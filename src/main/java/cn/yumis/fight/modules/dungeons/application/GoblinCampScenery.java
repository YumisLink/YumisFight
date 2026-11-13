package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.content.GoblinCamp;
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

/** A few native, noninteractive displays. Every saved entity carries explicit cell ownership. */
final class GoblinCampScenery {
    private static final String OWNER="ydnf_goblin_scenery";
    static boolean owns(Entity entity){return entity.getPersistentData().getBooleanOr(OWNER,false);}
    static boolean inCell(Entity entity,int cell){return owns(entity)&&entity.getPersistentData().getIntOr("ydnf_scene_cell",Integer.MIN_VALUE)==cell;}
    static void clear(ServerLevel world,Integer cell){
        var stale=new ArrayList<Entity>();
        for(Entity entity:world.getAllEntities())if(owns(entity)&&(cell==null||inCell(entity,cell)))stale.add(entity);
        stale.forEach(Entity::discard);
    }
    static void place(ServerLevel world,Dungeon dungeon){
        clear(world,dungeon.cell);
        try{
            var ops=world.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            for(var prop:GoblinCamp.relics()){
                var tag=new CompoundTag();
                tag.put("item",ItemStack.CODEC.encodeStart(ops,new ItemStack(prop.item())).getOrThrow());
                tag.putString("item_display","fixed");
                var rotation=new Quaternionf().rotateXYZ((float)Math.toRadians(prop.pitch()),(float)Math.toRadians(prop.yaw()),(float)Math.toRadians(prop.roll()));
                var transform=new Transformation(new Vector3f(),rotation,new Vector3f(prop.scale()),new Quaternionf());
                tag.put("transformation",Transformation.EXTENDED_CODEC.encodeStart(ops,transform).getOrThrow());
                tag.putFloat("view_range",.75f);tag.putFloat("width",2);tag.putFloat("height",3);
                tag.putFloat("shadow_radius",0);tag.putBoolean("Invulnerable",true);tag.putBoolean("NoGravity",true);
                Entity entity=EntityType.create(EntityTypes.ITEM_DISPLAY,TagValueInput.create(ProblemReporter.DISCARDING,world.registryAccess(),tag),world,EntitySpawnReason.COMMAND)
                    .orElseThrow(()->new IllegalStateException("无法创建营地遗物："+prop.id()));
                entity.setPos(dungeon.cell*512+prop.area().x()+prop.x(),80+prop.y(),prop.area().z()+prop.z());
                entity.getPersistentData().putBoolean(OWNER,true);
                entity.getPersistentData().putInt("ydnf_scene_cell",dungeon.cell);
                entity.getPersistentData().putString("ydnf_scene_instance",dungeon.id.toString());
                entity.getPersistentData().putString("ydnf_scene_layout",GoblinCamp.VERSION);
                entity.getPersistentData().putString("ydnf_scene_prop",prop.id());
                if(!world.addFreshEntity(entity))throw new IllegalStateException("营地遗物放置失败："+prop.id());
            }
        }catch(RuntimeException failure){clear(world,dungeon.cell);throw failure;}
    }
    static void ambience(ServerLevel world,Dungeon dungeon,List<ServerPlayer> players){
        int ox=dungeon.cell*512;
        var living=GoblinCamp.LIVING;
        if(near(players,ox+living.x(),living.z()-7))
            world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,ox+living.x()+.5,81.4,living.z()-6.5,1,.15,.1,.15,.01);
        var ritual=GoblinCamp.RITUAL;
        if(near(players,ox+ritual.x(),ritual.z()))
            world.sendParticles(ParticleTypes.WHITE_ASH,ox+ritual.x()+1.5,82,ritual.z()-7.5,2,2,.6,2,.005);
        var salvage=GoblinCamp.SALVAGE;
        if(near(players,ox+salvage.x()-18,salvage.z()))
            world.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM,ox+salvage.x()-17.5,87,salvage.z()-1.5,2,2,.5,2,0);
    }
    private static boolean near(List<ServerPlayer> players,double x,double z){return players.stream().anyMatch(p->p.distanceToSqr(x,80,z)<48*48);}
    private GoblinCampScenery(){}
}
