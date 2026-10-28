package cn.yumis.fight.modules.economy.api;

import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
public final class PrivateLoot extends Entity {
    @Override public boolean hurtServer(net.minecraft.server.level.ServerLevel level,net.minecraft.world.damagesource.DamageSource source,float amount){
        return false;
    }
    private static final EntityDataAccessor<ItemStack> STACK=SynchedEntityData.defineId(PrivateLoot.class,EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<String> OWNER=SynchedEntityData.defineId(PrivateLoot.class,EntityDataSerializers.STRING);
    public String dropId="";
    public PrivateLoot(EntityType<?> type,Level level){
        super(type,level);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){
        builder.define(STACK,ItemStack.EMPTY);
        builder.define(OWNER,"");
    }
    public void initialize(String id,String owner,ItemStack stack){
        dropId=id;
        entityData.set(OWNER,owner);
        entityData.set(STACK,stack.copy());
    }
    public ItemStack item(){
        return entityData.get(STACK);
    }
    public String owner(){
        return entityData.get(OWNER);
    }
    @Override public void tick(){
        super.tick();
        if(!level().isClientSide())LootEntityActions.validate(this);
    }
    @Override public void playerTouch(Player player){
        if(player instanceof ServerPlayer p)LootEntityActions.pickup(p,this);
    }
    @Override public boolean broadcastToPlayer(ServerPlayer p){
        return p.getStringUUID().equals(owner());
    }
    @Override protected void readAdditionalSaveData(ValueInput input){
        dropId=input.getStringOr("drop","");
        entityData.set(OWNER,input.getStringOr("owner",""));
        entityData.set(STACK,input.read("item",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }
    @Override protected void addAdditionalSaveData(ValueOutput output){
        output.putString("drop",dropId);
        output.putString("owner",owner());
        output.store("item",ItemStack.OPTIONAL_CODEC,item());
    }
}
