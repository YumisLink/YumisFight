package cn.yumis.fight.modules.combat.api;
import java.util.regex.*;
/** Server-owned combat timeout; an event keeps the duration selected when it occurred. */
public record CombatSettings(double combatTimeoutSeconds) {
    public static final CombatSettings DEFAULT=new CombatSettings(5);
    public CombatSettings {if(!Double.isFinite(combatTimeoutSeconds)||combatTimeoutSeconds<=0||combatTimeoutSeconds>Long.MAX_VALUE/1000d||Math.round(combatTimeoutSeconds*1000)<=0)throw new IllegalArgumentException("combatTimeoutSeconds必须为有效正秒数");}
    public long delayMillis(){return Math.round(combatTimeoutSeconds*1000);}
    public static CombatSettings parse(String toml){Matcher m=Pattern.compile("(?m)^\\s*combatTimeoutSeconds\\s*=([^\\r\\n#]*)").matcher(toml);if(!m.find())return DEFAULT;double seconds=Double.parseDouble(m.group(1).trim());if(m.find())throw new IllegalArgumentException("重复combatTimeoutSeconds配置");return new CombatSettings(seconds);}
}
