package com.example.TTGDHNDuongAn.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import com.example.TTGDHNDuongAn.dto.GoalCatalogDtos.*;
import com.example.TTGDHNDuongAn.dto.GoalSelectionDtos.Target;
import com.example.TTGDHNDuongAn.repository.GoalCatalogRepository;
import com.example.TTGDHNDuongAn.service.*;
import org.junit.jupiter.api.Test;

class GoalSelectionPolicyTests {
    private final GoalCatalogRepository repository=mock(GoalCatalogRepository.class);
    private final GoalCatalogService service=new GoalCatalogService(repository,new GoalSelectionPolicy());
    private final GoalLocation location=new GoalLocation("PR0001","Test","LV0001","Test","DM0001","Test",12,24);

    @Test
    void denverDetailNeverRequestsSpecificGoals() {
        when(repository.goalHeader("GL000001")).thenReturn(Optional.of(new GoalHeader("GL000001","GTN.1.1","Full name","Full description","ESDM",location)));
        var goal=service.goal("GL000001");
        assertFalse(goal.supportsSpecificGoals()); assertTrue(goal.canSelectGeneralGoal());
        assertEquals("Full description",goal.description()); assertTrue(goal.specificGoals().isEmpty());
        verify(repository,never()).specificGoals(anyString());
    }

    @Test
    void vbLoadsChildrenOnceAndAllowsMainAndChildTargets() {
        when(repository.goalHeader("GL000001")).thenReturn(Optional.of(new GoalHeader("GL000001","VB.1.1","Parent",null,"VB",location)));
        when(repository.specificGoals("GL000001")).thenReturn(List.of(
                new SpecificGoal("SG000001","VB.1.1A","Child A",12,24,true,1),
                new SpecificGoal("SG000002","VB.1.1B","Child B",12,24,true,2)));
        when(repository.headers(List.of("GL000001"))).thenReturn(java.util.Map.of("GL000001",new GoalHeader("GL000001","VB.1.1","Parent",null,"VB",location)));
        when(repository.children(List.of("GL000001"))).thenReturn(java.util.Map.of("GL000001",List.of(
            new SpecificGoal("SG000001","VB.1.1A","Child A",12,24,true,1),new SpecificGoal("SG000002","VB.1.1B","Child B",12,24,true,2))));
        var resolver=new GoalTargetResolver(service);
        var selected=resolver.resolveAll(List.of(new Target("GL000001","SG000001"),new Target("GL000001","SG000002")));
        assertEquals(2,selected.size());
        verify(repository,times(1)).children(List.of("GL000001"));
        verify(repository,times(1)).headers(List.of("GL000001"));
        assertNull(resolver.resolve("GL000001",null).specificGoalId());
    }

    @Test
    void vbWithoutChildrenStillAllowsItsMainGoal() {
        when(repository.goalHeader("GL000001")).thenReturn(Optional.of(new GoalHeader("GL000001","VB.1.1","Parent",null,"VB",location)));
        when(repository.specificGoals("GL000001")).thenReturn(List.of());
        var goal=service.goal("GL000001");
        assertTrue(goal.supportsSpecificGoals()); assertTrue(goal.canSelectGeneralGoal());
        assertTrue(goal.specificGoals().isEmpty());
    }
}
