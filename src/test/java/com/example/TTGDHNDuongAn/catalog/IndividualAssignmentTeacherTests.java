package com.example.TTGDHNDuongAn.catalog;

import com.example.TTGDHNDuongAn.service.IndividualAssignmentService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=${ASSIGNMENT_TEST_DB_URL}",
    "SETUP_ADMIN_EMAIL=", "SETUP_ADMIN_PASSWORD=", "spring.docker.compose.enabled=false",
    "logging.level.root=WARN", "debug=false"})
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_DB_URL",matches=".+")
@Transactional
class IndividualAssignmentTeacherTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired IndividualAssignmentService service;

    String staff(String position, String status, String user) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO staff(id,code,full_name,position,status,user_id,joined_on) VALUES(?,?,'Teacher test',?,?,?,'2023-01-01')",
            id,"TEST-"+id.substring(0,8),position,status,user);
        return id;
    }
    String account(String status) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role,status) VALUES(?,?,'unused','Teacher test','TEACHER',?)",
            id,id+"@assignment.invalid",status);
        return id;
    }
    @Test void listsAndAssignsTeachersWithoutLoginAccounts() {
        String first=staff(null,"ACTIVE",null), second=staff("Quản lý","ACTIVE",null);
        assertTrue(service.teachers().stream().anyMatch(t->t.id().equals(first)));
        assertTrue(service.teachers().stream().anyMatch(t->t.id().equals(second)));
        String student=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(?,?,'Student test','2022-01-01','2023-01-01')",
            student,"TEST-"+student.substring(0,8));
        var assignment=service.create(new IndividualAssignmentService.Create(student,first));
        assertEquals(first,assignment.teacherId());
        assertEquals(second,service.change(assignment.id(),new IndividualAssignmentService.Change(second)).teacherId());
    }
    @Test void filtersByStaffRoleAndStatusRegardlessOfPositionOrLoginStatus() {
        String linked=staff(null,"ACTIVE",account("ACTIVE"));
        assertTrue(service.teachers().stream().anyMatch(t->t.id().equals(linked)));
        String manager=staff("Quản lý","ACTIVE",null);
        jdbc.update("UPDATE staff SET account_role='ADMIN',position='Giáo viên' WHERE id=?",manager);
        String onLeave=staff("Giáo viên","ON_LEAVE",null);
        String disabled=staff("Giáo viên","ACTIVE",account("DISABLED"));
        assertTrue(service.teachers().stream().anyMatch(t->t.id().equals(disabled)));
        String student=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(?,?,'Student test','2022-01-01','2023-01-01')",
            student,"TEST-"+student.substring(0,8));
        for(String id:java.util.List.of(manager,onLeave)) {
            assertFalse(service.teachers().stream().anyMatch(t->t.id().equals(id)));
            assertEquals(400,assertThrows(ResponseStatusException.class,
                ()->service.create(new IndividualAssignmentService.Create(student,id))).getStatusCode().value());
        }
    }
}
