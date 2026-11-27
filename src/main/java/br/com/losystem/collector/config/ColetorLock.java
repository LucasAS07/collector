
package br.com.losystem.collector.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ColetorLock implements AutoCloseable {

    private static final int LOCK_ID_1 = 20261120;
    private static final int LOCK_ID_2 = 1;

    private final DatabaseConfig databaseConfig;

    private Connection connection;
    private boolean adquirido;

    public ColetorLock(DatabaseConfig databaseConfig)
            throws SQLException {

        this.databaseConfig = databaseConfig;

        if (!garantirBloqueio()) {
            throw new IllegalStateException(
                    "Já existe uma instância do coletor em execução."
            );
        }
    }

    public synchronized boolean garantirBloqueio()
            throws SQLException {

        if (adquirido && connection != null) {

            try (PreparedStatement ps =
                         connection.prepareStatement("SELECT 1");
                 ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return true;
                }

            } catch (SQLException e) {
                fecharConexaoLocal();
            }
        }

        connection = databaseConfig.getConnection();

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pg_try_advisory_lock(?, ?)")) {

            ps.setInt(1, LOCK_ID_1);
            ps.setInt(2, LOCK_ID_2);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next() && rs.getBoolean(1)) {
                    adquirido = true;
                    return true;
                }
            }

            fecharConexaoLocal();
            return false;

        } catch (SQLException | RuntimeException e) {

            fecharConexaoLocal();
            throw e;
        }
    }

    private void fecharConexaoLocal() {

        adquirido = false;

        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }

            connection = null;
        }
    }

    @Override
    public synchronized void close() {
        fecharConexaoLocal();
    }
}
