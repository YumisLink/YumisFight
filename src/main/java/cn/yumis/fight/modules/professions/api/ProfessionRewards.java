package cn.yumis.fight.modules.professions.api;

import cn.yumis.fight.modules.inventory.api.InventoryEdits;
import cn.yumis.fight.platform.ItemRegistry;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.Function;

/** Personal profession decisions from raw world events, evaluated by the owning client. */
public final class ProfessionRewards {
    public record Result(Map<String,Profession> professions,List<ItemStack> drops){}
    private ProfessionRewards(){}
    private static final Set<String> COOKED=Set.of("cooked_beef","cooked_porkchop","cooked_chicken","cooked_mutton","cooked_rabbit","cooked_cod","cooked_salmon","baked_potato","dried_kelp","dish_grilled_fish","dish_herb_soup");
    private static final Set<String> LEATHER=Set.of("leather","rabbit_hide","leather_helmet","leather_chestplate","leather_leggings","leather_boots","hide_tanned","hide_reinforced");
    private static final Set<String> HERBS=Set.of("herb_sunleaf","herb_moonroot");
    private static final Set<String> ORES=Set.of("ore_raw_chunk","ore_deep_shard");
    private static final Set<String> ELIXIRS=Set.of("elixir_minor_heal","elixir_deep_water");
    private static final Set<String> RUNES=Set.of("rune_blank","rune_ember");
    private static final Set<String> INGOTS=Set.of("ingot_billet","ingot_alloy");
    private static final Set<String> PARTS=Set.of("part_gear","part_crystal_lens");
    private static final List<String> GEAR_SUFFIXES=List.of("_sword","_axe","_pickaxe","_shovel","_hoe","_helmet","_chestplate","_leggings","_boots");
    /** 这三条线用"工艺额外产出"作为等级被动，不侵入附魔台、耐久与工业能耗链路。 */
    private static final Set<String> CRAFT_YIELD=Set.of("inscription","leatherworker","mechanic");
    private static final List<String> MACHINE_PREFIXES=List.of("mana_","mineral_generator","currency_fabricator","coal_generator","solar_collector","turbine_generator","wind_generator","thermal_generator","nuclear_","rtg_","transfer_crystal","harvest_crystal","energy_crystal","industrial_upgrade","industrial_magic_stone");
    private static boolean mature(BlockState s){
        return s.getBlock() instanceof CropBlock crop&&crop.isMaxAge(s)||s.is(Blocks.NETHER_WART)&&s.getValue(NetherWartBlock.AGE)==3||s.is(Blocks.COCOA)&&s.getValue(CocoaBlock.AGE)==2||s.is(Blocks.SWEET_BERRY_BUSH)&&s.getValue(SweetBerryBushBlock.AGE)==3;
    }
    private static boolean ore(BlockState s){
        String path=BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return path.endsWith("_ore")||path.equals("ancient_debris");
    }
    private static String id(ItemStack stack){
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }
    /** 0=没拿鱼竿；1=普通；2=好运；3=石墨；4=海潮；5=冥河幽光；6=远行者鱼友。 */
    private static int rodCatches(List<ItemStack> hands){
        int best=0;
        for(ItemStack stack:hands){
            if(!isRod(stack))continue;
            String name=id(stack);
            int count=name.equals(cn.yumis.fight.modules.professions.platform.ProfessionContent.FARER_ROD_ID)?6
                :name.equals(cn.yumis.fight.modules.professions.platform.ProfessionContent.STYX_ROD_ID)?5
                :name.equals(cn.yumis.fight.modules.professions.platform.ProfessionContent.TIDAL_ROD_ID)?4
                :name.equals(cn.yumis.fight.modules.professions.platform.ProfessionContent.GRAPHITE_ROD_ID)?3
                :name.equals(cn.yumis.fight.modules.professions.platform.ProfessionContent.LUCKY_ROD_ID)?2:1;
            best=Math.max(best,count);
        }
        return best;
    }
    private static boolean isRod(ItemStack stack){
        return stack.getItem() instanceof net.minecraft.world.item.FishingRodItem;
    }
    private static boolean holding(List<ItemStack> hands,String rodId){
        return hands.stream().anyMatch(stack->rodId.equals(id(stack)));
    }
    /** 能出钻石的鱼竿：石墨/海潮/冥河幽光/远行者。 */
    private static boolean treasureRod(List<ItemStack> hands){
        return holding(hands,cn.yumis.fight.modules.professions.platform.ProfessionContent.GRAPHITE_ROD_ID)
            ||holding(hands,cn.yumis.fight.modules.professions.platform.ProfessionContent.TIDAL_ROD_ID)
            ||holding(hands,cn.yumis.fight.modules.professions.platform.ProfessionContent.STYX_ROD_ID)
            ||holding(hands,cn.yumis.fight.modules.professions.platform.ProfessionContent.FARER_ROD_ID);
    }
    private static boolean obsidianRod(List<ItemStack> hands){
        return holding(hands,cn.yumis.fight.modules.professions.platform.ProfessionContent.TIDAL_ROD_ID)
            ||holding(hands,cn.yumis.fight.modules.professions.platform.ProfessionContent.STYX_ROD_ID)
            ||holding(hands,cn.yumis.fight.modules.professions.platform.ProfessionContent.FARER_ROD_ID);
    }
    private static boolean blazeRod(List<ItemStack> hands){
        return holding(hands,cn.yumis.fight.modules.professions.platform.ProfessionContent.STYX_ROD_ID)
            ||holding(hands,cn.yumis.fight.modules.professions.platform.ProfessionContent.FARER_ROD_ID);
    }
    private static boolean netherStarRod(List<ItemStack> hands){
        return holding(hands,cn.yumis.fight.modules.professions.platform.ProfessionContent.FARER_ROD_ID);
    }
    private static boolean styxRod(List<ItemStack> hands){
        return holding(hands,cn.yumis.fight.modules.professions.platform.ProfessionContent.STYX_ROD_ID);
    }
    private static boolean farerRod(List<ItemStack> hands){
        return holding(hands,cn.yumis.fight.modules.professions.platform.ProfessionContent.FARER_ROD_ID);
    }
    private static boolean tidalRod(List<ItemStack> hands){
        return holding(hands,cn.yumis.fight.modules.professions.platform.ProfessionContent.TIDAL_ROD_ID);
    }
    /** 传说看地形/高度，史诗看特殊水域/神殿/雪原，神话看虚空与珊瑚；其余档位只需要维度。 */
    private static cn.yumis.fight.modules.professions.api.FishSpecies.Context context(Player player){
        boolean nether=player.level().dimension().equals(net.minecraft.world.level.Level.NETHER);
        boolean high=player.getY()>=100;
        boolean geode=inGeode(player);
        boolean soul=nearSoulSandWater(player);
        boolean monument=!soul&&nearMonument(player);
        boolean snowy=!soul&&!monument&&inSnowyArea(player);
        boolean inVoid=player.getY()<player.level().getMinY();
        boolean coral=nearCoral(player);
        return new cn.yumis.fight.modules.professions.api.FishSpecies.Context(nether,high,geode,soul,monument,snowy,inVoid,coral);
    }
    /** 珊瑚区：珊瑚块/珊瑚扇/珊瑚礁；用方块 id 判定，避免绑定具体珊瑚类名。 */
    private static boolean nearCoral(Player player){
        var base=player.blockPosition();
        for(var pos:net.minecraft.core.BlockPos.betweenClosed(base.offset(-4,-2,-4),base.offset(4,2,4))){
            var block=player.level().getBlockState(pos).getBlock();
            if(BuiltInRegistries.BLOCK.getKey(block).getPath().contains("coral"))return true;
        }
        return false;
    }
    private static boolean blockIs(Player player,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.Block block){
        return player.level().getBlockState(pos).is(block);
    }
    private static boolean inGeode(Player player){
        var base=player.blockPosition();
        for(var pos:net.minecraft.core.BlockPos.betweenClosed(base.offset(-3,-3,-3),base.offset(3,3,3))){
            if(blockIs(player,pos,net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK)||blockIs(player,pos,net.minecraft.world.level.block.Blocks.BUDDING_AMETHYST))return true;
        }
        return false;
    }
    /** 9×9 范围内灵魂沙达到一定数量，且玩家在水里。 */
    private static boolean nearSoulSandWater(Player player){
        if(!player.isInWater())return false;
        var base=player.blockPosition();
        int count=0;
        for(var pos:net.minecraft.core.BlockPos.betweenClosed(base.offset(-4,-2,-4),base.offset(4,2,4))){
            if(blockIs(player,pos,net.minecraft.world.level.block.Blocks.SOUL_SAND)&&++count>=12)return true;
        }
        return false;
    }
    /** 海底神殿特征方块（海晶灯/暗海晶石/海晶石）。 */
    private static boolean nearMonument(Player player){
        var base=player.blockPosition();
        int count=0;
        for(var pos:net.minecraft.core.BlockPos.betweenClosed(base.offset(-6,-4,-6),base.offset(6,4,6))){
            if(blockIs(player,pos,net.minecraft.world.level.block.Blocks.SEA_LANTERN)
                ||blockIs(player,pos,net.minecraft.world.level.block.Blocks.DARK_PRISMARINE)
                ||blockIs(player,pos,net.minecraft.world.level.block.Blocks.PRISMARINE)
                ||blockIs(player,pos,net.minecraft.world.level.block.Blocks.PRISMARINE_BRICKS)){
                if(++count>=8)return true;
            }
        }
        return false;
    }
    /** 冰雪区域：附近冰/雪/细雪密度。 */
    private static boolean inSnowyArea(Player player){
        var base=player.blockPosition();
        int count=0;
        for(var pos:net.minecraft.core.BlockPos.betweenClosed(base.offset(-4,-2,-4),base.offset(4,2,4))){
            if(blockIs(player,pos,net.minecraft.world.level.block.Blocks.ICE)
                ||blockIs(player,pos,net.minecraft.world.level.block.Blocks.PACKED_ICE)
                ||blockIs(player,pos,net.minecraft.world.level.block.Blocks.BLUE_ICE)
                ||blockIs(player,pos,net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
                ||blockIs(player,pos,net.minecraft.world.level.block.Blocks.POWDER_SNOW)){
                if(++count>=8)return true;
            }
        }
        return false;
    }
    /** 制作结果按材质/命名归类；同一件只归一条线，避免一次操作双倍经验。 */
    private static String craftProfession(String item){
        if(HERBS.contains(item))return "farmer";
        if(ORES.contains(item))return "miner";
        if(ELIXIRS.contains(item))return "alchemist";
        if(RUNES.contains(item))return "inscription";
        if(LEATHER.contains(item))return "leatherworker";
        if(cn.yumis.fight.modules.professions.platform.ProfessionContent.ROD_ID.equals(item)
            ||cn.yumis.fight.modules.professions.platform.ProfessionContent.LUCKY_ROD_ID.equals(item)
            ||cn.yumis.fight.modules.professions.platform.ProfessionContent.GRAPHITE_ROD_ID.equals(item)
            ||cn.yumis.fight.modules.professions.platform.ProfessionContent.TIDAL_ROD_ID.equals(item)
            ||cn.yumis.fight.modules.professions.platform.ProfessionContent.STYX_ROD_ID.equals(item)
            ||cn.yumis.fight.modules.professions.platform.ProfessionContent.FARER_ROD_ID.equals(item))return "fisherman";
        if(INGOTS.contains(item)||isGear(item)&&!item.startsWith("leather_"))return "blacksmith";
        if(PARTS.contains(item))return "mechanic";
        for(String prefix:MACHINE_PREFIXES)if(item.startsWith(prefix))return "mechanic";
        if(COOKED.contains(item))return "cook";
        return null;
    }
    private static boolean isGear(String item){
        for(String suffix:GEAR_SUFFIXES)if(item.endsWith(suffix))return true;
        return false;
    }
    public static FishSpecies.Context fishingContext(Player player){return context(player);}
    public static Result decide(Player player,JsonObject event,Function<String,Profession> source,Random random){
        var hands=new ArrayList<ItemStack>();if(event.has("hands"))for(var raw:event.getAsJsonArray("hands"))hands.add(InventoryEdits.decode(player,raw));else{hands.add(player.getMainHandItem());hands.add(player.getOffhandItem());}
        var changes=new LinkedHashMap<String,Profession>();var drops=new ArrayList<ItemStack>();
        java.util.function.BiConsumer<String,Long> award=(id,xp)->{var value=changes.computeIfAbsent(id,key->source.apply(key));value.award(xp,false);value.grantTitle(id);};
        String kind=event.get("kind").getAsString();String profession=null;
        switch(kind){
            case "berry"->{var before=BlockState.CODEC.parse(JsonOps.INSTANCE,event.get("before")).getOrThrow();var after=BlockState.CODEC.parse(JsonOps.INSTANCE,event.get("after")).getOrThrow();if(mature(before)&&after.is(before.getBlock())&&after.getValue(SweetBerryBushBlock.AGE)==1)profession="farmer";}
            case "harvest"->{var state=BlockState.CODEC.parse(JsonOps.INSTANCE,event.get("state")).getOrThrow();profession=mature(state)?"farmer":ore(state)?"miner":null;}
            case "craft"->profession=craftProfession(event.get("item").getAsString());
            case "smelt"->{if(COOKED.contains(event.get("item").getAsString()))profession="cook";}
            case "brew"->profession="alchemist";
            case "enchant"->profession="inscription";
            case "fishing","lava","void"->{
                int level=source.apply("fisherman").level(),catches=rodCatches(hands);
                if(catches>0){boolean lava=kind.equals("lava"),inVoid=kind.equals("void");for(int i=0;i<catches;i++){
                    FishTier tier=inVoid?FishTier.MYTHIC:lava?FishTier.ARTIFACT:FishingRules.roll(level,random.nextDouble());
                    var context=lava||inVoid?FishSpecies.Context.plain(lava&&player.level().dimension().equals(net.minecraft.world.level.Level.NETHER)):(tier==FishTier.LEGENDARY||tier==FishTier.EPIC||tier==FishTier.MYTHIC)?(event.has("fishingContext")?new Gson().fromJson(event.get("fishingContext"),FishSpecies.Context.class):context(player)):FishSpecies.Context.plain(player.level().dimension().equals(net.minecraft.world.level.Level.NETHER));
                    if(tier==FishTier.EPIC&&!context.epicAvailable())tier=FishTier.LEGENDARY;award.accept("fisherman",FishingRules.experience(tier));
                    var treasure=treasureRod(hands)?treasure(random,obsidianRod(hands),blazeRod(hands),netherStarRod(hands)):ItemStack.EMPTY;
                    drops.add(treasure.isEmpty()?ItemRegistry.item(FishSpecies.itemFor(tier,random.nextDouble(),context),1):treasure);
                }}else{award.accept("fisherman",ProfessionCatalog.require("fisherman").operationExperience());if(random.nextDouble()<ProfessionBonuses.fishingLuck(level))drops.add(ItemRegistry.item(random.nextDouble()<.2?"catch_pearl":"catch_riverfish",1));}
            }
            default->throw new IllegalArgumentException("未知生活事件");
        }
        if(profession!=null){award.accept(profession,ProfessionCatalog.require(profession).operationExperience());int level=changes.get(profession).level();
            if(kind.equals("harvest")&&random.nextDouble()<ProfessionBonuses.extraYieldChance(level)&&event.has("drop")){var first=InventoryEdits.decode(player,event.get("drop"));if(!first.isEmpty())drops.add(new ItemStack(first.getItem(),1));}
            else if(kind.equals("craft")&&profession.equals("blacksmith")&&random.nextDouble()<ProfessionBonuses.smithingRefundChance(level)&&event.has("input")){var first=InventoryEdits.decode(player,event.get("input"));if(!first.isEmpty())drops.add(new ItemStack(first.getItem(),1));}
            else if(kind.equals("craft")&&CRAFT_YIELD.contains(profession)&&random.nextDouble()<ProfessionBonuses.craftYieldChance(level)&&event.has("output")){var first=InventoryEdits.decode(player,event.get("output"));if(!first.isEmpty())drops.add(new ItemStack(first.getItem(),1));}
            else if(kind.equals("smelt")&&random.nextDouble()<ProfessionBonuses.cookingYieldChance(level)&&event.has("output")){var first=InventoryEdits.decode(player,event.get("output"));if(!first.isEmpty())drops.add(new ItemStack(first.getItem(),1));}
        }
        return new Result(Map.copyOf(changes),List.copyOf(drops));
    }
    private static ItemStack treasure(Random random,boolean obsidian,boolean blaze,boolean star){
        if(FishingRules.isDiamond(random.nextDouble()))return new ItemStack(net.minecraft.world.item.Items.DIAMOND,1);
        if(obsidian&&FishingRules.isObsidian(random.nextDouble()))return new ItemStack(net.minecraft.world.item.Items.OBSIDIAN,1);
        if(blaze&&FishingRules.isBlazeRod(random.nextDouble()))return new ItemStack(net.minecraft.world.item.Items.BLAZE_ROD,1);
        if(star&&FishingRules.isNetherStar(random.nextDouble()))return new ItemStack(net.minecraft.world.item.Items.NETHER_STAR,1);return ItemStack.EMPTY;
    }
}
