package cn.yumis.fight.modules.industry.platform;
import cn.yumis.fight.platform.ItemRegistry;
import cn.yumis.fight.modules.industry.api.IndustrialRules;
import cn.yumis.fight.modules.industry.api.IndustrialRecipe;
import cn.yumis.fight.modules.industry.api.Fraction;

import cn.yumis.fight.modules.industry.api.DeviceType;

import cn.yumis.fight.modules.economy.api.CurrencyRules;

import com.google.gson.*;

import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;

import net.minecraft.server.level.*;

import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;

import net.minecraft.world.level.saveddata.*;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.fluid.*;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Industrial module implementation; owns one scheduling responsibility. */
public final class IndustrialProcessing {
    public static List<RecipeHolder<IndustrialRecipe>> recipes(ServerLevel level,DeviceType kind){if(kind.has(DeviceType.Capability.SMELTING))return List.of();return level.recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get()).stream().filter(h->h.value().machine().equals(kind.id)).sorted(Comparator.comparing(h->h.id().identifier().toString())).toList();}
    public static void prepare(MachineBlockEntity be){
        if(!be.device().selectedRecipe()){be.selected="";be.queued="";}if(!be.running||!be.tickTime.positive()){be.status="已停止";return;}if(be.device().needsStructure()&&!be.structureValid){be.status="结构不完整：缺少 "+IndustrialStructures.missing(be).size()+" 处";return;}if(!be.batchRecipe.isEmpty()){if(!be.canFinish())be.status="产物空间不足";return;}
        if(be.device().selectedRecipe()&&be.selected.isEmpty()){be.status="请选择生成配方";return;}
        ServerLevel level=(ServerLevel)be.getLevel();
        if(be.device().has(DeviceType.Capability.SMELTING)){prepareSmelting(be,level);return;}
        List<ItemStack> input=new ArrayList<>();for(int i=0;i<be.device().inputs();i++)input.add(be.getItem((i+be.roundRobin)%Math.max(1,be.device().inputs())));
        RecipeHolder<IndustrialRecipe> chosen=null;int[] plan=null;int bestRank=Integer.MAX_VALUE;
        for(var holder:recipes(level,be.device())){IndustrialRecipe r=holder.value();if(r.minimumTier()>be.tier()||be.device().exactRecipeTier()&&r.minimumTier()!=be.tier())continue;if(be.device().selectedRecipe()&&!be.selected.isEmpty()&&!holder.id().identifier().toString().equals(be.selected))continue;int[] take=r.takePlan(input);if(take!=null&&fluidInputFits(be,r)){int rank=0;while(rank<take.length&&take[rank]==0)rank++;if(chosen==null||rank<bestRank){chosen=holder;plan=take;bestRank=rank;}}}
        if(chosen==null){be.status="缺少原料或可用配方";return;}IndustrialRecipe r=chosen.value();int[] indices=new int[input.size()];for(int i=0;i<indices.length;i++)indices[i]=(i+be.roundRobin)%Math.max(1,be.device().inputs());if(be.device().selectedRecipe()&&be.selected.isEmpty())be.selected=chosen.id().identifier().toString();start(be,chosen.id().identifier().toString(),r,plan,indices);
    }
    private static void prepareSmelting(MachineBlockEntity be,ServerLevel level){
        int slots=be.device().inputs();
        for(int i=0;i<slots;i++){
            int slot=(i+be.roundRobin)%slots;ItemStack stack=be.getItem(slot);if(stack.isEmpty())continue;
            var input=new SingleRecipeInput(stack);var holder=level.recipeAccess().getRecipeFor(RecipeType.SMELTING,input,level);if(holder.isEmpty())continue;
            var recipe=holder.get().value();
            var batch=new IndustrialRecipe(be.device().id,1,List.of(new IndustrialRecipe.Need(Ingredient.of(stack.getItem()),1)),recipe.assemble(input),recipe.cookingTime(),100,Optional.empty(),FluidStack.EMPTY);
            start(be,"vanilla:"+holder.get().id().identifier(),batch,new int[]{1},new int[]{slot});return;
        }
        be.status="缺少原版熔炉可熔炼的原料";
    }
    private static boolean fluidInputFits(MachineBlockEntity be,IndustrialRecipe r){return r.fluidInput().isEmpty()||r.fluidInput().get().test(be.fluids.getResource(0).toStack(be.fluids.getAmountAsInt(0)));}
    private static void start(MachineBlockEntity be,String id,IndustrialRecipe r,int[] plan,int[] indices){
        ItemStack result=be.device().randomCurrency()?ItemRegistry.item(CurrencyRules.rollGuaranteed(new Random(be.getLevel().getRandom().nextLong()),IndustrialRules.current().currencyPool()).id(),1):r.result().copy();
        be.batchResult=result;be.batchFluid=r.fluidResult().copy();if(!be.canFinish()&&!be.device().randomCurrency()){be.batchResult=ItemStack.EMPTY;be.batchFluid=FluidStack.EMPTY;be.status="产物空间不足";return;}
        try(var tx=Transaction.openRoot()){
            List<ItemStack> escrow=new ArrayList<>();for(int i=0;i<plan.length;i++)if(plan[i]>0){ItemResource resource=be.items.getResource(indices[i]);if(be.items.extract(indices[i],resource,plan[i],tx)!=plan[i])return;escrow.add(resource.toStack(plan[i]));}
            if(r.fluidInput().isPresent()){int amount=r.fluidInput().get().amount();if(be.fluids.extract(0,be.fluids.getResource(0),amount,tx)!=amount)return;}
            tx.commit();be.batchInputs=escrow;
        }
        be.batchSequence++;be.batchRecipe=id;be.batchEnergy=r.energy();be.batchTicks=r.ticks();be.batchSpeed=be.device().speed(be.tier(),r.minimumTier(),IndustrialRules.current().furnaceSpeeds);be.work=Fraction.ZERO;be.paid=0;be.roundRobin=(be.roundRobin+1)%Math.max(1,be.device().inputs());be.status="加工中";be.setChanged();
    }
    public static void output(MachineBlockEntity be){if(!be.device().processing())return;ServerLevel level=(ServerLevel)be.getLevel();BlockPos below=be.getBlockPos().below();if(!level.hasChunkAt(below))return;ResourceHandler<ItemResource> target=level.getCapability(Capabilities.Item.BLOCK,below,Direction.UP);if(target==null)return;for(int i=MachineBlockEntity.OUTPUT;i<MachineBlockEntity.OUTPUT_END;i++){ItemResource resource=be.items.getResource(i);int amount=be.items.getAmountAsInt(i);if(resource.isEmpty()||amount==0)continue;try(var tx=Transaction.openRoot()){int n=target.insert(resource,amount,tx);if(n>0&&be.items.extract(i,resource,n,tx)==n)tx.commit();}}}
}
