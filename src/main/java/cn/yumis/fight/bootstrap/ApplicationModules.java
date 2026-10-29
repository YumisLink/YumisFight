package cn.yumis.fight.bootstrap;
import cn.yumis.fight.architecture.ModuleRegistry;
import cn.yumis.fight.architecture.ModuleDescriptor;
import cn.yumis.fight.architecture.CommandRouter;

import cn.yumis.fight.architecture.*;
import java.util.*;

/** Application composition only. Adding gameplay modules changes this registration, not the protected registry. */
public final class ApplicationModules {
    public static final ModuleRegistry REGISTRY=new ModuleRegistry(List.of(
        module("affixes","attributes"),
        module("attributes"),
        module("character","attributes","inventory","presentation"),
        module("combat","attributes","character","equipment","presentation"),
        module("dungeons","character","combat","party","presentation"),
        module("economy","affixes","attributes","character","dungeons","equipment","inventory","presentation"),
        module("equipment","affixes","attributes","character","inventory","presentation","professions"),
        module("industry","economy","inventory","presentation"),
        module("inventory","presentation"),
        module("party","presentation"),
        module("presentation"),
        module("professions","presentation"),
        module("skills","attributes","character","combat","dungeons","equipment","presentation")
    ));
    private ApplicationModules(){}
    private static ModuleDescriptor module(String id,String... dependencies){return new ModuleDescriptor(id,Set.of(dependencies));}
    public static void validateCommands(CommandRouter<?,?> router){for(String owner:router.owners().values())REGISTRY.require(owner);}
}
