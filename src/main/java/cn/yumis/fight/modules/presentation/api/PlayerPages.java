package cn.yumis.fight.modules.presentation.api;
import java.util.*;
/** Menu content registration and visible tab order. */
public final class PlayerPages {
    public record Page(String id,String title,String tab,String icon,String openCommand,boolean screen){}
    private static final List<Page> PAGES=new ArrayList<>();
    static {
        register(new Page("character","角色属性","属性","character","",true));
        register(new Page("skills","职业技能","技能","skills","",true));
        register(new Page("professions","副职业","副职","professions","professions",true));
        register(new Page("party","战斗队伍","队伍","party","",true));
        register(new Page("dungeons","地下城","副本","dungeons","",true));
        register(new Page("settings","设置","设置","settings","",true));
        register(new Page("shop","商城","商城","shop","",true));
        register(new Page("bag","次元背包","背包","dimensional_bag","bag",false));
        register(new Page("sale","出售装备","出售","shop","",true));
        register(new Page("affix_compendium","词条宝典","词条","skills","",true));
        register(new Page("damage_analysis","伤害分析","伤害","skills","",true));
    }
    private PlayerPages(){}
    public static synchronized int register(Page page){
        Objects.requireNonNull(page);if(page.id()==null||page.id().isBlank()||page.title()==null||page.tab()==null||page.icon()==null||page.openCommand()==null)throw new IllegalArgumentException("Invalid page");
        if(PAGES.stream().anyMatch(existing->existing.id().equals(page.id())))throw new IllegalArgumentException("Duplicate page: "+page.id());PAGES.add(page);return PAGES.size()-1;
    }
    public static synchronized List<Page> all(){return List.copyOf(PAGES);}
    public static Page at(int index){return PAGES.get(index);}
}
