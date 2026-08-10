package report_builder.service.category;

import java.util.List;
import java.util.Optional;

import report_builder.model.Category;

public interface CategoryService {
    List<Category> getAllCategories();

    List<Category> getRootCategories();

    List<Category> getSubCategories(Long parentId);

    Optional<Category> getById(Long catId);

    void addCategory(String name);

    void warmupCache();

    void save(Category category);

    void delete(Category category);
}
