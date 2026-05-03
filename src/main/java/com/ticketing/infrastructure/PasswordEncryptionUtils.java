package com.ticketing.infrastructure;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordEncryptionUtils {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Hashes a raw password using BCrypt.
     *
     * @param password raw password
     * @return BCrypt hashed password
     */
    public String encryptPassword(String password) {
        return passwordEncoder.encode(password);
    }

    /**
     * Checks whether a raw password matches a stored hash.
     *
     * @param password raw password
     * @param hashedPassword stored password hash
     * @return true if matched
     */
    public boolean matches(String password, String hashedPassword) {
        return passwordEncoder.matches(password, hashedPassword);
    }
}