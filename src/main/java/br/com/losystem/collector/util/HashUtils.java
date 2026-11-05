package br.com.losystem.collector.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class HashUtils {

    public static String calcularSHA256(Path caminho)
            throws IOException {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (var input = Files.newInputStream(caminho)) {

                byte[] buffer = new byte[8192];
                int bytesLidos;

                while ((bytesLidos = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesLidos);
                }
            }

            return HexFormat.of().formatHex(digest.digest());

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "Algoritmo SHA-256 não disponível.",
                    e
            );
        }
    }

}
