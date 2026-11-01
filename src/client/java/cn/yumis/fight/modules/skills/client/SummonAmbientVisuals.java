package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.List;
/** Small cosmetic embers and spirit glints. Body geometry belongs exclusively to the entity model. */
final class SummonAmbientVisuals {
    private SummonAmbientVisuals(){}
    static void extract(List<Quad> out){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        for(var raw:mc.level.entitiesForRendering())if(raw instanceof SummonedCreature e&&e.distanceToSqr(mc.player)<64*64){
            if(e.move().equals("death")||e.move().equals("retreat"))continue;
            double time=e.tickCount/20d;Vec3 p=e.position();
            switch(e.kind()){
                case FIRE->{for(int i=0;i<3;i++){double u=(time*.7+i*.31)%1;dot(out,p.add(Math.sin(i*2+time)*.13,.55+u*.27,Math.cos(i*2+time)*.13),.025,0xffd27a,(int)(180*(1-u)));}}
                case LIGHT->{for(int i=0;i<10;i++){double a=i*Math.PI/5+time*.7;dot(out,p.add(Math.cos(a)*.31,.34+Math.sin(a*2)*.045,Math.sin(a)*.16),.025,0xa1eaff,150);}dot(out,p.add(0,.35,0),.055,0xe1fbff,210);}
                case ICE->{double a=time*.8;dot(out,p.add(Math.cos(a)*.22,.53,Math.sin(a)*.22),.025,0xb6efff,155);}
                case DARK->{for(int i=0;i<2;i++){double a=time+i*Math.PI;dot(out,p.add(Math.sin(a)*.12,.65+i*.05,Math.cos(a)*.12),.025,0x78d5d5,150);}}
                case KING->{for(int i=0;i<4;i++){double a=time+i*Math.PI/2;dot(out,p.add(Math.sin(a)*.12,2.05+(time*.25+i*.15)%1*.28,Math.cos(a)*.1),.035,0x9beaff,160);}}
                default->{}
            }
        }
    }
    private static void dot(List<Quad> out,Vec3 p,double radius,int color,int alpha){
        out.add(new Quad(p.add(-radius,-radius,0),p.add(radius,-radius,0),p.add(radius,radius,0),p.add(-radius,radius,0),color,alpha));
        out.add(new Quad(p.add(0,-radius,-radius),p.add(0,-radius,radius),p.add(0,radius,radius),p.add(0,radius,-radius),color,alpha));
    }
}
