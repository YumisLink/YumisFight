package cn.yumis.fight.modules.mining.client;

import cn.yumis.fight.modules.mining.api.VeinMining;
import cn.yumis.fight.platform.ItemData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class VeinMiningClient {
    private record Preview(ClientLevel level, BlockPos origin, List<BlockPos> extra) {}

    private static final ContextKey<Preview> PREVIEW = new ContextKey<>(
            Identifier.fromNamespaceAndPath(ItemData.NS, "vein_preview"));
    private static KeyMapping key;
    private static IntSupplier limit;
    private static Consumer<JsonObject> send;
    private static Preview preview;

    private VeinMiningClient() {}

    public static void register(IEventBus bus, KeyMapping.Category category, IntSupplier configuredLimit,
                                Consumer<JsonObject> sender) {
        key = new KeyMapping("key.ydnf.vein_mining", InputConstants.KEY_GRAVE, category);
        limit = configuredLimit;
        send = sender;
        bus.addListener((RegisterKeyMappingsEvent event) -> event.register(key));
        NeoForge.EVENT_BUS.addListener(VeinMiningClient::updatePreview);
        NeoForge.EVENT_BUS.addListener(VeinMiningClient::extract);
        NeoForge.EVENT_BUS.addListener(VeinMiningClient::submit);
    }

    public static JsonObject plan(BlockPos origin, int sequence) {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc)) return null;

        List<BlockPos> selected = VeinMining.select(mc.level, mc.player, origin, limit.getAsInt());
        if (selected.size() < 2) return null;

        var packet = new JsonObject();
        packet.addProperty("sequence", sequence);
        packet.addProperty("dimension", mc.level.dimension().identifier().toString());
        packet.addProperty("block", BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(origin).getBlock()).toString());
        var positions = new JsonArray();
        for (BlockPos pos : selected) positions.add(pos.asLong());
        packet.add("positions", positions);
        return packet;
    }

    public static void submitPlan(JsonObject plan) {
        send.accept(plan);
    }

    private static boolean active(Minecraft mc) {
        return key != null && key.isDown() && mc.gui.screen() == null && mc.player != null && mc.level != null
                && mc.gameMode != null && VeinMining.canMine(mc.player, mc.gameMode.getPlayerMode());
    }

    private static void updatePreview(ClientTickEvent.Post event) {
        while (key.consumeClick()) {}
        preview = null;
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc) || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos origin = hit.getBlockPos();
        List<BlockPos> selected = VeinMining.select(mc.level, mc.player, origin, limit.getAsInt());
        if (selected.size() > 1) {
            preview = new Preview(mc.level, origin, List.copyOf(selected.subList(1, selected.size())));
        }
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (preview != null && preview.level() == mc.level && active(mc)
                && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(preview.origin())) {
            event.getRenderState().setRenderData(PREVIEW, preview);
        }
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        var state = event.getLevelRenderState();
        var frame = state.getRenderData(PREVIEW);
        if (frame == null || state.cameraRenderState == null) return;
        Vec3 camera = state.cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.linesTranslucent(),
                (pose, vertices) -> {
                    for (BlockPos pos : frame.extra()) {
                        Shapes.block().forAllEdges((x0, y0, z0, x1, y1, z1) -> {
                            float dx = (float) (x1 - x0);
                            float dy = (float) (y1 - y0);
                            float dz = (float) (z1 - z0);
                            float x = (float) (pos.getX() - camera.x + x0);
                            float y = (float) (pos.getY() - camera.y + y0);
                            float z = (float) (pos.getZ() - camera.z + z0);
                            vertices.addVertex(pose, x, y, z)
                                    .setColor(113, 232, 211, 220).setNormal(pose, dx, dy, dz).setLineWidth(1.5f);
                            vertices.addVertex(pose, x + dx, y + dy, z + dz)
                                    .setColor(113, 232, 211, 220).setNormal(pose, dx, dy, dz).setLineWidth(1.5f);
                        });
                    }
                });
    }
}
