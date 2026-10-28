package cn.yumis.fight.modules.economy.api;
import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.finite;
import java.util.*;
import java.util.regex.*;

/** Server-owned configuration, published only after the whole reload validates. */
public record RewardSettings(double combatExperienceMultiplier,double killGoldMultiplier) {
    public static final RewardSettings DEFAULT=new RewardSettings(1,1);
    public RewardSettings{
        finite(combatExperienceMultiplier);finite(killGoldMultiplier);
        if(combatExperienceMultiplier<0||killGoldMultiplier<0)throw new IllegalArgumentException("经验、金币倍率不可为负");
    }
    public static RewardSettings parse(String toml){
        Map<String,Double> values=new HashMap<>();
        Matcher m=Pattern.compile("(?m)^\\s*(combatExperienceMultiplier|killGoldMultiplier)\\s*=([^\\r\\n#]*)").matcher(toml);
        while(m.find()){double value=Double.parseDouble(m.group(2).trim());if(values.put(m.group(1),value)!=null)throw new IllegalArgumentException("重复奖励配置："+m.group(1));}
        return new RewardSettings(values.getOrDefault("combatExperienceMultiplier",1d),values.getOrDefault("killGoldMultiplier",1d));
    }
}
