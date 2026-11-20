package br.com.losystem.collector.repository;

import br.com.losystem.collector.model.RuntimeState;
import br.com.losystem.collector.model.TripHistory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CaminhaoRepository {

    public Long buscarOuCriar(
            Connection connection,
            TripHistory.Truck truck
    ) throws SQLException {

        if (truck == null) {
            throw new IllegalArgumentException(
                    "Os dados do caminhão não foram informados."
            );
        }

        if (truck.licensePlate() == null ||
                truck.licensePlate().isBlank()) {

            throw new IllegalArgumentException(
                    "A placa do caminhão é obrigatória."
            );
        }

        String sql = """
                INSERT INTO caminhao (marca, modelo, placa)
                VALUES (?, ?, ?)
                ON CONFLICT (placa)
                DO UPDATE SET
                    marca = COALESCE(EXCLUDED.marca, caminhao.marca),
                    modelo = COALESCE(EXCLUDED.modelo, caminhao.modelo)
                RETURNING id
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, truck.make());
            ps.setString(2, truck.model());
            ps.setString(3, truck.licensePlate().trim());

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getLong("id");
                }

                throw new SQLException(
                        "Não foi possível obter o ID do caminhão."
                );
            }
        }
    }

    public Long buscarIdPorPlaca(Connection connection, String placa)
            throws SQLException {

        if (placa == null || placa.isBlank()) {
            return null;
        }

        String sql = """
            SELECT id
            FROM caminhao
            WHERE placa = ?
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, placa);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }

        return null;
    }


    public Long buscarOuCriar(
            Connection connection,
            RuntimeState.Truck truck
    ) throws SQLException {

        if (truck == null) {
            throw new IllegalArgumentException(
                    "Os dados do caminhão não foram informados."
            );
        }

        if (truck.licensePlate() == null ||
                truck.licensePlate().isBlank()) {

            throw new IllegalArgumentException(
                    "A placa do caminhão é obrigatória."
            );
        }


        return buscarOuCriar(
                connection,
                new TripHistory.Truck(
                        truck.make(),
                        truck.model(),
                        truck.licensePlate(),
                        null,
                        null
                )
        );

    }


}
