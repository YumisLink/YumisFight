package cn.yumis.fight.modules.industry.platform.jade;

import cn.yumis.fight.modules.industry.platform.DemonAltarBlock;
import cn.yumis.fight.modules.industry.platform.DemonAltarBlockEntity;
import cn.yumis.fight.modules.industry.platform.DemonAltarBlockEntity.ManaStatus;
import cn.yumis.fight.modules.industry.platform.IndustrialContent;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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
import snownee.jade.api.ui.ResizeableElement;
import snownee.jade.api.view.ProgressView;

@WailaPlugin("jade")
public final class AltarJadePlugin implements IWailaPlugin {
    private static final Identifier UID = IndustrialContent.id("altar_mana");
    private static final String TEXT = "demon_altar." + IndustrialContent.NS + ".";

    @Override public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(DataProvider.INSTANCE, DemonAltarBlockEntity.class);
    }

    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(TooltipProvider.INSTANCE, DemonAltarBlock.class);
    }

    private enum DataProvider implements StreamServerDataProvider<BlockAccessor, ManaStatus> {
        INSTANCE;

        private static final StreamCodec<RegistryFriendlyByteBuf, ManaStatus> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ManaStatus::stored,
            ByteBufCodecs.BOOL, ManaStatus::gridConnected,
            ByteBufCodecs.VAR_LONG, ManaStatus::gridAvailable,
            ByteBufCodecs.LONG, ManaStatus::ritualCost,
            ByteBufCodecs.BOOL, ManaStatus::resultReady,
            ManaStatus::new
        );

        @Override public Identifier getUid() { return UID; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ManaStatus> streamCodec() { return CODEC; }

        @Override public ManaStatus streamData(BlockAccessor accessor) {
            if (accessor.getLevel() instanceof ServerLevel server && accessor.getBlockEntity() instanceof DemonAltarBlockEntity altar) {
                return altar.manaStatus(server);
            }
            return null;
        }
    }

    private enum TooltipProvider implements IBlockComponentProvider {
        INSTANCE;

        private static final int LOCAL_COLOR = 0xff9666d2;
        private static final int GRID_COLOR = 0xff409fb8;

        @Override public Identifier getUid() { return UID; }

        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            var status = DataProvider.INSTANCE.decodeFromData(accessor).orElse(null);
            if (status == null) {
                if (accessor.getBlockEntity() instanceof DemonAltarBlockEntity altar) {
                    tooltip.add(Component.translatable(TEXT + "jade_local", number(altar.storedMana())));
                }
                return;
            }

            tooltip.add(Component.translatable(TEXT + "jade_local", number(status.stored())));
            long cost = status.ritualCost();
            long gridContribution = 0;
            if (status.resultReady()) {
                tooltip.add(Component.translatable(TEXT + "jade_result_ready"));
            } else if (cost > 0) {
                long localContribution = Math.min(status.stored(), cost);
                gridContribution = Math.min(status.gridAvailable(), cost - localContribution);
                tooltip.add(progress(cost, localContribution, gridContribution));
            } else if (cost == 0) {
                tooltip.add(Component.translatable(TEXT + "jade_no_cost"));
            }

            if (!status.gridConnected()) {
                tooltip.add(Component.translatable(TEXT + "offline"));
            } else if (cost > status.stored()) {
                tooltip.add(Component.translatable(TEXT + "jade_grid_supply", number(gridContribution)));
            } else {
                tooltip.add(Component.translatable(TEXT + "jade_grid_available", number(status.gridAvailable())));
            }
        }

        private static ResizeableElement progress(long cost, long local, long grid) {
            var parts = List.of(
                new ProgressView.Part(0, (float) ((double) local / cost), null, null, LOCAL_COLOR),
                new ProgressView.Part(1, (float) ((double) grid / cost), null, null, GRID_COLOR)
            );
            var caption = Component.translatable(TEXT + "jade_ritual_supply", number(local + grid), number(cost));
            return JadeUI.progress(new ProgressView(parts, caption, JadeUI.progressStyle().canDecrease(true), BoxStyle.nestedBox()));
        }

        private static String number(long value) { return String.format(Locale.ROOT, "%,d", value); }
    }
}
