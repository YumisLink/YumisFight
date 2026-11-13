package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Adapts the existing batch ledger to a separate display's input/output slots. */
final class DisplayInfusion {
    private DisplayInfusion(){}
    static DisplayStandBlockEntity stand(MachineLane line){
        var be=line.machine;var pos=be.getBlockPos().below();
        if(be.getLevel()==null||!be.getLevel().hasChunkAt(pos)||!(be.getLevel().getBlockEntity(pos) instanceof DisplayStandBlockEntity stand))return null;
        return line.infusionTarget.isEmpty()||line.infusionTarget.equals(stand.key())?stand:null;
    }
    static void prepare(MachineLane line){
        var be=line.machine;var stand=stand(line);
        if(stand==null){line.status="正下方需要展示台";return;}
        ItemStack input=stand.getItem(0);
        for(var holder:IndustrialProcessing.recipes((ServerLevel)be.getLevel(),be.device())){
            var recipe=holder.value();if(recipe.minimumTier()>be.processingTier()||recipe.takePlan(List.of(input))==null||recipe.fluidInputs().size()!=1||recipe.results().size()!=1)continue;
            var fluid=recipe.fluidInputs().getFirst();int tank=line.fluidTank(0);
            if(be.fluids.availableAmount(tank)<fluid.amount()||!fluid.ingredient().test(be.fluids.getResource(tank).toStack(1)))continue;
            ItemStack result=recipe.results().getFirst();
            try(var tx=Transaction.openRoot()){
                if(stand.items.insert(1,ItemResource.of(result),result.getCount(),tx)!=result.getCount())continue;
            }
            try(var tx=Transaction.openRoot()){
                line.record(tx);line.infusionTarget=stand.key();line.batchRecipe=holder.id().identifier().toString();
                line.batchResults=new ArrayList<>(recipe.results());line.batchInputs=new ArrayList<>(List.of(input.copyWithCount(recipe.ingredients().getFirst().count())));
                line.batchFluidInputs=new ArrayList<>(List.of(new MachineLane.FluidInput(tank,be.fluids.getResource(tank).toStack(fluid.amount()))));
                line.progressive=true;line.deferredInputs=true;line.batchDuration=Fraction.of((long)recipe.ticks()*128);line.batchEnergy=recipe.energy();line.work=Fraction.ZERO;line.paid=0;line.batchSequence++;line.status="注液中";tx.commit();
            }
            return;
        }
        line.status="缺少匹配物品、液体或输出空间";
    }
    static boolean takeInput(MachineLane line,TransactionContext tx){
        var stand=stand(line);if(stand==null||line.batchInputs.size()!=1)return false;
        ItemStack input=line.batchInputs.getFirst();return stand.items.extract(0,ItemResource.of(input),input.getCount(),tx)==input.getCount();
    }
    static boolean outputFits(MachineLane line){
        var stand=stand(line);if(stand==null||line.batchResults.isEmpty())return false;
        try(var tx=Transaction.openRoot()){for(var result:line.batchResults)if(stand.items.insert(1,ItemResource.of(result),result.getCount(),tx)!=result.getCount())return false;return true;}
    }
    static int output(MachineLane line,ItemStack stack,TransactionContext tx){var stand=stand(line);return stand==null?0:stand.items.insert(1,ItemResource.of(stack),stack.getCount(),tx);}
    static void completed(MachineBlockEntity machine,net.neoforged.neoforge.fluids.FluidStack fluid){
        if(!(machine.getLevel() instanceof ServerLevel level))return;
        var pos=machine.getBlockPos();var id=net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()).getPath();
        var particle=IndustrialContent.INFUSION_DROPS.get(id);if(particle==null)return;
        level.sendParticles(particle.get(),pos.getX()+.5,pos.getY()-.05,pos.getZ()+.5,1,0,0,0,0);
    }
}
