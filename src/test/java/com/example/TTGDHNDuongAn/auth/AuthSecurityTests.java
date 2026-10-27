package com.example.TTGDHNDuongAn.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.junit.jupiter.api.TestInstance;

@SpringBootTest(properties = {
        "spring.datasource.url=${GOAL_CATALOG_TEST_DB_URL}",
        "SETUP_ADMIN_EMAIL=", "SETUP_ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "GOAL_CATALOG_TEST_DB_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthSecurityTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeAll
    void createTemporaryAccounts() {
        insertTestAccount("admin-auth-test@example.invalid", "Quản trị viên", "ADMIN", "Admin123!");
        insertTestAccount("teacher-auth-test@example.invalid", "Giáo viên kiểm thử", "TEACHER", "Teacher123!");
        insertTestAccount("parent-auth-test@example.invalid", "Phụ huynh kiểm thử", "PARENT", "Parent123!");
    }

    @AfterAll
    void removeTemporaryAccounts() {
        jdbcTemplate.update("DELETE FROM app_user WHERE email IN (?, ?, ?)",
                "admin-auth-test@example.invalid", "teacher-auth-test@example.invalid", "parent-auth-test@example.invalid");
    }

    private void insertTestAccount(String email, String displayName, String role, String password) {
        jdbcTemplate.update(
                "INSERT INTO app_user (id, email, password_hash, display_name, role, status) " +
                        "VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                UUID.randomUUID().toString(), email, passwordEncoder.encode(password), displayName, role);
    }

    @Test
    void protectedApiRequiresAnAuthenticatedSession() throws Exception {
        mockMvc.perform(get("/api/admin/students"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginCreatesSessionAndReturnsTheDatabaseRole() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin-auth-test@example.invalid","password":"Admin123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void parentCannotEnterAdminApi() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"parent-auth-test@example.invalid","password":"Parent123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(get("/api/admin/students").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void unclassifiedApiIsDeniedByDefault() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin-auth-test@example.invalid","password":"Admin123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(get("/api/unclassified").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin-auth-test@example.invalid","password":"Admin123!"}
                                """))
                .andExpect(status().isForbidden());
    }
}
