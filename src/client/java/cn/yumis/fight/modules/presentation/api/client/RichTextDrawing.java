package cn.yumis.fight.modules.presentation.api.client;

import cn.yumis.fight.modules.presentation.api.RichText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

/** Uses native wrapping and glyph layout, with a per-span top-to-bottom color pair. */
public final class RichTextDrawing {
    private RichTextDrawing(){}
    public static int length(FormattedCharSequence line){int[] count={0};line.accept((i,s,c)->{count[0]++;return true;});return count[0];}
    public static FormattedCharSequence prefix(FormattedCharSequence line,int characters){return sink->{int[] left={Math.max(0,characters)};return line.accept((i,s,c)->left[0]-->0&&sink.accept(i,s,c));};}
    public static void line(GuiGraphicsExtractor g,Font font,FormattedCharSequence line,int x,int y,int color){
        boolean[] gradient={false};line.accept((i,s,c)->{String mark=s.getInsertion();gradient[0]|=mark!=null&&mark.startsWith(RichText.GRADIENT);return true;});
        if(!gradient[0]){g.text(font,line,x,y,color,false);return;}
        int padding=Math.max(12,font.lineHeight*2),width=font.width(line)+padding;
        for(int row=-padding;row<font.lineHeight+padding;row++){
            double t=Math.clamp(row/(double)Math.max(1,font.lineHeight-2),0,1);
            FormattedCharSequence stripe=sink->line.accept((i,style,character)->{
                String mark=style.getInsertion();if(mark!=null&&mark.startsWith(RichText.GRADIENT)){
                    var pair=mark.substring(RichText.GRADIENT.length()).split("/");
                    if(pair.length==2&&pair[0].matches("[a-fA-F0-9]{6}")&&pair[1].matches("[a-fA-F0-9]{6}"))style=style.withInsertion(null).withColor(mix(Integer.parseInt(pair[0],16),Integer.parseInt(pair[1],16),t));
                }return sink.accept(i,style,character);
            });
            g.enableScissor(x-padding,y+row,x+width,y+row+1);try{g.text(font,stripe,x,y,color,false);}finally{g.disableScissor();}
        }
    }
    private static int mix(int top,int bottom,double t){int color=0;for(int shift:new int[]{16,8,0})color|=(int)Math.round(((top>>shift)&255)*(1-t)+((bottom>>shift)&255)*t)<<shift;return color;}
}
