package br.com.losystem.collector.scheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ColetaScheduler {

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

        System.out.println("Solicitando encerramento do agendador...");

        executor.shutdown();

        try {

            boolean finalizado = executor.awaitTermination(
                    30,
                    TimeUnit.SECONDS
            );

            if (finalizado) {
                System.out.println("Agendador encerrado corretamente.");
            } else {

                System.out.println(
                        "Tempo limite excedido. Solicitando interrupção..."
                );

                executor.shutdownNow();

                boolean interrompido = executor.awaitTermination(
                        10,
                        TimeUnit.SECONDS
                );

                if (!interrompido) {
                    System.err.println(
                            "O agendador não encerrou completamente."
                    );
                }
            }

        } catch (InterruptedException e) {

            executor.shutdownNow();

            Thread.currentThread().interrupt();

            System.err.println(
                    "Encerramento interrompido durante a espera."
            );
        }
    }

}
