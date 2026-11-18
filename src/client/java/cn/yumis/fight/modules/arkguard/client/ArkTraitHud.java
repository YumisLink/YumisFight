package cn.yumis.fight.modules.arkguard.client;

import cn.yumis.fight.modules.arkguard.api.ArkRules;
import cn.yumis.fight.modules.arkguard.api.ArkTraits;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.LinkedHashMap;
import java.util.Map;

final class ArkTraitHud {
    private static final long GAIN_MILLIS=3000,RISE_MILLIS=700;
    private record Row(ArkTraits.Meter meter,int gain,long gainUntil,long riseUntil){}
    private final Map<String,Row> rows=new LinkedHashMap<>();
    private Map<String,Integer> previousLayers=Map.of();
    private boolean started;

    void clear(){
        rows.clear();
        previousLayers=Map.of();
        started=false;
    }

    void update(boolean combat,Map<String,Integer> counts,Map<String,Integer> layers,ArkRules rules,long now){
        if(!started&&!combat)return;
        var next=new LinkedHashMap<String,Row>();
        for(String id:ArkTraits.displayOrder(counts.keySet(),counts,layers)){
            var meter=ArkTraits.meter(id,counts,layers,rules);
            var before=rows.get(id);
            long gainUntil=before==null?0:before.gainUntil();
            int gain=before!=null&&gainUntil>now?before.gain():0;
            int added=meter.layers()-previousLayers.getOrDefault(id,0);
            if(started&&added>0){
                gain+=added;
                gainUntil=now+GAIN_MILLIS;
            }
            long riseUntil=before==null?0:before.riseUntil();
            if(started&&meter.tier()>(before==null?0:before.meter().tier()))riseUntil=now+RISE_MILLIS;
            if(before!=null&&meter.tier()<before.meter().tier())riseUntil=0;
            next.put(id,new Row(meter,gain,gainUntil,riseUntil));
        }
        rows.clear();
        rows.putAll(next);
        previousLayers=Map.copyOf(layers);
        started=true;
    }

    void render(GuiGraphicsExtractor graphics,long now){
        if(rows.isEmpty())return;
        var font=UiFonts.font();
        int line=font.lineHeight+3,panelHeight=rows.size()*line+8;
        int widest=0;
        for(var row:rows.values()){
            String gain=row.gainUntil()>now?"  +"+row.gain():"";
            widest=Math.max(widest,font.width(row.meter().label()+gain)+(row.riseUntil()>now?14:0));
        }
        float availableWidth=Math.max(60,graphics.guiWidth()/2f-14);
        float availableHeight=Math.max(24,graphics.guiHeight()-48);
        float scale=Math.min(1,Math.min(availableWidth/(widest+10),availableHeight/panelHeight));
        graphics.pose().pushMatrix();
        graphics.pose().translate(6,centeredTop(graphics.guiHeight(),panelHeight,scale));
        graphics.pose().scale(scale,scale);
        UiTheme.hud(graphics,0,0,widest+10,panelHeight);
        int rowY=5;
        for(var row:rows.values()){
            String label=row.meter().label();
            int textColor=row.meter().tier()>0?UiTheme.HUD_TEXT:UiTheme.HUD_MUTED;
            graphics.text(font,label,5,rowY,textColor,true);
            int after=5+font.width(label);
            if(row.gainUntil()>now){
                String gain="  +"+row.gain();
                graphics.text(font,gain,after,rowY,row.meter().tier()>0?0xFF8DE5A4:UiTheme.HUD_MUTED,true);
                after+=font.width(gain);
            }
            if(row.riseUntil()>now){
                float remaining=(row.riseUntil()-now)/(float)RISE_MILLIS;
                int rise=Math.round(6*remaining);
                int alpha=Math.clamp(Math.round(255*remaining*2),40,255);
                graphics.text(font,"↑",after+4,rowY+rise,(alpha<<24)|(UiTheme.HUD_GOLD&0xFFFFFF),true);
            }
            rowY+=line;
        }
        graphics.pose().popMatrix();
    }
    static float centeredTop(int screenHeight,int panelHeight,float scale){
        return (screenHeight-panelHeight*scale)*.5f;
    }
}
