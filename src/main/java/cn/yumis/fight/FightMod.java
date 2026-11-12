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
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.event.server.*;
import java.util.*;
@Mod(ItemData.NS)
public final class FightMod {
    public static final String ID=ItemData.NS;
    public FightMod(IEventBus bus,ModContainer container){
        cn.yumis.fight.modules.mapgeneration.api.MapGeneration.registerConfig(container);
        cn.yumis.fight.modules.inventory.api.WearablePolicy.install((player,group,stack)->{
            if(!EquipmentLayout.current().accepts(group,stack))return false;
            var gear=cn.yumis.fight.modules.equipment.api.EquipmentItems.view(stack);
            if(gear==null)return !cn.yumis.fight.modules.equipment.api.EquipmentItems.GEAR_IDS.contains(cn.yumis.fight.platform.ItemData.id(stack));
            return !player.level().isClientSide()||gear.valid(cn.yumis.fight.modules.equipment.api.EquipmentEligibility.PROGRESS.apply(player),cn.yumis.fight.modules.equipment.api.EquipmentEligibility.allowance(player));
        });
        cn.yumis.fight.modules.inventory.api.SlotAccess.currencyPolicy(stack->!stack.isEmpty()&&cn.yumis.fight.platform.ItemData.own(stack)&&cn.yumis.fight.modules.economy.api.CurrencyRules.find(cn.yumis.fight.platform.ItemData.id(stack))!=null);
        cn.yumis.fight.modules.equipment.platform.EquipmentContent.register();
        cn.yumis.fight.modules.professions.platform.ProfessionContent.register();
        cn.yumis.fight.modules.economy.platform.CurrencyContent.register();
        cn.yumis.fight.modules.economy.platform.ChestContent.register();
        cn.yumis.fight.modules.economy.platform.ShopContent.register(bus);
        cn.yumis.fight.modules.npc.api.NpcContentTypes.register(bus);
        cn.yumis.fight.modules.npc.api.NpcEntity.playerOwnedSources(e->e instanceof cn.yumis.fight.modules.skills.api.SummonedCreature summon&&summon.owner!=null);
        cn.yumis.fight.modules.economy.api.ItemRarity.external(cn.yumis.fight.bootstrap.IndustrialItemDisplay::rarity);
        cn.yumis.fight.modules.character.platform.CharacterContent.register();
        cn.yumis.fight.bootstrap.ApplicationModules.REGISTRY.order();
        cn.yumis.fight.modules.industry.platform.IndustrialContent.register(bus);
        bus.addListener(cn.yumis.fight.bootstrap.IndustrialItemDisplay::names);
        bus.addListener((net.neoforged.neoforge.event.ModifyDefaultComponentsEvent event)->{for(String name:cn.yumis.fight.modules.economy.api.ShopDefinitions.RARE_MATERIALS){var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(name));event.modify(item,(components,context,value)->components.set(net.minecraft.core.component.DataComponents.ITEM_NAME,cn.yumis.fight.modules.presentation.api.ModText.decorate(net.minecraft.network.chat.Component.translatable(value.getDescriptionId())).withColor(cn.yumis.fight.modules.equipment.api.Rarity.RARE.color())));}});
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
        cn.yumis.fight.modules.presentation.api.EffectParticleTypes.register(bus);
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
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e)->{
            if(e.getServer() instanceof net.minecraft.server.dedicated.DedicatedServer dedicated&&!dedicated.allowFlight())dedicated.setAllowFlight(true);
            ServerApplication.start(e.getServer());
            cn.yumis.fight.modules.mapgeneration.api.MapGeneration.started(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e)->{
            cn.yumis.fight.modules.mapgeneration.api.MapGeneration.stopped(e.getServer());
            ServerApplication.stop();
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event)->{if(event.getTarget() instanceof LivingEntity living&&cn.yumis.fight.modules.npc.api.NpcInteractionComponent.query(living)!=null){event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);event.setCanceled(true);}});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event)->{if(!event.getLevel().isClientSide()&&(event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile||event.getEntity() instanceof net.minecraft.world.entity.item.PrimedTnt||event.getEntity() instanceof net.minecraft.world.entity.AreaEffectCloud))cn.yumis.fight.modules.npc.api.NpcEntity.rememberPlayerSource(event.getEntity());});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable event)->{if(event.getEntity() instanceof cn.yumis.fight.modules.npc.api.NpcEntity&&cn.yumis.fight.modules.npc.api.NpcEntity.playerOwned(event.getEffectSource()))event.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);});
        ServerApplication.registerEvents();
    }
}
