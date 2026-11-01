package cn.yumis.fight.modules.industry.client.jei;
import cn.yumis.fight.platform.ItemRegistry;
import cn.yumis.fight.modules.industry.platform.MachineUpgradeRecipe;

import cn.yumis.fight.modules.industry.api.IndustrialRules;
import cn.yumis.fight.modules.industry.api.IndustrialRecipe;
import cn.yumis.fight.modules.industry.platform.IndustrialContent;

import cn.yumis.fight.modules.industry.client.IndustrialRecipeCache;

import cn.yumis.fight.modules.economy.api.CurrencyRules;
import mezz.jei.api.*;
import mezz.jei.api.constants.*;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.registration.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import java.util.*;

/** Optional JEI integration. All recipe ingredients come from the server's synced recipe map. */
@JeiPlugin
public final class IndustrialJeiPlugin implements IModPlugin {
    private static mezz.jei.api.runtime.IJeiRuntime runtime;
    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime value){runtime=value;}
    @Override public void onRuntimeUnavailable(){runtime=null;}
    /** Called only by the isolated verification driver, through reflection so JEI stays optional. */
    public static com.google.gson.JsonObject inspect(String operation,String key){
        if(!Boolean.getBoolean("ydnf.verify"))throw new IllegalStateException("Verification is disabled");
        var result=new com.google.gson.JsonObject();result.addProperty("ready",runtime!=null);if(runtime==null)return result;
        var manager=runtime.getRecipeManager();var counts=new com.google.gson.JsonObject();for(var entry:TYPES.entrySet())counts.addProperty(entry.getKey(),manager.createRecipeLookup(entry.getValue()).get().count());result.add("categories",counts);
        result.addProperty("guarded_crafting",manager.createRecipeLookup(RecipeTypes.CRAFTING).get().filter(h->h.value() instanceof MachineUpgradeRecipe).count());
        result.addProperty("synced_industrial",IndustrialRecipeCache.recipes.byType(IndustrialContent.RECIPE_TYPE.get()).size());
        if(operation.equals("category"))runtime.getRecipesGui().showTypes(List.of(TYPES.get(key)));
        else if(operation.equals("recipes")||operation.equals("uses")){var item=BuiltInRegistries.ITEM.getValue(key.contains(":")?Identifier.parse(key):IndustrialContent.id(key));var focus=runtime.getJeiHelpers().getFocusFactory().createFocus(operation.equals("uses")?mezz.jei.api.recipe.RecipeIngredientRole.INPUT:mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,new ItemStack(item));runtime.getRecipesGui().show(focus);}
        return result;
    }
    private static final List<String> MACHINES=List.of("mana_crusher","mineral_generator","mana_forge","coolant_mixer","mineral_refinery","alchemical_reactor","water_pump","currency_fabricator","fuel_coal_generator","fuel_turbine_generator","fuel_thermal_generator","fuel_nuclear_reactor","fuel_rtg_generator","storage_upgrade");
    private static final Map<String,IRecipeHolderType<IndustrialRecipe>> TYPES=new LinkedHashMap<>();
    static {for(String machine:MACHINES)TYPES.put(machine,IRecipeHolderType.create(IndustrialContent.id(machine)));}
    @Override public Identifier getPluginUid(){return IndustrialContent.id("industry");}
    private static String group(IndustrialRecipe recipe){return recipe.machine().startsWith("storage_upgrade_")?"storage_upgrade":recipe.machine();}
    private static List<ItemStack> machines(String category){
        if(category.equals("storage_upgrade"))return List.of(new ItemStack(Items.CHEST));
        String kind=category.startsWith("fuel_")?category.substring(5):category;
        return IndustrialContent.SPECS.values().stream().filter(s->s.device().id.equals(kind)).map(s->IndustrialContent.stack(s.id(),1)).toList();
    }
    @Override public void registerCategories(IRecipeCategoryRegistration registration){for(String key:MACHINES)registration.addRecipeCategories(new Category(key,registration.getJeiHelpers().getGuiHelper()));}
    @Override public void registerRecipes(IRecipeRegistration registration){
        var recipes=IndustrialRecipeCache.recipes.byType(IndustrialContent.RECIPE_TYPE.get());
        for(String key:MACHINES)registration.addRecipes(TYPES.get(key),recipes.stream().filter(h->group(h.value()).equals(key)).toList());
        for(var spec:IndustrialContent.SPECS.values())if(spec.device().graded||spec.device().functional())registration.addItemStackInfo(IndustrialContent.stack(spec.id(),1),Component.literal("手持对应升级材料右键，消耗该工作台升级配方中除前级设备外的材料；原地升级保留内容。工作台升级仅接受空设备，拆除会清空液体和余能。"));
        for(String key:List.of("solar_collector","wind_generator"))registration.addItemStackInfo(IndustrialContent.stack(key,1),Component.literal(key.equals("wind_generator")?"露天三格风机；最低高度 "+IndustrialRules.current().windBaseHeight+"，基础产能 "+IndustrialRules.current().windBasePower+" 点/秒。":"露天日曜设备；白天产能 "+IndustrialRules.current().solarDayPower+" 点/秒，夜间或雷暴停止。"));
        for(int tier=1;tier<=5;tier++){
            registration.addItemStackInfo(IndustrialContent.stack("prospector_t"+tier,1),Component.literal("右键打开区块地图，Shift右键直接扫描。扫描"+(tier*2-1)+"×"+(tier*2-1)+"区块，耗时2秒，完成后冷却5秒，冷却与记录跟随玩家保存在当前世界。可移动，不耗魔能，不加载地形。"));
            registration.addItemStackInfo(IndustrialContent.stack("mineral_generator_t"+tier,1),Component.literal("区块采矿机：矿藏各自独立抽取，允许空区块，首次扫描或放置后固定且无限。只显示本级可采矿物，9格产物满则暂停。速度"+tier+"倍，功率"+(tier*tier)+"倍；水×1.5、冷却液×2.5，工作时耗液。魔力矿每批2～8颗，80%为3阶、20%为4阶，开工锁定。"));
        }
        for(int tier=1;tier<=4;tier++)registration.addItemStackInfo(IndustrialContent.stack("mana_accumulator_t"+tier,1),Component.literal("接入信标电网，增加 "+IndustrialRules.current().accumulatorCapacities[tier-1]+" 魔能容量，初始为空，不接受水晶充能，无额外充放速度限制。断网、卸载保留本节点余额；拆除清空；原地升级保留。"));
        for(int tier=1;tier<=3;tier++){
            registration.addItemStackInfo(IndustrialContent.stack("fluid_tank_t"+tier,1),Component.literal("一罐一种液体，可存 "+IndustrialRules.current().tankCapacities[tier-1]+" mB。三档均支持全部工艺液；原地升级保留，拆除清空。"));
            registration.addItemStackInfo(IndustrialContent.stack("fluid_crystal_t"+tier,1),Component.literal("权杖切换液体模式，依次选择源出口、目标入口，再点击供液水晶。源与目标各距水晶"+(tier==3?"同维度不限距离":IndustrialRules.current().transferRanges[tier-1]+"格以内")+"；实际成功输送才收费，未加载、缺液、满罐或类型不匹配时暂停。"));
        }
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration){
        for(String key:MACHINES)if(!key.equals("storage_upgrade"))for(ItemStack stack:machines(key))registration.addCraftingStation(TYPES.get(key),stack);
        for(ItemStack stack:machines("mana_furnace"))registration.addCraftingStation(RecipeTypes.SMELTING,stack);
    }
    private static final class Category extends AbstractRecipeCategory<RecipeHolder<IndustrialRecipe>> {
        private final String key;
        Category(String key,IGuiHelper gui){super(TYPES.get(key),key.equals("storage_upgrade")?Component.literal("次元背包升级"):machines(key).getFirst().getHoverName().copy().append(key.startsWith("fuel_")?" · 燃料":""),gui.createDrawableIngredient(VanillaTypes.ITEM_STACK,machines(key).getFirst()),170,118);this.key=key;}
        @Override public void setRecipe(IRecipeLayoutBuilder builder,RecipeHolder<IndustrialRecipe> holder,IFocusGroup focuses){
            IndustrialRecipe recipe=holder.value();
            for(int i=0;i<recipe.ingredients().size();i++){var need=recipe.ingredients().get(i);builder.addInputSlot(2+(i%3)*18,18+(i/3)*18).setStandardSlotBackground().addItemStacks(need.ingredient().items().map(h->new ItemStack(h.value(),need.count())).toList());}
            for(int i=0;i<recipe.fluidInputs().size();i++){var need=recipe.fluidInputs().get(i);var slot=builder.addInputSlot(62+i*20,18).setStandardSlotBackground().setFluidRenderer(need.amount(),false,16,34);BuiltInRegistries.FLUID.stream().filter(f->need.ingredient().test(new FluidStack(f,need.amount()))).forEach(f->slot.add(f,need.amount()));}
            for(int i=0;i<recipe.results().size();i++)builder.addOutputSlot(124+i*18,18).setOutputSlotBackground().add(recipe.results().get(i));
            if(!recipe.fluidResult().isEmpty()){var fluid=recipe.fluidResult();builder.addOutputSlot(142,40).setStandardSlotBackground().setFluidRenderer(fluid.getAmount(),false,16,34).add(fluid.getFluid(),fluid.getAmount(),fluid.getComponentsPatch());}
            if(key.startsWith("fuel_")){var rule=IndustrialRules.current().generators.get(key.substring(5));if(rule!=null&&!rule.fluid().isEmpty()&&rule.fluidPerSecond()>0){long total=(long)rule.fluidPerSecond()*recipe.ticks()/20;var slot=builder.addInputSlot(62,18).setStandardSlotBackground().setFluidRenderer(Math.max(1,total),false,16,34);BuiltInRegistries.FLUID.stream().filter(f->cn.yumis.fight.modules.industry.platform.IndustrialFluids.matches(FluidResource.of(f),rule.fluid())).forEach(f->slot.add(f,total));}}
            if(key.equals("currency_fabricator")){
                double[] probabilities=CurrencyRules.factoryProbabilities(IndustrialRules.current().currencyRarityWeights,IndustrialRules.current().currencyItemWeights);int index=0;
                for(int i=0;i<probabilities.length;i++)if(probabilities[i]>0){double chance=probabilities[i];builder.addOutputSlot(80+(index%5)*18,18+(index/5)*18).setStandardSlotBackground().add(ItemRegistry.item(CurrencyRules.ALL.get(i).id(),1)).addRichTooltipCallback((slot,tooltip)->tooltip.add(Component.literal(String.format(Locale.ROOT,"每轮随机1枚 · %.2f%%",chance*100))));index++;}
            }
        }
        @Override public void draw(RecipeHolder<IndustrialRecipe> holder,IRecipeSlotsView slots,GuiGraphicsExtractor graphics,double mouseX,double mouseY){
            var recipe=holder.value();var font=Minecraft.getInstance().font;
            graphics.text(font,key.equals("storage_upgrade")?"背包 "+recipe.machine().substring("storage_upgrade_".length())+" 档":"最低 "+recipe.minimumTier()+" 级",2,80,0xff444444,false);
            graphics.text(font,String.format(Locale.ROOT,"基准 %.2f 秒",recipe.ticks()/20.0),2,93,0xff444444,false);
            graphics.text(font,(key.startsWith("fuel_")?"总产能 ":"总耗能 ")+recipe.energy()+" 点",2,106,0xff444444,false);
            graphics.text(font,key.equals("currency_fabricator")?"随机1枚":key.equals("storage_upgrade")?"界面升级":"→",key.equals("currency_fabricator")?82:91,59,0xff168789,false);
        }
    }
}
