package com.example.TTGDHNDuongAn;

import com.example.TTGDHNDuongAn.repository.ShortIds;
import java.util.HashSet;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=${COMPACT_ID_TEST_DB_URL}",
    "spring.docker.compose.enabled=false", "debug=false", "logging.level.root=WARN",
    "SETUP_ADMIN_EMAIL=", "SETUP_ADMIN_PASSWORD="
})
@EnabledIfEnvironmentVariable(named="COMPACT_ID_TEST_DB_URL", matches=".+")
class CompactIdIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test void growsPastThreeDigitsAndRollsBackTheCounter() {
        var tx = new TransactionTemplate(transactions);
        int before = jdbc.queryForObject("SELECT issued_value FROM entity_id_sequences WHERE entity_name='strategies'", Integer.class);
        tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE entity_id_sequences SET issued_value=998 WHERE entity_name='strategies'");
            assertEquals("CL999", ShortIds.next(jdbc, "strategies"));
            assertEquals("CL1000", ShortIds.next(jdbc, "strategies"));
            status.setRollbackOnly();
        });
        assertEquals(before, jdbc.queryForObject("SELECT issued_value FROM entity_id_sequences WHERE entity_name='strategies'", Integer.class));
    }

    @Test void concurrentAllocationUsesOneCounterWithoutDuplicateIds() throws Exception {
        var tx = new TransactionTemplate(transactions);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i=0; i<32; i++) tasks.add(pool.submit(() ->
                tx.execute(status -> ShortIds.next(jdbc, "document"))));
            var ids = new HashSet<String>();
            for (var task : tasks) {
                String id = task.get();
                assertTrue(id.matches("TL[0-9]{3,6}"));
                assertTrue(ids.add(id));
            }
            assertEquals(32, ids.size());
        }
    }
}
