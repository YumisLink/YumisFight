package cn.yumis.fight.modules.equipment.client;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.ClientSettings;
import cn.yumis.fight.modules.inventory.api.SlotAccess;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.affixes.api.MagicEffects;
import cn.yumis.fight.modules.equipment.api.MaterialCatalog;
import cn.yumis.fight.modules.affixes.api.AffixText;
import cn.yumis.fight.modules.equipment.api.EquipmentAffixRanges;
import cn.yumis.fight.modules.equipment.api.EquipmentAffixes;
import cn.yumis.fight.modules.equipment.api.MageBlood;
import cn.yumis.fight.modules.equipment.api.EquipmentTemplates;
import cn.yumis.fight.modules.equipment.api.EquipmentDefinitions;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.JewelryType;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.equipment.api.WeaponTraits;
import cn.yumis.fight.modules.equipment.api.WeaponDisplayValues;

import cn.yumis.fight.modules.equipment.api.EquipmentRules;
import cn.yumis.fight.modules.equipment.api.EquipmentJson;

import cn.yumis.fight.modules.affixes.api.Affix;
import cn.yumis.fight.modules.equipment.api.Gem;
import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.attributes.api.Stats;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import java.util.*;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
public final class EquipmentTooltip {
    private record TooltipKey(String equipment,Object affixes,long affixRevision,Object definitions,long equipmentRevision,int weaponDisplayRevision,long favoritesRevision,boolean expanded,boolean valid,boolean advanced,String language,String fontId,String fontName,Object font){}
    private static final Map<TooltipKey,List<Component>> TOOLTIPS=new LinkedHashMap<>(32,.75f,true);
    private static long tooltipHits,tooltipBuilds;
    public record CacheMetrics(long hits,long builds,int entries){}
    public static CacheMetrics cacheMetrics(){return new CacheMetrics(tooltipHits,tooltipBuilds,TOOLTIPS.size());}
    private static java.util.function.Function<String,Component> currencyBadge=id->null;
    private static java.util.function.Function<String,List<String>> materialSources=id->List.of();
    private static java.util.function.BooleanSupplier extended=()->false;
    public static void install(java.util.function.Function<String,Component> currencies,java.util.function.Function<String,List<String>> sources,java.util.function.BooleanSupplier expanded){currencyBadge=currencies;materialSources=sources;extended=expanded;}

