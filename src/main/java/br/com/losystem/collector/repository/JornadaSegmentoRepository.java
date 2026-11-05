package br.com.losystem.collector.repository;

import br.com.losystem.collector.model.RuntimeState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class JornadaSegmentoRepository {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public int salvarTodos(
            Connection connection,
            Long jornadaId,
            RuntimeState.Shift jornada
    ) throws SQLException {

        if (jornada == null || jornada.segments() == null) {
            return 0;
        }

        String sql = """
                INSERT INTO jornada_segmento (
                    id_externo,
                    jornada_id,
                    categoria,
                    subtipo,
                    fonte,
                    inicio_ms,
                    fim_ms,
                    json_original
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (id_externo)
                DO UPDATE SET
                    jornada_id = EXCLUDED.jornada_id,
                    categoria = EXCLUDED.categoria,
                    subtipo = EXCLUDED.subtipo,
                    fonte = EXCLUDED.fonte,
                    inicio_ms = EXCLUDED.inicio_ms,
                    fim_ms = EXCLUDED.fim_ms,
                    json_original = EXCLUDED.json_original
                """;

        int segmentosProcessados = 0;

        for (RuntimeState.Segment segmento : jornada.segments()) {

            if (segmento == null ||
                    segmento.id() == null ||
                    segmento.id().isBlank()) {
                continue;
            }

            String jsonOriginal;

            try {
                jsonOriginal = objectMapper.writeValueAsString(segmento);
            } catch (JsonProcessingException e) {
                throw new SQLException(
                        "Erro ao converter segmento para JSON.",
                        e
                );
            }

            try (PreparedStatement ps = connection.prepareStatement(sql)) {

                int i = 1;

                ps.setString(i++, segmento.id());
                ps.setObject(i++, jornadaId);
                ps.setObject(i++, segmento.category());
                ps.setObject(i++, segmento.subtype());
                ps.setObject(i++, segmento.source());
                ps.setObject(i++, segmento.startAt());
                ps.setObject(i++, segmento.endAt());
                ps.setString(i, jsonOriginal);

                ps.executeUpdate();

                segmentosProcessados++;
            }
        }

        return segmentosProcessados;
    }

}
