package cn.yumis.fight.modules.dungeons.application;

import java.util.ArrayList;
import java.util.List;

/** 落雷凯诺的服务端演出时间表：纯数据、无 MC 依赖，由哥布林营地服务映射为原版粒子与音效；不含任何判定或数值。 */
record KenoShow(long at,String particle,int count,double x,double y,double z,double spread,double speed,String sound,float volume,float pitch){
    static final String SPARK="spark",FLASH="flash",ROD="rod",THUNDER="thunder",IMPACT="impact",SWEEP="sweep",BURST="burst",WARP="warp";
    /** 单次演出的粒子上限，防止大招或改数产生视觉噪声。 */
    static final int SPARK_CAP=80;

    /** 一次落雷命中：闪光照明、雷火花迸发与雷击音；grand 为大招规模（更大更低沉）。 */
    static List<KenoShow> lightning(long at,double x,double y,double z,double radius,boolean grand){
        List<KenoShow> out=new ArrayList<>(2);
        out.add(new KenoShow(at,FLASH,1,x,y+.6,z,0,0,null,0,0));
        int sparks=Math.min(SPARK_CAP,Math.round(24f+(float)radius*(grand?12:8)));
        out.add(new KenoShow(at,SPARK,sparks,x,y+.2,z,radius*.35,.09,IMPACT,grand?.9f:.7f,grand?1.1f:1.35f));
        return out;
    }

    /** 物理挥击（普攻／扇击／雷爪）：挥击音加少量雷火花；light 为普攻规模。 */
    static List<KenoShow> slash(long at,double x,double y,double z,boolean light){
        return List.of(new KenoShow(at,SPARK,light?8:14,x,y+.4,z,.5,.05,SWEEP,.8f,1.15f));
    }

    /** 雷环冲击波：主环爆发与 80ms 后的副环余波。 */
    static List<KenoShow> ring(long at,double x,double y,double z,double radius){
        int first=Math.min(SPARK_CAP,Math.round(18f+(float)radius*6));
        return List.of(
            new KenoShow(at,SPARK,first,x,y+.2,z,radius*.45,.12,BURST,.8f,.9f),
            new KenoShow(at+80,SPARK,first/2,x,y+.2,z,radius*.6,.06,null,0,0));
    }

    /** 雷闪位移：起终点电火花与传送音。 */
    static List<KenoShow> blink(long at,double fx,double fy,double fz,double tx,double ty,double tz){
        return List.of(
            new KenoShow(at,SPARK,20,fx,fy,fz,.6,.02,WARP,.4f,1.8f),
            new KenoShow(at,SPARK,20,tx,ty,tz,.6,.02,null,0,0));
    }

    /** 转阶段演出：周期电涌脉冲与终幕闪光雷声；P3 更急促、低沉并追加火花幕。 */
    static List<KenoShow> phase(long at,double x,double y,double z,int stage,long millis){
        List<KenoShow> out=new ArrayList<>();
        long step=stage>=3?300:450;
        for(long t=0;t<=millis-400;t+=step)out.add(new KenoShow(at+t,SPARK,12,x,y+.3,z,1.1,.05,null,0,0));
        out.add(new KenoShow(at+millis-100,FLASH,1,x,y+1,z,0,0,THUNDER,.55f,stage>=3?.85f:1.2f));
        if(stage>=3)out.add(new KenoShow(at+millis-40,SPARK,40,x,y+.5,z,1.6,.08,null,0,0));
        return out;
    }

    /** P2 召唤落场：出生点光柱迸发。 */
    static List<KenoShow> summon(long at,double x,double y,double z){
        return List.of(new KenoShow(at,ROD,12,x,y+.3,z,.45,.07,IMPACT,.5f,1.6f));
    }
}
