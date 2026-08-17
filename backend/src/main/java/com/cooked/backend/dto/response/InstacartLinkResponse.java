package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstacartLinkResponse {
    private String url;
    private String deepLinkUrl;
    private int itemCount;
    private int matchedCount;
    private String message;
}
