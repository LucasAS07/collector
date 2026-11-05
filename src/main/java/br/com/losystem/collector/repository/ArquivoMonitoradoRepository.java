package br.com.losystem.collector.repository;

import java.sql.*;
import java.time.Instant;

public class ArquivoMonitoradoRepository {

    public String buscarHashPorCaminho(
            Connection connection,
            String caminho
    ) throws SQLException {

        String sql = """
                SELECT hash_conteudo
                FROM arquivo_monitorado
                WHERE caminho = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, caminho);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getString("hash_conteudo");
                }
            }
        }

        return null;
    }

    public Long salvarOuAtualizar(
            Connection connection,
            String caminho,
            String nomeArquivo,
            Instant ultimaModificacao,
            String hash
    ) throws SQLException {

        String sql = """
                INSERT INTO arquivo_monitorado (
                    caminho,
                    nome_arquivo,
                    ultima_modificacao,
                    hash_conteudo,
                    ultima_leitura,
                    ativo
                )
                VALUES (?, ?, ?, ?, NOW(), TRUE)
                ON CONFLICT (caminho)
                DO UPDATE SET
                    nome_arquivo = EXCLUDED.nome_arquivo,
                    ultima_modificacao = EXCLUDED.ultima_modificacao,
                    hash_conteudo = EXCLUDED.hash_conteudo,
                    ultima_leitura = NOW(),
                    ativo = TRUE
                RETURNING id
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, caminho);
            ps.setString(2, nomeArquivo);
            ps.setTimestamp(
                    3,
                    Timestamp.from(ultimaModificacao)
            );
            ps.setString(4, hash);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getLong("id");
                }

                throw new SQLException(
                        "Não foi possível cadastrar o arquivo monitorado."
                );
            }
        }
    }

    public Long buscarIdPorCaminho(
            Connection connection,
            String caminho
    ) throws SQLException {

        String sql = """
            SELECT id
            FROM arquivo_monitorado
            WHERE caminho = ?
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, caminho);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }

        return null;
    }

    public void atualizarHash(
            Connection connection,
            Long arquivoId,
            String hash
    ) throws SQLException {

        String sql = """
            UPDATE arquivo_monitorado
            SET hash_conteudo = ?,
                ultima_modificacao = NOW(),
                ultima_leitura = NOW(),
                ativo = TRUE
            WHERE id = ?
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, hash);
            ps.setObject(2, arquivoId);

            ps.executeUpdate();
        }
    }


    public Long buscarOuCriar(
            Connection connection,
            String caminho,
            String nomeArquivo
    ) throws SQLException {

        String sql = """
            INSERT INTO arquivo_monitorado (
                caminho,
                nome_arquivo,
                ativo
            )
            VALUES (?, ?, TRUE)
            ON CONFLICT (caminho)
            DO UPDATE SET
                nome_arquivo = EXCLUDED.nome_arquivo,
                ativo = TRUE
            RETURNING id
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, caminho);
            ps.setString(2, nomeArquivo);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }

        throw new SQLException(
                "Não foi possível localizar ou cadastrar o arquivo."
        );
    }

}
