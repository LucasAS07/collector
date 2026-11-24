
package br.com.losystem.collector.reader;

import br.com.losystem.collector.util.ArquivoSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public class ArquivoSnapshotReader {

    private static final int MAX_TENTATIVAS = 3;
    private static final long INTERVALO_TENTATIVAS_MS = 200;

    public ArquivoSnapshot ler(Path arquivo) throws IOException {

        if (!Files.exists(arquivo)) {
            throw new IOException(
                    "Arquivo não encontrado: " + arquivo
            );
        }

        if (!Files.isRegularFile(arquivo)) {
            throw new IOException(
                    "O caminho informado não é um arquivo: " + arquivo
            );
        }

        IOException ultimaFalha = null;

        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {

            try {
                BasicFileAttributes antes = lerAtributos(arquivo);

                byte[] conteudo = lerBytes(arquivo);

                BasicFileAttributes depois = lerAtributos(arquivo);

                boolean arquivoEstavel =
                        antes.size() == depois.size()
                                && antes.lastModifiedTime().equals(
                                depois.lastModifiedTime()
                        )
                                && Objects.equals(
                                antes.fileKey(),
                                depois.fileKey()
                        );

                if (arquivoEstavel && conteudo.length == depois.size()) {

                    return new ArquivoSnapshot(
                            conteudo,
                            calcularHash(conteudo)
                    );
                }

                ultimaFalha = new IOException(
                        "O arquivo foi alterado durante a leitura. Tentativa "
                                + tentativa + " de " + MAX_TENTATIVAS + "."
                );

            } catch (IOException e) {

                ultimaFalha = e;
            }

            if (tentativa < MAX_TENTATIVAS) {
                aguardarProximaTentativa();
            }
        }

        throw new IOException(
                "Não foi possível obter uma leitura estável do arquivo após "
                        + MAX_TENTATIVAS + " tentativas: " + arquivo,
                ultimaFalha
        );
    }

    private void aguardarProximaTentativa() throws IOException {

        try {
            Thread.sleep(INTERVALO_TENTATIVAS_MS);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IOException(
                    "Leitura do arquivo interrompida.",
                    e
            );
        }
    }

    private String calcularHash(byte[] conteudo) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(conteudo);

            return HexFormat.of().formatHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "Não foi possível calcular o SHA-256.", e
            );
        }
    }

    BasicFileAttributes lerAtributos(Path arquivo) throws IOException {
        return Files.readAttributes(
                arquivo,
                BasicFileAttributes.class
        );
    }

    byte[] lerBytes(Path arquivo) throws IOException {
        return Files.readAllBytes(arquivo);
    }
}