    private EquipmentTooltip(){
    }
    private static Component text(String s,int color){
        return UiFonts.decorate(Component.literal(s).withStyle(style->style.withColor(color).withItalic(false)));
    }
    private static void separator(List<Component> lines,String title){
        lines.add(text("──────── "+title+" ────────",0x78869c));
    }
    public static String affix(Affix a){
        return AffixText.affix(a);
    }
    public static List<Component> currencyLines(ItemStack stack){
        String id=ItemData.id(stack);
        String effect=switch(id){
            case "transmutation_stone"->"普通装备变为稀有，并新增1条随机词条";
            case "augmentation_stone"->"稀有装备变为神器，并新增1条随机词条";
            case "regal_stone"->"神器装备变为传说，并新增1条随机词条";
            case "exalted_stone"->"传说装备新增1条随机词条，最多6条";
            case "delezie_blood"->"不可逆腐化：加孔、等级−5～+5、刷新词条各自独立20%，可同时发生；之后仅可继续镶嵌宝石";
            case "divine_stone"->"重掷装备随机词条数值；保留类型、数量与元素";
            case "chaos_stone"->"随机删除1条可删除词条并替换为新词条；同种效果不重复，总条数不变";
            case "fracturing_orb"->"锁定传说装备的1条未锁定随机词条；可反复使用，逐条锁定";
            case "socket_stone"->"新增1个宝石孔；武器/防具最多2孔，其他部位1孔";
            case "upgrade_stone"->"普通至传说装备等级+5，最高80；基础及随机数值随等级成长";
            case "epic_upgrade_stone"->"史诗装备等级+5，最高80；只重算基础属性";
            case "mythic_upgrade_stone"->"神话装备等级+5，最高80；只重算基础属性";
            case "set_extraction_stone"->"提取套装：消耗整件装备及1枚石头，获得套装石";
            case "affix_extraction_stone"->"消耗皇冠／华丽的小丑帽及1枚石头，固定提炼1颗防具宝石；装备与其中宝石全部销毁";
            case "set_stone"->"赋予或替换装备套装；目标须满足来源等级和部位";
            case "affix_gem"->"镶嵌到宝石孔；覆盖会销毁原宝石";
            default->"";
        };
        if(effect.isEmpty())return List.of();
        List<Component> lines=new ArrayList<>();
        var badge=currencyBadge.apply(id);
        if(badge!=null)lines.add(badge);
        lines.add(text(effect,0xa6c3db));
        if(id.equals("divine_stone"))lines.add(text("各品质的明确随机区间均可重掷；基础、固定值、技能等级及宝石不重洗；锁定只防删除替换",0xffbc78));
        if(id.equals("chaos_stone"))lines.add(text("技能等级词条不可重掷、未锁定可替换；基础、固有效果、宝石及锁定词条受保护",0xffbc78));
        if(id.equals("fracturing_orb"))lines.add(text("锁定后不可删除替换；神圣石仍可重掷数值",0xffbc78));
        try{
            if(id.equals("set_stone")){
                var payload=com.google.gson.JsonParser.parseString(EquipmentItems.payload(stack)).getAsJsonObject();
                var set=EquipmentDefinitions.current().sets.get(payload.get("set").getAsString());
                lines.add(text("来源 Lv."+payload.get("level").getAsInt()+" · "+(payload.get("group").getAsString().equals("armor")?"防具":"首饰"),0xc5cfdd));
                if(set!=null){lines.add(text(set.name(),0xb1ef89));addSet(lines,set,false);}
            }
            if(id.equals("affix_gem")){
                Gem gem=EquipmentJson.JSON.fromJson(EquipmentItems.payload(stack),Gem.class);
                if(gem!=null){
                    lines.add(text("来源 Lv."+gem.level+" · "+groupName(gem.group),0xc5cfdd));
                    for(Affix a:gem.affixes)lines.add(cn.yumis.fight.modules.affixes.api.client.AffixFavorites.decorate(a,text(affix(a),0x85e4d6)));
                }
            }
        }catch(RuntimeException malformed){lines.add(text("道具数据无效，无法加工",0xff9696));}
        lines.add(text("拿起通货，右键目标装备使用1枚；失败不消耗",0x9aa9be));
        return lines;
    }
    private static String groupName(String group){
        if(group.equals("all"))return "所有部位";
        if(group.equals("armor"))return "防具";
        if(group.equals("jewelry"))return "首饰";
        try{return Slot.valueOf(group).title();}catch(IllegalArgumentException e){return group;}
    }
    public static String processingReason(ItemStack currency,Gear gear){
        try{
            String id=ItemData.id(currency);
            if(gear.corrupted&&!id.equals("affix_gem"))return "腐化装备仅允许继续镶嵌宝石";
            if(id.equals("affix_extraction_stone"))return cn.yumis.fight.modules.equipment.api.GemExtraction.available(gear)?"":"此装备未开放固定宝石提炼";
            if(id.equals("set_extraction_stone"))return gear.set.isEmpty()?"该装备没有套装":"";
            if(id.equals("set_stone")){
                var p=com.google.gson.JsonParser.parseString(EquipmentItems.payload(currency)).getAsJsonObject();
                boolean armor=p.get("group").getAsString().equals("armor");
                if(gear.level<p.get("level").getAsInt()||armor&&!gear.slot.armor()||!armor&&!gear.slot.jewelry())return "套装石部位或来源等级不符";
                var definition=EquipmentDefinitions.current().sets.get(p.get("set").getAsString());
                return definition!=null&&definition.group().equals(p.get("group").getAsString())?"":"套装定义或部位分类无效";
            }
            if(id.equals("affix_gem")){
                Gem gem=EquipmentJson.JSON.fromJson(EquipmentItems.payload(currency),Gem.class);
                if(gear.sockets<=0)return "装备尚未打孔";
                String last="宝石不适用于此装备";
                for(int i=0;i<gear.sockets;i++)try{EquipmentRules.socket(gear,gem,i);return "";}catch(RuntimeException failure){last=failure instanceof IllegalArgumentException&&failure.getMessage()!=null?failure.getMessage():"宝石数据无效";}
                return last;
            }
            EquipmentRules.process(gear,ItemData.id(currency),new Random(0));
            return "";
        }catch(RuntimeException e){return e instanceof IllegalArgumentException&&e.getMessage()!=null?e.getMessage():"道具数据无效，无法加工";}
    }
    private static boolean shift(){
        if(extended.getAsBoolean())return true;
        var window=Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_SHIFT)||InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
    private static void addAffix(List<Component> out,Affix a,Gear gear,boolean gem,int color){
        if(MageBlood.is(gear)&&(a.operation.equals("MAGIC")||a.operation.equals("MAGIC_AMPLIFICATION"))){out.add(text(MagicEffects.text(a),color));if(shift())out.add(text(a.operation.equals("MAGIC")?"  神圣石从14种魔法重抽；允许重复，同名只生效一次":"  神圣石可洗：25%～50%",0x9aa9be));return;}
        if(gem){a=EquipmentJson.copy(a,Affix.class);if(Set.of("FLAT","PERCENT","CHARACTER_PERCENT","ALL_RESIST_FLAT","EXTRA").contains(a.operation)){double factor=cn.yumis.fight.modules.equipment.api.EquipmentEffects.gemMultiplier(gear);a.value*=factor;a.maximum*=factor;}}
        String label=(gem?"  ":"")+affix(a)+(a.locked?" 〔已锁定·不可删除替换〕":"");
        if(!shift()){out.add(cn.yumis.fight.modules.affixes.api.client.AffixFavorites.decorate(a,text(label,color)));return;}
        var range=EquipmentAffixRanges.reroll(a,gear.rarity,gem);
        if(range==null){out.add(cn.yumis.fight.modules.affixes.api.client.AffixFavorites.decorate(a,text(label+" 〔不可重掷〕",color)));return;}
        String unit=EquipmentAffixRanges.percentage(a)?"%":"";
        out.add(cn.yumis.fight.modules.affixes.api.client.AffixFavorites.decorate(a,text(label,color)));
        if(range.interval())out.add(text("  可洗区间："+AffixText.integer(range.minimum())+"～"+AffixText.integer(range.upperMinimum())+" 至 "+AffixText.integer(range.maximum())+"～"+AffixText.integer(range.upperMaximum())+"（上下限同步变化）",0x9aa9be));
        else out.add(text("  可洗："+AffixText.integer(range.minimum())+unit+"～"+AffixText.integer(range.maximum())+unit,0x9aa9be));
    }

