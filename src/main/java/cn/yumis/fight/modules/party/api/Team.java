package cn.yumis.fight.modules.party.api;

import java.util.*;

/** party模块拥有的数据合同；仅接收当前存档代际。 */
public class Team {
    public UUID id=UUID.randomUUID(),leader;
    public String name="战斗队伍";
    public LinkedHashSet<UUID> members=new LinkedHashSet<>();
    public Map<String,Long> invitations=new HashMap<>(),applications=new HashMap<>();
}
