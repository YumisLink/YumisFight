package cn.yumis.fight.modules.economy.api;
import java.util.*;

/** Connection-scoped authoritative balance and deduplicated, short-lived income feedback. */
public final class GoldFeedback {
    public record Gain(long amount,long started){}
    private final List<Gain> gains=new ArrayList<>();
    private long revision,animated;
    private boolean initialized;
    public long balance;
    public void clear(){balance=revision=animated=0;initialized=false;gains.clear();}
    public void snapshot(long balance,long revision){
        if(!initialized){initialized=true;this.balance=balance;this.revision=animated=revision;return;}
        if(revision>=this.revision){this.revision=revision;this.balance=balance;}
    }
    public void income(long amount,long balance,long revision,long now){
        if(!initialized||revision<=animated||amount<=0)return;
        snapshot(balance,revision);animated=revision;gains.removeIf(g->now-g.started>=450);
        if(!gains.isEmpty()&&now-gains.getLast().started<120){Gain last=gains.removeLast();gains.add(new Gain(Math.addExact(last.amount,amount),last.started));}
        else{if(gains.size()>=8)gains.removeFirst();gains.add(new Gain(amount,now));}
    }
    public List<Gain> gains(long now){gains.removeIf(g->now-g.started>=450);return List.copyOf(gains);}
}
