package com.ticketing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Ticketing System Tests")
public class Tests {

    private TestUtilities testUtils;

    @BeforeEach
    void setUp() {
        testUtils = new TestUtilities();
    }

    // String Manipulation Tests
    @Test
    @DisplayName("Should create valid email address")
    void testEmailCreation() {
        String email = testUtils.createEmail("john", "example.com");
        assertEquals("john@example.com", email);
        assertTrue(email.contains("@"));
    }

    @Test
    @DisplayName("Should validate email format")
    void testEmailValidation() {
        assertTrue(testUtils.isValidEmail("user@example.com"));
        assertFalse(testUtils.isValidEmail("invalid-email"));
        assertFalse(testUtils.isValidEmail("@example.com"));
    }

    // Numeric Tests
    @Test
    @DisplayName("Should calculate ticket price correctly")
    void testTicketPriceCalculation() {
        double basePrice = 100.0;
        double taxRate = 0.1;
        double totalPrice = testUtils.calculatePrice(basePrice, taxRate);
        assertEquals(110.0, totalPrice, 0.01);
    }

    @ParameterizedTest
    @ValueSource(doubles = {10.0, 50.0, 100.0, 500.0})
    @DisplayName("Should apply discount to various prices")
    void testDiscountApplication(double price) {
        double discountedPrice = testUtils.applyDiscount(price, 0.2);
        assertTrue(discountedPrice < price);
        assertEquals(price * 0.8, discountedPrice, 0.01);
    }

    // Collection Tests
    @Test
    @DisplayName("Should manage user list correctly")
    void testUserListManagement() {
        List<String> users = new ArrayList<>();
        users.add("Alice");
        users.add("Bob");
        users.add("Charlie");

        assertEquals(3, users.size());
        assertTrue(users.contains("Alice"));
        assertFalse(users.contains("Diana"));
    }

    @Test
    @DisplayName("Should track event registrations")
    void testEventRegistrationTracking() {
        Map<String, Integer> registrations = new HashMap<>();
        registrations.put("Conference", 150);
        registrations.put("Workshop", 50);
        registrations.put("Meetup", 25);

        assertEquals(3, registrations.size());
        assertEquals(150, registrations.get("Conference"));
        assertEquals(225, registrations.values().stream().mapToInt(Integer::intValue).sum());
    }

    // Parametrized Tests with CSV
    @ParameterizedTest
    @CsvSource({
            "user1, 100, 110",
            "user2, 50, 55",
            "user3, 200, 220"
    })
    @DisplayName("Should calculate total with tax for different amounts")
    void testTaxCalculationForMultipleUsers(String username, double amount, double expected) {
        double result = testUtils.calculatePrice(amount, 0.1);
        assertEquals(expected, result, 0.01);
    }

    // String Parsing Tests
    @Test
    @DisplayName("Should parse ticket code correctly")
    void testTicketCodeParsing() {
        String ticketCode = testUtils.generateTicketCode("EVT001", 12345);
        assertTrue(ticketCode.startsWith("EVT001"));
        assertTrue(ticketCode.length() > 6);
    }

    @Test
    @DisplayName("Should handle empty strings safely")
    void testEmptyStringHandling() {
        String result = testUtils.sanitizeInput("");
        assertEquals("", result);
        assertNotNull(result);
    }

    // Boolean Logic Tests
    @Test
    @DisplayName("Should verify user age eligibility")
    void testAgeEligibility() {
        assertTrue(testUtils.isEligibleAge(25));
        assertTrue(testUtils.isEligibleAge(18));
        assertFalse(testUtils.isEligibleAge(17));
    }

    @Test
    @DisplayName("Should validate ticket availability")
    void testTicketAvailability() {
        assertTrue(testUtils.isTicketAvailable(100, 50));  // 100 total, 50 sold
        assertTrue(testUtils.isTicketAvailable(100, 100)); // fully booked
        assertTrue(testUtils.isTicketAvailable(100, 99));  // almost sold out
    }

    // Exception Tests
    @Test
    @DisplayName("Should throw exception for invalid quantity")
    void testInvalidQuantityException() {
        assertThrows(IllegalArgumentException.class, () -> {
            testUtils.validateQuantity(-5);
        });
    }

    @Test
    @DisplayName("Should throw exception for null user")
    void testNullUserException() {
        assertThrows(NullPointerException.class, () -> {
            testUtils.validateUser(null);
        });
    }

    // Math Operations
    @Test
    @DisplayName("Should calculate average ticket price")
    void testAverageTicketPrice() {
        List<Double> prices = List.of(100.0, 150.0, 200.0);
        double average = testUtils.calculateAverage(prices);
        assertEquals(150.0, average, 0.01);
    }

    @Test
    @DisplayName("Should find minimum ticket price")
    void testMinimumTicketPrice() {
        List<Double> prices = List.of(100.0, 50.0, 200.0, 75.0);
        double min = testUtils.findMinPrice(prices);
        assertEquals(50.0, min);
    }

    // Integration-style Tests
    @Test
    @DisplayName("Should process complete ticket order")
    void testCompleteTicketOrder() {
        double basePrice = 100.0;
        double taxRate = 0.1;
        double discountRate = 0.05;

        double priceWithTax = testUtils.calculatePrice(basePrice, taxRate);
        double finalPrice = testUtils.applyDiscount(priceWithTax, discountRate);

        assertEquals(104.5, finalPrice, 0.01);
        assertTrue(finalPrice < priceWithTax);
    }

    @Test
    @DisplayName("Should generate valid purchase confirmation")
    void testPurchaseConfirmation() {
        String confirmation = testUtils.generateConfirmation("ORD001", "user@example.com", 100.0);
        assertTrue(confirmation.contains("ORD001"));
        assertTrue(confirmation.contains("user@example.com"));
        assertTrue(confirmation.contains("100"));
    }

    // Utility class for tests
    public static class TestUtilities {

        public String createEmail(String username, String domain) {
            return username + "@" + domain;
        }

        public boolean isValidEmail(String email) {
            return email.contains("@") && email.indexOf("@") > 0 && email.lastIndexOf("@") < email.length() - 1;
        }

        public double calculatePrice(double basePrice, double taxRate) {
            return basePrice * (1 + taxRate);
        }

        public double applyDiscount(double price, double discountRate) {
            return price * (1 - discountRate);
        }

        public String generateTicketCode(String eventId, long timestamp) {
            return eventId + "-" + System.identityHashCode(timestamp);
        }

        public String sanitizeInput(String input) {
            return input == null ? "" : input.trim();
        }

        public boolean isEligibleAge(int age) {
            return age >= 18;
        }

        public boolean isTicketAvailable(int total, int sold) {
            return sold <= total;
        }

        public void validateQuantity(int quantity) {
            if (quantity < 0) {
                throw new IllegalArgumentException("Quantity cannot be negative");
            }
        }

        public void validateUser(String user) {
            if (user == null) {
                throw new NullPointerException("User cannot be null");
            }
        }

        public double calculateAverage(List<Double> prices) {
            return prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        public double findMinPrice(List<Double> prices) {
            return prices.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        }

        public String generateConfirmation(String orderId, String email, double amount) {
            return String.format("Confirmation ID: %s, Email: %s, Amount: %.2f", orderId, email, amount);
        }
    }
}
