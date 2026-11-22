package cn.yumis.fight.modules.dungeons.api;
import java.util.*;
/** Current-generation encounter state owned exclusively by dungeons. Region zero is the overworld hunt. */
public final class TreasureState {
    public int region,baseLevel,tier=3,partySize=1;
    public double failedProbability=1;
    public UUID owner;
    public int mapX,mapZ;
    public double mapY;
    public long expires,nextVolley;
    public boolean portal,cleared,warningFive,warningOne;
    public String theme="forest";
    public double lastX,lastY,lastZ;

    /** Facts for a live goblin kingdom entrance: region zero stays cleared and the portal is open for thirty minutes. */
    public static TreasureState portal(UUID owner,int baseLevel,int x,int z,double y,long now){
        var t=new TreasureState();
        t.owner=owner;t.baseLevel=baseLevel;
        t.mapX=x;t.mapZ=z;t.mapY=y;
        t.lastX=x+.5;t.lastY=y;t.lastZ=z+.5;
        t.cleared=true;t.portal=true;t.expires=now+30*60*1000L;
        return t;
    }
}
