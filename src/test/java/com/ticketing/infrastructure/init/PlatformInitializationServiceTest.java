package com.ticketing.infrastructure.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.services.PlatformInitializationService;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.gateway.CancelResult;
import com.ticketing.domain.gateway.CustomerInfo;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.ITicketSupplyGateway;
import com.ticketing.domain.gateway.PaymentDetails;
import com.ticketing.domain.gateway.PaymentResult;
import com.ticketing.domain.gateway.RefundResult;
import com.ticketing.domain.gateway.SupplyResult;
import com.ticketing.domain.gateway.TicketRequest;
import com.ticketing.domain.system.StartupConfiguration;
import com.ticketing.infrastructure.InMemoryAdminRepository;

class PlatformInitializationServiceTest {

    private IAdminRepository adminRepository;
    private TestPaymentGateway paymentGateway;
    private TestTicketSupplyGateway supplyGateway;

    @BeforeEach
    void setUp() {
        adminRepository = new InMemoryAdminRepository();
        paymentGateway = new TestPaymentGateway();
        supplyGateway = new TestTicketSupplyGateway();
    }

    @Test
    void GivenValidConfig_WhenInitialize_ThenPlatformActiveAndAdminExists() {
        PlatformInitializationService service = serviceWith(new StartupConfiguration());

        PlatformInitializationService.InitializationResult result = service.initialize();

        assertTrue(result.success());
        assertTrue(service.isActive());
        assertEquals(1, adminRepository.findAll().size());
        assertTrue(adminRepository.existsByUsername("admin"));
        assertEquals(1, paymentGateway.reachabilityChecks);
        assertEquals(1, supplyGateway.reachabilityChecks);
        assertTrue(service.eventLog().contains("Platform initialization succeeded"));
    }

    @Test
    void GivenNoClearingService_WhenInitialize_ThenHaltsWithClearingError() {
        paymentGateway.reachable = false;
        PlatformInitializationService service = serviceWith(new StartupConfiguration());

        PlatformInitializationService.InitializationResult result = service.initialize();

        assertFalse(result.success());
        assertEquals("Unable to connect to clearing service", result.message());
        assertFalse(service.isActive());
        assertTrue(adminRepository.findAll().isEmpty());
        assertTrue(service.eventLog().contains("Platform initialization failed: Unable to connect to clearing service"));
    }

    @Test
    void GivenNoSupplyService_WhenInitialize_ThenHaltsWithSupplyError() {
        supplyGateway.reachable = false;
        PlatformInitializationService service = serviceWith(new StartupConfiguration());

        PlatformInitializationService.InitializationResult result = service.initialize();

        assertFalse(result.success());
        assertEquals("Unable to connect to supply service", result.message());
        assertFalse(service.isActive());
        assertTrue(adminRepository.findAll().isEmpty());
        assertTrue(service.eventLog().contains("Platform initialization failed: Unable to connect to supply service"));
    }

    @Test
    void GivenInvalidAdminCredentials_WhenInitialize_ThenHaltsWithCredentialsError() {
        StartupConfiguration invalidConfig = new StartupConfiguration(
                "",
                "not-an-email",
                "short",
                true,
                true
        );
        PlatformInitializationService service = serviceWith(invalidConfig);

        PlatformInitializationService.InitializationResult result = service.initialize();

        assertFalse(result.success());
        assertEquals("Invalid admin credentials", result.message());
        assertFalse(service.isActive());
        assertTrue(adminRepository.findAll().isEmpty());
    }

    @Test
    void GivenInitializedPlatform_WhenInitializeAgain_ThenAdminNotDuplicated() {
        PlatformInitializationService service = serviceWith(new StartupConfiguration());

        service.initialize();
        PlatformInitializationService.InitializationResult secondResult = service.initialize();

        assertTrue(secondResult.success());
        assertTrue(service.isActive());
        assertEquals(1, adminRepository.findAll().size());
    }

    private PlatformInitializationService serviceWith(StartupConfiguration startupConfiguration) {
        return new PlatformInitializationService(
                adminRepository,
                paymentGateway,
                supplyGateway,
                startupConfiguration
        );
    }

    private static final class TestPaymentGateway implements IPaymentGateway {
        private boolean reachable = true;
        private int reachabilityChecks;

        @Override
        public boolean isReachable() {
            reachabilityChecks++;
            return reachable;
        }

        @Override
        public PaymentResult charge(BigDecimal finalAmount, PaymentDetails details) {
            return PaymentResult.successful("payment-ok");
        }

        @Override
        public RefundResult refund(String transactionId, double amount) {
            return RefundResult.successful("refund-ok");
        }
    }

    private static final class TestTicketSupplyGateway implements ITicketSupplyGateway {
        private boolean reachable = true;
        private int reachabilityChecks;

        @Override
        public boolean isReachable() {
            reachabilityChecks++;
            return reachable;
        }

        @Override
        public SupplyResult issueTickets(List<TicketRequest> tickets, CustomerInfo customer) {
            return SupplyResult.successful(List.of("ticket-ok"));
        }

        @Override
        public CancelResult cancelTickets(List<String> ticketCodes) {
            return CancelResult.successful();
        }
    }
}
