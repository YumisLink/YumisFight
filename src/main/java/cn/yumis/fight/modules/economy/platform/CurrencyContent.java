package cn.yumis.fight.modules.economy.platform;
import cn.yumis.fight.modules.economy.api.CurrencyRules;

import static cn.yumis.fight.platform.ItemRegistry.*;
public final class CurrencyContent {
    private CurrencyContent(){}
    public static void register(){for(var currency:CurrencyRules.ALL)ALL.put(currency.id(),ITEMS.registerItem(currency.id(),p->new CurrencyItem(p,currency.rarity())));}
}

