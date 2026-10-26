package cn.yumis.fight.game;
import static cn.yumis.fight.rules.Rules.*;
import static cn.yumis.fight.rules.Models.*;
import cn.yumis.fight.rules.EquipmentRules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import java.util.*;
public final class NativeRules {
    private NativeRules(){
    }
    public static int level(ItemStack stack){
        String id=GameItems.id(stack);
        if(id.contains("netherite"))return 15;
        if(id.contains("diamond"))return 10;
        if(id.contains("golden")||id.contains("turtle")||id.equals("crossbow"))return 5;
        if(id.equals("trident"))return 15;
        if(id.equals("mace"))return 20;
        return 1;
    }
    public static boolean weapon(ItemStack stack){
        String id=GameItems.id(stack);
        return id.endsWith("_sword")||id.endsWith("_axe")||id.endsWith("_pickaxe")||id.endsWith("_shovel")||id.endsWith("_hoe")||id.endsWith("_spear")||Set.of("bow","crossbow","trident","mace").contains(id);
    }
    public static int enchantment(ItemStack stack,String name){
        var enchantments=stack.get(DataComponents.ENCHANTMENTS);
        if(enchantments!=null)for(var entry:enchantments.entrySet())if(entry.getKey().unwrapKey().map(k->k.identifier().getPath()).orElse("").equals(name))return entry.getIntValue();
        return 0;
    }
    private static double scale(Player player,ItemStack stack){
        String id=GameItems.id(stack);
        if(id.equals("mace"))return 1.2;
        if(id.equals("crossbow")||id.equals("trident"))return 1;
        if(id.equals("bow")||id.endsWith("_sword")||id.endsWith("_spear"))return .8;
        double reference=id.contains("netherite")?8:id.contains("diamond")?7:id.contains("iron")?6:id.contains("stone")||id.contains("copper")?5:4;
        return .8*Math.max(1,player.getAttributeValue(Attributes.ATTACK_DAMAGE))/reference;
    }
    public static void addEquipment(Player player,Stats stats,Progress progress){
        addEquipment(player,stats,progress,player.getMainHandItem());
    }
    public static void addEquipment(Player player,Stats stats,Progress progress,ItemStack weapon){
        for(EquipmentSlot part:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){
            ItemStack stack=player.getItemBySlot(part);
            if(stack.isEmpty()||GameItems.gear(stack)!=null||level(stack)>progress.level)continue;
            String id=GameItems.id(stack);
            if(id.equals("elytra"))continue;
            if(!stack.has(DataComponents.EQUIPPABLE))continue;
            Slot slot=part==EquipmentSlot.HEAD?Slot.HEAD:part==EquipmentSlot.CHEST?Slot.CHEST:part==EquipmentSlot.LEGS?Slot.LEGS:Slot.FEET;
            Gear white=EquipmentRules.create(slot,Weapon.KATANA,Rarity.COMMON,level(stack),new Random(0));
            double ratio=id.contains("leather")?.5:id.contains("copper")?.75:id.contains("chainmail")?.9:1;
            EquipmentRules.contribution(white).forEach((stat,value)->stats.add(stat,value*ratio));
        }
        ItemStack hand=weapon;
        if(weapon(hand)&&GameItems.gear(hand)==null&&level(hand)<=progress.level){
            double value=(15+4*(level(hand)-1))*scale(player,hand);
            stats.add(Stat.PHYSICAL_ATTACK,value);
            stats.add(Stat.MAGICAL_ATTACK,value);
            int sharp=enchantment(hand,"sharpness"),power=Set.of("bow","crossbow").contains(GameItems.id(hand))?enchantment(hand,"power"):0;
            stats.add(Stat.PHYSICAL_ATTACK,5*(sharp+power));
            stats.modifiers.put("piercing",enchantment(hand,"piercing")*.15);
        }
        var strength=player.getEffect(MobEffects.STRENGTH);
        if(strength!=null)stats.add(Stat.PHYSICAL_ATTACK,15*(strength.getAmplifier()+1));
        var weakness=player.getEffect(MobEffects.WEAKNESS);
        if(weakness!=null)stats.add(Stat.PHYSICAL_ATTACK,-20*(weakness.getAmplifier()+1));
        stats.values.computeIfPresent(Stat.PHYSICAL_ATTACK,(k,v)->Math.max(0,v));
    }
    public static double receivedMultiplier(Player player,String source,boolean projectile,boolean bypassEnchantments,boolean bypassResistance){
        double result=1;
        int points=0;
        if(!bypassEnchantments)for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){
            ItemStack item=player.getItemBySlot(slot);
            points+=enchantment(item,"protection");
            if(projectile)points+=2*enchantment(item,"projectile_protection");
            if(source.equals("fall")||source.equals("stalagmite"))points+=3*enchantment(item,"feather_falling");
            if(Set.of("explosion","playerExplosion","badRespawnPoint","fireworks").contains(source))points+=2*enchantment(item,"blast_protection");
            if(Set.of("lava","inFire","onFire","hotFloor").contains(source))points+=2*enchantment(item,"fire_protection");
        }
        result*=1-.04*Math.clamp(points,0,20);
        var resistance=player.getEffect(MobEffects.RESISTANCE);
        if(!bypassResistance&&resistance!=null)result*=Math.max(0,1-.2*(resistance.getAmplifier()+1));
        return result;
    }
}
