package com.doggycredit.recommendation.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.audit.AuditTrail;
import com.doggycredit.shared.ErrorCode;
import com.doggycredit.shared.TenantContext;

/** C-06 (LOS-86): consulta y edición del catálogo de productos del tenant actual. */
@Service
class ProductCatalogService {

	private final ProductRepository products;
	private final AuditTrail audit;

	ProductCatalogService(ProductRepository products, AuditTrail audit) {
		this.products = products;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	List<Product> list() {
		return products.findAllByOrderByProductTypeAsc();
	}

	/**
	 * Cambia el interruptor y los rangos. Un producto de otro tenant no se ve (RLS +
	 * @TenantId), así que responde igual que uno inexistente: 404, sin distinguir cuál.
	 * El cambio queda en la auditoría (el informe pide auditar los cambios de política).
	 */
	@Transactional
	Product update(UUID id, boolean active, BigDecimal minAmount, BigDecimal maxAmount, int minTermMonths,
			int maxTermMonths) {
		if (minAmount.signum() <= 0 || maxAmount.compareTo(minAmount) < 0) {
			throw invalid("El monto máximo no puede ser menor que el mínimo, y el mínimo debe ser positivo.");
		}
		if (minTermMonths < 1 || maxTermMonths < minTermMonths) {
			throw invalid("El plazo máximo no puede ser menor que el mínimo, y el mínimo es de 1 mes.");
		}
		Product product = products.findById(id)
			.orElseThrow(() -> new ErrorResponseException(HttpStatus.NOT_FOUND,
					ErrorCode.PRODUCT_NOT_FOUND.toProblemDetail("El producto no existe o pertenece a otro tenant."),
					null));
		product.update(active, minAmount, maxAmount, minTermMonths, maxTermMonths);
		audit.record(TenantContext.currentOrNone(), "PRODUCT_UPDATED", "PRODUCT", product.id());
		return product;
	}

	private static ErrorResponseException invalid(String detail) {
		return new ErrorResponseException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.toProblemDetail(detail),
				null);
	}

}
