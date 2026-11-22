package cn.yumis.fight.modules.mapgeneration.platform;

import cn.yumis.fight.platform.ItemData;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.Optional;

/** Native, chunk-bounded structures made from the map module's authored facilities. */
public final class WildBuildingWorldgen {
    private static final DeferredRegister<StructureType<?>> STRUCTURES=DeferredRegister.create(Registries.STRUCTURE_TYPE,ItemData.NS);
    private static final DeferredRegister<MapCodec<? extends StructureProcessor>> PROCESSORS=DeferredRegister.create(Registries.STRUCTURE_PROCESSOR,ItemData.NS);
    private static final DeferredHolder<StructureType<?>,StructureType<WildStructure>> TYPE=STRUCTURES.register("wild_building",()->()->WildStructure.CODEC);
    private static final MapCodec<AttachedEntities> ATTACHED_ENTITIES=MapCodec.unit(new AttachedEntities());
    private static final MapCodec<WaterlogFix> WATERLOG_FIX=MapCodec.unit(new WaterlogFix());
    static {PROCESSORS.register("attached_entities",()->ATTACHED_ENTITIES);PROCESSORS.register("waterlog_fix",()->WATERLOG_FIX);}
    private WildBuildingWorldgen() {}
    public static void register(IEventBus bus) {STRUCTURES.register(bus);PROCESSORS.register(bus);}

