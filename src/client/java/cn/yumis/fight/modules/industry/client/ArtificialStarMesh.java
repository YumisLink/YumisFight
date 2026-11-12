package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.platform.ArtificialStarPartBlock;
import static cn.yumis.fight.modules.industry.platform.ArtificialStarPartBlock.sunCell;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import java.util.*;

/** A dark metal pedestal and warm emissive parts share one three-second breathing timeline. */
final class ArtificialStarMesh {
    private static final Identifier WHITE=Identifier.withDefaultNamespace("textures/block/white_concrete.png");
    private static final int SEGMENTS=32;
    private static final int STONE=0,GOLD=1,LAMP=2,STEEL=3,SHADOW=4,SOLAR=5;
    private static final double BREATH_TICKS=60;
    private record Face(float[] points,float nx,float ny,float nz,int material,int color){}
    private record Part(String name,int tint,int alpha,boolean emissive,boolean translucent,float y,float angle,float scale){}
    private static final Map<String,List<Face>> GEOMETRY=Map.of("base",base(),"lights",lights(),"field",field(),"rays",rays(),"sun",sun(),"halo",sphere(ArtificialStarPartBlock.SUN_RADIUS*1.04));
    private ArtificialStarMesh(){}
    private static List<Part> parts(double age,boolean active){
        Part base=new Part("base",0xffffff,255,false,false,0,0,1);
        if(!active)return List.of(base,new Part("lights",0x392315,255,false,false,0,0,1));
        float pulse=(float)((1-Math.cos(age*Math.PI*2/BREATH_TICKS))/2);
        int lamp=mix(0xb54209,0xffb93c,pulse),solar=mix(0xd5a153,0xfff3d4,pulse);
        float y=(float)(ArtificialStarPartBlock.SUN_Y+Math.sin(age*Math.PI*2/BREATH_TICKS)*.025),angle=(float)((age*.25)%360),scale=.985f+.015f*pulse;
        return List.of(base,new Part("lights",lamp,255,true,false,0,0,1),
            new Part("field",lamp,(int)(140+80*pulse),true,true,0,0,1),new Part("rays",lamp,(int)(18+24*pulse),true,true,0,0,1),
            new Part("sun",solar,255,true,false,y,angle,scale),new Part("halo",0xffb548,(int)(12+28*pulse),true,true,y,angle,scale));
    }
    static void submit(PoseStack pose,SubmitNodeCollector nodes,int light,float age,boolean active){
        for(Part part:parts(age,active)){
            pose.pushPose();pose.translate(.5,part.y(),.5);pose.mulPose(Axis.YP.rotationDegrees(part.angle()));pose.scale(part.scale(),part.scale(),part.scale());
            draw(GEOMETRY.get(part.name()),pose,nodes,part.emissive()?0xf000f0:light,part.tint(),part.alpha(),part.translucent());pose.popPose();
        }
    }
    private static void draw(List<Face> mesh,PoseStack pose,SubmitNodeCollector nodes,int light,int tint,int alpha,boolean translucent){
        nodes.submitCustomGeometry(pose,translucent?RenderTypes.entityTranslucentEmissive(WHITE):RenderTypes.entityCutout(WHITE),(matrix,vertices)->{
            for(Face face:mesh){
                int color=face.color(),red=((color>>16)&255)*((tint>>16)&255)/255,green=((color>>8)&255)*((tint>>8)&255)/255,blue=(color&255)*(tint&255)/255;
                for(int i=0;i<4;i++)vertices.addVertex(matrix,face.points()[i*3],face.points()[i*3+1],face.points()[i*3+2])
                    .setColor(red,green,blue,alpha).setUv(i==0||i==3?.015625f:.984375f,i<2?.984375f:.015625f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,face.nx(),face.ny(),face.nz());
            }
        });
    }
    private static List<Face> base(){
        List<Face> mesh=new ArrayList<>();
        cylinder(mesh,ArtificialStarPartBlock.BASE_RADIUS,0,.12,SHADOW);
        cylinder(mesh,2.4,.12,.18,GOLD);cylinder(mesh,2.34,.18,.66,STONE);
        cylinder(mesh,2.4,.66,.76,STEEL);cylinder(mesh,2.18,.76,.82,STONE);
        ring(mesh,2.25,2.34,.765,GOLD);ring(mesh,1.6,1.82,.825,SHADOW);
        cylinder(mesh,1.5,.82,.88,STEEL);cylinder(mesh,1.36,.88,ArtificialStarPartBlock.BASE_HEIGHT,STONE);
        // Eight broad clamps and inset seams match the segmented concept pedestal.
        for(int clamp=0;clamp<8;clamp++){
            double a=clamp*Math.PI/4;
            box(mesh,2.04,.15,-.23,2.43,.77,.23,a,STONE);box(mesh,2.0,.76,-.27,2.44,.9,.27,a,GOLD);
            box(mesh,2.42,.22,-.16,2.44,.69,-.12,a,GOLD);box(mesh,2.42,.22,.12,2.44,.69,.16,a,GOLD);
            box(mesh,2.432,.31,-.035,2.446,.36,.035,a,STEEL);box(mesh,2.432,.57,-.035,2.446,.62,.035,a,STEEL);
            double start=a+.17,end=a+Math.PI/4-.17;
            arcWall(mesh,2.345,.28,.57,start,end,SHADOW);
        }
        for(int bar=0;bar<4;bar++){
            double a=Math.PI/4+bar*Math.PI/2;
            box(mesh,1.38,.821,-.14,2.01,.85,.14,a,GOLD);box(mesh,1.43,.85,-.095,1.97,.86,.095,a,SHADOW);
        }
        box(mesh,-.35,.9,-.35,.35,1.025,.35,0,GOLD);box(mesh,-.27,1.025,-.27,.27,1.05,.27,0,SHADOW);
        return List.copyOf(mesh);
    }
    private static List<Face> lights(){
        List<Face> mesh=new ArrayList<>();
        for(int sector=0;sector<8;sector++){
            double a=sector*Math.PI/4,start=a+.19,end=a+Math.PI/4-.19;
            arcWall(mesh,2.35,.34,.49,start,end,LAMP);arcRing(mesh,1.66,1.76,.834,start,end,LAMP);
        }
        for(int bar=0;bar<4;bar++)box(mesh,1.46,.861,-.07,1.94,.866,.07,Math.PI/4+bar*Math.PI/2,LAMP);
        box(mesh,-.2,1.05,-.2,.2,1.065,.2,0,LAMP);return List.copyOf(mesh);
    }
    private static List<Face> field(){List<Face> mesh=new ArrayList<>();ring(mesh,1.72,1.77,1.3,LAMP);wall(mesh,1.77,1.275,1.3,LAMP);return List.copyOf(mesh);}
    private static List<Face> rays(){
        List<Face> mesh=new ArrayList<>();for(int ray=0;ray<8;ray++){
            double a=ray*Math.PI/4+.2,co=Math.cos(a),si=Math.sin(a);
            double[] A=point(1.18,.97,a-.009),B=point(1.18,1.73,a-.009),C=point(1.18,1.73,a+.009),D=point(1.18,.97,a+.009);
            add(mesh,A,B,C,D,co,0,si,LAMP,0xffffff);add(mesh,D,C,B,A,-co,0,-si,LAMP,0xffffff);
        }return List.copyOf(mesh);
    }
    private static void arcWall(List<Face> mesh,double radius,double y0,double y1,double start,double end,int material){
        int steps=Math.max(1,(int)Math.ceil((end-start)*SEGMENTS/(Math.PI*2)));
        for(int i=0;i<steps;i++){double a=start+(end-start)*i/steps,b=start+(end-start)*(i+1)/steps,mid=(a+b)/2;
            add(mesh,point(radius,y0,a),point(radius,y1,a),point(radius,y1,b),point(radius,y0,b),Math.cos(mid),0,Math.sin(mid),material,0xffffff);}
    }
    private static void arcRing(List<Face> mesh,double inner,double outer,double y,double start,double end,int material){
        int steps=Math.max(1,(int)Math.ceil((end-start)*SEGMENTS/(Math.PI*2)));
        for(int i=0;i<steps;i++){double a=start+(end-start)*i/steps,b=start+(end-start)*(i+1)/steps;
            add(mesh,point(inner,y,a),point(inner,y,b),point(outer,y,b),point(outer,y,a),0,1,0,material,0xffffff);}
    }
    private static void cylinder(List<Face> mesh,double radius,double y0,double y1,int material){wall(mesh,radius,y0,y1,material);ring(mesh,0,radius,y1,material);}
    private static void wall(List<Face> mesh,double radius,double y0,double y1,int material){
        for(int segment=0;segment<SEGMENTS;segment++){
            double a=segment*Math.PI*2/SEGMENTS,b=(segment+1)*Math.PI*2/SEGMENTS,mid=(a+b)/2;
            add(mesh,point(radius,y0,a),point(radius,y1,a),point(radius,y1,b),point(radius,y0,b),Math.cos(mid),0,Math.sin(mid),material,0xffffff);
        }
    }
    private static void ring(List<Face> mesh,double inner,double outer,double y,int material){
        for(int segment=0;segment<SEGMENTS;segment++){
            double a=segment*Math.PI*2/SEGMENTS,b=(segment+1)*Math.PI*2/SEGMENTS;
            double[] A=point(inner,y,a),B=point(inner,y,b),C=point(outer,y,b),D=point(outer,y,a);
            add(mesh,A,B,C,D,0,1,0,material,0xffffff);add(mesh,D,C,B,A,0,-1,0,material,0xffffff);
        }
    }
    private static List<Face> sphere(double radius){
        List<Face> mesh=new ArrayList<>();int bands=16,segments=32;
        for(int band=0;band<bands;band++)for(int segment=0;segment<segments;segment++){
            double a=segment*Math.PI*2/segments,b=(segment+1)*Math.PI*2/segments,lo=-Math.PI/2+band*Math.PI/bands,hi=-Math.PI/2+(band+1)*Math.PI/bands;
            double phi=(lo+hi)/2,theta=(a+b)/2;
            int color=(segment*17+band*11)%13<3?0xffb65b:(segment+band)%7==0?0xfff5ce:0xffda91;
            add(mesh,spherical(radius,lo,a),spherical(radius,hi,a),spherical(radius,hi,b),spherical(radius,lo,b),Math.cos(phi)*Math.cos(theta),Math.sin(phi),Math.cos(phi)*Math.sin(theta),SOLAR,color);
        }
        return List.copyOf(mesh);
    }
    private static List<Face> sun(){
        List<Face> mesh=new ArrayList<>();int steps=ArtificialStarPartBlock.SUN_STEPS;double unit=ArtificialStarPartBlock.SUN_CELL;
        for(int y=0;y<steps;y++)for(int z=0;z<steps;z++)for(int x=0;x<steps;x++){
            if(!sunCell(x,y,z))continue;
            double X=(x-steps/2d)*unit,Y=(y-steps/2d)*unit,Z=(z-steps/2d)*unit;
            int color=(x*17+y*11+z*7)%13<3?0xff9c27:(x+y+z)%7==0?0xffef9a:0xffca4b;
            double[] a={X,Y,Z},b={X+unit,Y,Z},c={X+unit,Y+unit,Z},d={X,Y+unit,Z},
                e={X,Y,Z+unit},f={X+unit,Y,Z+unit},g={X+unit,Y+unit,Z+unit},h={X,Y+unit,Z+unit};
            if(!sunCell(x-1,y,z))add(mesh,e,h,d,a,-1,0,0,SOLAR,color);
            if(!sunCell(x+1,y,z))add(mesh,b,c,g,f,1,0,0,SOLAR,color);
            if(!sunCell(x,y-1,z))add(mesh,a,b,f,e,0,-1,0,SOLAR,color);
            if(!sunCell(x,y+1,z))add(mesh,h,g,c,d,0,1,0,SOLAR,color);
            if(!sunCell(x,y,z-1))add(mesh,a,d,c,b,0,0,-1,SOLAR,color);
            if(!sunCell(x,y,z+1))add(mesh,f,g,h,e,0,0,1,SOLAR,color);
        }
        return List.copyOf(mesh);
    }
    private static void box(List<Face> mesh,double x0,double y0,double z0,double x1,double y1,double z1,double angle,int material){
        double[] a=rotated(x0,y0,z0,angle),b=rotated(x1,y0,z0,angle),c=rotated(x1,y1,z0,angle),d=rotated(x0,y1,z0,angle),
            e=rotated(x0,y0,z1,angle),f=rotated(x1,y0,z1,angle),g=rotated(x1,y1,z1,angle),h=rotated(x0,y1,z1,angle);
        double co=Math.cos(angle),si=Math.sin(angle);
        add(mesh,e,h,d,a,-co,0,-si,material,0xffffff);add(mesh,b,c,g,f,co,0,si,material,0xffffff);
        add(mesh,a,b,f,e,0,-1,0,material,0xffffff);add(mesh,h,g,c,d,0,1,0,material,0xffffff);
        add(mesh,a,d,c,b,si,0,-co,material,0xffffff);add(mesh,f,g,h,e,-si,0,co,material,0xffffff);
    }
    private static double[] rotated(double x,double y,double z,double angle){return new double[]{x*Math.cos(angle)-z*Math.sin(angle),y,x*Math.sin(angle)+z*Math.cos(angle)};}
    private static double[] point(double radius,double y,double angle){return new double[]{Math.cos(angle)*radius,y,Math.sin(angle)*radius};}
    private static double[] spherical(double radius,double phi,double theta){return point(radius*Math.cos(phi),radius*Math.sin(phi),theta);}
    private static int palette(int material){return switch(material){case STONE->0x393b40;case GOLD->0xe2b457;case STEEL->0x62656c;case SHADOW->0x20232a;default->0xffffff;};}
    private static int mix(int low,int high,float amount){int r=(int)(((low>>16)&255)*(1-amount)+((high>>16)&255)*amount),g=(int)(((low>>8)&255)*(1-amount)+((high>>8)&255)*amount),b=(int)((low&255)*(1-amount)+(high&255)*amount);return r<<16|g<<8|b;}
    private static void add(List<Face> mesh,double[] a,double[] b,double[] c,double[] d,double nx,double ny,double nz,int material,int color){
        int paint=palette(material);color=(((color>>16)&255)*((paint>>16)&255)/255)<<16|(((color>>8)&255)*((paint>>8)&255)/255)<<8|(color&255)*(paint&255)/255;
        mesh.add(new Face(new float[]{(float)a[0],(float)a[1],(float)a[2],(float)b[0],(float)b[1],(float)b[2],(float)c[0],(float)c[1],(float)c[2],(float)d[0],(float)d[1],(float)d[2]},(float)nx,(float)ny,(float)nz,material,color));
    }
}
