package report_builder.service.category;

import java.util.List;
import java.util.Optional;

import report_builder.model.Category;

public interface CategoryService {
    List<Category> getAllCategories();

    Optional<Category> getById(Long catId);

    void addCategory(String name);

    void save(Category category);

    void delete(Category category);
}
