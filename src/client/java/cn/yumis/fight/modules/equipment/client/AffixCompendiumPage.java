package cn.yumis.fight.modules.equipment.client;

import cn.yumis.fight.modules.affixes.api.client.AffixFavorites;
import cn.yumis.fight.modules.equipment.api.EquipmentAffixCatalog;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.presentation.api.client.*;
import java.util.*;
import net.minecraft.network.chat.Component;

/** Eighty-level, real-definition previews; favorites never affect generation. */
public final class AffixCompendiumPage {
    private AffixCompendiumPage(){}
    public static void render(PlayerPageSurface page){
        page.line("词条宝典",UiTheme.GOLD);var query=page.textInput("affix.search","搜索词条、效果或适用部位",64);boolean only=page.remember("affix.favorites",false);
        page.rowButton(only?"★ 只看收藏":"☆ 查看全部",()->{page.rememberValue("affix.favorites",!only);page.rebuild();});
        String selected=page.remember("affix.slot","");
        int filterColumns=Math.max(1,(page.contentWidth()-24)/78),filterCell=(page.contentWidth()-24)/filterColumns,filterTop=page.cursor();
        for(int i=0;i<=Slot.values().length;i++){
            Slot slot=i==0?null:Slot.values()[i-1];String value=slot==null?"":slot.name();
            var filter=page.button(slot==null?"全部部位":slot.title(),(i%filterColumns)*filterCell,filterTop+(i/filterColumns)*24,filterCell-4,()->{page.rememberValue("affix.slot",value);page.rebuild();});
            if(filter instanceof ThemedButton themed)themed.selected=selected.equals(value);
        }
        page.cursor(filterTop+((Slot.values().length+1+filterColumns-1)/filterColumns)*24+4);
        if(!AffixFavorites.error().isEmpty())page.line(AffixFavorites.error(),UiTheme.ERROR);
        String search=query.get().trim().toLowerCase(Locale.ROOT);int count=0,columns=Math.max(1,(page.contentWidth()-24)/88),cell=(page.contentWidth()-24)/columns,top=page.cursor();
        for(var entry:EquipmentAffixCatalog.entries()){
            if(!selected.isEmpty()&&entry.slots().stream().noneMatch(slot->slot.name().equals(selected)))continue;
            boolean favorite=AffixFavorites.contains(entry.id());if(only&&!favorite)continue;String slots=String.join("、",entry.slots().stream().map(slot->slot.title()).toList());
            if(!search.isEmpty()&&!(entry.name()+" "+entry.category()+" "+entry.description()+" "+slots).toLowerCase(Locale.ROOT).contains(search))continue;
            int x=(count%columns)*cell,y=top+(count/columns)*80,w=cell-5;var button=page.button("",x,y,w,()->{AffixFavorites.toggle(entry.id());page.rebuild();});button.setHeight(75);
            String icon=switch(entry.category()){case "技能等级"->"profession_reset_ticket";case "元素附伤"->"divine_stone";case "属性提升"->"augmentation_stone";default->"exalted_stone";};page.icon("textures/item/"+icon+".png",x+(w-24)/2,y+7,24);
            var font=UiFonts.font();var lines=font.split(Component.literal(entry.name()),w-8);for(int i=0;i<Math.min(2,lines.size());i++){StringBuilder text=new StringBuilder();lines.get(i).accept((index,style,code)->{text.appendCodePoint(code);return true;});page.text(text.toString(),x+4,y+38+i*11,favorite?UiTheme.GOLD:UiTheme.TEXT);}page.text(favorite?"★":"☆",x+w-13,y+5,UiTheme.GOLD);
            page.hover(x,y,w,75,(graphics,mx,my)->{var detail=new ArrayList<Component>();detail.add(Component.literal(entry.name()+" · Lv.80").withColor(UiTheme.GOLD));if(!entry.description().isBlank())detail.add(Component.literal(entry.description()));for(var preview:entry.previews()){detail.add(Component.literal(String.join("、",preview.locations())).withColor(UiTheme.MUTED));for(var line:UiFonts.font().split(Component.literal(preview.text()),Math.min(380,Math.max(130,page.windowWidth()-32)))){StringBuilder text=new StringBuilder();line.accept((i,style,code)->{text.appendCodePoint(code);return true;});detail.add(Component.literal(text.toString()).withColor(0x85e4d6));}}graphics.setComponentTooltipForNextFrame(UiFonts.font(),detail,mx,my,net.minecraft.world.item.ItemStack.EMPTY);});count++;
        }
        page.cursor(top+((count+columns-1)/columns)*80);if(count==0)page.line(only?"暂无匹配的收藏词条":"没有匹配的词条",UiTheme.MUTED);
    }
}
