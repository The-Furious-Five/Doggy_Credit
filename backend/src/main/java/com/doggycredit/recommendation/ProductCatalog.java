package com.doggycredit.recommendation;

import java.util.List;
import java.util.UUID;

import com.doggycredit.shared.contract.model.Producto;

/**
 * Catálogo de productos del tenant actual (C-02), para otros módulos. Lo usa la bandeja
 * de revisión manual (C-10, RF-DEC-03): ANALYST no puede leer /productos, pero al aprobar
 * un caso tiene que elegir uno de los productos activos.
 */
public interface ProductCatalog {

	/** Productos activos del tenant actual (RLS), en el orden del catálogo. */
	List<Producto> activeProducts();

	/** true si el producto existe, es del tenant actual y está activo. */
	boolean isActive(UUID productId);

}
