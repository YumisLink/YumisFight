package cn.yumis.fight.modules.talents.api;
import cn.yumis.fight.modules.character.api.CharacterRules;
import cn.yumis.fight.modules.character.api.Progress;
import java.util.*;

/** 账本纯函数：预算、径向／环弧点亮资格、锚定拆除不变量与计数。仅依赖（规则包, 进度）入参，可无头穷举。 */
public final class TalentLedger {
    private TalentLedger(){}
    public static int budget(int level){return Math.clamp(level,0,CharacterRules.MAX_LEVEL)/5;}
    public static int used(Progress p){return p.talentNodes.size()+(p.talentAxis!=null?1:0);}
    public static int available(Progress p){return budget(p.level)-used(p);}
    /** 出生点占 1 点（拍板记录①）；换轴须先拆空本轴两线再拆出生点。 */
    public static boolean canPlaceBirth(TalentCatalog c,Progress p,String axis){return p.talentAxis==null&&c.axis(axis)!=null&&available(p)>=1;}
    public static boolean canRemoveBirth(TalentCatalog c,Progress p){
        if(p.talentAxis==null)return false;
        for(var line:c.linesOf(p.talentAxis))for(var node:c.lineNodes(line.id()))if(p.talentNodes.contains(node.id()))return false;
        return true;
    }
    /** 点亮资格：1–3 位须本轴出生点＋内侧全亮；4／10 位＝径向或同环相邻已亮；5–9 位须同线 4 已亮＋内侧全亮。 */
    public static boolean canPlace(TalentCatalog c,Progress p,String nodeId){
        var node=c.find(nodeId);
        if(node==null||node.retired()||p.talentNodes.contains(nodeId)||available(p)<1)return false;
        if(node.position()==4||node.position()==10)return radialReady(c,p.talentAxis,p.talentNodes,node)||ringLit(c,p.talentNodes,node);
        return radialReady(c,p.talentAxis,p.talentNodes,node);
    }
    /** 拆除＝叶＋锚定不变量：拆后余下每个已点仍可由径向路径或已锚定环邻支撑（堵借道悬空）。 */
    public static boolean canRemove(TalentCatalog c,Progress p,String nodeId){
        var node=c.find(nodeId);
        if(node==null||!p.talentNodes.contains(nodeId))return false;
        for(String lit:p.talentNodes){var other=c.node(lit);if(other.line().equals(node.line())&&other.position()>node.position())return false;}
        var remaining=new LinkedHashSet<>(p.talentNodes);remaining.remove(nodeId);
        return anchored(c,p.talentAxis,remaining);
    }
    public static int completeLines(TalentCatalog c,Set<String> lit){
        int count=0;
        for(var line:c.lines()){
            boolean full=true;
            for(int position=1;position<=TalentCatalog.POSITION_COUNT&&full;position++)if(!lit.contains(line.id()+"_"+position))full=false;
            if(full)count++;
        }
        return count;
    }
    public static int capstonesLit(TalentCatalog c,Set<String> lit){
        int count=0;
        for(String id:lit){var node=c.find(id);if(node!=null&&node.position()==4)count++;}
        return count;
    }
    /** 锚定不变量：径向种子＝内侧路径可点亮；环点可由任一已锚定环邻接力。拆除与加载一致性共用。 */
    public static boolean anchored(TalentCatalog c,String axis,Set<String> lit){
        var done=new HashSet<String>();
        for(String id:lit)if(radialReady(c,axis,lit,c.node(id)))done.add(id);
        for(boolean grew=true;grew;){
            grew=false;
            for(String id:lit){
                if(done.contains(id))continue;
                var node=c.node(id);
                if(node.position()!=4&&node.position()!=10)continue;
                for(var neighbor:c.ringNeighbors(id))if(done.contains(neighbor.id())){done.add(id);grew=true;break;}
            }
        }
        return done.size()==lit.size();
    }
    private static boolean radialReady(TalentCatalog c,String axis,Set<String> lit,TalentCatalog.Node node){
        if(node.position()<=3)return axis!=null&&axis.equals(c.line(node.line()).axis())&&betweenLit(lit,node.line(),0,node.position());
        if(node.position()==4)return betweenLit(lit,node.line(),0,4);
        return lit.contains(node.line()+"_4")&&betweenLit(lit,node.line(),4,node.position());
    }
    private static boolean betweenLit(Set<String> lit,String line,int from,int to){
        for(int position=from+1;position<to;position++)if(!lit.contains(line+"_"+position))return false;
        return true;
    }
    private static boolean ringLit(TalentCatalog c,Set<String> lit,TalentCatalog.Node node){
        for(var neighbor:c.ringNeighbors(node.id()))if(lit.contains(neighbor.id()))return true;
        return false;
    }
}
