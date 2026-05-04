// package  com.ticketing.domain.event;

// import com.ticketing.infrastructure.Interface.*;


// import com.ticketing.domain.member.User;
// import com.ticketing.domain.order.ActiveOrder;

// import java.util.Currency;

// public class NoDiscountPolicy implements IDiscountPolicy {
//     private final Currency defaultCurrency;

//     public NoDiscountPolicy(Currency defaultCurrency) {
//         this.defaultCurrency = defaultCurrency;
//     }

//     @Override
//     public Money applyTo(ActiveOrder order, User user) {
//         return Money.zero(defaultCurrency);
//     }
// }

