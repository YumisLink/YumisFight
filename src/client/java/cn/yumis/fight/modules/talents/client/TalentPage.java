package cn.yumis.fight.modules.talents.client;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.client.AttributeLabels;
import cn.yumis.fight.modules.combat.api.ElementAccumulation;
import cn.yumis.fight.modules.talents.api.TalentCatalog;
import cn.yumis.fight.modules.talents.api.TalentConditions;
import cn.yumis.fight.modules.talents.api.TalentLedger;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageSurface;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;

/** 天赋盘页面：六轴分页展示出生点与两线十位，点亮／拆除经账本纯函数本地判定后提交进度事实。 */
public final class TalentPage {
    private TalentPage(){}
    public static void render(PlayerPageSurface page,Progress progress,BiConsumer<String,String> operate,Consumer<String> message){
        var catalog=TalentCatalog.current();
        int budget=TalentLedger.budget(progress.level),used=TalentLedger.used(progress),available=TalentLedger.available(progress);
        page.line("天赋点 "+used+" / "+budget+"，可用 "+available+(available>0?"":"（每5级1点）"),UiTheme.GOLD);
        page.line("出生点占用1点；4／10位为环位，可由同环相邻已亮节点点亮。",UiTheme.MUTED);
        page.gap();
        String remembered=page.remember("talents.axis",progress.talentAxis==null?catalog.axes().get(0).id():progress.talentAxis);
        var axis=catalog.axis(remembered);
        if(axis==null)axis=catalog.axes().get(0);
        List<PlayerPageSurface.Navigation> tabs=new ArrayList<>();
        for(var entry:catalog.axes())tabs.add(new PlayerPageSurface.Navigation(entry.id(),entry.title()));
        page.navigation(tabs,axis.id(),id->{page.rememberValue("talents.axis",id);page.rebuild();});
        renderBirth(page,catalog,progress,axis,operate,message);
        page.gap();
        for(var line:catalog.linesOf(axis.id())){
            page.line(line.title()+"线",UiTheme.TEXT);
            for(var node:catalog.lineNodes(line.id()))renderNode(page,catalog,progress,node,operate,message);
            page.gap();
        }
    }
    private static void renderBirth(PlayerPageSurface page,TalentCatalog catalog,Progress progress,TalentCatalog.Axis axis,BiConsumer<String,String> operate,Consumer<String> message){
        var birth=catalog.birth(axis.id());
        boolean owned=progress.talentAxis!=null&&progress.talentAxis.equals(axis.id());
        if(owned){
            page.line("出生点："+birth.title()+"（已点亮，拆除前先拆空本轴两线）",UiTheme.TEXT);
            page.rowButton("拆除出生点（返还1点）",()->{
                if(!TalentLedger.canRemoveBirth(catalog,progress)){message.accept("本轴仍有节点，先拆空两线再拆出生点");return;}
                operate.accept("removeBirth",axis.id());
            });
        }else if(progress.talentAxis==null){
            page.line("出生点：未点亮",UiTheme.MUTED);
            page.rowButton("点亮出生点（占用1点）",()->{
                if(!TalentLedger.canPlaceBirth(catalog,progress,axis.id())){message.accept(TalentLedger.available(progress)<1?"天赋点不足":"出生点不可用");return;}
                operate.accept("placeBirth",axis.id());
            });
        }else page.line("出生点：已选其他轴（"+catalog.axis(progress.talentAxis).title()+"）",UiTheme.MUTED);
        page.hover(0,page.cursor()-24,page.contentWidth()-24,20,tooltip(birth));
    }
    private static void renderNode(PlayerPageSurface page,TalentCatalog catalog,Progress progress,TalentCatalog.Node node,BiConsumer<String,String> operate,Consumer<String> message){
        boolean lit=progress.talentNodes.contains(node.id());
        String state=lit?"已点亮":node.retired()?"已停用":TalentLedger.canPlace(catalog,progress,node.id())?"可点亮":"未满足";
        page.rowButton(node.position()+(node.position()==4||node.position()==10?"（环）":"")+"位　"+node.title()+"　"+state,()->{
            if(lit){
                if(!TalentLedger.canRemove(catalog,progress,node.id())){message.accept("该节点当前不可拆除：拆除后外侧节点会悬空");return;}
                operate.accept("removeTalent",node.id());
            }else{
                if(node.retired()||!TalentLedger.canPlace(catalog,progress,node.id())){message.accept("该节点当前不可点亮："+qualification(catalog,progress,node));return;}
                operate.accept("placeTalent",node.id());
            }
        });
        page.hover(0,page.cursor()-24,page.contentWidth()-24,20,tooltip(node));
    }
    private static String qualification(TalentCatalog catalog,Progress progress,TalentCatalog.Node node){
        if(node.retired())return "该节点已停用";
        if(TalentLedger.available(progress)<1)return "天赋点不足";
        if(node.position()<=3){
            if(progress.talentAxis==null||!progress.talentAxis.equals(catalog.line(node.line()).axis()))return "需先点亮本轴出生点";
            return "需同线内侧节点全亮";
        }
        if(node.position()==4||node.position()==10)return "需同线内侧全亮或同环相邻已亮";
        return "需先点亮同线第4位并全亮内侧";
    }
    private static PlayerPageSurface.Hover tooltip(TalentCatalog.Node node){
        var lines=new ArrayList<String>();
        lines.add(node.title()+"（"+tier(node.position())+"·第"+node.position()+"位"+(node.position()==4||node.position()==10?"，环位":"")+"）");
        for(var effect:node.effects())lines.add(effect(effect));
        return hover(lines);
    }
    private static PlayerPageSurface.Hover tooltip(TalentCatalog.Birth birth){
        var lines=new ArrayList<String>();
        lines.add(birth.title()+"（出生点，占用1点）");
        for(var effect:birth.effects())lines.add(effect(effect));
        return hover(lines);
    }
    private static PlayerPageSurface.Hover hover(List<String> lines){
        return (g,x,y)->g.setComponentTooltipForNextFrame(UiFonts.font(),lines.stream().map(Component::literal).map(UiFonts::decorate).toList(),x,y);
    }
    private static String tier(int position){return position<=3?"基础":position==4?"环枢":position<10?"深固":"环顶";}
    private static String effect(TalentCatalog.Effect effect){
        String condition=effect.condition()==null?"":TalentConditions.describe(effect.condition().kind(),effect.condition().threshold(),effect.condition().tag());
        String prefix=condition.isEmpty()?"":condition+"：";
        return switch(effect.channel()){
            case "mut"->prefix+AttributeLabels.statName(Stat.valueOf(effect.key()))+" 提升 "+percent(effect.value());
            case "add"->prefix+AttributeLabels.statName(Stat.valueOf(effect.key()))+" +"+effect.value()+" 点";
            case "total"->prefix+AttributeLabels.statName(Stat.valueOf(effect.key()))+" 总增 "+percent(effect.value())+"（各来源独立乘算）";
            case "final"->prefix+"最终伤害提升 "+percent(effect.value());
            case "crit"->prefix+"暴击率 +"+percent(effect.value());
            case "taken"->prefix+"承伤减免 "+percent(effect.value());
            case "killHeal"->prefix+(effect.key().isEmpty()?"击败敌人回复 "+effect.value()+" 点生命":"召唤兽击败敌人回复 "+effect.value()+" 点生命")+"（需奖励资格）";
            case "count"->prefix+(effect.key().equals("capstone")?"每个已亮环位：":"每条已整线：")+"最终伤害提升 "+percent(effect.value())+"×计数";
            case "param"->prefix+param(effect.key(),effect.value());
            case "conversion"->prefix+conversion(effect.key(),effect.value());
            default->effect.channel();
        };
    }
    private static String param(String key,double value){
        return switch(key){
            case "summonInherit/HEALTH"->"召唤兽生命继承 ×(1+"+percent(value)+")";
            case "summonInherit/DEFENSE"->"召唤兽物防／魔防继承 ×(1+"+percent(value)+")";
            case "summonInherit/RESIST"->"召唤兽四抗／闪避继承 ×(1+"+percent(value)+")";
            case "summonInherit/SHIELD"->"召唤兽护盾继承 ×(1+"+percent(value)+")";
            case "summonDamage"->"召唤伤害总增 "+percent(value)+"（独立乘算）";
            case "summonDuration"->"召唤在场时长 ×(1+"+percent(value)+")";
            case "summonAggro"->"召唤索敌半径 ×(1+"+percent(value)+")";
            case "summonElement"->"召唤攻击元素："+element((int)value);
            case "summonMana"->"召唤技能消耗 ×(1−"+percent(value)+")";
            case "killWindow"->"击杀窗口 +"+Math.round(value)+" 毫秒（延展“击败敌人后”条件行）";
            default->key;
        };
    }
    private static String conversion(String key,double ratio){
        int split=key.indexOf(':');
        String sources=key.substring(0,split);
        var kind=ElementAccumulation.kind(key.substring(split+1));
        String elements=sources.equals("ALL")?"全部":elementTokens(sources);
        return elements+"伤害积累「"+kind.title+"」，比例 "+ratio;
    }
    private static String elementTokens(String sources){
        var names=new ArrayList<String>();
        for(var token:sources.split(",",-1))names.add(element(Integer.parseInt(token)));
        return String.join("／",names);
    }
    private static String element(int element){return switch(element){case -1->"无属性";case 0->"光";case 1->"火";case 2->"冰";case 3->"暗";default->"元素"+element;};}
    private static String percent(double value){return Math.round(value*100)+"%";}
}
