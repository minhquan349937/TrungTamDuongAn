package com.example.TTGDHNDuongAn.security;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AccountSessionFilterTests {
    JdbcTemplate jdbc;
    AccountSessionFilter filter;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:session"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE app_user(id VARCHAR(20),role VARCHAR(10),status VARCHAR(20),password_hash VARCHAR(255))");
        jdbc.update("INSERT INTO app_user VALUES('u1','ADMIN','ACTIVE','hash')");filter=new AccountSessionFilter(jdbc);
        var principal=new AppUserPrincipal("u1","admin@example.com","Admin","ADMIN","hash");
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
    }
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    @Test void validAccountReachesNextFilter() throws Exception {
        var chain=new MockFilterChain();var response=new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(),response,chain);
        assertNotNull(chain.getRequest());assertEquals(200,response.getStatus());
    }
    @ParameterizedTest @ValueSource(strings={"UPDATE app_user SET status='DISABLED'","UPDATE app_user SET status='LOCKED'","UPDATE app_user SET role='TEACHER'","UPDATE app_user SET password_hash='new-hash'","DELETE FROM app_user"})
    void revokedAccountInvalidatesSessionAndCannotReachProtectedEndpoint(String sql) throws Exception {
        jdbc.update(sql);var request=new MockHttpServletRequest();var session=(MockHttpSession)request.getSession(true);
        var response=new MockHttpServletResponse();var chain=new MockFilterChain();
        filter.doFilter(request,response,chain);
        assertEquals(401,response.getStatus());assertTrue(session.isInvalid());assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void anonymousRequestsContinueToNormalAuthorization() throws Exception {
        SecurityContextHolder.clearContext();var chain=new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest(),new MockHttpServletResponse(),chain);assertNotNull(chain.getRequest());
    }
}
