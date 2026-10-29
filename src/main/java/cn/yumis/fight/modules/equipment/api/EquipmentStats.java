package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.inventory.api.MenuHooks;
import cn.yumis.fight.modules.inventory.api.ExtraInventory;

import cn.yumis.fight.platform.ItemData;

import cn.yumis.fight.modules.attributes.api.AttributeSources;
import cn.yumis.fight.modules.attributes.api.Stat;

import cn.yumis.fight.modules.attributes.api.AttributeServices;

import cn.yumis.fight.modules.character.api.Progress;

import cn.yumis.fight.modules.attributes.api.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponentPatch;
import cn.yumis.fight.modules.affixes.api.AffixDefinitions;
import java.util.*;
/** The same aggregation is used by the local calculator and the server's display projection. */
public final class EquipmentStats {
    private static final net.minecraft.resources.Identifier SPEED_ID=net.minecraft.resources.Identifier.fromNamespaceAndPath(ItemData.NS,"equipment_move_speed");
    private static final WeakIdentityCache<Player,PlayerCache> CACHED=new WeakIdentityCache<>(256);
    private static long lookups,hits,builds,revisions;
    private static final class PlayerCache {final LinkedHashMap<Key,Entry> entries=new LinkedHashMap<>(4,.75f,true);Key active;long revision;}
    private record ItemKey(net.minecraft.world.item.Item item,DataComponentPatch components,int count,boolean wearable){}
    private record ProgressKey(int level,int strength,int intelligence,int spirit,String job,Map<String,Integer> skills){}
    private record Key(ProgressKey progress,List<ItemKey> items,List<String> groups,double nativeAttack,int strength,int weakness,int allowance,AttributeSources sources,long sourceRevision,AffixDefinitions affixes,long affixRevision,EquipmentDefinitions definitions,long equipmentRevision,EquipmentLayout layout){}
    private record Entry(Stats stats,EquipmentEffects.Snapshot effects){}
    public record CacheMetrics(long lookups,long hits,long builds,int players,int entries){}
    public static synchronized CacheMetrics cacheMetrics(){return new CacheMetrics(lookups,hits,builds,CACHED.size(),CACHED.values().stream().mapToInt(cache->cache.entries.size()).sum());}
    /** Call after cachedBuild; changes when its complete dependency key changes, including a cached old variant. */
    public static synchronized long cacheRevision(Player player){var cache=CACHED.get(player);return cache==null?0:cache.revision;}
    public static synchronized void invalidate(Player player){CACHED.remove(player);}
    public static synchronized void clearCache(){CACHED.clear();}
    private EquipmentStats(){
    }
    public static void applyMovement(Player player,Stats stats){
        var attribute=player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(attribute==null)return;
        double amount=player.isInWater()||player.getAbilities().flying||player.isFallFlying()?0:Math.max(0,stats.get(Stat.MOVEMENT_SPEED))-1;
        var old=attribute.getModifier(SPEED_ID);
        if(amount==0){if(old!=null)attribute.removeModifier(SPEED_ID);}
        else if(old==null||old.amount()!=amount)attribute.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SPEED_ID,amount,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    public static Stats build(Player player,Progress progress){
        return build(player,progress,AttributeServices.attributes(player.getUUID()));
    }
    public static Stats build(Player player,Progress progress,cn.yumis.fight.modules.attributes.api.AttributeSources sources){
        return build(player,progress,sources,player.getMainHandItem());
    }
    public static Stats build(Player player,Progress progress,cn.yumis.fight.modules.attributes.api.AttributeSources sources,net.minecraft.world.item.ItemStack weapon){
        return cachedBuild(player,progress,sources,weapon);
    }
    public static Stats cachedBuild(Player player,Progress progress,AttributeSources sources){return cachedBuild(player,progress,sources,player.getMainHandItem());}
    public static Stats cachedBuild(Player player,Progress progress,AttributeSources sources,ItemStack weapon){return cached(player,progress,sources,weapon).stats.copy();}
    public static EquipmentEffects.Snapshot cachedEffects(Player player,Progress progress){return cachedEffects(player,progress,AttributeServices.attributes(player.getUUID()));}
    public static EquipmentEffects.Snapshot cachedEffects(Player player,Progress progress,AttributeSources sources){return cached(player,progress,sources,player.getMainHandItem()).effects.copy();}
    public static EquipmentEffects.Snapshot projectileEffects(Player player,Progress progress,ItemStack weapon){return cached(player,progress,AttributeServices.attributes(player.getUUID()),weapon).effects.copy();}
    private static ItemKey itemKey(ItemStack item,boolean wearable){return new ItemKey(item.getItem(),item.getComponentsPatch(),item.getCount(),wearable);}
    private static int amplifier(Player player,net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect){var current=player.getEffect(effect);return current==null?-1:current.getAmplifier();}
    private static Key key(Player player,Progress progress,AttributeSources sources,ItemStack weapon){
        List<ItemKey> items=new ArrayList<>();items.add(itemKey(weapon,true));
        for(EquipmentSlot part:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))items.add(itemKey(player.getItemBySlot(part),true));
        var extra=MenuHooks.extra(player);for(int i=0;i<extra.getContainerSize();i++){var item=extra.getItem(i);items.add(itemKey(item,!item.isEmpty()&&extra.canPlaceItem(i,item)));}
        var affixes=AffixDefinitions.current();var definitions=EquipmentDefinitions.current();
        return new Key(new ProgressKey(progress.level,progress.strength,progress.intelligence,progress.spirit,progress.jobId(),Map.copyOf(progress.skills)),List.copyOf(items),List.copyOf(extra.groups),player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE),amplifier(player,net.minecraft.world.effect.MobEffects.STRENGTH),amplifier(player,net.minecraft.world.effect.MobEffects.WEAKNESS),EquipmentEligibility.allowance(player),sources,sources.revision(),affixes,affixes.revision,definitions,definitions.revision,EquipmentLayout.current());
    }
    private static synchronized Entry cached(Player player,Progress progress,AttributeSources sources,ItemStack weapon){
        Key key=key(player,progress,sources,weapon);lookups++;
        var cache=CACHED.computeIfAbsent(player,ignored->new PlayerCache());var values=cache.entries;
        if(!key.equals(cache.active)){cache.active=key;cache.revision=++revisions;}
        Entry entry=values.get(key);if(entry!=null){hits++;return entry;}
        List<Gear> gear=collect(player,weapon);
        ExtraInventory extra=MenuHooks.extra(player);
        Stats result=cn.yumis.fight.modules.equipment.api.EquipmentAttributes.stats(progress,gear,sources,key.allowance,stats->{
            NativeEquipmentRules.addEquipment(player,stats,progress,weapon);EquipmentLayout.current().attributes(extra,stats);
        }
        );
        entry=new Entry(result,EquipmentEffects.resolve(progress,gear,key.allowance));values.put(key,entry);builds++;
        while(values.size()>4)values.remove(values.keySet().iterator().next());
        return entry;
    }
    public static EquipmentEffects.Snapshot effects(Player player,Progress progress){return cachedEffects(player,progress);}
    public static List<Gear> collect(Player player,net.minecraft.world.item.ItemStack weapon){
        List<Gear> gear=new ArrayList<>();
        Gear hand=EquipmentItems.gear(weapon);
        if(hand!=null&&hand.slot==Slot.WEAPON)gear.add(hand);
        for(EquipmentSlot part:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){
            Gear g=EquipmentItems.gear(player.getItemBySlot(part));
            Slot expected=part==EquipmentSlot.HEAD?Slot.HEAD:part==EquipmentSlot.CHEST?Slot.CHEST:part==EquipmentSlot.LEGS?Slot.LEGS:Slot.FEET;
            if(g!=null&&g.slot==expected)gear.add(g);
        }
        ExtraInventory extra=MenuHooks.extra(player);
        for(int i=0;i<extra.getContainerSize();i++){
            Gear g=EquipmentItems.gear(extra.getItem(i));
            if(g!=null&&extra.canPlaceItem(i,extra.getItem(i)))gear.add(g);
        }
        return gear;
    }

}
