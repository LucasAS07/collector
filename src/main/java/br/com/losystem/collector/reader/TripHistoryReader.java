package br.com.losystem.collector.reader;

import br.com.losystem.collector.model.TripHistory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TripHistoryReader {

    private final ObjectMapper objectMapper;

    public TripHistoryReader() {
        this.objectMapper = new ObjectMapper();
    }

    public TripHistory read(Path filePath) throws IOException {

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
            return objectMapper.readValue(reader, TripHistory.class);
        }
    }


    public TripHistory read(byte[] conteudo) throws IOException {
        return objectMapper.readValue(conteudo, TripHistory.class);
    }

}
