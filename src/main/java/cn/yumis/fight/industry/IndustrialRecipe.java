package cn.yumis.fight.industry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import java.util.*;

/** Standard data-pack recipe; counts are independent of the standard Ingredient predicate. */
public record IndustrialRecipe(String machine,int minimumTier,List<Need> ingredients,Optional<ItemStackTemplate> output,int ticks,long energy,Optional<SizedFluidIngredient> fluidInput,Optional<FluidStackTemplate> fluidOutput) implements Recipe<IndustrialRecipe.Input> {
    public IndustrialRecipe(String machine,int tier,List<Need> ingredients,ItemStack result,int ticks,long energy,Optional<SizedFluidIngredient> input,FluidStack fluidResult){this(machine,tier,ingredients,result.isEmpty()?Optional.empty():Optional.of(ItemStackTemplate.fromNonEmptyStack(result)),ticks,energy,input,fluidResult.isEmpty()?Optional.empty():Optional.of(FluidStackTemplate.fromNonEmptyStack(fluidResult)));}
    public ItemStack result(){return output.map(ItemStackTemplate::create).orElse(ItemStack.EMPTY);}
    public FluidStack fluidResult(){return fluidOutput.map(FluidStackTemplate::create).orElse(FluidStack.EMPTY);}
    public record Need(Ingredient ingredient,int count){
        public static final Codec<Need> CODEC=RecordCodecBuilder.create(i->i.group(Ingredient.CODEC.fieldOf("ingredient").forGetter(Need::ingredient),Codec.intRange(1,4096).optionalFieldOf("count",1).forGetter(Need::count)).apply(i,Need::new));
    }
    public record Input(List<ItemStack> items) implements RecipeInput {
        @Override public ItemStack getItem(int i){return items.get(i);}
        @Override public int size(){return items.size();}
    }
    public static final MapCodec<IndustrialRecipe> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        Codec.STRING.fieldOf("machine").forGetter(IndustrialRecipe::machine),Codec.intRange(1,5).optionalFieldOf("minimum_tier",1).forGetter(IndustrialRecipe::minimumTier),
        Need.CODEC.listOf().optionalFieldOf("ingredients",List.of()).forGetter(IndustrialRecipe::ingredients),ItemStackTemplate.CODEC.optionalFieldOf("result").forGetter(IndustrialRecipe::output),
        Codec.intRange(1,Integer.MAX_VALUE).fieldOf("ticks").forGetter(IndustrialRecipe::ticks),Codec.LONG.validate(n->n>=0?com.mojang.serialization.DataResult.success(n):com.mojang.serialization.DataResult.error(()->"魔能不能为负")).fieldOf("energy").forGetter(IndustrialRecipe::energy),
        SizedFluidIngredient.CODEC.optionalFieldOf("fluid_input").forGetter(IndustrialRecipe::fluidInput),FluidStackTemplate.CODEC.optionalFieldOf("fluid_result").forGetter(IndustrialRecipe::fluidOutput)
    ).apply(i,IndustrialRecipe::new));
    @Override public boolean matches(Input input,Level level){return takePlan(input.items)!=null;}
    /** Integral max-flow avoids greedy matching bugs with overlapping item tags. */
    public int[] takePlan(List<ItemStack> stacks){
        int n=ingredients.size(),m=stacks.size(),sink=n+m+1;int[][] cap=new int[sink+1][sink+1];int wanted=0;
        for(int i=0;i<n;i++){cap[0][1+i]=ingredients.get(i).count;wanted=Math.addExact(wanted,ingredients.get(i).count);for(int j=0;j<m;j++)if(ingredients.get(i).ingredient.test(stacks.get(j)))cap[1+i][1+n+j]=4096;}
        for(int j=0;j<m;j++)cap[1+n+j][sink]=stacks.get(j).getCount();
        int found=0;
        while(found<wanted){int[] parent=new int[sink+1];Arrays.fill(parent,-1);parent[0]=0;ArrayDeque<Integer> q=new ArrayDeque<>();q.add(0);while(!q.isEmpty()&&parent[sink]<0){int v=q.remove();for(int u=1;u<=sink;u++)if(parent[u]<0&&cap[v][u]>0){parent[u]=v;q.add(u);}}
            if(parent[sink]<0)return null;int amount=wanted-found;for(int v=sink;v!=0;v=parent[v])amount=Math.min(amount,cap[parent[v]][v]);for(int v=sink;v!=0;v=parent[v]){cap[parent[v]][v]-=amount;cap[v][parent[v]]+=amount;}found+=amount;
        }
        int[] plan=new int[m];for(int j=0;j<m;j++)plan[j]=cap[sink][1+n+j];return plan;
    }
    @Override public ItemStack assemble(Input input){return result();}
    @Override public boolean showNotification(){return false;}
    @Override public boolean isSpecial(){return true;}
    @Override public String group(){return "";}
    @Override public RecipeSerializer<? extends Recipe<Input>> getSerializer(){return IndustrialContent.RECIPE_SERIALIZER.get();}
    @Override public RecipeType<? extends Recipe<Input>> getType(){return IndustrialContent.RECIPE_TYPE.get();}
    @Override public PlacementInfo placementInfo(){return PlacementInfo.NOT_PLACEABLE;}
    @Override public RecipeBookCategory recipeBookCategory(){return RecipeBookCategories.CRAFTING_MISC;}
}
