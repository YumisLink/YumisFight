package cn.yumis.fight.modules.mapgeneration.application;

import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Uses vanilla command-storage SavedData; no global business runtime or new transaction layer. */
public final class GenerationStore {
    private static final Identifier KEY=Identifier.parse("yumis_dungeon_and_fight:map_generation");
    private GenerationStore() {}
    public static void newWorld(MinecraftServer server) {CompoundTag data=read(server);data.putBoolean("new_world",true);write(server,data);}
    public static boolean consumeNewWorld(MinecraftServer server) {
        CompoundTag data=read(server);boolean result=data.getBooleanOr("new_world",false);
        data.putBoolean("new_world",false);write(server,data);return result;
    }
    public static boolean overlaps(ServerLevel level,int minX,int minZ,int maxX,int maxZ) {
        for(Tag tag:read(level.getServer()).getListOrEmpty("instances")) {
            CompoundTag item=(CompoundTag)tag;
            if(!item.getStringOr("dimension","").equals(level.dimension().identifier().toString()))continue;
            if(minX<=item.getIntOr("max_x",0)&&maxX>=item.getIntOr("min_x",0)&&minZ<=item.getIntOr("max_z",0)&&maxZ>=item.getIntOr("min_z",0))return true;
        }
        return false;
    }
    public static boolean blocksHostiles(ServerLevel level,int x,int z) {
        for(Tag tag:read(level.getServer()).getListOrEmpty("instances")) {
            var item=(CompoundTag)tag;
            if(item.getBooleanOr("no_hostiles",false)&&item.getStringOr("dimension","").equals(level.dimension().identifier().toString())
                &&x>=item.getIntOr("min_x",0)&&x<=item.getIntOr("max_x",0)&&z>=item.getIntOr("min_z",0)&&z<=item.getIntOr("max_z",0))return true;
        }
        return false;
    }
    static void complete(MinecraftServer server,GenerationPlan plan) {
        CompoundTag data=read(server),item=new CompoundTag();ListTag list=data.getListOrEmpty("instances");
        item.putString("blueprint",plan.id.toString());item.putString("mode",plan.mode.name());
        item.putString("dimension",plan.level.dimension().identifier().toString());
        item.putBoolean("no_hostiles",plan.blueprint.settings.noHostileSpawns);
        item.putInt("x",plan.origin.getX());item.putInt("y",plan.origin.getY());item.putInt("z",plan.origin.getZ());
        item.putInt("min_x",plan.minX);item.putInt("max_x",plan.maxX);item.putInt("min_z",plan.minZ);item.putInt("max_z",plan.maxZ);
        list.add(item);data.put("instances",list);write(server,data);
    }
    private static CompoundTag read(MinecraftServer server) {return server.getCommandStorage().get(KEY).copy();}
    private static void write(MinecraftServer server,CompoundTag data) {server.getCommandStorage().set(KEY,data);}
}
