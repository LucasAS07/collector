package br.com.losystem.collector.repository;

import br.com.losystem.collector.model.TripHistory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ViagemRepository {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Long salvarOuAtualizar(
            Connection connection,
            TripHistory.Trip trip,
            Long caminhaoId
    ) throws SQLException {

        if (trip == null || trip.id() == null || trip.id().isBlank()) {
            throw new IllegalArgumentException(
                    "A viagem precisa possuir um ID externo."
            );
        }

        String sql = """
                INSERT INTO viagem (
                    id_externo,
                    caminhao_id,
                    status,
                    motivo_encerramento,
                    inicio_ms,
                    fim_ms,
                    duracao_segundos,
                    tempo_movimento_segundos,
                    carga_nome,
                    carga_peso,
                    origem_cidade,
                    origem_empresa,
                    destino_cidade,
                    destino_empresa,
                    distancia_km,
                    tempo_jogo_ms,
                    carreta_modelo,
                    segunda_carreta_modelo,
                    receita_bruta,
                    despesas,
                    lucro_liquido,
                    multas,
                    pedagios,
                    custo_combustivel,
                    cotacao_euro,
                    velocidade_media,
                    velocidade_maxima,
                    combustivel_consumido,
                    consumo_medio,
                    dano_caminhao,
                    dano_carga,
                    json_original
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb
                )
                ON CONFLICT (id_externo)
                DO UPDATE SET
                    caminhao_id = EXCLUDED.caminhao_id,
                    status = EXCLUDED.status,
                    motivo_encerramento = EXCLUDED.motivo_encerramento,
                    inicio_ms = EXCLUDED.inicio_ms,
                    fim_ms = EXCLUDED.fim_ms,
                    duracao_segundos = EXCLUDED.duracao_segundos,
                    tempo_movimento_segundos = EXCLUDED.tempo_movimento_segundos,
                    carga_nome = EXCLUDED.carga_nome,
                    carga_peso = EXCLUDED.carga_peso,
                    origem_cidade = EXCLUDED.origem_cidade,
                    origem_empresa = EXCLUDED.origem_empresa,
                    destino_cidade = EXCLUDED.destino_cidade,
                    destino_empresa = EXCLUDED.destino_empresa,
                    distancia_km = EXCLUDED.distancia_km,
                    tempo_jogo_ms = EXCLUDED.tempo_jogo_ms,
                    carreta_modelo = EXCLUDED.carreta_modelo,
                    segunda_carreta_modelo = EXCLUDED.segunda_carreta_modelo,
                    receita_bruta = EXCLUDED.receita_bruta,
                    despesas = EXCLUDED.despesas,
                    lucro_liquido = EXCLUDED.lucro_liquido,
                    multas = EXCLUDED.multas,
                    pedagios = EXCLUDED.pedagios,
                    custo_combustivel = EXCLUDED.custo_combustivel,
                    cotacao_euro = EXCLUDED.cotacao_euro,
                    velocidade_media = EXCLUDED.velocidade_media,
                    velocidade_maxima = EXCLUDED.velocidade_maxima,
                    combustivel_consumido = EXCLUDED.combustivel_consumido,
                    consumo_medio = EXCLUDED.consumo_medio,
                    dano_caminhao = EXCLUDED.dano_caminhao,
                    dano_carga = EXCLUDED.dano_carga,
                    json_original = EXCLUDED.json_original,
                    atualizado_em = NOW()
                RETURNING id
                """;

        var job = trip.job();
        var truck = trip.truck();
        var finances = trip.finances();
        var metrics = trip.metrics();
        var damage = trip.damage();

        String jsonOriginal;

        try {
            jsonOriginal = objectMapper.writeValueAsString(trip);
        } catch (JsonProcessingException e) {
            throw new SQLException(
                    "Erro ao converter viagem para JSON.",
                    e
            );
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            int i = 1;

            ps.setObject(i++, trip.id());
            ps.setObject(i++, caminhaoId);
            ps.setObject(i++, trip.status());
            ps.setObject(i++, trip.reason());
            ps.setObject(i++, trip.startedAt());
            ps.setObject(i++, trip.endedAt());
            ps.setObject(i++, trip.durationSeconds());
            ps.setObject(i++, trip.movingSeconds());

            ps.setObject(i++, job != null ? job.cargoName() : null);
            ps.setObject(i++, job != null ? job.cargoMass() : null);
            ps.setObject(i++, job != null ? job.originCity() : null);
            ps.setObject(i++, job != null ? job.originCompany() : null);
            ps.setObject(i++, job != null ? job.destinationCity() : null);
            ps.setObject(i++, job != null ? job.destinationCompany() : null);
            ps.setObject(i++, job != null ? job.distanceKm() : null);
            ps.setObject(i++, job != null ? job.gameTimeTaken() : null);

            ps.setObject(i++, truck != null ? truck.trailerModel() : null);
            ps.setObject(i++, truck != null ? truck.trailer2Model() : null);

            ps.setObject(i++, finances != null ? finances.gross() : null);
            ps.setObject(i++, finances != null ? finances.expenses() : null);
            ps.setObject(i++, finances != null ? finances.netProfit() : null);
            ps.setObject(i++, finances != null ? finances.fines() : null);
            ps.setObject(i++, finances != null ? finances.tolls() : null);
            ps.setObject(i++, finances != null ? finances.fuelCost() : null);
            ps.setObject(i++, finances != null ? finances.exchangeRate() : null);

            ps.setObject(i++, metrics != null ? metrics.averageSpeed() : null);
            ps.setObject(i++, metrics != null ? metrics.maxSpeed() : null);
            ps.setObject(i++, metrics != null ? metrics.fuelConsumed() : null);
            ps.setObject(i++, metrics != null ? metrics.averageConsumption() : null);

            ps.setObject(i++, damage != null ? damage.truckPercent() : null);
            ps.setObject(i++, damage != null ? damage.cargoPercent() : null);

            ps.setString(i, jsonOriginal);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getLong("id");
                }

                throw new SQLException(
                        "Não foi possível salvar a viagem."
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
                FROM viagem
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
