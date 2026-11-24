package br.com.losystem.collector.reader;

import br.com.losystem.collector.model.TripHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TripHistoryReaderTest {
    private TripHistoryReader reader;

    @BeforeEach
    void setUp() {
        reader = new TripHistoryReader();
    }

    @Test
    void deveLerJsonValido() throws IOException {

        String json = """
                {
                    "version": 1,
                    "updatedAt": 1764000000000,
                    "items": []
                }
                """;

        TripHistory resultado = reader.read(
                json.getBytes(StandardCharsets.UTF_8)
        );

        assertNotNull(resultado);
        assertEquals(1, resultado.version());
        assertEquals(1764000000000L, resultado.updatedAt());
        assertNotNull(resultado.items());
        assertTrue(resultado.items().isEmpty());
    }

    @Test
    void deveRejeitarConteudoVazio() {

        byte[] conteudo = new byte[0];

        assertThrows(
                IOException.class,
                () -> reader.read(conteudo)
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

        byte[] conteudo = "null".getBytes(StandardCharsets.UTF_8);

        assertThrows(
                IOException.class,
                () -> reader.read(conteudo)
        );
    }

    @Test
    void deveRejeitarJsonMalformado() {

        String json = "{ \"version\": 1, \"items\": ";

        byte[] conteudo = json.getBytes(StandardCharsets.UTF_8);

        assertThrows(
                IOException.class,
                () -> reader.read(conteudo)
        );
    }

    @Test
    void deveRejeitarConteudoAdicionalAposJson() {

        String json = """
                {
                    "version": 1,
                    "updatedAt": 1764000000000,
                    "items": []
                }
                {}
                """;

        byte[] conteudo = json.getBytes(StandardCharsets.UTF_8);

        assertThrows(
                IOException.class,
                () -> reader.read(conteudo)
        );
    }
}