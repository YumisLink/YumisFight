package cn.yumis.fight.modules.party.application;
import cn.yumis.fight.modules.party.api.*;
import cn.yumis.fight.foundation.Journal;
import net.minecraft.server.level.ServerPlayer;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Function;
/** Team membership and invitations are owned here, using stable team and member identities. */
public final class PartyService {
    private final Journal journal;private final Function<String,ServerPlayer> players;private final Runnable refresh;
    public PartyService(Journal journal,Function<String,ServerPlayer> players,Runnable refresh){this.journal=journal;this.players=players;this.refresh=refresh;}
    public Team team(UUID player){for(String key:journal.keys("team/")){Team t=journal.get(key,Team.class,null);if(t!=null&&t.members.contains(player))return t;}return null;}
    public List<PartyInvitation> incoming(UUID player){
        if(team(player)!=null)return List.of();List<PartyInvitation> result=new ArrayList<>();long now=System.currentTimeMillis();
        for(String key:journal.keys("team/")){Team t=journal.get(key,Team.class,null);if(t==null)continue;long expiry=t.invitations.getOrDefault(player.toString(),0L);if(expiry>now)result.add(new PartyInvitation(t.id,t.leader,expiry));}
        result.sort(Comparator.comparingLong(PartyInvitation::expires));return List.copyOf(result);
    }
    public void request(ServerPlayer p,JsonObject j)throws Exception{
        String kind=j.get("kind").getAsString();Team t=team(p.getUUID());long now=System.currentTimeMillis();
        switch(kind){
            case "create"->{if(t!=null)throw new IllegalArgumentException("你已有队伍");t=new Team();t.leader=p.getUUID();t.members.add(p.getUUID());}
            case "invite"->{
                ServerPlayer target=players.apply(j.get("name").getAsString().trim());
                if(target==null||target.getUUID().equals(p.getUUID()))throw new IllegalArgumentException("请选择另一位在线玩家");
                if(team(target.getUUID())!=null)throw new IllegalArgumentException("目标已有队伍");
                if(t==null){t=new Team();t.leader=p.getUUID();t.members.add(p.getUUID());}
                if(!t.leader.equals(p.getUUID()))throw new IllegalArgumentException("仅队长可以邀请");
                t.invitations.put(target.getStringUUID(),now+300000);
            }
            case "accept","decline"->{
                UUID id=UUID.fromString(j.get("team").getAsString());t=journal.get("team/"+id,Team.class,null);
                if(t==null||t.invitations.getOrDefault(p.getStringUUID(),0L)<=now)throw new IllegalArgumentException("邀请已失效");
                if(kind.equals("accept")){if(team(p.getUUID())!=null)throw new IllegalArgumentException("你已有队伍");t.members.add(p.getUUID());}
                t.invitations.remove(p.getStringUUID());
            }
            case "transfer","kick","disband"->{
                if(t==null||!t.leader.equals(p.getUUID()))throw new IllegalArgumentException("仅队长可以操作");
                if(kind.equals("disband")){journal.put("team/"+t.id,null);refresh.run();return;}
                UUID target=UUID.fromString(j.get("target").getAsString());
                if(target.equals(p.getUUID())||!t.members.contains(target))throw new IllegalArgumentException("请选择其他队员");
                if(kind.equals("transfer"))t.leader=target;else t.members.remove(target);
            }
            case "leave"->{
                if(t==null)return;if(t.leader.equals(p.getUUID())&&t.members.size()>1)throw new IllegalArgumentException("请先移交队长");
                t.members.remove(p.getUUID());if(t.members.isEmpty()){journal.put("team/"+t.id,null);refresh.run();return;}
            }
            default->throw new IllegalArgumentException("未知队伍操作");
        }
        journal.put("team/"+t.id,t);refresh.run();
    }
}
