package cn.yumis.fight.modules.industry.platform;
import cn.yumis.fight.modules.industry.api.IndustrialRules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;

import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class IndustrialWorldgen {
    private static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,IndustrialContent.NS);
    private static final DeferredHolder<Feature<?>,IndustrialFeature> FEATURE=FEATURES.register("industrial_resources",IndustrialFeature::new);
    public static void register(IEventBus bus){FEATURES.register(bus);}
    public record Config(String kind) implements FeatureConfiguration {public static final Codec<Config> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("kind").forGetter(Config::kind)).apply(i,Config::new));}
    public static final class IndustrialFeature extends Feature<Config> {
        IndustrialFeature(){super(Config.CODEC);}
        @Override public boolean place(FeaturePlaceContext<Config> context){
            var level=context.level();var random=context.random();BlockPos origin=context.origin();IndustrialRules rules=IndustrialRules.current();String kind=context.config().kind;
            if(kind.equals("grass")){
                if(!rules.magicWorldgen)return false;boolean any=false;var chunk=level.getChunk(origin);
                for(int section=0;section<chunk.getSections().length;section++){var cells=chunk.getSection(section);if(!cells.maybeHas(state->state.is(Blocks.SHORT_GRASS)))continue;int y0=SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(section));for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<16;y++)if(cells.getBlockState(x,y,z).is(Blocks.SHORT_GRASS)&&random.nextInt(rules.magicGrassChance)==0){level.setBlock(new BlockPos(origin.getX()+x,y0+y,origin.getZ()+z),IndustrialContent.ALL_BLOCKS.get("magic_grass").get().defaultBlockState(),2);any=true;}}
                return any;
            }
            if(kind.equals("lead")&&!rules.leadWorldgen||kind.equals("uranium")&&!rules.uraniumWorldgen||kind.equals("titanium")&&!rules.titaniumWorldgen||kind.equals("magic")&&!rules.magicWorldgen)return false;
            IndustrialRules.Ore rule=rules.ores.get(kind);if(rule==null||random.nextInt(rule.chance())!=0)return false;
            if(kind.equals("titanium")&&(long)origin.getX()*origin.getX()+(long)origin.getZ()*origin.getZ()<1024L*1024)return false;
            Block block=IndustrialContent.ALL_BLOCKS.get(kind.equals("magic")?"magic_ore":kind+"_ore").get();boolean any=false;
            for(int attempt=0;attempt<rule.attempts();attempt++){
                int target=rule.minSize()+random.nextInt(rule.maxSize()-rule.minSize()+1);BlockPos p=new BlockPos(origin.getX()+random.nextInt(16),rule.minY()+random.nextInt(rule.maxY()-rule.minY()+1),origin.getZ()+random.nextInt(16));
                for(int step=0;step<target;step++){var state=level.getBlockState(p);if(kind.equals("titanium")?state.is(Blocks.END_STONE)&&(long)p.getX()*p.getX()+(long)p.getZ()*p.getZ()>=1024L*1024:state.is(BlockTags.BASE_STONE_OVERWORLD)){level.setBlock(p,block.defaultBlockState(),2);any=true;}BlockPos next=p.relative(Direction.getRandom(random));p=new BlockPos(next.getX(),Math.clamp(next.getY(),rule.minY(),rule.maxY()),next.getZ());}
            }return any;
        }
    }
}
