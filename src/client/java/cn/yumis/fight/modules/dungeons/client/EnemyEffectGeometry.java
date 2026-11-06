package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Small drawing primitives shared by this module's telegraphs and palace effects. */
final class EnemyEffectGeometry {
    static void disc(List<Quad> q,Vec3 c,double r,int color,int alpha){for(int i=0;i<40;i++){double a=i*Math.PI/20,b=(i+1)*Math.PI/20;q.add(new Quad(c,c.add(Math.cos(a)*r,0,Math.sin(a)*r),c.add(Math.cos(b)*r,0,Math.sin(b)*r),c,color,alpha));}}
    static void ring(List<Quad> q,Vec3 c,double radius,double width,int color,int alpha){for(int i=0;i<48;i++){double a=i*Math.PI/24,b=(i+1)*Math.PI/24,inner=Math.max(0,radius-width/2),outer=radius+width/2;q.add(new Quad(c.add(Math.cos(a)*outer,0,Math.sin(a)*outer),c.add(Math.cos(b)*outer,0,Math.sin(b)*outer),c.add(Math.cos(b)*inner,0,Math.sin(b)*inner),c.add(Math.cos(a)*inner,0,Math.sin(a)*inner),color,alpha));}}
    static void fan(List<Quad> q,Vec3 c,Vec3 direction,double radius,double half,int color,int alpha){double heading=Math.atan2(direction.z,direction.x);for(int i=0;i<32;i++){double a=heading-half+2*half*i/32,b=heading-half+2*half*(i+1)/32;Vec3 p=c.add(Math.cos(a)*radius,0,Math.sin(a)*radius),r=c.add(Math.cos(b)*radius,0,Math.sin(b)*radius);q.add(new Quad(c,p,r,c,color,alpha));line(q,p,r,.04,color,210);}line(q,c,c.add(Math.cos(heading-half)*radius,0,Math.sin(heading-half)*radius),.035,color,190);line(q,c,c.add(Math.cos(heading+half)*radius,0,Math.sin(heading+half)*radius),.035,color,190);}
    static void rect(List<Quad> q,Vec3 from,Vec3 to,double width,int color,int alpha){Vec3 d=to.subtract(from).multiply(1,0,1).normalize(),side=new Vec3(d.z,0,-d.x).scale(width/2);q.add(new Quad(from.add(side),to.add(side),to.subtract(side),from.subtract(side),color,alpha));line(q,from.add(side),to.add(side),.035,color,200);line(q,from.subtract(side),to.subtract(side),.035,color,200);line(q,to.add(side),to.subtract(side),.035,color,200);}
    static void line(List<Quad> q,Vec3 a,Vec3 b,double width,int color,int alpha){Vec3 d=b.subtract(a),side=d.cross(new Vec3(0,1,0));if(side.lengthSqr()<.0001)side=new Vec3(1,0,0);side=side.normalize().scale(width);q.add(new Quad(a.add(side),b.add(side),b.subtract(side),a.subtract(side),color,alpha));Vec3 other=d.cross(side);if(other.lengthSqr()<.0001)return;other=other.normalize().scale(width);q.add(new Quad(a.add(other),b.add(other),b.subtract(other),a.subtract(other),color,alpha));}
    static void box(List<Quad> q,Vec3 p,double x,double y,double z,int color,int alpha){Vec3[] v={p.add(-x,-y,-z),p.add(x,-y,-z),p.add(x,y,-z),p.add(-x,y,-z),p.add(-x,-y,z),p.add(x,-y,z),p.add(x,y,z),p.add(-x,y,z)};for(int[] f:new int[][]{{4,5,6,7},{1,0,3,2},{5,1,2,6},{0,4,7,3},{3,7,6,2},{0,1,5,4}})q.add(new Quad(v[f[0]],v[f[1]],v[f[2]],v[f[3]],color,alpha));}
    private EnemyEffectGeometry(){}
}
