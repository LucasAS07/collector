package br.com.losystem.collector.reader;

import br.com.losystem.collector.model.RuntimeState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeStateReaderTest {

    private RuntimeStateReader reader;

    @BeforeEach
    void setUp() {
        reader = new RuntimeStateReader();
    }

    @Test
    void deveLerJsonValido() throws IOException {

        String json = """
                {
                    "tachograph_state": {
                        "version": 1,
                        "history": []
                    }
                }
                """;

        RuntimeState resultado = reader.read(
                json.getBytes(StandardCharsets.UTF_8)
        );

        assertNotNull(resultado);
        assertNotNull(resultado.tachograph_state());
        assertEquals(1, resultado.tachograph_state().version());
        assertNotNull(resultado.tachograph_state().history());
        assertTrue(resultado.tachograph_state().history().isEmpty());
    }

    @Test
    void deveRejeitarConteudoVazio() {

        assertThrows(
                IOException.class,
                () -> reader.read(new byte[0])
        );
    }

    @Test
    void deveRejeitarConteudoNulo() {

        assertThrows(
                IOException.class,
                () -> reader.read(null)
        );
    }

    @Test
    void deveRejeitarJsonComValorNulo() {

        assertThrows(
                IOException.class,
                () -> reader.read("null".getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void deveRejeitarJsonMalformado() {

        String json = "{\"tachograph_state\": ";

        assertThrows(
                IOException.class,
                () -> reader.read(json.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void deveRejeitarConteudoAdicionalAposJson() {

        String json = """
                {
                    "tachograph_state": {
                        "version": 1,
                        "history": []
                    }
                }
                {}
                """;

        assertThrows(
                IOException.class,
                () -> reader.read(json.getBytes(StandardCharsets.UTF_8))
        );
    }

}