
package br.com.losystem.collector.reader;

import br.com.losystem.collector.util.ArquivoSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class ArquivoSnapshotReaderConcorrenciaTest {

    @TempDir
    Path diretorioTemporario;

    @Test
    void deveTentarNovamenteQuandoArquivoForAlterado()
            throws IOException {

        Path arquivo = criarArquivo();

        FileTime instante1 = FileTime.fromMillis(1000);
        FileTime instante2 = FileTime.fromMillis(2000);
        FileTime instante3 = FileTime.fromMillis(3000);

        LeitorSimulado leitor = new LeitorSimulado();

        // Primeira tentativa: arquivo alterado.
        leitor.adicionarAtributos(atributos(3, instante1));
        leitor.adicionarAtributos(atributos(4, instante2));

        // Segunda tentativa: arquivo estável.
        leitor.adicionarAtributos(atributos(3, instante3));
        leitor.adicionarAtributos(atributos(3, instante3));

        ArquivoSnapshot snapshot = leitor.ler(arquivo);

        assertNotNull(snapshot);
        assertArrayEquals(
                new byte[]{1, 2, 3},
                snapshot.conteudo()
        );

        assertEquals(4, leitor.getQuantidadeLeiturasAtributos());
        assertEquals(2, leitor.getQuantidadeLeiturasBytes());
    }

    @Test
    void deveFalharAposTresTentativasInstaveis()
            throws IOException {

        Path arquivo = criarArquivo();

        LeitorSimulado leitor = new LeitorSimulado();

        // Cada tentativa terá atributos diferentes.
        for (int i = 0; i < 3; i++) {

            leitor.adicionarAtributos(
                    atributos(
                            3,
                            FileTime.fromMillis(1000 + i * 2)
                    )
            );

            leitor.adicionarAtributos(
                    atributos(
                            3,
                            FileTime.fromMillis(1001 + i * 2)
                    )
            );
        }

        IOException erro = assertThrows(
                IOException.class,
                () -> leitor.ler(arquivo)
        );

        assertTrue(
                erro.getMessage().contains("3 tentativas")
        );

        assertEquals(6, leitor.getQuantidadeLeiturasAtributos());
        assertEquals(3, leitor.getQuantidadeLeiturasBytes());
    }

    @Test
    void deveTentarNovamenteAposFalhaTemporariaDeLeitura()
            throws IOException {

        Path arquivo = criarArquivo();

        FileTime instante = FileTime.fromMillis(1000);

        LeitorSimulado leitor = new LeitorSimulado();

        // Primeira tentativa: falha na leitura dos bytes.
        leitor.adicionarAtributos(atributos(3, instante));

        // Segunda tentativa: leitura normal.
        leitor.adicionarAtributos(atributos(3, instante));
        leitor.adicionarAtributos(atributos(3, instante));

        leitor.falharPrimeiraLeitura();

        ArquivoSnapshot snapshot = leitor.ler(arquivo);

        assertNotNull(snapshot);
        assertArrayEquals(
                new byte[]{1, 2, 3},
                snapshot.conteudo()
        );

        assertEquals(3, leitor.getQuantidadeLeiturasAtributos());
        assertEquals(2, leitor.getQuantidadeLeiturasBytes());
    }

    private Path criarArquivo() throws IOException {
        Path arquivo = diretorioTemporario.resolve("teste.json");
        Files.write(arquivo, new byte[]{1, 2, 3});
        return arquivo;
    }

    private BasicFileAttributes atributos(
            long tamanho,
            FileTime modificacao
    ) {
        return new AtributosFake(tamanho, modificacao, "arquivo-teste");
    }

    private BasicFileAttributes atributos(
            long tamanho,
            FileTime modificacao,
            Object fileKey
    ) {
        return new AtributosFake(tamanho, modificacao, fileKey);
    }

    private record AtributosFake(
            long size,
            FileTime lastModifiedTime,
            Object fileKey
    ) implements BasicFileAttributes {

        @Override
        public FileTime lastAccessTime() {
            return lastModifiedTime;
        }

        @Override
        public FileTime creationTime() {
            return lastModifiedTime;
        }

        @Override
        public boolean isRegularFile() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public boolean isSymbolicLink() {
            return false;
        }

        @Override
        public boolean isOther() {
            return false;
        }

        @Override
        public Object fileKey() {
            return fileKey;
        }
    }

    private static class LeitorSimulado
            extends ArquivoSnapshotReader {

        private boolean bytesInconsistentesNaPrimeiraLeitura;
        private final Queue<BasicFileAttributes> atributos =
                new ArrayDeque<>();

        private int quantidadeLeiturasAtributos;
        private int quantidadeLeiturasBytes;
        private boolean falharPrimeiraLeitura;

        void adicionarAtributos(BasicFileAttributes atributo) {
            atributos.add(atributo);
        }

        void falharPrimeiraLeitura() {
            falharPrimeiraLeitura = true;
        }

        int getQuantidadeLeiturasAtributos() {
            return quantidadeLeiturasAtributos;
        }

        int getQuantidadeLeiturasBytes() {
            return quantidadeLeiturasBytes;
        }

        @Override
        BasicFileAttributes lerAtributos(Path arquivo)
                throws IOException {

            quantidadeLeiturasAtributos++;

            BasicFileAttributes resultado = atributos.poll();

            if (resultado == null) {
                throw new IOException(
                        "Não existem atributos simulados disponíveis."
                );
            }

            return resultado;
        }

        @Override
        byte[] lerBytes(Path arquivo) throws IOException {

            quantidadeLeiturasBytes++;

            if (falharPrimeiraLeitura
                    && quantidadeLeiturasBytes == 1) {

                throw new IOException(
                        "Falha temporária simulada na leitura."
                );
            }

            if (bytesInconsistentesNaPrimeiraLeitura
                    && quantidadeLeiturasBytes == 1) {

                return new byte[]{1, 2};
            }

            return new byte[]{1, 2, 3};
        }

        void retornarBytesInconsistentesNaPrimeiraLeitura() {
            bytesInconsistentesNaPrimeiraLeitura = true;
        }
    }

    @Test
    void deveTentarNovamenteQuandoFileKeyForAlterado() throws IOException {
        Path arquivo = criarArquivo();

        FileTime instante = FileTime.fromMillis(1000);

        LeitorSimulado leitor = new LeitorSimulado();

        leitor.adicionarAtributos(
                atributos(3, instante, "arquivo-original")
        );

        leitor.adicionarAtributos(
                atributos(3, instante, "arquivo-substituido")
        );

        leitor.adicionarAtributos(
                atributos(3, instante, "arquivo-substituido")
        );

        leitor.adicionarAtributos(
                atributos(3, instante, "arquivo-substituido")
        );

        ArquivoSnapshot snapshot = leitor.ler(arquivo);

        assertNotNull(snapshot);
        assertArrayEquals(
                new byte[]{1, 2, 3},
                snapshot.conteudo()
        );

        assertEquals(4, leitor.getQuantidadeLeiturasAtributos());
        assertEquals(2, leitor.getQuantidadeLeiturasBytes());
    }

    @Test
    void deveTentarNovamenteQuandoQuantidadeDeBytesForInconsistente()
            throws IOException {

        Path arquivo = criarArquivo();

        FileTime instante = FileTime.fromMillis(1000);

        LeitorSimulado leitor = new LeitorSimulado();

        // Primeira tentativa: atributos indicam 3 bytes,
        // mas a leitura retorna apenas 2.
        leitor.adicionarAtributos(atributos(3, instante));
        leitor.adicionarAtributos(atributos(3, instante));

        // Segunda tentativa: leitura consistente.
        leitor.adicionarAtributos(atributos(3, instante));
        leitor.adicionarAtributos(atributos(3, instante));

        leitor.retornarBytesInconsistentesNaPrimeiraLeitura();

        ArquivoSnapshot snapshot = leitor.ler(arquivo);

        assertNotNull(snapshot);
        assertArrayEquals(
                new byte[]{1, 2, 3},
                snapshot.conteudo()
        );

        assertEquals(4, leitor.getQuantidadeLeiturasAtributos());
        assertEquals(2, leitor.getQuantidadeLeiturasBytes());
    }
}