    public static final class WildStructure extends Structure {
        public static final MapCodec<WildStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
                settingsCodec(i),
                StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s->s.pool),
                Codec.intRange(0,128).optionalFieldOf("size",1).forGetter(s->s.size),
                Codec.intRange(-64,256).fieldOf("height_offset").forGetter(s->s.heightOffset),
                Codec.intRange(0,256).fieldOf("floor_offset").forGetter(s->s.floorOffset),
                Codec.intRange(0,256).fieldOf("max_relief").forGetter(s->s.maxRelief),
                Codec.intRange(0,256).fieldOf("ground_clearance").forGetter(s->s.groundClearance),
                BlockPos.CODEC.optionalFieldOf("surface_anchor").forGetter(s->s.surfaceAnchor)
        ).apply(i,WildStructure::new));
        private final Holder<StructureTemplatePool> pool;
        private final int size,heightOffset,floorOffset,maxRelief,groundClearance;
        private final Optional<BlockPos> surfaceAnchor;
        private final JigsawStructure delegate;
        private WildStructure(StructureSettings settings,Holder<StructureTemplatePool> pool,int size,int heightOffset,int floorOffset,int maxRelief,int groundClearance,Optional<BlockPos> surfaceAnchor) {
            super(settings);this.pool=pool;this.size=size;this.heightOffset=heightOffset;this.floorOffset=floorOffset;this.maxRelief=maxRelief;this.groundClearance=groundClearance;
            this.surfaceAnchor=surfaceAnchor;
            // Depth one retains only the starting piece; deeper sizes let authored pools assemble multi-piece buildings.
            this.delegate=new JigsawStructure(settings,pool,size,ConstantHeight.of(VerticalAnchor.absolute(heightOffset)),false,Heightmap.Types.WORLD_SURFACE_WG);
        }
        @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
            var candidate=delegate.findGenerationPoint(context);
            if(candidate.isEmpty())return Optional.empty();
            var stub=candidate.get();var builder=stub.getPiecesBuilder();
            if(builder.isEmpty())return Optional.empty();
            var box=builder.getBoundingBox();int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
            // Biomes are quart-resolution. Sampling the whole footprint also excludes ocean edges.
            for(int x=box.minX();x<=box.maxX();x=x==box.maxX()?box.maxX()+1:Math.min(box.maxX(),x+4))
                for(int z=box.minZ();z<=box.maxZ();z=z==box.maxZ()?box.maxZ()+1:Math.min(box.maxZ(),z+4)) {
                    int top=context.chunkGenerator().getFirstFreeHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());
                    int bottom=context.chunkGenerator().getFirstFreeHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
                    if(top>bottom)return Optional.empty();
                    var biome=context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(top-1),QuartPos.fromBlock(z),context.randomState().sampler());
                    if(!context.validBiome().test(biome))return Optional.empty();
                    min=Math.min(min,top);max=Math.max(max,top);
                }
            if (max - min > maxRelief) return Optional.empty();
            BlockPos reference = stub.position();
            int offset = 0;
            if (surfaceAnchor.isPresent()) {
                if (!(builder.build().pieces().getFirst() instanceof PoolElementStructurePiece start)) return Optional.empty();
                BlockPos anchor = StructureTemplate.transform(surfaceAnchor.get(), Mirror.NONE, start.getRotation(), BlockPos.ZERO)
                    .offset(start.getPosition());
                if (!box.isInside(anchor)) return Optional.empty();
                int ground = context.chunkGenerator().getFirstFreeHeight(anchor.getX(), anchor.getZ(),
                    Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
                int floor = context.chunkGenerator().getFirstFreeHeight(anchor.getX(), anchor.getZ(),
                    Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
                if (ground > floor || Math.max(max, ground) - Math.min(min, ground) > maxRelief) return Optional.empty();
                offset = ground - anchor.getY();
                int minY = context.heightAccessor().getMinY();
                if (box.minY() + offset < minY || box.maxY() + offset >= minY + context.heightAccessor().getHeight()) return Optional.empty();
                reference = anchor.above(offset - 1);
            }
            if (groundClearance > 0 && box.minY() + offset + floorOffset - max < groundClearance) return Optional.empty();
            if (offset != 0) builder.offsetPiecesVertically(offset);
            return Optional.of(new GenerationStub(reference, Either.right(builder)));
        }
        @Override public StructureType<?> type() {return TYPE.get();}

        /**
         * 放置后头顶净空：每座建筑随机抽 1~2 个占地立柱向上探测，自然地表压到屋顶
         * 以上才对整座建筑清理屋顶上方净空带，否则视为通过。种子混入世界种子与
         * 包围盒角点，同一建筑的各区块调用得到相同采样点，判定整座一致；写入由
         * {@link OverheadClearance} 裁剪到当前区块切片，受保护方块一并跳过。
         */
        @Override public void afterPlace(WorldGenLevel level,StructureManager structureManager,ChunkGenerator generator,
                RandomSource random,BoundingBox chunkBB,ChunkPos chunkPos,PiecesContainer pieces) {
            BoundingBox box=pieces.calculateBoundingBox();
            long seed=level.getSeed()^((long)box.minX()*341873128712L)^((long)box.minZ()*132897987541L)^((long)box.maxY()*668268263L);
            var randomState=level.getLevel().getChunkSource().randomState();
            if(!OverheadClearance.hangsOverhead(seed,box,(x,z)->
                    generator.getFirstFreeHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,randomState)))return;
            OverheadClearance.clear(box,chunkBB,(x,y,z)->{
                var state=level.getBlockState(new BlockPos(x,y,z));
                return state.isAir()||state.is(BlockTags.FEATURES_CANNOT_REPLACE);
            },(x,y,z)->level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS));
        }
    }

    /** The vanilla template entity hook needs the transformed hanging position in entity NBT too. */
    private static final class AttachedEntities implements StructureProcessor {
        @Override public StructureTemplate.StructureEntityInfo processEntity(LevelReader world,BlockPos origin,StructureTemplate.StructureEntityInfo original,
                StructureTemplate.StructureEntityInfo placed,StructurePlaceSettings settings,StructureTemplate template) {
            String id=placed.nbt.getStringOr("id","");
            if(!id.equals("minecraft:item_frame")&&!id.equals("minecraft:glow_item_frame")&&!id.equals("minecraft:painting"))return placed;
            var tag=placed.nbt.copy();var pos=placed.blockPos;
            tag.put("block_pos",new IntArrayTag(new int[]{pos.getX(),pos.getY(),pos.getZ()}));
            return new StructureTemplate.StructureEntityInfo(placed.pos,pos,tag);
        }
        @Override public MapCodec<? extends StructureProcessor> codec() {return ATTACHED_ENTITIES;}
    }

    /**
     * Placed fluid-bearing states (authored water or mis-waterlogged blocks) only get neighbour
     * updates from vanilla template placement, never a scheduled tick, so they stay static.
     * Scheduling an immediate block tick lets them settle right after placement; ticks are only
     * legal inside the worldgen region's centre chunk, which is also the chunk being written.
     */
    private static final class WaterlogFix implements StructureProcessor {
        @Override public StructureTemplate.StructureBlockInfo processBlock(LevelReader world,BlockPos origin,BlockPos pos,BlockPos relativePos,
                StructureTemplate.StructureBlockInfo info,StructurePlaceSettings settings) {
            if(info.state().getFluidState().isEmpty())return info;
            if(world instanceof WorldGenRegion region&&!region.getCenter().equals(new ChunkPos(info.pos().getX()>>4,info.pos().getZ()>>4)))return info;
            if(world instanceof LevelAccessor accessor)accessor.scheduleTick(info.pos(),info.state().getBlock(),0);
            return info;
        }
        @Override public MapCodec<? extends StructureProcessor> codec() {return WATERLOG_FIX;}
    }
}
