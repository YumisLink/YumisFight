package cn.yumis.fight.modules.industry.platform;

import com.google.gson.*;

import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;

import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;

import net.minecraft.world.level.saveddata.*;

import net.neoforged.neoforge.transfer.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.fluid.*;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Industrial module implementation; owns one scheduling responsibility. */
public final class IndustrialFluids {
    public static boolean matches(FluidResource resource,String id){if(id.startsWith("#"))return resource.toStack(1).is(TagKey.create(Registries.FLUID,Identifier.parse(id.substring(1))));return resource.is(BuiltInRegistries.FLUID.getValue(Identifier.parse(id)));}
}
