package cn.yumis.fight.modules.professions.client;

import cn.yumis.fight.modules.professions.api.ProfessionBonuses;
import cn.yumis.fight.modules.professions.api.ProfessionCatalog;
import cn.yumis.fight.modules.professions.api.ProfessionRules;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageSurface;
import com.google.gson.JsonObject;
import java.math.BigInteger;

/** Profession levels and within-level experience rendered with the vanilla experience bar. */
public final class ProfessionPage {
    private ProfessionPage(){}
    public static void render(PlayerPageSurface page,JsonObject snapshot,long gold,Runnable refresh,int titleColor,int muted){
        if(!snapshot.has("professions")){page.line("正在读取副职业…");return;}
        var all=snapshot.getAsJsonObject("professions");
        for(var definition:ProfessionCatalog.all()){
            if(!all.has(definition.id()))continue;
            var progress=all.getAsJsonObject(definition.id());
            if(progress==null||!progress.has("level")||!progress.has("experience"))continue;
            int level=progress.get("level").getAsInt();
            boolean maximum=level>=ProfessionBonuses.MAX_LEVEL;
            BigInteger experience=new BigInteger(progress.get("experience").getAsString());
            BigInteger floor=ProfessionRules.professionThreshold(level);
            BigInteger need=maximum?BigInteger.ONE:ProfessionRules.professionThreshold(level+1).subtract(floor);
            BigInteger current=experience.subtract(floor).max(BigInteger.ZERO);
            page.line(definition.name()+" Lv."+level,titleColor);
            int y=page.cursor(),width=Math.min(182,page.contentWidth()-24);
            page.experienceBar(0,y,width,maximum?1:current.doubleValue()/need.doubleValue());
            String hint=maximum?"已满级":current+"/"+need;
            page.hover(0,y-2,width,9,(g,mx,my)->g.setComponentTooltipForNextFrame(cn.yumis.fight.modules.presentation.api.client.UiFonts.font(),java.util.List.of(net.minecraft.network.chat.Component.literal(hint)),mx,my));
            page.cursor(y+17);
        }
    }
}
