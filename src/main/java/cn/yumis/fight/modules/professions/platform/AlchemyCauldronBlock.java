package cn.yumis.fight.modules.professions.platform;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
/** 炼金锅：装水、投草药、点金棒点化，玻璃瓶取药。 */
public final class AlchemyCauldronBlock extends BaseEntityBlock {
    public static final BooleanProperty WATER=BooleanProperty.create("water");
    public static final BooleanProperty HEAT=BooleanProperty.create("heat");
    private static final VoxelShape SHAPE=Shapes.or(Block.box(2,0,2,14,3,14),Block.box(1,3,1,15,12,15));
    public static final MapCodec<AlchemyCauldronBlock> CODEC=simpleCodec(AlchemyCauldronBlock::new);
    public AlchemyCauldronBlock(Properties properties){
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WATER,false).setValue(HEAT,false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(WATER,HEAT);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new AlchemyCauldronBlockEntity(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide()?null:(l,p,s,be)->{if(be instanceof AlchemyCauldronBlockEntity cauldron)cauldron.tick();};
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(level.getBlockEntity(pos) instanceof AlchemyCauldronBlockEntity cauldron))return InteractionResult.PASS;
        if(stack.is(Items.WATER_BUCKET))return cauldron.fill(player,stack)?InteractionResult.SUCCESS:InteractionResult.PASS;
        String held=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if(stack.is(Items.GLASS_BOTTLE)||held.endsWith("_bottle"))return cauldron.bottle(player,stack)?InteractionResult.SUCCESS:InteractionResult.PASS;
        if(cauldron.insert(stack))return InteractionResult.SUCCESS;
        return InteractionResult.PASS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(level.getBlockEntity(pos) instanceof AlchemyCauldronBlockEntity cauldron))return InteractionResult.PASS;
        return cauldron.take(player)?InteractionResult.SUCCESS:InteractionResult.PASS;
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){
        if(level.getBlockEntity(pos) instanceof AlchemyCauldronBlockEntity cauldron)cauldron.drops();
        super.affectNeighborsAfterRemoval(state,level,pos,moved);
    }
    /** 热源：火、营火、岩浆、岩浆块、灵魂火。 */
    public static boolean heatSource(BlockState state){
        if(state.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock)return state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT);
        String path=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.contains("fire")||path.contains("campfire")||path.contains("lava")||path.equals("magma_block");
    }
}
