package br.com.losystem.collector.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ColetaScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ColetaScheduler.class);

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "rbr-coletor");
                thread.setDaemon(false);
                return thread;
            });

    public void iniciar(Runnable tarefa) {

        executor.scheduleWithFixedDelay(
                tarefa,
                0,
                5,
                TimeUnit.SECONDS
        );
    }

    public void parar() {

        logger.info("Solicitando encerramento do agendador...");

        executor.shutdown();

        try {

            boolean finalizado = executor.awaitTermination(
                    30,
                    TimeUnit.SECONDS
            );

            if (finalizado) {
                logger.info("Agendador encerrado corretamente.");
            } else {
                logger.warn("Tempo limite excedido. Solicitando interrupção...");

                executor.shutdownNow();

                boolean interrompido = executor.awaitTermination(
                        10,
                        TimeUnit.SECONDS
                );

                if (!interrompido) {
                    logger.error("O agendador não encerrou completamente.");
                }
            }

        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("Encerramento interrompido durante a espera.", e);
        }
    }

}
