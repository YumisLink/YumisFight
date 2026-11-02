package cn.yumis.fight.modules.character.application;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.inventory.api.*;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import cn.yumis.fight.platform.ItemData;
import java.util.*;
import java.util.function.*;
/** Character qualification and consumables share one inventory/WAL transaction. */
public final class ProgressService {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private static Progress copy(Progress value){return JSON.fromJson(JSON.toJson(value),Progress.class);}
    private final SkillProgression skills;
    private final Function<ServerPlayer,cn.yumis.fight.modules.attributes.api.Stats> stats;
    private final Journal journal;
    private final InventoryOperations inventory;
    private final Function<ServerPlayer,Progress> progressOf;
    private final ToLongFunction<ServerPlayer> combatUntil;
    private final LongSupplier combatDelay;
    private final BiConsumer<ServerPlayer,Progress> update;
    private final Consumer<ServerPlayer> profile;
    private JobTransitions transitions;
    private InitialEquipment initial;
    private record ResetOffer(String token,int slot,boolean base,long expires){}
    private final Map<UUID,ResetOffer> offers=new HashMap<>();
    public void transitions(JobTransitions value){transitions=Objects.requireNonNull(value);}
    public void initial(InitialEquipment value){initial=Objects.requireNonNull(value);}
    public ProgressService(Journal journal,InventoryOperations inventory,Function<ServerPlayer,Progress> progressOf,ToLongFunction<ServerPlayer> combatUntil,BiConsumer<ServerPlayer,Progress> update,Consumer<ServerPlayer> profile,Function<ServerPlayer,cn.yumis.fight.modules.attributes.api.Stats> stats,SkillProgression skills,LongSupplier combatDelay){this.skills=skills;this.stats=stats;this.journal=journal;this.inventory=inventory;this.progressOf=progressOf;this.combatUntil=combatUntil;this.update=update;this.profile=profile;this.combatDelay=combatDelay;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("character","progress",this::request);}
    public void useItem(ServerPlayer player,CharacterActions.Ticket ticket){
        if(ticket==CharacterActions.Ticket.PROFESSION_RESET||ticket==CharacterActions.Ticket.CAREER_RESET){offerReset(player,ticket==CharacterActions.Ticket.PROFESSION_RESET);return;}
        var request=new JsonObject();request.addProperty("kind",ticket==CharacterActions.Ticket.CAREER_UNLOCK?"unlockCareer":"unlockAwakening");request.addProperty("operation",UUID.randomUUID().toString());request.addProperty("ticketSlot",player.getInventory().getSelectedSlot());request(player,request);
    }
    public void offerReset(ServerPlayer player,boolean base){
        var current=progressOf.apply(player);if(!current.selected()||!base&&JobCatalog.basic(current.jobId()))throw new IllegalArgumentException("没有可重置的职业");
        String token=UUID.randomUUID().toString();offers.put(player.getUUID(),new ResetOffer(token,player.getInventory().getSelectedSlot(),base,System.currentTimeMillis()+60000));
        Network.send(player,"careerResetConfirm",Map.of("token",token,"base",base));
    }
    public void markQuest(ServerPlayer player,String base){
        if(!JobCatalog.basic(base))throw new IllegalArgumentException("需要基础职业ID");
        var next=copy(progressOf.apply(player));next.unlockJobChange();persistAdmin(player,next);
    }
    public void awaken(ServerPlayer player){
        var next=copy(progressOf.apply(player));if(next.level<50||!next.selected()||JobCatalog.basic(next.jobId()))throw new IllegalArgumentException("觉醒需要50级且已经转职");next.awakened=true;persistAdmin(player,next);
    }
    private void persistAdmin(ServerPlayer player,Progress next){try{journal.begin();journal.put("player/"+player.getUUID(),next);journal.finish();update.accept(player,next);profile.accept(player);}catch(Exception failure){journal.abort();throw new IllegalStateException(failure);}}
    public void request(ServerPlayer p,JsonObject j){
        var next=copy(progressOf.apply(p));next.awakened=next.awakened();String kind=j.get("kind").getAsString(),op=j.get("operation").getAsString(),key="progressReceipt/"+p.getUUID()+"/"+op;
        var previous=journal.get(key);if(!previous.isJsonNull()){if(!previous.equals(j))throw new IllegalArgumentException("操作号冲突");inventory.restore(p);profile.accept(p);return;}
        if(inventory.locked(p.getUUID()))throw new IllegalArgumentException("库存正在处理");
        if(!kind.equals("bind")&&System.currentTimeMillis()<combatUntil.applyAsLong(p))throw new IllegalArgumentException("请在连续"+(combatDelay.getAsLong()/1000d)+"秒未造成或受到伤害后操作");
        JobTransitions.Change change=null;ResetOffer offer=null;ItemStack starter=ItemStack.EMPTY;
        switch(kind){
            case "learn","unlearn","resetSkills","bind"->skills.apply(next,stats.apply(p),j);
            case "allocate"->next.allocate(j.get("stat").getAsString(),j.has("count")?j.get("count").getAsInt():1);
            case "resetAttributes"->next.strength=next.intelligence=next.spirit=0;
            case "sync"->next.sync=j.get("enabled").getAsBoolean();
            case "unlockCareer"->{if(next.level<20)throw new IllegalArgumentException("转职解锁券需要20级");if(next.jobChangeUnlocked())throw new IllegalArgumentException("已经拥有通用转职资格，未消耗券");next.unlockJobChange();}
            case "unlockAwakening"->{if(next.level<50)throw new IllegalArgumentException("觉醒券需要50级");if(next.awakened())throw new IllegalArgumentException("已经觉醒，未消耗券");next.awakened=true;}
            case "chooseJob"->{
                String id=j.get("job").getAsString();if(next.selected()||!JobCatalog.basic(id))throw new IllegalArgumentException("只能选择一次基础职业，请使用职业重置券重选");
                next.promote(id);if(!next.starterGranted){starter=Objects.requireNonNull(initial,"初始武器服务未装配").weapon(id);next.starterGranted=true;}
            }
            case "promote"->{String id=j.get("job").getAsString();if(!JobCatalog.canPromote(next,id))throw new IllegalArgumentException("需要20级、对应基础职业和通用转职解锁资格");change=transitions.prepare(p,next,id);next.promote(id);}
            case "resetProfession","resetCareer"->{
                offer=offers.get(p.getUUID());boolean base=kind.equals("resetProfession");
                if(offer==null||offer.base!=base||offer.expires<System.currentTimeMillis()||!offer.token.equals(j.get("token").getAsString()))throw new IllegalArgumentException("重置确认已过期，请重新使用券");
                if(!next.selected()||!base&&JobCatalog.basic(next.jobId()))throw new IllegalArgumentException("没有可重置的职业");
                String destination=base?JobCatalog.NONE:next.baseJob();change=transitions.prepare(p,next,destination);
                if(base){var reset=new JsonObject();reset.addProperty("kind","resetSkills");skills.apply(next,stats.apply(p),reset);}
                next.promote(destination);
            }
            default->throw new IllegalArgumentException("未知进度操作");
        }
        if(kind.equals("resetSkills"))change=transitions.clear(p,next);
        if(kind.equals("unlearn"))change=transitions.reduceSkill(p,next,j.get("skill").getAsString());
        var writes=new LinkedHashMap<String,JsonElement>();writes.put("player/"+p.getUUID(),JSON.toJsonTree(next));writes.put(key,j.deepCopy());if(change!=null)writes.putAll(change.writes());
        ResetOffer ticket=offer;ItemStack reward=starter;
        if(!reward.isEmpty()&&InventoryPacking.room(p,reward)<reward.getCount()){
            try{journal.begin();initial.drop(p,reward);for(var entry:writes.entrySet())journal.put(entry.getKey(),entry.getValue());journal.finish();initial.publish();}catch(Exception failure){journal.abort();initial.abort();throw new IllegalStateException(failure);}
        }else{
            inventory.transact(p,op,j,()->{
                if(kind.equals("unlockCareer")||kind.equals("unlockAwakening")){int slot=j.get("ticketSlot").getAsInt();if(slot<0||slot>=36)throw new IllegalArgumentException("券槽位无效");var item=p.getInventory().getItem(slot);String expected=kind.equals("unlockCareer")?CharacterItems.CAREER_UNLOCK:CharacterItems.AWAKENING;if(item.isEmpty()||!ItemData.own(item)||!ItemData.id(item).equals(expected))throw new IllegalArgumentException("解锁券已移动或不存在");item.shrink(1);}
                if(ticket!=null){var stack=p.getInventory().getItem(ticket.slot);String expected=ticket.base?CharacterItems.PROFESSION_RESET:CharacterItems.CAREER_RESET;if(stack.isEmpty()||!ItemData.own(stack)||!ItemData.id(stack).equals(expected))throw new IllegalArgumentException("重置券已移动或不存在");stack.shrink(1);}
                if(!reward.isEmpty())inventory.requireSpace(p,reward);
            },writes);
        }
        update.accept(p,next);if(change!=null)change.committed().run();if(ticket!=null)offers.remove(p.getUUID());profile.accept(p);
    }
}
