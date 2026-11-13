package cn.yumis.fight.modules.npc.api;

import cn.yumis.fight.modules.presentation.api.RichText;
import java.util.*;

/** The guide NPC owns this six-lesson content and player-wide progress, independently of career slots. */
public final class NpcTutorial {
    public static final String NPC="elaina";
    public static final Set<String> GOALS=Set.of("choose_job","learn_skill","bind_skill","practice_hit","allocate","craft");
    public record ItemGrant(String item,String material,int count) {
        public ItemGrant {if(count<1||item==null||!item.matches("[a-z0-9_./:-]+"))throw new IllegalArgumentException("引导奖励物品无效");}
    }
    public record Reward(long experience,long gold,List<ItemGrant> items) {
        public Reward {if(experience<0||gold<0)throw new IllegalArgumentException("引导奖励不可为负");items=items==null?List.of():List.copyOf(items);}
    }
    public record Step(String id,String goal,RichText title,RichText objective,List<RichText> dialogue,Reward reward,String page) {
        public Step {if(id==null||!id.matches("[a-z0-9_]+")||!GOALS.contains(goal)||title==null||objective==null||reward==null||dialogue==null||dialogue.isEmpty())throw new IllegalArgumentException("引导任务定义不完整");dialogue=List.copyOf(dialogue);}
    }
    public record Definition(List<RichText> introduction,List<Step> steps,List<RichText> completion) {
        public Definition {
            introduction=List.copyOf(Objects.requireNonNull(introduction));steps=List.copyOf(Objects.requireNonNull(steps));completion=List.copyOf(Objects.requireNonNull(completion));
            if(introduction.isEmpty()||steps.size()!=6||completion.isEmpty()||steps.stream().map(Step::id).distinct().count()!=steps.size()||!steps.stream().map(Step::goal).collect(java.util.stream.Collectors.toSet()).equals(GOALS))throw new IllegalArgumentException("伊蕾娜引导须包含六项不同目标");
        }
    }
    public static final class State {
        public long revision;
        public boolean started,promotionHinted;
        public Set<String> goals=new LinkedHashSet<>(),claimed=new LinkedHashSet<>();
        public String dialogue="introduction";
        public int page;
    }
    private NpcTutorial(){}
    public static Definition definition(){var value=NpcDefinitions.current().require(NPC).tutorial();if(value==null)throw new IllegalStateException("伊蕾娜引导内容未加载");return value;}
    public static Step current(State state){return definition().steps().stream().filter(step->!state.claimed.contains(step.id())).findFirst().orElse(null);}
    public static boolean complete(State state,Step step){return state.started&&state.goals.contains(step.goal());}
}
