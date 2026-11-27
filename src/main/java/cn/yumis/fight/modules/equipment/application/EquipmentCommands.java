package cn.yumis.fight.modules.equipment.application;

import cn.yumis.fight.modules.affixes.api.AffixDefinitions;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.equipment.api.CraftingEssences;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.equipment.api.EquipmentResolution;
import cn.yumis.fight.modules.equipment.api.EquipmentRules;
import cn.yumis.fight.modules.equipment.api.EquipmentTemplates;
import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import cn.yumis.fight.modules.presentation.api.ModText;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Admin commands owned by the equipment module.
 * `/ydnf craft <player> <essenceId> [percent]` force-writes a crafting essence affix onto the
 * target's held gear, bypassing the normal artifact/slot/family qualification for testing; all
 * essences are available, including drop-only ones. The percent argument is the roll percentile
 * used by essences whose affix has a value range; fixed-value essences ignore it.
 * `/ydnf equipment set <player> <level> <rarity> <affixes> <enhance> [weapon] [preferred] [maxRoll]`
 * grants one piece per slot, clamped to the slot minimum level; the first `preferred` affixes take
 * slot-appropriate meta picks and the rest roll normally.
 */
public final class EquipmentCommands {
    /** Bootstrap-supplied ports; the command only needs the inventory transaction port and a profile refresh. */
    public record Admin(InventoryOperations inventory,Consumer<ServerPlayer> profile,Function<ServerPlayer,Progress> progress){}
    private record Pref(Stat stat,String operation){}
    private EquipmentCommands(){}

