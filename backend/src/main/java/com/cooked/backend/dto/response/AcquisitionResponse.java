package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Sign-ups over the last {@link #days} days and the "how did you hear about Cooked?" answers. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcquisitionResponse {
    private int days;
    private long newUsers;
    private long newUsersPrev;
    private List<DayCount> signupsDaily;
    private List<SourceCount> bySource;

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class DayCount {
        private String date;
        private long total;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class SourceCount {
        private String source;
        private long total;
    }
}
