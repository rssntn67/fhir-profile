package it.arsinfo.fhir.web.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice(basePackages = "it.arsinfo.fhir.web.api")
class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> handleConflict(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> handleIllegalState(IllegalStateException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> handleNotFound(NoSuchElementException ex) {
        return Map.of("error", ex.getMessage());
    }
}
