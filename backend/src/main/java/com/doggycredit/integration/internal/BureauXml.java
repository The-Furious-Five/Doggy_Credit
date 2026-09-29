package com.doggycredit.integration.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.doggycredit.consent.Source;
import com.doggycredit.integration.BureauReport;
import com.doggycredit.integration.BureauReport.Endeudamiento;
import com.doggycredit.integration.BureauReport.Linaje;
import com.doggycredit.integration.BureauReport.TipoTitular;
import com.doggycredit.integration.SourceUnavailableException;
import com.doggycredit.shared.Money;

/**
 * Capa anticorrupción del buró: XML del proveedor (contracts/connectors/bureau.v1.yaml)
 * → fragmento canónico v1. Todo o nada: si algo no cuadra, el informe entero es
 * {@link SourceUnavailableException}, nunca un mapeo parcial.
 */
public final class BureauXml {

	static final String CONNECTOR_VERSION = "bureau-connector/1";

	static final String MAPPING_VERSION = "bureau.v1->perfil-financiero.v1";

	private static final Set<String> RATINGS = Set.of("A", "B", "C", "D", "E", "F");

	private static final String NO_HISTORY = "N";

	private static final Set<String> CURRENCIES = Set.of("BOB", "USD");

	private BureauXml() {
	}

	/** ¿Es el 404 propio del buró para este documento? */
	public static boolean isDocumentNotFound(byte[] body, String documento) {
		try {
			Element root = parse(body);
			return root.getTagName().equals("ErrorBuro") && text(root, "Codigo").equals("DOCUMENTO_NO_ENCONTRADO")
					&& text(root, "Documento").equals(documento);
		}
		catch (SourceUnavailableException e) {
			return false;
		}
	}

	/** @param documento documento normalizado que se pidió; el informe debe ser de ese titular. */
	public static BureauReport toReport(byte[] body, String documento, Instant obtainedAt) {
		Element root = parse(body);
		try {
			if (!root.getTagName().equals("InformeCrediticio")) {
				throw malformed("raíz inesperada <" + root.getTagName() + ">", null);
			}
			if (!text(root, "Documento").equals(documento)) {
				throw malformed("el informe es de otro documento", null);
			}
			TipoTitular tipo = switch (text(root, "TipoTitular")) {
				case "PERSONA" -> TipoTitular.PERSONA;
				case "PYME" -> TipoTitular.PYME;
				default -> throw malformed("TipoTitular desconocido", null);
			};
			String rating = child(root, "Calificacion").getAttribute("codigo");
			Money saldo = money(child(root, "DeudaVigente"));
			Money cuota = money(child(root, "CuotaMensual"));
			int mora = nonNegative(child(root, "MoraMaximaUltimos12Meses").getAttribute("dias"));
			int antiguedad = nonNegative(text(root, "AntiguedadSistemaFinancieroMeses"));
			LocalDate fechaCorte = LocalDate.parse(text(root, "FechaCorte"));

			List<Endeudamiento> deudas;
			if (RATINGS.contains(rating)) {
				deudas = List.of(new Endeudamiento(Source.BURO, "CONSOLIDADO", saldo, cuota, mora, rating, fechaCorte));
			}
			else if (NO_HISTORY.equals(rating) && saldo.amount().signum() == 0 && cuota.amount().signum() == 0) {
				deudas = List.of();
			}
			else {
				throw malformed("Calificacion '" + rating + "' inválida o incoherente con la deuda", null);
			}
			Linaje linaje = new Linaje(Source.BURO, CONNECTOR_VERSION, MAPPING_VERSION, UUID.randomUUID().toString(),
					sha256(body), obtainedAt);
			return new BureauReport(tipo, antiguedad, deudas, linaje);
		}
		catch (IllegalArgumentException | DateTimeException e) {
			throw malformed(e.getMessage(), e);
		}
	}

	private static Element parse(byte[] body) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			// XXE: sin DOCTYPE, sin entidades externas, sin XInclude.
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(new DefaultHandler()); // sin esto, el parser escribe en System.err
			return builder.parse(new ByteArrayInputStream(body)).getDocumentElement();
		}
		catch (ParserConfigurationException | SAXException | IOException e) {
			throw malformed("XML inválido", e);
		}
	}

	private static Element child(Element parent, String name) {
		NodeList nodes = parent.getElementsByTagName(name);
		if (nodes.getLength() != 1) {
			throw malformed("se esperaba exactamente un <" + name + ">", null);
		}
		return (Element) nodes.item(0);
	}

	private static String text(Element parent, String name) {
		return child(parent, name).getTextContent().strip();
	}

	private static Money money(Element element) {
		String currency = element.getAttribute("moneda");
		BigDecimal amount = new BigDecimal(element.getTextContent().strip());
		if (!CURRENCIES.contains(currency) || amount.signum() < 0) {
			throw malformed("monto inválido en <" + element.getTagName() + ">", null);
		}
		return new Money(amount, Currency.getInstance(currency));
	}

	private static int nonNegative(String value) {
		int n = Integer.parseInt(value.strip());
		if (n < 0) {
			throw new IllegalArgumentException("valor negativo: " + n);
		}
		return n;
	}

	private static String sha256(byte[] body) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static SourceUnavailableException malformed(String detail, Throwable cause) {
		return new SourceUnavailableException(Source.BURO, "informe mal formado: " + detail, cause);
	}

}
