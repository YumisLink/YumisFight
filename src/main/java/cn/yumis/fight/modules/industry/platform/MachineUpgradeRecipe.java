package cn.yumis.fight.modules.industry.platform;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

/** Ordinary shaped crafting, except machines with stored contents must be emptied by placing/breaking first. */
public record MachineUpgradeRecipe(ShapedRecipe base) implements CraftingRecipe {
    public static final MapCodec<MachineUpgradeRecipe> CODEC=ShapedRecipe.MAP_CODEC.xmap(MachineUpgradeRecipe::new,MachineUpgradeRecipe::base);
    public static final StreamCodec<RegistryFriendlyByteBuf,MachineUpgradeRecipe> STREAM_CODEC=ShapedRecipe.STREAM_CODEC.map(MachineUpgradeRecipe::new,MachineUpgradeRecipe::base);
    private static boolean clean(CraftingInput input){for(int i=0;i<input.size();i++)if(input.getItem(i).has(DataComponents.BLOCK_ENTITY_DATA))return false;return true;}
    @Override public boolean matches(CraftingInput input,Level level){return clean(input)&&base.matches(input,level);}
    @Override public ItemStack assemble(CraftingInput input){return clean(input)?base.assemble(input):ItemStack.EMPTY;}
    @Override public RecipeSerializer<MachineUpgradeRecipe> getSerializer(){return IndustrialContent.MACHINE_UPGRADE_SERIALIZER.get();}
    @Override public CraftingBookCategory category(){return base.category();}
    @Override public String group(){return base.group();}
    @Override public boolean showNotification(){return base.showNotification();}
    @Override public PlacementInfo placementInfo(){return base.placementInfo();}
    @Override public List<RecipeDisplay> display(){return base.display();}
}
