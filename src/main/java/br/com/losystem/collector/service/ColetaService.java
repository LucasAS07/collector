package br.com.losystem.collector.service;

import br.com.losystem.collector.config.DatabaseConfig;
import br.com.losystem.collector.model.RuntimeState;
import br.com.losystem.collector.model.TripHistory;
import br.com.losystem.collector.reader.ArquivoSnapshotReader;
import br.com.losystem.collector.reader.RuntimeStateReader;
import br.com.losystem.collector.reader.TripHistoryReader;
import br.com.losystem.collector.repository.*;
import br.com.losystem.collector.util.ArquivoSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

public class ColetaService {

    private static final Logger logger = LoggerFactory.getLogger(ColetaService.class);
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

        long inicioCiclo = System.nanoTime();
        try {
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

            logger.info("========== INICIANDO CICLO DE COLETA ==========");

            importarHistoricoViagens(
                    databaseConfig,
                    tripHistoryPath
            );

            String caminhoRuntime =
                    runtimeStatePath.toAbsolutePath().toString();

            ArquivoSnapshotReader snapshotReader = new ArquivoSnapshotReader();

            ArquivoSnapshot snapshotRuntime;

            try {
                snapshotRuntime = snapshotReader.ler(runtimeStatePath);
            } catch (Exception e) {
                logger.error("Falha ao ler o arquivo {}: {}", runtimeStatePath.getFileName(), e.getMessage());
                throw e;
            }

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
                logger.info("runtime-state.json sem alterações. Processamento do tacógrafo ignorado.");
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

                logger.info("runtime-state.json alterado. Iniciando processamento do tacógrafo.");

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

                logger.info(
                        "Tacógrafo - versão: {}, jornadas registradas: {}, jornada ativa: {}",
                        tachograph.version(),
                        tachograph.history() == null ? 0 : tachograph.history().size(),
                        tachograph.activeShift() != null
                                ? tachograph.activeShift().id()
                                : "nenhuma"
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

                            logger.info("Processando jornada ativa.");

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

                        logger.info(
                                "Importação do tacógrafo concluída com sucesso. " +
                                        "Jornadas lidas: {}, inseridas: {}, atualizadas: {}.",
                                registrosLidos,
                                registrosInseridos,
                                registrosAtualizados
                        );

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

                logger.error("Falha ao processar runtime-state.json na execução {}: {}", execucaoId, e.getMessage());


                throw e;
            }
        } finally {
            registrarDuracaoCiclo(inicioCiclo);
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

        ArquivoSnapshot snapshot;

        try {
            snapshot = snapshotReader.ler(arquivo);
        } catch (Exception e) {
            logger.error("Falha ao ler o arquivo {}: {}", arquivo.getFileName(), e.getMessage());
            throw e;
        }

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
            logger.info("Arquivo sem alterações: {}. Importação ignorada.", arquivo.getFileName());
            return;
        }

        logger.info("Arquivo {} alterado ou ainda não importado. Iniciando processamento.", arquivo.getFileName());

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

            logger.info(
                    "Importação de viagens concluída com sucesso. " +
                    "Lidos: {}, inseridos: {}, atualizados: {}.",
                    registrosLidos,
                    registrosInseridos,
                    registrosAtualizados
            );

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

            logger.error(
                    "Falha ao importar {} na execução {}: {}",
                    arquivo.getFileName(),
                    execucaoId,
                    e.getMessage()
            );

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

        logger.debug(
                "Processando jornada. ID externo: {}, simulador: {}",
                shift.id(),
                shift.simulator()
        );

        if (shift.truck() != null) {
            logger.debug(
                    "Caminhão: {} {}, placa: {}",
                    shift.truck().make(),
                    shift.truck().model(),
                    shift.truck().licensePlate()
            );
        }

        logger.debug(
                "Distância percorrida: {} km, velocidade máxima: {} km/h",
                shift.distanceKm(),
                shift.maxSpeedKph()
        );

        Long caminhaoId = null;

        if (shift.truck() != null
                && shift.truck().licensePlate() != null
                && !shift.truck().licensePlate().isBlank()) {

            caminhaoId = caminhaoRepository.buscarOuCriar(
                    connection,
                    shift.truck()
            );

        } else {
            logger.warn("Caminhão sem placa identificada. Jornada será salva sem vínculo.");
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

        logger.debug(
                "Jornada processada. ID interno: {}, segmentos: {}, alertas: {}, correções: {}",
                jornadaId,
                segmentos,
                alertas,
                correcoes
        );
    }

    private void registrarDuracaoCiclo(long inicioCiclo) {
        long duracaoMs = (System.nanoTime() - inicioCiclo) /1_000_000;
        logger.info("Ciclo de coleta finalizado em {} ms.", duracaoMs);
    }

}
