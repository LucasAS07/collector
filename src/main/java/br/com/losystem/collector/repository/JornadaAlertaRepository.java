package br.com.losystem.collector.repository;

import br.com.losystem.collector.model.RuntimeState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class JornadaAlertaRepository {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public int salvarTodos(
            Connection connection,
            Long jornadaId,
            RuntimeState.Shift jornada
    ) throws SQLException {

        if (jornada == null || jornada.warnings() == null) {
            return 0;
        }

        String sql = """
                INSERT INTO jornada_alerta (
                    id_externo,
                    jornada_id,
                    tipo,
                    ocorrido_em_ms,
                    inicio_ms,
                    json_original
                )
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (id_externo)
                DO UPDATE SET
                    jornada_id = EXCLUDED.jornada_id,
                    tipo = EXCLUDED.tipo,
                    ocorrido_em_ms = EXCLUDED.ocorrido_em_ms,
                    inicio_ms = EXCLUDED.inicio_ms,
                    json_original = EXCLUDED.json_original
                """;

        int alertasProcessados = 0;

        for (RuntimeState.Warning alerta : jornada.warnings()) {

            if (alerta == null ||
                    alerta.id() == null ||
                    alerta.id().isBlank()) {
                continue;
            }

            String jsonOriginal;

            try {
                jsonOriginal = objectMapper.writeValueAsString(alerta);
            } catch (JsonProcessingException e) {
                throw new SQLException(
                        "Erro ao converter alerta para JSON.",
                        e
                );
            }

            try (PreparedStatement ps = connection.prepareStatement(sql)) {

                int i = 1;

                ps.setString(i++, alerta.id());
                ps.setObject(i++, jornadaId);
                ps.setObject(i++, alerta.kind());
                ps.setObject(i++, alerta.at());
                ps.setObject(i++, alerta.startAt());
                ps.setString(i, jsonOriginal);

                ps.executeUpdate();

                alertasProcessados++;
            }
        }

        return alertasProcessados;
    }
}
