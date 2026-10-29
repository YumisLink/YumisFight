package cn.yumis.fight.modules.character.client;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.character.api.CharacterRules;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageSurface;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import java.util.*;
import java.util.function.*;

/** Prepared values and tooltip callbacks keep the character page independent of combat/equipment implementations. */
public final class CharacterPage {
    public record Value(String name,String value,PlayerPageSurface.Hover tooltip){}
    public record Allocation(String id,Value value){}
    public record View(Progress progress,boolean fighting,List<Allocation> allocations,List<Value> attributes){}
    private CharacterPage(){}
    public static void render(PlayerPageSurface page,View view,Consumer<String> allocate,Runnable reset,Consumer<Boolean> sync){
        var p=view.progress();
        page.line(JobCatalog.require(p.jobId()).title()+"  Lv."+p.level+" / 80",UiTheme.GOLD);
        page.line("战斗经验 "+p.experience+" / "+(p.level==80?"MAX":CharacterRules.nextExperience(p.level))+"    自由点 "+p.free());
        page.line("按住 Shift 点击 +1：最多加5点。",UiTheme.MUTED);
        page.line(view.fighting()?"战斗中":"已脱战");page.gap();
        for(var allocation:view.allocations()){
            var value=allocation.value();page.line(value.name()+"  "+value.value());
            page.hover(0,page.cursor()-12,page.contentWidth()-90,12,value.tooltip());
            page.button("+1",page.contentWidth()-80,page.cursor()-14,52,()->allocate.accept(allocation.id()));page.cursor(page.cursor()+10);
        }
        page.rowButton("重置自由点",reset);
        int columns=page.contentWidth()>=440?4:page.contentWidth()>=260?2:1,cell=(page.contentWidth()-24)/columns,index=0,base=page.cursor();
        for(var value:view.attributes()){
            int x=(index%columns)*cell,y=base+(index/columns)*38;index++;
            page.card(x,y,cell-5,33);page.hover(x,y,cell-5,33,value.tooltip());
            page.text(value.name(),x+6,y+5,UiTheme.MUTED);page.text(value.value(),x+6,y+18,UiTheme.TEXT);
        }
        page.cursor(base+((index+columns-1)/columns)*38);page.gap();
        page.rowButton("个人等级同步："+(p.sync?"开启":"关闭"),()->sync.accept(!p.sync));
        page.line("饰品按 G 打开 Curios 界面管理；无效等级或职业的装备不提供属性。",UiTheme.MUTED);
    }
}
