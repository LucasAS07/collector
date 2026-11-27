package br.com.losystem.collector.service;

import br.com.losystem.collector.config.DatabaseConfig;
import br.com.losystem.collector.model.RuntimeState;
import br.com.losystem.collector.model.TripHistory;
import br.com.losystem.collector.reader.ArquivoSnapshotReader;
import br.com.losystem.collector.reader.RuntimeStateReader;
import br.com.losystem.collector.reader.TripHistoryReader;
import br.com.losystem.collector.repository.*;
import br.com.losystem.collector.util.ArquivoSnapshot;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

public class ColetaService {

    private final DatabaseConfig databaseConfig;
    private final Path tripHistoryPath;
    private final Path runtimeStatePath;

    public ColetaService(
            DatabaseConfig databaseConfig,
            Path tripHistoryPath,
            Path runtimeStatePath
    ) {
        this.databaseConfig = databaseConfig;
        this.tripHistoryPath = tripHistoryPath;
        this.runtimeStatePath = runtimeStatePath;
    }


    public void executarCicloColeta() throws Exception {

        ImportacaoExecucaoRepository execucaoRepository =
                new ImportacaoExecucaoRepository();

        ArquivoMonitoradoRepository arquivoRepository =
                new ArquivoMonitoradoRepository();

        CaminhaoRepository caminhaoRepository =
                new CaminhaoRepository();

        JornadaRepository jornadaRepository =
                new JornadaRepository();

        JornadaSegmentoRepository segmentoRepository =
                new JornadaSegmentoRepository();

        JornadaAlertaRepository alertaRepository =
                new JornadaAlertaRepository();

        JornadaCorrecaoRepository jornadaCorrecaoRepository =
                new JornadaCorrecaoRepository();

        System.out.println("\n================================");
        System.out.println("INICIANDO CICLO DE COLETA");
        System.out.println("================================");

        importarHistoricoViagens(
                databaseConfig,
                tripHistoryPath
        );

        String caminhoRuntime =
                runtimeStatePath.toAbsolutePath().toString();

        ArquivoSnapshotReader snapshotReader = new ArquivoSnapshotReader();

        ArquivoSnapshot snapshotRuntime =
                snapshotReader.ler(runtimeStatePath);

        String hashAtualRuntime = snapshotRuntime.hash();

        String hashAnteriorRuntime;
        Long runtimeArquivoId;

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

        if (hashAtualRuntime.equals(hashAnteriorRuntime)) {

            System.out.println("\nruntime-state.json sem alterações.");
            System.out.println("Processamento do tacógrafo ignorado.");

            return;
        }

        Long execucaoId;

        try (Connection connection = databaseConfig.getConnection()) {

            connection.setAutoCommit(false);

            try {

                execucaoId = execucaoRepository.iniciar(
                        connection,
                        runtimeArquivoId
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

            System.out.println("\nruntime-state.json foi alterado.");
            System.out.println("Iniciando processamento do tacógrafo...");

            RuntimeStateReader runtimeReader =
                    new RuntimeStateReader();

            RuntimeState runtimeState =
                    runtimeReader.read(snapshotRuntime.conteudo());

            RuntimeState.TachographState tachograph =
                    runtimeState.tachograph_state();

            if (tachograph == null) {
                throw new IllegalStateException(
                        "Estado do tacógrafo não encontrado no runtime-state.json."
                );
            }

            System.out.println("\n========== TACÓGRAFO ==========");
            System.out.println("Versão: " + tachograph.version());

            System.out.println(
                    "Jornadas registradas: " +
                            (tachograph.history() == null
                                    ? 0
                                    : tachograph.history().size())
            );

            System.out.println(
                    tachograph.activeShift() != null
                            ? "Existe uma jornada ativa: " +
                            tachograph.activeShift().id()
                            : "Nenhuma jornada ativa."
            );

            try (Connection connection = databaseConfig.getConnection()) {

                connection.setAutoCommit(false);

                try {

                    if (tachograph.history() != null) {

                        for (RuntimeState.Shift shift : tachograph.history()) {

                            boolean jaExiste =
                                    jornadaRepository.existePorIdExterno(
                                            connection,
                                            shift.id()
                                    );

                            processarJornada(
                                    connection,
                                    shift,
                                    caminhaoRepository,
                                    jornadaRepository,
                                    segmentoRepository,
                                    alertaRepository,
                                    jornadaCorrecaoRepository
                            );

                            registrosLidos++;

                            if (jaExiste) {
                                registrosAtualizados++;
                            } else {
                                registrosInseridos++;
                            }
                        }
                    }

                    if (tachograph.activeShift() != null) {

                        System.out.println("\nProcessando jornada ativa...");

                        RuntimeState.Shift shift =
                                tachograph.activeShift();

                        boolean jaExiste =
                                jornadaRepository.existePorIdExterno(
                                        connection,
                                        shift.id()
                                );

                        processarJornada(
                                connection,
                                shift,
                                caminhaoRepository,
                                jornadaRepository,
                                segmentoRepository,
                                alertaRepository,
                                jornadaCorrecaoRepository
                        );

                        registrosLidos++;

                        if (jaExiste) {
                            registrosAtualizados++;
                        } else {
                            registrosInseridos++;
                        }
                    }

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
                            runtimeArquivoId,
                            hashAtualRuntime
                    );

                    connection.commit();

                    System.out.println(
                            "\nImportação do tacógrafo concluída com sucesso!"
                    );

                    System.out.println("Jornadas lidas: " + registrosLidos);
                    System.out.println("Inseridas: " + registrosInseridos);
                    System.out.println("Atualizadas: " + registrosAtualizados);

                } catch (Exception e) {

                    try {
                        connection.rollback();
                    } catch (SQLException erroRollback) {
                        e.addSuppressed(erroRollback);
                    }

                    throw e;
                }
            }

        } catch (Exception e) {

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

            } catch (Exception erroConexao) {

                e.addSuppressed(erroConexao);
            }

            throw e;
        }
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

        ArquivoSnapshotReader snapshotReader =
                new ArquivoSnapshotReader();

        ArquivoSnapshot snapshot = snapshotReader.ler(arquivo);

        String hashAtual = snapshot.hash();

        Long arquivoId;
        String hashAnterior;

        try (Connection connection = databaseConfig.getConnection()) {

            connection.setAutoCommit(false);

            try {
                arquivoId = arquivoRepository.buscarOuCriar(
                        connection,
                        caminho,
                        arquivo.getFileName().toString()
                );

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

        if (hashAtual.equals(hashAnterior)) {

            System.out.println("\nArquivo sem alterações: " +
                    arquivo.getFileName());

            System.out.println("Importação ignorada.");

            return;
        }

        System.out.println("\nArquivo alterado ou ainda não importado.");
        System.out.println("Iniciando processamento...");

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

            TripHistoryReader reader = new TripHistoryReader();

            TripHistory history = reader.read(snapshot.conteudo());

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

                    try {
                        connection.rollback();
                    } catch (SQLException erroRollback) {
                        e.addSuppressed(erroRollback);
                    }

                    throw e;
                }
            }

            System.out.println("\nImportação de viagens concluída!");
            System.out.println("Registros lidos: " + registrosLidos);
            System.out.println("Inseridos: " + registrosInseridos);
            System.out.println("Atualizados: " + registrosAtualizados);

        } catch (Exception e) {

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

            } catch (Exception erroConexao) {

                e.addSuppressed(erroConexao);
            }

