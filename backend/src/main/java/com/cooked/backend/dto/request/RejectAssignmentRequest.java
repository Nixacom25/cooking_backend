package com.cooked.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectAssignmentRequest {
    private List<String> errorCategories;
    private String feedbackComment;
    /** Recipe parts to fix (keys of RecipeReviewService.FIELDS). */
    private List<String> fieldsToFix;
    /** Recipe parts the reviewer checked as good. */
    private List<String> fieldsApproved;
}
