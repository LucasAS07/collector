package br.com.losystem.collector;


import br.com.losystem.collector.config.DatabaseConfig;
import br.com.losystem.collector.model.RuntimeState;
import br.com.losystem.collector.model.TripHistory;
import br.com.losystem.collector.reader.RuntimeStateReader;
import br.com.losystem.collector.reader.TripHistoryReader;
import br.com.losystem.collector.repository.*;
import br.com.losystem.collector.util.HashUtils;

import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Properties;

public class Main {
    public static void main(String[] args) {
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
                System.out.println("Banco: " + connection.getCatalog());
            }

            // Lê o histórico de viagens
            String rbrPath = properties.getProperty("rbr.path");

            Path tripHistoryPath = Path.of(
                    rbrPath,
                    "trip-history.json"
            );

            importarHistoricoViagens(
                    databaseConfig,
                    tripHistoryPath
            );

            CaminhaoRepository caminhaoRepository =
                    new CaminhaoRepository();

            Path runtimeStatePath = Path.of(
                    rbrPath,
                    "runtime-state.json"
            );

            ArquivoMonitoradoRepository arquivoRepository =
                    new ArquivoMonitoradoRepository();

            String caminhoRuntime = runtimeStatePath.toAbsolutePath().toString();

            String hashAtualRuntime =
                    HashUtils.calcularSHA256(runtimeStatePath);

            String hashAnteriorRuntime;
            Long runtimeArquivoId;

// Consulta o arquivo monitorado
            try (Connection connection = databaseConfig.getConnection()) {

                runtimeArquivoId = arquivoRepository.buscarOuCriar(
                        connection,
                        caminhoRuntime,
                        runtimeStatePath.getFileName().toString()
                );

                hashAnteriorRuntime =
                        arquivoRepository.buscarHashPorCaminho(
                                connection,
                                caminhoRuntime
                        );
            }

            boolean runtimeAlterado =
                    !hashAtualRuntime.equals(hashAnteriorRuntime);

