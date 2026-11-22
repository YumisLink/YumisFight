package cn.yumis.fight.modules.economy.client;

import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import cn.yumis.fight.platform.ItemData;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;

/** Rarity pillars over landed mod gear, LootBeams style: bright core plus faint glow, fading near the picker. */
public final class LootBeamVisuals {
    private static final ContextKey<Frame> FRAME =
        new ContextKey<>(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "loot_beams"));
    private static final double REACH_SQR = 48 * 48D;
    private record Frame(List<Quad> quads) {}
    private record Beam(ItemStack stack, Rarity rarity) {}
    private static final Map<UUID, Beam> CACHE = new LinkedHashMap<>();
    private static Object world;

    private LootBeamVisuals() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LootBeamVisuals::extract);
        NeoForge.EVENT_BUS.addListener(LootBeamVisuals::submit);
    }

    private static void extract(ExtractLevelRenderStateEvent event) {
        List<Quad> quads = new ArrayList<>();
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (minecraft.level != null && player != null) {
            if (world != minecraft.level) {
                world = minecraft.level;
                CACHE.clear();
            }
            long time = minecraft.level.getGameTime();
            Set<UUID> seen = new HashSet<>();
            for (var raw : minecraft.level.entitiesForRendering()) {
                if (!(raw instanceof ItemEntity item)) continue;
                var stack = item.getItem();
                if (stack.isEmpty() || player.distanceToSqr(item) > REACH_SQR) continue;
                var beam = CACHE.get(item.getUUID());
                if (beam == null || beam.stack() != stack) {
                    beam = new Beam(stack, gearRarity(stack));
                    CACHE.put(item.getUUID(), beam);
                }
                seen.add(item.getUUID());
                if (beam.rarity() == null || beam.rarity().ordinal() < Rarity.ARTIFACT.ordinal()) continue;
                // LootBeams fades the beam within two blocks so it never blocks the picker's view.
                double fade = Math.min(1, player.distanceToSqr(item) / 4);
                if (fade <= .05) continue;
                int color = beam.rarity().color();
                double height = 2.2 + .4 * (beam.rarity().ordinal() - Rarity.ARTIFACT.ordinal());
                double breathe = .86 + .14 * Math.sin((time + (item.getUUID().getLeastSignificantBits() & 15)) * Math.PI / 16);
                Vec3 base = item.position().add(0, .2, 0);
                WorldQuads.beam(quads, base, base.add(0, height, 0), color, .07, (int) (205 * breathe * fade),
                    color, .1, (int) (70 * breathe * fade));
            }
            CACHE.keySet().retainAll(seen);
        }
        event.getRenderState().setRenderData(FRAME, new Frame(List.copyOf(quads)));
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Frame frame = event.getLevelRenderState().getRenderData(FRAME);
        if (frame != null) WorldQuads.submit(event, frame.quads());
    }

    /** Equipment only; other mod items such as materials and currency never grow beams. */
    private static Rarity gearRarity(ItemStack stack) {
        if (!ItemData.own(stack)) return null;
        var view = EquipmentItems.view(stack);
        return view == null ? null : view.rarity();
    }
}
