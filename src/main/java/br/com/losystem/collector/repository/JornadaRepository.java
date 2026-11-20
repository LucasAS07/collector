package br.com.losystem.collector.repository;

import br.com.losystem.collector.model.RuntimeState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class JornadaRepository {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Long salvarOuAtualizar(
            Connection connection,
            RuntimeState.Shift jornada,
            Long caminhaoId
    ) throws SQLException {

        if (jornada == null || jornada.id() == null || jornada.id().isBlank()) {
            throw new IllegalArgumentException(
                    "A jornada precisa possuir um ID externo."
            );
        }

        String sql = """
                INSERT INTO jornada (
                    id_externo,
                    caminhao_id,
                    simulador,
                    inicio_ms,
                    fim_ms,
                    hodometro_inicial,
                    hodometro_final,
                    distancia_km,
                    velocidade_maxima,
                    atividade_declarada,
                    json_original
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb
                )
                ON CONFLICT (id_externo)
                DO UPDATE SET
                    caminhao_id = EXCLUDED.caminhao_id,
                    simulador = EXCLUDED.simulador,
                    inicio_ms = EXCLUDED.inicio_ms,
                    fim_ms = EXCLUDED.fim_ms,
                    hodometro_inicial = EXCLUDED.hodometro_inicial,
                    hodometro_final = EXCLUDED.hodometro_final,
                    distancia_km = EXCLUDED.distancia_km,
                    velocidade_maxima = EXCLUDED.velocidade_maxima,
                    atividade_declarada = EXCLUDED.atividade_declarada,
                    json_original = EXCLUDED.json_original,
                    atualizado_em = NOW()
                RETURNING id
                """;

        String jsonOriginal;

        try {
            jsonOriginal = objectMapper.writeValueAsString(jornada);
        } catch (JsonProcessingException e) {
            throw new SQLException(
                    "Erro ao converter jornada para JSON.",
                    e
            );
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            int i = 1;

            ps.setObject(i++, jornada.id());
            ps.setObject(i++, caminhaoId);
            ps.setObject(i++, jornada.simulator());
            ps.setObject(i++, jornada.startedAt());
            ps.setObject(i++, jornada.endedAt());

            ps.setObject(i++, jornada.startOdometerKm());
            ps.setObject(i++, jornada.endOdometerKm());
            ps.setObject(i++, jornada.distanceKm());
            ps.setObject(i++, jornada.maxSpeedKph());

            JsonNode declaredActivity = jornada.declaredActivity();

            String atividadeDeclarada = null;

            if (declaredActivity != null && !declaredActivity.isNull()) {

                if (declaredActivity.isTextual()) {
                    atividadeDeclarada = declaredActivity.asText();
                } else {
                    atividadeDeclarada = declaredActivity.toString();
                }
            }

            ps.setString(i++, atividadeDeclarada);

            ps.setString(i, jsonOriginal);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getLong("id");
                }

                throw new SQLException(
                        "Não foi possível salvar a jornada."
                );
            }
        }
    }

    public boolean existePorIdExterno(
            Connection connection,
            String idExterno
    ) throws SQLException {

        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM jornada
                WHERE id_externo = ?
            )
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, idExterno);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        }

        return false;
    }
}
