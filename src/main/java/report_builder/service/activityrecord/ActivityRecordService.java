package report_builder.service.activityrecord;

import report_builder.dto.RecordRequestDto;

public interface ActivityRecordService {
    int[] updateRecord(RecordRequestDto requestDto);
}
