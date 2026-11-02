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
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Every line escrows its own inputs and snapshots its own result, energy and exact duration. */
public final class IndustrialProcessing {
    public static List<RecipeHolder<IndustrialRecipe>> recipes(ServerLevel level,DeviceType kind){
        if(kind.has(DeviceType.Capability.SMELTING))return List.of();
        return level.recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get()).stream()
            .filter(holder->holder.value().machine().equals(kind.id)).sorted(Comparator.comparing(holder->holder.id().identifier().toString())).toList();
    }
    public static List<RecipeHolder<IndustrialRecipe>> choices(MachineBlockEntity be){
        var list=recipes((ServerLevel)be.getLevel(),be.device());
        if(!be.device().has(DeviceType.Capability.MINING))return list;
        var deposits=IndustrialDeposits.get((ServerLevel)be.getLevel()).deposit((ServerLevel)be.getLevel(),be.getBlockPos().getX()>>4,be.getBlockPos().getZ()>>4);
        return list.stream().filter(holder->{String id=mineralId(holder);var rule=IndustrialRules.current().minerals.get(id);
            return rule!=null&&rule.tier()<=be.tier()&&(MineralCatalog.universal(id)||deposits.containsKey(id));}).toList();
    }
    public static String mineralId(RecipeHolder<IndustrialRecipe> holder){String path=holder.id().identifier().getPath();return path.startsWith("industrial/generate_")?path.substring("industrial/generate_".length()):"";}
    public static void prepare(MachineLane line){
        var be=line.machine;
        if(!be.device().selectedRecipe()){line.selected="";line.queued="";}
        if(!be.running||!line.tickTime.positive()){line.status="已停止";return;}
        if(be.device().needsStructure()&&!be.structureValid){line.status="结构不完整：缺少 "+IndustrialStructures.missing(be).size()+" 处";return;}
        if(!line.batchRecipe.isEmpty()){if(!line.canFinish())line.status="本线产物空间不足";return;}
        if(be.device().has(DeviceType.Capability.REQUIRE_RECIPE)&&line.selected.isEmpty()){
            line.status=be.device().has(DeviceType.Capability.MINING)?"请选择矿物；所有区块均可开采圆石":"请锁定加工工艺";return;
        }
        ServerLevel level=(ServerLevel)be.getLevel();
        if(be.device().has(DeviceType.Capability.SMELTING)){prepareSmelting(line,level);return;}
        int count=be.device().inputs();List<ItemStack> input=new ArrayList<>();
        for(int i=0;i<count;i++)input.add(be.getItem(line.inputSlot((i+line.roundRobin)%count)));
        RecipeHolder<IndustrialRecipe> chosen=null;int[] plan=null;int bestRank=Integer.MAX_VALUE;
        for(var holder:choices(be)){
            var recipe=holder.value();
            if(recipe.minimumTier()>be.tier()||be.device().exactRecipeTier()&&recipe.minimumTier()!=be.tier())continue;
            if(be.device().selectedRecipe()&&!line.selected.isEmpty()&&!holder.id().identifier().toString().equals(line.selected))continue;
            int[] take=recipe.takePlan(input);
            if(take!=null&&fluidInputFits(line,recipe)){
                int rank=0;while(rank<take.length&&take[rank]==0)rank++;
                boolean sameProduct=chosen!=null&&primary(chosen.value()).equals(primary(recipe));
                int complexity=recipe.ingredients().size()+recipe.fluidInputs().size(),oldComplexity=chosen==null?Integer.MAX_VALUE:chosen.value().ingredients().size()+chosen.value().fluidInputs().size();
                if(chosen==null||sameProduct&&complexity<oldComplexity||(!sameProduct||complexity==oldComplexity)&&rank<bestRank){chosen=holder;plan=take;bestRank=rank;}
            }
        }
        if(chosen==null){line.status="缺少本线原料、液体或可采矿物";return;}
        int[] indices=new int[input.size()];for(int i=0;i<indices.length;i++)indices[i]=line.inputSlot((i+line.roundRobin)%count);
        start(line,chosen.id().identifier().toString(),chosen.value(),plan,indices,be.device().has(DeviceType.Capability.MINING)?mineralId(chosen):"");
    }
    private static void prepareSmelting(MachineLane line,ServerLevel level){
        var be=line.machine;
        for(int i=0;i<be.device().inputs();i++){
            int input=(i+line.roundRobin)%be.device().inputs(),slot=line.inputSlot(input);ItemStack stack=be.getItem(slot);if(stack.isEmpty())continue;
            var source=new SingleRecipeInput(stack);var holder=level.recipeAccess().getRecipeFor(RecipeType.SMELTING,source,level);if(holder.isEmpty())continue;var recipe=holder.get().value();
            var batch=new IndustrialRecipe(be.device().id,1,List.of(new IndustrialRecipe.Need(Ingredient.of(stack.getItem()),1)),List.of(ItemStackTemplate.fromNonEmptyStack(recipe.assemble(source))),recipe.cookingTime(),100,List.of(),Optional.empty());
            start(line,"vanilla:"+holder.get().id().identifier(),batch,new int[]{1},new int[]{slot},"");line.roundRobin=(input+1)%be.device().inputs();return;
        }
        line.status="缺少普通熔炉可熔炼的原料";
    }
    private static String primary(IndustrialRecipe recipe){return recipe.outputs().isEmpty()?recipe.fluidResult().isEmpty()?"":net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(recipe.fluidResult().getFluid()).toString():recipe.results().stream().map(stack->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).sorted().reduce((a,b)->a+"/"+b).orElse("");}
    private static boolean fluidInputFits(MachineLane line,IndustrialRecipe recipe){return fluidPlan(line,recipe)!=null;}
    private static int[] fluidPlan(MachineLane line,IndustrialRecipe recipe){
        int[] plan=new int[recipe.fluidInputs().size()];return fluidPlan(line,recipe,plan,new long[line.machine.device().fluidInputs()],0)?plan:null;
    }
    private static boolean fluidPlan(MachineLane line,IndustrialRecipe recipe,int[] plan,long[] used,int input){
        if(input==plan.length)return true;var need=recipe.fluidInputs().get(input);
        for(int local=0;local<used.length;local++){int tank=line.fluidTank(local);var resource=line.machine.fluids.getResource(tank);
            if(resource.isEmpty()||line.machine.fluids.availableAmount(tank)-used[local]<need.amount()||!need.ingredient().test(resource.toStack(1)))continue;
            used[local]+=need.amount();plan[input]=tank;if(fluidPlan(line,recipe,plan,used,input+1))return true;used[local]-=need.amount();
        }return false;
    }
    private static void start(MachineLane line,String id,IndustrialRecipe recipe,int[] plan,int[] indices,String mineral){
        var be=line.machine;List<ItemStack> results=recipe.results();
        if(be.device().randomCurrency())results=List.of(ItemRegistry.item(CurrencyRules.rollGuaranteed(new Random(be.getLevel().getRandom().nextLong()),IndustrialRules.current().currencyPool()).id(),1));
        long energy=recipe.energy();
        Fraction duration=Fraction.of((long)recipe.ticks()*128).divide(be.device().speed(be.tier(),IndustrialRules.current().furnaceSpeeds));
        String cooling="";int rate=0;
        if(!mineral.isEmpty()){
            var rule=IndustrialRules.current().minerals.get(mineral);
            int richness=MineralCatalog.universal(mineral)?100:IndustrialDeposits.get((ServerLevel)be.getLevel()).deposit((ServerLevel)be.getLevel(),be.getBlockPos().getX()>>4,be.getBlockPos().getZ()>>4).get(mineral);
            duration=Fraction.of((long)rule.ticks()*12800).divide((long)be.tier()*richness);
            int tank=line.fluidTank(0);
            if(be.fluids.getAmountAsInt(tank)>0){
                var fluid=be.fluids.getResource(tank);
                if(IndustrialFluids.matches(fluid,"minecraft:water")){duration=duration.multiply(2).divide(3);cooling="minecraft:water";rate=IndustrialRules.current().miningWaterRate*be.tier();}
                else if(IndustrialFluids.matches(fluid,IndustrialContent.NS+":coolant")){duration=duration.multiply(2).divide(5);cooling=IndustrialContent.NS+":coolant";rate=IndustrialRules.current().miningCoolantRate*be.tier();}
            }
            energy=duration.multiply(Math.multiplyExact(rule.power(),(long)be.tier()*be.tier())).divide(2560).ceil();
            ItemStack output=new ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(rule.item())),rule.count());
            if(mineral.equals("magic")){var random=be.getLevel().getRandom();var rules=IndustrialRules.current();output=IndustrialContent.stack("energy_crystal_"+(random.nextInt(100)<rules.highCrystalWeight?4:3),rules.crystalDropMin+random.nextInt(rules.crystalDropMax-rules.crystalDropMin+1));}
            results=List.of(output);
        }
        energy=Math.multiplyExact(energy,be.energyMultiplier());duration=duration.divide(be.pluginSpeed());
        try(var tx=Transaction.openRoot()){
            int[] fluidSlots=fluidPlan(line,recipe);if(fluidSlots==null)return;
            line.record(tx);line.batchResults=new ArrayList<>(results);line.batchFluid=recipe.fluidResult();line.progressive=true;
            line.batchFluidInputs=new ArrayList<>();
            for(int input=0;input<recipe.fluidInputs().size();input++){int tank=fluidSlots[input],amount=recipe.fluidInputs().get(input).amount();line.batchFluidInputs.add(new MachineLane.FluidInput(tank,be.fluids.getResource(tank).toStack(amount)));}
            line.batchRecipe=id;line.batchEnergy=energy;line.batchDuration=duration;line.batchMiningFluid=cooling;line.batchFluidRate=rate;line.batchFluidPaid=0;line.work=Fraction.ZERO;line.paid=0;
            List<ItemStack> escrow=new ArrayList<>();
            for(int i=0;i<plan.length;i++)if(plan[i]>0){var resource=be.items.getResource(indices[i]);if(be.items.extract(indices[i],resource,plan[i],tx)!=plan[i])return;escrow.add(resource.toStack(plan[i]));}
            line.batchInputs=escrow;line.batchSequence++;line.roundRobin=(line.roundRobin+1)%Math.max(1,be.device().inputs());line.status="加工中";tx.commit();
        }
        be.setChanged();
    }
    public static void output(MachineBlockEntity be){
        if(!be.device().outputs())return;ServerLevel level=(ServerLevel)be.getLevel();BlockPos below=be.getBlockPos().below();if(!level.hasChunkAt(below))return;
        ResourceHandler<ItemResource> target=level.getCapability(Capabilities.Item.BLOCK,below,Direction.UP);if(target==null)return;
        for(int slot:be.slots().outputSlots()){
            var resource=be.items.getResource(slot);int amount=be.items.getAmountAsInt(slot);if(resource.isEmpty()||amount==0)continue;
            try(var tx=Transaction.openRoot()){int count=target.insert(resource,amount,tx);if(count>0&&be.items.extract(slot,resource,count,tx)==count)tx.commit();}
        }
    }
}
