package cn.yumis.fight.architecture;

import java.util.Objects;
/** 底层架构文件：通用机制。修改前须用户明确授权，并逐批通过 question tool 确认已 GitHub fetch。 */
public record DefinitionSnapshot<T>(long revision,T value) {
    public DefinitionSnapshot {if(revision<0)throw new IllegalArgumentException("Negative revision");Objects.requireNonNull(value);}
}

