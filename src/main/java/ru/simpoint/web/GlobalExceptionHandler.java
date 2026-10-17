package ru.simpoint.web;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.simpoint.exception.BusinessRuleException;
import ru.simpoint.exception.NotFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Не найдено", e.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail businessRule(BusinessRuleException e) {
        return problem(HttpStatus.CONFLICT, "Нарушение бизнес-правила", e.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail optimisticLock(ObjectOptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Конфликт изменений",
                "Запись была изменена параллельно, повторите операцию");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail dataIntegrity(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "Конфликт данных", "Нарушено ограничение целостности данных");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Ошибка валидации", "Проверьте поля запроса");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Некорректный запрос", e.getMessage());
    }

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ProblemDetail unauthorized(Exception e) {
        return problem(HttpStatus.UNAUTHORIZED, "Ошибка входа", "Неверный логин или пароль");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail forbidden(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "Доступ запрещён", "Недостаточно прав для этой операции");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
