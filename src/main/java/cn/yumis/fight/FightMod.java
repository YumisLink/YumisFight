package cn.yumis.fight;
import cn.yumis.fight.bootstrap.CreativeCatalog;
import cn.yumis.fight.modules.equipment.api.EquipmentLayout;
import cn.yumis.fight.platform.ItemRegistry;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.industry.platform.IndustrialWorld;
import cn.yumis.fight.modules.industry.platform.IndustrialContent;

import cn.yumis.fight.network.Network;
import cn.yumis.fight.bootstrap.ServerApplication;
import net.minecraft.resources.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.effect.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.event.server.*;
import java.util.*;
@Mod(ItemData.NS)
public final class FightMod {
    public static final String ID=ItemData.NS;
    public FightMod(IEventBus bus){
        cn.yumis.fight.modules.inventory.api.WearablePolicy.install((player,group,stack)->{
            if(!EquipmentLayout.current().accepts(group,stack))return false;
            var gear=cn.yumis.fight.modules.equipment.api.EquipmentItems.view(stack);
            if(gear==null)return !cn.yumis.fight.modules.equipment.api.EquipmentItems.GEAR_IDS.contains(cn.yumis.fight.platform.ItemData.id(stack));
            return gear.valid(cn.yumis.fight.modules.equipment.api.EquipmentEligibility.PROGRESS.apply(player),cn.yumis.fight.modules.equipment.api.EquipmentEligibility.allowance(player));
        });
        cn.yumis.fight.modules.inventory.api.SlotAccess.currencyPolicy(stack->!stack.isEmpty()&&cn.yumis.fight.platform.ItemData.own(stack)&&cn.yumis.fight.modules.economy.api.CurrencyRules.find(cn.yumis.fight.platform.ItemData.id(stack))!=null);
        cn.yumis.fight.modules.equipment.platform.EquipmentContent.register();
        cn.yumis.fight.modules.economy.platform.CurrencyContent.register();
        cn.yumis.fight.modules.character.platform.CharacterContent.register();
        cn.yumis.fight.bootstrap.ApplicationModules.REGISTRY.order();
        cn.yumis.fight.modules.industry.platform.IndustrialContent.register(bus);
        cn.yumis.fight.modules.inventory.api.InventoryMenuPorts.install(
            ()->cn.yumis.fight.modules.industry.platform.IndustrialContent.STORAGE_MENU.get(),
            (player,tier)->{
                var recipe=player.level().recipeAccess().recipeMap().byType(cn.yumis.fight.modules.industry.platform.IndustrialContent.RECIPE_TYPE.get()).stream().map(h->h.value()).filter(r->r.machine().equals("storage_upgrade_"+tier)).findFirst().orElse(null);
                if(recipe==null)return null;
                String requirements=recipe.ingredients().stream().map(n->n.count()+" × "+n.ingredient().items().findFirst().map(h->new net.minecraft.world.item.ItemStack(h.value()).getHoverName().getString()).orElse("缺少兼容材料")).reduce((a,b)->a+"；"+b).orElse("无需材料");
                return new cn.yumis.fight.modules.inventory.api.InventoryMenuPorts.Upgrade(recipe::takePlan,requirements);
            },
            (player,action)->cn.yumis.fight.modules.industry.platform.IndustrialWorld.get(player.level()).defer(action),
            (player,open)->ServerApplication.get().inventory.bag(player,open));

        cn.yumis.fight.modules.equipment.api.EquipmentContentTypes.register(bus);
        cn.yumis.fight.modules.inventory.api.InventoryContentTypes.register(bus);
        cn.yumis.fight.modules.dungeons.api.DungeonContentTypes.register(bus);
        cn.yumis.fight.modules.economy.api.RewardContentTypes.register(bus);
        cn.yumis.fight.modules.combat.api.CombatContentTypes.register(bus);
        cn.yumis.fight.modules.skills.api.SkillContentTypes.register(bus);
        ItemRegistry.ITEMS.register(bus);
        CreativeCatalog.TABS.register(bus);
        bus.addListener(Network::register);
        try{
            var f=RangedAttribute.class.getDeclaredField("maxValue");
            f.setAccessible(true);
            f.setDouble(Attributes.MAX_HEALTH.value(),1_000_000);
        }
        catch(Exception e){
            throw new IllegalStateException("无法扩大生命属性容量",e);
        }
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e)->ServerApplication.start(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e)->ServerApplication.stop());
        ServerApplication.registerEvents();
    }
}
