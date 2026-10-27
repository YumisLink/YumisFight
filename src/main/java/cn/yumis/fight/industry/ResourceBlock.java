package cn.yumis.fight.industry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.List;

public final class ResourceBlock extends Block {
    private final String metal;
    public ResourceBlock(String metal,BlockBehaviour.Properties p){super(p);this.metal=metal;}
    @Override protected MapCodec<? extends Block> codec(){return simpleCodec(p->new ResourceBlock(metal,p));}
    static int enchant(LootParams.Builder params,net.minecraft.resources.ResourceKey<Enchantment> key){net.minecraft.world.item.ItemInstance tool=params.getOptionalParameter(LootContextParams.TOOL);return tool==null?0:tool.getEnchantmentLevel(params.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key));}
    static ItemStack crystals(LootParams.Builder params,int low){var random=params.getLevel().getRandom();var rules=IndustrialRules.CURRENT;int tier=low+(random.nextInt(100)<rules.highCrystalWeight?1:0);int amount=rules.crystalDropMin+random.nextInt(rules.crystalDropMax-rules.crystalDropMin+1);int fortune=enchant(params,Enchantments.FORTUNE);if(fortune>0)amount*=Math.max(1,random.nextInt(fortune+2));return IndustrialContent.stack("energy_crystal_"+tier,amount);}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){
        if(enchant(params,Enchantments.SILK_TOUCH)>0)return List.of(new ItemStack(this));
        if(metal.equals("magic"))return List.of(crystals(params,3));
        int fortune=enchant(params,Enchantments.FORTUNE);int n=fortune==0?1:Math.max(1,params.getLevel().getRandom().nextInt(fortune+2));return List.of(IndustrialContent.stack("raw_"+metal,n));
    }
}
