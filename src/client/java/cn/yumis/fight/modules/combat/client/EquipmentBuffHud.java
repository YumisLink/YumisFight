package cn.yumis.fight.modules.combat.client;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
/** Source-specific equipment states are displayed with their remaining lifetime. */
public final class EquipmentBuffHud {
    private EquipmentBuffHud(){}
    public static void render(GuiGraphicsExtractor g,cn.yumis.fight.modules.combat.api.Runtime resources,long now,int right,int bottom){
        var font=UiFonts.font();int y=bottom;
        for(var entry:resources.equipmentBuffs.entrySet()){
            var buff=entry.getValue();if(buff.expires<=now||buff.stacks<=0)continue;
            String label=switch(entry.getKey()){case "golden"->"黄金之力 "+buff.stacks+" / 255";case "imprint"->"印记之力 "+buff.stacks+" / 18";case "elite/strong"->"精英力量 · 强壮";case "elite/rage"->"精英力量 · 狂暴";case "elite/armor"->"精英力量 · 坚韧";case "elite/elemental"->"精英力量 · 元素守护";default->"";};
            if(label.isEmpty())continue;label+="  "+Math.max(0,(buff.expires-now+999)/1000)+"秒";
            int width=font.width(label)+10;g.fill(right-width,y-14,right,y,UiTheme.HUD_PANEL);g.text(font,label,right-width+5,y-11,UiTheme.HUD_GOLD,true);y-=16;
        }
    }
}
