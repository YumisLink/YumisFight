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
        Life target(UUID id); net.minecraft.world.phys.Vec3 position(UUID id);
        default boolean consumesHealth(UUID id){return true;}
        default boolean training(UUID id){return false;}
        default boolean networkTraining(UUID id){return false;}
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
        public net.minecraft.world.phys.Vec3 point;
        Pending(JsonObject r,String a,double max,int color,boolean critical,DamageAnalysis.Source source){
            report=r;
            action=a;
            maximum=max;
            this.color=color;
            this.critical=critical;
            timing=state.combatSettings();
            analysis=!a.equals("heal")?DamageAnalysis.submit(source,state.networkTraining(UUID.fromString(r.get("target").getAsString()))?DamageAnalysis.Kind.TIMED_TRAINING:DamageAnalysis.Kind.COMBAT,now(),state.combatSettings()):null;
            sent=now();
            occurredAt=sent;
            point=state.position(UUID.fromString(r.get("target").getAsString()));
        }
    }
    public double predicted(Life life){
        if(!state.consumesHealth(life.target))return life.q;
        double q=life.q;
        for(Pending p:pending.values())if(p.report.get("target").getAsString().equals(life.target.toString())&&p.report.get("generation").getAsLong()==life.generation&&p.report.get("basis").getAsLong()==life.basis)q=Math.clamp(q+(p.action.equals("heal")?1:-1)*p.report.get("fraction").getAsDouble(),0,1);
        return q;
    }
    public void report(Life life,double fraction,boolean heal,double maximum,int color,boolean critical,JsonObject extras){
        if(state.training(life.target))return;
        boolean outcomeOnly=!heal&&fraction==0&&extras!=null&&extras.has("outcome")&&(extras.has("nativeRoot")||extras.has("feedbackEntity"));
        if(!state.ready()||life.dead||fraction<0||fraction==0&&!outcomeOnly||!Double.isFinite(fraction))return;
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
        if(extras!=null)extras.entrySet().stream().filter(e->!e.getKey().equals(DamageAnalysis.LOCAL_SOURCE)).forEach(e->j.add(e.getKey(),e.getValue()));
        String action=heal?"heal":outcomeOnly?"nativeOutcome":"damage";
        pending.put(j.get("operation").getAsLong(),new Pending(j,action,maximum,color,critical,DamageAnalysis.source(extras)));
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
        for(Pending p:pending.values())if(!p.action.equals("heal")){
            boolean hit=!p.report.has("outcome")||p.report.get("outcome").getAsString().equals("HIT");
            if(!hit)continue;
            boolean taken=p.report.get("target").getAsString().equals(state.own().target.toString());
            double damage=p.report.has("damageTaken")?p.report.get("damageTaken").getAsDouble():p.report.get("fraction").getAsDouble();
            if(damage>0){if(taken)CombatTiming.taken(state.resources(),p.occurredAt,p.timing);else CombatTiming.dealt(state.resources(),p.occurredAt,p.timing);}
        }
    }
    public void ack(JsonObject j){
        if(!state.ready()||!state.session().equals(j.get("session").getAsString()))return;
        Pending p=pending.remove(j.get("operation").getAsLong());
        Receipt r=JSON.fromJson(j.get("receipt"),Receipt.class);
        if(p==null){var saved=analysisPending.remove(j.get("operation").getAsLong());if(saved!=null&&r.accepted()){if(saved.output&&r.actual()>0)DamageAnalysis.accepted(analysisTicket(saved.ticket,j),r.actual()*saved.maximum);else if(!saved.output&&(saved.taken||r.actual()>0))DamageAnalysis.combat(analysisTicket(saved.ticket,j));}return;}
        if(!r.accepted()){
            refreshDamageClocks();
            if(!r.reason().isEmpty())state.message(r.reason());
            return;
        }
        if(j.has("ownerLife")){Life actor=JSON.fromJson(j.get("ownerLife"),Life.class);if(actor.generation==state.own().generation&&actor.revision>=state.own().revision){state.own().q=actor.q;state.own().revision=actor.revision;state.own().dead=actor.dead;}}
        if(j.has("equipmentBuffs")){var type=new com.google.gson.reflect.TypeToken<java.util.Map<String,cn.yumis.fight.modules.combat.api.EquipmentBuffs.State>>(){}.getType();java.util.Map<String,cn.yumis.fight.modules.combat.api.EquipmentBuffs.State> buffs=JSON.fromJson(j.get("equipmentBuffs"),type);long delta=-state.serverOffset();for(var buff:buffs.values())buff.expires+=delta;state.resources().equipmentBuffs=buffs;}
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
        }
        if(r.actual()>0&&p.action.equals("damage")&&!id.equals(state.own().target)){
            DamageAnalysis.accepted(analysisTicket(p.analysis,j),r.actual()*p.maximum);
            if(numbers.size()>=96)numbers.removeFirst();
            numbers.add(new DamageNumber(r.actual()*p.maximum,now()+DamageNumber.LIFETIME_MILLIS,p.color,p.point,p.report.get("dimension").getAsString(),id,p.critical));
        }
    }
    private static DamageAnalysis.Ticket analysisTicket(DamageAnalysis.Ticket ticket,JsonObject ack){return ticket==null?null:new DamageAnalysis.Ticket(ticket.round(),ticket.source(),ticket.kind(),ticket.occurredAt(),ack.get("combatDelayMillis").getAsLong());}
}
