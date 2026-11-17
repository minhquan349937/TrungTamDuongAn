package com.example.TTGDHNDuongAn.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Recheck account access so existing sessions cannot retain revoked permissions. */
public class AccountSessionFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    public AccountSessionFilter(JdbcTemplate jdbc) {this.jdbc=jdbc;}

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            var valid=jdbc.query("SELECT status,role,password_hash FROM app_user WHERE id=?",
                (rs,n)->"ACTIVE".equals(rs.getString("status")) && principal.role().equals(rs.getString("role"))
                    && principal.passwordHash().equals(rs.getString("password_hash")),principal.id());
            if(valid.isEmpty() || !valid.getFirst()) {
                SecurityContextHolder.clearContext();
                var session=request.getSession(false);
                if(session!=null) session.invalidate();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
