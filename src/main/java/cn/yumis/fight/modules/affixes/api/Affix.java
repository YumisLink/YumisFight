package cn.yumis.fight.modules.affixes.api;
import cn.yumis.fight.modules.attributes.api.Stat;

import java.util.*;

/** affixes模块拥有的数据合同；仅接收当前存档代际。 */
public class Affix {
    public transient com.google.gson.JsonObject preserved;
    public int schemaVersion;
    public String instanceId="",syncStatus="";
    public Anchor origin;
    public static class Anchor {
        public String operation,stat;
        public int element,rank;
        public long revision;
        public double value,maximum,baseline,baselineMaximum,roll,definitionScale=1;
        public Double quality;
    }
    public String id,stat="",operation="FLAT",family="",skill="",parameter="";
    public double value,maximum,baseline,baselineMaximum,roll=1,corruptionMultiplier=1;
    public boolean base,fixed,locked;
    public int element=-1,rank;
    public Affix(){
    }
    public Affix(String id,Stat stat,double value,boolean base){
        this.id=id;
        this.stat=stat.name();
        this.value=value;
        this.baseline=value;
        this.base=base;
        this.fixed=base;
    }
}
