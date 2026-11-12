package cn.yumis.fight.modules.mapgeneration.application;

import cn.yumis.fight.modules.mapgeneration.api.MapBlueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.util.*;

public final class GenerationPlan {
    public enum Mode { TERRAIN, OCEAN, FLOATING }
    public record Facility(MapBlueprint.Facility definition,StructureTemplate template,BlockPos position,double surface,String biome,BiomeMaterials.Theme theme,MapFoundations.Footprint footprint) {
        boolean containsGround(int x,int z) {
            int localX=x-position.getX(),localZ=z-position.getZ();
            return footprint==null?localX>=0&&localX<template.getSize().getX()&&localZ>=0&&localZ<template.getSize().getZ():footprint.contains(localX,localZ);
        }
    }
    public final ServerLevel level;
    public final Identifier id;
    public final MapBlueprint blueprint;
    public final BlockPos origin;
    public final Mode mode;
    public final int minX,minZ,maxX,maxZ,width,depth;
    public final int[] terrain,bearing;
    public final boolean[] water;
    public final String[] biomes;
    public final Map<Integer,Integer> roadHeights=new HashMap<>();
    public final Set<Integer> roadCells=new HashSet<>();
    public final Set<Integer> keepClear=new HashSet<>();
    public final Map<Integer,String> roadIds=new HashMap<>();
    public final List<Facility> facilities=new ArrayList<>();
    public final List<int[]> flatZones=new ArrayList<>();
    /** Last road cells before a rigid landing, mapped toward the landing. */
    public final Map<Integer,Integer> platformApproaches=new LinkedHashMap<>();
    public final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> foundationBlocks=new LinkedHashMap<>();
    public final Set<BlockPos> authoredLights=new HashSet<>();
    public final List<ChunkPos> acquiredChunks=new ArrayList<>();
    public final Map<String,Integer> metrics=new LinkedHashMap<>();
    public GenerationPlan(ServerLevel level,Identifier id,MapBlueprint blueprint,BlockPos origin,Mode mode) {
        this.level=level;this.id=id;this.blueprint=blueprint;this.origin=origin;this.mode=mode;
        minX=origin.getX()+blueprint.bounds[0];minZ=origin.getZ()+blueprint.bounds[1];maxX=origin.getX()+blueprint.bounds[2];maxZ=origin.getZ()+blueprint.bounds[3];
        width=maxX-minX+1;depth=maxZ-minZ+1;terrain=new int[width*depth];bearing=new int[terrain.length];water=new boolean[terrain.length];biomes=new String[terrain.length];
    }
    public int index(int x,int z) {return (z-minZ)*width+x-minX;}
    public int x(int i) {return minX+i%width;}
    public int z(int i) {return minZ+i/width;}
    public boolean inside(int x,int z) {return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ;}
    public int ground(int x,int z) {return terrain[index(x,z)];}
    public int nearestRoad(int x,int z) {
        int best=-1;double distance=Double.MAX_VALUE;
        for(int i:roadCells) {double d=(double)(x-x(i))*(x-x(i))+(double)(z-z(i))*(z-z(i));if(d<distance||d==distance&&i<best){best=i;distance=d;}}
        if(best<0)throw new IllegalArgumentException("蓝图道路为空");return best;
    }
    public String summary() {
        return "blueprint="+id+" mode="+mode+" origin="+origin.toShortString()+" facilities="+facilities.size()+" roads="+roadCells.size()+" metrics="+metrics;
    }
}
