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

        if (jornada == null) {
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

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            // Segmentos históricos
            if (jornada.segments() != null) {

                for (RuntimeState.Segment segmento : jornada.segments()) {

                    if (segmento == null) {
                        continue;
                    }

                    segmentosProcessados += salvarSegmento(
                            ps,
                            jornadaId,
                            segmento
                    );
                }
            }

            // Segmento atual, que pode estar aberto
            RuntimeState.Segment segmentoAtual = jornada.currentSegment();

            if (segmentoAtual != null) {

                segmentosProcessados += salvarSegmento(
                        ps,
                        jornadaId,
                        segmentoAtual
                );
            }
        }

        return segmentosProcessados;
    }

    private int salvarSegmento(
            PreparedStatement ps,
            Long jornadaId,
            RuntimeState.Segment segmento
    ) throws SQLException {

        if (segmento.id() == null || segmento.id().isBlank()) {
            return 0;
        }

        try {
            String jsonOriginal =
                    objectMapper.writeValueAsString(segmento);

            ps.setString(1, segmento.id());
            ps.setLong(2, jornadaId);
            ps.setString(3, segmento.category());
            ps.setString(4, segmento.subtype());
            ps.setString(5, segmento.source());

            if (segmento.startAt() != null) {
                ps.setLong(6, segmento.startAt());
            } else {
                ps.setNull(6, java.sql.Types.BIGINT);
            }

            if (segmento.endAt() != null) {
                ps.setLong(7, segmento.endAt());
            } else {
                ps.setNull(7, java.sql.Types.BIGINT);
            }

            ps.setString(8, jsonOriginal);

            ps.executeUpdate();

            return 1;

        } catch (JsonProcessingException e) {
            throw new SQLException(
                    "Erro ao converter segmento para JSON: " + segmento.id(),
                    e
            );
        }
    }
}
