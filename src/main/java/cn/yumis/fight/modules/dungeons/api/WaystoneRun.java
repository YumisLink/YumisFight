package cn.yumis.fight.modules.dungeons.api;

import java.util.UUID;

/** Run snapshot; changing rules never changes an active challenge. */
public final class WaystoneRun {
    public Waystone stone;
    public UUID owner;
    public WaystoneRules rules;
    public int monsterLevel,ownerLevel,resultingTier;
    public double statMultiplier,lastOwnerDropRate;
    public long startedAt,finishedAt,penaltyMillis;
    public boolean settled;
    public String result="";
    public long elapsed(long now){return Math.max(0,(finishedAt>0?finishedAt:now)-startedAt)+penaltyMillis;}
}
