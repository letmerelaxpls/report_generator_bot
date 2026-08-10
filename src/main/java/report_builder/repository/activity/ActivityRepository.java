package report_builder.repository.activity;

import org.springframework.data.jpa.repository.EntityGraph;
import report_builder.model.ActivityRecord;
import report_builder.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityRepository extends JpaRepository<ActivityRecord, Long> {
    Optional<ActivityRecord> findByCategoryAndDate(Category category, LocalDate date);

    @EntityGraph(attributePaths = {"category.parent", "category.aggregateTo"})
    List<ActivityRecord> findAllByDateBetween(LocalDate start, LocalDate end);

    void deleteAllByCategory(Category category);
}
