package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import java.util.*;
public final class CageEntity extends Entity {
    private static final EntityDataAccessor<String> ROOT=SynchedEntityData.defineId(CageEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SIDE=SynchedEntityData.defineId(CageEntity.class,EntityDataSerializers.INT);
    public CageEntity(EntityType<?> t,Level l){
        super(t,l);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){
        b.define(ROOT,"");
        b.define(SIDE,-1);
    }
    public String root(){
        return entityData.get(ROOT);
    }
    public int side(){
        return entityData.get(SIDE);
    }
    public void initialize(String root,int side){
        entityData.set(ROOT,root);
        entityData.set(SIDE,side);
    }
    @Override public void tick(){
        super.tick();
        double x=getX(),y=getY(),z=getZ();
        int side=entityData.get(SIDE);
        if(side>=0)setBoundingBox(side<2?new AABB(x-1.5,y,z+(side==0?-1.5:1.35),x+1.5,y+2.5,z+(side==0?-1.35:1.5)):new AABB(x+(side==2?-1.5:1.35),y,z-1.5,x+(side==2?-1.35:1.5),y+2.5,z+1.5));
    }
    @Override public boolean canBeCollidedWith(Entity other){
        return entityData.get(SIDE)>=0;
    }
    @Override public boolean isPickable(){
        return true;
    }
    @Override public boolean hurtServer(ServerLevel l,DamageSource source,float amount){
        if(source.getEntity() instanceof ServerPlayer p){
            com.google.gson.JsonObject j=new com.google.gson.JsonObject();
            j.addProperty("cage",root());
            j.addProperty("operation",source.getDirectEntity()==null?UUID.randomUUID().toString():source.getDirectEntity().getUUID()+"/"+l.getGameTime());
            CageActions.hit(p,j);
        }
        return true;
    }
    @Override protected void readAdditionalSaveData(ValueInput i){
    }
    @Override protected void addAdditionalSaveData(ValueOutput o){
    }
}
