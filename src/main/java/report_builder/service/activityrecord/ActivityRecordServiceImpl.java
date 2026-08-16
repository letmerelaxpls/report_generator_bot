package report_builder.service.activityrecord;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import report_builder.dto.RecordRequestDto;
import report_builder.model.ActivityRecord;
import report_builder.model.Category;
import report_builder.repository.activity.ActivityRepository;
import report_builder.repository.category.CategoryRepository;

@Service
@RequiredArgsConstructor
public class ActivityRecordServiceImpl implements ActivityRecordService {
    private static final String ADD_OPERATION = "ADD";
    private static final String SUBTRACT_OPERATION = "SUBTRACT";
    private final CategoryRepository categoryRepository;
    private final ActivityRepository activityRepository;

    @Override
    public int updateRecord(RecordRequestDto requestDto) {
        LocalDate date = LocalDate.parse(requestDto.date());
        Category category = categoryRepository.findById(requestDto.catId()).orElseThrow();
        ActivityRecord activityRecord = activityRepository.findByCategoryAndDate(category, date)
                .orElseGet(() -> {
                    ActivityRecord newAR = new ActivityRecord();
                    newAR.setCategory(category);
                    newAR.setDate(date);
                    return newAR;
                });

        int current = activityRecord.getCount();

        if (ADD_OPERATION.equals(requestDto.operation())) {
            activityRecord.setCount(activityRecord.getCount() + requestDto.count());
        } else if (SUBTRACT_OPERATION.equals(requestDto.operation())) {
            if (current <= 0) {
                throw new IllegalArgumentException("Немає записів за цей день");
            }
            activityRecord.setCount(Math.max(0, activityRecord.getCount() - requestDto.count()));
        } else {
            throw new IllegalArgumentException("Unsupported operation: " + requestDto.operation());
        }

        activityRepository.save(activityRecord);
        return activityRecord.getCount();
    }
}
