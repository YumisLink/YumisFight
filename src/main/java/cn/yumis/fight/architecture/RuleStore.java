package cn.yumis.fight.architecture;

import java.util.Objects;
import java.util.function.Consumer;
/** 底层架构文件：通用机制。修改前须用户明确授权，并逐批通过 question tool 确认已 GitHub fetch。 */
public final class RuleStore<T> {
    private volatile DefinitionSnapshot<T> current;
    private final Consumer<T> validate;
    public RuleStore(T initial,Consumer<T> validate){this.validate=Objects.requireNonNull(validate);validate.accept(initial);current=new DefinitionSnapshot<>(0,initial);}
    public DefinitionSnapshot<T> snapshot(){return current;}
    public synchronized DefinitionSnapshot<T> publish(T next){validate.accept(next);var candidate=new DefinitionSnapshot<>(Math.incrementExact(current.revision()),next);current=candidate;return candidate;}
}

