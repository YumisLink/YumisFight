package cn.yumis.fight.modules.industry.platform;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Consumer;

final class IndustrialAssemblyItem extends BlockItem {
    private final boolean shell;

    IndustrialAssemblyItem(Block block, boolean shell, Properties properties) {
        super(block, properties);
        this.shell = shell;
    }

    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        if (!(context.getLevel() instanceof ServerLevel level)) return super.placeBlock(context, state);
        var assembly = IndustrialAssembly.of(((MachineBlock) getBlock()).initial.device());
        var facing = state.getValue(MachineBlock.FACING);
        var pos = context.getClickedPos();
        var original = level.getBlockState(pos);
        if (!assembly.canPlace(context, facing) || !super.placeBlock(context, state)) return false;
        if (assembly.assemble(level, pos, facing)) return true;
        level.setBlock(pos, original, IndustrialAssembly.PLACEMENT_FLAGS);
        return false;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                          Consumer<Component> tooltip, TooltipFlag flag) {
        if (shell) tooltip.accept(Component.translatable("item.yumis_dungeon_and_fight.buddy_generator_shell.hint"));
    }
}
