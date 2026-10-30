package cn.yumis.fight.modules.equipment.client;

import cn.yumis.fight.modules.affixes.api.client.AffixFavorites;
import cn.yumis.fight.modules.equipment.api.EquipmentAffixCatalog;
import cn.yumis.fight.modules.presentation.api.client.*;
import java.util.*;
import net.minecraft.network.chat.Component;

/** Encyclopedia of actual random candidates. Stars only decorate this client's text. */
public final class AffixCompendiumPage {
    private AffixCompendiumPage(){}
    public static void render(PlayerPageSurface page){
        page.line("词条宝典",UiTheme.GOLD);page.line("点星收藏；装备、宝石和刷新结果中会标记喜欢的词条。",UiTheme.MUTED);
        var query=page.textInput("affix.search","搜索词条、效果或适用部位",64);boolean only=page.remember("affix.favorites",false);
        page.rowButton(only?"★ 只看收藏":"☆ 查看全部",()->{page.rememberValue("affix.favorites",!only);page.rebuild();});
        if(!AffixFavorites.error().isEmpty())page.line(AffixFavorites.error(),UiTheme.ERROR);
        String search=query.get().trim().toLowerCase(Locale.ROOT);int count=0;
        for(var entry:EquipmentAffixCatalog.entries()){
            boolean favorite=AffixFavorites.contains(entry.id());if(only&&!favorite)continue;
            String slots=String.join("、",entry.slots().stream().map(slot->slot.title()).toList());
            if(!search.isEmpty()&&!(entry.name()+" "+entry.category()+" "+entry.description()+" "+slots).toLowerCase(Locale.ROOT).contains(search))continue;
            int y=page.cursor(),width=page.contentWidth();page.card(0,y,width,48);
            var star=page.button(favorite?"★":"☆",4,y+5,30,()->{AffixFavorites.toggle(entry.id());page.rebuild();});star.setHeight(30);
            page.text(UiFonts.font().plainSubstrByWidth(entry.name(),Math.max(1,width-46)),40,y+7,favorite?UiTheme.GOLD:UiTheme.TEXT);
            page.text(UiFonts.font().plainSubstrByWidth(entry.category()+" · "+slots,Math.max(1,width-46)),40,y+26,UiTheme.MUTED);
            page.hover(38,y,width-38,46,(g,mx,my)->g.setComponentTooltipForNextFrame(UiFonts.font(),List.of(Component.literal(entry.name()),Component.literal(entry.category()+" · "+slots),Component.literal(entry.description().isBlank()?"收藏只影响本机文本展示，不改变抽取概率或效果。":entry.description())),mx,my,net.minecraft.world.item.ItemStack.EMPTY));
            page.cursor(y+53);count++;
        }
        if(count==0)page.line(only?"暂无匹配的收藏词条":"没有匹配的词条",UiTheme.MUTED);
    }
}
