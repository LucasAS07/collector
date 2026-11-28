
package br.com.losystem.collector.repository;

import br.com.losystem.collector.model.TripHistory;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class ViagemEventoRepositoryTest {

    private final ViagemEventoRepository repository =
            new ViagemEventoRepository();

    @Test
    void deveGerarIdentificadorQuandoDetailForNull() throws SQLException {
        var evento = new TripHistory.TripEvent(
                "ENGINE",
                "Evento de teste",
                null,
                123456L
        );

        String id = repository.gerarIdExterno("viagem-1", evento);

        assertNotNull(id);
        assertEquals(64, id.length());
    }

    @Test
    void deveGerarIdentificadorQuandoTitleForNull() throws SQLException {
        var evento = new TripHistory.TripEvent(
                "ENGINE",
                null,
                "Detalhe de teste",
                123456L
        );

        String id = repository.gerarIdExterno("viagem-1", evento);

        assertNotNull(id);
        assertEquals(64, id.length());
    }

    @Test
    void deveGerarIdentificadorQuandoCreatedAtForNull() throws SQLException {
        var evento = new TripHistory.TripEvent(
                "ENGINE",
                "Evento de teste",
                "Detalhe de teste",
                null
        );

        String id = repository.gerarIdExterno("viagem-1", evento);

        assertNotNull(id);
        assertEquals(64, id.length());
    }

    @Test
    void deveGerarIdentificadorQuandoTodosOsCamposForemNull() throws SQLException {
        var evento = new TripHistory.TripEvent(
                null,
                null,
                null,
                null
        );

        String id = repository.gerarIdExterno("viagem-1", evento);

        assertNotNull(id);
        assertEquals(64, id.length());
    }

    @Test
    void deveGerarMesmoIdentificadorParaMesmoEvento() throws SQLException {
        var evento = new TripHistory.TripEvent(
                "ENGINE",
                "Evento de teste",
                null,
                123456L
        );

        String primeiroId = repository.gerarIdExterno("viagem-1", evento);
        String segundoId = repository.gerarIdExterno("viagem-1", evento);

        assertEquals(primeiroId, segundoId);
    }

    @Test
    void deveGerarIdentificadoresDiferentesParaEventosDiferentes() throws SQLException {
        var evento1 = new TripHistory.TripEvent(
                "ENGINE",
                "Evento de teste",
                null,
                123456L
        );

        var evento2 = new TripHistory.TripEvent(
                "ENGINE",
                "Evento de teste",
                null,
                123457L
        );

        String primeiroId = repository.gerarIdExterno("viagem-1", evento1);
        String segundoId = repository.gerarIdExterno("viagem-1", evento2);

        assertNotEquals(primeiroId, segundoId);
    }

    @Test
    void deveGerarIdentificadorNoFormatoSha256() throws SQLException {
        var evento = new TripHistory.TripEvent(
                "ENGINE",
                "Evento de teste",
                "Detalhe de teste",
                123456L
        );

        String id = repository.gerarIdExterno("viagem-1", evento);

        assertNotNull(id);
        assertEquals(64, id.length());
        assertTrue(id.matches("[a-f0-9]{64}"));
    }

    @Test
    void deveGerarIdentificadoresDiferentesParaViagensDiferentes() throws SQLException {
        var evento = new TripHistory.TripEvent(
                "ENGINE",
                "Evento de teste",
                "Detalhe de teste",
                123456L
        );

        String idViagem1 = repository.gerarIdExterno("viagem-1", evento);
        String idViagem2 = repository.gerarIdExterno("viagem-2", evento);

        assertNotEquals(idViagem1, idViagem2);
    }


}
