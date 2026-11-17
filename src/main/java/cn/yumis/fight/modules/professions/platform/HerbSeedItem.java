package cn.yumis.fight.modules.professions.platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
/**
 * 草药种子。
 * 普通草药只能种在耕地；雪地/沼泽/灵魂土只对应特殊环境；水下耕地长海底草药（海妖花/船锚珊瑚）；
 * 噩梦藤只能贴在绯红/诡异菌木侧面。
 */
public final class HerbSeedItem extends Item {
    public HerbSeedItem(Properties properties){
        super(properties);
    }
    @Override public InteractionResult useOn(UseOnContext context){
        Level level=context.getLevel();
        if(level.isClientSide())return InteractionResult.SUCCESS;
        BlockPos pos=context.getClickedPos();
        BlockState clicked=level.getBlockState(pos);
        Direction face=context.getClickedFace();
        if(NightmareVineBlock.isStem(clicked))return plantVine(level,pos,face,context,HerbContent.NIGHTMARE_VINE.get().defaultBlockState());
        if(StormVineBlock.isRock(clicked))return plantVine(level,pos,face,context,HerbContent.STORM_VINE.get().defaultBlockState());
        String id=BuiltInRegistries.BLOCK.getKey(clicked.getBlock()).getPath();
        boolean farmland=id.equals("farmland");
        boolean soul=id.contains("soul_soil")||id.contains("soul_sand");
        boolean soilLike=id.equals("grass_block")||id.equals("dirt")||id.equals("podzol")||id.equals("coarse_dirt")
            ||id.equals("rooted_dirt")||id.equals("moss_block")||id.equals("mud");
        boolean specialBiome=soilLike&&(snowyBiome(level,pos)||swampBiome(level,pos));
        boolean stoneTarget=id.contains("stone");
        boolean boneTarget=id.equals("bone_block");
        boolean obsidianTarget=id.equals("obsidian");
        boolean mushroomStyle=soilLike&&level.getMaxLocalRawBrightness(pos.above())<13;
        if(!farmland&&!soul&&!specialBiome&&!stoneTarget&&!boneTarget&&!obsidianTarget&&!mushroomStyle)return InteractionResult.PASS;
        BlockPos above=pos.above();
        BlockState aboveState=level.getBlockState(above);
        // 水下耕地：像水稻一样长海底草药
        if(farmland&&aboveState.is(Blocks.WATER)){
            level.setBlockAndUpdate(above,HerbContent.OCEAN_HERB.get().defaultBlockState().setValue(OceanHerbBlock.WATERLOGGED,true));
            context.getItemInHand().shrink(1);
            return InteractionResult.SUCCESS;
        }
        if(!aboveState.isAir())return InteractionResult.PASS;
        level.setBlockAndUpdate(above,HerbContent.HERB_BUSH.get().defaultBlockState());
        context.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }
    private static InteractionResult plantVine(Level level,BlockPos stemPos,Direction face,UseOnContext context,BlockState vine){
        if(!face.getAxis().isHorizontal())return InteractionResult.PASS;
        BlockPos target=stemPos.relative(face);
        if(!level.getBlockState(target).isAir())return InteractionResult.PASS;
        if(!vine.canSurvive(level,target))return InteractionResult.PASS;
        level.setBlockAndUpdate(target,vine);
        context.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }
    private static String biomePath(Level level,BlockPos pos){
        var id=level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getKey(level.getBiome(pos).value());
        return id==null?"":id.getPath();
    }
    private static boolean snowyBiome(Level level,BlockPos pos){
        String path=biomePath(level,pos);
        return path.contains("snow")||path.contains("frozen")||path.contains("ice")||path.equals("grove");
    }
    private static boolean swampBiome(Level level,BlockPos pos){
        return biomePath(level,pos).contains("swamp");
    }
}
