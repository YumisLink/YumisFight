package cn.yumis.fight.modules.party.api;

import java.util.*;

/** party模块拥有的数据合同；仅接收当前存档代际。 */
public class Team {
    public UUID id=UUID.randomUUID(),leader;
    public String name="战斗队伍";
    public LinkedHashSet<UUID> members=new LinkedHashSet<>();
    public Map<String,Long> invitations=new HashMap<>();
    /** 队长在线时返回队长，否则按成员顺序返回首位在线成员作为代理队长。 */
    public UUID effectiveLeader(java.util.function.Predicate<UUID> online){
        if(leader!=null&&online.test(leader))return leader;
        for(UUID member:members)if(online.test(member))return member;
        return leader;
    }
    /** 判定玩家是否具备当前有效队长（含队长离线时的代理队长）权限。 */
    public boolean canLead(UUID actor,java.util.function.Predicate<UUID> online){
        if(actor==null||!members.contains(actor))return false;
        UUID effective=effectiveLeader(online);
        return actor.equals(effective);
    }
}
