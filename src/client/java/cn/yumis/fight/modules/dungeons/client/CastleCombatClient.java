package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.CastleEnemy;
import cn.yumis.fight.modules.dungeons.api.DungeonContentTypes;
import cn.yumis.fight.modules.dungeons.api.KarlPhantomSword;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.HuskRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.function.Consumer;
import java.util.function.Function;

public final class CastleCombatClient {
    private static final Gson JSON = new Gson();

    private CastleCombatClient() {}

    public static void register(IEventBus bus, Function<LivingEntity, Target> context, Consumer<JsonObject> hit) {
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerEntityRenderer(DungeonContentTypes.CASTLE_KNIGHT.get(), HuskRenderer::new);
            event.registerEntityRenderer(DungeonContentTypes.CASTLE_KNIGHT_RESPAWN.get(), NoopRenderer::new);
            event.registerEntityRenderer(DungeonContentTypes.KARL.get(), HuskRenderer::new);
            event.registerEntityRenderer(DungeonContentTypes.KARL_PHANTOM_SWORD.get(), render -> new ThrownItemRenderer<>(render, 1.3f, true));
        });
        NeoForge.EVENT_BUS.addListener((EntityTickEvent.Post event) -> {
            if (!(event.getEntity() instanceof KarlPhantomSword sword) || !sword.level().isClientSide()) return;
            var player = Minecraft.getInstance().player;
            if (player == null || player.level() != sword.level() || !ClientUi.current().ready()
                || !(sword.getOwner() instanceof CastleEnemy owner) || !owner.isAlive() || !sword.crosses(player.getBoundingBox())) return;
            var source = context.apply(owner);
            if (source == null || source.life.dead) return;
            var action = new JsonObject();
            action.addProperty("id", "karl/" + sword.getStringUUID());
            action.addProperty("clientContact", true);
            action.add("boss", JSON.toJsonTree(source));
            action.addProperty("coefficient", 1);
            action.addProperty("magic", false);
            action.addProperty("element", -1);
            hit.accept(action);
        });
    }
}
