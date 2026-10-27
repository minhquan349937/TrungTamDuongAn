package com.example.TTGDHNDuongAn.repository.catalog;
import com.example.TTGDHNDuongAn.model.catalog.CatalogGoal;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface CatalogGoalJpaRepository extends JpaRepository<CatalogGoal,String> {
    @EntityGraph(attributePaths={"levelDomain","levelDomain.level","levelDomain.domain","levelDomain.program"})
    @Query("SELECT g FROM CatalogGoal g WHERE g.id IN :ids ORDER BY g.levelDomain.program.code,g.levelDomain.domain.displayOrder,g.levelDomain.domain.id,g.levelDomain.level.displayOrder,g.levelDomain.level.id,g.displayOrder,g.id")
    java.util.List<CatalogGoal> hierarchies(@Param("ids") java.util.Collection<String> ids);
    // One fetch graph for the full hierarchy; deliberately never fetch children.
    @EntityGraph(attributePaths={"levelDomain","levelDomain.level","levelDomain.domain","levelDomain.program"})
    @Query("SELECT g FROM CatalogGoal g WHERE g.id=:id")
    Optional<CatalogGoal> hierarchy(@Param("id") String id);
}
