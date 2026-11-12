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
    static {register(DeviceType.FORGE.id,(be,help)->{help.add("3×3×3：控制器位于底层正面中央，另需25块同级科技机外壳，中心留空。");help.add("下方容器位于结构外，可接收控制器产物。");});register(DeviceType.STAR.id,(be,help)->{help.add("5×5地面、总高5格：放置点位于圆形底座中央，放置一台后自动补全整机，无需另放机壳。");help.add("运行时浮现太阳，底座橙色灯槽与太阳同步缓慢呼吸；停止发电时太阳与悬浮光环消失，仅保留底座。");help.add("只有圆盘具有碰撞，悬浮太阳可穿过。");help.add(ArtificialStarPartBlock.hazardDescription());help.add("点击底座或运行中的太阳可操作，权杖可联网及绑定供料、供液。拆除任一可见部件回收整台。");});}
    private IndustrialDeviceInfo(){}
    public static JsonObject enrich(MachineBlockEntity be,MachineLane line,JsonObject j){
        JsonArray help=new JsonArray();help.add("工业魔能独立于角色MP；设备只在必要区块加载时运行。");
        j.addProperty("technology_tier",be.technologyTier());j.addProperty("processing_tier",be.processingTier());
        help.add("科技等级："+IndustrialTechnology.label(be.technologyTier())+"；工艺能力上限："+IndustrialTechnology.label(be.processingTier())+"。");
        if(be.device().processing())help.add(be.device().has(DeviceType.Capability.FLUID_PROCESSING)?"本机带液体加工能力：整批备料，按工作进度反应和产出。":"本机完成读条时才扣左侧原料并整批出货；原料不足取消读条，已耗魔能不退。");
        if(be.device().has(DeviceType.Capability.IGNITION)){j.addProperty("progress",be.ignited?1:be.ignitionPaid/(double)IndustrialGeneration.IGNITION_ENERGY);help.add("T6人造恒星：每次点火需要1枚T6点火芯与1亿魔能，只领取现有生产后的剩余供能；缺料、缺冷却或结构损坏需重新点火，区块卸载与重启仅暂停。");help.add("点火状态："+(be.ignited?"已点火":"未点火 · 已充能"+be.ignitionPaid)+"；冷却液100B/秒。");}
        if(be.currencyUpgrade!=null){
            j.addProperty("upgrade_capacity",CurrencyUpgrader.CAPACITY);
            help.add("左9格输入、右9格输出，仅接收自然掉落池中低于史诗的通货；不同种类可按同稀有度混合凑4枚。");
            help.add("每20tick一轮，低到高批量处理；每组50%成功，成功随机出下一稀有度1枚。失败同样消耗4枚与完整魔能，最高史诗。");
            help.add("普通→稀有100；稀有→神器1000；神器→传说10000；传说→史诗100000魔能。");
            help.add("缓存1000000魔能，支持信标及1—5阶水晶。右侧9格都被占用即停，未叠满也不继续。");
            help.add("合成结束后退出活动队列；新投料或解除满仓/缺能阻塞唤醒。休眠保留充能与自动向下输出，产物不自行回流。");
        }
        if(be.device().randomCurrency()){var rules=IndustrialRules.current();JsonArray probabilities=new JsonArray();for(double p:cn.yumis.fight.modules.economy.api.CurrencyRules.factoryProbabilities(rules.currencyRarityWeights,rules.currencyItemWeights))probabilities.add(p);j.add("currency_probabilities",probabilities);}
        if(be.device().processing()){help.add(be.device().has(DeviceType.Capability.GRID_ONLY)?"本机通过信标电网供能。":"备用水晶插槽仅接受"+(be.tier()==5?"8":(be.tier()*2-1)+"～"+be.tier()*2)+"阶；储物袋可按等量魔能取出对应阶位。");help.add("产物先存本机，再向下方容器输出；缺能或满仓保留批次。");help.add("当前显示第"+(line.index+1)+"条产线；进度保留。");if(be.device().has(DeviceType.Capability.PARALLEL))help.add("本机 "+be.lineCount()+" 条独立产线，每条单独输入、输出和保存进度；升级解锁新线，不移动库存。单线每升一级速度翻倍。");if(be.device().randomCurrency())help.add("每条产线每轮必出1枚；使用当前工业奖池，不读取玩家掉宝，不再判5%。点击概率查看每种通货的实际权重。");}
        if(be.device().generator()){j.addProperty("power",be.currentPower);help.add("实际产能 "+be.currentPower+" 点/秒；满网继续运行，多余产能浪费。");if(be.fuelTotal>0)help.add("本燃料总产能 "+be.fuelEnergy+"，燃烧 "+String.format(java.util.Locale.ROOT,"%.1f 秒",be.fuelTotal/20.0)+"；流体 "+be.fuelFluidRate+" mB/秒。");if(be.device().has(DeviceType.Capability.SOLAR)||be.device().has(DeviceType.Capability.WIND)){ServerLevel l=(ServerLevel)be.getLevel();help.add("时间 "+Math.floorMod(l.getDefaultClockTime(),24000)+"；天气 "+(l.isThundering()?"雷暴":l.isRaining()?"雨":"晴")+"；顶部天空 "+(IndustrialGeneration.skyVisible(l,be.device().has(DeviceType.Capability.WIND)?be.getBlockPos().above(2):be.getBlockPos())?"可见":"被遮挡"));}}
        if(be.device().generator()){
            if(be.device().has(DeviceType.Capability.WIND))help.add("底座放置，占据竖直三格；点击任意一节可操作，拆除任意一节回收整台。最低高度 "+IndustrialRules.current().windBaseHeight+"，基础 "+IndustrialRules.current().windBasePower+" 点/秒，每升 "+IndustrialRules.current().windHeightStep+" 格再加 "+IndustrialRules.current().windPowerStep+" 点/秒；必须露天且有天空光。");
            else if(be.device().has(DeviceType.Capability.SOLAR))help.add("日照边界 "+java.util.Arrays.toString(IndustrialRules.current().solarPeriods)+"；白天 "+IndustrialRules.current().solarDayPower+"，晨昏 "+IndustrialRules.current().solarEdgePower+"，雨天 "+IndustrialRules.current().solarRainPower+" 点/秒，雷暴或夜间停机。");
            else {var rule=IndustrialRules.current().generators.get(be.device().id);help.add("必要流体 "+(rule.fluid().isEmpty()?"无":rule.fluid()+" · "+rule.fluidPerSecond()+" mB/秒"));for(var holder:((ServerLevel)be.getLevel()).recipeAccess().recipeMap().byType(IndustrialContent.RECIPE_TYPE.get())){var r=holder.value();if(r.machine().equals("fuel_"+be.device().id))help.add(r.ingredients().stream().map(n->n.count()+" × "+n.ingredient().items().limit(2).map(h->new ItemStack(h.value()).getHoverName().getString()).reduce((a,b)->a+" / "+b).orElse("缺少兼容燃料")).reduce((a,b)->a+"；"+b).orElse("无燃料")+"："+String.format(java.util.Locale.ROOT,"%.1f 秒",r.ticks()/20.0)+"，共 "+r.energy()+" 点");}}
        }
        if(be.device().has(DeviceType.Capability.STORAGE_NETWORK)){help.add("直接放置即可工作，无需底座。");help.add("三维半径 "+be.radius()+"，本核心容量 "+IndustrialRules.current().beaconCapacity+"；任意等级机器均可接入。");help.add("信标之间用权杖手动连接；中继卸载保留原网络关系。");}
        if(be.device().has(DeviceType.Capability.MINING)){help.add("矿藏首次判定后不重抽，储量无限；圆石不参与抽签，在任何区块均可开采，固定普通丰度。");help.add("本区块 "+(be.getBlockPos().getX()>>4)+", "+(be.getBlockPos().getZ()>>4)+"；只显示本机等级可识别的矿物。");help.add("矿物费用为最低等级、普通丰度、无冷却的一批总耗能；机身升级保留原线性速度与平方功率关系，丰度和冷却影响每批时间与费用。");help.add("水总速度×1.5，冷却液×2.5，互斥；工作时随等级耗液，当前批次锁定冷却类型。玩家手挖至多T3，高阶原矿由矿机取得。");}
        if(be.device().has(DeviceType.Capability.ENERGY_STORAGE)){help.add("接网增加 "+be.storageCapacity()+" 魔能容量，余能存入本节点；无额外充放速度限制，不接收水晶充能。");help.add("断网、卸载保存本节点余额，拆除清空；原地升级保留余额。");be.status=IndustrialWorld.get((ServerLevel)be.getLevel()).validBeacon(be)==null?"未接网，已保留本节点余额":"已接网，共享电网存量";}
        if(be.device().has(DeviceType.Capability.FLUID_STORAGE))help.add("一罐一种液体，容量 "+be.fluidCapacity()+" mB。拆除清空；手持下一档科技机外壳右键原地升级可保留存量。");
        if(be.device().has(DeviceType.Capability.PUMP))help.add("接网且相邻任一面有水源时抽水；出水缓存自动供给六面相邻设备的入液口，紧贴涡轮任一面即可直接供水。逐tick轮换优先方向，目标满罐时保留余水；已有缓存停机或断网后仍可输出。");
        if(be.device().has(DeviceType.Capability.FLUID_INPUT_ALL_SIDES))help.add("六面均为入液口，可接收本机所需流体；抽水机紧贴任一面即可自动供水。");
        if(be.device().has(DeviceType.Capability.FLUID_LOGISTICS)){int t=be.tier()-1;help.add("每次最多 "+IndustrialRules.current().fluidTransferAmounts[t]+" mB，成功操作一次 "+IndustrialRules.current().fluidTransferOperationEnergy[t]+" 点；未满批同样收费。");help.add("权杖移动模式先绑定水晶，再选择来源和目标容器；每次成功批量操作固定收费，未加载、空源或满罐不收费。");}
        if(be.device().has(DeviceType.Capability.LOGISTICS)||be.device().has(DeviceType.Capability.FLUID_LOGISTICS))help.add("本档支持 "+be.routeLimit()+" 对来源与目标并行；配对面板可删除，并点击端点坐标显示光束。");
        help.add("侧边箭头展开3格插件，每格按插件定义限制数量；每件独立生效，收起仍有效。");
        EXTRA_HELP.getOrDefault(be.device().id,(machine,lines)->{}).accept(be,help);
        if(be.device().has(DeviceType.Capability.SMELTING)){help.add("与原版普通熔炉使用相同配方、产物和基础熔炼时长；不使用高炉或烟熏炉专属配方。");help.add("普通熔炼每份耗能50点，高阶矿物使用专属工业配方；当前速度为原版的"+IndustrialRules.current().furnaceSpeeds[be.tier()-1]+"倍，每条产线的三个输入槽轮流加工，各线同时运行。");}
        if(be.device().has(DeviceType.Capability.LOGISTICS)){int t=be.tier()-1;help.add("每 "+IndustrialRules.current().transferIntervals[t]+" tick最多搬运 "+IndustrialRules.current().transferAmounts[t]+" 件，成功操作一次 "+IndustrialRules.current().transferEnergy[t]+" 点。");help.add("权杖移动模式先绑定水晶，再选择来源和目标；来源机器只抽取产物，目标发电机自动接入背面燃料槽，仅接收适用燃料。");help.add("只支持同维度；目标不存在或未加载时暂停，原坐标恢复容器后继续。");help.add("当前目标："+j.get("target").getAsString());}
        if(be.device().has(DeviceType.Capability.HARVEST)){help.add("水平 "+IndustrialRules.current().harvestSizes[be.tier()-1]+"×"+IndustrialRules.current().harvestSizes[be.tier()-1]+"，每 "+IndustrialRules.current().harvestPeriod+" tick一轮，每株 "+IndustrialRules.current().harvestEnergy+" 点。");help.add("完整成熟掉落（含种子、副产物）入下方箱，植物保留并重置；不扣种子补种。");}
        if(be.device().needsStructure()){JsonArray missing=new JsonArray();for(BlockPos p:IndustrialStructures.missing(be))missing.add(p.asLong());j.add("missing",missing);j.add("structure",IndustrialStructures.description(be));j.addProperty("structure_material",be.device()==DeviceType.STAR?"放置后自动补全，无需额外机壳":new ItemStack(IndustrialStructures.structureMaterial(be)).getHoverName().getString());}
        if(!be.device().randomCurrency()&&!line.batchResults.isEmpty())j.add("preview",ItemStack.OPTIONAL_CODEC.encodeStart(be.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),line.batchResults.getFirst()).getOrThrow());
        if(be.device().has(DeviceType.Capability.FLUID_PROCESSING)&&!line.batchFluid.isEmpty())j.add("preview",ItemStack.OPTIONAL_CODEC.encodeStart(be.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),new ItemStack(line.batchFluid.getFluid().getBucket())).getOrThrow());
        if(be.device().has(DeviceType.Capability.FLUID_STORAGE))be.status="独立液体库存";String status=!be.running?"已停止":be.device().processing()?line.displayStatus():be.status;j.addProperty("status",status);help.add("当前状态："+status);j.add("help",help);return j;
    }
}
