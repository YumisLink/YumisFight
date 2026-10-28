package cn.yumis.fight.modules.skills.client;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerSkin;
import java.util.UUID;
public final class VisualDouble extends RemotePlayer {
    private final AbstractClientPlayer owner;
    public VisualDouble(ClientLevel level,AbstractClientPlayer owner){
        super(level,new GameProfile(UUID.randomUUID(),owner.getGameProfile().name()));
        this.owner=owner;
        setNoGravity(true);
    }
    @Override public PlayerSkin getSkin(){
        return owner.getSkin();
    }
    @Override public boolean isPickable(){
        return false;
    }
    @Override public boolean canBeCollidedWith(Entity other){
        return false;
    }
}
