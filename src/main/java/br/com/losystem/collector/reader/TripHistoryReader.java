
package br.com.losystem.collector.reader;

import br.com.losystem.collector.model.TripHistory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class TripHistoryReader {

    private final ObjectMapper objectMapper;

    public TripHistoryReader() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                true
        );
    }

    public TripHistory read(byte[] conteudo) throws IOException {

        if (conteudo == null || conteudo.length == 0) {
            throw new IOException(
                    "O conteúdo do trip-history.json está vazio ou nulo."
            );
        }

        TripHistory tripHistory =
                objectMapper.readValue(conteudo, TripHistory.class);

        if (tripHistory == null) {
            throw new IOException(
                    "O JSON do trip-history.json não produziu um objeto válido."
            );
        }

        return tripHistory;
    }
}
