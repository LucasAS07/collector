package br.com.losystem.collector.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ImportacaoExecucaoRepository {

    public Long iniciar(
            Connection connection,
            Long arquivoId
    ) throws SQLException {

        String sql = """
                INSERT INTO importacao_execucao (
                    arquivo_id,
                    iniciado_em,
                    status,
                    registros_lidos,
                    registros_inseridos,
                    registros_atualizados
                )
                VALUES (?, NOW(), 'EM_ANDAMENTO', 0, 0, 0)
                RETURNING id
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setObject(1, arquivoId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getLong("id");
                }

                throw new SQLException(
                        "Não foi possível iniciar a importação."
                );
            }
        }
    }

    public void finalizar(
            Connection connection,
            Long execucaoId,
            String status,
            int registrosLidos,
            int registrosInseridos,
            int registrosAtualizados,
            String mensagemErro
    ) throws SQLException {

        String sql = """
                UPDATE importacao_execucao
                SET
                    finalizado_em = NOW(),
                    status = ?,
                    registros_lidos = ?,
                    registros_inseridos = ?,
                    registros_atualizados = ?,
                    mensagem_erro = ?
                WHERE id = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, registrosLidos);
            ps.setInt(3, registrosInseridos);
            ps.setInt(4, registrosAtualizados);
            ps.setString(5, mensagemErro);
            ps.setObject(6, execucaoId);

            int registrosAfetados = ps.executeUpdate();

            if (registrosAfetados == 0) {
                throw new SQLException(
                        "Execução não encontrada: " + execucaoId
                );
            }
        }
    }

}
