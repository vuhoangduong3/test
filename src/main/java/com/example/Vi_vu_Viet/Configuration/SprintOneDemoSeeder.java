package com.example.Vi_vu_Viet.Configuration;

import com.example.Vi_vu_Viet.Entity.Auth.UserAccount;
import com.example.Vi_vu_Viet.Entity.Trip.*;
import com.example.Vi_vu_Viet.Util.Enums.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@Profile("!test")
@ConditionalOnProperty(name = "app.seed.demo-data", havingValue = "true")
@RequiredArgsConstructor
public class SprintOneDemoSeeder implements ApplicationRunner {
    private final EntityManager entityManager;
    private final PasswordEncoder encoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!entityManager.createQuery("select c.id from BusCompany c where c.slug = :slug", java.util.UUID.class)
                .setParameter("slug", "vivuviet-demo").getResultList().isEmpty()) return;
        BusCompany company = new BusCompany();
        company.setName("ViVuViet Demo"); company.setSlug("vivuviet-demo");
        company.setTimezone("Asia/Ho_Chi_Minh"); company.setStatus(BusCompanyStatus.ACTIVE);
        entityManager.persist(company);
        UserAccount user = new UserAccount();
        user.setFull_name("Demo Admin"); user.setEmail("admin@vivuviet.demo");
        user.setPassword_hash(encoder.encode("Demo1234!")); user.setAccount_status(UserAccountStatus.ACTIVE);
        entityManager.persist(user);
        CompanyMember member = new CompanyMember();
        member.setCompany_id(company); member.setUser_id(user); member.setStatus(CompanyMemberStatus.ACTIVE);
        member.setJoined_at(LocalDateTime.now()); entityManager.persist(member);
        CompanyMemberRole role = new CompanyMemberRole();
        role.setCompany_id(company); role.setMembership_id(member); role.setRole(CompanyMemberRoleType.COMPANY_ADMIN);
        entityManager.persist(role);
        Area origin = new Area(); origin.setCode("DEMO-HN"); origin.setName("Ha Noi"); origin.setType("CITY"); origin.setStatus("ACTIVE"); entityManager.persist(origin);
        Area destination = new Area(); destination.setCode("DEMO-DN"); destination.setName("Da Nang"); destination.setType("CITY"); destination.setStatus("ACTIVE"); entityManager.persist(destination);
        Route route = new Route(); route.setCompany_id(company); route.setCode("DEMO-HN-DN"); route.setName("Ha Noi - Da Nang");
        route.setOrigin_area_id(origin); route.setDestination_area_id(destination); route.setDistance_km(new BigDecimal("760"));
        route.setDuration_minutes(900); route.setBase_fare_amount(450000L); route.setStatus(RouteStatus.ACTIVE); entityManager.persist(route);
    }
}
