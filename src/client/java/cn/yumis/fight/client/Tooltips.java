package cn.yumis.fight.client;
import cn.yumis.fight.game.*;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import java.util.*;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
public final class Tooltips {
    private Tooltips(){
    }
    private static Component text(String s,int color){
        return UiFonts.decorate(Component.literal(s).withStyle(style->style.withColor(color).withItalic(false)));
    }
    private static void separator(List<Component> lines,String title){
        lines.add(text("──────── "+title+" ────────",0x78869c));
    }
    public static String affix(Affix a){
        if(a.operation.equals("SKILL_FAMILY"))return "所有"+Family.valueOf(a.family).title()+"系技能 +"+a.rank+"级（固定）";
        if(a.operation.equals("EXTRA"))return new String[]{
            "光","火","冰","暗"
        }
        [a.element]+"属性附伤 "+FightScreen.number(a.value)+"～"+FightScreen.number(a.maximum);
        if(a.stat.isEmpty())return a.id;
        return (a.operation.equals("PERCENT")?"本装备 ":"")+FightScreen.statName(Stat.valueOf(a.stat))+" +"+FightScreen.number(a.value*(a.operation.equals("PERCENT")?100:1))+(a.operation.equals("PERCENT")?"%":"");
    }
    public static List<Component> currencyLines(ItemStack stack){
        String id=GameItems.id(stack);
        String effect=switch(id){
            case "transmutation_stone"->"普通装备变为稀有，并新增1条随机词条";
            case "augmentation_stone"->"稀有装备变为神器，并新增1条随机词条";
            case "regal_stone"->"神器装备变为传说，并新增1条随机词条";
            case "exalted_stone"->"传说装备新增1条随机词条，最多6条";
            case "divine_stone"->"重掷装备随机词条数值；保留类型、数量与元素";
            case "socket_stone"->"新增1个宝石孔；武器/防具最多2孔，其他部位1孔";
            case "epic_upgrade_stone"->"史诗装备等级+5，最高80；只重算基础属性";
            case "mythic_upgrade_stone"->"神话装备等级+5，最高80；只重算基础属性";
            case "set_extraction_stone"->"提取套装：消耗整件装备及1枚石头，获得套装石";
            case "affix_extraction_stone"->"词条拆解尚未开放，使用不会消耗";
            case "set_stone"->"赋予或替换装备套装；目标须满足来源等级和部位";
            case "affix_gem"->"镶嵌到宝石孔；覆盖会销毁原宝石";
            default->"";
        };
        if(effect.isEmpty())return List.of();
        List<Component> lines=new ArrayList<>();
        lines.add(text(effect,0xa6c3db));
        if(id.equals("divine_stone"))lines.add(text("基础、固定、宝石词条不可重掷；史诗/神话不可用",0xffbc78));
        try{
            if(id.equals("set_stone")){
                var payload=com.google.gson.JsonParser.parseString(GameItems.payload(stack)).getAsJsonObject();
                var set=Definitions.CURRENT.sets.get(payload.get("set").getAsString());
                lines.add(text("来源 Lv."+payload.get("level").getAsInt()+" · "+(payload.get("group").getAsString().equals("armor")?"防具":"首饰"),0xc5cfdd));
                if(set!=null){lines.add(text(set.name(),0xb1ef89));addSet(lines,set,false);}
            }
            if(id.equals("affix_gem")){
                Gem gem=Models.JSON.fromJson(GameItems.payload(stack),Gem.class);
                if(gem!=null){
                    lines.add(text("来源 Lv."+gem.level+" · "+groupName(gem.group),0xc5cfdd));
                    for(Affix a:gem.affixes)lines.add(text(affix(a),0x85e4d6));
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
            String id=GameItems.id(currency);
            if(id.equals("affix_extraction_stone"))return "词条拆解尚未开放";
            if(id.equals("set_extraction_stone"))return gear.set.isEmpty()?"该装备没有套装":"";
            if(id.equals("set_stone")){
                var p=com.google.gson.JsonParser.parseString(GameItems.payload(currency)).getAsJsonObject();
                boolean armor=p.get("group").getAsString().equals("armor");
                if(gear.level<p.get("level").getAsInt()||armor&&!gear.slot.armor()||!armor&&!gear.slot.jewelry())return "套装石部位或来源等级不符";
                var definition=Definitions.CURRENT.sets.get(p.get("set").getAsString());
                return definition!=null&&definition.group().equals(p.get("group").getAsString())?"":"套装定义或部位分类无效";
            }
            if(id.equals("affix_gem")){
                Gem gem=Models.JSON.fromJson(GameItems.payload(currency),Gem.class);
                if(gear.sockets<=0)return "装备尚未打孔";
                String last="宝石不适用于此装备";
                for(int i=0;i<gear.sockets;i++)try{EquipmentRules.socket(gear,gem,i);return "";}catch(RuntimeException failure){last=failure instanceof IllegalArgumentException&&failure.getMessage()!=null?failure.getMessage():"宝石数据无效";}
                return last;
            }
            EquipmentRules.process(gear,GameItems.id(currency),new Random(0));
            return "";
        }catch(RuntimeException e){return e instanceof IllegalArgumentException&&e.getMessage()!=null?e.getMessage():"道具数据无效，无法加工";}
    }
    private static boolean shift(){
        var window=Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_SHIFT)||InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
    private static void addAffix(List<Component> out,Affix a,Gear gear,boolean gem,int color){
        String label=(gem?"  ":"")+affix(a);
        if(!shift()){out.add(text(label,color));return;}
        var range=AffixRanges.reroll(a,gear.rarity,gem);
        if(range==null){out.add(text(label+" 〔不可重掷〕",color));return;}
        String unit=a.operation.equals("PERCENT")?"%":"";
        if(range.interval())label+=" 〔可洗下限 "+FightScreen.number(range.minimum())+"～"+FightScreen.number(range.maximum())+"；上限 "+FightScreen.number(range.upperMinimum())+"～"+FightScreen.number(range.upperMaximum())+"〕";
        else label+=" 〔可洗 "+FightScreen.number(range.minimum())+unit+"～"+FightScreen.number(range.maximum())+unit+"〕";
        out.add(text(label,color));
    }
    private static void addSet(List<Component> out,Definitions.SetDefinition set,boolean range){
        for(var effect:set.effects()){
            String value=effect.stat()!=null?FightScreen.statName(effect.stat())+" +"+FightScreen.number(effect.value()):switch(effect.modifier()){
                case "basicSkillMain"->"基础三技能主伤 +"+FightScreen.number(effect.value()*100)+"%";
                case "basicSkillCooldown"->"基础三技能冷却 −"+FightScreen.number(effect.value()*100)+"%";
                default->effect.modifier()+" +"+FightScreen.number(effect.value());
            };
            out.add(text(effect.pieces()+"件："+value,0xb1ef89));
        }
        if(range)out.add(text("  套装效果不可重掷",0x78869c));
    }
    public static void item(ItemTooltipEvent e){
        var stack=e.getItemStack();
        Gear g=GameItems.gear(stack);
        List<Component> out=e.getToolTip();
        if(GameItems.own(stack))out.replaceAll(UiFonts::decorate);
        if(g==null){
            String id=GameItems.id(stack);
            if(CurrencySlots.isCurrency(stack)){out.addAll(currencyLines(stack));return;}
            if(id.equals("crafting_material")){
                var m=Catalog.CURRENT.materials.get(GameItems.material(stack));
                if(m!=null){
                    separator(out,"区域材料");
                    for(var d:Catalog.CURRENT.dungeons)if(d.materials().contains(m.id()))out.add(text("来源："+d.name()+(d.available()?"":"（尚未开放）"),0xa6c3db));
                    out.add(text("制作专用 · 固定数量 · 堆叠上限64",0xc5cfdd));
                }
            }
            return;
        }
        // Keep vanilla enchantments, lore and component descriptions supplied by other mods.
        out.add(text(g.rarity.title()+" · "+(g.slot==Slot.WEAPON?g.weapon.title():g.slot.title())+" · 需求 Lv."+g.level,g.rarity.color()));
        if(e.getEntity()!=null&&!g.valid(ClientState.INSTANCE.progress))out.add(text("等级或职业条件不足，装备属性不生效",0xff7272));
        separator(out,"基础属性");
        for(Affix a:g.affixes)if(a.base)addAffix(out,a,g,false,0xd9e4ed);
        List<Affix> extra=g.affixes.stream().filter(a->!a.base).toList();
        if(!extra.isEmpty()){
            separator(out,"装备词条");
            for(Affix a:extra)addAffix(out,a,g,false,a.operation.equals("SKILL_FAMILY")?0x95d8ff:a.fixed?0xffd080:0xc8a8f0);
        }
        if(!g.set.isEmpty()){
            separator(out,"套装");
            var definition=Definitions.CURRENT.sets.get(g.set);
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
        if(!shift())out.add(text("按住 Shift 查看可重掷范围",0x78869c));
        if(e.getFlags().isAdvanced())out.add(text("ID "+g.instance+" / r"+g.revision,0x68748b));
    }
}
