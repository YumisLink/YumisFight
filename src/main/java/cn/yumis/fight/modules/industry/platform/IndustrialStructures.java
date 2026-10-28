package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.DeviceType;

import com.google.gson.*;

import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;

import net.minecraft.server.level.*;

import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;

import net.minecraft.world.level.saveddata.*;

import net.neoforged.neoforge.transfer.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.fluid.*;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Industrial module implementation; owns one scheduling responsibility. */
public final class IndustrialStructures {
    @FunctionalInterface public interface Pattern {List<BlockPos> missing(MachineBlockEntity machine);}
    private static final Map<String,Pattern> PATTERNS=new LinkedHashMap<>();
    static {
        register(DeviceType.BEACON,be->{
            List<BlockPos> missing=new ArrayList<>();Block material=structureMaterial(be.tier());
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){BlockPos pos=be.getBlockPos().offset(x,-1,z);if(!be.getLevel().hasChunkAt(pos)||!be.getLevel().getBlockState(pos).is(material))missing.add(pos);}return missing;
        });
        register(DeviceType.FORGE,be->{
            List<BlockPos> missing=new ArrayList<>();Block material=structureMaterial(be.tier());BlockPos origin=be.getBlockPos(),center=origin.relative(be.getBlockState().getValue(MachineBlock.FACING).getOpposite()).above();
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){BlockPos pos=center.offset(x,y,z);if(pos.equals(origin))continue;boolean air=x==0&&y==0&&z==0;if(!be.getLevel().hasChunkAt(pos)||(air?!be.getLevel().getBlockState(pos).isAir():!be.getLevel().getBlockState(pos).is(material)))missing.add(pos);}return missing;
        });
    }
    public static synchronized void register(DeviceType type,Pattern pattern){if(!type.needsStructure()||PATTERNS.putIfAbsent(type.id,Objects.requireNonNull(pattern))!=null)throw new IllegalArgumentException("Invalid or duplicate structure: "+type.id);}
    public static List<BlockPos> missing(MachineBlockEntity be){if(!be.device().needsStructure())return List.of();var pattern=PATTERNS.get(be.device().id);if(pattern==null)throw new IllegalStateException("Missing structure definition: "+be.device().id);return pattern.missing(be);}
    public static Block structureMaterial(int tier){return IndustrialContent.ALL_BLOCKS.get(switch(tier){case 1->"energy_crystal_block_2";case 2->"";case 3->"titanium_block";case 4->"terra_block";default->"spacetime_block";})==null?Blocks.DIAMOND_BLOCK:IndustrialContent.ALL_BLOCKS.get(switch(tier){case 1->"energy_crystal_block_2";case 3->"titanium_block";case 4->"terra_block";default->"spacetime_block";}).get();}
}
