package com.example.TTGDHNDuongAn.repository;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CatalogIdGenerator {
    public enum Kind {
        PROGRAM("programs", "PR", 4), LEVEL("program_levels", "LV", 4),
        DOMAIN("domains", "DM", 4), LEVEL_DOMAIN("program_level_domains", "LD", 4), GOAL("goals", "GL", 6), SPECIFIC("specific_goals", "SG", 6);
        final String table;
        final String prefix;
        final int width;
        Kind(String table, String prefix, int width) {
            this.table = table; this.prefix = prefix; this.width = width;
        }
        public void validate(String id) {
            if (id == null || !id.matches(prefix + "[0-9]{" + width + ",6}"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + prefix + " catalog ID");
        }
    }

    private final JdbcTemplate jdbc;
    public CatalogIdGenerator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // The lock lasts until the caller's insert/update transaction commits or rolls back.
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Kind kind) {
        Integer value = jdbc.queryForObject(
                "SELECT next_value FROM catalog_id_sequences WHERE entity_name=? FOR UPDATE",
                Integer.class, kind.table);
        Integer maximum=jdbc.queryForObject("SELECT COALESCE(MAX(CAST(SUBSTRING(id,3) AS UNSIGNED)),0) FROM "+kind.table,Integer.class);
        value=Math.max(value == null ? 1 : value, maximum+1);
        if (value > 999999)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Catalog ID capacity exhausted");
        jdbc.update("UPDATE catalog_id_sequences SET next_value=? WHERE entity_name=?", value + 1, kind.table);
        return kind.prefix + String.format(Locale.ROOT, "%0" + kind.width + "d", value);
    }
}
