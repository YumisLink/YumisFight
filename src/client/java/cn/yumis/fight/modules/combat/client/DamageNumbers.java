package cn.yumis.fight.modules.combat.client;

import cn.yumis.fight.modules.combat.api.client.DamageNumber;
import cn.yumis.fight.modules.presentation.api.client.ClientSettings;
import cn.yumis.fight.modules.presentation.api.client.NumberText;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.*;

/** Screen-space hit stacks, rebuilt from live presentation records without retaining session state. */
public final class DamageNumbers {
    private static final int MARGIN=4,GAP=4,CRITICAL_OUTLINE=0xffd45a;
    private record Entry(DamageNumber number,String text,float scale,int width,int height,double age,int alpha){}
    private record Placement(Entry entry,int left,int top){}
    private DamageNumbers(){}

    public static void render(GuiGraphicsExtractor g,Minecraft mc,long now,String dimension,List<DamageNumber> numbers){
        var font=UiFonts.font();
        var ordered=new ArrayList<DamageNumber>();
        // Reverse insertion order also keeps hits received in the same millisecond newest-first.
        for(int i=numbers.size()-1;i>=0;i--){
            var number=numbers.get(i);
            if(number.expires()>now&&number.dimension().equals(dimension))ordered.add(number);
        }
        ordered.sort(Comparator.comparingLong(DamageNumber::expires).reversed());
        Map<UUID,List<Entry>> groups=new LinkedHashMap<>();
        for(var number:ordered){
            double age=Math.clamp(1-(number.expires()-now)/(double)DamageNumber.LIFETIME_MILLIS,0,1);
            int alpha=(int)(255*Math.min(1,(1-age)*4));
            if(alpha<4)continue;
            String text=NumberText.damage(number.amount())+(number.critical()?"!":"");
            float scale=ClientSettings.damageScale*(number.critical()?1.5f:1f);
            // Include a font pixel on every side for the outline or regular drop shadow.
            var entry=new Entry(number,text,scale,(int)Math.ceil((font.width(text)+2)*scale),(int)Math.ceil((font.lineHeight+2)*scale),age,alpha);
            groups.computeIfAbsent(number.target(),key->new ArrayList<>()).add(entry);
        }
        List<Placement> placed=new ArrayList<>();
        for(var group:groups.values())layout(g,mc,group,placed);
        // Newer numbers keep visual priority if a glyph extends beyond its measured font cell.
        for(int i=placed.size()-1;i>=0;i--)draw(g,font,placed.get(i));
    }

    private static void layout(GuiGraphicsExtractor g,Minecraft mc,List<Entry> entries,List<Placement> placed){
        var camera=mc.gameRenderer.mainCamera();
        var point=entries.getFirst().number.position();
        var relative=point.subtract(camera.position());var forward=camera.forwardVector();
        if(relative.x*forward.x()+relative.y*forward.y()+relative.z*forward.z()<=0)return;
        var screen=mc.gameRenderer.projectPointToScreen(point);
        if(!Double.isFinite(screen.x)||!Double.isFinite(screen.y)||Math.abs(screen.x)>1||Math.abs(screen.y)>1)return;
        int width=entries.stream().mapToInt(Entry::width).max().orElse(1);
        int height=entries.stream().mapToInt(Entry::height).max().orElse(1);
        int anchorX=(int)Math.round((screen.x+1)*.5*g.guiWidth());
        if(width<=g.guiWidth()-MARGIN*2)anchorX=Math.clamp(anchorX,MARGIN+width/2,g.guiWidth()-MARGIN-(width+1)/2);
        else anchorX=g.guiWidth()/2;
        int anchorY=Math.clamp((int)Math.round((1-screen.y)*.5*g.guiHeight()),Math.min(MARGIN+height,g.guiHeight()-MARGIN),g.guiHeight()-MARGIN);
        int columnHeight=Math.max(height,Math.min(180,(int)(g.guiHeight()*.55)));
        int topLimit=Math.max(MARGIN,anchorY-columnHeight);
        int step=width+GAP,direction=anchorX<=g.guiWidth()/2?1:-1;
        List<Integer> columns=new ArrayList<>();
        columns.add(anchorX);
        for(int index=1;index<=2*(g.guiWidth()/step+1);index++){
            int offset=(index+1)/2*(index%2==1?direction:-direction);
            int center=anchorX+offset*step;
            if(center-width/2>=MARGIN&&center+(width+1)/2<=g.guiWidth()-MARGIN)columns.add(center);
        }
        int column=0,bottom=anchorY;
        for(var entry:entries){
            while(column<columns.size()){
                int left=columns.get(column)-entry.width/2;
                int risingBottom=Math.max(topLimit+entry.height,anchorY-(int)Math.round(entry.age*24));
                int top=Math.min(bottom,risingBottom)-entry.height;
                top=avoid(left,top,entry.width,entry.height,placed);
                if(top>=topLimit){
                    placed.add(new Placement(entry,left,top));
                    bottom=top-GAP;
                    break;
                }
                column++;
                bottom=anchorY;
            }
            // The retained hit is still available next frame; finite screens favor the recent rows.
            if(column>=columns.size())break;
        }
    }

    private static int avoid(int left,int top,int width,int height,List<Placement> placed){
        boolean moved;
        do{
            moved=false;
            for(var other:placed){
                if(left<other.left+other.entry.width+GAP&&left+width+GAP>other.left
                    &&top<other.top+other.entry.height+GAP&&top+height+GAP>other.top){
                    top=other.top-height-GAP;
                    moved=true;
                }
            }
        }while(moved);
        return top;
    }

    private static void draw(GuiGraphicsExtractor g,Font font,Placement placement){
        var entry=placement.entry;
        g.pose().pushMatrix();
        try{
            g.pose().translate(placement.left+entry.scale,placement.top+entry.scale);
            g.pose().scale(entry.scale,entry.scale);
            if(entry.number.critical()){
                int outline=(entry.alpha<<24)|CRITICAL_OUTLINE;
                for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)if(x!=0||y!=0)g.text(font,entry.text,x,y,outline,false);
            }
            g.text(font,entry.text,0,0,(entry.alpha<<24)|(entry.number.color()&0xffffff),!entry.number.critical());
        }finally{g.pose().popMatrix();}
    }
}
