import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.math.BigInteger;

/*
 * Trabalho M2 - Linguagens Formais e Automatos - UNIVALI
 *
 * Classe principal: le o arquivo com o programa e roda o interpretador.
 * Uso: java Main exemplo.txt
 * Se nao passar nenhum arquivo, roda o exemplo do enunciado.
 */
public class Main {

    // exemplo do enunciado (usado quando nao passa arquivo)
    private static final String exemplo_enunciado =
            "A = 10;\n" +
            "B = 11;\n" +
            "B = 111 + A * B;\n" +
            "Show ( B );\n";

    public static void main(String[] args) {
        // le o arquivo passado como argumento
        String fonte;
        try {
            fonte = (args.length > 0)
                    ? new String(Files.readAllBytes(Path.of(args[0])), StandardCharsets.UTF_8)
                    : exemplo_enunciado;
        } catch (Exception e) {
            System.err.println("Nao foi possivel ler o arquivo: " + e.getMessage());
            return;
        }

        // alguns editores colocam um BOM no comeco do arquivo, entao tiramos ele
        if (!fonte.isEmpty() && fonte.charAt(0) == '\uFEFF') {
            fonte = fonte.substring(1);
        }

        // mostra o codigo que foi lido
        System.out.println("---------- codigo-fonte ----------");
        System.out.println(fonte.trim());
        System.out.println("---------- saida -----------------");

        // lexico e sintatico foram gerados pelo GALS, o semantico foi feito por nos
        Lexico lexico = new Lexico();
        Sintatico sintatico = new Sintatico();
        Semantico semantico = new Semantico();

        lexico.setInput(fonte);

        try {
            // a analise e a execucao do programa acontecem juntas aqui
            sintatico.parse(lexico, semantico);

            // se nao deu erro, mostra o valor final de cada variavel
            System.out.println("---------- tabela de simbolos ----");
            for (Map.Entry<String, BigInteger> e : semantico.getTabelaSimbolos().entrySet()) {
                System.out.println("  " + e.getKey() + " = " + e.getValue().toString(2));
            }
            System.out.println("Programa interpretado com sucesso.");

        } catch (LexicalError e) {
            System.out.println("Erro lexico: " + e.getMessage() + local(fonte, e.getPosition()));
        } catch (SyntacticError e) {
            System.out.println("Erro sintatico: " + e.getMessage() + local(fonte, e.getPosition()));
        } catch (SemanticError e) {
            System.out.println("Erro semantico: " + e.getMessage() + local(fonte, e.getPosition()));
        }
    }

    // o GALS devolve a posicao do erro como indice no texto,
    // aqui transformamos em linha e coluna para ficar mais facil de achar
    private static String local(String fonte, int posicao) {
        if (posicao < 0 || posicao > fonte.length()) return "";
        int linha = 1, coluna = 1;
        for (int i = 0; i < posicao; i++) {
            if (fonte.charAt(i) == '\n') { linha++; coluna = 1; } else { coluna++; }
        }
        return " (linha " + linha + ", coluna " + coluna + ")";
    }
}
