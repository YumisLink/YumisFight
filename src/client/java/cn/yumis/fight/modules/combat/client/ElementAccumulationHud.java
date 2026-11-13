package cn.yumis.fight.modules.combat.client;

import cn.yumis.fight.modules.combat.api.ElementAccumulation;
import cn.yumis.fight.modules.combat.api.ElementAccumulation.Kind;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Confirmed target buildup, followed by a shrinking remaining-duration circle. */
public final class ElementAccumulationHud {
    public static final int HEIGHT=14;
    private static final int DIAMETER=14,INNER=DIAMETER-2;
    private static final double TURN=Math.PI*2;
    private static final Kind[] ORDER={Kind.FREEZE,Kind.IGNITE,Kind.SHOCK,Kind.EROSION};
    private static final double[][] ANGLES=angles();
    private ElementAccumulationHud(){}

    public static void render(GuiGraphicsExtractor g,Life life,boolean strong,long now,int x,int y,int width){
        var font=UiFonts.font();var state=life.elemental;boolean current=!life.dead&&state.matches(life);
        int count=0;
        for(Kind kind:ORDER)if(current&&(state.active(kind,life,now)||state.buildup[kind.ordinal()]>0))count++;
        int left=x+(width-count*(DIAMETER+2)+2)/2,shown=0;
        for(int i=0;i<ORDER.length;i++){
            Kind kind=ORDER[i];int index=kind.ordinal();
            boolean active=current&&state.active(kind,life,now);
            if(!current||!active&&state.buildup[index]<=0)continue;
            int center=left+shown++*(DIAMETER+2)+DIAMETER/2,top=y;
            double progress=active?Math.clamp((double)(state.until[index]-now)/kind.duration(strong),0,1):Math.clamp(state.buildup[index]/ElementAccumulation.THRESHOLD,0,1);
            int color=0xff000000|kind.color;
            circle(g,center-DIAMETER/2,top,progress,color,active);
            if(active){
                String remaining=Long.toString((state.until[index]-now+999)/1000);
                g.text(font,remaining,center-font.width(remaining)/2,top+(DIAMETER-font.lineHeight)/2,0xfff1f4f8,true);
            }
        }
    }

    private static void circle(GuiGraphicsExtractor g,int x,int y,double progress,int color,boolean active){
        UiTheme.surface(g,x,y,DIAMETER,DIAMETER,DIAMETER/2,progress>=1?color:0xff161a22,active?0xfff1f4f8:color);
        if(progress<=0||progress>=1)return;
        double sweep=progress*TURN;
        for(int row=0;row<INNER;row++){
            int start=-1;
            for(int col=0;col<=INNER;col++){
                boolean filled=col<INNER&&ANGLES[row][col]>=0&&ANGLES[row][col]<sweep;
                if(filled&&start<0)start=col;
                else if(!filled&&start>=0){
                    g.fill(x+1+start,y+1+row,x+1+col,y+2+row,color);start=-1;
                }
            }
        }
    }

    /** Precompute pixel angles once; each frame emits contiguous horizontal spans. */
    private static double[][] angles(){
        var result=new double[INNER][INNER];double radius=INNER/2d;
        for(int row=0;row<INNER;row++)for(int col=0;col<INNER;col++){
            double dx=col+.5-radius,dy=row+.5-radius;
            result[row][col]=dx*dx+dy*dy>radius*radius?-1:(Math.atan2(dx,-dy)+TURN)%TURN;
        }
        return result;
    }
}
