package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.Fraction;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;

/** One independently scheduled, same-dimension source/destination pair. */
public final class CrystalRoute {
    public static final Codec<CrystalRoute> CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.STRING.fieldOf("id").forGetter(r->r.id),Codec.STRING.fieldOf("dimension").forGetter(r->r.dimension),Codec.LONG.fieldOf("source").forGetter(r->r.source),Codec.LONG.fieldOf("target").forGetter(r->r.target),
        Direction.CODEC.fieldOf("source_face").forGetter(r->r.sourceFace),Direction.CODEC.fieldOf("target_face").forGetter(r->r.targetFace),
        Codec.STRING.optionalFieldOf("time","0/1").forGetter(r->r.time.toString()),Codec.INT.optionalFieldOf("cursor",0).forGetter(r->r.cursor),Codec.LONG.optionalFieldOf("credit",0L).forGetter(r->r.credit),Codec.LONG.optionalFieldOf("cost",0L).forGetter(r->r.cost),Codec.LONG.optionalFieldOf("quota",0L).forGetter(r->r.quota)
    ).apply(i,(id,d,s,t,sf,tf,time,cursor,credit,cost,quota)->{var r=new CrystalRoute(id,d,s,t,sf,tf);r.time=Fraction.parse(time);r.cursor=cursor;r.credit=credit;r.cost=cost;r.quota=quota;return r;}));
    public final String id,dimension;public final long source,target;public final Direction sourceFace,targetFace;
    public long credit,cost,quota;
    public Fraction time=Fraction.ZERO;public int cursor;public String status="等待运输";
    public CrystalRoute(String dimension,long source,long target,Direction sourceFace,Direction targetFace){this(java.util.UUID.randomUUID().toString(),dimension,source,target,sourceFace,targetFace);}
    private CrystalRoute(String id,String dimension,long source,long target,Direction sourceFace,Direction targetFace){this.id=id;this.dimension=dimension;this.source=source;this.target=target;this.sourceFace=sourceFace;this.targetFace=targetFace;}
    public boolean same(CrystalRoute other){return dimension.equals(other.dimension)&&source==other.source&&target==other.target&&sourceFace==other.sourceFace&&targetFace==other.targetFace;}
}
