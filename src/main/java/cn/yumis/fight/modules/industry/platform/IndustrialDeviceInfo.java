package cn.yumis.fight.modules.industry.platform;
import cn.yumis.fight.modules.industry.api.*;
import com.google.gson.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Device help and previews are content, not network settlement logic. */
public final class IndustrialDeviceInfo {
    private static final Map<String,java.util.function.BiConsumer<MachineBlockEntity,JsonArray>> EXTRA_HELP=new LinkedHashMap<>();
    public static void register(String id,java.util.function.BiConsumer<MachineBlockEntity,JsonArray> help){if(EXTRA_HELP.putIfAbsent(id,help)!=null)throw new IllegalArgumentException("Duplicate device help: "+id);}
    static {register(DeviceType.FORGE.id,(be,help)->{help.add("3×3×3：控制器位于底层正面中央，另需25块同级材料，中心留空。");help.add("下方容器位于结构外，可接收控制器产物。");});}
    private IndustrialDeviceInfo(){}
    public static JsonObject enrich(MachineBlockEntity be,JsonObject j){
        JsonArray help=new JsonArray();help.add("工业魔能独立于角色MP；设备只在必要区块加载时运行。");
        if(be.device().randomCurrency()){var rules=IndustrialRules.current();JsonArray probabilities=new JsonArray();for(double p:cn.yumis.fight.modules.economy.api.CurrencyRules.factoryProbabilities(rules.currencyRarityWeights,rules.currencyItemWeights))probabilities.add(p);j.add("currency_probabilities",probabilities);}
        if(be.device().processing()){help.add("插槽仅接受"+(be.tier()*2-1)+"～"+be.tier()*2+"阶。");help.add("产物先存本机，再向下方容器输出；缺能或满仓保留批次。");help.add("当前批次耗能 "+be.batchEnergy+"，已扣 "+be.paid+"；处理能力由整机共享。");if(be.device().randomCurrency())help.add("每轮必出1枚；使用当前工业奖池，不读取玩家掉宝，不再判5%。点击概率查看每种通货的实际权重。");}
        if(be.device().generator()){j.addProperty("power",be.currentPower);help.add("实际产能 "+be.currentPower+" 点/秒；满网继续运行，多余产能浪费。");if(be.fuelTotal>0)help.add("本燃料总产能 "+be.fuelEnergy+"，燃烧 "+String.format(java.util.Locale.ROOT,"%.1f 秒",be.fuelTotal/20.0)+"；流体 "+be.fuelFluidRate+" mB/秒。");if(be.device().has(DeviceType.Capability.SOLAR)||be.device().has(DeviceType.Capability.WIND)){ServerLevel l=(ServerLevel)be.getLevel();help.add("时间 "+Math.floorMod(l.getDefaultClockTime(),24000)+"；天气 "+(l.isThundering()?"雷暴":l.isRaining()?"雨":"晴")+"；顶部天空 "+(IndustrialGeneration.skyVisible(l,be.device().has(DeviceType.Capability.WIND)?be.getBlockPos().above(2):be.getBlockPos())?"可见":"被遮挡"));}}
        if(be.device().generator()){
            if(be.device().has(DeviceType.Capability.WIND))help.add("底座放置，占据竖直三格；点击任意一节可操作，拆除任意一节回收整台。最低高度 "+IndustrialRules.current().windBaseHeight+"，基础 "+IndustrialRules.current().windBasePower+" 点/秒，每升 "+IndustrialRules.current().windHeightStep+" 格再加 "+IndustrialRules.current().windPowerStep+" 点/秒；必须露天且有天空光。");
            else if(be.device().has(DeviceType.Capability.SOLAR))help.add("日照边界 "+java.util.Arrays.toString(IndustrialRules.current().solarPeriods)+"；白天 "+IndustrialRules.current().solarDayPower+"，晨昏 "+IndustrialRules.current().solarEdgePower+"，雨天 "+IndustrialRules.current().solarRainPower+" 点/秒，雷暴或夜间停机。");
            else {var rule=IndustrialRules.current().generators.get(be.device().id);help.add("必要流体 "+(rule.fluid().isEmpty()?"无":rule.fluid()+" · "+rule.fluidPerSecond()+" mB/秒"));for(var holder:((ServerLevel)be.getLevel()).recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get())){var r=holder.value();if(r.machine().equals("fuel_"+be.device().id))help.add(r.ingredients().stream().map(n->n.count()+" × "+n.ingredient().items().limit(2).map(h->new ItemStack(h.value()).getHoverName().getString()).reduce((a,b)->a+" / "+b).orElse("缺少兼容燃料")).reduce((a,b)->a+"；"+b).orElse("无燃料")+"："+String.format(java.util.Locale.ROOT,"%.1f 秒",r.ticks()/20.0)+"，共 "+r.energy()+" 点");}}
        }
        if(be.device().has(DeviceType.Capability.STORAGE_NETWORK)){help.add("直接放置即可工作，无需底座。");help.add("三维半径 "+be.radius()+"，本核心容量 "+IndustrialRules.current().beaconCapacity+"；任意等级机器均可接入。");help.add("信标之间用权杖手动连接；中继卸载保留原网络关系。");}
        EXTRA_HELP.getOrDefault(be.device().id,(machine,lines)->{}).accept(be,help);
        if(be.device().has(DeviceType.Capability.SMELTING)){help.add("与原版普通熔炉使用相同配方、产物和基础熔炼时长；不使用高炉或烟熏炉专属配方。");help.add("每份耗能100点；当前速度为原版的"+IndustrialRules.current().furnaceSpeeds[be.tier()-1]+"倍，三个输入槽轮流加工。");}
        if(be.device().has(DeviceType.Capability.LOGISTICS)){int t=be.tier()-1;help.add("每 "+IndustrialRules.current().transferIntervals[t]+" tick最多搬运 "+IndustrialRules.current().transferAmounts[t]+" 件，每件 "+IndustrialRules.current().transferEnergy[t]+" 点。");help.add("放在源容器上方；下方为本工业机器时仅抽取产物，不转移原料、燃料或供能水晶。");help.add("只支持同维度；目标不存在或未加载时暂停，原坐标恢复容器后继续。");help.add("当前目标："+j.get("target").getAsString());}
        if(be.device().has(DeviceType.Capability.HARVEST)){help.add("水平 "+IndustrialRules.current().harvestSizes[be.tier()-1]+"×"+IndustrialRules.current().harvestSizes[be.tier()-1]+"，每 "+IndustrialRules.current().harvestPeriod+" tick一轮，每株 "+IndustrialRules.current().harvestEnergy+" 点。");help.add("完整成熟掉落（含种子、副产物）入下方箱，植物保留并重置；不扣种子补种。");}
        if(be.device().needsStructure()){JsonArray missing=new JsonArray();for(BlockPos p:IndustrialStructures.missing(be))missing.add(p.asLong());j.add("missing",missing);j.addProperty("structure_material",new ItemStack(IndustrialStructures.structureMaterial(be.tier())).getHoverName().getString());}
        if(!be.device().randomCurrency()&&!be.batchResult.isEmpty())j.add("preview",ItemStack.OPTIONAL_CODEC.encodeStart(be.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),be.batchResult).getOrThrow());
        if(be.device().has(DeviceType.Capability.FLUID_PROCESSING)&&!be.batchFluid.isEmpty())j.add("preview",ItemStack.OPTIONAL_CODEC.encodeStart(be.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),new ItemStack(be.batchFluid.getFluid().getBucket())).getOrThrow());
        help.add("当前状态："+be.status);j.add("help",help);return j;
    }
}
