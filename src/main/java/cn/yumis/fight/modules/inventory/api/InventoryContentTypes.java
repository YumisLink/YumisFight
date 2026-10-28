package cn.yumis.fight.modules.inventory.api;
import com.mojang.serialization.Codec;
import net.minecraft.resources.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.server.*;
import java.util.*;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.inventory.api.ExtraInventory;

/** Module-owned platform registrations. */
public final class InventoryContentTypes {
    private static final String ID=ItemData.NS;
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS=DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,ID);
    public static final com.mojang.serialization.MapCodec<String> STATE_CODEC=Codec.STRING.listOf().xmap(parts->String.join("",parts),(String value)->{
        List<String> parts=new ArrayList<>();for(int i=0;i<value.length();i+=8000)parts.add(value.substring(i,Math.min(i+8000,value.length())));return parts;
    }).fieldOf("value");
    public static final DeferredHolder<AttachmentType<?>,AttachmentType<String>> STATE=ATTACHMENTS.register("state_modules_v1",()->AttachmentType.builder(()->"").serialize(STATE_CODEC).copyOnDeath().build());
    public static final DeferredHolder<AttachmentType<?>,AttachmentType<ExtraInventory>> EXTRA=ATTACHMENTS.register("extra_container_modules_v1",()->AttachmentType.builder(holder->new ExtraInventory((net.minecraft.world.entity.player.Player)holder)).build());
    public static void register(IEventBus bus){ATTACHMENTS.register(bus);}

}
