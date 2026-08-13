package report_builder;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import report_builder.model.Category;
import report_builder.repository.category.CategoryRepository;
import report_builder.service.category.CategoryService;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CacheWarmer {
    private final CategoryService categoryService;
    private final CategoryRepository categoryRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        System.out.println("🚀 Starting cache warming...");

        categoryService.getAllCategories();

        List<Category> roots = categoryService.getRootCategories();
        for (Category root : roots) {
            warmupRecursive(root);
        }

        List<Category> all = categoryRepository.findAll();
        for (Category cat : all) {
            categoryService.getById(cat.getId());
        }

        System.out.println("✅ Category cache is ready!");
    }

    private void warmupRecursive(Category parent) {
        List<Category> children = categoryService.getSubCategories(parent.getId());

        for (Category child : children) {
            warmupRecursive(child);
        }
    }
}
