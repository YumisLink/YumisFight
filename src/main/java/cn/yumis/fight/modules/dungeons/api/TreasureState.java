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

    // 主题轮换、双门预告与彩虹破门救场
    public List<String> visitedThemes=new ArrayList<>();
    public String westTheme="",eastTheme="";
    public String pendingPortalSide="",pendingPortalTheme="";
    public long portalRescueAt;
    public boolean portalRescuing;

    // 随机奇遇：逃跑的黄金哥布林
    public UUID goldenGoblin;
    public long goldenGoblinExpires;
    public boolean goldenGoblinSpawned,goldenGoblinBonus;

    // 通关命运双骰（1-9 不重复猜大小翻倍，含跨区连庄 64x 起步最高 4096x）
    public boolean diceActive,diceStreakReady,diceMustWinOne,diceGuessHigher=true;
    public String dicePhase="";
    public int diceFirst,diceSecond,diceMultiplier=1,diceRounds,dicePendingTier=3,diceLastResult;
    public String dicePendingTheme="goblin_camp";
    public long diceAnimUntil;
    public UUID diceEntity1,diceEntity2;

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
