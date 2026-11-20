package br.com.losystem.collector;

import br.com.losystem.collector.config.ColetorLock;
import br.com.losystem.collector.config.DatabaseConfig;
import br.com.losystem.collector.repository.ImportacaoExecucaoRepository;
import br.com.losystem.collector.scheduler.ColetaScheduler;
import br.com.losystem.collector.service.ColetaService;

import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Properties;

public class Main {

    public static void main(String[] args) {

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

                System.out.println("PostgreSQL conectado!");
                System.out.println(
                        "Banco: " + connection.getCatalog()
                );
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
                        System.out.println("\nEncerrando coletor...");

                        scheduler.parar();

                        try {
                            coletorLock.close();
                            System.out.println("Bloqueio do coletor liberado.");
                        } catch (Exception e) {
                            System.err.println(
                                    "Erro ao liberar bloqueio: " + e.getMessage()
                            );
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

                    System.out.println(
                            "Execuções abandonadas recuperadas: " + recuperadas
                    );

                } catch (Exception e) {

                    try {
                        connection.rollback();
                    } catch (Exception erroRollback) {
                        e.addSuppressed(erroRollback);
                    }

                    throw e;
                }
            }

            System.out.println("\nColetor iniciado!");
            System.out.println(
                    "Intervalo de verificação: 5 segundos."
            );

            scheduler.iniciar(() -> {
                try {
                    coletaService.executarCicloColeta();
                } catch (Exception e) {
                    System.err.println(
                            "Erro durante o ciclo de coleta: " + e.getMessage()
                    );
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {

            System.err.println(
                    "Erro ao iniciar coletor: " + e.getMessage()
            );

            e.printStackTrace();
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
