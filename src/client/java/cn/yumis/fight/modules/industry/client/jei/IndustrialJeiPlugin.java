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
    private static final List<String> MACHINES=List.of("mana_crusher","mana_furnace","isotopic_separator","isotope_activator","forming_assembler","thermal_distiller","fuel_artificial_star","mineral_generator","mana_forge","coolant_mixer","mineral_refinery","alchemical_reactor","water_pump","currency_fabricator","fuel_coal_generator","fuel_turbine_generator","fuel_thermal_generator","fuel_nuclear_reactor","fuel_rtg_generator","storage_upgrade");
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
        registration.addItemStackInfo(IndustrialContent.stack("storage_plugin",1),Component.literal("机器侧边箭头展开3格插件栏，每格最多8件。每件使物品输入／输出槽与液槽容量翻倍，逐件乘算；不可堆叠物仍为1。取出插件前须清空超额储量。"));
        registration.addItemStackInfo(IndustrialContent.stack("speed_plugin",1),Component.literal("每件使速度×1.5、单批耗能×2，逐件乘算。背包堆叠64、机内每格8。适用加工、采矿、通货升级、抽水、收获与水晶运输，发电机暂不适用。"));
        registration.addItemStackInfo(IndustrialContent.stack("water_pump",1),Component.literal("接网且相邻任一面有水源时抽水，不移除水源。出水缓存自动供给六面相邻设备的入液口，紧贴涡轮任一面即可直接供水；满罐时保留余水。远距离送水仍可使用供液水晶。"));
        registration.addItemStackInfo(IndustrialContent.stack("mana_storage_bag",1),Component.literal("随身携带时，新获得的1至10阶魔能水晶自动转为魔能，满背包也可拾取。每升一阶所需魔能为前一阶的8倍，存取无损。\n手持Shift＋滚轮切换取出等级；右键取1个，Shift＋右键取64个，魔能或空间不足时整次取消。取出的水晶可留在背包使用。\n魔能跟随袋子保存，多袋按主手、副手、背包顺序收纳。4根线、4张皮革和1枚1阶水晶在工作台合成。"));
        var recipes=IndustrialRecipeCache.recipes.byType(IndustrialContent.RECIPE_TYPE.get());
        for(String key:MACHINES)registration.addRecipes(TYPES.get(key),recipes.stream().filter(h->group(h.value()).equals(key)).toList());
        for(var spec:IndustrialContent.SPECS.values())if(spec.tier()<spec.device().maximumTier())registration.addItemStackInfo(IndustrialContent.stack(spec.id(),1),Component.literal("手持下一档科技机外壳右键，消耗该工作台升级配方中除前级设备外的材料；原地升级保留内容。工作台升级仅接受空设备，拆除会清空液体和余能。"));
        registration.addItemStackInfo(IndustrialContent.stack("coolant_mixer",1),Component.literal("T3冷却液工艺，基础每5秒产1B、每秒耗能500。支持超速芯片：每件速度×1.5、单批耗能×2；按带液体设备逐步反应和产出。"));
        for(String key:List.of("solar_collector","wind_generator"))registration.addItemStackInfo(IndustrialContent.stack(key,1),Component.literal(key.equals("wind_generator")?"露天三格风机；最低高度 "+IndustrialRules.current().windBaseHeight+"，基础产能 "+IndustrialRules.current().windBasePower+" 点/秒。":"露天日曜设备；白天产能 "+IndustrialRules.current().solarDayPower+" 点/秒，夜间或雷暴停止。"));
        for(int tier=1;tier<=5;tier++){
            registration.addItemStackInfo(IndustrialContent.stack("prospector_t"+tier,1),Component.literal("右键直接扫描，Shift右键打开可拖动区块地图。扫描"+(tier*2-1)+"×"+(tier*2-1)+"区块，耗时2秒，完成后冷却5秒，冷却与记录跟随玩家保存在当前世界。可移动，不耗魔能，不加载地形。"));
            registration.addItemStackInfo(IndustrialContent.stack("mineral_generator_t"+tier,1),Component.literal("采矿机：圆石在任意区块均可开采；其他矿藏各自独立抽取，首次判定后固定且无限，紫水晶二级开放。只显示本级可采矿物，9格产物满则暂停。配方费用为最低等级、普通丰度、无冷却时的一批总耗能；保留机身线性速度和平方功率关系。水×1.5、冷却液×2.5，工作时耗液。玩家手挖至多T3。魔力矿每批2～8颗，80%为3阶、20%为4阶，开工锁定。"));
        }
        for(var spec:IndustrialContent.SPECS.values())if(spec.device().has(cn.yumis.fight.modules.industry.api.DeviceType.Capability.PARALLEL))registration.addItemStackInfo(IndustrialContent.stack(spec.id(),1),Component.literal("本级解锁 "+spec.device().lineCount(spec.tier())+" 条独立产线；单线相对一级速度 "+(1<<(spec.tier()-1))+" 倍。每线独立输入、输出、进度及工艺液，界面点击产线切换；自动投料分配到可接收的产线。每份配方仍按实际消耗结算，通货每份600魔能，各线独立抽取。"));
        for(int tier=1;tier<=4;tier++)registration.addItemStackInfo(IndustrialContent.stack("mana_accumulator_t"+tier,1),Component.literal("接入信标电网，增加 "+IndustrialRules.current().accumulatorCapacities[tier-1]+" 魔能容量，初始为空，不接受水晶充能，无额外充放速度限制。断网、卸载保留本节点余额；拆除清空；原地升级保留。"));
        for(int tier=1;tier<=3;tier++){
            registration.addItemStackInfo(IndustrialContent.stack("fluid_tank_t"+tier,1),Component.literal("一罐一种液体，可存 "+IndustrialRules.current().tankCapacities[tier-1]+" mB。三档均支持全部工艺液；原地升级保留，拆除清空。"));
            registration.addItemStackInfo(IndustrialContent.stack("fluid_crystal_t"+tier,1),Component.literal("权杖移动模式先绑定水晶，再选择来源容器和目标容器。源与目标各距水晶"+(tier==3?"同维度不限距离":IndustrialRules.current().transferRanges[tier-1]+"格以内")+"；各档支持1／3／16对并行，每对成功批量操作一次固定消耗"+IndustrialRules.current().fluidTransferOperationEnergy[tier-1]+"魔能，未满批同价。未加载、缺液、满罐或类型不匹配不收费。"));
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
            for(int i=0;i<recipe.fluidInputs().size();i++){var need=recipe.fluidInputs().get(i);var slot=builder.addInputSlot(62+i*20,18).setStandardSlotBackground().setFluidRenderer(need.amount(),false,16,16);BuiltInRegistries.FLUID.stream().filter(f->need.ingredient().test(new FluidStack(f,need.amount()))).forEach(f->slot.add(f,need.amount()));}
            for(int i=0;i<recipe.results().size();i++)builder.addOutputSlot(124+i*18,18).setOutputSlotBackground().add(recipe.results().get(i));
            if(!recipe.fluidResult().isEmpty()){var fluid=recipe.fluidResult();builder.addOutputSlot(142,40).setStandardSlotBackground().setFluidRenderer(fluid.getAmount(),false,16,16).add(fluid.getFluid(),fluid.getAmount(),fluid.getComponentsPatch());}
            if(key.startsWith("fuel_")){var rule=IndustrialRules.current().generators.get(key.substring(5));if(rule!=null&&!rule.fluid().isEmpty()&&rule.fluidPerSecond()>0){long total=(long)rule.fluidPerSecond()*recipe.ticks()/20;var slot=builder.addInputSlot(62,18).setStandardSlotBackground().setFluidRenderer(Math.max(1,total),false,16,16);BuiltInRegistries.FLUID.stream().filter(f->cn.yumis.fight.modules.industry.platform.IndustrialFluids.matches(FluidResource.of(f),rule.fluid())).forEach(f->slot.add(f,total));}}
            if(key.equals("currency_fabricator")){
                double[] probabilities=CurrencyRules.factoryProbabilities(IndustrialRules.current().currencyRarityWeights,IndustrialRules.current().currencyItemWeights);int index=0;
                for(int i=0;i<probabilities.length;i++)if(probabilities[i]>0){double chance=probabilities[i];builder.addOutputSlot(80+(index%5)*18,18+(index/5)*18).setStandardSlotBackground().add(ItemRegistry.item(CurrencyRules.ALL.get(i).id(),1)).addRichTooltipCallback((slot,tooltip)->tooltip.add(Component.literal(String.format(Locale.ROOT,"每轮随机1枚 · %.2f%%",chance*100))));index++;}
            }
        }
        @Override public void draw(RecipeHolder<IndustrialRecipe> holder,IRecipeSlotsView slots,GuiGraphicsExtractor graphics,double mouseX,double mouseY){
            var recipe=holder.value();var font=Minecraft.getInstance().font;
            if(key.startsWith("fuel_")){
                graphics.text(font,String.format(Locale.ROOT,"%.2f 魔能/秒",recipe.energy()*20.0/recipe.ticks()),2,80,0xff444444,false);
                graphics.text(font,String.format(Locale.ROOT,"持续 %.2f 秒",recipe.ticks()/20.0),2,93,0xff444444,false);
                graphics.text(font,"总魔能 "+recipe.energy(),2,106,0xff444444,false);
            }else{
                int energyTier=cn.yumis.fight.modules.industry.api.IndustrialTechnology.energyTier(holder.id().identifier().toString());
                graphics.text(font,key.equals("storage_upgrade")?"背包 "+recipe.machine().substring("storage_upgrade_".length())+" 档":"工艺T"+recipe.minimumTier()+(energyTier==0?"":" · 能耗T"+energyTier),2,80,0xff444444,false);
                double seconds=recipe.ticks()/20.0;
                if(key.equals("mineral_generator")){var mineral=IndustrialRules.current().minerals.get(cn.yumis.fight.modules.industry.platform.IndustrialProcessing.mineralId(holder));if(mineral!=null)seconds=mineral.ticks()/(20.0*mineral.tier());}
                graphics.text(font,String.format(Locale.ROOT,"基准 %.2f 秒",seconds),2,93,0xff444444,false);
                graphics.text(font,"总耗能 "+recipe.energy()+" 点",2,106,0xff444444,false);
            }
            graphics.text(font,key.equals("currency_fabricator")?"随机1枚":key.equals("storage_upgrade")?"界面升级":"→",key.equals("currency_fabricator")?82:91,59,0xff168789,false);
        }
    }
}
