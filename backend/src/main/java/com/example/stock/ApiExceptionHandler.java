// Converts validation, missing data and transaction conflicts to the shared API error response.
package com.example.stock;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ApiExceptionHandler {
    record ErrorBody(String message) {}

    /** Returns a safe business message and its designated status. */
    @ExceptionHandler(Failure.class)
    ResponseEntity<ErrorBody> handleFailure(Failure failure) {
        return ResponseEntity.status(failure.status).body(new ErrorBody(failure.getMessage()));
    }

    /** Handles malformed JSON, missing parameters and parameters of the wrong numeric type. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> handleMalformedRequest() {
        return ResponseEntity.badRequest().body(new ErrorBody("请求字段缺失、格式有误或数值超出范围"));
    }

    /** Tells clients to retry after a database lock conflict rolls back the transaction. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ErrorBody> handleLockConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody("数据正在被其他操作使用，请重试"));
    }

    static final class Failure extends RuntimeException {
        private final HttpStatus status;

        private Failure(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        static Failure badRequest(String message) {
            return new Failure(HttpStatus.BAD_REQUEST, message);
        }

        static Failure notFound(String message) {
            return new Failure(HttpStatus.NOT_FOUND, message);
        }

        static Failure conflict(String message) {
            return new Failure(HttpStatus.CONFLICT, message);
        }
    }
}
