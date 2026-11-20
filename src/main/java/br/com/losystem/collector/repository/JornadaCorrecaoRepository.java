package br.com.losystem.collector.repository;

import br.com.losystem.collector.model.RuntimeState;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class JornadaCorrecaoRepository {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public int salvarTodos(
            Connection connection,
            Long jornadaId,
            RuntimeState.Shift shift
    ) throws SQLException {

        if (shift == null || shift.corrections() == null) {
            return 0;
        }

        String sql = """
                INSERT INTO jornada_correcao (
                    id_externo,
                    jornada_id,
                    json_original
                )
                VALUES (?, ?, ?::jsonb)
                ON CONFLICT (id_externo)
                DO UPDATE SET
                    jornada_id = EXCLUDED.jornada_id,
                    json_original = EXCLUDED.json_original
                """;

        int registrosProcessados = 0;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            for (var correcao : shift.corrections()) {

                if (correcao == null || correcao.isNull()) {
                    continue;
                }

                String idExterno = correcao.path("id").asText(null);

                if (idExterno == null || idExterno.isBlank()) {
                    continue;
                }

                try {
                    String json = objectMapper.writeValueAsString(correcao);

                    ps.setString(1, idExterno);
                    ps.setLong(2, jornadaId);
                    ps.setString(3, json);

                    ps.executeUpdate();

                    registrosProcessados++;

                } catch (Exception e) {
                    throw new SQLException(
                            "Erro ao salvar correção do tacógrafo: " + idExterno,
                            e
                    );
                }
            }
        }

        return registrosProcessados;
    }

}