    private static void addSet(List<Component> out,EquipmentDefinitions.SetDefinition set,boolean range){
        for(var effect:set.effects()){
            String value=effect.stat()!=null?cn.yumis.fight.modules.attributes.api.client.AttributeLabels.statName(effect.stat())+" +"+AffixText.integer(effect.value()):switch(effect.modifier()){
                case "basicSkillMain"->"基础三技能主伤 +"+AffixText.integer(effect.value()*100)+"%";
                case "basicSkillCooldown"->"基础三技能冷却 −"+AffixText.integer(effect.value()*100)+"%";
                default->effect.modifier()+" +"+AffixText.integer(effect.value());
            };
            out.add(text(effect.pieces()+"件："+value,0xb1ef89));
        }
        if(range)out.add(text("  套装效果不可重掷",0x78869c));
    }
    private static void addWeaponAttributes(List<Component> out,Gear gear){
        separator(out,"武器属性（双攻与附加伤害）");
        var values=WeaponDisplayValues.project(gear);
        out.add(text("物理攻击 "+AffixText.signed(values.physicalAttack()),0xd9e4ed));
        out.add(text("魔法攻击 "+AffixText.signed(values.magicalAttack()),0xd9e4ed));
        for(var extra:values.extras())out.add(text("附加"+AffixText.integer(extra.minimum())+"～"+AffixText.integer(extra.maximum())+"点"+new String[]{"光","火","冰","暗"}[extra.element()]+"属性伤害",0x85e4d6));
    }
    public static void item(ItemTooltipEvent e){

        var stack=e.getItemStack();
        var view=EquipmentItems.view(stack);
        List<Component> out=e.getToolTip();
        if(ItemData.own(stack))out.replaceAll(UiFonts::decorate);
        if(cn.yumis.fight.modules.equipment.api.client.MythicDisplay.mythic(stack)&&!out.isEmpty())out.set(0,cn.yumis.fight.modules.equipment.api.client.MythicDisplay.mark(out.getFirst()));
        if(view==null){
            String id=ItemData.id(stack);
            if(id.equals("colorless_crystal")&&ItemData.own(stack)){out.add(text("拾取后存入无色小晶块余额，不占背包；余额在I菜单商城中查看",0xc5e6f5));return;}
            if(SlotAccess.isCurrency(stack)){var currencies=currencyLines(stack);if(!out.isEmpty()&&currencies.stream().anyMatch(line->cn.yumis.fight.modules.equipment.api.client.MythicDisplay.marked(line.getVisualOrderText())))out.set(0,cn.yumis.fight.modules.equipment.api.client.MythicDisplay.mark(out.getFirst()));out.addAll(currencies);return;}
            if(id.equals("crafting_material")){
                var m=MaterialCatalog.current().materials.get(EquipmentItems.material(stack));
                if(m!=null){
                    separator(out,"区域材料");
                    for(String source:materialSources.apply(m.id()))out.add(text(source,0xa6c3db));
                    out.add(text("制作专用 · 固定数量 · 堆叠上限64",0xc5cfdd));
                }
            }
            return;
        }
        var affixes=cn.yumis.fight.modules.affixes.api.AffixDefinitions.current();var definitions=EquipmentDefinitions.current();
        boolean valid=e.getEntity()==null||view.valid(cn.yumis.fight.modules.equipment.api.client.EquipmentClient.current().progress(),cn.yumis.fight.modules.equipment.api.EquipmentEligibility.allowance(e.getEntity()));
        TooltipKey key=new TooltipKey(EquipmentItems.serialized(stack),affixes,affixes.revision,definitions,definitions.revision,WeaponDisplayValues.REVISION,cn.yumis.fight.modules.affixes.api.client.AffixFavorites.revision(),shift(),valid,e.getFlags().isAdvanced(),Minecraft.getInstance().getLanguageManager().getSelected(),ClientSettings.uiFont,UiFonts.currentName(),UiFonts.font());
        List<Component> cached=TOOLTIPS.get(key);
        if(cached==null){Gear gear=EquipmentItems.gear(stack);if(gear==null)return;cached=gearLines(gear,valid,key.advanced());tooltipBuilds++;if(key.equipment().length()<=131072){TOOLTIPS.put(key,cached);while(TOOLTIPS.size()>256)TOOLTIPS.remove(TOOLTIPS.keySet().iterator().next());}}
        else tooltipHits++;
        // Other tooltip providers retain their original lines; only our appended section is cached.
        for(Component line:cached)out.add(line.copy());
    }
    private static List<Component> gearLines(Gear g,boolean valid,boolean advanced){
        List<Component> out=new ArrayList<>();
        // Keep vanilla enchantments, lore and component descriptions supplied by other mods.
        if(g.corrupted)out.add(text("腐化 · 禁止常规加工，仍可镶嵌、出售、整件分解",0xff7070));
        Component quality=text(g.rarity.title(),g.rarity.color());if(g.rarity==cn.yumis.fight.modules.equipment.api.Rarity.MYTHIC)quality=cn.yumis.fight.modules.equipment.api.client.MythicDisplay.mark(quality);
        out.add(Component.empty().append(quality).append(text(" · "+(g.slot==Slot.WEAPON?g.weapon.title():g.slot.armor()?EquipmentTemplates.typeTitle(g)+" · "+g.slot.title():g.slot.jewelry()?EquipmentTemplates.typeTitle(g)+" · "+g.slot.title():g.slot.title())+" · 需求 Lv."+g.level,g.rarity.color())));
        if(!valid)out.add(text("等级或职业条件不足，装备属性不生效",0xff7272));
        boolean weapon=g.slot==Slot.WEAPON;
        boolean baseValues=shift();
        if(weapon&&g.weapon==Weapon.STAFF)out.add(text("技能释放速度 −20% · 12格无元素魔法弹",0x9aa9be));
        if(weapon&&!baseValues)addWeaponAttributes(out,g);
        else{
            if(g.affixes.stream().anyMatch(a->a.base))separator(out,"基础属性");
            if(weapon){double speed=WeaponTraits.speed(g.weapon.name());if(speed!=0)out.add(text("武器基础攻击速度 "+AffixText.signed(speed)+"%",0x9aa9be));}
            boolean allResist=EquipmentTemplates.jewelry(g)==JewelryType.ALL_RESIST;
            for(Affix a:g.affixes)if(a.base){
                if(allResist&&a.stat.endsWith("_RESIST")){
                    if(a.stat.equals("LIGHT_RESIST")){Affix combined=EquipmentJson.copy(a,Affix.class);combined.operation=EquipmentAffixes.ALL_RESIST_FLAT;combined.stat="";addAffix(out,combined,g,false,0xd9e4ed);}
                }else addAffix(out,a,g,false,0xd9e4ed);
            }
        }
        List<Affix> extra=g.affixes.stream().filter(a->!a.base).toList();
        if(!extra.isEmpty()){
            separator(out,"装备词条");
            for(Affix a:extra)addAffix(out,a,g,false,a.locked?0xffce7d:a.operation.equals("SKILL_FAMILY")?0x95d8ff:a.fixed?0xffd080:0xc8a8f0);
        }
        if(MageBlood.is(g)){
            double q=MageBlood.multiplier(g);out.add(text("重复计数 "+MageBlood.duplicates(g)+" · 魔法效果 ×"+String.format(java.util.Locale.ROOT,"%.2f",q),0xffd080));
            var preview=new Stats();MageBlood.apply(g,preview);
            for(var entry:preview.attributes.entrySet()){
                var a=entry.getValue();String label;
                try{label=AffixText.statName(Stat.valueOf(entry.getKey()));}catch(IllegalArgumentException ignored){label=entry.getKey().equals("modifier/final")?"伤害":entry.getKey().equals("modifier/crit")?"暴击率":entry.getKey();}
                if(a.mut!=0)out.add(text("  生效："+label+"提高"+AffixText.integer(a.mut*100)+"%",0xb8dfda));
                if(a.add!=0)out.add(text("  生效："+label+" +"+AffixText.integer(a.add*(entry.getKey().equals("modifier/crit")?100:1))+(entry.getKey().equals("modifier/crit")?"%":""),0xb8dfda));
            }
            out.add(text("升级只提高等级，魔法数值不成长",0x9aa9be));
        }
        if(!g.set.isEmpty()){
            separator(out,"套装");
            var definition=EquipmentDefinitions.current().sets.get(g.set);
            out.add(text(definition==null?g.set:definition.name(),0xb1ef89));
            if(definition!=null)addSet(out,definition,shift());
        }
        if(g.sockets>0){
            separator(out,"宝石孔");
            for(int i=0;i<g.sockets;i++){
                if(i>=g.gems.size())out.add(text("◇ 空孔 "+(i+1),0x8c9db3));
                else{
                    Gem gem=g.gems.get(i);
                    out.add(text("◆ "+gem.name+" [Lv."+gem.level+"]",0x7ae7cf));
                    for(Affix a:gem.affixes)addAffix(out,a,g,true,0xb8dfda);
                }
            }
        }
        if(!baseValues)out.add(text(weapon?"按住 Shift 查看基础属性与可重掷范围":"按住 Shift 查看可重掷范围",0x78869c));
        if(advanced)out.add(text("ID "+g.instance+" / r"+g.revision,0x68748b));
        return List.copyOf(out);
    }
}
