package com.example.kkaribackend.global.error;

import com.example.kkaribackend.global.error.exception.ErrorProperty;
import com.example.kkaribackend.global.error.exception.GlobalErrorCode;
import com.example.kkaribackend.global.error.response.ErrorResponse;
import com.example.kkaribackend.global.error.response.ValidErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        ServletRequestBindingException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class
    })
    protected ResponseEntity<ErrorResponse> handleBadRequestException(Exception e) {
        return respond(GlobalErrorCode.INVALID_INPUT, e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ValidErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        // FieldError.toString()에는 입력값이 들어가므로 필드 이름만 남긴다
        log.warn("{}: {}", e.getClass().getSimpleName(),
            e.getBindingResult().getFieldErrors().stream().map(FieldError::getField).toList());
        return ResponseEntity
            .status(GlobalErrorCode.INVALID_INPUT.getStatus())
            .body(ValidErrorResponse.of(GlobalErrorCode.INVALID_INPUT, e.getBindingResult()));
    }

    @ExceptionHandler({
        NoResourceFoundException.class,
        NoHandlerFoundException.class
    })
    protected ResponseEntity<ErrorResponse> handleNotFound(Exception e) {
        return respond(GlobalErrorCode.RESOURCE_NOT_FOUND, e);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleMethodNotAllowed(Exception e) {
        return respond(GlobalErrorCode.METHOD_NOT_ALLOWED, e);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    protected ResponseEntity<ErrorResponse> handleNotAcceptable(Exception e) {
        return respond(GlobalErrorCode.NOT_ACCEPTABLE, e);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    protected ResponseEntity<ErrorResponse> handleContentTooLarge(Exception e) {
        return respond(GlobalErrorCode.CONTENT_TOO_LARGE, e);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleUnsupportedMediaType(Exception e) {
        return respond(GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE, e);
    }


    private ResponseEntity<ErrorResponse> respond(ErrorProperty errorCode, Exception e) {
        // 요청 파싱·바인딩 예외 메시지에는 입력값이 들어갈 수 있어 예외 이름만 남긴다
        log.warn("{}: {}", e.getClass().getSimpleName(), errorCode.getCode());
        return ResponseEntity
            .status(errorCode.getStatus())
            .body(ErrorResponse.of(errorCode));
    }
}
