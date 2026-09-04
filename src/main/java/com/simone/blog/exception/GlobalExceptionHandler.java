package com.simone.blog.exception;


import com.simone.blog.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.debug("Richiesta fallita: {} {} - {} ", request.getMethod(), request.getRequestURI(),ex.getMessage());
        return new ApiError(Instant.now(),HttpStatus.NOT_FOUND.value(),"RESOURCE_NOT_FOUND", ex.getMessage(),request.getRequestURI() );
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        log.debug("Richiesta fallita: {} {} - {} ", request.getMethod(), request.getRequestURI(),ex.getMessage());
        return new ApiError(Instant.now(),HttpStatus.BAD_REQUEST.value(),"BAD_REQUEST",ex.getMessage(),request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(MethodArgumentNotValidException ex,HttpServletRequest request){
        String message = ex.getBindingResult().getFieldErrors().stream().map( e -> e.getField() + ": " + e.getDefaultMessage()).collect(Collectors.joining("; "));
        log.debug("Richiesta fallita: {} {} - {} ", request.getMethod(), request.getRequestURI(),message);
        return new ApiError(Instant.now(),HttpStatus.BAD_REQUEST.value(),"VALIDATION_ERROR",message,request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleGeneralServerError(Exception ex,HttpServletRequest request){
        log.error("Richiesta fallita: {} {} ", request.getMethod(), request.getRequestURI(),ex);
        return new ApiError(Instant.now(),HttpStatus.INTERNAL_SERVER_ERROR.value(),"INTERNAL_SERVER_ERROR","Si è verificato un errore imprevisto",request.getRequestURI());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError handleUnauthorized(UnauthorizedException ex,HttpServletRequest request){
        log.debug("Richiesta fallita: {} {} - {} ", request.getMethod(), request.getRequestURI(),ex.getMessage());
        return new ApiError(Instant.now(),HttpStatus.UNAUTHORIZED.value(),"UNAUTHORIZED",ex.getMessage(),request.getRequestURI());
    }
}
