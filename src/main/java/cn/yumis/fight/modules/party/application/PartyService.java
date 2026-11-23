package cn.yumis.fight.modules.party.application;
import cn.yumis.fight.modules.party.api.*;
import cn.yumis.fight.foundation.Journal;
import net.minecraft.server.level.ServerPlayer;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.*;
/** Team membership and invitations are owned here, using stable team and member identities. Leader-only operations fall to the acting member when the leader is offline; only one invitation may be pending per player; a write-through index keeps reads off the journal scan. */
public final class PartyService {
    private final Journal journal;private final Function<String,ServerPlayer> players;private final Predicate<UUID> online;private final Runnable refresh;
    /** 写穿内存索引：所有 team/ 写入都经过本类，读侧不再全表扫描 Journal。 */
    private final Map<UUID,UUID> memberTeam=new HashMap<>();
    private final Map<UUID,Team> teams=new HashMap<>();
    private boolean indexed;
    public PartyService(Journal journal,Function<String,ServerPlayer> players,Predicate<UUID> online,Runnable refresh){this.journal=journal;this.players=players;this.online=online;this.refresh=refresh;}
    /** 成员操作时代管：队长离线时由发起操作的队员接管队长；返回是否可以执行队长操作。 */
    static boolean actingLeader(Team t,UUID actor,Predicate<UUID> online){
        if(t==null||!t.members.contains(actor))return false;
        if(t.leader.equals(actor))return true;
        if(online.test(t.leader))return false;
        t.leader=actor;
        return true;
    }
    private void index(){
        if(indexed)return;
        for(String key:journal.keys("team/")){Team t=journal.get(key,Team.class,null);if(t!=null)cache(t);}
        indexed=true;
    }
    private void cache(Team t){
        teams.put(t.id,t);
        for(UUID member:t.members)memberTeam.put(member,t.id);
        for(var entries=memberTeam.entrySet().iterator();entries.hasNext();){var entry=entries.next();if(entry.getValue().equals(t.id)&&!t.members.contains(entry.getKey()))entries.remove();}
    }
    private void save(Team t)throws Exception{journal.put("team/"+t.id,t);cache(t);refresh.run();}
    private void drop(Team t)throws Exception{journal.put("team/"+t.id,null);teams.remove(t.id);for(UUID member:t.members)memberTeam.remove(member);refresh.run();}
    public Team team(UUID player){index();UUID id=memberTeam.get(player);return id==null?null:teams.get(id);}
    public List<PartyInvitation> incoming(UUID player){
        if(team(player)!=null)return List.of();index();List<PartyInvitation> result=new ArrayList<>();long now=System.currentTimeMillis();
        for(Team t:teams.values()){long expiry=t.invitations.getOrDefault(player.toString(),0L);if(expiry>now)result.add(new PartyInvitation(t.id,t.leader,expiry));}
        result.sort(Comparator.comparingLong(PartyInvitation::expires));return List.copyOf(result);
    }
    /** 唯一邀请：返回该玩家当前未过期邀请数，大于 0 时不得再发新邀请。 */
    long pendingInvitations(UUID player,long now){
        index();long count=0;
        for(Team t:teams.values())if(t.invitations.getOrDefault(player.toString(),0L)>now)count++;
        return count;
    }
    public void request(ServerPlayer p,JsonObject j)throws Exception{
        String kind=j.get("kind").getAsString();Team t=team(p.getUUID());long now=System.currentTimeMillis();
        switch(kind){
            case "create"->{if(t!=null)throw new IllegalArgumentException("你已有队伍");t=new Team();t.leader=p.getUUID();t.members.add(p.getUUID());}
            case "invite"->{
                ServerPlayer target=players.apply(j.get("name").getAsString().trim());
                if(target==null||target.getUUID().equals(p.getUUID()))throw new IllegalArgumentException("请选择另一位在线玩家");
                if(team(target.getUUID())!=null)throw new IllegalArgumentException("目标已有队伍");
                if(pendingInvitations(target.getUUID(),now)>0)throw new IllegalArgumentException("对方已有待处理邀请，需等待接受、拒绝或过期");
                if(t==null){t=new Team();t.leader=p.getUUID();t.members.add(p.getUUID());}
                if(!actingLeader(t,p.getUUID(),online))throw new IllegalArgumentException("仅队长可以邀请");
                t.invitations.put(target.getStringUUID(),now+300000);
            }
            case "accept","decline"->{
                UUID id=UUID.fromString(j.get("team").getAsString());index();t=teams.get(id);
                if(t==null||t.invitations.getOrDefault(p.getStringUUID(),0L)<=now)throw new IllegalArgumentException("邀请已失效");
                if(kind.equals("accept")){if(team(p.getUUID())!=null)throw new IllegalArgumentException("你已有队伍");t.members.add(p.getUUID());}
                t.invitations.remove(p.getStringUUID());
            }
            case "transfer","kick","disband"->{
                if(!actingLeader(t,p.getUUID(),online))throw new IllegalArgumentException("仅队长可以操作");
                if(kind.equals("disband")){drop(t);return;}
                UUID target=UUID.fromString(j.get("target").getAsString());
                if(target.equals(p.getUUID())||!t.members.contains(target))throw new IllegalArgumentException("请选择其他队员");
                if(kind.equals("transfer"))t.leader=target;else t.members.remove(target);
            }
            case "leave"->{
                if(t==null)return;if(t.leader.equals(p.getUUID())&&t.members.size()>1)throw new IllegalArgumentException("请先移交队长");
                t.members.remove(p.getUUID());if(t.members.isEmpty()){drop(t);return;}
            }
            default->throw new IllegalArgumentException("未知队伍操作");
        }
        save(t);
    }
}
