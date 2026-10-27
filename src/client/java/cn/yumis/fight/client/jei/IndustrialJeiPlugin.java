package cn.yumis.fight.client.jei;

import cn.yumis.fight.client.IndustrialRecipeCache;
import cn.yumis.fight.game.GameItems;
import cn.yumis.fight.industry.*;
import cn.yumis.fight.rules.CurrencyRules;
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
    private static final List<String> MACHINES=List.of("mana_crusher","mana_furnace","mineral_generator","mana_forge","coolant_mixer","currency_fabricator","fuel_coal_generator","fuel_turbine_generator","fuel_thermal_generator","fuel_nuclear_reactor","fuel_rtg_generator","storage_upgrade");
    private static final Map<String,IRecipeHolderType<IndustrialRecipe>> TYPES=new LinkedHashMap<>();
    static {for(String machine:MACHINES)TYPES.put(machine,IRecipeHolderType.create(IndustrialContent.id(machine)));}
    @Override public Identifier getPluginUid(){return IndustrialContent.id("industry");}
    private static String group(IndustrialRecipe recipe){return recipe.machine().startsWith("storage_upgrade_")?"storage_upgrade":recipe.machine();}
    private static List<ItemStack> machines(String category){
        if(category.equals("storage_upgrade"))return List.of(new ItemStack(Items.CHEST));
        String kind=category.startsWith("fuel_")?category.substring(5):category;
        return IndustrialContent.SPECS.values().stream().filter(s->s.kind().id.equals(kind)).map(s->IndustrialContent.stack(s.id(),1)).toList();
    }
    @Override public void registerCategories(IRecipeCategoryRegistration registration){for(String key:MACHINES)registration.addRecipeCategories(new Category(key,registration.getJeiHelpers().getGuiHelper()));}
    @Override public void registerRecipes(IRecipeRegistration registration){
        var recipes=IndustrialRecipeCache.recipes.byType(IndustrialContent.RECIPE_TYPE.get());
        for(String key:MACHINES)registration.addRecipes(TYPES.get(key),recipes.stream().filter(h->group(h.value()).equals(key)).toList());
        for(var spec:IndustrialContent.SPECS.values())if(spec.kind().graded||spec.kind().functional())registration.addItemStackInfo(IndustrialContent.stack(spec.id(),1),Component.literal("可手持对应升级组件右键逐档升级。工作台升级仅接受空机器；旧机器请先放置再拆出内容。"));
        for(String key:List.of("solar_collector","wind_generator"))registration.addItemStackInfo(IndustrialContent.stack(key,1),Component.literal(key.equals("wind_generator")?"露天三格风机；最低高度 "+IndustrialRules.CURRENT.windBaseHeight+"，基础产能 "+IndustrialRules.CURRENT.windBasePower+" 点/秒。":"露天日曜设备；白天产能 "+IndustrialRules.CURRENT.solarDayPower+" 点/秒，夜间或雷暴停止。"));
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
            recipe.fluidInput().ifPresent(need->{var slot=builder.addInputSlot(62,18).setStandardSlotBackground().setFluidRenderer(need.amount(),false,16,34);BuiltInRegistries.FLUID.stream().filter(f->need.ingredient().test(new FluidStack(f,need.amount()))).forEach(f->slot.add(f,need.amount()));});
            if(!recipe.result().isEmpty())builder.addOutputSlot(142,18).setOutputSlotBackground().add(recipe.result());
            if(!recipe.fluidResult().isEmpty()){var fluid=recipe.fluidResult();builder.addOutputSlot(142,40).setStandardSlotBackground().setFluidRenderer(fluid.getAmount(),false,16,34).add(fluid.getFluid(),fluid.getAmount(),fluid.getComponentsPatch());}
            if(key.startsWith("fuel_")){var rule=IndustrialRules.CURRENT.generators.get(key.substring(5));if(rule!=null&&!rule.fluid().isEmpty()&&rule.fluidPerSecond()>0){long total=(long)rule.fluidPerSecond()*recipe.ticks()/20;var slot=builder.addInputSlot(62,18).setStandardSlotBackground().setFluidRenderer(Math.max(1,total),false,16,34);BuiltInRegistries.FLUID.stream().filter(f->IndustrialWorld.fluidMatches(FluidResource.of(f),rule.fluid())).forEach(f->slot.add(f,total));}}
            if(key.equals("currency_fabricator")){
                double[] probabilities=CurrencyRules.factoryProbabilities(IndustrialRules.CURRENT.currencyRarityWeights,IndustrialRules.CURRENT.currencyItemWeights);int index=0;
                for(int i=0;i<probabilities.length;i++)if(probabilities[i]>0){double chance=probabilities[i];builder.addOutputSlot(80+(index%5)*18,18+(index/5)*18).setStandardSlotBackground().add(GameItems.item(CurrencyRules.ALL.get(i).id(),1)).addRichTooltipCallback((slot,tooltip)->tooltip.add(Component.literal(String.format(Locale.ROOT,"每轮随机1枚 · %.2f%%",chance*100))));index++;}
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
