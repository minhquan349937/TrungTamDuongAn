package com.example.TTGDHNDuongAn.repository;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CatalogIdGenerator {
    public enum Kind {
        PROGRAM("programs", "PR"), LEVEL("program_levels", "LV"),
        DOMAIN("domains", "DM"), LEVEL_DOMAIN("program_level_domains", "LD"), GOAL("goals", "GL"), SPECIFIC("specific_goals", "SG");
        final String table;
        final String prefix;
        Kind(String table, String prefix) {
            this.table = table; this.prefix = prefix;
        }
        public void validate(String id) {
            if (id == null || !id.matches(prefix + "[0-9]{3,6}"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + prefix + " catalog ID");
        }
    }

    private final JdbcTemplate jdbc;
    public CatalogIdGenerator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // The lock lasts until the caller's insert/update transaction commits or rolls back.
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Kind kind) {
        return ShortIds.next(jdbc, kind.table);
    }
}
