package cn.yumis.fight.modules.presentation.api.client;

import cn.yumis.fight.modules.mining.api.VeinMining;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.*;
public final class ClientSettings {
    private static final Gson JSON = new Gson();
    private static final Pattern COLLAPSED_QUESTS_LINE = Pattern.compile("(?m)^collapsedQuests\\h*=\\h*(\\[[^\\r\\n]*\\])\\h*$");
    private static final Set<String> collapsedQuests = new TreeSet<>();
    public static String panel="medium",targetPosition="top_center";
    public static String uiFont="modern";
    public static final int MAX_DAMAGE_SCALE=4;
    public static final int TARGET_FRAME_WIDTH=184;
    public static final int MAX_NEAR_EFFECT_FADE_DISTANCE_STEPS=500;
    public static final int MAX_SKILL_VOLUME_PERCENT = 100;
    private static final int DEFAULT_NEAR_EFFECT_FADE_DISTANCE_STEPS=85;
    private static int skillVolumePercent = MAX_SKILL_VOLUME_PERCENT;
    public static int damageScale=2;
    public static double targetScale=1;
    public static boolean nearEffectFade=true,itemQualityFrames=true;
    public static int nearEffectFadeDistanceSteps=DEFAULT_NEAR_EFFECT_FADE_DISTANCE_STEPS;
    static boolean menuShortcutDefaultsApplied;
    public static boolean chatEnterDefaultApplied;
    public static final int MAX_ENEMY_HEALTH_DISTANCE=256;
    public static int enemyHealthDistance=16;
    public static int veinMiningLimit=VeinMining.DEFAULT_LIMIT;
    public static boolean enemyHealthPerspective=false,attackNeutralMobs=false;
    /** 次元背包自动收纳：默认关；开启后默认仅在普通背包放不下时才收纳。 */
    public static boolean bagAutoStore=false,bagStoreOnlyWhenFull=true;
    public static boolean arkEnemyHealth=true,arkAllyHealth=true,arkEnemyDamage=true,arkAllyDamage=true;
    static final String PIXEL_THEME="pixel";
    public static String globalTheme=PIXEL_THEME,menuTheme=PIXEL_THEME,machineTheme=PIXEL_THEME;
    public static boolean menuFollowsTheme=true,machineFollowsTheme=true;
    public static String theme(ThemeDomain domain){return switch(domain){case GENERAL->globalTheme;case MENU->menuFollowsTheme?globalTheme:menuTheme;case MACHINE->machineFollowsTheme?globalTheme:machineTheme;};}
    private static String checkedTheme(String value){
        return switch(value){
            case "light","dark",PIXEL_THEME -> value;
            default -> PIXEL_THEME;
        };
    }
    public static boolean bagUsesMenuTheme(){return machineFollowsTheme&&!menuFollowsTheme&&PIXEL_THEME.equals(menuTheme);}
    public static String themeName(String theme){
        return switch(theme){
            case "light" -> "灰色立体";
            case "dark" -> "黑灰透明";
            default -> "深色像素";
        };
    }
    private static String nextTheme(String theme){
        return switch(theme){
            case PIXEL_THEME -> "light";
            case "light" -> "dark";
            default -> PIXEL_THEME;
        };
    }
    public static void toggleGlobalTheme(){globalTheme=nextTheme(globalTheme);save();UiTheme.preferencesChanged();}
    public static void follow(ThemeDomain domain,boolean enabled){String current=theme(domain);if(domain==ThemeDomain.MENU){menuTheme=current;menuFollowsTheme=enabled;}else if(domain==ThemeDomain.MACHINE){machineTheme=current;machineFollowsTheme=enabled;}save();UiTheme.preferencesChanged();}
    public static void toggleDomain(ThemeDomain domain){
        String next=nextTheme(theme(domain));
        if(domain==ThemeDomain.MENU){
            menuFollowsTheme=false;
            menuTheme=next;
        }else if(domain==ThemeDomain.MACHINE){
            machineFollowsTheme=false;
            machineTheme=next;
        }
        save();
        UiTheme.preferencesChanged();
    }
    public static boolean questCollapsed(String id) {
        return collapsedQuests.contains(id);
    }
    public static void toggleQuestCollapsed(String id) {
        if (!collapsedQuests.remove(id)) collapsedQuests.add(id);
        save();
    }
    public static int skillVolumePercent() {
        return skillVolumePercent;
    }
    public static void setSkillVolumePercent(int percent) {
        skillVolumePercent = Math.clamp(percent, 0, MAX_SKILL_VOLUME_PERCENT);
        save();
    }
    private static Path path(){
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("yumis_dungeon_and_fight-modules-v1-client.toml");
    }
    public static void load(){
        collapsedQuests.clear();
        try{
            if(!Files.exists(path())){
                save();
                return;
            }
            String s=Files.readString(path());
            menuShortcutDefaultsApplied=Boolean.parseBoolean(value(s,"menuShortcutDefaultsApplied","false"));
            chatEnterDefaultApplied=Boolean.parseBoolean(value(s,"chatEnterDefaultApplied","false"));
            panel=value(s,"panelSize","medium");
            targetPosition=value(s,"targetPosition","top_center");
            damageScale=Integer.parseInt(value(s,"damageScale","2"));
            targetScale=Double.parseDouble(value(s,"targetScale","1"));
            skillVolumePercent=readSkillVolumePercent(value(s,"skillVolumePercent",""));
            nearEffectFade=!value(s,"nearEffectFade","true").equalsIgnoreCase("false");
            nearEffectFadeDistanceSteps=readNearEffectFadeDistance(value(s,"nearEffectFadeDistance",""));
            itemQualityFrames=!value(s,"itemQualityFrames","true").equalsIgnoreCase("false");
            enemyHealthDistance=readEnemyHealthDistance(value(s,"enemyHealthDistance","16"));
            veinMiningLimit=readVeinMiningLimit(value(s,"veinMiningLimit",Integer.toString(VeinMining.DEFAULT_LIMIT)));
            enemyHealthPerspective=Boolean.parseBoolean(value(s,"enemyHealthPerspective","false"));
            attackNeutralMobs=Boolean.parseBoolean(value(s,"attackNeutralMobs","false"));
            bagAutoStore=Boolean.parseBoolean(value(s,"bagAutoStore","false"));
            bagStoreOnlyWhenFull=!value(s,"bagStoreOnlyWhenFull","true").equalsIgnoreCase("false");
            arkEnemyHealth=Boolean.parseBoolean(value(s,"arkEnemyHealth","true"));
            arkAllyHealth=Boolean.parseBoolean(value(s,"arkAllyHealth","true"));
            arkEnemyDamage=Boolean.parseBoolean(value(s,"arkEnemyDamage","true"));
            arkAllyDamage=Boolean.parseBoolean(value(s,"arkAllyDamage","true"));
            uiFont=value(s,"uiFont","modern");
            globalTheme=checkedTheme(value(s,"globalTheme",PIXEL_THEME));
            menuTheme=checkedTheme(value(s,"menuTheme",PIXEL_THEME));
            machineTheme=checkedTheme(value(s,"machineTheme",PIXEL_THEME));
            menuFollowsTheme=Boolean.parseBoolean(value(s,"menuFollowsTheme","true"));
            machineFollowsTheme=Boolean.parseBoolean(value(s,"machineFollowsTheme","true"));
            if(!uiFont.equals("modern")&&!uiFont.matches("[a-f0-9]{64}"))uiFont="modern";
            if(!java.util.Set.of("small","medium","large").contains(panel))panel="medium";
            if(damageScale<1||damageScale>MAX_DAMAGE_SCALE)damageScale=2;
            if(targetScale!=1&&targetScale!=1.5&&targetScale!=2)targetScale=1;
            var quests = COLLAPSED_QUESTS_LINE.matcher(s);
            if (quests.find()) {
                for (String id : JSON.fromJson(quests.group(1), String[].class)) {
                    if (id != null && !id.isBlank()) collapsedQuests.add(id);
                }
            }
            UiTheme.preferencesChanged();
        }
        catch(Exception e){
            ClientUi.current().message("设置读取失败："+e.getMessage());
        }
    }
    private static String value(String s,String key,String fallback){
        Matcher m=Pattern.compile("(?m)^"+key+"\\s*=\\s*\"?([\\w.]+)\"?\\s*$").matcher(s);
        return m.find()?m.group(1):fallback;
    }
    private static int readEnemyHealthDistance(String raw){
        try{return Math.clamp(Integer.parseInt(raw),1,MAX_ENEMY_HEALTH_DISTANCE);}
        catch(NumberFormatException ignored){return 16;}
    }
    private static int readSkillVolumePercent(String raw) {
        try {
            return Math.clamp(Integer.parseInt(raw), 0, MAX_SKILL_VOLUME_PERCENT);
        } catch (NumberFormatException ignored) {
            return MAX_SKILL_VOLUME_PERCENT;
        }
    }
    private static int readNearEffectFadeDistance(String raw){
        try{
            double distance=Double.parseDouble(raw);
            if(Double.isFinite(distance))return (int)Math.round(Math.clamp(distance,0,MAX_NEAR_EFFECT_FADE_DISTANCE_STEPS/100d)*100);
        }catch(NumberFormatException ignored){}
        return DEFAULT_NEAR_EFFECT_FADE_DISTANCE_STEPS;
    }
    private static int readVeinMiningLimit(String raw){
        try{return Math.clamp(Integer.parseInt(raw),1,VeinMining.MAX_LIMIT);}
        catch(NumberFormatException ignored){return VeinMining.DEFAULT_LIMIT;}
    }
    public static void save(){
        try{
            Files.createDirectories(path().getParent());
            Files.writeString(path(),"# 本机界面与战斗显示设置；保存后立即生效。\npanelSize=\""+panel+"\"\ndamageScale="+damageScale+"\ntargetPosition=\""+targetPosition+"\"\ntargetScale="+targetScale+"\nitemQualityFrames="+itemQualityFrames+"\nitemQualityFrames="+itemQualityFrames+"\nnearEffectFade="+nearEffectFade+"\nuiFont=\""+uiFont+"\"\n# light 为灰色立体，dark 为黑灰透明，pixel 为深色像素。\nglobalTheme=\""+globalTheme+"\"\n# 取消跟随时保留该类面板当时的主题。\nmenuFollowsTheme="+menuFollowsTheme+"\nmenuTheme=\""+menuTheme+"\"\nmachineFollowsTheme="+machineFollowsTheme+"\nmachineTheme=\""+machineTheme+"\"\n"
                +"nearEffectFadeDistance="+(nearEffectFadeDistanceSteps/100d)+"\n"
                +"skillVolumePercent="+skillVolumePercent+"\n"
                +"arkEnemyHealth="+arkEnemyHealth+"\narkAllyHealth="+arkAllyHealth+"\narkEnemyDamage="+arkEnemyDamage+"\narkAllyDamage="+arkAllyDamage+"\n"
                +"enemyHealthDistance="+enemyHealthDistance+"\nenemyHealthPerspective="+enemyHealthPerspective+"\nattackNeutralMobs="+attackNeutralMobs+"\n"
                +"veinMiningLimit="+veinMiningLimit+"\n"
                +"bagAutoStore="+bagAutoStore+"\nbagStoreOnlyWhenFull="+bagStoreOnlyWhenFull+"\n"
                +"menuShortcutDefaultsApplied="+menuShortcutDefaultsApplied+"\n"
                +"chatEnterDefaultApplied="+chatEnterDefaultApplied+"\n"
                +"collapsedQuests="+JSON.toJson(collapsedQuests)+"\n");
        }
        catch(Exception e){
            ClientUi.current().message("设置保存失败："+e.getMessage());
        }
    }
}
