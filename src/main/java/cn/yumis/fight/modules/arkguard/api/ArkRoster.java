package cn.yumis.fight.modules.arkguard.api;

import cn.yumis.fight.modules.economy.api.CurrencyRules;
import com.google.gson.Gson;
import java.util.*;

/** Personal decisions run on the owner's client. The scene receives their complete resulting facts. */
public final class ArkRoster {
    private static final Gson JSON=new Gson();
    public String owner="",round="",currency=CurrencyRules.ARK_COIN.id(),carrying="";
    public long revision,nextOrder;
    public int wave=1,level=1,coins=4,lastUpgradeWave=1;
    public List<Card> cards=new ArrayList<>();
    public List<String> shop=new ArrayList<>();
    public Map<String,Integer> pool=new LinkedHashMap<>();
    public List<Future> future=new ArrayList<>();
    public static final class Card {
        public String token="",unit="";
        public int star=1,bench=-1,x,y,z,facing=-1;
        public long order,placedAt;
        public boolean deployed,held;
        public boolean onField(){return deployed&&!held;}
        public float yaw(float fallback){return facing<0?fallback:facing*90f;}
        public int copies(){return switch(star){case 1->1;case 2->3;case 3->9;default->throw new IllegalArgumentException("星级无效");};}
    }
    private record Position(int x,int y,int z,int bench){
        Position(Card card){this(card.x,card.y,card.z,card.bench);}
    }
    /** Conditional future facts are prepared by the owner; the host only selects the reached wave. */
    public static final class Future {
        public int wave,coins;
        public List<String> shop;
        public Map<String,Integer> pool;
    }
    public static ArkRoster create(String owner,String round,ArkRules rules,Random random){
        var roster=new ArkRoster();roster.owner=owner;roster.round=round;
        for(var unit:rules.units)roster.pool.put(unit.id(),rules.cardsPerUnit);
        for(int i=0;i<rules.shopSlots(roster.level);i++)roster.shop.add("");
        roster.refresh(rules,random,false);roster.plan(rules,random);return roster;
    }
    public ArkRoster copy(){return JSON.fromJson(JSON.toJson(this),ArkRoster.class);}
    public Card card(String token){return cards.stream().filter(c->c.token.equals(token)).findFirst().orElseThrow(()->new IllegalArgumentException("角色已变化"));}
    public int population(){return (int)cards.stream().filter(Card::onField).count();}
    public boolean owns(String unit){return cards.stream().anyMatch(c->c.unit.equals(unit));}
    public int upgradePrice(ArkRules rules){return level>=rules.maximumLevel?0:Math.max(0,rules.upgradeCosts[level-1]-(wave-lastUpgradeWave));}
    public void upgrade(ArkRules rules,Random random){
        if(level>=rules.maximumLevel)throw new IllegalArgumentException("商店已经满级");
        int cost=upgradePrice(rules);pay(cost);level++;lastUpgradeWave=wave;
        while(shop.size()<rules.shopSlots(level))shop.add(draw(rules,random));
    }
    public void refresh(ArkRules rules,Random random,boolean paid){
        if(paid)pay(rules.refreshCost);
        for(String id:shop)if(!id.isBlank())pool.merge(id,1,Integer::sum);
        shop.clear();
        for(int i=0;i<rules.shopSlots(level);i++)shop.add(draw(rules,random));
    }
    private String draw(ArkRules rules,Random random){
        double total=0;double[] weights=new double[6];
        for(int tier=1;tier<=6;tier++){
            final int selected=tier;
            if(rules.units.stream().anyMatch(u->u.tier()==selected&&pool.getOrDefault(u.id(),0)>0))
                total+=weights[tier-1]=rules.shopProbabilities[level-1][tier-1];
        }
        if(total==0)return "";
        double roll=random.nextDouble()*total;int tier=1;
        for(;tier<6;tier++){if(roll<weights[tier-1])break;roll-=weights[tier-1];}
        int selected=tier,available=rules.units.stream().filter(u->u.tier()==selected).mapToInt(u->pool.getOrDefault(u.id(),0)).sum();
        if(available<=0)return "";
        int pick=random.nextInt(available);String chosen="";
        for(var unit:rules.units)if(unit.tier()==tier){int count=pool.getOrDefault(unit.id(),0);if(pick<count){chosen=unit.id();break;}pick-=count;}
        if(chosen.isBlank())throw new IllegalStateException("方舟卡池为空");
        pool.put(chosen,pool.get(chosen)-1);return chosen;
    }
    public void buy(int slot,int benches,ArkRules rules){
        if(slot<0||slot>=shop.size()||shop.get(slot).isBlank())throw new IllegalArgumentException("商品位置为空");
        String id=shop.get(slot);var unit=rules.unit(id);pay(unit.cost());
        var card=new Card();card.token=UUID.randomUUID().toString();card.unit=id;card.order=++nextOrder;
        card.bench=freeBench(benches);cards.add(card);shop.set(slot,"");merge();
        if(cards.stream().anyMatch(c->!c.deployed&&!c.held&&c.bench<0))throw new IllegalArgumentException("备战区已满，且不能立即合成");
    }
    private int freeBench(int capacity){for(int slot=0;slot<capacity;slot++){final int s=slot;if(cards.stream().noneMatch(c->c.bench==s))return slot;}return -1;}
    private void merge(){
        boolean changed;
        do{
            changed=false;
            outer:for(Card candidate:List.copyOf(cards)){
                if(candidate.star>=3)continue;
                List<Card> same=cards.stream().filter(c->c.unit.equals(candidate.unit)&&c.star==candidate.star)
                    .sorted(Comparator.<Card>comparingInt(c->c.deployed&&!c.held?0:1)
                        .thenComparingLong(c->c.deployed&&!c.held?c.placedAt:c.order)).limit(3).toList();
                if(same.size()!=3)continue;
                Card keep=same.getFirst();keep.star++;
                for(int i=1;i<3;i++){Card consumed=same.get(i);cards.remove(consumed);if(carrying.equals(consumed.token))carrying="";}
                changed=true;break outer;
            }
        }while(changed);
    }
    public void sell(String token,ArkRules rules){
        Card c=card(token);var unit=rules.unit(c.unit);
        coins+=unit.parameters().containsKey("sale")?(int)unit.p("sale"):c.copies();
        pool.merge(c.unit,c.copies(),Integer::sum);cards.remove(c);if(carrying.equals(token))carrying="";
    }
    public void pick(String token){
        if(!carrying.isBlank())throw new IllegalArgumentException("请先放下抱着的角色");
        Card c=card(token);c.held=true;carrying=token;
    }
    /** Coordinates are support blocks. Geometry/terrain is checked by the owner's interaction before calling this. */
    public void place(int x,int y,int z,int bench,float yaw,ArkRules rules){
        Card c=heldCard();
        if(bench>=0){
            if(cards.stream().anyMatch(other->other!=c&&!other.held&&!other.deployed&&other.bench==bench))throw new IllegalArgumentException("该备战位已经有角色");
        }else{
            if(population()>=rules.populations[level-1])throw new IllegalArgumentException("上场人口已满");
            if(cards.stream().anyMatch(other->other!=c&&other.onField()&&other.x==x&&other.y==y&&other.z==z))throw new IllegalArgumentException("该布阵格已经有角色");
        }
        put(c,new Position(x,y,z,bench),false);
        c.facing=cardinalFacing(yaw);
        carrying="";
    }
    public void swap(String token,float yaw){
        Card held=heldCard(),other=card(token);
        if(held==other||other.held)throw new IllegalArgumentException("请选择另一个已放置的角色");
        Position original=new Position(held);
        put(held,new Position(other),true);
        put(other,original,true);
        held.facing=cardinalFacing(yaw);
        carrying="";
    }
    private Card heldCard(){
        if(carrying.isBlank())throw new IllegalArgumentException("当前没有抱起的角色");
        return card(carrying);
    }
    private void put(Card card,Position position,boolean keepOrder){
        boolean alreadyOnField=card.deployed;
        card.x=position.x();
        card.y=position.y();
        card.z=position.z();
        card.bench=position.bench();
        card.deployed=position.bench()<0;
        if(!card.deployed)card.placedAt=0;
        else if(!keepOrder||!alreadyOnField)card.placedAt=++nextOrder;
        card.held=false;
    }
    public static int cardinalFacing(float yaw){return Math.floorMod(Math.round(yaw/90f),4);}
    public Card inFrontOf(Card origin){
        if(!origin.onField()||origin.facing<0)return null;
        int x=origin.x+(origin.facing==3?1:origin.facing==1?-1:0);
        int z=origin.z+(origin.facing==0?1:origin.facing==2?-1:0);
        return cards.stream().filter(c->c.onField()&&c.x==x&&c.y==origin.y&&c.z==z).findFirst().orElse(null);
    }
    private void pay(int amount){if(amount<0||coins<amount)throw new IllegalArgumentException("方舟币不足");coins-=amount;}
    public static int basicIncome(int wave){return Math.min(12,wave+3);}
    public int endIncome(ArkRules rules){return cards.stream().filter(c->c.deployed&&!c.held&&c.unit.equals("ark_yunyimu")).mapToInt(c->(int)rules.unit(c.unit).s("income",c.star)).sum();}
    public int fortune(ArkRules rules){return Math.min(3,ArkTraits.counts(this,rules).getOrDefault("fortune",0));}
    public void plan(ArkRules rules,Random random){
        future.clear();var projected=copy();projected.future.clear();
        int extra=endIncome(rules),fortune=fortune(rules);
        for(int w=wave+1;w<=20;w++){
            projected.wave=w;projected.coins+=extra+basicIncome(w)+fortune;
            projected.refresh(rules,random,false);
            var frame=new Future();frame.wave=w;frame.coins=projected.coins;frame.shop=new ArrayList<>(projected.shop);frame.pool=new LinkedHashMap<>(projected.pool);future.add(frame);
        }
    }
    public void reach(int nextWave){
        var frame=future.stream().filter(f->f.wave==nextWave).findFirst().orElseThrow(()->new IllegalStateException("缺少本人预先提交的下一波事实"));
        wave=nextWave;coins=frame.coins;shop=new ArrayList<>(frame.shop);pool=new LinkedHashMap<>(frame.pool);
        future.removeIf(f->f.wave<=nextWave);
    }
}
