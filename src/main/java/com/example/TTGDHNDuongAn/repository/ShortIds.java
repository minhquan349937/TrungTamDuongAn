package com.example.TTGDHNDuongAn.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** One atomic counter per entity. The caller inserts the returned ID in its transaction. */
public final class ShortIds {
    private ShortIds() {}
    public static final Map<String, String> PREFIXES = Map.ofEntries(
        Map.entry("app_user", "TK"),
        Map.entry("audit_event", "NK"),
        Map.entry("center", "TT"),
        Map.entry("class_session", "BH"),
        Map.entry("consultation_requests", "TV"),
        Map.entry("document", "TL"),
        Map.entry("domains", "DM"),
        Map.entry("enrollment", "GD"),
        Map.entry("goal_evaluations", "DG"),
        Map.entry("goals", "GL"),
        Map.entry("guardian", "PH"),
        Map.entry("invoice", "HD"),
        Map.entry("invoice_line", "CTHD"),
        Map.entry("learning_class", "LH"),
        Map.entry("learning_plans", "KH"),
        Map.entry("leave_request", "NP"),
        Map.entry("notification", "TB"),
        Map.entry("payment", "TTN"),
        Map.entry("plan_evaluations", "NX"),
        Map.entry("plan_goals", "MTKH"),
        Map.entry("program_level_domains", "LD"),
        Map.entry("program_levels", "LV"),
        Map.entry("programs", "PR"),
        Map.entry("specific_goals", "SG"),
        Map.entry("staff", "NV"),
        Map.entry("staff_checkin", "CC"),
        Map.entry("staff_salary", "LNV"),
        Map.entry("student", "HS"),
        Map.entry("student_attendance", "DD"),
        Map.entry("website_albums", "AL"),
        Map.entry("website_faqs", "FAQ"),
        Map.entry("website_media", "MD"),
        Map.entry("website_publications", "XB"),
        Map.entry("website_section_items", "ND"),
        Map.entry("website_services", "DV"),
        Map.entry("website_settings", "WCFG"),
        Map.entry("website_team_members", "NS"),
        Map.entry("strategies", "CL"),
        Map.entry("plan_goal_strategies", "MTCL"));

    public static String next(JdbcTemplate jdbc, String table) {
        String prefix = PREFIXES.get(table);
        if (prefix == null) throw new IllegalArgumentException("Unknown ID entity: " + table);
        if (!compactSchema(jdbc)) {
            // V15 is usable before the optional, database-wide V16 ID conversion.
            // Preserve existing UUID URLs while allocating short strategy/catalog IDs.
            if (!java.util.Set.of("programs","program_levels","domains","program_level_domains","goals","specific_goals","strategies","plan_goal_strategies").contains(table))
                return UUID.randomUUID().toString();
            return legacyCatalogNext(jdbc,table,prefix);
        }
        return jdbc.execute((ConnectionCallback<String>) connection -> {
            // LAST_INSERT_ID is connection-local; both statements use this same connection.
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE entity_id_sequences SET issued_value=LAST_INSERT_ID(issued_value+1) " +
                    "WHERE entity_name=? AND issued_value<999999")) {
                update.setString(1, table);
                if (update.executeUpdate() != 1)
                    throw new IllegalStateException("ID counter missing or exhausted: " + table);
            }
            try (PreparedStatement select = connection.prepareStatement("SELECT LAST_INSERT_ID()");
                    ResultSet result = select.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("ID counter returned no value");
                return prefix + String.format(Locale.ROOT, "%03d", result.getLong(1));
            }
        });
    }

    public static boolean compactSchema(JdbcTemplate jdbc) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='entity_id_sequences'",Integer.class);
        return count!=null && count==1;
    }

    private static String legacyCatalogNext(JdbcTemplate jdbc,String table,String prefix) {
        return jdbc.execute((ConnectionCallback<String>) connection -> {
            try(var init=connection.prepareStatement("INSERT INTO catalog_id_sequences(entity_name,next_value) " +
                    "SELECT ?,COALESCE(MAX(CAST(SUBSTRING(id,?) AS UNSIGNED)),0)+1 FROM `"+table+"` WHERE id REGEXP ? " +
                    "ON DUPLICATE KEY UPDATE next_value=GREATEST(next_value,VALUES(next_value))")) {
                init.setString(1,table);init.setInt(2,prefix.length()+1);init.setString(3,"^"+prefix+"[0-9]+$");init.executeUpdate();
            }
            try(var update=connection.prepareStatement("UPDATE catalog_id_sequences SET next_value=LAST_INSERT_ID(next_value)+1 WHERE entity_name=? AND next_value<=999999")) {
                update.setString(1,table);
                if(update.executeUpdate()!=1) throw new IllegalStateException("ID counter exhausted: "+table);
            }
            try(var select=connection.prepareStatement("SELECT LAST_INSERT_ID()");var result=select.executeQuery()) {
                if(!result.next()) throw new IllegalStateException("ID counter returned no value");
                return prefix+String.format(Locale.ROOT,"%03d",result.getLong(1));
            }
        });
    }

    public static boolean isId(String value) {
        if (value == null) return false;
        if (PREFIXES.values().stream().anyMatch(prefix -> value.matches(prefix + "[0-9]{3,6}"))) return true;
        // Read compatibility for legacy callers/fixtures; all new entity IDs use counters.
        try { return UUID.fromString(value).toString().equalsIgnoreCase(value); }
        catch (IllegalArgumentException ex) { return false; }
    }
}
