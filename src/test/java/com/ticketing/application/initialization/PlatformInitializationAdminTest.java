package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ticketing.application.services.AdminService;
import com.ticketing.domain.admin.Admin;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.member.request.LoginRequest;
import com.ticketing.domain.member.response.LoginResponse;

@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(properties = {
    "ticketing.admin.username=testadmin",
    "ticketing.admin.password=testadmin123"
})
class PlatformInitializationAdminTest {

    @Autowired
    private IAdminRepository adminRepository;

    @Autowired
    private AdminService adminService;

    @Test
    void givenPlatformInitialized_whenCheckingAdmin_thenAdminExistsWithHashedPassword() {
        // Assert the admin exists
        Admin admin = adminRepository.findByUsername("testadmin").orElse(null);
        assertNotNull(admin, "Admin should be created on initialization");

        // Assert the password is NOT plaintext
        assertFalse("testadmin123".equals(admin.getEncryptedPassword()), "Password should be stored hashed");
        assertTrue(admin.getEncryptedPassword().length() > 20, "Password hash should be long");
    }

    @Autowired
    private com.ticketing.application.auth.ISessionTokenService sessionTokenService;

    @Test
    void givenPlatformInitialized_whenAdminLogsIn_thenLoginSucceeds() {
        // Generate a real guest token
        String guestToken = sessionTokenService.generateGuestToken();

        // Admin login
        LoginResponse response = adminService.adminLogin(new LoginRequest("testadmin", "testadmin123"), guestToken);

        // Assert success
        assertTrue(response.success(), "Admin login should succeed");
        assertNotNull(response.sessionToken(), "Should return a session token");
    }
}
