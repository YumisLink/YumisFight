package cn.yumis.fight.modules.party.client;
import cn.yumis.fight.modules.party.api.Team;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageSurface;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import java.util.UUID;
import java.util.function.*;
public final class PartyPage {
    private PartyPage(){}
    public static void render(PlayerPageSurface page,Team team,Function<UUID,String> playerName,BiConsumer<String,String> command){
        if(team==null)page.line("当前没有战斗队伍");else{
            page.line("战斗队伍 · 人数 "+team.members.size(),UiTheme.GOLD);
            for(UUID id:team.members)page.line((id.equals(team.leader)?"[队长] ":"[成员] ")+playerName.apply(id));
            page.line("待批准申请 "+team.applications.size()+"    待接受邀请 "+team.invitations.size());
        }
        page.gap();page.line("目标玩家账号名：");var input=page.textInput("party.name","玩家账号名",32);
        String[] actions={"create","invite","apply","accept","approve","transfer","kick","leave","disband"},labels={"创建队伍","邀请目标玩家","申请加入目标队伍","接受目标队长邀请","批准目标申请","移交队长","移除目标成员","离开队伍","解散队伍"};
        for(int i=0;i<actions.length;i++){String action=actions[i];page.rowButton(labels[i],()->command.accept(action,input.get().trim()));}
        page.line("队伍人数不限；创建地下城由队长选择设置，公开轮次可随时加入。",UiTheme.MUTED);
    }
}