            throw e;
        }
    }


    private void processarJornada(
            Connection connection,
            RuntimeState.Shift shift,
            CaminhaoRepository caminhaoRepository,
            JornadaRepository jornadaRepository,
            JornadaSegmentoRepository segmentoRepository,
            JornadaAlertaRepository jornadaAlertaRepository,
            JornadaCorrecaoRepository jornadaCorrecaoRepository
    ) throws SQLException {

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

        Long caminhaoId = null;

        if (shift.truck() != null
                && shift.truck().licensePlate() != null
                && !shift.truck().licensePlate().isBlank()) {

            caminhaoId = caminhaoRepository.buscarOuCriar(
                    connection,
                    shift.truck()
            );

        } else {
            System.out.println(
                    "Caminhão sem placa identificada. Jornada será salva sem vínculo."
            );
        }

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

        int alertas = jornadaAlertaRepository.salvarTodos(
                connection,
                jornadaId,
                shift
        );

        int correcoes = jornadaCorrecaoRepository.salvarTodos(
                connection,
                jornadaId,
                shift
        );

        System.out.println("Jornada processada. ID interno: " + jornadaId);
        System.out.println("Segmentos processados: " + segmentos);
        System.out.println("Alertas processados: " + alertas);
        System.out.println("Correções processadas: " + correcoes);

        System.out.println(
                "Correções: " +
                        (shift.corrections() == null
                                ? 0
                                : shift.corrections().size())
        );
    }

}
