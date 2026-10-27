package cn.yumis.fight.client;
import java.nio.file.*;
import java.util.regex.*;
public final class ClientSettings {
    public static String panel="medium",targetPosition="top_center";
    public static String uiFont="modern";
    public static int damageScale=2,migration;
    public static double targetScale=1;
    public static boolean characterDark;
    private static Path path(){
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("yumis_dungeon_and_fight-client.toml");
    }
    public static void load(){
        try{
            if(!Files.exists(path())){
                save();
                return;
            }
            String s=Files.readString(path());
            panel=value(s,"panelSize","medium");
            targetPosition=value(s,"targetPosition","top_center");
            damageScale=Integer.parseInt(value(s,"damageScale","2"));
            targetScale=Double.parseDouble(value(s,"targetScale","1"));
            migration=Integer.parseInt(value(s,"keyMigrationVersion","0"));
            uiFont=value(s,"uiFont","modern");
            characterDark=Boolean.parseBoolean(value(s,"characterDarkMode","false"));
            boolean migrateFont=uiFont.equals("noto");
            if(migrateFont)uiFont="modern";
            if(!uiFont.equals("modern")&&!uiFont.matches("[a-f0-9]{64}"))uiFont="modern";
            if(!java.util.Set.of("small","medium","large").contains(panel))panel="medium";
            if(!java.util.Set.of(1,2,4).contains(damageScale))damageScale=2;
            if(targetScale!=1&&targetScale!=1.5&&targetScale!=2)targetScale=1;
            if(migrateFont)save();
        }
        catch(Exception e){
            ClientState.INSTANCE.message("设置读取失败："+e.getMessage());
        }
    }
    private static String value(String s,String key,String fallback){
        Matcher m=Pattern.compile("(?m)^"+key+"\\s*=\\s*\"?([\\w.]+)\"?\\s*$").matcher(s);
        return m.find()?m.group(1):fallback;
    }
    public static void save(){
        try{
            Files.createDirectories(path().getParent());
            Files.writeString(path(),"panelSize=\""+panel+"\"\ndamageScale="+damageScale+"\ntargetPosition=\""+targetPosition+"\"\ntargetScale="+targetScale+"\nkeyMigrationVersion="+migration+"\nuiFont=\""+uiFont+"\"\ncharacterDarkMode="+characterDark+"\n");
        }
        catch(Exception e){
            ClientState.INSTANCE.message("设置保存失败："+e.getMessage());
        }
    }
}
