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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
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
    static {PROCESSORS.register("attached_entities",()->ATTACHED_ENTITIES);}
    private WildBuildingWorldgen() {}
    public static void register(IEventBus bus) {STRUCTURES.register(bus);PROCESSORS.register(bus);}

    public static final class WildStructure extends Structure {
        public static final MapCodec<WildStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
                settingsCodec(i),
                StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s->s.pool),
                Codec.intRange(-64,256).fieldOf("height_offset").forGetter(s->s.heightOffset),
                Codec.intRange(0,256).fieldOf("floor_offset").forGetter(s->s.floorOffset),
                Codec.intRange(0,256).fieldOf("max_relief").forGetter(s->s.maxRelief),
                Codec.intRange(0,256).fieldOf("ground_clearance").forGetter(s->s.groundClearance)
        ).apply(i,WildStructure::new));
        private final Holder<StructureTemplatePool> pool;
        private final int heightOffset,floorOffset,maxRelief,groundClearance;
        private final JigsawStructure delegate;
        private WildStructure(StructureSettings settings,Holder<StructureTemplatePool> pool,int heightOffset,int floorOffset,int maxRelief,int groundClearance) {
            super(settings);this.pool=pool;this.heightOffset=heightOffset;this.floorOffset=floorOffset;this.maxRelief=maxRelief;this.groundClearance=groundClearance;
            // Depth one is required for the native assembler to retain the starting piece.
            this.delegate=new JigsawStructure(settings,pool,1,ConstantHeight.of(VerticalAnchor.absolute(heightOffset)),false,Heightmap.Types.WORLD_SURFACE_WG);
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
            if(max-min>maxRelief||groundClearance>0&&box.minY()+floorOffset-max<groundClearance)return Optional.empty();
            // Reuse the already chosen rotation and pieces; don't draw a second structure.
            return Optional.of(new GenerationStub(stub.position(),Either.right(builder)));
        }
        @Override public StructureType<?> type() {return TYPE.get();}
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
}
