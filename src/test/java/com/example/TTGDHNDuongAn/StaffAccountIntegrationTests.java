package com.example.TTGDHNDuongAn;

import com.example.TTGDHNDuongAn.service.StaffProfileService;
import com.example.TTGDHNDuongAn.service.StaffProfileService.Input;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=${STAFF_TEST_DB_URL}",
    "SETUP_ADMIN_EMAIL=", "SETUP_ADMIN_PASSWORD=", "spring.docker.compose.enabled=false",
    "logging.level.root=WARN", "debug=false"})
@EnabledIfEnvironmentVariable(named="STAFF_TEST_DB_URL", matches=".+")
class StaffAccountIntegrationTests {
    @Autowired StaffProfileService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;

    private Input input(String email, String role) {
        return new Input("Test nhân sự", role, null, null, email, LocalDate.of(2024,1,1), "ACTIVE");
    }

    @Test void createsBothRolesAndUpdatesLinkedAccountWithForeignKeyIntact() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            for (String role : new String[]{"ADMIN", "TEACHER"}) {
                String email = UUID.randomUUID()+"@staff-test.invalid";
                var profile = service.create(input(email.toUpperCase(), role));
                var account = jdbc.queryForMap("SELECT u.* FROM app_user u JOIN staff s ON s.user_id=u.id WHERE s.id=?", profile.id());
                assertEquals(email, account.get("email"));
                assertEquals("Test nhân sự", account.get("display_name"));
                assertEquals(role, profile.accountRole());
                assertEquals(role, account.get("role"));
                assertEquals("ACTIVE", account.get("status"));
                assertTrue(encoder.matches("123456", (String) account.get("password_hash")));

                String changedRole = role.equals("ADMIN") ? "TEACHER" : "ADMIN";
                service.update(profile.id(), input("new-"+email, changedRole));
                var updated = jdbc.queryForMap("SELECT u.*,s.account_role FROM app_user u JOIN staff s ON s.user_id=u.id WHERE s.id=?", profile.id());
                assertEquals(account.get("id"), updated.get("id"));
                assertEquals(account.get("password_hash"), updated.get("password_hash"));
                assertEquals(changedRole, updated.get("role"));
                assertEquals(changedRole, updated.get("account_role"));
                assertEquals("new-"+email, updated.get("email"));
            }
        });
    }

    @Test void duplicateEmailAndRollbackLeaveNoOrphanAccounts() {
        String email = UUID.randomUUID()+"@staff-test.invalid";
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            service.create(input(email, "TEACHER"));
            int before = jdbc.queryForObject("SELECT COUNT(*) FROM staff", Integer.class);
            var error = assertThrows(ResponseStatusException.class, () -> service.create(input(email, "ADMIN")));
            assertEquals(409, error.getStatusCode().value());
            assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM staff", Integer.class));
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE email=?", Integer.class, email));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE email=?", Integer.class, email));
    }

    @Test void savingLegacyStaffCreatesMissingAccount() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            String id = UUID.randomUUID().toString();
            String email = id+"@staff-test.invalid";
            jdbc.update("INSERT INTO staff(id,code,full_name,joined_on) VALUES(?,?,'Legacy','2024-01-01')", id, "TEST-"+id.substring(0,8));
            service.update(id, input(email, "ADMIN"));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM staff s JOIN app_user u ON u.id=s.user_id AND u.role=s.account_role WHERE s.id=? AND u.email=? AND u.role='ADMIN'",
                Integer.class, id, email));
        });
    }
}
