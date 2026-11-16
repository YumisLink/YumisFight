package cn.yumis.fight.modules.arkguard.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** The shared Ark scene's address and boundaries, also consumed by its menu. */
public final class ArkGuardRing {
    public static final Identifier BLUEPRINT=Identifier.parse("yumis_dungeon_and_fight:ark_guard_ring");
    public static final ResourceKey<Level> DIMENSION=ResourceKey.create(Registries.DIMENSION,Identifier.parse("yumis_dungeon_and_fight:dungeon"));
    public static final BlockPos ORIGIN=new BlockPos(0,96,-4096);
    private ArkGuardRing(){}
    public static boolean contains(Level level,BlockPos pos){
        return level.dimension().equals(DIMENSION)&&pos.getX()>=ORIGIN.getX()-60&&pos.getX()<ORIGIN.getX()+60
            &&pos.getZ()>=ORIGIN.getZ()-60&&pos.getZ()<ORIGIN.getZ()+60;
    }
}
