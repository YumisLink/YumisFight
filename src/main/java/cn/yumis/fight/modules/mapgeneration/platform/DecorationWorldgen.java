package cn.yumis.fight.modules.mapgeneration.platform;

import cn.yumis.fight.modules.mapgeneration.api.WorldDecorations;
import cn.yumis.fight.platform.ItemData;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import java.util.*;

/** One normal generation feature. It never runs from chunk-load or server-tick callbacks. */
public final class DecorationWorldgen {
    private static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,ItemData.NS);
    private static final DeferredHolder<Feature<?>,SurfaceFeature> SURFACE=FEATURES.register("world_decorations",SurfaceFeature::new);
    private DecorationWorldgen(){}
    public static void register(IEventBus bus){FEATURES.register(bus);}

    public record MiniStructure(Map<String,Identifier> templates,int chunkRarity,boolean forestOnly,boolean preferRoadside,List<String> replaces) {
        public static final Codec<MiniStructure> CODEC=RecordCodecBuilder.<MiniStructure>create(instance->instance.group(
                Codec.unboundedMap(Codec.STRING,Identifier.CODEC).fieldOf("templates").forGetter(MiniStructure::templates),
                Codec.intRange(1,4096).fieldOf("chunk_rarity").forGetter(MiniStructure::chunkRarity),
                Codec.BOOL.fieldOf("forest_only").forGetter(MiniStructure::forestOnly),
                Codec.BOOL.fieldOf("prefer_roadside").forGetter(MiniStructure::preferRoadside),
                Codec.STRING.listOf().optionalFieldOf("replaces",List.of()).forGetter(MiniStructure::replaces)
        ).apply(instance,MiniStructure::new)).validate(entry->entry.templates.containsKey("oak")?
                DataResult.success(entry):DataResult.error(()->"Miniature decoration templates need an oak fallback"));
    }
    public record Config(int leafClustersMin,int leafClustersMax,int leafPatchMin,int leafPatchMax,
                         int looseMin,int looseMax,int roadChunkRarity,int campChunkRarity,int campPropsMin,int campPropsMax,
                         List<MiniStructure> miniStructures) implements FeatureConfiguration {
        public static final Codec<Config> CODEC=RecordCodecBuilder.<Config>create(instance->instance.group(
                Codec.intRange(0,8).fieldOf("leaf_clusters_min").forGetter(Config::leafClustersMin),
                Codec.intRange(0,8).fieldOf("leaf_clusters_max").forGetter(Config::leafClustersMax),
                Codec.intRange(1,16).fieldOf("leaf_patch_min").forGetter(Config::leafPatchMin),
                Codec.intRange(1,16).fieldOf("leaf_patch_max").forGetter(Config::leafPatchMax),
                Codec.intRange(0,16).fieldOf("loose_min").forGetter(Config::looseMin),
                Codec.intRange(0,16).fieldOf("loose_max").forGetter(Config::looseMax),
                Codec.intRange(1,1024).fieldOf("road_chunk_rarity").forGetter(Config::roadChunkRarity),
                Codec.intRange(1,4096).fieldOf("camp_chunk_rarity").forGetter(Config::campChunkRarity),
                Codec.intRange(1,5).fieldOf("camp_props_min").forGetter(Config::campPropsMin),
                Codec.intRange(1,5).fieldOf("camp_props_max").forGetter(Config::campPropsMax),
                MiniStructure.CODEC.listOf().fieldOf("mini_structures").forGetter(Config::miniStructures)
        ).apply(instance,Config::new)).validate(config->config.leafClustersMin>config.leafClustersMax||config.leafPatchMin>config.leafPatchMax||
                config.looseMin>config.looseMax||config.campPropsMin>config.campPropsMax?
                DataResult.error(()->"World decoration minimum exceeds maximum"):DataResult.success(config));
    }
    private record Tree(String wood,String leaves,boolean conifer){}
    private static final Map<Block,Tree> TREES=Map.ofEntries(
            Map.entry(Blocks.OAK_LEAVES,new Tree("oak","oak_leaf_litter",false)),
            Map.entry(Blocks.BIRCH_LEAVES,new Tree("birch","birch_leaf_litter",false)),
            Map.entry(Blocks.SPRUCE_LEAVES,new Tree("spruce","spruce_leaf_litter",true)),
            Map.entry(Blocks.JUNGLE_LEAVES,new Tree("jungle","jungle_leaf_litter",false)),
            Map.entry(Blocks.ACACIA_LEAVES,new Tree("acacia","acacia_leaf_litter",false)),
            Map.entry(Blocks.DARK_OAK_LEAVES,new Tree("dark_oak","dark_oak_leaf_litter",false)),
            Map.entry(Blocks.MANGROVE_LEAVES,new Tree("mangrove","mangrove_leaf_litter",false)),
            Map.entry(Blocks.PALE_OAK_LEAVES,new Tree("pale_oak","pale_oak_leaf_litter",false)),
            Map.entry(Blocks.CHERRY_LEAVES,new Tree("cherry",null,false)));
    private static final String[] HUMAN={"firewood_bundle","short_log_stack","old_waypost","rope_waypost"};
    private static final int[][] GROUP={{0,0},{2,0},{0,2},{-2,1},{1,-2}};

    public static final class SurfaceFeature extends Feature<Config> {
        public SurfaceFeature(){super(Config.CODEC);}
        @Override public boolean place(FeaturePlaceContext<Config> context){
            var level=context.level();
            if(level.getLevel().dimension()!=Level.OVERWORLD)return false;
            var random=context.random();var config=context.config();
            var chunk=ChunkPos.containing(context.origin());
            boolean any=miniatures(level,chunk,random,config.miniStructures);
            int clusters=between(random,config.leafClustersMin,config.leafClustersMax),made=0;
            for(int attempt=0;attempt<clusters*5&&made<clusters;attempt++){
                var center=sample(level,chunk,random);
                var tree=tree(level,center);
                if(tree==null||tree.leaves==null||!soil(level.getBlockState(center.below())))continue;
                int placed=0,target=between(random,config.leafPatchMin,config.leafPatchMax);
                int layout=random.nextInt(DecorationDefinitions.LEAF_LAYOUTS);
                for(int patch=0;patch<target*5&&placed<target;patch++){
                    var pos=surface(level,center.getX()+random.nextInt(5)-2,center.getZ()+random.nextInt(5)-2);
                    if(Math.abs(pos.getY()-center.getY())>2||!soil(level.getBlockState(pos.below())))continue;
                    var state=WorldDecorations.block(tree.leaves).defaultBlockState()
                            .setValue(LeafLitterBlock.FACING,direction(random)).setValue(SegmentableBlock.AMOUNT,1+random.nextInt(4)).setValue(LeafDecorationBlock.LAYOUT,layout);
                    if(put(level,chunk,pos,state))placed++;
                }
                if(placed>0){made++;any=true;}
            }
            int loose=between(random,config.looseMin,config.looseMax);
            for(int attempt=0;attempt<loose;attempt++){
                var pos=sample(level,chunk,random);var ground=level.getBlockState(pos.below());
                if(!naturalGround(ground))continue;
                var tree=tree(level,pos);
                if(tree==null&&random.nextBoolean())continue;
                boolean stone=stony(ground)||nearWater(level,pos);
                String id;
                if(!stone&&tree!=null&&tree.conifer&&soil(ground))id=switch(random.nextInt(3)){case 0->"pine_cones";case 1->"ground_branches";default->stone(ground,tree);};
                else if(!stone&&tree!=null&&soil(ground)&&random.nextBoolean())id="ground_branches";
                else id=stone(ground,tree);
                var state=WorldDecorations.block(id).defaultBlockState().setValue(DecorationBlock.FACING,direction(random)).setValue(ScatteredDecorationBlock.VARIANT,random.nextInt(DecorationDefinitions.SCATTER_LAYOUTS));
                any|=put(level,chunk,pos,state);
            }
            if(random.nextInt(config.roadChunkRarity)==0){
                var candidates=roadside(level,chunk);
                if(!candidates.isEmpty())any|=human(level,chunk,candidates.get(random.nextInt(candidates.size())),random,config);
            }
            if(random.nextInt(config.campChunkRarity)==0){
                var center=sample(level,chunk,random);
                if(soil(level.getBlockState(center.below())))any|=human(level,chunk,center,random,config);
            }
            return any;
        }
    }
    private static int between(RandomSource random,int min,int max){return min+random.nextInt(max-min+1);}
    private static Direction direction(RandomSource random){return Direction.from2DDataValue(random.nextInt(4));}
    private static BlockPos sample(WorldGenLevel level,ChunkPos chunk,RandomSource random){return surface(level,chunk.getMinBlockX()+2+random.nextInt(12),chunk.getMinBlockZ()+2+random.nextInt(12));}
    private static BlockPos surface(WorldGenLevel level,int x,int z){return new BlockPos(x,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z);}
    static boolean soil(BlockState state){return state.is(BlockTags.DIRT)||state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.MOSS_BLOCK)||state.is(Blocks.MUD)||state.is(Blocks.MUDDY_MANGROVE_ROOTS);}
    private static boolean stony(BlockState state){return state.is(BlockTags.BASE_STONE_OVERWORLD)||state.is(Blocks.GRAVEL)||state.is(Blocks.SAND)||state.is(Blocks.RED_SAND)||state.is(Blocks.CLAY);}
    private static boolean naturalGround(BlockState state){return soil(state)||stony(state);}
    static boolean open(BlockState state){return state.isAir()||state.is(Blocks.SHORT_GRASS)||state.is(Blocks.FERN);}
    private static boolean put(WorldGenLevel level,ChunkPos chunk,BlockPos pos,BlockState state){
        if(!ChunkPos.containing(pos).equals(chunk)||!open(level.getBlockState(pos))||!level.getFluidState(pos).isEmpty()||!state.canSurvive(level,pos))return false;
        return level.setBlock(pos,state,Block.UPDATE_CLIENTS);
    }
    private static Tree tree(WorldGenLevel level,BlockPos pos){
        Tree result=null;double best=Double.POSITIVE_INFINITY;
        var cursor=new BlockPos.MutableBlockPos();
        for(int dy=2;dy<=34;dy+=dy<18?2:4){
            if(pos.getY()+dy>=level.getMaxY())break;
            for(int dx=-3;dx<=3;dx+=3)for(int dz=-3;dz<=3;dz+=3){
                double score=dx*dx+dz*dz+dy*dy*.1;
                if(score>=best)continue;
                var state=level.getBlockState(cursor.setWithOffset(pos,dx,dy,dz));
                var kind=TREES.get(state.getBlock());
                if(kind!=null&&(!state.hasProperty(LeavesBlock.PERSISTENT)||!state.getValue(LeavesBlock.PERSISTENT))){result=kind;best=score;}
            }
        }
        return result;
    }
    private static boolean nearWater(WorldGenLevel level,BlockPos pos){
        var cursor=new BlockPos.MutableBlockPos();
        for(int dx=-2;dx<=2;dx+=2)for(int dz=-2;dz<=2;dz+=2)if(level.getFluidState(cursor.setWithOffset(pos,dx,-1,dz)).is(FluidTags.WATER))return true;
        return false;
    }
    private static String stone(BlockState ground,Tree tree){
        boolean dark=ground.is(Blocks.DEEPSLATE)||ground.is(Blocks.TUFF)||ground.is(Blocks.PODZOL)||ground.is(Blocks.MUD)||tree!=null&&tree.conifer;
        return dark?"dark_pebbles":"gray_pebbles";
    }
    private static List<BlockPos> roadside(WorldGenLevel level,ChunkPos chunk){
        List<BlockPos> result=new ArrayList<>();
        for(int x=2;x<14;x++)for(int z=2;z<14;z++){
            var pos=surface(level,chunk.getMinBlockX()+x,chunk.getMinBlockZ()+z);
            if(!soil(level.getBlockState(pos.below()))||!open(level.getBlockState(pos)))continue;
            boolean road=false;
            for(var direction:Direction.Plane.HORIZONTAL)for(int dy=-1;dy<=1;dy++)if(level.getBlockState(pos.below().relative(direction).offset(0,dy,0)).is(Blocks.DIRT_PATH))road=true;
            if(road)result.add(pos);
        }
        return result;
    }
    private static boolean human(WorldGenLevel level,ChunkPos chunk,BlockPos center,RandomSource random,Config config){
        int target=between(random,config.campPropsMin,config.campPropsMax),turn=random.nextInt(4),first=random.nextInt(HUMAN.length);
        List<BlockPos> positions=new ArrayList<>();
        List<BlockState> states=new ArrayList<>();Set<MiniStructure> replacements=new LinkedHashSet<>();int planned=0;
        for(int i=0;i<GROUP.length&&planned<target;i++){
            int dx=GROUP[i][0],dz=GROUP[i][1];
            for(int rotation=0;rotation<turn;rotation++){int old=dx;dx=-dz;dz=old;}
            var pos=surface(level,center.getX()+dx,center.getZ()+dz);
            if(!ChunkPos.containing(pos).equals(chunk)||Math.abs(pos.getY()-center.getY())>1||!soil(level.getBlockState(pos.below()))||!open(level.getBlockState(pos))||!level.getFluidState(pos).isEmpty())continue;
            String id=HUMAN[(first+i)%HUMAN.length];var replacement=config.miniStructures.stream().filter(entry->entry.replaces.contains(id)).findFirst();
            if(replacement.isPresent()){replacements.add(replacement.get());planned++;continue;}
            var state=WorldDecorations.block(id).defaultBlockState().setValue(DecorationBlock.FACING,direction(random));
            if(state.canSurvive(level,pos)){positions.add(pos);states.add(state);planned++;}
        }
        if(planned<config.campPropsMin)return false;
        boolean any=false;
        for(var entry:replacements)for(int attempt=0;attempt<8;attempt++){
            var candidate=attempt==0?center:surface(level,center.getX()+random.nextInt(7)-3,center.getZ()+random.nextInt(7)-3);
            if(placeMiniature(level,chunk,candidate,random,entry)){any=true;break;}
        }
        for(int i=0;i<positions.size();i++)any|=put(level,chunk,positions.get(i),states.get(i));
        return any;
    }
    private static boolean placeMiniature(WorldGenLevel level,ChunkPos chunk,BlockPos center,RandomSource random,MiniStructure entry){
        var nearby=tree(level,center);if(entry.forestOnly&&nearby==null)return false;
        String wood=nearby==null?"oak":nearby.wood;var id=entry.templates.getOrDefault(wood,entry.templates.get("oak"));
        var template=level.getLevel().getStructureManager().get(id).orElseThrow(()->new IllegalStateException("Missing miniature decoration template: "+id));
        return MiniDecorationPlacement.place(level,chunk,center,template,Rotation.values()[random.nextInt(4)],random);
    }
    private static boolean miniatures(WorldGenLevel level,ChunkPos chunk,RandomSource random,List<MiniStructure> entries){
        boolean any=false;
        for(var entry:entries){
            if(random.nextInt(entry.chunkRarity)!=0)continue;
            var roads=entry.preferRoadside?roadside(level,chunk):List.<BlockPos>of();
            for(int attempt=0;attempt<8;attempt++){
                BlockPos center;
                if(!roads.isEmpty()){
                    var edge=roads.get(random.nextInt(roads.size()));
                    center=surface(level,edge.getX()+random.nextInt(7)-3,edge.getZ()+random.nextInt(7)-3);
                }else center=sample(level,chunk,random);
                if(placeMiniature(level,chunk,center,random,entry)){any=true;break;}
            }
        }
        return any;
    }
}
