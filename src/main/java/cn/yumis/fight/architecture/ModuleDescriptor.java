package cn.yumis.fight.architecture;

import java.util.Set;
/** 底层架构文件：通用机制。修改前须用户明确授权，并逐批通过 question tool 确认已 GitHub fetch。 */
public record ModuleDescriptor(String id, Set<String> dependencies) {
    public ModuleDescriptor { if(id==null||!id.matches("[a-z][a-z0-9_]*"))throw new IllegalArgumentException("Invalid module ID"); dependencies=Set.copyOf(dependencies); if(dependencies.contains(id))throw new IllegalArgumentException("Self dependency: "+id); }
}

