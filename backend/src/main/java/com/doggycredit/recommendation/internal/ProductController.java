package com.doggycredit.recommendation.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.shared.RoleGuard;
import com.doggycredit.shared.contract.api.ProductosApi;
import com.doggycredit.shared.contract.model.Producto;
import com.doggycredit.shared.contract.model.ProductoCambios;

/**
 * C-06 (LOS-86): GET /productos y PUT /productos/{id}, solo TENANT_ADMIN. Ruta y DTOs
 * vienen del contrato OpenAPI; el tenant sale del JWT (@TenantId + RLS).
 */
@RestController
class ProductController implements ProductosApi {

	static final String REQUIRED_ROLE = "TENANT_ADMIN";

	private final ProductCatalogService catalog;

	ProductController(ProductCatalogService catalog) {
		this.catalog = catalog;
	}

	@Override
	public ResponseEntity<List<Producto>> listarProductos() {
		RoleGuard.require(REQUIRED_ROLE);
		return ResponseEntity.ok(catalog.list().stream().map(ProductController::toDto).toList());
	}

	@Override
	public ResponseEntity<Producto> actualizarProducto(UUID id, ProductoCambios cambios) {
		RoleGuard.require(REQUIRED_ROLE);
		Product updated = catalog.update(id, cambios.getActivo(), new BigDecimal(cambios.getMontoMinimo()),
				new BigDecimal(cambios.getMontoMaximo()), cambios.getPlazoMinimoMeses(), cambios.getPlazoMaximoMeses());
		return ResponseEntity.ok(toDto(updated));
	}

	private static Producto toDto(Product product) {
		return new Producto().id(product.id())
			.tipo(Producto.TipoEnum.fromValue(product.productType()))
			.moneda(Producto.MonedaEnum.fromValue(product.currency()))
			.montoMinimo(money(product.minAmount()))
			.montoMaximo(money(product.maxAmount()))
			.plazoMinimoMeses(product.minTermMonths())
			.plazoMaximoMeses(product.maxTermMonths())
			.tasaAnual(money(product.annualRate()))
			.bandaMinima(Producto.BandaMinimaEnum.fromValue(product.minBand()))
			.activo(product.active());
	}

	private static String money(BigDecimal value) {
		return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
	}

}
