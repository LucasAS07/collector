package br.com.losystem.collector.repository;

import br.com.losystem.collector.model.TripHistory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class ViagemEventoRepositoryIntegracaoTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("collector_test")
                    .withUsername("test")
                    .withPassword("test");

    @Test
    void deveIniciarPostgreSQLParaTestes() {
        assertTrue(postgres.isRunning());
    }

    @BeforeAll
    static void criarTabela() throws SQLException {
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
             var statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE viagem_evento (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    id_externo VARCHAR NOT NULL UNIQUE,
                    viagem_id BIGINT NOT NULL,
                    tipo VARCHAR,
                    titulo VARCHAR,
                    detalhe TEXT,
                    ocorrido_em_ms BIGINT,
                    json_original JSONB
                )
                """);
        }
    }

    @Test
    void deveCriarTabelaViagemEvento() throws SQLException {
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                 SELECT COUNT(*)
                 FROM information_schema.tables
                 WHERE table_name = 'viagem_evento'
                 """)) {

            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }

    @Test
    void deveSalvarEventoNoBancoDeDados() throws SQLException {
        var repository = new ViagemEventoRepository();

        var evento = new TripHistory.TripEvent(
                "ENGINE",
                "Motor ligado",
                "Motor iniciado pelo jogador",
                123456L
        );

        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        )) {
            int inseridos = repository.salvarTodos(
                    connection,
                    1L,
                    "viagem-1",
                    List.of(evento)
            );

            assertEquals(1, inseridos);

            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                     SELECT tipo, titulo, detalhe, ocorrido_em_ms
                     FROM viagem_evento
                     """)) {

                assertTrue(result.next());
                assertEquals("ENGINE", result.getString("tipo"));
                assertEquals("Motor ligado", result.getString("titulo"));
                assertEquals(
                        "Motor iniciado pelo jogador",
                        result.getString("detalhe")
                );
                assertEquals(123456L, result.getLong("ocorrido_em_ms"));
            }
        }
    }

    @Test
    void naoDeveInserirEventoDuplicado() throws SQLException {
        var repository = new ViagemEventoRepository();

        var evento = new TripHistory.TripEvent(
                "ENGINE",
                "Motor ligado",
                "Motor iniciado pelo jogador",
                123456L
        );

        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        )) {
            int primeiraInsercao = repository.salvarTodos(
                    connection,
                    1L,
                    "viagem-duplicidade",
                    List.of(evento)
            );

            int segundaInsercao = repository.salvarTodos(
                    connection,
                    1L,
                    "viagem-duplicidade",
                    List.of(evento)
            );

            assertEquals(1, primeiraInsercao);
            assertEquals(0, segundaInsercao);

            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                     SELECT COUNT(*)
                     FROM viagem_evento
                     WHERE id_externo = '%s'
                     """.formatted(
                         repository.gerarIdExterno("viagem-duplicidade", evento)
                 ))) {

                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test
    void deveSalvarMultiplosEventos() throws SQLException {
        var repository = new ViagemEventoRepository();

        var eventos = List.of(
                new TripHistory.TripEvent(
                        "ENGINE",
                        "Motor ligado",
                        "Motor iniciado",
                        1000L
                ),
                new TripHistory.TripEvent(
                        "FUEL",
                        "Abastecimento",
                        "Tanque abastecido",
                        2000L
                ),
                new TripHistory.TripEvent(
                        "DAMAGE",
                        "Dano no caminhão",
                        "Colisão registrada",
                        3000L
                )
        );

        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        )) {
            int inseridos = repository.salvarTodos(
                    connection,
                    2L,
                    "viagem-multiplos",
                    eventos
            );

            assertEquals(3, inseridos);

            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                     SELECT COUNT(*)
                     FROM viagem_evento
                     WHERE viagem_id = 2
                     """)) {

                assertTrue(result.next());
                assertEquals(3, result.getInt(1));
            }
        }
    }

    @Test
    void naoDeveInserirQuandoListaForNull() throws SQLException {
        var repository = new ViagemEventoRepository();

        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        )) {
            int inseridos = repository.salvarTodos(
                    connection,
                    1L,
                    "viagem-null",
                    null
            );

            assertEquals(0, inseridos);
        }
    }

    @Test
    void naoDeveInserirQuandoListaEstiverVazia() throws SQLException {
        var repository = new ViagemEventoRepository();

        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        )) {
            int inseridos = repository.salvarTodos(
                    connection,
                    1L,
                    "viagem-vazia",
                    List.of()
            );

            assertEquals(0, inseridos);
        }
    }

    @Test
    void deveSalvarEventoComCamposNulos() throws SQLException {
        var repository = new ViagemEventoRepository();

        var evento = new TripHistory.TripEvent(
                null,
                null,
                null,
                null
        );

        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        )) {
            int inseridos = repository.salvarTodos(
                    connection,
                    3L,
                    "viagem-campos-nulos",
                    List.of(evento)
            );

            assertEquals(1, inseridos);

            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                     SELECT tipo, titulo, detalhe, ocorrido_em_ms,
                            json_original::text AS json_original
                     FROM viagem_evento
                     WHERE viagem_id = 3
                     """)) {

                assertTrue(result.next());
                assertNull(result.getString("tipo"));
                assertNull(result.getString("titulo"));
                assertNull(result.getString("detalhe"));

                assertEquals(
                        java.sql.Types.BIGINT,
                        result.getMetaData().getColumnType(4)
                );
                assertTrue(result.getObject("ocorrido_em_ms") == null);

                assertNotNull(result.getString("json_original"));
            }
        }
    }

    @Test
    void deveArmazenarJsonOriginalDoEvento() throws SQLException {
        var repository = new ViagemEventoRepository();

        var evento = new TripHistory.TripEvent(
                "FUEL",
                "Abastecimento",
                "Abasteceu 100 litros",
                987654L
        );

        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        )) {
            repository.salvarTodos(
                    connection,
                    4L,
                    "viagem-json",
                    List.of(evento)
            );

            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                     SELECT
                         json_original ->> 'type' AS tipo,
                         json_original ->> 'title' AS titulo,
                         json_original ->> 'detail' AS detalhe,
                         (json_original ->> 'createdAt')::BIGINT AS ocorrido_em
                     FROM viagem_evento
                     WHERE viagem_id = 4
                     """)) {

                assertTrue(result.next());
                assertEquals("FUEL", result.getString("tipo"));
                assertEquals("Abastecimento", result.getString("titulo"));
                assertEquals(
                        "Abasteceu 100 litros",
                        result.getString("detalhe")
                );
                assertEquals(987654L, result.getLong("ocorrido_em"));
            }
        }
    }

    @Test
    void deveInserirSomenteEventosNovosEmListaComDuplicados() throws SQLException {
        var repository = new ViagemEventoRepository();

        var eventoA = new TripHistory.TripEvent(
                "ENGINE",
                "Motor ligado",
                "Motor iniciado",
                1000L
        );

        var eventoB = new TripHistory.TripEvent(
                "FUEL",
                "Abastecimento",
                "Tanque abastecido",
                2000L
        );

        var eventoC = new TripHistory.TripEvent(
                "DAMAGE",
                "Dano registrado",
                "Colisão",
                3000L
        );

        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        )) {
            int primeiraInsercao = repository.salvarTodos(
                    connection,
                    5L,
                    "viagem-mista",
                    List.of(eventoB)
            );

            assertEquals(1, primeiraInsercao);

            int segundaInsercao = repository.salvarTodos(
                    connection,
                    5L,
                    "viagem-mista",
                    List.of(eventoA, eventoB, eventoC)
            );

            assertEquals(2, segundaInsercao);

            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                     SELECT COUNT(*)
                     FROM viagem_evento
                     WHERE viagem_id = 5
                     """)) {

                assertTrue(result.next());
                assertEquals(3, result.getInt(1));
            }
        }
    }

    @BeforeEach
    void limparTabela() throws SQLException {
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
             var statement = connection.createStatement()) {

            statement.execute("""
                TRUNCATE TABLE viagem_evento RESTART IDENTITY
                """);
        }
    }
}