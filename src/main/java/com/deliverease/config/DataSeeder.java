package com.deliverease.config;

import com.deliverease.model.*;
import com.deliverease.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates a default admin and two demo delivery partners on first start-up. */
@Component
public class DataSeeder implements CommandLineRunner {
    private final UserRepository users;
    private final PartnerRepository partners;
    private final PasswordEncoder encoder;

    public DataSeeder(UserRepository users, PartnerRepository partners, PasswordEncoder encoder) {
        this.users = users;
        this.partners = partners;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (users.countByRole(Role.ADMIN) == 0) {
            createUser("Admin", "admin@deliverease.com", "admin123", Role.ADMIN);
        }
        if (partners.count() == 0) {
            seedPartner("Ravi Kumar", "ravi@deliverease.com", "9000000001", "Bike");
            seedPartner("Anita Shaikh", "anita@deliverease.com", "9000000002", "Scooter");
        }
    }

    private User createUser(String name, String email, String password, Role role) {
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPassword(encoder.encode(password));
        u.setRole(role);
        return users.save(u);
    }

    private void seedPartner(String name, String email, String phone, String vehicle) {
        User u = createUser(name, email, "partner123", Role.DELIVERY_PARTNER);
        u.setPhone(phone);
        users.save(u);
        DeliveryPartner p = new DeliveryPartner();
        p.setName(name);
        p.setPhone(phone);
        p.setVehicleType(vehicle);
        p.setUser(u);
        partners.save(p);
    }
}
