package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** What an intern changed in a recipe, for the reviewer. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewChangesResponse {

    /** False when no snapshot exists (assignment never saved since the feature shipped): nothing can be compared. */
    private boolean baselineAvailable;
    /** The snapshot was taken at the intern's first save, not at assignment: earlier edits are not visible. */
    private boolean baselineLate;
    private List<FieldChange> fields;
    /** Parts sent back last time, and whether the intern changed them since. */
    private List<Instruction> instructions;
    private List<String> previouslyApproved;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldChange {
        private String key;
        private boolean changed;
        private String before;
        private String after;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Instruction {
        private String key;
        private boolean respected;
    }
}
