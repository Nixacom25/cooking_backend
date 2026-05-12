package com.cooked.backend.exception;

import com.cooked.backend.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;

/**
 * Custom Error Controller to ensure the backend NEVER returns HTML.
 * This handles errors that occur outside the RestController scope (e.g. 404 for invalid paths).
 */
@RestController
public class CustomErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;

    public CustomErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        ServletWebRequest servletWebRequest = new ServletWebRequest(request);
        Map<String, Object> attributes = errorAttributes.getErrorAttributes(
                servletWebRequest, 
                ErrorAttributeOptions.defaults()
        );

        int status = (int) attributes.getOrDefault("status", 500);
        String message = (String) attributes.getOrDefault("message", "No message available");
        HttpStatus httpStatus = HttpStatus.resolve(status);
        if (httpStatus == null) httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;

        String errorCode = "UNKNOWN_ERROR";
        if (status == 404) {
            errorCode = "PATH_NOT_FOUND";
            message = " the requested endpoint '" + attributes.get("path") + "' does not exist.";
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(status)
                .message(message)
                .source("BACKEND_FRAMEWORK")
                .errorCode(errorCode)
                .timestamp(System.currentTimeMillis())
                .build();

        return new ResponseEntity<>(errorResponse, httpStatus);
    }
}
