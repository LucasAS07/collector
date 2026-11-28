package br.com.losystem.collector;

import br.com.losystem.collector.config.ColetorLock;
import br.com.losystem.collector.config.DatabaseConfig;
import br.com.losystem.collector.repository.ImportacaoExecucaoRepository;
import br.com.losystem.collector.scheduler.ColetaScheduler;
import br.com.losystem.collector.service.ColetaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Properties;

public class Main {

    public static void main(String[] args) {

        final Logger logger = LoggerFactory.getLogger(Main.class);

        try {
            Properties properties = new Properties();

            try (InputStream input = Main.class
                    .getClassLoader()
                    .getResourceAsStream("application.properties")) {

                if (input == null) {
                    throw new RuntimeException(
                            "application.properties não encontrado."
                    );
                }

                properties.load(input);
            }

            properties.setProperty(
                    "db.username",
                    resolveEnvironmentVariable("USER_DB")
            );

            properties.setProperty(
                    "db.password",
                    resolveEnvironmentVariable("PASSWORD_DB")
            );

            DatabaseConfig databaseConfig =
                    new DatabaseConfig(properties);

            try (Connection connection =
                         databaseConfig.getConnection()) {

                logger.info("PostgreSQL conectado!");
                logger.info("Banco: {}", connection.getCatalog());
            }

            String rbrPath = properties.getProperty("rbr.path");

            Path tripHistoryPath = Path.of(
                    rbrPath,
                    "trip-history.json"
            );

            Path runtimeStatePath = Path.of(
                    rbrPath,
                    "runtime-state.json"
            );

            ColetaService coletaService =
                    new ColetaService(
                            databaseConfig,
                            tripHistoryPath,
                            runtimeStatePath
                    );

            ColetorLock coletorLock = new ColetorLock(databaseConfig);

            ColetaScheduler scheduler = new ColetaScheduler();

            Runtime.getRuntime().addShutdownHook(
                    new Thread(() -> {
                        logger.info("Encerrando coletor...");

                        scheduler.parar();

                        try {
                            coletorLock.close();
                            logger.info("Bloqueio do coletor liberado.");
                        } catch (Exception e) {
                            logger.error("Erro ao liberar bloqueio: ", e);
                        }
                    })
            );

            try (Connection connection = databaseConfig.getConnection()) {

                connection.setAutoCommit(false);

                try {
                    ImportacaoExecucaoRepository repository =
                            new ImportacaoExecucaoRepository();

                    int recuperadas =
                            repository.recuperarExecucoesAbandonadas(connection);

                    connection.commit();

                    logger.info("Execuções abandonadas recuperadas: {}",recuperadas);

                } catch (Exception e) {

                    try {
                        connection.rollback();
                    } catch (Exception erroRollback) {
                        e.addSuppressed(erroRollback);
                    }

                    throw e;
                }
            }

            logger.info("Coletor iniciado!");
            logger.info("Intervalo de verificação: 5 segundos.");

            scheduler.iniciar(() -> {
                try {
                    if (!coletorLock.garantirBloqueio()) {
                        logger.warn("Coleta ignorada: outra instância possui o bloqueio.");
                        return;
                    }

                    coletaService.executarCicloColeta();

                } catch (Exception e) {
                    logger.error("Erro durante o ciclo de coleta: ", e);
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            logger.error("Erro ao iniciar coletor", e);
            System.exit(1);
        }
    }

    private static String resolveEnvironmentVariable(String name) {

        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Variável de ambiente não configurada: " + name
            );
        }

        return value;
    }
}
