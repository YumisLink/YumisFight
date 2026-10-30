package cn.yumis.fight.modules.combat.client;

import cn.yumis.fight.modules.combat.api.client.DamageAnalysis;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.Locale;

/** A live, scrollable result page. Viewing it neither starts nor resets a round. */
public final class DamageAnalysisPanel implements ClientPagePanel {
    private int scroll,maxScroll;
    private Region region;
    @Override public void render(GuiGraphicsExtractor g,Region r,int mouseX,int mouseY,float delta){
        region=r;var font=UiFonts.font();var view=DamageAnalysis.view();
        int x=r.x()+8,y=r.y()+8,w=Math.max(1,r.width()-16);
        g.text(font,"Ctrl＋】开始 / 停止 · "+view.reason(),x,y,view.recording()?UiTheme.GOLD:UiTheme.MUTED,false);
        y+=16;String mode=view.kind()==null?"等待首次命中":view.kind()==DamageAnalysis.Kind.COMBAT?"实战有效伤害":view.kind()==DamageAnalysis.Kind.MIXED?"实战及计时沙袋伤害":"沙袋训练伤害";
        g.text(font,mode+" · "+String.format(Locale.ROOT,"%.1f",view.seconds())+"秒",x,y,UiTheme.TEXT,false);
        y+=16;double dps=view.seconds()>0?view.total()/view.seconds():0;
        String total="总伤害 "+NumberText.damage(view.total()),dpsText="DPS "+NumberText.damage(dps);
        if(font.width(total+"    "+dpsText)<=w)g.text(font,total+"    "+dpsText,x,y,UiTheme.GOLD,false);
        else {g.text(font,total,x,y,UiTheme.GOLD,false);y+=14;g.text(font,dpsText,x,y,UiTheme.GOLD,false);}
        y+=22;int amountX=x+w/2,shareX=x+w*3/4,hitsX=x+w-46;
        boolean wide=w>=440&&view.rows().stream().allMatch(row->font.width(NumberText.damage(row.damage()))<w/4-12);
        g.text(font,"技能",x,y,UiTheme.MUTED,false);
        if(wide){g.text(font,"伤害",amountX,y,UiTheme.MUTED,false);g.text(font,"占比",shareX,y,UiTheme.MUTED,false);g.text(font,"命中",hitsX,y,UiTheme.MUTED,false);}y+=16;
        int rowHeight=wide?25:49,visible=Math.max(0,(r.y()+r.height()-y-6)/rowHeight);
        maxScroll=Math.max(0,view.rows().size()-visible);scroll=Math.clamp(scroll,0,maxScroll);
        java.util.List<net.minecraft.network.chat.Component> hover=null;
        g.enableScissor(r.x(),y,r.x()+r.width(),r.y()+r.height());
        for(int i=0;i<visible&&scroll+i<view.rows().size();i++){
            var row=view.rows().get(scroll+i);int rowY=y+i*rowHeight;
            String name=font.plainSubstrByWidth(row.source().title(),Math.max(1,wide?amountX-x-8:w));
            g.text(font,name,x,rowY,UiTheme.TEXT,false);
            String amount=NumberText.damage(row.damage());
            g.text(font,wide?amount:"伤害 "+amount,wide?amountX:x,wide?rowY:rowY+14,UiTheme.GOLD,false);
            String share=String.format(Locale.ROOT,"%.1f%%",view.total()>0?100*row.damage()/view.total():0);
            g.text(font,wide?share:share+"    命中 "+row.hits(),wide?shareX:x,wide?rowY:rowY+28,UiTheme.MUTED,false);
            if(wide)g.text(font,Long.toString(row.hits()),hitsX,rowY,UiTheme.TEXT,false);
            if(mouseX>=x&&mouseX<x+w&&mouseY>=rowY&&mouseY<rowY+rowHeight)hover=java.util.List.of(net.minecraft.network.chat.Component.literal(row.source().title()),net.minecraft.network.chat.Component.literal("伤害 "+amount+" · "+share+" · 命中 "+row.hits()));
        }
        if(view.rows().isEmpty())g.text(font,"开启统计后造成伤害，结果会显示在这里。",x,y,UiTheme.MUTED,false);
        g.disableScissor();
        if(hover!=null)g.setComponentTooltipForNextFrame(font,hover,mouseX,mouseY,net.minecraft.world.item.ItemStack.EMPTY);
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){
        if(region==null||x<region.x()||x>=region.x()+region.width()||y<region.y()||y>=region.y()+region.height())return false;
        scroll=Math.clamp(scroll-(int)Math.signum(sy)*3,0,maxScroll);return true;
    }
}
