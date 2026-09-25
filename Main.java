import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.math.BigInteger;

/**
 * Programa principal do interpretador.
 *
 * Uso:   java Main exemplo.txt
 *        java Main               (roda o exemplo do enunciado)
 *
 * Trabalho M2 - Linguagens Formais e Automatos - UNIVALI
 */
public class Main {

    private static final String EXEMPLO_DO_ENUNCIADO =
            "A = 10;\n" +
            "B = 11;\n" +
            "B = 111 + A * B;\n" +
            "Show ( B );\n";

    public static void main(String[] args) {
        String fonte;
        try {
            fonte = (args.length > 0)
                    ? new String(Files.readAllBytes(Path.of(args[0])), StandardCharsets.UTF_8)
                    : EXEMPLO_DO_ENUNCIADO;
        } catch (Exception e) {
            System.err.println("Nao foi possivel ler o arquivo: " + e.getMessage());
            return;
        }

        System.out.println("---------- codigo-fonte ----------");
        System.out.println(fonte.trim());
        System.out.println("---------- saida -----------------");

        Lexico lexico = new Lexico();
        Sintatico sintatico = new Sintatico();
        Semantico semantico = new Semantico();

        lexico.setInput(fonte);

        try {
            sintatico.parse(lexico, semantico);

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

    /** Converte a posicao absoluta devolvida pelo GALS em linha e coluna. */
    private static String local(String fonte, int posicao) {
        if (posicao < 0 || posicao > fonte.length()) return "";
        int linha = 1, coluna = 1;
        for (int i = 0; i < posicao; i++) {
            if (fonte.charAt(i) == '\n') { linha++; coluna = 1; } else { coluna++; }
        }
        return " (linha " + linha + ", coluna " + coluna + ")";
    }
}
