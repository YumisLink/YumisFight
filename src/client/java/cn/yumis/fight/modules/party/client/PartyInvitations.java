package cn.yumis.fight.modules.party.client;
import cn.yumis.fight.modules.party.api.PartyInvitation;
import cn.yumis.fight.modules.presentation.api.client.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.*;
/** Non-blocking HUD banner for incoming party invitations (never hijacks mouse/screen during gameplay). */
public final class PartyInvitations{
    private static final List<PartyInvitation> PENDING=new ArrayList<>();
    private static final Set<String> SHOWN=new HashSet<>();
    private static String bannerText="";
    private static long bannerUntil;
    private PartyInvitations(){}
    public static void clear(){PENDING.clear();SHOWN.clear();bannerText="";bannerUntil=0;}
    public static void receive(JsonArray data){
        PENDING.clear();
        if(data!=null)for(var value:data)PENDING.add(new Gson().fromJson(value,PartyInvitation.class));
        SHOWN.removeIf(key->PENDING.stream().noneMatch(i->key.equals(i.team()+"/"+i.expires())));
        if(PENDING.isEmpty()){bannerText="";bannerUntil=0;}
    }
    public static void tick(boolean safe){
        long now=ClientUi.current().serverNow();
        var invitation=PENDING.stream().filter(i->i.expires()>now&&!SHOWN.contains(i.team()+"/"+i.expires())).min(Comparator.comparingLong(PartyInvitation::expires)).orElse(null);
        if(invitation==null)return;
        SHOWN.add(invitation.team()+"/"+invitation.expires());
        var mc=Minecraft.getInstance();
        var player=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(invitation.leader());
        String name=player==null?invitation.leader().toString():player.getProfile().name();
        bannerText="收到来自 "+name+" 的组队邀请，按 I → 队伍 查看接受/拒绝";
        bannerUntil=ClientUi.now()+8000L;
        ClientUi.current().message(bannerText);
    }
    public static String activeBanner(long now){return now<bannerUntil?bannerText:"";}
    public static void render(GuiGraphicsExtractor g){
        long now=ClientUi.now();
        if(now>=bannerUntil||bannerText.isEmpty())return;
        var font=UiFonts.font();
        int pad=8,w=Math.min(g.guiWidth()-16,font.width(bannerText)+pad*2),x=g.guiWidth()-w-8,y=8;
        UiTheme.hud(g,x,y,w,20);
        g.text(font,font.plainSubstrByWidth(bannerText,w-pad*2),x+pad,y+6,UiTheme.HUD_GOLD,true);
    }
}
