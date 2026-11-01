package cn.yumis.fight.modules.skills.api;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
/** Native entity hooks enter the skill-owned lifetime and receipt service. */
public final class SummonActions {
    public interface Handler {void tick(SummonedCreature entity);boolean incoming(SummonedCreature entity,ServerLevel level,DamageSource source,float points);}
    private static Handler handler=new Handler(){public void tick(SummonedCreature e){if(e.tickCount>20)e.discard();}public boolean incoming(SummonedCreature e,ServerLevel l,DamageSource s,float p){return false;}};
    private SummonActions(){}
    public static void install(Handler value){handler=java.util.Objects.requireNonNull(value);}
    public static void tick(SummonedCreature entity){handler.tick(entity);}
    public static boolean incoming(SummonedCreature entity,ServerLevel level,DamageSource source,float points){return handler.incoming(entity,level,source,points);}
    public static void clear(){handler=new Handler(){public void tick(SummonedCreature entity){entity.discard();}public boolean incoming(SummonedCreature entity,ServerLevel level,DamageSource source,float points){return false;}};}
}
