package com.doggycredit.shared.internal;

import java.util.Comparator;
import java.util.stream.Collectors;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.doggycredit.shared.ErrorCode;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * Errores de validación del contrato como 400 VALIDATION_ERROR (RFC 9457, catálogo único).
 *
 * <p>Un parámetro de consulta fuera de rango (p. ej. {@code tamano=101}) lo valida Bean Validation
 * sobre la interfaz generada del contrato y lanza {@link ConstraintViolationException}, que
 * Spring no traduce: sin esto la respuesta era 500. Es un error del cliente: 400 VALIDATION_ERROR.
 */
// Antes que el ProblemDetailsExceptionHandler de Spring Boot (spring.mvc.problemdetails), que
// también atiende MethodArgumentNotValidException pero sin el code del catálogo.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class ConstraintViolationAdvice {

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ProblemDetail> handle(ConstraintViolationException exception) {
		String detail = exception.getConstraintViolations().stream()
			.map(ConstraintViolationAdvice::describe)
			.sorted(Comparator.naturalOrder())
			.collect(Collectors.joining("; "));
		ProblemDetail problem = ErrorCode.VALIDATION_ERROR.toProblemDetail(
				detail.isEmpty() ? "Uno o más campos no son válidos." : detail);
		return ResponseEntity.status(problem.getStatus()).body(problem);
	}

	/**
	 * Un cuerpo JSON que no cumple el contrato (p. ej. falta {@code justificacion}, obligatoria)
	 * lo rechaza {@code @Valid @RequestBody} con {@link MethodArgumentNotValidException}. Spring
	 * respondía su 400 por defecto, sin el {@code code} del catálogo único: 400 VALIDATION_ERROR.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> handle(MethodArgumentNotValidException exception) {
		String detail = exception.getBindingResult().getFieldErrors().stream()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.sorted(Comparator.naturalOrder())
			.collect(Collectors.joining("; "));
		ProblemDetail problem = ErrorCode.VALIDATION_ERROR.toProblemDetail(
				detail.isEmpty() ? "Uno o más campos no son válidos." : detail);
		return ResponseEntity.status(problem.getStatus()).body(problem);
	}

	/** "tamano: must be less than or equal to 100": solo el nombre del parámetro, sin el método. */
	private static String describe(ConstraintViolation<?> violation) {
		String path = violation.getPropertyPath().toString();
		String field = path.substring(path.lastIndexOf('.') + 1);
		return field + ": " + violation.getMessage();
	}

}
