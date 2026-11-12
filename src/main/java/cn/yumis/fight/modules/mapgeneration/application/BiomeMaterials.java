package cn.yumis.fight.modules.mapgeneration.application;

import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import cn.yumis.fight.modules.mapgeneration.api.MapBlueprint;
import java.util.*;

/** Native wood families; preserves all compatible geometry and block-entity data. */
final class BiomeMaterials {
    record Theme(String main,String accent,String leaves) {}
    static Theme theme(String biome) {
        if(biome.contains("cherry"))return new Theme("cherry","dark_oak","cherry");
        if(biome.contains("pale_garden"))return new Theme("pale_oak","dark_oak","pale_oak");
        if(biome.contains("mangrove"))return new Theme("mangrove","dark_oak","mangrove");
        if(biome.contains("birch"))return new Theme("birch","dark_oak","birch");
        if(biome.contains("dark_forest"))return new Theme("dark_oak","spruce","dark_oak");
        if(biome.contains("jungle"))return new Theme("jungle","dark_oak","jungle");
        if(biome.contains("savanna")||biome.contains("desert")||biome.contains("badlands"))return new Theme("acacia","dark_oak","acacia");
        if(biome.contains("taiga")||biome.contains("snow")||biome.contains("frozen")||biome.contains("cold_ocean")||biome.contains("grove")||biome.contains("jagged")||biome.contains("peaks")&&!biome.contains("stony"))return new Theme("spruce","dark_oak","spruce");
        return new Theme("oak","spruce","oak");
    }
    static Identifier replace(Identifier id,Theme theme,MapBlueprint.Settings settings) {
        if(!id.getNamespace().equals("minecraft"))return id;
        String name=id.getPath();
        if(name.equals("bamboo_mosaic"))return Identifier.withDefaultNamespace(theme.main+"_planks");
        if(name.startsWith("bamboo_mosaic_"))return Identifier.withDefaultNamespace(theme.main+name.substring("bamboo_mosaic".length()));
        List<String> families=new ArrayList<>(settings.mainWoodFamilies);families.addAll(settings.accentWoodFamilies);families.sort(Comparator.comparingInt(String::length).reversed());
        for(String family:families) {
            String prefix=name.startsWith("stripped_")?"stripped_":"";
            String n=name.substring(prefix.length());
            if(!n.startsWith(family+"_"))continue;
            String suffix=n.substring(family.length());
            String target=suffix.equals("_leaves")?theme.leaves:settings.mainWoodFamilies.contains(family)?theme.main:theme.accent;
            if(family.equals("bamboo")&&suffix.equals("_block"))suffix="_log";
            if(theme.main.equals("birch")&&target.equals("birch")&&(suffix.equals("_log")||suffix.equals("_wood")))target="oak";
            Identifier result=Identifier.withDefaultNamespace(prefix+target+suffix);
            return BuiltInRegistries.BLOCK.containsKey(result)?result:id;
        }
        return id;
    }
    static StructureTemplate adapt(ServerLevel level,StructureTemplate original,Theme theme,MapBlueprint.Settings settings) {
        CompoundTag tag=original.save(new CompoundTag());
        for(var iterator=tag.getListOrEmpty("entities").iterator();iterator.hasNext();) {
            CompoundTag entry=(CompoundTag)iterator.next(),entity=entry.getCompoundOrEmpty("nbt");String type=entity.getStringOr("id","");
            if(type.equals("minecraft:player")||type.equals("minecraft:item")||type.equals("minecraft:experience_orb")){iterator.remove();continue;}
            var tags=entity.getListOrEmpty("Tags");var marker=net.minecraft.nbt.StringTag.valueOf("ydnf_map_animal");if(!tags.contains(marker))tags.add(marker);entity.put("Tags",tags);
        }
        for(Tag item:tag.getListOrEmpty("palette")) {
            CompoundTag entry=(CompoundTag)item;
            Identifier old=Identifier.parse(entry.getStringOr("Name","minecraft:air")),replacement=replace(old,theme,settings);
            if(old.equals(replacement))continue;
            entry.putString("Name",replacement.toString());
            BlockState target=BuiltInRegistries.BLOCK.getValue(replacement).defaultBlockState();
            CompoundTag props=entry.getCompoundOrEmpty("Properties");
            for(String key:new ArrayList<>(props.keySet()))if(target.getBlock().getStateDefinition().getProperty(key)==null)props.remove(key);
        }
        StructureTemplate result=new StructureTemplate();
        result.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),tag);return result;
    }
}
