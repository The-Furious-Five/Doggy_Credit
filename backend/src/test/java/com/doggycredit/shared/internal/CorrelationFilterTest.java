package com.doggycredit.shared.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.doggycredit.TestcontainersConfiguration;

/**
 * RF-API-02.1: el filtro de correlación genera o propaga el id.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CorrelationFilterTest {

    @Autowired
    MockMvc mvc;

    @Test
    void generaCorrelationIdSiNoViene() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void reemplazaCorrelationIdMaliciosoOEnorme() throws Exception {
        String inyeccion = "abc\r\nINFO falso: acceso concedido";
        String enorme = "a".repeat(10_000);
        for (String malo : new String[] { inyeccion, enorme }) {
            mvc.perform(get("/actuator/health").header("X-Correlation-Id", malo))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Correlation-Id",
                            org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
        }
    }

    @Test
    void propagaCorrelationIdSiViene() throws Exception {
        String myId = "test-correlation-123";
        mvc.perform(get("/actuator/health").header("X-Correlation-Id", myId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", myId));
    }
}
