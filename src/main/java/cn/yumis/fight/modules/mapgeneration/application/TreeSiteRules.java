package cn.yumis.fight.modules.mapgeneration.application;

import java.util.Locale;
import java.util.Set;

/**
 * Pure site rules for blueprint trees, kept free of Minecraft types so the decision can be
 * verified headlessly. A tree facility is dropped entirely when its trunk would stand in
 * water, hang without support, or sit in a biome where trees never grow naturally.
 */
final class TreeSiteRules {
    /** Vanilla overworld biomes without any natural tree generation. */
    private static final Set<String> TREELESS=Set.of(
        "desert","badlands","eroded_badlands",
        "snowy_plains","ice_spikes","snowy_slopes","frozen_peaks","jagged_peaks","stony_peaks",
        "windswept_gravelly_hills","beach","snowy_beach","stony_shore","mushroom_fields");

    private TreeSiteRules() {}

    /** Accepts full ids like "minecraft:desert" or bare paths like "desert". */
    static boolean treeless(String biome) {
        if(biome==null)return false;
        String path=biome;
        int colon=biome.indexOf(':');
        if(colon>=0)path=biome.substring(colon+1);
        return TREELESS.contains(path.toLowerCase(Locale.ROOT));
    }

    /**
     * Rejects a tree site when the trunk base is flooded, has no solid support under it
     * (floating maps without an island), or the biome never grows trees.
     */
    static boolean reject(boolean trunkInWater,boolean trunkUnsupported,String biome) {
        return trunkInWater||trunkUnsupported||treeless(biome);
    }
}
