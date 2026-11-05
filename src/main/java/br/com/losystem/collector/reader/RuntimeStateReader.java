package br.com.losystem.collector.reader;

import br.com.losystem.collector.model.RuntimeState;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RuntimeStateReader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RuntimeState read(Path filePath) throws IOException {

        if (!Files.exists(filePath)) {
            throw new IOException(
                    "Arquivo não encontrado: " + filePath
            );
        }

        if (!Files.isRegularFile(filePath)) {
            throw new IOException(
                    "O caminho informado não é um arquivo: " + filePath
            );
        }

        try (var reader = Files.newBufferedReader(filePath)) {
            return objectMapper.readValue(
                    reader,
                    RuntimeState.class
            );
        }
    }

}
