package br.com.losystem.collector.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// Bloqueia o banco para que 2 instacias do coletor não consiga coletar ao mesmo tempo.
public class ColetorLock {

    private static final int LOCK_ID_1 = 20261120;
    private static final int LOCK_ID_2 = 1;

    private final Connection connection;
    private boolean adquirido;

    public ColetorLock(DatabaseConfig databaseConfig)
            throws SQLException {

        this.connection = databaseConfig.getConnection();

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pg_try_advisory_lock(?, ?)")) {

            ps.setInt(1, LOCK_ID_1);
            ps.setInt(2, LOCK_ID_2);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    adquirido = rs.getBoolean(1);
                }
            }

            if (!adquirido) {
                connection.close();

                throw new IllegalStateException(
                        "Já existe uma instância do coletor em execução."
                );
            }

        } catch (SQLException | RuntimeException e) {

            if (!connection.isClosed()) {
                connection.close();
            }

            throw e;
        }
    }

    public void close() throws SQLException {

        if (!adquirido || connection.isClosed()) {
            return;
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pg_advisory_unlock(?, ?)")) {

            ps.setInt(1, LOCK_ID_1);
            ps.setInt(2, LOCK_ID_2);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    adquirido = !rs.getBoolean(1);
                }
            }

        } finally {
            connection.close();
        }
    }

}
