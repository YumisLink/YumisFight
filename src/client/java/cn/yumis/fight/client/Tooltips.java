package cn.yumis.fight.client;
import cn.yumis.fight.game.*;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import java.util.*;
public final class Tooltips {
    private Tooltips(){
    }
    private static Component text(String s,int color){
        return Component.literal(s).withStyle(style->style.withColor(color).withItalic(false));
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
    public static void item(ItemTooltipEvent e){
        var stack=e.getItemStack();
        Gear g=GameItems.gear(stack);
        List<Component> out=e.getToolTip();
        if(g==null){
            String id=GameItems.id(stack);
            if(id.equals("crafting_material")){
                var m=Catalog.CURRENT.materials.get(GameItems.material(stack));
                if(m!=null){
                    separator(out,"区域材料");
                    for(var d:Catalog.CURRENT.dungeons)if(d.materials().contains(m.id()))out.add(text("来源："+d.name()+(d.available()?"":"（尚未开放）"),0xa6c3db));
                    out.add(text("制作专用 · 固定数量 · 堆叠上限64",0xc5cfdd));
                }
            }
            else if(id.equals("affix_extraction_stone"))out.add(text("特殊词条拆卸暂未开放",0xffbc78));
            else if(id.equals("set_extraction_stone"))out.add(text("右键装备提取套装，消耗整件装备与1枚石头",0xffbc78));
            else if(id.equals("socket_stone"))out.add(text("拿起后右键装备：新增1孔",0xa6c3db));
            else if(id.endsWith("upgrade_stone"))out.add(text("对应品质装备提升5级，封顶80，仅重算基础属性",0xa6c3db));
            else if(id.equals("affix_gem")){
                try{
                    Gem gem=Models.JSON.fromJson(GameItems.payload(stack),Gem.class);
                    if(gem!=null){
                        separator(out,"宝石");
                        out.add(text("来源等级 "+gem.level+" · 部位 "+gem.group,0xa6c3db));
                        for(Affix a:gem.affixes)out.add(text(affix(a),0x85e4d6));
                        out.add(text("右键已打孔装备镶嵌；覆盖会消耗旧宝石",0xffbc78));
                    }
                }
                catch(RuntimeException ignored){
                }
            }
            return;
        }
        if(out.size()>1)out.subList(1,out.size()).clear();
        out.add(text(g.rarity.title()+" · "+(g.slot==Slot.WEAPON?g.weapon.title():g.slot.title())+" · 需求 Lv."+g.level,g.rarity.color()));
        if(e.getEntity()!=null&&!g.valid(ClientState.INSTANCE.progress))out.add(text("等级或职业条件不足，装备属性不生效",0xff7272));
        separator(out,"基础属性");
        for(Affix a:g.affixes)if(a.base)out.add(text(affix(a),0xd9e4ed));
        List<Affix> extra=g.affixes.stream().filter(a->!a.base).toList();
        if(!extra.isEmpty()){
            separator(out,"装备词条");
            for(Affix a:extra)out.add(text(affix(a),a.operation.equals("SKILL_FAMILY")?0x95d8ff:a.fixed?0xffd080:0xc8a8f0));
        }
        if(!g.set.isEmpty()){
            separator(out,"套装");
            out.add(text(g.set.equals("first_sword")?"初式剑术套装":g.set.equals("four_elements")?"四象共鸣套装":g.set,0xb1ef89));
            out.add(text(g.slot.armor()?"2件：基础三技能主伤+10%；4件：其冷却−10%":"3件：四属性强化各+10",0xb1ef89));
        }
        if(g.sockets>0){
            separator(out,"宝石孔");
            for(int i=0;i<g.sockets;i++){
                if(i>=g.gems.size())out.add(text("◇ 空孔 "+(i+1),0x8c9db3));
                else{
                    Gem gem=g.gems.get(i);
                    out.add(text("◆ "+gem.name+" [Lv."+gem.level+"]",0x7ae7cf));
                    for(Affix a:gem.affixes)out.add(text("  "+affix(a),0xb8dfda));
                }
            }
        }
        if(e.getFlags().isAdvanced())out.add(text("ID "+g.instance+" / r"+g.revision,0x68748b));
    }
}
