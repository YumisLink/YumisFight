package cn.yumis.fight.modules.party.api;
import java.util.UUID;
public record PartyInvitation(UUID team,UUID leader,long expires){}
