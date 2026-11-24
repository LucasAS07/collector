package br.com.losystem.collector.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArquivoSnapshotTest {
    @Test
    void devePreservarConteudoOriginalAposCriacao() {

        byte[] conteudo = {1, 2, 3};
        String hash = "hash-teste";

        ArquivoSnapshot snapshot =
                new ArquivoSnapshot(conteudo, hash);

        conteudo[0] = 99;

        assertArrayEquals(
                new byte[]{1, 2, 3},
                snapshot.conteudo()
        );
    }

    @Test
    void deveImpedirAlteracaoDoConteudoInterno() {

        ArquivoSnapshot snapshot =
                new ArquivoSnapshot(
                        new byte[]{1, 2, 3},
                        "hash-teste"
                );

        byte[] conteudo = snapshot.conteudo();

        conteudo[0] = 99;

        assertArrayEquals(
                new byte[]{1, 2, 3},
                snapshot.conteudo()
        );
    }

    @Test
    void devePreservarHashOriginal() {

        ArquivoSnapshot snapshot =
                new ArquivoSnapshot(
                        new byte[]{1, 2, 3},
                        "hash-original"
                );

        assertEquals("hash-original", snapshot.hash());
    }
}