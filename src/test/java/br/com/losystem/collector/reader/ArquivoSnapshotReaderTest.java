package br.com.losystem.collector.reader;

import br.com.losystem.collector.util.ArquivoSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ArquivoSnapshotReaderTest {
    private ArquivoSnapshotReader reader;

    @TempDir
    Path diretorioTemporario;

    @BeforeEach
    void setUp() {
        reader = new ArquivoSnapshotReader();
    }

    @Test
    void deveLerArquivoEstavel() throws IOException {

        Path arquivo = diretorioTemporario.resolve("teste.json");

        String conteudo = "{\"version\":1}";

        Files.writeString(arquivo, conteudo);

        ArquivoSnapshot snapshot = reader.ler(arquivo);

        assertNotNull(snapshot);
        assertArrayEquals(
                conteudo.getBytes(StandardCharsets.UTF_8),
                snapshot.conteudo()
        );
        assertNotNull(snapshot.hash());
    }

    @Test
    void deveGerarHashSha256Com64Caracteres() throws IOException {

        Path arquivo = diretorioTemporario.resolve("teste.json");

        Files.writeString(arquivo, "{\"version\":1}");

        ArquivoSnapshot snapshot = reader.ler(arquivo);

        assertEquals(64, snapshot.hash().length());
        assertTrue(snapshot.hash().matches("[a-f0-9]{64}"));
    }

    @Test
    void deveRejeitarArquivoInexistente() {

        Path arquivo = diretorioTemporario.resolve("inexistente.json");

        assertThrows(
                IOException.class,
                () -> reader.ler(arquivo)
        );
    }

    @Test
    void deveRejeitarDiretorioComoArquivo() {

        assertThrows(
                IOException.class,
                () -> reader.ler(diretorioTemporario)
        );
    }

    @Test
    void deveLerArquivoVazioComoSnapshot() throws IOException {

        Path arquivo = diretorioTemporario.resolve("vazio.json");

        Files.createFile(arquivo);

        ArquivoSnapshot snapshot = reader.ler(arquivo);

        assertNotNull(snapshot);
        assertEquals(0, snapshot.conteudo().length);
        assertEquals(64, snapshot.hash().length());
    }

}