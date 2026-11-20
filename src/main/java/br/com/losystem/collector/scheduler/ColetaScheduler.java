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
        executor.shutdown();
    }

}
