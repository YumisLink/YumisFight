package cn.yumis.fight.modules.professions.application;
import cn.yumis.fight.modules.professions.api.FishTier;
import cn.yumis.fight.modules.professions.api.FishingRules;
import cn.yumis.fight.modules.professions.api.ProfessionBonuses;
import cn.yumis.fight.modules.professions.api.ProfessionCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import java.util.*;
public final class ProfessionEvents {
    private ProfessionEvents(){}
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
    /** 正在被"水面行走"托住的玩家；离开水面或换竿时恢复重力。 */
    private static final Set<UUID> FLOATING=new HashSet<>();
    /** 史诗竿水下 +30% 移速的属性修饰符 id。 */
    private static final net.minecraft.resources.Identifier STYX_SWIM=net.minecraft.resources.Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","styx_swim");
    private static boolean mature(BlockState s){
        return s.getBlock() instanceof CropBlock crop&&crop.isMaxAge(s)||s.is(Blocks.NETHER_WART)&&s.getValue(NetherWartBlock.AGE)==3||s.is(Blocks.COCOA)&&s.getValue(CocoaBlock.AGE)==2||s.is(Blocks.SWEET_BERRY_BUSH)&&s.getValue(SweetBerryBushBlock.AGE)==3;
    }
    private static boolean ore(BlockState s){
        String path=BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return path.endsWith("_ore")||path.equals("ancient_debris");
    }
    /** 草药生长环境：灵魂土/灵魂沙 > 下界 > 雪地 > 沼泽 > 山地 > 草原/森林。 */
    private static cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat habitatAt(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos pos){
        String under=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.below()).getBlock()).getPath();
        if(under.equals("bone_block"))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.BONE;
        if(under.equals("obsidian"))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.OBSIDIAN;
        if(under.contains("soul_soil")||under.contains("soul_sand"))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.SOUL;
        var biomeId=level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getKey(level.getBiome(pos).value());
        String path=biomeId==null?"":biomeId.getPath();
        boolean highPeak=pos.getY()>=120&&(path.contains("jagged_peaks")||path.contains("windswept")||path.contains("stony_peaks"));
        if(highPeak&&under.contains("stone"))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.PEAKS;
        if(level.dimension().equals(net.minecraft.world.level.Level.END)&&path.contains("highlands"))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.END_HIGHLANDS;
        if(path.contains("mushroom_fields"))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.MUSHROOM;
        if(path.contains("deep_dark"))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.DEEP_DARK;
        if(under.contains("stone")&&pos.getY()>=100&&(path.contains("jagged_peaks")||path.contains("stony_peaks")))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.STORM_CLIFF;
        if(nearMonumentBlocks(level,pos))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.MONUMENT;
        if(path.contains("bamboo_jungle"))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.BAMBOO;
        if(path.contains("dark_forest"))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.DARK_FOREST;
        if(path.contains("sunflower_plains"))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.SUNFLOWER;
        if(level.dimension().equals(net.minecraft.world.level.Level.NETHER)){
            return path.contains("crimson")||path.contains("warped")
                ?cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.NETHER_FOREST
                :cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.NETHER_WASTES;
        }
        if(path.contains("snow")||path.contains("frozen")||path.contains("ice")||path.equals("grove"))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.SNOWY;
        if(path.contains("swamp"))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.SWAMP;
        if(path.contains("windswept")&&pos.getY()>=90)return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.WINDSWEPT;
        if(path.contains("ocean"))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.OCEAN;
        if(path.contains("savanna"))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.SAVANNA;
        if(level.getBiome(pos).is(net.minecraft.tags.BiomeTags.IS_MOUNTAIN))return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.MOUNTAIN;
        return cn.yumis.fight.modules.professions.api.HerbSpecies.Habitat.PLAINS;
    }
    /** 海底神殿附近：附近有海晶石/海晶灯/暗海晶石。 */
    private static boolean nearMonumentBlocks(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos pos){
        for(var p:net.minecraft.core.BlockPos.betweenClosed(pos.offset(-12,-6,-12),pos.offset(12,6,12))){
            String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath();
            if(id.contains("prismarine")||id.contains("sea_lantern"))return true;
        }
        return false;
    }
    /** 基底决定固定产物：骨块必出死亡花苞，黑曜石必出黑石草。 */
    private static String groundHerbId(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos pos){
        String under=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.below()).getBlock()).getPath();
        if(under.equals("bone_block"))return cn.yumis.fight.modules.professions.api.HerbSpecies.DEATHBLOOM;
        if(under.equals("obsidian"))return cn.yumis.fight.modules.professions.api.HerbSpecies.BLACKSTONE_GRASS;
        return null;
    }
    /** 草药品种：脚下是骨块/黑曜石时固定产物，其余按环境与档位抽取。 */
    private static String herbItemAt(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos pos,cn.yumis.fight.modules.professions.api.HerbTier tier,net.minecraft.util.RandomSource random){
        String ground=groundHerbId(level,pos);
        if(ground!=null)return ground;
        return cn.yumis.fight.modules.professions.api.HerbSpecies.pick(habitatAt(level,pos),tier,random.nextDouble());
    }
    /** 按工具决定一次收几株、有没有种子、档位加成多少；噩梦藤与海底草药固定产物。 */
    private static void harvestWith(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos pos,ServerPlayer p,java.util.function.Supplier<ProfessionService> service,String toolId,String operation,net.minecraft.world.level.block.state.BlockState harvested){
        int farmerLevel=service.get().get(p.getUUID(),"farmer").level();
        int count=1;
        int luck=0;
        boolean seed=false;
        if(cn.yumis.fight.modules.professions.platform.ProfessionContent.MYTHIC_SICKLE_ID.equals(toolId)){
            count=cn.yumis.fight.modules.professions.platform.MythicSickleItem.HARVEST_COUNT;
            luck=cn.yumis.fight.modules.professions.platform.MythicSickleItem.LUCK_BONUS;
            seed=true;
        }else if(cn.yumis.fight.modules.professions.platform.ProfessionContent.EPIC_SICKLE_ID.equals(toolId)){
            count=cn.yumis.fight.modules.professions.platform.EpicSickleItem.HARVEST_COUNT;
            luck=cn.yumis.fight.modules.professions.platform.EpicSickleItem.LUCK_BONUS;
            seed=true;
        }else if(cn.yumis.fight.modules.professions.platform.ProfessionContent.LEGENDARY_SICKLE_ID.equals(toolId)){
            count=cn.yumis.fight.modules.professions.platform.LegendarySickleItem.HARVEST_COUNT;
            luck=cn.yumis.fight.modules.professions.platform.LegendarySickleItem.LUCK_BONUS;
            seed=true;
        }else if(cn.yumis.fight.modules.professions.platform.ProfessionContent.ARTIFACT_SICKLE_ID.equals(toolId)){
            count=cn.yumis.fight.modules.professions.platform.ArtifactSickleItem.HARVEST_COUNT;
            luck=cn.yumis.fight.modules.professions.platform.ArtifactSickleItem.LUCK_BONUS;
            seed=true;
        }else if(cn.yumis.fight.modules.professions.platform.ProfessionContent.RARE_SICKLE_ID.equals(toolId)){
            count=2;luck=cn.yumis.fight.modules.professions.platform.RareSickleItem.LUCK_BONUS;seed=true;
        }else if(cn.yumis.fight.modules.professions.platform.ProfessionContent.SICKLE_ID.equals(toolId)){
            seed=true;
        }
        if(harvested!=null&&harvested.getBlock() instanceof cn.yumis.fight.modules.professions.platform.NightmareVineBlock){
            level.addFreshEntity(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(cn.yumis.fight.modules.professions.api.HerbSpecies.NIGHTMARE_VINE,1)));
            service.get().award(p.getUUID(),"farmer",cn.yumis.fight.modules.professions.api.HerbRules.experience(cn.yumis.fight.modules.professions.api.HerbTier.ARTIFACT),operation+"/"+UUID.randomUUID(),false);
            if(seed)level.addFreshEntity(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(cn.yumis.fight.modules.professions.platform.HerbContent.SEED_ID,1)));
            return;
        }
        if(harvested!=null&&harvested.getBlock() instanceof cn.yumis.fight.modules.professions.platform.StormVineBlock){
            level.addFreshEntity(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(cn.yumis.fight.modules.professions.api.HerbSpecies.STORMVINE,1)));
            service.get().award(p.getUUID(),"farmer",cn.yumis.fight.modules.professions.api.HerbRules.experience(cn.yumis.fight.modules.professions.api.HerbTier.MYTHIC),operation+"/"+UUID.randomUUID(),false);
            if(seed)level.addFreshEntity(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(cn.yumis.fight.modules.professions.platform.HerbContent.SEED_ID,1)));
            return;
        }
        if(harvested!=null&&harvested.getBlock() instanceof cn.yumis.fight.modules.professions.platform.OceanHerbBlock){
            level.addFreshEntity(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(oceanHerbId(level.getRandom()),1)));
            service.get().award(p.getUUID(),"farmer",cn.yumis.fight.modules.professions.api.HerbRules.experience(cn.yumis.fight.modules.professions.api.HerbTier.LEGENDARY),operation+"/"+UUID.randomUUID(),false);
            if(seed)level.addFreshEntity(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(cn.yumis.fight.modules.professions.platform.HerbContent.SEED_ID,1)));
            return;
        }
        for(int i=0;i<count;i++){
            var tier=cn.yumis.fight.modules.professions.api.HerbRules.roll(farmerLevel+luck,level.getRandom().nextDouble());
            String herbId=herbItemAt(level,pos,tier,level.getRandom());
            level.addFreshEntity(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(herbId,1)));
            service.get().award(p.getUUID(),"farmer",cn.yumis.fight.modules.professions.api.HerbRules.experience(tier),operation+"/"+UUID.randomUUID(),false);
        }
        if(seed)level.addFreshEntity(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(cn.yumis.fight.modules.professions.platform.HerbContent.SEED_ID,1)));
    }
    /** 海底草药只产海妖花或船锚珊瑚。 */
    private static String oceanHerbId(net.minecraft.util.RandomSource random){
        return random.nextBoolean()?cn.yumis.fight.modules.professions.api.HerbSpecies.OCEAN_LEGENDARY.get(0):cn.yumis.fight.modules.professions.api.HerbSpecies.OCEAN_LEGENDARY.get(1);
    }
    /** 野生草药丛/噩梦藤被破坏：产出同样按手持工具结算。 */
    private static void harvestHerbBush(BlockDropsEvent e,ServerPlayer p,java.util.function.Supplier<ProfessionService> service){
        ItemStack tool=e.getTool();
        String toolId=tool==null||tool.isEmpty()?"":id(tool);
        var level=e.getLevel();
        var pos=e.getPos();
        boolean vine=e.getState().getBlock() instanceof cn.yumis.fight.modules.professions.platform.NightmareVineBlock;
        int farmerLevel=service.get().get(p.getUUID(),"farmer").level();
        int count=1;
        int luck=0;
        boolean seed=false;
        if(cn.yumis.fight.modules.professions.platform.ProfessionContent.MYTHIC_SICKLE_ID.equals(toolId)){
            count=cn.yumis.fight.modules.professions.platform.MythicSickleItem.HARVEST_COUNT;
            luck=cn.yumis.fight.modules.professions.platform.MythicSickleItem.LUCK_BONUS;
            seed=true;
        }else if(cn.yumis.fight.modules.professions.platform.ProfessionContent.EPIC_SICKLE_ID.equals(toolId)){
            count=cn.yumis.fight.modules.professions.platform.EpicSickleItem.HARVEST_COUNT;
            luck=cn.yumis.fight.modules.professions.platform.EpicSickleItem.LUCK_BONUS;
            seed=true;
        }else if(cn.yumis.fight.modules.professions.platform.ProfessionContent.LEGENDARY_SICKLE_ID.equals(toolId)){
            count=cn.yumis.fight.modules.professions.platform.LegendarySickleItem.HARVEST_COUNT;
            luck=cn.yumis.fight.modules.professions.platform.LegendarySickleItem.LUCK_BONUS;
            seed=true;
        }else if(cn.yumis.fight.modules.professions.platform.ProfessionContent.ARTIFACT_SICKLE_ID.equals(toolId)){
            count=cn.yumis.fight.modules.professions.platform.ArtifactSickleItem.HARVEST_COUNT;
            luck=cn.yumis.fight.modules.professions.platform.ArtifactSickleItem.LUCK_BONUS;
            seed=true;
        }else if(cn.yumis.fight.modules.professions.platform.ProfessionContent.RARE_SICKLE_ID.equals(toolId)){
            count=2;luck=cn.yumis.fight.modules.professions.platform.RareSickleItem.LUCK_BONUS;seed=true;
        }else if(cn.yumis.fight.modules.professions.platform.ProfessionContent.SICKLE_ID.equals(toolId)){
            seed=true;
        }
        if(vine){
            e.getDrops().add(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(cn.yumis.fight.modules.professions.api.HerbSpecies.NIGHTMARE_VINE,1)));
            service.get().award(p.getUUID(),"farmer",cn.yumis.fight.modules.professions.api.HerbRules.experience(cn.yumis.fight.modules.professions.api.HerbTier.ARTIFACT),"herb_gather/"+UUID.randomUUID(),false);
            if(seed)e.getDrops().add(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(cn.yumis.fight.modules.professions.platform.HerbContent.SEED_ID,1)));
            return;
        }
        if(e.getState().getBlock() instanceof cn.yumis.fight.modules.professions.platform.StormVineBlock){
            e.getDrops().add(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(cn.yumis.fight.modules.professions.api.HerbSpecies.STORMVINE,1)));
            service.get().award(p.getUUID(),"farmer",cn.yumis.fight.modules.professions.api.HerbRules.experience(cn.yumis.fight.modules.professions.api.HerbTier.MYTHIC),"herb_gather/"+UUID.randomUUID(),false);
            if(seed)e.getDrops().add(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(cn.yumis.fight.modules.professions.platform.HerbContent.SEED_ID,1)));
            return;
        }
        if(e.getState().getBlock() instanceof cn.yumis.fight.modules.professions.platform.OceanHerbBlock){
            e.getDrops().add(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(oceanHerbId(level.getRandom()),1)));
            service.get().award(p.getUUID(),"farmer",cn.yumis.fight.modules.professions.api.HerbRules.experience(cn.yumis.fight.modules.professions.api.HerbTier.LEGENDARY),"herb_gather/"+UUID.randomUUID(),false);
            if(seed)e.getDrops().add(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(cn.yumis.fight.modules.professions.platform.HerbContent.SEED_ID,1)));
            return;
        }
        for(int i=0;i<count;i++){
            var tier=cn.yumis.fight.modules.professions.api.HerbRules.roll(farmerLevel+luck,level.getRandom().nextDouble());
            String herbId=herbItemAt(level,pos,tier,level.getRandom());
            e.getDrops().add(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(herbId,1)));
            service.get().award(p.getUUID(),"farmer",cn.yumis.fight.modules.professions.api.HerbRules.experience(tier),"herb_gather/"+UUID.randomUUID(),false);
        }
        if(seed)e.getDrops().add(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,cn.yumis.fight.platform.ItemRegistry.item(cn.yumis.fight.modules.professions.platform.HerbContent.SEED_ID,1)));
    }
    private static String id(ItemStack stack){
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }
    /** 0=没拿鱼竿；1=普通；2=好运；3=石墨；4=海潮；5=冥河幽光；6=远行者鱼友。 */
    private static int rodCatches(ServerPlayer player){
        int best=0;
        for(ItemStack stack:new ItemStack[]{player.getMainHandItem(),player.getOffhandItem()}){
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
    private static boolean holding(ServerPlayer player,String rodId){
        return rodId.equals(id(player.getMainHandItem()))||rodId.equals(id(player.getOffhandItem()));
    }
    /** 能出钻石的鱼竿：石墨/海潮/冥河幽光/远行者。 */
    private static boolean treasureRod(ServerPlayer player){
        return holding(player,cn.yumis.fight.modules.professions.platform.ProfessionContent.GRAPHITE_ROD_ID)
            ||holding(player,cn.yumis.fight.modules.professions.platform.ProfessionContent.TIDAL_ROD_ID)
            ||holding(player,cn.yumis.fight.modules.professions.platform.ProfessionContent.STYX_ROD_ID)
            ||holding(player,cn.yumis.fight.modules.professions.platform.ProfessionContent.FARER_ROD_ID);
    }
    private static boolean obsidianRod(ServerPlayer player){
        return holding(player,cn.yumis.fight.modules.professions.platform.ProfessionContent.TIDAL_ROD_ID)
            ||holding(player,cn.yumis.fight.modules.professions.platform.ProfessionContent.STYX_ROD_ID)
            ||holding(player,cn.yumis.fight.modules.professions.platform.ProfessionContent.FARER_ROD_ID);
    }
    private static boolean blazeRod(ServerPlayer player){
        return holding(player,cn.yumis.fight.modules.professions.platform.ProfessionContent.STYX_ROD_ID)
            ||holding(player,cn.yumis.fight.modules.professions.platform.ProfessionContent.FARER_ROD_ID);
    }
    private static boolean netherStarRod(ServerPlayer player){
        return holding(player,cn.yumis.fight.modules.professions.platform.ProfessionContent.FARER_ROD_ID);
    }
    private static boolean styxRod(ServerPlayer player){
        return holding(player,cn.yumis.fight.modules.professions.platform.ProfessionContent.STYX_ROD_ID);
    }
    private static boolean farerRod(ServerPlayer player){
        return holding(player,cn.yumis.fight.modules.professions.platform.ProfessionContent.FARER_ROD_ID);
    }
    private static boolean tidalRod(ServerPlayer player){
        return holding(player,cn.yumis.fight.modules.professions.platform.ProfessionContent.TIDAL_ROD_ID);
    }
    /** 钻石/黑曜石/烈焰棒/下界之星替换该次渔获；返回 true 表示已经给出宝物。 */
    private static boolean dropTreasure(ServerPlayer player,boolean obsidian,boolean blaze,boolean netherStar){
        if(FishingRules.isDiamond(player.getRandom().nextDouble())){
            player.drop(new ItemStack(net.minecraft.world.item.Items.DIAMOND,1),false);
            return true;
        }
        if(obsidian&&FishingRules.isObsidian(player.getRandom().nextDouble())){
            player.drop(new ItemStack(net.minecraft.world.item.Items.OBSIDIAN,1),false);
            return true;
        }
        if(blaze&&FishingRules.isBlazeRod(player.getRandom().nextDouble())){
            player.drop(new ItemStack(net.minecraft.world.item.Items.BLAZE_ROD,1),false);
            return true;
        }
        if(netherStar&&FishingRules.isNetherStar(player.getRandom().nextDouble())){
            player.drop(new ItemStack(net.minecraft.world.item.Items.NETHER_STAR,1),false);
            return true;
        }
        return false;
    }
    /** 传说看地形/高度，史诗看特殊水域/神殿/雪原，神话看虚空与珊瑚；其余档位只需要维度。 */
    private static cn.yumis.fight.modules.professions.api.FishSpecies.Context context(ServerPlayer player){
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
    private static boolean nearCoral(ServerPlayer player){
        var base=player.blockPosition();
        for(var pos:net.minecraft.core.BlockPos.betweenClosed(base.offset(-4,-2,-4),base.offset(4,2,4))){
            var block=player.level().getBlockState(pos).getBlock();
            if(BuiltInRegistries.BLOCK.getKey(block).getPath().contains("coral"))return true;
        }
        return false;
    }
    private static boolean blockIs(net.minecraft.server.level.ServerPlayer player,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.Block block){
        return player.level().getBlockState(pos).is(block);
    }
    private static boolean inGeode(ServerPlayer player){
        var base=player.blockPosition();
        for(var pos:net.minecraft.core.BlockPos.betweenClosed(base.offset(-3,-3,-3),base.offset(3,3,3))){
            if(blockIs(player,pos,net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK)||blockIs(player,pos,net.minecraft.world.level.block.Blocks.BUDDING_AMETHYST))return true;
        }
        return false;
    }
    /** 9×9 范围内灵魂沙达到一定数量，且玩家在水里。 */
    private static boolean nearSoulSandWater(ServerPlayer player){
        if(!player.isInWater())return false;
        var base=player.blockPosition();
        int count=0;
        for(var pos:net.minecraft.core.BlockPos.betweenClosed(base.offset(-4,-2,-4),base.offset(4,2,4))){
            if(blockIs(player,pos,net.minecraft.world.level.block.Blocks.SOUL_SAND)&&++count>=12)return true;
        }
        return false;
    }
    /** 海底神殿特征方块（海晶灯/暗海晶石/海晶石）。 */
    private static boolean nearMonument(ServerPlayer player){
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
    private static boolean inSnowyArea(ServerPlayer player){
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
    private static void award(java.util.function.Supplier<ProfessionService> service,ServerPlayer player,String profession,String operation){
        long xp=ProfessionCatalog.require(profession).operationExperience();
        service.get().award(player.getUUID(),profession,xp,operation,false);
    }
    /** 神话/史诗竿的水下移速：用属性修饰符，避开原版药水没有 30%/40% 档位的问题。 */
    private static void swimSpeed(ServerPlayer player,double amount){
        var attribute=player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if(attribute==null)return;
        if(amount>0)attribute.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(STYX_SWIM,amount,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else attribute.removeModifier(STYX_SWIM);
    }
    /** 等级被动：按概率把首条掉落复制 1 个出来，不取消原掉落、不改原数量。 */
    private static void bonusYield(BlockDropsEvent e,double chance){
        if(chance<=0||e.getDrops().isEmpty())return;
        if(e.getLevel().getRandom().nextDouble()>=chance)return;
        ItemStack source=e.getDrops().get(0).getItem();
        if(source.isEmpty())return;
        ItemStack extra=new ItemStack(source.getItem(),1);
        e.getDrops().add(new ItemEntity(e.getLevel(),e.getPos().getX()+0.5,e.getPos().getY()+0.5,e.getPos().getZ()+0.5,extra));
    }
    public static void register(java.util.function.BooleanSupplier ready,java.util.function.Supplier<ProfessionService> service){
        cn.yumis.fight.modules.professions.platform.ProfessionConsumable.LEVEL_SOURCE=player->service.get().get(player.getUUID(),"alchemist").level();
        cn.yumis.fight.modules.professions.platform.GraphiteRod.HANDLER=(player,rod)->{
            boolean nether=player.level().dimension().equals(net.minecraft.world.level.Level.NETHER);
            var context=cn.yumis.fight.modules.professions.api.FishSpecies.Context.plain(nether);
            boolean obsidian=obsidianRod(player);
            boolean blaze=blazeRod(player);
            boolean star=netherStarRod(player);
            int count=Math.max(1,rodCatches(player));
            for(int i=0;i<count;i++){
                service.get().award(player.getUUID(),"fisherman",FishingRules.experience(FishTier.ARTIFACT),"lava/"+UUID.randomUUID()+"#"+i,false);
                if(dropTreasure(player,obsidian,blaze,star))continue;
                String catchId=cn.yumis.fight.modules.professions.api.FishSpecies.itemFor(FishTier.ARTIFACT,player.getRandom().nextDouble(),context);
                player.drop(cn.yumis.fight.platform.ItemRegistry.item(catchId,1),false);
            }
            return true;
        };
        cn.yumis.fight.modules.professions.platform.FarerRod.HANDLER=(player,rod)->{
            var context=new cn.yumis.fight.modules.professions.api.FishSpecies.Context(false,false,false,false,false,false,true,false);
            int count=Math.max(1,rodCatches(player));
            for(int i=0;i<count;i++){
                service.get().award(player.getUUID(),"fisherman",FishingRules.experience(FishTier.MYTHIC),"void/"+UUID.randomUUID()+"#"+i,false);
                if(dropTreasure(player,true,true,true))continue;
                String catchId=cn.yumis.fight.modules.professions.api.FishSpecies.itemFor(FishTier.MYTHIC,player.getRandom().nextDouble(),context);
                player.drop(cn.yumis.fight.platform.ItemRegistry.item(catchId,1),false);
            }
            return true;
        };
        cn.yumis.fight.modules.professions.platform.SickleItem.HANDLER=(player,pos)->{
            var level=player.level();
            var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof cn.yumis.fight.modules.professions.platform.HerbBushBlock))return false;
            level.removeBlock(pos,false);
            harvestWith(level,pos,player,service,cn.yumis.fight.modules.professions.platform.ProfessionContent.SICKLE_ID,"sickle",state);
            return true;
        };
        cn.yumis.fight.modules.professions.platform.RareSickleItem.HANDLER=(player,pos)->{
            var level=player.level();
            var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof cn.yumis.fight.modules.professions.platform.HerbBushBlock))return false;
            level.removeBlock(pos,false);
            harvestWith(level,pos,player,service,cn.yumis.fight.modules.professions.platform.ProfessionContent.RARE_SICKLE_ID,"rare_sickle",state);
            return true;
        };
        cn.yumis.fight.modules.professions.platform.ArtifactSickleItem.HANDLER=(player,pos)->{
            var level=player.level();
            var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof cn.yumis.fight.modules.professions.platform.HerbBushBlock))return false;
            level.removeBlock(pos,false);
            harvestWith(level,pos,player,service,cn.yumis.fight.modules.professions.platform.ProfessionContent.ARTIFACT_SICKLE_ID,"artifact_sickle",state);
            return true;
        };
        cn.yumis.fight.modules.professions.platform.LegendarySickleItem.HANDLER=(player,pos)->{
            var level=player.level();
            var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof cn.yumis.fight.modules.professions.platform.HerbBushBlock))return false;
            level.removeBlock(pos,false);
            harvestWith(level,pos,player,service,cn.yumis.fight.modules.professions.platform.ProfessionContent.LEGENDARY_SICKLE_ID,"legendary_sickle",state);
            return true;
        };
        cn.yumis.fight.modules.professions.platform.EpicSickleItem.HANDLER=(player,pos)->{
            var level=player.level();
            var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof cn.yumis.fight.modules.professions.platform.HerbBushBlock))return false;
            level.removeBlock(pos,false);
            harvestWith(level,pos,player,service,cn.yumis.fight.modules.professions.platform.ProfessionContent.EPIC_SICKLE_ID,"epic_sickle",state);
            return true;
        };
        cn.yumis.fight.modules.professions.platform.MythicSickleItem.HANDLER=(player,pos)->{
            var level=player.level();
            var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof cn.yumis.fight.modules.professions.platform.HerbBushBlock))return false;
            level.removeBlock(pos,false);
            harvestWith(level,pos,player,service,cn.yumis.fight.modules.professions.platform.ProfessionContent.MYTHIC_SICKLE_ID,"mythic_sickle",state);
            return true;
        };
        NeoForge.EVENT_BUS.addListener((BlockDropsEvent e)->{
            if(!ready.getAsBoolean()||e.isCanceled()||!(e.getBreaker() instanceof ServerPlayer p))return;
            BlockState state=e.getState();
            if(mature(state)){
                if(!e.getDrops().isEmpty()){
                    award(service,p,"farmer","harvest/"+UUID.randomUUID());
                    bonusYield(e,ProfessionBonuses.extraYieldChance(service.get().get(p.getUUID(),"farmer").level()));
                }
                return;
            }
            if(ore(state)){
                if(!e.getDrops().isEmpty()){
                    award(service,p,"miner","mining/"+UUID.randomUUID());
                    bonusYield(e,ProfessionBonuses.extraYieldChance(service.get().get(p.getUUID(),"miner").level()));
                }
                return;
            }
            if(state.getBlock() instanceof cn.yumis.fight.modules.professions.platform.HerbBushBlock){
                harvestHerbBush(e,p,service);
                return;
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((ItemFishedEvent e)->{
            if(!ready.getAsBoolean()||e.isCanceled()||!(e.getEntity() instanceof ServerPlayer p)||e.getDrops().isEmpty())return;
            int level=service.get().get(p.getUUID(),"fisherman").level();
            int catches=rodCatches(p);
            if(catches>0){
                boolean nether=p.level().dimension().equals(net.minecraft.world.level.Level.NETHER);
                boolean treasure=treasureRod(p);
                boolean obsidian=obsidianRod(p);
                boolean blaze=blazeRod(p);
                boolean star=netherStarRod(p);
                for(int i=0;i<catches;i++){
                    FishTier tier=FishingRules.roll(level,p.getRandom().nextDouble());
                    var context=(tier==FishTier.LEGENDARY||tier==FishTier.EPIC||tier==FishTier.MYTHIC)
                        ?context(p)
                        :cn.yumis.fight.modules.professions.api.FishSpecies.Context.plain(nether);
                    if(tier==FishTier.EPIC&&!context.epicAvailable())tier=FishTier.LEGENDARY;
                    service.get().award(p.getUUID(),"fisherman",FishingRules.experience(tier),"fishing/"+e.getHookEntity().getUUID()+"#"+i,false);
                    if(treasure&&dropTreasure(p,obsidian,blaze,star))continue;
                    String catchId=cn.yumis.fight.modules.professions.api.FishSpecies.itemFor(tier,p.getRandom().nextDouble(),context);
                    p.drop(cn.yumis.fight.platform.ItemRegistry.item(catchId,1),false);
                }
                return;
            }
            award(service,p,"fisherman","fishing/"+e.getHookEntity().getUUID());
            if(p.getRandom().nextDouble()<ProfessionBonuses.fishingLuck(level)){
                String catchId=p.getRandom().nextDouble()<0.2?"catch_pearl":"catch_riverfish";
                p.drop(cn.yumis.fight.platform.ItemRegistry.item(catchId,1),false);
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e)->{
            if(!(e.getEntity() instanceof ServerPlayer p))return;
            cn.yumis.fight.modules.professions.platform.AlchemyPotions.expire(p,System.currentTimeMillis());
            boolean farer=farerRod(p);
            boolean styx=styxRod(p);
            boolean tidal=tidalRod(p);
            boolean floating=FLOATING.contains(p.getUUID());
            if(!farer&&!styx&&!tidal){
                if(floating){FLOATING.remove(p.getUUID());p.setNoGravity(false);}
                swimSpeed(p,0);
                return;
            }
            if(p.isInWater()){
                int amplifier=farer?4:styx?2:1;
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WATER_BREATHING,60,amplifier));
                if(farer)swimSpeed(p,0.40);
                else if(styx)swimSpeed(p,0.30);
                else{
                    swimSpeed(p,0);
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED,40,0));
                }
                var feet=p.blockPosition();
                boolean surface=p.level().getBlockState(feet).is(net.minecraft.world.level.block.Blocks.WATER)
                    &&!p.level().getBlockState(feet.above()).is(net.minecraft.world.level.block.Blocks.WATER);
                if(surface&&!p.isShiftKeyDown()){
                    p.setNoGravity(true);
                    var motion=p.getDeltaMovement();
                    p.setDeltaMovement(motion.x,Math.max(motion.y,0),motion.z);
                    FLOATING.add(p.getUUID());
                    return;
                }
            }else{
                swimSpeed(p,0);
            }
            if(floating){FLOATING.remove(p.getUUID());p.setNoGravity(false);}
        }
        );
        NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemCraftedEvent e)->{
            if(!ready.getAsBoolean()||!(e.getEntity() instanceof ServerPlayer p))return;
            ItemStack result=e.getCrafting();
            if(result==null||result.isEmpty())return;
            String profession=craftProfession(id(result));
            if(profession==null)return;
            award(service,p,profession,"craft/"+UUID.randomUUID());
            if("blacksmith".equals(profession)&&p.getRandom().nextDouble()<ProfessionBonuses.smithingRefundChance(service.get().get(p.getUUID(),"blacksmith").level())){
                var grid=e.getInventory();
                for(int i=0;i<grid.getContainerSize();i++){
                    ItemStack input=grid.getItem(i);
                    if(!input.isEmpty()){
                        p.drop(new ItemStack(input.getItem(),1),false);
                        break;
                    }
                }
            }else if(CRAFT_YIELD.contains(profession)&&p.getRandom().nextDouble()<ProfessionBonuses.craftYieldChance(service.get().get(p.getUUID(),profession).level())){
                p.drop(new ItemStack(result.getItem(),1),false);
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemSmeltedEvent e)->{
            if(!ready.getAsBoolean()||!(e.getEntity() instanceof ServerPlayer p))return;
            ItemStack result=e.getSmelting();
            if(result==null||result.isEmpty()||!COOKED.contains(id(result)))return;
            award(service,p,"cook","smelt/"+UUID.randomUUID());
            if(p.getRandom().nextDouble()<ProfessionBonuses.cookingYieldChance(service.get().get(p.getUUID(),"cook").level()))p.drop(new ItemStack(result.getItem(),1),false);
        }
        );
        NeoForge.EVENT_BUS.addListener((PlayerBrewedPotionEvent e)->{
            if(!ready.getAsBoolean()||!(e.getEntity() instanceof ServerPlayer p))return;
            if(e.getStack()==null||e.getStack().isEmpty())return;
            award(service,p,"alchemist","brew/"+UUID.randomUUID());
        }
        );
        NeoForge.EVENT_BUS.addListener((PlayerEnchantItemEvent e)->{
            if(!ready.getAsBoolean()||!(e.getEntity() instanceof ServerPlayer p))return;
            if(e.getEnchantedItem()==null||e.getEnchantedItem().isEmpty())return;
            award(service,p,"inscription","enchant/"+UUID.randomUUID());
        }
        );
    }
}
