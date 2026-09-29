package com.doggycredit.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentNumberTest {

	@Test
	void normalizaEspaciosPuntosYMayusculas() {
		assertThat(DocumentNumber.normalize(" 12.345.678-k ")).isEqualTo("12345678-K");
		assertThat(DocumentNumber.normalize("12 345\t678-K")).isEqualTo("12345678-K");
		assertThat(DocumentNumber.normalize("ab.c 1")).isEqualTo("ABC1");
		assertThat(DocumentNumber.normalize("12345678-K")).isEqualTo("12345678-K");
	}

}
