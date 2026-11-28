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

    @Test
    void deveValidarHashSha256DoConteudo() throws IOException {
        Path arquivo = diretorioTemporario.resolve("teste.json");
        String conteudo = "{\"version\":1}";

        Files.writeString(arquivo, conteudo);

        ArquivoSnapshot snapshot = reader.ler(arquivo);

        assertEquals(
                "2430f1a2ad2982d0067885488a4c89e21ad1d7c83b115ba8f1b20acc88dfaea8",
                snapshot.hash()
        );
    }

    @Test
    void deveGerarMesmoHashParaConteudosIguais() throws IOException {
        Path arquivo1 = diretorioTemporario.resolve("arquivo1.json");
        Path arquivo2 = diretorioTemporario.resolve("arquivo2.json");

        String conteudo = "{\"version\":1}";

        Files.writeString(arquivo1, conteudo);
        Files.writeString(arquivo2, conteudo);

        ArquivoSnapshot snapshot1 = reader.ler(arquivo1);
        ArquivoSnapshot snapshot2 = reader.ler(arquivo2);

        assertEquals(snapshot1.hash(), snapshot2.hash());
    }

    @Test
    void deveGerarHashesDiferentesParaConteudosDiferentes() throws IOException {
        Path arquivo1 = diretorioTemporario.resolve("arquivo1.json");
        Path arquivo2 = diretorioTemporario.resolve("arquivo2.json");

        Files.writeString(arquivo1, "{\"version\":1}");
        Files.writeString(arquivo2, "{\"version\":2}");

        ArquivoSnapshot snapshot1 = reader.ler(arquivo1);
        ArquivoSnapshot snapshot2 = reader.ler(arquivo2);

        assertNotEquals(snapshot1.hash(), snapshot2.hash());
    }

    @Test
    void deveRepetirLeituraQuandoArquivoForAlterado() throws IOException {
        Path arquivo = diretorioTemporario.resolve("teste.json");
        String conteudo = "{\"version\":1}";

        Files.writeString(arquivo, conteudo);

        boolean[] arquivoAlterado = {false};

        ArquivoSnapshotReader readerComAlteracao = new ArquivoSnapshotReader() {
            @Override
            byte[] lerBytes(Path caminho) throws IOException {
                byte[] bytes = super.lerBytes(caminho);

                if (!arquivoAlterado[0]) {
                    Files.writeString(
                            caminho,
                            " ",
                            java.nio.file.StandardOpenOption.APPEND
                    );

                    arquivoAlterado[0] = true;
                }

                return bytes;
            }
        };

        ArquivoSnapshot snapshot = readerComAlteracao.ler(arquivo);

        assertTrue(arquivoAlterado[0]);
        assertArrayEquals(
                Files.readAllBytes(arquivo),
                snapshot.conteudo()
        );
    }

    @Test
    void deveFalharAposEsgotarTentativasDeLeitura() throws IOException {
        Path arquivo = diretorioTemporario.resolve("teste.json");
        Files.writeString(arquivo, "{\"version\":1}");

        java.util.concurrent.atomic.AtomicInteger tentativas =
                new java.util.concurrent.atomic.AtomicInteger();

        ArquivoSnapshotReader readerInstavel = new ArquivoSnapshotReader() {
            @Override
            byte[] lerBytes(Path caminho) throws IOException {
                byte[] bytes = super.lerBytes(caminho);

                tentativas.incrementAndGet();

                Files.writeString(
                        caminho,
                        " ",
                        java.nio.file.StandardOpenOption.APPEND
                );

                return bytes;
            }
        };

        IOException excecao = assertThrows(
                IOException.class,
                () -> readerInstavel.ler(arquivo)
        );

        assertEquals(3, tentativas.get());

        assertTrue(
                excecao.getMessage().contains(
                        "Não foi possível obter uma leitura estável"
                )
        );
    }

    @Test
    void deveInterromperLeituraQuandoThreadForInterrompida() throws IOException {
        Path arquivo = diretorioTemporario.resolve("teste.json");
        Files.writeString(arquivo, "{\"version\":1}");

        ArquivoSnapshotReader readerInterrompido = new ArquivoSnapshotReader() {
            @Override
            byte[] lerBytes(Path caminho) throws IOException {
                byte[] bytes = super.lerBytes(caminho);

                Files.writeString(
                        caminho,
                        " ",
                        java.nio.file.StandardOpenOption.APPEND
                );

                Thread.currentThread().interrupt();

                return bytes;
            }
        };

        try {
            IOException excecao = assertThrows(IOException.class, () -> readerInterrompido.ler(arquivo));

            assertEquals("Leitura do arquivo interrompida.", excecao.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());

        } finally {
            Thread.interrupted();
        }
    }

}