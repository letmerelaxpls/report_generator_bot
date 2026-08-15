package report_builder.service.category;

import java.util.List;
import java.util.Optional;
import report_builder.model.Category;
import report_builder.repository.category.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;

    @Override
    @Cacheable("categories")
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Override
    @Cacheable(value = "rootCategories")
    public List<Category> getRootCategories() {
        return categoryRepository.findAllByParentIsNull();
    }

    @Override
    @Cacheable(value = "subCategories", key = "#a0")
    public List<Category> getSubCategories(Long parentId) {
        return categoryRepository.findAllByParentId(parentId);
    }

    @Override
    @Cacheable(value = "categories", key = "#a0")
    public Optional<Category> getById(Long catId) {
        return categoryRepository.findByIdWithRelations(catId);
    }

    @Override
    public void warmupCache() {
        List<Category> categories = getAllCategories();

        List<Category> roots = getRootCategories();

        for (Category cat: categories) {
            getById(cat.getId());

            getSubCategories(cat.getId());
        }
        System.out.println("All categories have been added to cache. Total categories: " + categories.size());
    }
}
