package com.example.TTGDHNDuongAn.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.example.TTGDHNDuongAn.dto.GoalSelectionDtos.ActivityInput;

@SpringBootTest(properties={"SETUP_ADMIN_EMAIL=","SETUP_ADMIN_PASSWORD=","spring.docker.compose.enabled=false","debug=false"})
@Transactional
class SuggestedActivityServiceTests {
    @Autowired SuggestedActivityService activities;
    @Autowired JdbcTemplate jdbc;

    @Test void oneActivityCanBeSuggestedForMultipleGoals() {
        List<String> goals=jdbc.queryForList("SELECT id FROM goals WHERE is_active=1 ORDER BY id LIMIT 2",String.class);
        Assumptions.assumeTrue(goals.size()==2,"Requires two catalog goals");
        String name="Activity test "+UUID.randomUUID();
        var created=activities.create(goals.get(0),new SuggestedActivityService.CreateActivity(name,"Shared instructions"));
        var linked=activities.link(goals.get(1),new SuggestedActivityService.LinkActivity(created.id()));
        assertEquals(created.id(),linked.id());
        assertTrue(activities.forGoal(goals.get(0)).stream().anyMatch(row->row.id().equals(created.id())));
        assertTrue(activities.forGoal(goals.get(1)).stream().anyMatch(row->row.id().equals(created.id())));
        assertEquals(created.id(),activities.resolve(goals.get(1),List.of(new ActivityInput(created.id(),null))).getFirst().activityId());
    }
}
