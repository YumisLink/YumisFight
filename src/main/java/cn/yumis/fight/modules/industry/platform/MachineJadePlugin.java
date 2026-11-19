package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.platform.IndustrialWorld.GridMana;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.api.view.ProgressView;

@WailaPlugin("jade")
public final class MachineJadePlugin implements IWailaPlugin {
    private static final Identifier UID = IndustrialContent.id("machine_mana");
    private static final List<Class<? extends Block>> MACHINES = List.of(MachineBlock.class,
        WindGeneratorPartBlock.class, ArtificialStarPartBlock.class, IndustrialAssemblyPartBlock.class);

    @Override public void register(IWailaCommonRegistration registration) {
        for (var type : MACHINES) registration.registerBlockDataProvider(DataProvider.INSTANCE, type);
    }

    @Override public void registerClient(IWailaClientRegistration registration) {
        for (var type : MACHINES) registration.registerBlockComponent(TooltipProvider.INSTANCE, type);
    }

    private enum DataProvider implements StreamServerDataProvider<BlockAccessor, GridMana> {
        INSTANCE;

        private static final StreamCodec<RegistryFriendlyByteBuf, GridMana> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GridMana::connected,
            ByteBufCodecs.VAR_LONG, GridMana::stored,
            ByteBufCodecs.VAR_LONG, GridMana::capacity,
            ByteBufCodecs.VAR_LONG, GridMana::generatedPerSecond,
            GridMana::new
        );

        @Override public Identifier getUid() { return UID; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, GridMana> streamCodec() { return CODEC; }

        @Override public GridMana streamData(BlockAccessor accessor) {
            if (!(accessor.getLevel() instanceof ServerLevel server)) return null;
            BlockPos base = controllerPosition(server, accessor);
            if (!server.hasChunkAt(base) || !(server.getBlockEntity(base) instanceof MachineBlockEntity machine)) return null;
            var target = accessor.getBlockState().getBlock();
            if (target instanceof WindGeneratorPartBlock && !WindGeneratorPartBlock.isWind(machine.getBlockState())
                || target instanceof ArtificialStarPartBlock && !ArtificialStarPartBlock.isStar(machine.getBlockState())) return null;
            return IndustrialWorld.get(server).gridMana(machine);
        }

        private static BlockPos controllerPosition(ServerLevel server, BlockAccessor accessor) {
            BlockPos pos = accessor.getPosition();
            var state = accessor.getBlockState();
            return switch (state.getBlock()) {
                case WindGeneratorPartBlock ignored -> WindGeneratorPartBlock.base(server, pos);
                case ArtificialStarPartBlock ignored -> ArtificialStarPartBlock.base(server, pos);
                case IndustrialAssemblyPartBlock ignored -> {
                    var part = IndustrialAssembly.part(state.getValue(IndustrialAssemblyPartBlock.PART));
                    var base = part.controller(pos, state.getValue(MachineBlock.FACING));
                    yield server.hasChunkAt(base) ? IndustrialAssemblyPartBlock.base(server, pos) : base;
                }
                default -> pos;
            };
        }
    }

    private enum TooltipProvider implements IBlockComponentProvider {
        INSTANCE;

        private static final int GRID_COLOR = 0xff409fb8;

        @Override public Identifier getUid() { return UID; }

        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            DataProvider.INSTANCE.decodeFromData(accessor).ifPresent(mana -> append(tooltip, mana));
        }

        private static void append(ITooltip tooltip, GridMana mana) {
            if (!mana.connected()) {
                tooltip.add(Component.translatable("machine." + IndustrialContent.NS + ".jade_grid_offline"));
                return;
            }

            var caption = Component.literal(mana.stored() + "/" + mana.capacity() + "(+ " + mana.generatedPerSecond() + "/s)");
            if (mana.capacity() <= 0) {
                tooltip.add(caption);
                return;
            }
            float ratio = (float) Math.clamp((double) mana.stored() / mana.capacity(), 0, 1);
            var part = ProgressView.Part.of(ratio, GRID_COLOR);
            tooltip.add(JadeUI.progress(new ProgressView(part, caption, JadeUI.progressStyle().canDecrease(true), BoxStyle.nestedBox())));
        }
    }
}
