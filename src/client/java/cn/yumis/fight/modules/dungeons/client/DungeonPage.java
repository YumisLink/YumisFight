package cn.yumis.fight.modules.dungeons.client;
import cn.yumis.fight.modules.dungeons.api.DungeonCatalog;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.api.Difficulty;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageSurface;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import java.util.function.Consumer;
public final class DungeonPage {
    public record Join(String dungeon,Difficulty difficulty,boolean sync){}
    private DungeonPage(){}
    public static void render(PlayerPageSurface page,Dungeon current,Consumer<Join> join,Runnable leave,Consumer<String> message){
        if(current!=null){page.line("当前："+current.dungeon+"  波次 "+(current.wave+1)+" / 7");page.line("累计参与 "+current.roster.size()+"  "+current.difficulty.title()+"  "+current.status);page.rowButton("离开地下城",leave);page.gap();}
        Difficulty difficulty=page.remember("dungeon.difficulty",Difficulty.NORMAL);boolean sync=page.remember("dungeon.sync",true);int width=(page.contentWidth()-32)/2;
        page.button("难度："+difficulty.title(),0,page.cursor(),width,()->{page.rememberValue("dungeon.difficulty",Difficulty.values()[(difficulty.ordinal()+1)%Difficulty.values().length]);page.rebuild();});
        page.button("同步："+(sync?"开启":"关闭"),width+8,page.cursor(),width,()->{page.rememberValue("dungeon.sync",!sync);page.rebuild();});page.cursor(page.cursor()+28);
        for(var definition:DungeonCatalog.current().dungeons)page.rowButton("Lv."+definition.level()+" "+definition.name()+(definition.available()?" · 创建／进入":" · 尚未开放"),()->{if(!definition.available()){message.accept("该副本为未来内容占位");return;}join.accept(new Join(definition.id(),difficulty,sync));});
        page.line("已运行的公开轮次沿用原设置。材料按产区掉落，不随同步等级改变。",UiTheme.MUTED);
    }
}
