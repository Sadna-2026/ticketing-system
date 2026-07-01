package com.ticketing.infrastructure.persistence.parity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.lottery.ILotteryRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryLotteryRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;
import com.ticketing.infrastructure.persistence.JpaCompanyRepository;
import com.ticketing.infrastructure.persistence.JpaEventRepository;
import com.ticketing.infrastructure.persistence.JpaLotteryRepository;
import com.ticketing.infrastructure.persistence.JpaMemberRepository;
import com.ticketing.infrastructure.persistence.JpaOrderRepository;

/**
 * Acceptance check for #492: exactly one active implementation per repository
 * interface per persistence mode. The wiring is driven by
 * {@code @ConditionalOnProperty(name = "ticketing.persistence", ...)} on each adapter
 * — if a fresh implementation ever forgets the condition (or sets the wrong value),
 * two impls would be active at once and Spring would either fail to autowire or
 * silently pick one. This test makes that misconfiguration impossible to merge.
 *
 * <p>Two {@code @Nested} {@code @SpringBootTest} contexts cover the two modes. The
 * full context boots to make sure the conditional filtering is exactly what the
 * production code paths see — not a slice.
 */
@org.junit.jupiter.api.Tag("slow")
class PersistenceModeUniqueImplTest {

    @SpringBootTest(properties = {
            "ticketing.persistence=memory",
            "ticketing.seed.enabled=false",
            "ticketing.startup.initialize-platform=false"
    })
    @DisplayName("Memory mode wires exactly the in-memory impls (#492)")
    @Nested
    class MemoryMode {

        @Autowired
        private ApplicationContext ctx;

        @Test
        void exactlyOneImplPerWritableRepositoryInterface_andItIsTheInMemoryOne() {
            assertSingleBean(ctx, IMemberRepository.class, InMemoryMemberRepository.class);
            assertSingleBean(ctx, ICompanyRepository.class, InMemoryCompanyRepository.class);
            assertSingleBean(ctx, IEventRepository.class, InMemoryEventRepository.class);
            assertSingleBean(ctx, IOrderRepository.class, InMemoryOrderRepository.class);
            assertSingleBean(ctx, ILotteryRepository.class, InMemoryLotteryRepository.class);
        }
    }

    @SpringBootTest(properties = {
            "ticketing.persistence=jpa",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "ticketing.seed.enabled=false",
            "ticketing.startup.initialize-platform=false"
    })
    @DisplayName("JPA mode wires exactly the JPA impls (#492)")
    @Nested
    class JpaMode {

        @Autowired
        private ApplicationContext ctx;

        @Test
        void exactlyOneImplPerWritableRepositoryInterface_andItIsTheJpaOne() {
            assertSingleBean(ctx, IMemberRepository.class, JpaMemberRepository.class);
            assertSingleBean(ctx, ICompanyRepository.class, JpaCompanyRepository.class);
            assertSingleBean(ctx, IEventRepository.class, JpaEventRepository.class);
            assertSingleBean(ctx, IOrderRepository.class, JpaOrderRepository.class);
            assertSingleBean(ctx, ILotteryRepository.class, JpaLotteryRepository.class);
        }
    }

    private static <T> void assertSingleBean(ApplicationContext ctx, Class<T> iface, Class<? extends T> expectedImpl) {
        var beans = ctx.getBeansOfType(iface);
        assertThat(beans)
                .as("exactly one bean of type %s should be active in this persistence mode", iface.getSimpleName())
                .hasSize(1);
        assertThat(beans.values().iterator().next())
                .as("the active %s should be %s", iface.getSimpleName(), expectedImpl.getSimpleName())
                .isInstanceOf(expectedImpl);
    }
}
