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

    @Test
    void deveLerJsonComHistoricoPreenchido() throws IOException {

        String json = """
            {
                "tachograph_state": {
                    "version": 1,
                    "history": [
                        {
                            "id": "shift-001",
                            "startedAt": 1730000000,
                            "endedAt": 1730003600,
                            "simulator": "ETS2",
                            "truck": {
                                "make": "Volvo",
                                "model": "FH16",
                                "licensePlate": "ABC123"
                            },
                            "distanceKm": 125.5,
                            "maxSpeedKph": 90.0,
                            "segments": [
                                {
                                    "id": "segment-001",
                                    "category": "DRIVING",
                                    "subtype": "NORMAL",
                                    "source": "TELEMETRY",
                                    "startAt": 1730000000,
                                    "endAt": 1730003600
                                }
                            ]
                        }
                    ]
                }
            }
            """;

        RuntimeState resultado = reader.read(
                json.getBytes(StandardCharsets.UTF_8)
        );

        assertNotNull(resultado);
        assertNotNull(resultado.tachograph_state());
        assertEquals(1, resultado.tachograph_state().history().size());

        RuntimeState.Shift jornada =
                resultado.tachograph_state().history().get(0);

        assertEquals("shift-001", jornada.id());
        assertEquals("ETS2", jornada.simulator());
        assertEquals("Volvo", jornada.truck().make());
        assertEquals("FH16", jornada.truck().model());
        assertEquals("ABC123", jornada.truck().licensePlate());

        assertEquals(0, jornada.distanceKm().compareTo(
                new java.math.BigDecimal("125.5")
        ));

        assertEquals(1, jornada.segments().size());
        assertEquals("DRIVING", jornada.segments().get(0).category());
    }

    @Test
    void deveLerJsonComCamposOpcionaisAusentes() throws IOException {

        String json = """
            {
                "tachograph_state": {
                    "version": 1,
                    "history": [
                        {
                            "id": "shift-002",
                            "startedAt": 1730000000,
                            "simulator": "ETS2"
                        }
                    ]
                }
            }
            """;

        RuntimeState resultado = reader.read(
                json.getBytes(StandardCharsets.UTF_8)
        );

        assertNotNull(resultado);
        assertNotNull(resultado.tachograph_state());

        RuntimeState.Shift jornada =
                resultado.tachograph_state().history().get(0);

        assertEquals("shift-002", jornada.id());
        assertEquals("ETS2", jornada.simulator());

        assertNull(jornada.truck());
        assertNull(jornada.segments());
        assertNull(jornada.warnings());
        assertNull(jornada.telemetry());
    }

    @Test
    void deveRejeitarTipoIncompativelNoCampoVersion() {

        String json = """
            {
                "tachograph_state": {
                    "version": "texto",
                    "history": []
                }
            }
            """;

        assertThrows(
                IOException.class,
                () -> reader.read(json.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void deveIgnorarPropriedadesDesconhecidas() throws IOException {

        String json = """
            {
                "tachograph_state": {
                    "version": 1,
                    "history": [],
                    "campo_desconhecido": "valor"
                },
                "propriedade_extra": {
                    "qualquer": true
                }
            }
            """;

        RuntimeState resultado = reader.read(
                json.getBytes(StandardCharsets.UTF_8)
        );

        assertNotNull(resultado);
        assertNotNull(resultado.tachograph_state());
        assertEquals(1, resultado.tachograph_state().version());
        assertTrue(resultado.tachograph_state().history().isEmpty());
    }

}