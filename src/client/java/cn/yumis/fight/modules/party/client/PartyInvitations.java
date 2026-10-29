package cn.yumis.fight.modules.party.client;
import cn.yumis.fight.modules.party.api.PartyInvitation;
import cn.yumis.fight.modules.presentation.api.client.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;
/** Invitations wait until the caller reports that a popup will not interrupt play. */
public final class PartyInvitations{
    private static final List<PartyInvitation> PENDING=new ArrayList<>();private static final Set<String> SHOWN=new HashSet<>();
    private PartyInvitations(){}
    public static void clear(){PENDING.clear();SHOWN.clear();}
    public static void receive(JsonArray data){PENDING.clear();for(var value:data)PENDING.add(new Gson().fromJson(value,PartyInvitation.class));SHOWN.removeIf(key->PENDING.stream().noneMatch(i->key.equals(i.team()+"/"+i.expires())));}
    public static void tick(boolean safe){
        if(!safe)return;long now=ClientUi.current().serverNow();
        var invitation=PENDING.stream().filter(i->i.expires()>now&&!SHOWN.contains(i.team()+"/"+i.expires())).min(Comparator.comparingLong(PartyInvitation::expires)).orElse(null);if(invitation==null)return;
        SHOWN.add(invitation.team()+"/"+invitation.expires());var mc=Minecraft.getInstance();var player=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(invitation.leader());String name=player==null?invitation.leader().toString():player.getProfile().name();
        mc.gui.setScreen(new ThemedConfirmScreen(accept->{mc.gui.setScreen(null);var request=ClientUi.current().operation(accept?"accept":"decline");request.addProperty("team",invitation.team().toString());ClientUi.current().send("party",request);},Component.literal("队伍邀请"),Component.literal(name+" 邀请你加入战斗队伍"),ThemeDomain.MENU));
    }
}
