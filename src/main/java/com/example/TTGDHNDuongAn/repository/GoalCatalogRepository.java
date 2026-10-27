package com.example.TTGDHNDuongAn.repository;

import java.util.List;
import java.util.Optional;

import com.example.TTGDHNDuongAn.dto.GoalCatalogDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GoalCatalogRepository {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final JdbcTemplate jdbc;
    private final CatalogIdGenerator ids;
    private final com.example.TTGDHNDuongAn.repository.catalog.CatalogGoalJpaRepository goalEntities;

    public GoalCatalogRepository(JdbcTemplate jdbc, CatalogIdGenerator ids, com.example.TTGDHNDuongAn.repository.catalog.CatalogGoalJpaRepository goalEntities) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.goalEntities=goalEntities;
    }

    public List<GoalCatalogDtos.Program> programs() {
        return jdbc.query("SELECT id,code,name,description FROM programs WHERE is_active=1 ORDER BY code,id",
                (rs, n) -> new GoalCatalogDtos.Program(rs.getString("id"), rs.getString("code"),
                        rs.getString("name"), rs.getString("description")));
    }

    public boolean activeProgramExists(String id) {
        return exists("SELECT COUNT(*) FROM programs WHERE id=? AND is_active=1", id);
    }

    public List<GoalCatalogDtos.ProgramLevel> levels(String programId) {
        return jdbc.query("SELECT id,program_id,level_number,name,age_min_months,age_max_months,display_order " +
                        "FROM program_levels WHERE program_id=? ORDER BY display_order,id",
                (rs, n) -> new GoalCatalogDtos.ProgramLevel(rs.getString("id"),
                        rs.getString("program_id"), rs.getInt("level_number"), rs.getString("name"),
                        rs.getInt("age_min_months"), rs.getInt("age_max_months"), rs.getInt("display_order")),
                programId);
    }

    public boolean levelExists(String id) {
        return exists("SELECT COUNT(*) FROM program_levels pl JOIN programs p ON p.id=pl.program_id " +
                "WHERE pl.id=? AND p.is_active=1", id);
    }

    public List<GoalCatalogDtos.Domain> domains(String levelId) {
        return jdbc.query("SELECT d.id,ld.id level_domain_id,ld.program_level_id,d.code,d.name,d.description,ld.display_order FROM domains d " +
                        "JOIN program_level_domains ld ON ld.domain_id=d.id WHERE ld.program_level_id=? AND d.is_active=1 ORDER BY ld.display_order,d.id",
                (rs,n)->new GoalCatalogDtos.Domain(rs.getString("id"),rs.getString("program_level_id"),rs.getString("code"),
                    rs.getString("name"),rs.getString("description"),rs.getInt("display_order"),rs.getString("level_domain_id")),levelId);
    }

    public boolean activeDomainExists(String id) {
        return exists("SELECT COUNT(*) FROM program_level_domains ld JOIN domains d ON d.id=ld.domain_id " +
                "JOIN programs p ON p.id=ld.program_id WHERE ld.id=? AND d.is_active=1 AND p.is_active=1",id);
    }

    public List<GoalCatalogDtos.ProgramDomain> programDomains(String programId) {
        return jdbc.query("SELECT id,program_id,code,name,description FROM domains WHERE program_id=? AND is_active=1 ORDER BY display_order,id",
            (rs,n)->new GoalCatalogDtos.ProgramDomain(rs.getString("id"),rs.getString("program_id"),rs.getString("code"),rs.getString("name"),rs.getString("description")),programId);
    }
    public String createProgramDomain(String programId,GoalCatalogDtos.CreateDomain request) {
        String id=ids.next(CatalogIdGenerator.Kind.DOMAIN);
        jdbc.update("INSERT INTO domains(id,program_id,code,name,display_order) VALUES(?,?,?,?,?)",id,programId,request.code(),request.name(),request.displayOrder());
        return id;
    }
    public String linkDomain(String levelId,GoalCatalogDtos.LinkDomain request) {
        String id=ids.next(CatalogIdGenerator.Kind.LEVEL_DOMAIN);
        String program=jdbc.queryForObject("SELECT program_id FROM program_levels WHERE id=?",String.class,levelId);
        jdbc.update("INSERT INTO program_level_domains(id,program_id,program_level_id,domain_id,display_order) VALUES(?,?,?,?,?)",
            id,program,levelId,request.domainId(),request.displayOrder());
        return id;
    }

    public GoalCatalogDtos.Page<GoalCatalogDtos.Goal> goals(String domainId, String search, int page, int size) {
        String where = " FROM goals WHERE level_domain_id=? AND is_active=1 AND " +
                "(?='' OR code LIKE CONCAT('%',?,'%') OR name LIKE CONCAT('%',?,'%'))";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class,
                domainId, search, search, search);
        List<GoalCatalogDtos.Goal> rows = jdbc.query(
                "SELECT id,code,name,description,display_order" + where +
                        " ORDER BY display_order,id LIMIT ? OFFSET ?",
                (rs, n) -> new GoalCatalogDtos.Goal(rs.getString("id"), rs.getString("code"),
                        rs.getString("name"), rs.getString("description"), rs.getInt("display_order")),
                domainId, search, search, search, size, (long) page * size);
        long count = total == null ? 0 : total;
        return new GoalCatalogDtos.Page<>(rows, page, size, count, (count + size - 1) / size);
    }

    public Optional<GoalCatalogDtos.GoalHeader> goalHeader(String id) {
        if(!exists("SELECT COUNT(*) FROM goals g JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN domains d ON d.id=ld.domain_id JOIN programs p ON p.id=ld.program_id WHERE g.id=? AND g.is_active=1 AND d.is_active=1 AND p.is_active=1",id)) return Optional.empty();
        entityManager.clear();
        return goalEntities.hierarchy(id).map(com.example.TTGDHNDuongAn.service.CatalogGoalMapper::header);
    }

    public List<GoalCatalogDtos.SpecificGoal> specificGoals(String goalId) {
        return jdbc.query(
                "SELECT sg.id,sg.code,sg.description,sg.display_order,sg.age_min_months,sg.age_max_months, " +
                        "pl.age_min_months AS level_min,pl.age_max_months AS level_max " +
                        "FROM specific_goals sg JOIN goals g ON g.id=sg.goal_id " +
                        "JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN domains d ON d.id=ld.domain_id JOIN program_levels pl ON pl.id=ld.program_level_id " +
                        "WHERE sg.goal_id=? AND sg.is_active=1 ORDER BY sg.display_order,sg.id",
                (rs, n) -> new GoalCatalogDtos.SpecificGoal(rs.getString("id"), rs.getString("code"),
                        rs.getString("description"),
                        rs.getObject("age_min_months") == null ? rs.getInt("level_min") : rs.getInt("age_min_months"),
                        rs.getObject("age_max_months") == null ? rs.getInt("level_max") : rs.getInt("age_max_months"),
                        rs.getObject("age_min_months") == null || rs.getObject("age_max_months") == null,
                        rs.getInt("display_order")), goalId);
    }

    public java.util.Map<String,GoalCatalogDtos.GoalHeader> headers(java.util.Collection<String> ids) {
        if(ids.isEmpty()) return java.util.Map.of();
        var named=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);
        var active=named.queryForList("SELECT g.id FROM goals g JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN domains d ON d.id=ld.domain_id JOIN programs p ON p.id=ld.program_id WHERE g.id IN (:ids) AND g.is_active=1 AND d.is_active=1 AND p.is_active=1",java.util.Map.of("ids",ids),String.class);
        entityManager.clear();
        if(active.isEmpty()) return java.util.Map.of();
        return goalEntities.hierarchies(active).stream().map(com.example.TTGDHNDuongAn.service.CatalogGoalMapper::header)
            .collect(java.util.stream.Collectors.toMap(GoalCatalogDtos.GoalHeader::id,java.util.function.Function.identity(),(a,b)->a,java.util.LinkedHashMap::new));
    }

    public java.util.Map<String,List<GoalCatalogDtos.SpecificGoal>> children(java.util.Collection<String> goalIds) {
        if(goalIds.isEmpty()) return java.util.Map.of();
        var result=new java.util.HashMap<String,List<GoalCatalogDtos.SpecificGoal>>();
        var named=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);
        named.query("SELECT sg.*,pl.age_min_months level_min,pl.age_max_months level_max FROM specific_goals sg JOIN goals g ON g.id=sg.goal_id JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN program_levels pl ON pl.id=ld.program_level_id WHERE sg.goal_id IN (:ids) AND sg.is_active=1 ORDER BY sg.display_order,sg.id",
            java.util.Map.of("ids",goalIds),(org.springframework.jdbc.core.RowCallbackHandler)rs -> {
                var child=new GoalCatalogDtos.SpecificGoal(rs.getString("id"),rs.getString("code"),rs.getString("description"),
                    rs.getObject("age_min_months")==null?rs.getInt("level_min"):rs.getInt("age_min_months"),
                    rs.getObject("age_max_months")==null?rs.getInt("level_max"):rs.getInt("age_max_months"),
                    rs.getObject("age_min_months")==null||rs.getObject("age_max_months")==null,rs.getInt("display_order"));
                result.computeIfAbsent(rs.getString("goal_id"),key->new java.util.ArrayList<>()).add(child);
            });
        return result;
    }

    public String insertGoal(String domainId, GoalCatalogDtos.CreateGoal request) {
        String id = ids.next(CatalogIdGenerator.Kind.GOAL);
        jdbc.update("INSERT INTO goals(id,level_domain_id,code,name,description,display_order) VALUES(?,?,?,?,?,?)",
                id, domainId, request.code(), request.name(), request.description(),
                request.displayOrder() == null ? 0 : request.displayOrder());
        return id;
    }

    public String insertProgram(GoalCatalogDtos.CreateProgram request) {
        String id = ids.next(CatalogIdGenerator.Kind.PROGRAM);
        jdbc.update("INSERT INTO programs(id,code,name,description) VALUES(?,?,?,?)",
                id, request.code(), request.name(), request.description());
        return id;
    }

    public String insertLevel(String programId, GoalCatalogDtos.CreateLevel request) {
        String id = ids.next(CatalogIdGenerator.Kind.LEVEL);
        jdbc.update("INSERT INTO program_levels(id,program_id,level_number,name,age_min_months,age_max_months,display_order) VALUES(?,?,?,?,?,?,?)",
                id, programId, request.levelNumber(), request.name(), request.ageMinMonths(),
                request.ageMaxMonths(), request.displayOrder());
        return id;
    }

    public String insertDomain(String levelId, GoalCatalogDtos.CreateDomain request) {
        String programId=jdbc.queryForObject("SELECT program_id FROM program_levels WHERE id=?",String.class,levelId);
        var existing=jdbc.queryForList("SELECT id FROM domains WHERE program_id=? AND code=?",String.class,programId,request.code());
        String id;
        if(existing.isEmpty()) {
            id=ids.next(CatalogIdGenerator.Kind.DOMAIN);
            jdbc.update("INSERT INTO domains(id,program_id,code,name) VALUES(?,?,?,?)",id,programId,request.code(),request.name());
        } else {
            id=existing.getFirst();
            String name=jdbc.queryForObject("SELECT name FROM domains WHERE id=?",String.class,id);
            if(!name.equals(request.name())) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,"Existing domain code has a different name");
        }
        String link=ids.next(CatalogIdGenerator.Kind.LEVEL_DOMAIN);
        jdbc.update("INSERT INTO program_level_domains(id,program_id,program_level_id,domain_id,display_order) VALUES(?,?,?,?,?)",
            link,programId,levelId,id,request.displayOrder());
        return id;
    }

    public String insertSpecific(String goalId, GoalCatalogDtos.WriteSpecificGoal request) {
        String id = ids.next(CatalogIdGenerator.Kind.SPECIFIC);
        jdbc.update("INSERT INTO specific_goals(id,goal_id,code,description,age_min_months,age_max_months,display_order) VALUES(?,?,?,?,?,?,?)",
                id, goalId, request.code(), request.description(), request.ageMinMonths(),
                request.ageMaxMonths(), request.displayOrder());
        return id;
    }

    public void updateGoal(String id, GoalCatalogDtos.CreateGoal request) {
        jdbc.update("UPDATE goals SET code=?,name=?,description=?,display_order=? WHERE id=?",
                request.code(), request.name(), request.description(), request.displayOrder() == null ? 0 : request.displayOrder(), id);
    }

    public void updateSpecific(String goalId, String id, GoalCatalogDtos.WriteSpecificGoal request) {
        jdbc.update("UPDATE specific_goals SET code=?,description=?,age_min_months=?,age_max_months=?,display_order=? WHERE id=? AND goal_id=?",
                request.code(), request.description(), request.ageMinMonths(), request.ageMaxMonths(), request.displayOrder(), id, goalId);
    }

    public void archiveGoal(String id) { jdbc.update("UPDATE goals SET is_active=0 WHERE id=?", id); }
    public void archiveSpecific(String id) { jdbc.update("UPDATE specific_goals SET is_active=0 WHERE id=?", id); }

    public int[] goalAgeRange(String goalId) {
        return jdbc.queryForObject("SELECT pl.age_min_months,pl.age_max_months FROM goals g " +
                "JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN domains d ON d.id=ld.domain_id JOIN program_levels pl ON pl.id=ld.program_level_id WHERE g.id=?",
                (rs, n) -> new int[]{rs.getInt(1), rs.getInt(2)}, goalId);
    }

    private boolean exists(String sql, String id) {
        Integer count = jdbc.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }
}
