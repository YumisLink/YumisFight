package cn.yumis.fight.modules.character.application;

import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.inventory.api.*;
import cn.yumis.fight.network.Network;
import cn.yumis.fight.platform.ItemData;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.*;

/** Character changes, consumables and profile selection commit in the same inventory/WAL transaction. */
public final class ProgressService {
    private final Journal journal;
    private final CharacterPersistence records;
    private final InventoryOperations inventory;
    private final Function<ServerPlayer,Progress> progressOf;
    private final ToLongFunction<ServerPlayer> combatUntil;
    private final LongSupplier combatDelay;
    private final BiConsumer<ServerPlayer,Progress> update;
    private final Consumer<ServerPlayer> profile;
    private final Function<ServerPlayer,cn.yumis.fight.modules.attributes.api.Stats> stats;
    private final SkillProgression skills;
    private JobTransitions transitions;
    private InitialEquipment initial;
    private Function<ServerPlayer,ProfileConditions> conditions=p->{throw new IllegalStateException("职业切换条件未装配");};
    private record Offer(String token,int slot,String item,String kind,String profileId,long revision,int level,long expires){}
    private final Map<UUID,Offer> offers=new HashMap<>();
    public void transitions(JobTransitions value){transitions=Objects.requireNonNull(value);}
    public void initial(InitialEquipment value){initial=Objects.requireNonNull(value);}
    public void profileGuard(Function<ServerPlayer,ProfileConditions> value){conditions=Objects.requireNonNull(value);}
    public ProgressService(Journal journal,CharacterPersistence records,InventoryOperations inventory,Function<ServerPlayer,Progress> progressOf,ToLongFunction<ServerPlayer> combatUntil,BiConsumer<ServerPlayer,Progress> update,Consumer<ServerPlayer> profile,Function<ServerPlayer,cn.yumis.fight.modules.attributes.api.Stats> stats,SkillProgression skills,LongSupplier combatDelay){
        this.journal=journal;this.records=records;this.inventory=inventory;this.progressOf=progressOf;this.combatUntil=combatUntil;this.update=update;this.profile=profile;this.stats=stats;this.skills=skills;this.combatDelay=combatDelay;
    }
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("character","progress",this::request);}
    public void useItem(ServerPlayer player,CharacterActions.Ticket ticket){
        var book=records.profiles(player.getUUID());var current=progressOf.apply(player);
        String kind=switch(ticket){case PROFESSION_RESET->"createProfile";case CAREER_UNLOCK->"unlockCareer";case AWAKENING->"unlockAwakening";case LEVEL_ONE->"levelOne";case LEVEL_TEN->"levelTen";case LEVEL_MAX->"levelMax";};
        String item=switch(ticket){case PROFESSION_RESET->CharacterItems.PROFESSION_RESET;case CAREER_UNLOCK->CharacterItems.CAREER_UNLOCK;case AWAKENING->CharacterItems.AWAKENING;case LEVEL_ONE->CharacterItems.LEVEL_ONE;case LEVEL_TEN->CharacterItems.LEVEL_TEN;case LEVEL_MAX->CharacterItems.LEVEL_MAX;};
        var request=new JsonObject();request.addProperty("kind",kind);request.addProperty("operation",UUID.randomUUID().toString());request.addProperty("profileId",book.activeId);request.addProperty("profileRevision",book.revision);
        if(ticket==CharacterActions.Ticket.CAREER_UNLOCK||ticket==CharacterActions.Ticket.AWAKENING){request.addProperty("ticketSlot",player.getInventory().getSelectedSlot());request(player,request);return;}
        safe(player);
        String text;
        if(ticket==CharacterActions.Ticket.PROFESSION_RESET){canCreate(current,book);text="消耗1张职业重置券，保留当前配置并创建20级新配置；新配置需自行选择职业，不赠送武器。";}
        else {if(current.level>=CharacterRules.MAX_LEVEL)throw new IllegalArgumentException("当前配置已经满级，未消耗券");int target=target(kind,current.level);text="将「"+book.entries.get(book.activeId).name+"」从"+current.level+"级升至"+target+"级，经验清零；消耗整张券。";}
        String token=UUID.randomUUID().toString();offers.put(player.getUUID(),new Offer(token,player.getInventory().getSelectedSlot(),item,kind,book.activeId,book.revision,current.level,System.currentTimeMillis()+60000));
        request.addProperty("token",token);request.addProperty("text",text);Network.send(player,"characterTicketConfirm",request);
    }
    private static int target(String kind,int level){return kind.equals("levelMax")?CharacterRules.MAX_LEVEL:Math.min(CharacterRules.MAX_LEVEL,level+(kind.equals("levelTen")?10:1));}
    private static void canCreate(Progress p,CareerProfiles book){
        if(p.level<20||!p.selected()||JobCatalog.basic(p.jobId()))throw new IllegalArgumentException("当前配置需要至少20级且已转职，才能创建新配置");
        if(book.entries.size()>=book.capacity)throw new IllegalArgumentException("职业配置已满，当前最多"+book.capacity+"套");
    }
    private void safe(ServerPlayer p){
        if(inventory.locked(p.getUUID()))throw new IllegalArgumentException("库存正在处理");
        if(System.currentTimeMillis()<combatUntil.applyAsLong(p))throw new IllegalArgumentException("请在连续"+(combatDelay.getAsLong()/1000d)+"秒未造成或受到伤害后操作");
        conditions.apply(p).requireReady();
    }
    public String blockReason(ServerPlayer p){try{safe(p);return "";}catch(IllegalArgumentException failure){return failure.getMessage();}}
    public void markQuest(ServerPlayer p,String base){if(!JobCatalog.basic(base))throw new IllegalArgumentException("需要基础职业ID");var next=CareerProfiles.copy(progressOf.apply(p));next.unlockJobChange();persistAdmin(p,next);}
    public void awaken(ServerPlayer p){var next=CareerProfiles.copy(progressOf.apply(p));if(next.level<50||!next.selected()||JobCatalog.basic(next.jobId()))throw new IllegalArgumentException("觉醒需要50级且已经转职");next.awakened=true;persistAdmin(p,next);}
    private void persistAdmin(ServerPlayer p,Progress next){try{journal.transaction(records.writes(p.getUUID(),next));update.accept(p,next);profile.accept(p);}catch(Exception failure){throw new IllegalStateException(failure);}}
    public void request(ServerPlayer p,JsonObject j){
        String kind=j.get("kind").getAsString(),op=j.get("operation").getAsString();UUID.fromString(op);String key="character-v2/receipt/"+p.getUUID()+"/"+op;
        var previous=journal.get(key);
        if(!previous.isJsonNull()){if(!previous.equals(j))throw new IllegalArgumentException("操作号冲突");inventory.restore(p);update.accept(p,records.read(p.getUUID()));profile.accept(p);return;}
        var book=records.profiles(p.getUUID());
        if(!j.has("profileId")||!j.has("profileRevision")||!book.activeId.equals(j.get("profileId").getAsString())||book.revision!=j.get("profileRevision").getAsLong()){profile.accept(p);throw new IllegalArgumentException("职业配置已切换，请刷新后操作");}
        if(inventory.locked(p.getUUID()))throw new IllegalArgumentException("库存正在处理");
        if(!kind.equals("bind")&&!kind.equals("renameProfile")&&System.currentTimeMillis()<combatUntil.applyAsLong(p))throw new IllegalArgumentException("请在连续"+(combatDelay.getAsLong()/1000d)+"秒未造成或受到伤害后操作");
        var next=CareerProfiles.copy(progressOf.apply(p));JobTransitions.Change change=null;Offer offer=null;ItemStack starter=ItemStack.EMPTY;
        switch(kind){
            case "learn","unlearn","resetSkills","bind"->skills.apply(next,stats.apply(p),j);
            case "allocate"->next.allocate(j.get("stat").getAsString(),j.has("count")?j.get("count").getAsInt():1);
            case "resetAttributes"->next.strength=next.intelligence=next.spirit=0;
            case "sync"->next.sync=j.get("enabled").getAsBoolean();
            case "renameProfile"->book.rename(j.get("target").getAsString(),j.get("name").getAsString());
            case "unlockCareer"->{if(next.level<20)throw new IllegalArgumentException("转职解锁券需要20级");if(next.jobChangeUnlocked())throw new IllegalArgumentException("已经拥有通用转职资格，未消耗券");next.unlockJobChange();}
            case "unlockAwakening"->{if(next.level<50)throw new IllegalArgumentException("觉醒券需要50级");if(next.awakened())throw new IllegalArgumentException("已经觉醒，未消耗券");next.awakened=true;}
            case "chooseJob"->{
                String id=j.get("job").getAsString();if(next.selected()||!JobCatalog.basic(id))throw new IllegalArgumentException("每套配置只能选择一次基础职业");
                next.promote(id);if(!next.starterGranted){starter=Objects.requireNonNull(initial,"初始武器服务未装配").weapon(id);next.starterGranted=true;}
            }
            case "promote"->{String id=j.get("job").getAsString();if(book.occupied(id))throw new IllegalArgumentException("已拥有该转职配置，请切换至已有配置");if(!JobCatalog.canPromote(next,id))throw new IllegalArgumentException("需要20级、对应基础职业和通用转职解锁资格");change=transitions.prepare(p,next,id);next.promote(id);}
            case "switchProfile"->{String target=j.get("target").getAsString();if(target.equals(book.activeId)){profile.accept(p);return;}safe(p);next=book.select(next,target);change=transitions.switchProfile(p,next);}
            case "createProfile","levelOne","levelTen","levelMax"->{
                safe(p);offer=offers.get(p.getUUID());
                if(offer==null||!offer.kind.equals(kind)||offer.expires<System.currentTimeMillis()||!offer.token.equals(j.get("token").getAsString())||!offer.profileId.equals(book.activeId)||offer.revision!=book.revision||offer.level!=next.level)throw new IllegalArgumentException("用券确认已过期，请重新使用券");
                if(kind.equals("createProfile")){canCreate(next,book);next=book.create(next);change=transitions.switchProfile(p,next);}
                else next.gainLevels(target(kind,next.level)-next.level);
            }
            default->throw new IllegalArgumentException("未知进度操作");
        }
        if(kind.equals("resetSkills"))change=transitions.clear(p,next);
        if(kind.equals("unlearn"))change=transitions.reduceSkill(p,next,j.get("skill").getAsString());
        var writes=new LinkedHashMap<>(records.writes(p.getUUID(),next,book));writes.put(key,j.deepCopy());if(change!=null)writes.putAll(change.writes());
        Offer ticket=offer;ItemStack reward=starter;var committedChange=change;
        try{
            if(!reward.isEmpty()&&InventoryPacking.room(p,reward)<reward.getCount()){
                try{journal.begin();initial.drop(p,reward);for(var entry:writes.entrySet())journal.put(entry.getKey(),entry.getValue());journal.finish();initial.publish();}catch(Exception failure){journal.abort();initial.abort();throw new IllegalStateException(failure);}
            }else inventory.transact(p,op,j,()->{
                if(kind.equals("unlockCareer")||kind.equals("unlockAwakening"))consume(p,j.get("ticketSlot").getAsInt(),kind.equals("unlockCareer")?CharacterItems.CAREER_UNLOCK:CharacterItems.AWAKENING);
                if(ticket!=null)consume(p,ticket.slot,ticket.item);
                if(!reward.isEmpty())inventory.requireSpace(p,reward);
            },writes);
        }catch(RuntimeException failure){
            // A native flush can fail after the WAL commit. Install the durable selection before logout can save.
            if(journal.get(key).equals(j)){update.accept(p,records.read(p.getUUID()));if(committedChange!=null)committedChange.committed().run();offers.remove(p.getUUID());}
            throw failure;
        }
        update.accept(p,next);if(change!=null)change.committed().run();if(ticket!=null)offers.remove(p.getUUID());profile.accept(p);
    }
    private static void consume(ServerPlayer p,int slot,String item){
        if(slot<0||slot>=36)throw new IllegalArgumentException("券槽位无效");var stack=p.getInventory().getItem(slot);
        if(stack.isEmpty()||!ItemData.own(stack)||!ItemData.id(stack).equals(item))throw new IllegalArgumentException("券已移动或不存在");stack.shrink(1);
    }
}
