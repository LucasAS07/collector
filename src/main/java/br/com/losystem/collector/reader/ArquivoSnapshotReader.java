package br.com.losystem.collector.reader;

import br.com.losystem.collector.util.ArquivoSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class ArquivoSnapshotReader {

    private static final int MAX_TENTATIVAS = 3;

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

        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {

            BasicFileAttributes antes =
                    Files.readAttributes(
                            arquivo,
                            BasicFileAttributes.class
                    );

            byte[] conteudo = Files.readAllBytes(arquivo);

            BasicFileAttributes depois =
                    Files.readAttributes(
                            arquivo,
                            BasicFileAttributes.class
                    );

            boolean arquivoEstavel =
                    antes.size() == depois.size()
                            && antes.lastModifiedTime().equals(
                            depois.lastModifiedTime()
                    )
                            && java.util.Objects.equals(
                            antes.fileKey(),
                            depois.fileKey()
                    );

            if (arquivoEstavel && conteudo.length == depois.size()) {

                return new ArquivoSnapshot(
                        conteudo,
                        calcularHash(conteudo)
                );
            }

        }

        throw new IOException(
                "Não foi possível obter uma leitura estável do arquivo após "
                        + MAX_TENTATIVAS + " tentativas: " + arquivo
        );
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
}
