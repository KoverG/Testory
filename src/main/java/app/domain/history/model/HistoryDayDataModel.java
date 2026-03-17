package app.domain.history.model;

import java.util.List;

public record HistoryDayDataModel(
        HistoryDaySummaryModel summary,
        List<HistoryTimelineItemModel> timeline
) {
    public HistoryDayDataModel {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
    }
}