
package br.com.losystem.collector.reader;

import br.com.losystem.collector.model.RuntimeState;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class RuntimeStateReader {

    private final ObjectMapper objectMapper;

    public RuntimeStateReader() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                true
        );
    }

    public RuntimeState read(byte[] conteudo) throws IOException {

        if (conteudo == null || conteudo.length == 0) {
            throw new IOException(
                    "O conteúdo do runtime-state.json está vazio ou nulo."
            );
        }

        RuntimeState runtimeState =
                objectMapper.readValue(conteudo, RuntimeState.class);

        if (runtimeState == null) {
            throw new IOException(
                    "O JSON do runtime-state.json não produziu um objeto válido."
            );
        }

        return runtimeState;
    }
}
