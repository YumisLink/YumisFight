package cn.yumis.fight.modules.professions.client;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.Map;
/** 合剂增益显示：攻击类与防御类各一条，带剩余时间。 */
public final class AlchemyBuffHud {
    private AlchemyBuffHud(){}
    private static final Map<String,Long> BUFFS=new HashMap<>();
    private static final Map<String,String> POTIONS=new HashMap<>();
    /** 服务端同步：expires <= 0 表示移除该类别。 */
    public static void update(String category,String potion,long expires){
        if(expires<=0){BUFFS.remove(category);POTIONS.remove(category);return;}
        BUFFS.put(category,expires);
        POTIONS.put(category,potion);
    }
    public static void clear(){BUFFS.clear();POTIONS.clear();}
    public static void render(GuiGraphicsExtractor g,long now,int right,int bottom){
        var font=UiFonts.font();int y=bottom;
        for(var entry:BUFFS.entrySet()){
            long expires=entry.getValue();if(expires<=now)continue;
            String potion=POTIONS.getOrDefault(entry.getKey(),"");
            if(potion.isEmpty())continue;
            String name=Component.translatable("item.yumis_dungeon_and_fight."+potion).getString();
            String label="合剂 · "+name+"  "+Math.max(0,(expires-now+999)/1000)+"秒";
            int width=font.width(label)+10;UiTheme.hud(g,right-width,y-14,(right)-(right-width),(y)-(y-14));g.text(font,label,right-width+5,y-11,UiTheme.HUD_GOLD,true);y-=16;
        }
    }
}
