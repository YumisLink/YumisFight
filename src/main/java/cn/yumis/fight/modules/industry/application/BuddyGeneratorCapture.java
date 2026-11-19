package cn.yumis.fight.modules.industry.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.industry.api.DeviceType;
import cn.yumis.fight.modules.industry.api.IndustrialPorts;
import cn.yumis.fight.modules.industry.platform.IndustrialContent;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.pig.Pig;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/** The inventory swap and permanent removal of this pig share one WAL commit. */
public final class BuddyGeneratorCapture {
    private static volatile Journal journal;

    private BuddyGeneratorCapture() {}

    public static void start(Journal storage, MinecraftServer server) {
        journal = storage;
        // Spawn-area entities may have loaded before the application started.
        for (var level : server.getAllLevels()) {
            var captured = new ArrayList<Pig>();
            for (var entity : level.getAllEntities()) {
                if (entity instanceof Pig pig && captured(storage, pig.getUUID())) captured.add(pig);
            }
            captured.forEach(Pig::discard);
        }
    }

    public static void stop() { journal = null; }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BuddyGeneratorCapture::interact);
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            Journal storage = journal;
            if (storage != null && !event.getLevel().isClientSide()
                && event.getEntity() instanceof Pig pig && captured(storage, pig.getUUID())) event.setCanceled(true);
        });
    }

    private static String key(UUID pig) { return "buddyCapture/" + pig; }

    private static boolean captured(Journal storage, UUID pig) { return !storage.get(key(pig)).isJsonNull(); }

    private static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Pig pig) || !pig.isAlive()
            || !event.getItemStack().is(IndustrialContent.ALL_ITEMS.get(DeviceType.BUDDY_SHELL.id).get())) return;
        if (event.getEntity().isSpectator() || !event.getEntity().getAbilities().mayBuild) return;

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        Journal storage = journal;
        if (!(event.getEntity() instanceof ServerPlayer player) || storage == null || !IndustrialPorts.ready()) return;
        if (!player.level().mayInteract(player, pig.blockPosition())) return;
        UUID pigId = pig.getUUID();
        if (captured(storage, pigId)) {
            pig.discard();
            return;
        }

        var hand = event.getHand();
        var intent = new JsonObject();
        intent.addProperty("pig", pigId.toString());
        intent.addProperty("hand", hand.name());
        try {
            boolean committed = IndustrialPorts.inventory().transact(player, "buddy-capture/" + pigId, intent,
                () -> player.setItemInHand(hand, IndustrialContent.stack(DeviceType.BUDDY_GENERATOR.id, 1)),
                Map.of(key(pigId), new JsonPrimitive(player.getUUID().toString())));
            if (committed) {
                pig.playAmbientSound();
                pig.discard();
                player.sendOverlayMessage(Component.translatable("item.yumis_dungeon_and_fight.buddy_generator_shell.captured"));
            } else if (captured(storage, pigId)) {
                pig.discard();
            }
        } catch (RuntimeException failure) {
            if (captured(storage, pigId)) pig.discard();
            IndustrialPorts.message(player, failure.getMessage() == null ? "激活失败，请重试。" : failure.getMessage());
        }
    }
}
