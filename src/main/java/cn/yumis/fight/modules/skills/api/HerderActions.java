package cn.yumis.fight.modules.skills.api;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
/** Native entity hooks enter the skill-owned lifetime and receipt service. */
public final class HerderActions {
    public interface Handler {void tick(Herder entity);boolean incoming(Herder entity,ServerLevel level,DamageSource source,float points);}
    private static Handler handler=new Handler(){public void tick(Herder e){if(e.tickCount>20)e.discard();}public boolean incoming(Herder e,ServerLevel l,DamageSource s,float p){return false;}};
    private HerderActions(){}
    public static void install(Handler value){handler=java.util.Objects.requireNonNull(value);}
    public static void tick(Herder entity){handler.tick(entity);}
    public static boolean incoming(Herder entity,ServerLevel level,DamageSource source,float points){return handler.incoming(entity,level,source,points);}
    public static void clear(){handler=new Handler(){public void tick(Herder entity){entity.discard();}public boolean incoming(Herder entity,ServerLevel level,DamageSource source,float points){return false;}};}
}
