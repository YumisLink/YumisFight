package cn.yumis.fight.modules.party.application;
import cn.yumis.fight.modules.party.api.Team;
import cn.yumis.fight.modules.party.api.*;
import cn.yumis.fight.foundation.Journal;
import net.minecraft.server.level.ServerPlayer;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Function;
/** 队伍状态与操作；玩家查询和刷新通过窄接口注入。 */
public final class PartyService {
    private final Journal journal;
    private final Function<String,ServerPlayer> players;
    private final Runnable refresh;
    public PartyService(Journal journal,Function<String,ServerPlayer> players,Runnable refresh){this.journal=journal;this.players=players;this.refresh=refresh;}
    public Team team(UUID player){
        for(String key:journal.keys("team/")){
            Team team=journal.get(key,Team.class,null);
            if(team!=null&&team.members.contains(player))return team;
        }
        return null;
    }
    public void request(ServerPlayer p,JsonObject j)throws Exception{
        String kind=j.get("kind").getAsString();
        Team t=team(p.getUUID());
        ServerPlayer target=j.has("name")?players.apply(j.get("name").getAsString()):null;
        long now=System.currentTimeMillis();
        switch(kind){
            case "create"->{
                if(t!=null)throw new IllegalArgumentException("你已有队伍");
                t=new Team();
                t.leader=p.getUUID();
                t.members.add(p.getUUID());
            }
            case "invite","approve","kick","transfer","disband"->{
                if(t==null||!t.leader.equals(p.getUUID()))throw new IllegalArgumentException("仅队长可以操作");
                if(kind.equals("disband")){
                    journal.put("team/"+t.id,null);
                    refresh.run();
                    return;
                }
                if(target==null)throw new IllegalArgumentException("目标玩家不在线");
                if(kind.equals("invite")){
                    if(team(target.getUUID())!=null)throw new IllegalArgumentException("目标已有队伍");
                    t.invitations.put(target.getStringUUID(),now+300000);
                }
                if(kind.equals("approve")){
                    if(t.applications.getOrDefault(target.getStringUUID(),0L)<now||team(target.getUUID())!=null)throw new IllegalArgumentException("申请已失效");
                    t.members.add(target.getUUID());
                    t.applications.remove(target.getStringUUID());
                }
                if(kind.equals("kick")){
                    if(target==p)throw new IllegalArgumentException("队长请移交或解散");
                    t.members.remove(target.getUUID());
                }
                if(kind.equals("transfer")){
                    if(!t.members.contains(target.getUUID()))throw new IllegalArgumentException("目标不在队伍");
                    t.leader=target.getUUID();
                }
            }
            case "apply","accept"->{
                if(t!=null||target==null)throw new IllegalArgumentException("已有队伍或目标不在线");
                t=team(target.getUUID());
                if(t==null)throw new IllegalArgumentException("目标没有队伍");
                if(kind.equals("apply"))t.applications.put(p.getStringUUID(),now+300000);
                else{
                    if(t.invitations.getOrDefault(p.getStringUUID(),0L)<now)throw new IllegalArgumentException("邀请已失效");
                    t.members.add(p.getUUID());
                    t.invitations.remove(p.getStringUUID());
                }
            }
            case "leave"->{
                if(t==null)return;
                if(t.leader.equals(p.getUUID())&&t.members.size()>1)throw new IllegalArgumentException("请先手动移交队长");
                t.members.remove(p.getUUID());
                if(t.members.isEmpty()){
                    journal.put("team/"+t.id,null);
                    refresh.run();
                    return;
                }
            }
            default->throw new IllegalArgumentException("未知队伍操作");
        }
        journal.put("team/"+t.id,t);
        refresh.run();
    }
}
