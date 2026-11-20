package cn.yumis.fight.modules.presentation.api.client;

import cn.yumis.fight.modules.mining.api.VeinMining;
import java.nio.file.*;
import java.util.regex.*;
public final class ClientSettings {
    public static String panel="medium",targetPosition="top_center";
    public static String uiFont="modern";
    public static final int MAX_DAMAGE_SCALE=4;
    public static final int TARGET_FRAME_WIDTH=184;
    public static int damageScale=2;
    public static double targetScale=1;
    public static boolean nearEffectFade=true,itemQualityFrames=true;
    static boolean menuShortcutDefaultsApplied;
    public static final int MAX_ENEMY_HEALTH_DISTANCE=256;
    public static int enemyHealthDistance=16;
    public static int veinMiningLimit=VeinMining.DEFAULT_LIMIT;
    public static boolean enemyHealthPerspective=false,attackNeutralMobs=false;
    public static boolean arkEnemyHealth=true,arkAllyHealth=true,arkEnemyDamage=true,arkAllyDamage=true;
    public static String globalTheme="light",menuTheme="dark",machineTheme="light";
    public static boolean menuFollowsTheme=false,machineFollowsTheme=true;
    public static String theme(ThemeDomain domain){return switch(domain){case GENERAL->globalTheme;case MENU->menuFollowsTheme?globalTheme:menuTheme;case MACHINE->machineFollowsTheme?globalTheme:machineTheme;};}
    private static String checkedTheme(String value,String fallback){return value.equals("dark")||value.equals("light")?value:fallback;}
    public static String themeName(String theme){return theme.equals("dark")?"黑灰透明":"灰色立体";}
    public static void toggleGlobalTheme(){globalTheme=globalTheme.equals("dark")?"light":"dark";save();UiTheme.preferencesChanged();}
    public static void follow(ThemeDomain domain,boolean enabled){String current=theme(domain);if(domain==ThemeDomain.MENU){menuTheme=current;menuFollowsTheme=enabled;}else if(domain==ThemeDomain.MACHINE){machineTheme=current;machineFollowsTheme=enabled;}save();UiTheme.preferencesChanged();}
    public static void toggleDomain(ThemeDomain domain){String next=theme(domain).equals("dark")?"light":"dark";if(domain==ThemeDomain.MENU){menuFollowsTheme=false;menuTheme=next;}else if(domain==ThemeDomain.MACHINE){machineFollowsTheme=false;machineTheme=next;}save();UiTheme.preferencesChanged();}
    private static Path path(){
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("yumis_dungeon_and_fight-modules-v1-client.toml");
    }
    public static void load(){
        try{
            if(!Files.exists(path())){
                save();
                return;
            }
            String s=Files.readString(path());
            menuShortcutDefaultsApplied=Boolean.parseBoolean(value(s,"menuShortcutDefaultsApplied","false"));
            panel=value(s,"panelSize","medium");
            targetPosition=value(s,"targetPosition","top_center");
            damageScale=Integer.parseInt(value(s,"damageScale","2"));
            targetScale=Double.parseDouble(value(s,"targetScale","1"));
            nearEffectFade=!value(s,"nearEffectFade","true").equalsIgnoreCase("false");
            itemQualityFrames=!value(s,"itemQualityFrames","true").equalsIgnoreCase("false");
            enemyHealthDistance=readEnemyHealthDistance(value(s,"enemyHealthDistance","16"));
            veinMiningLimit=readVeinMiningLimit(value(s,"veinMiningLimit",Integer.toString(VeinMining.DEFAULT_LIMIT)));
            enemyHealthPerspective=Boolean.parseBoolean(value(s,"enemyHealthPerspective","false"));
            attackNeutralMobs=Boolean.parseBoolean(value(s,"attackNeutralMobs","false"));
            arkEnemyHealth=Boolean.parseBoolean(value(s,"arkEnemyHealth","true"));
            arkAllyHealth=Boolean.parseBoolean(value(s,"arkAllyHealth","true"));
            arkEnemyDamage=Boolean.parseBoolean(value(s,"arkEnemyDamage","true"));
            arkAllyDamage=Boolean.parseBoolean(value(s,"arkAllyDamage","true"));
            uiFont=value(s,"uiFont","modern");
            globalTheme=checkedTheme(value(s,"globalTheme","light"),"light");
            menuTheme=checkedTheme(value(s,"menuTheme","dark"),"dark");
            machineTheme=checkedTheme(value(s,"machineTheme","light"),"light");
            menuFollowsTheme=Boolean.parseBoolean(value(s,"menuFollowsTheme","false"));
            machineFollowsTheme=Boolean.parseBoolean(value(s,"machineFollowsTheme","true"));
            if(!uiFont.equals("modern")&&!uiFont.matches("[a-f0-9]{64}"))uiFont="modern";
            if(!java.util.Set.of("small","medium","large").contains(panel))panel="medium";
            if(damageScale<1||damageScale>MAX_DAMAGE_SCALE)damageScale=2;
            if(targetScale!=1&&targetScale!=1.5&&targetScale!=2)targetScale=1;
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
    private static int readVeinMiningLimit(String raw){
        try{return Math.clamp(Integer.parseInt(raw),1,VeinMining.MAX_LIMIT);}
        catch(NumberFormatException ignored){return VeinMining.DEFAULT_LIMIT;}
    }
    public static void save(){
        try{
            Files.createDirectories(path().getParent());
            Files.writeString(path(),"# 本机界面与战斗显示设置；保存后立即生效。\npanelSize=\""+panel+"\"\ndamageScale="+damageScale+"\ntargetPosition=\""+targetPosition+"\"\ntargetScale="+targetScale+"\nitemQualityFrames="+itemQualityFrames+"\nitemQualityFrames="+itemQualityFrames+"\nnearEffectFade="+nearEffectFade+"\nuiFont=\""+uiFont+"\"\n# light 为灰色立体，dark 为黑灰透明。\nglobalTheme=\""+globalTheme+"\"\n# 取消跟随时保留该类面板当时的主题。\nmenuFollowsTheme="+menuFollowsTheme+"\nmenuTheme=\""+menuTheme+"\"\nmachineFollowsTheme="+machineFollowsTheme+"\nmachineTheme=\""+machineTheme+"\"\n"
                +"arkEnemyHealth="+arkEnemyHealth+"\narkAllyHealth="+arkAllyHealth+"\narkEnemyDamage="+arkEnemyDamage+"\narkAllyDamage="+arkAllyDamage+"\n"
                +"enemyHealthDistance="+enemyHealthDistance+"\nenemyHealthPerspective="+enemyHealthPerspective+"\nattackNeutralMobs="+attackNeutralMobs+"\n"
                +"veinMiningLimit="+veinMiningLimit+"\n"
                +"menuShortcutDefaultsApplied="+menuShortcutDefaultsApplied+"\n");
        }
        catch(Exception e){
            ClientUi.current().message("设置保存失败："+e.getMessage());
        }
    }
}
