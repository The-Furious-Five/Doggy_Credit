package com.doggycredit.shared.internal;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.doggycredit.shared.ErrorCode;

/**
 * Dos personas editan el mismo registro (con {@code @Version}) a la vez: el segundo guardado
 * choca con la versión y Hibernate lanza {@link ObjectOptimisticLockingFailureException}.
 * Sin esto la respuesta era 500; es un conflicto que el cliente resuelve recargando: 409.
 */
@RestControllerAdvice
class ConcurrentUpdateAdvice {

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ResponseEntity<ProblemDetail> handle(ObjectOptimisticLockingFailureException exception) {
		ProblemDetail problem = ErrorCode.CONCURRENT_UPDATE.toProblemDetail(
				"Otra persona modificó este registro al mismo tiempo. Recarga y vuelve a intentarlo.");
		return ResponseEntity.status(problem.getStatus()).body(problem);
	}

}
