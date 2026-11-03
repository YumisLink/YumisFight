package cn.yumis.fight.modules.skills.api;

import java.util.*;

/** Session/life/encounter scoped; never restored from an earlier connection's journal. */
public final class DemonHunterState {
    public int fury;
    public long decayAt,auraUntil,metamorphosisUntil;
    public String auraCast="",metamorphosisCast="";
    public Map<String,Long> gains=new LinkedHashMap<>();
    public DemonHunterState copy(){var s=new DemonHunterState();s.fury=fury;s.decayAt=decayAt;s.auraUntil=auraUntil;s.metamorphosisUntil=metamorphosisUntil;s.auraCast=auraCast;s.metamorphosisCast=metamorphosisCast;s.gains.putAll(gains);return s;}
}
