package br.com.losystem.collector.util;

import java.util.Objects;

public record ArquivoSnapshot(byte[] conteudo, String hash) {

    public ArquivoSnapshot {
        Objects.requireNonNull(conteudo, "O conteúdo do snapshot não pode ser nulo.");
        Objects.requireNonNull(hash, "O hash do snapshot não pode ser nulo.");

        conteudo = conteudo.clone();
    }

    @Override
    public byte[] conteudo() {
        return conteudo.clone();
    }

}
