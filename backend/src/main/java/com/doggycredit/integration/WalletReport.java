package com.doggycredit.integration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.doggycredit.integration.BureauReport.Linaje;
import com.doggycredit.shared.Money;

/**
 * Fragmento del Perfil Financiero 360 v1 que aporta la billetera móvil (B-05):
 * {@code wallet} y, cuando no hay banco, {@code flujos} con origen WALLET.
 *
 * @param antiguedadMeses {@code wallet.antiguedadMeses}
 * @param saldoPromedio   {@code wallet.saldoPromedio}
 * @param meses           cantidad y montos por mes, del más antiguo al más reciente
 */
public record WalletReport(int antiguedadMeses, Money saldoPromedio, List<Mes> meses, LocalDate fechaCorte,
		Linaje linaje) {

	public WalletReport {
		meses = List.copyOf(meses);
	}

	/** {@code wallet.transaccionesMensuales}: promedio redondeado de los meses informados. */
	public int transaccionesMensuales() {
		return meses.isEmpty() ? 0
				: BigDecimal.valueOf(meses.stream().mapToInt(Mes::transacciones).sum())
					.divide(BigDecimal.valueOf(meses.size()), 0, RoundingMode.HALF_UP)
					.intValue();
	}

	public record Mes(YearMonth periodo, int transacciones, Money recibido, Money enviado) {
	}

}
