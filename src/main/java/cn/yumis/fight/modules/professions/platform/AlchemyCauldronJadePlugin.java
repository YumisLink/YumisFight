package cn.yumis.fight.modules.professions.platform;

import cn.yumis.fight.platform.ItemData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Jade tooltip provider for alchemy cauldrons: shows water, heat, cooking progress and brewed potions. */
@WailaPlugin("jade")
public final class AlchemyCauldronJadePlugin implements IWailaPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(ItemData.NS, "alchemy_cauldron");

    @Override public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(DataProvider.INSTANCE, AlchemyCauldronBlockEntity.class);
    }

    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(TooltipProvider.INSTANCE, AlchemyCauldronBlock.class);
    }

    private enum DataProvider implements StreamServerDataProvider<BlockAccessor, String> {
        INSTANCE;

        private static final StreamCodec<RegistryFriendlyByteBuf, String> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, s -> s, s -> s
        );

        @Override public Identifier getUid() { return UID; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, String> streamCodec() { return CODEC; }

        @Override public String streamData(BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof AlchemyCauldronBlockEntity cauldron) {
                boolean heated = AlchemyCauldronBlock.heatSource(accessor.getLevel().getBlockState(accessor.getPosition().below()));
                return cauldron.statusText(heated).getString();
            }
            return "";
        }
    }

    private enum TooltipProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override public Identifier getUid() { return UID; }

        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            String text = DataProvider.INSTANCE.decodeFromData(accessor).orElse("");
            if (text.isEmpty() && accessor.getBlockEntity() instanceof AlchemyCauldronBlockEntity cauldron) {
                boolean heated = AlchemyCauldronBlock.heatSource(accessor.getLevel().getBlockState(accessor.getPosition().below()));
                text = cauldron.statusText(heated).getString();
            }
            if (!text.isEmpty()) tooltip.add(Component.literal(text));
        }
    }
}
