package cn.yumis.fight.modules.skills.api;
import cn.yumis.fight.modules.combat.api.Runtime;
import java.util.*;
import java.util.function.*;
/** Content-owned reduction handlers; no scheduling or character-specific dispatch. */
public final class SkillReductions {
    private record Handler(Consumer<CareerState> state,Consumer<Runtime> resources){}
    private static final Map<String,Handler> HANDLERS=new LinkedHashMap<>();
    private static void register(String id,Consumer<CareerState> state,Consumer<Runtime> resources){if(HANDLERS.putIfAbsent(id,new Handler(state,resources))!=null)throw new IllegalArgumentException("重复退级处理："+id);}
    static {
        register(SkillCatalog.OVERDRIVE.id,s->{},r->{r.buffs.remove(SkillCatalog.OVERDRIVE.id);r.absorption.remove("overdrivePower");r.absorptionUntil.remove("overdrivePower");});
        register(OracleSkills.WILL,s->s.oracle=new OracleState(),r->{});
        register(CareerSkills.FIELD,s->s.field=null,r->{});
        register(CareerSkills.FRENZY,s->s.frenzy=false,r->{});
        register(CareerSkills.BRANDS,s->{s.brands=0;s.nextBrand=System.currentTimeMillis()+5000;},r->{});
        register(CareerSkills.KALLA,s->s.kallaUntil=0,r->{});
        register(CareerSkills.EYE,s->{s.eyeUntil=0;s.eyeBonusMut=0;},r->{});
        register(CareerSkills.SWORD,s->{s.bloodUntil=0;s.blood=0;},r->{});
    }
    private SkillReductions(){}
    public static void apply(String id,CareerState state,Runtime resources){var handler=HANDLERS.get(id);if(handler!=null){handler.state.accept(state);handler.resources.accept(resources);}}
    public static void resources(String id,Runtime resources){var handler=HANDLERS.get(id);if(handler!=null)handler.resources.accept(resources);}
}
