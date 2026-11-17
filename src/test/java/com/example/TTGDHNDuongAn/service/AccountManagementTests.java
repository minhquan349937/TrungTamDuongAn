package com.example.TTGDHNDuongAn.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.example.TTGDHNDuongAn.service.AccountManagementService.Update;

class AccountManagementTests {
    JdbcTemplate jdbc;
    AccountManagementService service;
    TransactionTemplate transactions;
    BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
    String originalHash;
    SingleConnectionDataSource source;
    @BeforeEach void setup() {
        source=new SingleConnectionDataSource("jdbc:h2:mem:accounts"+UUID.randomUUID()+";MODE=MySQL","sa","",true);
        jdbc=new JdbcTemplate(source); transactions=new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.execute("CREATE TABLE app_user(id VARCHAR(20) PRIMARY KEY,display_name VARCHAR(150),email VARCHAR(254) UNIQUE,phone VARCHAR(30) UNIQUE,role VARCHAR(10),status VARCHAR(20),password_hash VARCHAR(255),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(id,role))");
        jdbc.execute("CREATE TABLE staff(id VARCHAR(20) PRIMARY KEY,user_id VARCHAR(20),account_role VARCHAR(10),position VARCHAR(150),full_name VARCHAR(150),email VARCHAR(254),phone VARCHAR(30),FOREIGN KEY(user_id,account_role) REFERENCES app_user(id,role),CHECK(account_role IN ('ADMIN','TEACHER')))");
        jdbc.execute("CREATE TABLE guardian(id VARCHAR(20) PRIMARY KEY,user_id VARCHAR(20),account_role VARCHAR(10) DEFAULT 'PARENT',full_name VARCHAR(150),email VARCHAR(254),phone VARCHAR(30),FOREIGN KEY(user_id,account_role) REFERENCES app_user(id,role),CHECK(account_role='PARENT'))");
        originalHash=encoder.encode("original-password");
        jdbc.update("INSERT INTO app_user(id,display_name,email,phone,role,status,password_hash) VALUES('u1','Old name','old@example.com','0912345678','TEACHER','ACTIVE',?)",originalHash);
        jdbc.update("INSERT INTO staff(id,user_id,account_role,full_name) VALUES('s1','u1','TEACHER','Old name')");
        service=new AccountManagementService(jdbc,new AccountContactService(jdbc,encoder),encoder);
    }
    @AfterEach void cleanup() {source.destroy();}
    AccountManagementService.Account save(Update input) {return transactions.execute(tx->service.updateAccount("u1",input));}
    Update input(String role,String status,String password) {return new Update("Updated name","new@example.com","0912345679",role,status,password);}
    String hash() {return jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id='u1'",String.class);}
    @Test void updatesLinkedProfileAndRoleWithCompositeForeignKeyIntact() {
        var result=save(input("ADMIN","DISABLED","new-password"));
        assertEquals("ADMIN",result.role());assertEquals("DISABLED",result.status());assertEquals("Updated name",result.displayName());
        assertTrue(encoder.matches("new-password",hash()));assertFalse(encoder.matches("original-password",hash()));
        var profile=jdbc.queryForMap("SELECT * FROM staff WHERE id='s1'");
        assertEquals("u1",profile.get("USER_ID"));assertEquals("ADMIN",profile.get("ACCOUNT_ROLE"));
        assertEquals("Updated name",profile.get("FULL_NAME"));assertEquals("new@example.com",profile.get("EMAIL"));
        assertEquals("0912345679",profile.get("PHONE"));assertEquals("Quản trị viên",profile.get("POSITION"));
        save(input("TEACHER","ACTIVE",null));assertTrue(encoder.matches("new-password",hash()));
    }
    @Test void blankOrOmittedPasswordKeepsOriginalAndContactEndpointStillWorks() {
        save(input("TEACHER","ACTIVE",""));assertEquals(originalHash,hash());
        transactions.execute(tx->service.update("u1",new AccountManagementService.Contact("contact@example.com","0912345670")));
        assertEquals(originalHash,hash());assertEquals("contact@example.com",service.list().getFirst().email());
    }
    @Test void rejectsInvalidInputAndIncompatibleRolesBeforeChangingAnything() {
        for(Update value:new Update[]{input("PARENT","ACTIVE",null),input("ROOT","ACTIVE",null),input("ADMIN","UNKNOWN",null),input("ADMIN","ACTIVE","123"),input("ADMIN","ACTIVE"," ".repeat(6)),input("ADMIN","ACTIVE","ắ".repeat(25)),new Update(" ",null,null,"ADMIN","ACTIVE",null)}) {
            assertEquals(400,assertThrows(ResponseStatusException.class,()->save(value)).getStatusCode().value());
            assertEquals("TEACHER",service.list().getFirst().role());assertEquals(originalHash,hash());
        }
    }
    @Test void duplicateContactRollsBackWholeUpdate() {
        jdbc.update("INSERT INTO app_user(id,email,role,status,password_hash) VALUES('u2','new@example.com','ADMIN','ACTIVE','unused')");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->save(input("ADMIN","DISABLED","new-password"))).getStatusCode().value());
        assertEquals("Old name",jdbc.queryForObject("SELECT display_name FROM app_user WHERE id='u1'",String.class));
        assertEquals("TEACHER",jdbc.queryForObject("SELECT account_role FROM staff WHERE id='s1'",String.class));assertEquals(originalHash,hash());
    }
    @Test void parentContactAndNameStaySynchronizedAndPhoneIsRequired() {
        jdbc.update("DELETE FROM staff");jdbc.update("UPDATE app_user SET role='PARENT' WHERE id='u1'");
        jdbc.update("INSERT INTO guardian(id,user_id,full_name) VALUES('g1','u1','Old name')");
        save(input("PARENT","LOCKED",null));
        assertEquals("Updated name",jdbc.queryForObject("SELECT full_name FROM guardian WHERE id='g1'",String.class));
        assertEquals(400,assertThrows(ResponseStatusException.class,()->save(input("ADMIN","ACTIVE",null))).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->save(new Update("Name","new@example.com",null,"PARENT","ACTIVE",null))).getStatusCode().value());
    }
    @Test void unlinkedAccountCanChangeToAnySupportedRoleAndMissingAccountIs404() {
        jdbc.update("DELETE FROM staff");
        assertEquals("PARENT",save(input("PARENT","ACTIVE",null)).role());
        assertEquals("ADMIN",save(input("ADMIN","ACTIVE",null)).role());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.updateAccount("missing",input("ADMIN","ACTIVE",null))).getStatusCode().value());
    }
}
