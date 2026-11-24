package br.com.losystem.collector.repository;

import br.com.losystem.collector.model.TripHistory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

public class ViagemEventoRepository {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public int salvarTodos(
            Connection connection,
            Long viagemId,
            String viagemIdExterno,
            List<TripHistory.TripEvent> eventos
    ) throws SQLException {

        if (eventos == null || eventos.isEmpty()) {
            return 0;
        }

        String sql = """
                INSERT INTO viagem_evento (
                    id_externo,
                    viagem_id,
                    tipo,
                    titulo,
                    detalhe,
                    ocorrido_em_ms,
                    json_original
                )
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (id_externo) DO NOTHING
                """;

        int inseridos = 0;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            for (TripHistory.TripEvent evento : eventos) {

                String idExterno = gerarIdExterno(
                        viagemIdExterno,
                        evento
                );

                String jsonOriginal;

                try {
                    jsonOriginal = objectMapper.writeValueAsString(evento);
                } catch (JsonProcessingException e) {
                    throw new SQLException(
                            "Erro ao converter evento para JSON.",
                            e
                    );
                }

                ps.setString(1, idExterno);
                ps.setLong(2, viagemId);
                ps.setString(3, evento.type());
                ps.setString(4, evento.title());
                ps.setString(5, evento.detail());

                if (evento.createdAt() != null) {
                    ps.setLong(6, evento.createdAt());
                } else {
                    ps.setNull(6, java.sql.Types.BIGINT);
                }

                ps.setString(7, jsonOriginal);

                inseridos += ps.executeUpdate();
            }
        }

        return inseridos;
    }


     String gerarIdExterno(
            String viagemIdExterno,
            TripHistory.TripEvent evento
    ) throws SQLException {

        try {
            String conteudo = objectMapper.writeValueAsString(
                    Arrays.asList(
                            viagemIdExterno,
                            evento.type(),
                            evento.title(),
                            evento.detail(),
                            evento.createdAt()
                    )
            );

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    conteudo.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (JsonProcessingException |
                 NoSuchAlgorithmException e) {

            throw new SQLException(
                    "Erro ao gerar identificador do evento.",
                    e
            );
        }
    }


}
