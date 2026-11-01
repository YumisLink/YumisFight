package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.platform.ItemRegistry;
import cn.yumis.fight.modules.industry.api.*;
import cn.yumis.fight.modules.economy.api.CurrencyRules;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.fluid.*;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Atomically escrows all inputs; results, duration and energy are locked at batch start. */
public final class IndustrialProcessing {
    public static List<RecipeHolder<IndustrialRecipe>> recipes(ServerLevel level,DeviceType kind){if(kind.has(DeviceType.Capability.SMELTING))return List.of();return level.recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get()).stream().filter(h->h.value().machine().equals(kind.id)).sorted(Comparator.comparing(h->h.id().identifier().toString())).toList();}
    public static List<RecipeHolder<IndustrialRecipe>> choices(MachineBlockEntity be){
        var list=recipes((ServerLevel)be.getLevel(),be.device());
        if(!be.device().has(DeviceType.Capability.MINING))return list;
        var deposits=IndustrialDeposits.get((ServerLevel)be.getLevel()).deposit((ServerLevel)be.getLevel(),be.getBlockPos().getX()>>4,be.getBlockPos().getZ()>>4);
        return list.stream().filter(h->{String id=mineralId(h);var rule=IndustrialRules.current().minerals.get(id);return rule!=null&&rule.tier()<=be.tier()&&deposits.containsKey(id);}).toList();
    }
    public static String mineralId(RecipeHolder<IndustrialRecipe> holder){String path=holder.id().identifier().getPath();return path.startsWith("industrial/generate_")?path.substring("industrial/generate_".length()):"";}
    public static void prepare(MachineBlockEntity be){
        if(!be.device().selectedRecipe()){be.selected="";be.queued="";}
        if(!be.running||!be.tickTime.positive()){be.status="已停止";return;}
        if(be.device().needsStructure()&&!be.structureValid){be.status="结构不完整：缺少 "+IndustrialStructures.missing(be).size()+" 处";return;}
        if(!be.batchRecipe.isEmpty()){if(!be.canFinish())be.status="产物空间不足";return;}
        if(be.device().has(DeviceType.Capability.REQUIRE_RECIPE)&&be.selected.isEmpty()){be.status=be.device().has(DeviceType.Capability.MINING)?choices(be).isEmpty()?be.tier()<5?"当前等级未识别矿物，可升级或更换区块":"本区块为空矿区":"请选择本区块可识别矿物":"请锁定加工工艺";return;}
        ServerLevel level=(ServerLevel)be.getLevel();
        if(be.device().has(DeviceType.Capability.SMELTING)){prepareSmelting(be,level);return;}
        List<ItemStack> input=new ArrayList<>();for(int i=0;i<be.device().inputs();i++)input.add(be.getItem((i+be.roundRobin)%Math.max(1,be.device().inputs())));
        RecipeHolder<IndustrialRecipe> chosen=null;int[] plan=null;int bestRank=Integer.MAX_VALUE;
        for(var holder:choices(be)){
            var r=holder.value();if(r.minimumTier()>be.tier()||be.device().exactRecipeTier()&&r.minimumTier()!=be.tier())continue;
            if(be.device().selectedRecipe()&&!be.selected.isEmpty()&&!holder.id().identifier().toString().equals(be.selected))continue;
            int[] take=r.takePlan(input);if(take!=null&&fluidInputFits(be,r)){int rank=0;while(rank<take.length&&take[rank]==0)rank++;if(chosen==null||rank<bestRank){chosen=holder;plan=take;bestRank=rank;}}
        }
        if(chosen==null){be.status="缺少原料、液体或本区块可采矿物";return;}
        int[] indices=new int[input.size()];for(int i=0;i<indices.length;i++)indices[i]=(i+be.roundRobin)%Math.max(1,be.device().inputs());
        start(be,chosen.id().identifier().toString(),chosen.value(),plan,indices,be.device().has(DeviceType.Capability.MINING)?mineralId(chosen):"");
    }
    private static void prepareSmelting(MachineBlockEntity be,ServerLevel level){
        for(int i=0;i<be.device().inputs();i++){
            int slot=(i+be.roundRobin)%be.device().inputs();ItemStack stack=be.getItem(slot);if(stack.isEmpty())continue;
            var input=new SingleRecipeInput(stack);var holder=level.recipeAccess().getRecipeFor(RecipeType.SMELTING,input,level);if(holder.isEmpty())continue;var recipe=holder.get().value();
            var batch=new IndustrialRecipe(be.device().id,1,List.of(new IndustrialRecipe.Need(Ingredient.of(stack.getItem()),1)),List.of(ItemStackTemplate.fromNonEmptyStack(recipe.assemble(input))),recipe.cookingTime(),100,List.of(),Optional.empty());
            start(be,"vanilla:"+holder.get().id().identifier(),batch,new int[]{1},new int[]{slot},"");return;
        }
        be.status="缺少原版熔炉可熔炼的原料";
    }
    private static boolean fluidInputFits(MachineBlockEntity be,IndustrialRecipe r){
        if(r.fluidInputs().size()>be.device().fluidInputs())return false;
        for(int i=0;i<r.fluidInputs().size();i++)if(!r.fluidInputs().get(i).test(be.fluids.getResource(i).toStack(be.fluids.getAmountAsInt(i))))return false;
        return true;
    }
    private static void start(MachineBlockEntity be,String id,IndustrialRecipe r,int[] plan,int[] indices,String mineral){
        List<ItemStack> results=r.results();
        if(be.device().randomCurrency())results=List.of(ItemRegistry.item(CurrencyRules.rollGuaranteed(new Random(be.getLevel().getRandom().nextLong()),IndustrialRules.current().currencyPool()).id(),1));
        long energy=r.energy();Fraction duration=Fraction.of((long)r.ticks()*128).divide(be.device().speed(be.tier(),r.minimumTier(),IndustrialRules.current().furnaceSpeeds));
        String cooling="";int rate=0;
        if(!mineral.isEmpty()){
            var rule=IndustrialRules.current().minerals.get(mineral);int richness=IndustrialDeposits.get((ServerLevel)be.getLevel()).deposit((ServerLevel)be.getLevel(),be.getBlockPos().getX()>>4,be.getBlockPos().getZ()>>4).get(mineral);
            duration=Fraction.of((long)rule.ticks()*12800).divide((long)be.tier()*richness);
            if(be.fluids.getAmountAsInt(0)>0){var fluid=be.fluids.getResource(0);if(IndustrialFluids.matches(fluid,"minecraft:water")){duration=duration.multiply(2).divide(3);cooling="minecraft:water";rate=IndustrialRules.current().miningWaterRate*be.tier();}else if(IndustrialFluids.matches(fluid,IndustrialContent.NS+":coolant")){duration=duration.multiply(2).divide(5);cooling=IndustrialContent.NS+":coolant";rate=IndustrialRules.current().miningCoolantRate*be.tier();}}
            energy=duration.multiply(Math.multiplyExact(rule.power(),(long)be.tier()*be.tier())).divide(2560).ceil();
            ItemStack output=new ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(rule.item())),rule.count());
            if(mineral.equals("magic")){var random=be.getLevel().getRandom();var rules=IndustrialRules.current();output=IndustrialContent.stack("energy_crystal_"+(random.nextInt(100)<rules.highCrystalWeight?4:3),rules.crystalDropMin+random.nextInt(rules.crystalDropMax-rules.crystalDropMin+1));}
            results=List.of(output);
        }
        // Probe the complete set together so two products cannot reserve the same free slot.
        be.batchResults=new ArrayList<>(results);be.batchFluid=r.fluidResult();
        if(!be.canFinish()&&!be.device().randomCurrency()&&!mineral.equals("magic")){be.batchResults.clear();be.batchFluid=FluidStack.EMPTY;be.status="产物空间不足";return;}
        boolean committed=false;
        try(var tx=Transaction.openRoot()){
            List<ItemStack> escrow=new ArrayList<>();for(int i=0;i<plan.length;i++)if(plan[i]>0){var resource=be.items.getResource(indices[i]);if(be.items.extract(indices[i],resource,plan[i],tx)!=plan[i])return;escrow.add(resource.toStack(plan[i]));}
            for(int tank=0;tank<r.fluidInputs().size();tank++){int amount=r.fluidInputs().get(tank).amount();if(be.fluids.extract(tank,be.fluids.getResource(tank),amount,tx)!=amount)return;}
            tx.commit();be.batchInputs=escrow;committed=true;
        }finally{if(!committed){be.batchResults.clear();be.batchFluid=FluidStack.EMPTY;}}
        be.batchSequence++;be.batchRecipe=id;be.batchEnergy=energy;be.batchDuration=duration;be.batchMiningFluid=cooling;be.batchFluidRate=rate;be.batchFluidPaid=0;be.work=Fraction.ZERO;be.paid=0;be.roundRobin=(be.roundRobin+1)%Math.max(1,be.device().inputs());be.status="加工中";be.setChanged();
    }
    public static void output(MachineBlockEntity be){if(!be.device().outputs())return;ServerLevel level=(ServerLevel)be.getLevel();BlockPos below=be.getBlockPos().below();if(!level.hasChunkAt(below))return;ResourceHandler<ItemResource> target=level.getCapability(Capabilities.Item.BLOCK,below,Direction.UP);if(target==null)return;for(int i=be.slots().outputStart();i<be.slots().outputEnd();i++){var resource=be.items.getResource(i);int amount=be.items.getAmountAsInt(i);if(resource.isEmpty()||amount==0)continue;try(var tx=Transaction.openRoot()){int n=target.insert(resource,amount,tx);if(n>0&&be.items.extract(i,resource,n,tx)==n)tx.commit();}}}
}
