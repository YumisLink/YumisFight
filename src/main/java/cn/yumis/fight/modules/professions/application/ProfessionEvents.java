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
            var context=cn.yumis.fight.modules.professions.api.FishSpecies.Context.plain(false);
            int count=Math.max(1,rodCatches(player));
            for(int i=0;i<count;i++){
                service.get().award(player.getUUID(),"fisherman",FishingRules.experience(FishTier.MYTHIC),"void/"+UUID.randomUUID()+"#"+i,false);
                if(dropTreasure(player,true,true,true))continue;
                String catchId=cn.yumis.fight.modules.professions.api.FishSpecies.itemFor(FishTier.MYTHIC,player.getRandom().nextDouble(),context);
                player.drop(cn.yumis.fight.platform.ItemRegistry.item(catchId,1),false);
            }
            return true;
        };
        NeoForge.EVENT_BUS.addListener((BlockDropsEvent e)->{
            if(ready.getAsBoolean()&&!e.isCanceled()&&e.getBreaker() instanceof ServerPlayer p&&!e.getDrops().isEmpty()){
                if(mature(e.getState())){
                    award(service,p,"farmer","harvest/"+UUID.randomUUID());
                    bonusYield(e,ProfessionBonuses.extraYieldChance(service.get().get(p.getUUID(),"farmer").level()));
                }else if(ore(e.getState())){
                    award(service,p,"miner","mining/"+UUID.randomUUID());
                    bonusYield(e,ProfessionBonuses.extraYieldChance(service.get().get(p.getUUID(),"miner").level()));
                }
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
