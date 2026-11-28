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

    @Test
    void deveLerJsonComViagemCompleta() throws IOException {

        String json = """
            {
                "version": 1,
                "updatedAt": 1764000000000,
                "items": [
                    {
                        "id": "trip-001",
                        "clientDeliveryId": "delivery-001",
                        "status": "completed",
                        "reason": "delivered",
                        "startedAt": 1763990000000,
                        "endedAt": 1764000000000,
                        "durationSeconds": 3600.5,
                        "movingSeconds": 3200.5,
                        "job": {
                            "cargoName": "Eletrônicos",
                            "cargoMass": 12000.5,
                            "originCity": "São Paulo",
                            "originCompany": "Empresa A",
                            "destinationCity": "Rio de Janeiro",
                            "destinationCompany": "Empresa B",
                            "distanceKm": 450.75,
                            "gameTimeTaken": 3600
                        },
                        "truck": {
                            "make": "Volvo",
                            "model": "FH16",
                            "licensePlate": "ABC123",
                            "trailerModel": "Refrigerated"
                        },
                        "finances": {
                            "gross": 15000.50,
                            "expenses": 2500.25,
                            "netProfit": 12500.25,
                            "fines": 100.00,
                            "tolls": 200.00,
                            "fuelCost": 1200.50,
                            "exchangeRate": 1.0,
                            "expensesList": []
                        },
                        "metrics": {
                            "averageSpeed": 75.5,
                            "maxSpeed": 110.0,
                            "fuelConsumed": 150.25,
                            "averageConsumption": 32.5
                        },
                        "damage": {
                            "truckPercent": 1.5,
                            "cargoPercent": 0.5
                        },
                        "events": [
                            {
                                "type": "delivery",
                                "title": "Entrega concluída",
                                "detail": "Carga entregue com sucesso",
                                "createdAt": 1764000000000
                            }
                        ],
                        "createdAt": 1764000000000
                    }
                ]
            }
            """;

        TripHistory resultado = reader.read(
                json.getBytes(StandardCharsets.UTF_8)
        );

        assertNotNull(resultado);
        assertEquals(1, resultado.version());
        assertEquals(1, resultado.items().size());

        TripHistory.Trip viagem = resultado.items().get(0);

        assertEquals("trip-001", viagem.id());
        assertEquals("completed", viagem.status());
        assertEquals("Volvo", viagem.truck().make());
        assertEquals("FH16", viagem.truck().model());
        assertEquals("Eletrônicos", viagem.job().cargoName());
        assertEquals(1, viagem.events().size());
        assertEquals("Entrega concluída", viagem.events().get(0).title());
    }

    @Test
    void devePreservarPrecisaoDosValoresDecimais() throws IOException {

        String json = """
            {
                "version": 1,
                "items": [
                    {
                        "id": "trip-002",
                        "durationSeconds": 3600.75,
                        "job": {
                            "cargoMass": 12500.50,
                            "distanceKm": 450.75
                        },
                        "finances": {
                            "gross": 15000.50,
                            "expenses": 2500.25,
                            "netProfit": 12500.25
                        },
                        "metrics": {
                            "averageSpeed": 75.55,
                            "maxSpeed": 110.25
                        }
                    }
                ]
            }
            """;

        TripHistory resultado = reader.read(
                json.getBytes(StandardCharsets.UTF_8)
        );

        TripHistory.Trip viagem = resultado.items().get(0);

        assertEquals(
                0,
                new java.math.BigDecimal("15000.50")
                        .compareTo(viagem.finances().gross())
        );

        assertEquals(
                0,
                new java.math.BigDecimal("450.75")
                        .compareTo(viagem.job().distanceKm())
        );

        assertEquals(
                0,
                new java.math.BigDecimal("75.55")
                        .compareTo(viagem.metrics().averageSpeed())
        );

        assertEquals(
                0,
                new java.math.BigDecimal("3600.75")
                        .compareTo(viagem.durationSeconds())
        );
    }

    @Test
    void deveIgnorarPropriedadesDesconhecidasNosObjetosInternos()
            throws IOException {

        String json = """
            {
                "version": 1,
                "updatedAt": 1764000000000,
                "items": [
                    {
                        "id": "trip-003",
                        "status": "completed",
                        "campoDesconhecido": true,
                        "job": {
                            "cargoName": "Eletrônicos",
                            "novoCampo": 123
                        }
                    }
                ]
            }
            """;

        TripHistory resultado = reader.read(
                json.getBytes(StandardCharsets.UTF_8)
        );

        assertNotNull(resultado);
        assertEquals(1, resultado.version());
        assertEquals(1, resultado.items().size());

        TripHistory.Trip viagem = resultado.items().get(0);

        assertEquals("trip-003", viagem.id());
        assertEquals("completed", viagem.status());
        assertEquals("Eletrônicos", viagem.job().cargoName());
    }

    @Test
    void deveRejeitarPropriedadesDesconhecidasNaRaiz() {

        String json = """
            {
                "version": 1,
                "updatedAt": 1764000000000,
                "items": [],
                "campoNovo": "valor"
            }
            """;

        assertThrows(
                IOException.class,
                () -> reader.read(json.getBytes(StandardCharsets.UTF_8))
        );
    }
}