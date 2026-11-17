package cn.yumis.fight.modules.professions.api;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
/** 草药抽取、工具产量与生长环境；采集奖励由本人客户端调用。 */
public final class HerbRules {
    private HerbRules(){}
    public record Tool(int count,int luck,boolean seed){}
    public static Tool tool(String id){
        return switch(id){
            case "sickle"->new Tool(1,0,true);
            case "rare_sickle"->new Tool(2,cn.yumis.fight.modules.professions.platform.RareSickleItem.LUCK_BONUS,true);
            case "artifact_sickle"->new Tool(cn.yumis.fight.modules.professions.platform.ArtifactSickleItem.HARVEST_COUNT,cn.yumis.fight.modules.professions.platform.ArtifactSickleItem.LUCK_BONUS,true);
            case "legendary_sickle"->new Tool(cn.yumis.fight.modules.professions.platform.LegendarySickleItem.HARVEST_COUNT,cn.yumis.fight.modules.professions.platform.LegendarySickleItem.LUCK_BONUS,true);
            case "epic_sickle"->new Tool(cn.yumis.fight.modules.professions.platform.EpicSickleItem.HARVEST_COUNT,cn.yumis.fight.modules.professions.platform.EpicSickleItem.LUCK_BONUS,true);
            case "mythic_sickle"->new Tool(cn.yumis.fight.modules.professions.platform.MythicSickleItem.HARVEST_COUNT,cn.yumis.fight.modules.professions.platform.MythicSickleItem.LUCK_BONUS,true);
            default->new Tool(1,0,false);
        };
    }
    public static boolean stormHabitat(Level level,BlockPos pos){
        String biome=biomePath(level,pos);
        return level.dimension().equals(Level.OVERWORLD)&&pos.getY()>=100&&(biome.equals("jagged_peaks")||biome.equals("stony_peaks"));
    }
    public static HerbSpecies.Habitat habitat(Level level,BlockPos pos){
        String under=BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.below()).getBlock()).getPath();
        if(under.equals("bone_block"))return HerbSpecies.Habitat.BONE;
        if(under.equals("obsidian"))return HerbSpecies.Habitat.OBSIDIAN;
        String biome=biomePath(level,pos);
        if(under.contains("soul_soil")||under.contains("soul_sand"))return HerbSpecies.Habitat.SOUL;
        if(pos.getY()>=120&&under.contains("stone")&&(biome.contains("jagged_peaks")||biome.contains("windswept")||biome.contains("stony_peaks")))return HerbSpecies.Habitat.PEAKS;
        if(level.dimension().equals(Level.END)&&biome.contains("highlands"))return HerbSpecies.Habitat.END_HIGHLANDS;
        if(biome.contains("mushroom_fields"))return HerbSpecies.Habitat.MUSHROOM;
        if(biome.contains("deep_dark"))return HerbSpecies.Habitat.DEEP_DARK;
        if(stormHabitat(level,pos)&&under.contains("stone"))return HerbSpecies.Habitat.STORM_CLIFF;
        if(nearMonument(level,pos))return HerbSpecies.Habitat.MONUMENT;
        if(biome.contains("bamboo_jungle"))return HerbSpecies.Habitat.BAMBOO;
        if(biome.contains("dark_forest"))return HerbSpecies.Habitat.DARK_FOREST;
        if(biome.contains("sunflower_plains"))return HerbSpecies.Habitat.SUNFLOWER;
        if(level.dimension().equals(Level.NETHER))return biome.contains("crimson")||biome.contains("warped")?HerbSpecies.Habitat.NETHER_FOREST:HerbSpecies.Habitat.NETHER_WASTES;
        if(biome.contains("snow")||biome.contains("frozen")||biome.contains("ice")||biome.equals("grove"))return HerbSpecies.Habitat.SNOWY;
        if(biome.contains("swamp"))return HerbSpecies.Habitat.SWAMP;
        if(biome.contains("windswept")&&pos.getY()>=90)return HerbSpecies.Habitat.WINDSWEPT;
        if(biome.contains("ocean"))return HerbSpecies.Habitat.OCEAN;
        if(biome.contains("savanna"))return HerbSpecies.Habitat.SAVANNA;
        if(level.getBiome(pos).is(net.minecraft.tags.BiomeTags.IS_MOUNTAIN))return HerbSpecies.Habitat.MOUNTAIN;
        return HerbSpecies.Habitat.PLAINS;
    }
    private static String biomePath(Level level,BlockPos pos){
        var key=level.registryAccess().lookupOrThrow(Registries.BIOME).getKey(level.getBiome(pos).value());
        return key==null?"":key.getPath();
    }
    private static boolean nearMonument(Level level,BlockPos pos){
        for(var cell:BlockPos.betweenClosed(pos.offset(-12,-6,-12),pos.offset(12,6,12))){
            if(!level.hasChunkAt(cell))continue;
            var state=level.getBlockState(cell);
            if(state.is(Blocks.PRISMARINE)||state.is(Blocks.PRISMARINE_BRICKS)||state.is(Blocks.DARK_PRISMARINE)||state.is(Blocks.SEA_LANTERN))return true;
        }
        return false;
    }
    public static String item(Level level,BlockPos pos,BlockState harvested,HerbTier tier,double random){
        if(harvested.getBlock() instanceof cn.yumis.fight.modules.professions.platform.NightmareVineBlock)return HerbSpecies.NIGHTMARE_VINE;
        if(harvested.getBlock() instanceof cn.yumis.fight.modules.professions.platform.StormVineBlock)return HerbSpecies.STORMVINE;
        if(harvested.getBlock() instanceof cn.yumis.fight.modules.professions.platform.OceanHerbBlock)return HerbSpecies.OCEAN_LEGENDARY.get(random<.5?0:1);
        if(harvested.getBlock() instanceof cn.yumis.fight.modules.professions.platform.OceanHerbBlock)return HerbSpecies.OCEAN_LEGENDARY.get(random<.5?0:1);
        var ground=level.getBlockState(pos.below());
        if(ground.is(Blocks.BONE_BLOCK))return HerbSpecies.DEATHBLOOM;
        if(ground.is(Blocks.OBSIDIAN))return HerbSpecies.BLACKSTONE_GRASS;
        return HerbSpecies.pick(habitat(level,pos),tier,random);
    }
    /** 每级给高一档稀有度的权重加成比例。 */
    public static final double LUCK_PER_LEVEL=0.15;
    /** 镰刀单次收割的草药数量上限（每次 1 株）。 */
    public static final int PER_HARVEST=1;
    /** 经验：普通 10，每高一档 +10。 */
    public static long experience(HerbTier tier){
        return 10L+10L*tier.ordinal();
    }
    public static HerbTier roll(int level,double random){
        int effective=Math.max(0,level);
        double total=0;
        double[] weights=new double[HerbTier.values().length];
        for(HerbTier tier:HerbTier.values()){
            weights[tier.ordinal()]=tier.weight()*(1+LUCK_PER_LEVEL*effective*tier.ordinal());
            total+=weights[tier.ordinal()];
        }
        double target=Math.max(0,Math.min(1,random))*total;
        double cursor=0;
        for(HerbTier tier:HerbTier.values()){
            cursor+=weights[tier.ordinal()];
            if(target<cursor)return tier;
        }
        return HerbTier.MYTHIC;
    }
    public static double chance(HerbTier tier,int level){
        int effective=Math.max(0,level);
        double total=0;
        for(HerbTier each:HerbTier.values())total+=each.weight()*(1+LUCK_PER_LEVEL*effective*each.ordinal());
        return tier.weight()*(1+LUCK_PER_LEVEL*effective*tier.ordinal())/total;
    }
}
