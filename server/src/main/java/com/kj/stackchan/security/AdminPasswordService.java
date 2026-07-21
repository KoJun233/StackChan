package com.kj.stackchan.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminPasswordService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminPasswordService(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        AdminUserEntity admin = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid current password"));
        if (!passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
            throw new BadCredentialsException("Invalid current password");
        }
        admin.changePasswordHash(passwordEncoder.encode(newPassword));
    }
}
