package report_builder.service.category;

import java.util.List;
import java.util.Optional;

import report_builder.model.Category;
import report_builder.repository.category.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
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
    public Optional<Category> getById(Long catId) {
        return categoryRepository.findById(catId);
    }

    @CacheEvict(value = "categories", allEntries = true)
    public void addCategory(String name) {
        Category category = new Category();
        category.setName(name);
        categoryRepository.save(category);
    }

    @Override
    public void save(Category category) {
        categoryRepository.save(category);
    }

    @Override
    public void delete(Category category) {
        categoryRepository.delete(category);
    }
}