            if (!runtimeAlterado) {

                System.out.println("\nruntime-state.json sem alterações.");
                System.out.println("Processamento do tacógrafo ignorado.");

            } else {

                System.out.println("\nruntime-state.json foi alterado.");
                System.out.println("Iniciando processamento do tacógrafo...");

                RuntimeStateReader runtimeReader =
                        new RuntimeStateReader();

                RuntimeState runtimeState =
                        runtimeReader.read(runtimeStatePath);

                RuntimeState.TachographState tachograph =
                        runtimeState.tachograph_state();

                System.out.println("\n========== TACÓGRAFO ==========");

                if (tachograph == null) {

                    System.out.println("Estado do tacógrafo não encontrado.");

                } else {

                    System.out.println("Versão: " + tachograph.version());

                    System.out.println(
                            "Jornadas registradas: " +
                                    (tachograph.history() == null
                                            ? 0
                                            : tachograph.history().size())
                    );

                    System.out.println(
                            tachograph.activeShift() != null
                                    ? "Existe uma jornada ativa: " + tachograph.activeShift().id()
                                    : "Nenhuma jornada ativa."
                    );
                }

                JornadaRepository jornadaRepository = new JornadaRepository();
                JornadaSegmentoRepository segmentoRepository =
                        new JornadaSegmentoRepository();
                JornadaAlertaRepository alertaRepository =
                        new JornadaAlertaRepository();

                try (Connection connection = databaseConfig.getConnection()) {

                    connection.setAutoCommit(false);

                    try {

                        if (tachograph != null) {

                            // Processa jornadas concluídas
                            if (tachograph.history() != null) {

                                for (RuntimeState.Shift shift : tachograph.history()) {

                                    processarJornada(
                                            connection,
                                            shift,
                                            caminhaoRepository,
                                            jornadaRepository,
                                            segmentoRepository,
                                            alertaRepository
                                    );
                                }
                            }

                            // Processa jornada ativa
                            if (tachograph.activeShift() != null) {

                                System.out.println("\nProcessando jornada ativa...");

                                processarJornada(
                                        connection,
                                        tachograph.activeShift(),
                                        caminhaoRepository,
                                        jornadaRepository,
                                        segmentoRepository,
                                        alertaRepository
                                );
                            }
                        }

                        // Atualiza o hash somente depois de processar tudo
                        arquivoRepository.atualizarHash(
                                connection,
                                runtimeArquivoId,
                                hashAtualRuntime
                        );

                        connection.commit();

                        System.out.println(
                                "\nImportação do tacógrafo concluída com sucesso!"
                        );

                    } catch (Exception e) {
                        connection.rollback();
                        throw e;
                    }
                }
            }


        } catch (Exception e) {
            System.err.println(
                    "Erro ao executar coletor: " + e.getMessage()
            );
            e.printStackTrace();
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


    private static void importarHistoricoViagens(
            DatabaseConfig databaseConfig,
            Path arquivo
    ) throws Exception {

        ArquivoMonitoradoRepository arquivoRepository =
                new ArquivoMonitoradoRepository();

        ImportacaoExecucaoRepository execucaoRepository =
                new ImportacaoExecucaoRepository();

        ViagemRepository viagemRepository =
                new ViagemRepository();

        ViagemEventoRepository eventoRepository =
                new ViagemEventoRepository();

        CaminhaoRepository caminhaoRepository =
                new CaminhaoRepository();

        String caminho = arquivo.toAbsolutePath().toString();
        String hashAtual = HashUtils.calcularSHA256(arquivo);

        Long arquivoId;
        String hashAnterior;

// 1. Localiza ou cadastra o arquivo monitorado
        try (Connection connection = databaseConfig.getConnection()) {

            connection.setAutoCommit(false);

            try {
                arquivoId = arquivoRepository.buscarOuCriar(
                        connection,
                        caminho,
                        arquivo.getFileName().toString()
                );

                // Consulta o último hash registrado
                hashAnterior = arquivoRepository.buscarHashPorCaminho(
                        connection,
                        caminho
                );

                connection.commit();

            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }

// 2. Verifica se o conteúdo do arquivo mudou
        if (hashAtual.equals(hashAnterior)) {

            System.out.println("\nArquivo sem alterações: " +
                    arquivo.getFileName());

            System.out.println("Importação ignorada.");

            return;
        }

        System.out.println("\nArquivo alterado ou ainda não importado.");
        System.out.println("Iniciando processamento...");

        // 2. Registra o início da execução
        Long execucaoId;

        try (Connection connection = databaseConfig.getConnection()) {

            connection.setAutoCommit(false);

            try {
                execucaoId = execucaoRepository.iniciar(
                        connection,
                        arquivoId
                );

                connection.commit();

            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }

        int registrosLidos = 0;
        int registrosInseridos = 0;
        int registrosAtualizados = 0;

        try {

            // 3. Lê e processa o arquivo
            TripHistoryReader reader = new TripHistoryReader();
            TripHistory history = reader.read(arquivo);

            registrosLidos = history.items() == null
                    ? 0
                    : history.items().size();

            try (Connection connection = databaseConfig.getConnection()) {

                connection.setAutoCommit(false);

                try {

                    if (history.items() != null) {

                        for (TripHistory.Trip trip : history.items()) {

                            boolean jaExiste =
                                    viagemRepository.existePorIdExterno(
                                            connection,
                                            trip.id()
                                    );

                            Long caminhaoId =
                                    caminhaoRepository.buscarOuCriar(
                                            connection,
                                            trip.truck()
                                    );

                            Long viagemId =
                                    viagemRepository.salvarOuAtualizar(
                                            connection,
                                            trip,
                                            caminhaoId
                                    );

                            eventoRepository.salvarTodos(
                                    connection,
                                    viagemId,
                                    trip.id(),
                                    trip.events()
                            );

                            if (jaExiste) {
                                registrosAtualizados++;
                            } else {
                                registrosInseridos++;
                            }
                        }
                    }

                    connection.commit();

                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                }
            }

            // 4. Finaliza com sucesso e atualiza o hash
            try (Connection connection = databaseConfig.getConnection()) {

                connection.setAutoCommit(false);

                try {

                    execucaoRepository.finalizar(
                            connection,
                            execucaoId,
                            "SUCESSO",
                            registrosLidos,
                            registrosInseridos,
                            registrosAtualizados,
                            null
                    );

                    arquivoRepository.atualizarHash(
                            connection,
                            arquivoId,
                            hashAtual
                    );

                    connection.commit();

                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                }
            }

            System.out.println("\nImportação de viagens concluída!");
            System.out.println("Registros lidos: " + registrosLidos);
            System.out.println("Inseridos: " + registrosInseridos);
            System.out.println("Atualizados: " + registrosAtualizados);

        } catch (Exception e) {

            // 5. Registra a falha em uma nova transação
            try (Connection connection = databaseConfig.getConnection()) {

                connection.setAutoCommit(false);

                try {
                    execucaoRepository.finalizar(
                            connection,
                            execucaoId,
                            "ERRO",
                            registrosLidos,
                            registrosInseridos,
                            registrosAtualizados,
                            e.getMessage()
                    );

                    connection.commit();

                } catch (Exception erroRegistro) {
                    connection.rollback();
                    e.addSuppressed(erroRegistro);
                }
            }

            throw e;
        }
    }

    private static void processarJornada(
            Connection connection,
            RuntimeState.Shift shift,
            CaminhaoRepository caminhaoRepository,
            JornadaRepository jornadaRepository,
            JornadaSegmentoRepository segmentoRepository,
            JornadaAlertaRepository alertaRepository
    ) throws Exception {

        System.out.println("\n----------------------------");
        System.out.println("ID: " + shift.id());
        System.out.println("Simulador: " + shift.simulator());

        if (shift.truck() != null) {
            System.out.println(
                    "Caminhão: " +
                            shift.truck().make() + " " +
                            shift.truck().model()
            );

            System.out.println(
                    "Placa: " + shift.truck().licensePlate()
            );
        }

        System.out.println("Distância: " + shift.distanceKm() + " km");
        System.out.println("Velocidade máxima: " + shift.maxSpeedKph() + " km/h");

        String placa = shift.truck() != null
                ? shift.truck().licensePlate()
                : null;

        Long caminhaoId = caminhaoRepository.buscarIdPorPlaca(
                connection,
                placa
        );

        Long jornadaId = jornadaRepository.salvarOuAtualizar(
                connection,
                shift,
                caminhaoId
        );

        int segmentos = segmentoRepository.salvarTodos(
                connection,
                jornadaId,
                shift
        );

        int alertas = alertaRepository.salvarTodos(
                connection,
                jornadaId,
                shift
        );

        System.out.println("Jornada processada. ID interno: " + jornadaId);
        System.out.println("Segmentos processados: " + segmentos);
        System.out.println("Alertas processados: " + alertas);

        System.out.println(
                "Correções: " +
                        (shift.corrections() == null
                                ? 0
                                : shift.corrections().size())
        );
    }
}
