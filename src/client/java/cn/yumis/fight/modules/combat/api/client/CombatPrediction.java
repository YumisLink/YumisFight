package cn.yumis.fight.modules.combat.api.client;
import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.modules.combat.api.Runtime;
import com.google.gson.*;
import java.util.*;
/** Connection-scoped pending damage and exactly-once receipt retirement. */
public final class CombatPrediction {
    public interface State {
        boolean ready(); String session(); Life own(); Runtime resources(); long serverOffset(); long serverNow();
        CombatSettings combatSettings();
        cn.yumis.fight.modules.attributes.api.Stats stats();
        Life target(UUID id); net.minecraft.world.phys.Vec3 position(UUID id);
        default boolean consumesHealth(UUID id){return true;}
        default DamageAnalysis.Kind damageKind(UUID id){return DamageAnalysis.Kind.COMBAT;}
        void send(String action,JsonObject packet); void message(String text); void reported(JsonObject packet);
    }
    private final State state;
    private static final com.google.gson.Gson JSON=new com.google.gson.Gson();
    private long nextOperation=1,confirmedCombatUntil,confirmedShieldRecoveryAt;
    private final Map<Long,Pending> pending=new LinkedHashMap<>();
    private record AnalysisPending(DamageAnalysis.Ticket ticket,double maximum,boolean output,boolean taken){}
    private final Map<Long,AnalysisPending> analysisPending=new LinkedHashMap<>();
    private final ArrayList<DamageNumber> numbers=new ArrayList<>();
    private final List<DamageNumber> numberView=Collections.unmodifiableList(numbers);
    public CombatPrediction(State state){this.state=state;}
    public static long now(){return net.minecraft.util.Util.getMillis();}
    public int pendingCount(){return pending.size();}
    public Set<UUID> referencedTargets(){
        Set<UUID> targets=new HashSet<>();
        for(Pending value:pending.values())targets.add(UUID.fromString(value.report.get("target").getAsString()));
        return Set.copyOf(targets);
    }
    public List<DamageNumber> numbers(){return numberView;}
    public void clear(){pending.clear();analysisPending.clear();numbers.clear();confirmedCombatUntil=confirmedShieldRecoveryAt=0;}
    public void reset(boolean newSession){if(newSession)analysisPending.clear();else retainAnalysis();pending.clear();confirmedCombatUntil=state.resources().combatUntil;confirmedShieldRecoveryAt=state.resources().shieldRecoveryAt;if(newSession)nextOperation=1;}
    public void clearPending(){retainAnalysis();pending.clear();}
    private void retainAnalysis(){pending.forEach(this::retainAnalysis);}
    private void retainAnalysis(long operation,Pending value){if(value.analysis!=null)analysisPending.put(operation,new AnalysisPending(value.analysis,value.maximum,!value.report.get("target").getAsString().equals(state.own().target.toString()),value.report.has("damageTaken")&&value.report.get("damageTaken").getAsDouble()>0));}
    public void invalidate(UUID id){pending.entrySet().removeIf(e->{if(!e.getValue().report.get("target").getAsString().equals(id.toString()))return false;retainAnalysis(e.getKey(),e.getValue());return true;});}
    public void tick(){analysisPending.values().removeIf(value->!DamageAnalysis.current(value.ticket));for(Pending pending:pending.values())if(now()-pending.sent>=1000){pending.sent=now();state.send(pending.action,pending.report);}numbers.removeIf(number->number.expires()<now());}
    public class Pending {
        public JsonObject report;
        public String action;
        public long sent;
        public final long occurredAt;
        public final CombatSettings timing;
        public double maximum;
        public int color;
        public final boolean critical;
        public final DamageAnalysis.Ticket analysis;
        public final DamageNumber preview;
        public net.minecraft.world.phys.Vec3 point;
        Pending(JsonObject r,String a,double max,int color,boolean critical,DamageAnalysis.Source source){
            report=r;
            action=a;
            maximum=max;
            this.color=color;
            this.critical=critical;
            timing=state.combatSettings();
            var kind=state.damageKind(UUID.fromString(r.get("target").getAsString()));
            analysis=!a.equals("heal")&&!r.has("resourceCost")?DamageAnalysis.submit(source,kind,now(),state.combatSettings()):null;
            sent=now();
            occurredAt=sent;
            point=state.position(UUID.fromString(r.get("target").getAsString()));
            preview=kind==DamageAnalysis.Kind.TRAINING&&a.equals("damage")?new DamageNumber(r.get("fraction").getAsDouble()*max,now()+DamageNumber.LIFETIME_MILLIS,color,point,r.get("dimension").getAsString(),UUID.fromString(r.get("target").getAsString()),critical):null;
            if(preview!=null){if(numbers.size()>=96)numbers.removeFirst();numbers.add(preview);}
        }
    }
    public ElementAccumulation.State elemental(ElementAccumulation.State confirmed){ElementAccumulation.State value=confirmed;for(var p:pending.values())if(p.report.get("target").getAsString().equals(state.own().target.toString())&&p.report.has("elemental"))value=JSON.fromJson(p.report.get("elemental"),ElementAccumulation.State.class);return value;}
    public double predicted(Life life){
        if(!state.consumesHealth(life.target))return life.q;
        double q=life.q;
        for(Pending p:pending.values())if(p.report.get("target").getAsString().equals(life.target.toString())&&p.report.get("generation").getAsLong()==life.generation&&p.report.get("basis").getAsLong()==life.basis)q=Math.clamp(q+(p.action.equals("heal")?1:-1)*p.report.get("fraction").getAsDouble(),0,1);
        for(Pending p:pending.values())if(life.target.equals(state.own().target)&&p.report.has("ownerHealing"))q=Math.clamp(q+p.report.get("ownerHealing").getAsDouble(),0,1);
        return q;
    }
    public void report(Life life,double fraction,boolean heal,double maximum,int color,boolean critical,JsonObject extras){
        boolean outcomeOnly=!heal&&fraction==0&&extras!=null&&extras.has("outcome")&&(extras.has("nativeRoot")||extras.has("feedbackEntity"));
        boolean effectSettlement=!heal&&fraction==0&&extras!=null&&extras.has("effect")&&extras.has("effectKind")&&ElementAccumulation.kind(extras.get("effectKind").getAsString())!=null;
        if(!state.ready()||life.dead||fraction<0||fraction==0&&!outcomeOnly&&!effectSettlement||!Double.isFinite(fraction))return;
        boolean own=life.target.equals(state.own().target),cost=extras!=null&&extras.has("resourceCost");
        boolean contact=own?(fraction>0||extras!=null&&extras.has("damageTaken")&&extras.get("damageTaken").getAsDouble()>0):fraction>0&&predicted(life)>0&&(state.consumesHealth(life.target)||state.damageKind(life.target)!=DamageAnalysis.Kind.COMBAT);
        if(!heal&&!cost&&contact)EquipmentBuffs.combat(state.resources(),now());
        JsonObject j=new JsonObject();
        j.addProperty("session",state.session());
        j.addProperty("operation",nextOperation++);
        j.addProperty("sourceGeneration",state.own().generation);
        j.addProperty("dimension",life.dimension);
        j.addProperty("target",life.target.toString());
        j.addProperty("generation",life.generation);
        j.addProperty("basis",life.basis);
        j.addProperty("instance",life.instance);
        j.addProperty("fraction",fraction);
        j.addProperty("heal",heal);
        j.addProperty("occurredAt",state.serverNow());
        j.addProperty("combatDelayMillis",state.combatSettings().delayMillis());
        j.addProperty("shieldCooldownRecovery",state.stats().get(cn.yumis.fight.modules.attributes.api.Stat.SHIELD_COOLDOWN_RECOVERY));
        if(extras!=null)extras.entrySet().stream().filter(e->!e.getKey().equals(DamageAnalysis.LOCAL_SOURCE)).forEach(e->j.add(e.getKey(),e.getValue()));
        String action=heal?"heal":outcomeOnly?"nativeOutcome":"damage";
        pending.put(j.get("operation").getAsLong(),new Pending(j,action,maximum,color,critical,DamageAnalysis.source(extras)));
        state.own().dead=predicted(state.own())<=0;
        refreshDamageClocks();
        JsonObject runtime=new JsonObject();
        runtime.addProperty("session",state.session());
        runtime.addProperty("generation",state.own().generation);
        state.resources().recordedAt=now();
        runtime.addProperty("serverIssuedAt",state.serverNow());
        runtime.add("state",JSON.toJsonTree(state.resources()));
        j.add("runtime",runtime);
        state.reported(j);
        state.send(action,j);
    }
    private void refreshDamageClocks(){
        state.resources().combatUntil=confirmedCombatUntil;
        state.resources().shieldRecoveryAt=confirmedShieldRecoveryAt;
        for(Pending p:pending.values())if(!p.action.equals("heal")&&!p.report.has("resourceCost")){
            boolean hit=!p.report.has("outcome")||p.report.get("outcome").getAsString().equals("HIT");
            if(!hit)continue;
            boolean taken=p.report.get("target").getAsString().equals(state.own().target.toString());
            double damage=p.report.has("damageTaken")?p.report.get("damageTaken").getAsDouble():p.report.get("fraction").getAsDouble();
            if(damage>0){if(taken)CombatTiming.taken(state.resources(),p.occurredAt,p.timing,p.report.get("shieldCooldownRecovery").getAsDouble());else CombatTiming.dealt(state.resources(),p.occurredAt,p.timing);}
        }
    }
    public void ack(JsonObject j){
        if(!state.ready()||!state.session().equals(j.get("session").getAsString()))return;
        Pending p=pending.remove(j.get("operation").getAsLong());
        Receipt r=JSON.fromJson(j.get("receipt"),Receipt.class);
        if(p==null){var saved=analysisPending.remove(j.get("operation").getAsLong());if(saved!=null&&r.accepted()){if(saved.output&&r.actual()>0)DamageAnalysis.accepted(analysisTicket(saved.ticket,j),r.actual()*saved.maximum);else if(!saved.output&&(saved.taken||r.actual()>0))DamageAnalysis.combat(analysisTicket(saved.ticket,j));}return;}
        if(!r.accepted()){
            if(p.preview!=null)numbers.remove(p.preview);
            state.own().dead=predicted(state.own())<=0;refreshDamageClocks();
            if(!r.reason().isEmpty())state.message(r.reason());
            return;
        }
        if(j.has("ownerLife")){Life actor=JSON.fromJson(j.get("ownerLife"),Life.class);if(actor.generation==state.own().generation&&actor.revision>=state.own().revision){state.own().q=actor.q;state.own().revision=actor.revision;state.own().dead=actor.dead;state.own().elemental=actor.elemental;}}
        if(j.has("combatUntil"))confirmedCombatUntil=Math.max(confirmedCombatUntil,j.get("combatUntil").getAsLong()-state.serverOffset());
        if(j.has("shieldRecoveryAt"))confirmedShieldRecoveryAt=Math.max(confirmedShieldRecoveryAt,j.get("shieldRecoveryAt").getAsLong()-state.serverOffset());
        refreshDamageClocks();
        UUID id=UUID.fromString(j.get("target").getAsString());
        if(id.equals(state.own().target)&&(r.actual()>0||p.report.has("damageTaken")&&p.report.get("damageTaken").getAsDouble()>0))DamageAnalysis.combat(analysisTicket(p.analysis,j));
        Life l=id.equals(state.own().target)?state.own():state.target(id);
        if(l!=null&&j.get("generation").getAsLong()==l.generation&&r.revision()>=l.revision){
            l.q=r.q();
            l.revision=r.revision();
            l.dead=l.q<=0;
            if(j.has("elemental"))l.elemental=JSON.fromJson(j.get("elemental"),ElementAccumulation.State.class);
        }
        state.own().elemental=elemental(state.own().elemental);state.own().dead=predicted(state.own())<=0;
        if(r.actual()>0&&p.action.equals("damage")&&!id.equals(state.own().target)){
            DamageAnalysis.accepted(analysisTicket(p.analysis,j),r.actual()*p.maximum);
            if(p.preview==null){if(numbers.size()>=96)numbers.removeFirst();numbers.add(new DamageNumber(r.actual()*p.maximum,now()+DamageNumber.LIFETIME_MILLIS,p.color,p.point,p.report.get("dimension").getAsString(),id,p.critical));}
        }
    }
    private static DamageAnalysis.Ticket analysisTicket(DamageAnalysis.Ticket ticket,JsonObject ack){return ticket==null?null:new DamageAnalysis.Ticket(ticket.round(),ticket.source(),ticket.kind(),ticket.occurredAt(),ack.has("combatDelayMillis")?ack.get("combatDelayMillis").getAsLong():ticket.delayMillis());}
}