    public static void commands(LiteralArgumentBuilder<CommandSourceStack> root,Supplier<Admin> admin){
        var percent=Commands.argument("percent",IntegerArgumentType.integer(0,100)).executes(c->craft(c,admin.get(),IntegerArgumentType.getInteger(c,"percent")));
        var essence=Commands.argument("essence",StringArgumentType.word())
            .suggests((context,builder)->{for(var d:CraftingEssences.all())builder.suggest(d.id());return builder.buildFuture();})
            .executes(c->craft(c,admin.get(),50))
            .then(percent);
        root.then(Commands.literal("craft")
            .requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.argument("player",EntityArgument.player()).then(essence)));
        var maxRoll=Commands.argument("maxRoll",BoolArgumentType.bool()).executes(c->gearSet(c,admin.get()));
        var preferred=Commands.argument("preferred",IntegerArgumentType.integer(0,6)).executes(c->gearSet(c,admin.get())).then(maxRoll);
        var weapon=Commands.argument("weapon",StringArgumentType.word())
            .suggests((context,builder)->{for(var kind:Weapon.values())builder.suggest(kind.name().toLowerCase(Locale.ROOT));return builder.buildFuture();})
            .executes(c->gearSet(c,admin.get())).then(preferred);
        var enhance=Commands.argument("enhance",IntegerArgumentType.integer(0,cn.yumis.fight.modules.equipment.api.EnhancementRules.MAXIMUM))
            .executes(c->gearSet(c,admin.get())).then(weapon);
        var affixes=Commands.argument("affixes",IntegerArgumentType.integer(0,6)).executes(c->gearSet(c,admin.get())).then(enhance);
        var rarity=Commands.argument("rarity",StringArgumentType.word())
            .suggests((context,builder)->{for(var value:Rarity.values())if(EquipmentTemplates.designed(value))builder.suggest(value.name().toLowerCase(Locale.ROOT));return builder.buildFuture();})
            .executes(c->gearSet(c,admin.get())).then(affixes);
        var level=Commands.argument("level",IntegerArgumentType.integer(1,80)).executes(c->gearSet(c,admin.get())).then(rarity);
        root.then(Commands.literal("set")
            .requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.argument("player",EntityArgument.player()).then(level)));
    }
    private static int gearSet(CommandContext<CommandSourceStack> context,Admin admin){
        try{
            ServerPlayer player=EntityArgument.getPlayer(context,"player");
            int level=IntegerArgumentType.getInteger(context,"level");
            Rarity rarity=rarity(StringArgumentType.getString(context,"rarity"));
            int affixes=IntegerArgumentType.getInteger(context,"affixes");
            int enhance=IntegerArgumentType.getInteger(context,"enhance");
            int preferred=Math.min(optional(context,"preferred",0),affixes);
            boolean maxRoll=optional(context,"maxRoll",false);
            String weaponId=optional(context,"weapon",null);
            Progress progress=admin.progress().apply(player);
            Weapon weapon=weaponId==null?jobWeapon(progress):Weapon.valueOf(weaponId.toUpperCase(Locale.ROOT));
            List<Gear> gears=generateSet(progress,weapon,level,rarity,affixes,enhance,preferred,maxRoll,new Random());
            List<ItemStack> stacks=new ArrayList<>();
            for(Gear gear:gears)stacks.add(EquipmentItems.gear(gear));
            JsonObject intent=new JsonObject();
            intent.addProperty("adminEquipmentSet",weapon.name());intent.addProperty("level",level);intent.addProperty("rarity",rarity.name());
            admin.inventory().transact(player,"admin-equipment-set/"+UUID.randomUUID(),intent,()->{
                for(ItemStack stack:stacks)admin.inventory().requireSpace(player,stack);
            },Map.of());
            admin.profile().accept(player);
            int count=stacks.size(),attempts=enhance;
            context.getSource().sendSuccess(()->ModText.of("已向 "+player.getName().getString()+" 发放 "+count+" 件"+rarity.title()+"装备（"+weapon.name()+"，强化 +"+attempts+"）"),true);
            return count;
        }catch(Exception failure){context.getSource().sendFailure(ModText.of(failure.getMessage()==null?failure.toString():failure.getMessage()));return 0;}
    }
    /** Generates one initialized piece per slot for admin delivery; slots below the requested level use their minimum. */
    public static List<Gear> generateSet(Progress progress,Weapon weapon,int level,Rarity rarity,int affixes,int enhance,int preferred,boolean maxRoll,Random random){
        int wanted=Math.clamp(preferred,0,affixes);
        List<Gear> gears=new ArrayList<>();
        for(Slot slot:Slot.values()){
            Gear gear=EquipmentTemplates.createStandard(slot,weapon,Rarity.COMMON,Math.max(level,slot.minimum()),random);
            gear.rarity=rarity;
            gear.source="admin/equipment_set";
            List<Pref> wants=new ArrayList<>(preferredAffixes(gear,progress));
            for(int i=0;i<affixes;i++){
                double roll=maxRoll?1.2d:.8+random.nextDouble()*.4;
                if(i<wanted){
                    boolean added=false;
                    for(int w=0;w<wants.size();w++){
                        Pref want=wants.get(w);
                        if(EquipmentRules.appendRolled(gear,random,d->d.stat()==want.stat()&&d.operation().equals(want.operation()),roll)){
                            wants.remove(w);
                            added=true;
                            break;
                        }
                    }
                    if(added)continue;
                }
                if(!EquipmentRules.appendRolled(gear,random,null,roll))throw new IllegalArgumentException("没有合格词条");
            }
            if(enhance>0){gear.enhanceLevel=enhance;gear.revision++;}
            gears.add(EquipmentResolution.initialize(gear));
        }
        return gears;
    }
    private static Rarity rarity(String name){
        Rarity rarity;
        try{rarity=Rarity.valueOf(name.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException unknown){throw new IllegalArgumentException("未知品质："+name);}
        if(!EquipmentTemplates.designed(rarity))throw new IllegalArgumentException("史诗、神话、太初请使用具名模板，不能批量生成");
        return rarity;
    }
    private static Weapon jobWeapon(Progress progress){
        var allowed=JobCatalog.weapons(progress.jobId());
        for(Weapon kind:Weapon.standard())if(allowed.contains(kind.name()))return kind;
        return Weapon.KATANA;
    }
    private static List<Pref> preferredAffixes(Gear gear,Progress progress){
        boolean magic=JobCatalog.style(progress.jobId()).magic();
        Stat attack=magic?Stat.MAGICAL_ATTACK:Stat.PHYSICAL_ATTACK,primary=magic?Stat.INTELLIGENCE:Stat.STRENGTH,secondary=magic?Stat.SPIRIT:Stat.VITALITY;
        List<Pref> prefs=new ArrayList<>();
        if(gear.slot==Slot.WEAPON){
            prefs.add(new Pref(attack,"PERCENT"));
            prefs.add(new Pref(Stat.ATTACK_SPEED,"FLAT"));
            prefs.add(new Pref(attack,"FLAT"));
            prefs.add(new Pref(Stat.LIGHT,"PERCENT"));
            prefs.add(new Pref(Stat.FIRE,"PERCENT"));
        }else if(gear.slot.armor()){
            prefs.add(new Pref(Stat.PHYSICAL_DEFENSE,"PERCENT"));
            prefs.add(new Pref(Stat.MAGICAL_DEFENSE,"PERCENT"));
            prefs.add(new Pref(Stat.SHIELD,"PERCENT"));
            prefs.add(new Pref(Stat.HEALTH,"FLAT"));
            prefs.add(new Pref(primary,"PERCENT"));
            prefs.add(new Pref(secondary,"PERCENT"));
        }else{
            prefs.add(new Pref(attack,"PERCENT"));
            prefs.add(new Pref(primary,"PERCENT"));
            prefs.add(new Pref(Stat.PHYSICAL_DEFENSE,"FLAT"));
            prefs.add(new Pref(Stat.MAGICAL_DEFENSE,"FLAT"));
            prefs.add(new Pref(Stat.LIGHT,"PERCENT"));
            prefs.add(new Pref(Stat.FIRE,"PERCENT"));
            prefs.add(new Pref(Stat.ICE,"PERCENT"));
            prefs.add(new Pref(Stat.DARK,"PERCENT"));
        }
        return prefs;
    }
    @SuppressWarnings("unchecked")
    private static <T> T optional(CommandContext<CommandSourceStack> context,String name,T fallback){
        try{return (T)context.getArgument(name,Object.class);}catch(IllegalArgumentException missing){return fallback;}
    }
    private static int craft(CommandContext<CommandSourceStack> context,Admin admin,int percent){
        try{
            ServerPlayer player=EntityArgument.getPlayer(context,"player");
            var essence=CraftingEssences.find(StringArgumentType.getString(context,"essence"));
            if(essence==null)throw new IllegalArgumentException("未知工艺，请用指令补全查看可用ID");
            int slot=player.getInventory().getSelectedSlot();
            if(EquipmentItems.gear(player.getInventory().getItem(slot))==null)throw new IllegalArgumentException("手持物品不是装备");
            JsonObject intent=new JsonObject();intent.addProperty("adminCraft",essence.id());intent.addProperty("percent",percent);
            admin.inventory().transact(player,"admin-craft/"+UUID.randomUUID(),intent,()->{
                ItemStack held=player.getInventory().getItem(slot);
                Gear gear=EquipmentItems.gear(held);
                if(gear==null)throw new IllegalArgumentException("手持物品不是装备");
                ItemStack updated=held.copy();
                EquipmentItems.write(updated,CraftingEssences.applyForced(gear,essence,percent/100d));
                player.getInventory().setItem(slot,updated);
            },Map.of());
            admin.profile().accept(player);
            var definition=AffixDefinitions.current().affixes.get(essence.affix());
            String note=definition!=null&&definition.minimum()==null?"（定值词条，分位不改变数值）":"（分位 "+percent+"%）";
            String name=essence.name(),target=player.getName().getString();
            context.getSource().sendSuccess(()->ModText.of("已向 "+target+" 的手持装备写入工艺："+name+note),true);
            return 1;
        }catch(Exception failure){context.getSource().sendFailure(ModText.of(failure.getMessage()));return 0;}
    }
}
