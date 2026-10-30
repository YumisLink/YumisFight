package cn.yumis.fight.modules.skills.api;

import java.util.*;

/** Connection/life-scoped skill state. Timestamps in messages use server wall time. */
public final class CareerState {
    public String session="",job="",dimension="",instance="";
    public long generation,nextBrand,kallaUntil,eyeUntil,bloodUntil;
    public int brands,blood;
    public boolean frenzy;
    public double eyeBonusMut;
    public OracleState oracle=new OracleState();
    public Field field;
    public Map<String,Long> reductions=new LinkedHashMap<>();
    public List<Field> fields=new ArrayList<>();
    public List<Actor> actors=new ArrayList<>();
    public static final class Actor {public String owner="";public int keiga,brands,blood;public boolean frenzy;public long kallaUntil,eyeUntil,bloodUntil;}
    public static final class Field {
        public String id="",owner="",dimension="",instance="";
        public int rank;
        public double x,y,z;
        public long expires;
        public boolean friendly;
        public boolean contains(double px,double py,double pz){return Math.abs(py-y)<=4&&(px-x)*(px-x)+(pz-z)*(pz-z)<=25;}
    }
    public double red(double x,double y,double z,long now){return fields.stream().filter(f->f.friendly&&f.expires>now&&f.contains(x,y,z)).mapToDouble(f->CareerSkills.redBonus(f.rank)).max().orElse(0);}
    public boolean green(double x,double y,double z,long now){return fields.stream().anyMatch(f->f.rank>=3&&f.expires>now&&f.contains(x,y,z));}
    public boolean ownField(double x,double y,double z,long now){return field!=null&&field.expires>now&&field.contains(x,y,z);}
}
