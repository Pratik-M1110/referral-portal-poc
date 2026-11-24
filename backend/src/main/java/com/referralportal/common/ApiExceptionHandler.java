package com.referralportal.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

/** Every API error is {"message": "..."} so the app can show it as-is. */
@RestControllerAdvice
class ApiExceptionHandler {

    record ErrorBody(String message) {}

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorBody> status(ResponseStatusException e) {
        String message = e.getReason() == null ? "Something went wrong." : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorBody(message));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ErrorBody> missingPart(MissingServletRequestPartException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorBody("Choose a PDF file to upload."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorBody> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorBody("Resume must be 5 MB or smaller."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream().findFirst()
                .map(f -> f.getDefaultMessage() == null ? "Please check " + f.getField() + "." : f.getDefaultMessage())
                .orElse("Please check your input.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorBody(message));
    }
}
