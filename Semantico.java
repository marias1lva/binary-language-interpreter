import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * Trabalho M2 - Linguagens Formais e Automatos - UNIVALI
 * Prof. Alex Luciano Roesler Rese
 *
 * Analisador semantico. O GALS gera essa classe vazia, entao o interpretador
 * foi feito aqui: uma pilha para calcular as expressoes e uma tabela de
 * simbolos para guardar as variaveis.
 *
 * Os numeros sao lidos em base 2 e o Show tambem imprime em base 2.
 * Usamos BigInteger para nao ter estouro como teria com int ou long.
 */
public class Semantico implements Constants {

    // se true, o Show mostra tambem o valor em decimal (ajuda nos testes)
    public static final boolean mostrar_decimal = false;

    // se true, divisao e Log com resultado nao exato dao erro em vez de truncar
    public static final boolean exigir_resultado_exato = false;

    private static final int limite_bits_resultado = 1_000_000;

    private final Map<String, BigInteger> tabelaSimbolos = new LinkedHashMap<>();
    private final Deque<BigInteger> pilha = new ArrayDeque<>();
    private final StringBuilder saida = new StringBuilder();

    private String destino = null;

    public void executeAction(int action, Token token) throws SemanticError {
        switch (action) {

            // guarda o nome da variavel que vai receber o valor
            case 1:
                destino = token.getLexeme();
                break;

            // fim da atribuicao: o put cria a variavel ou substitui o valor antigo
            case 2: {
                BigInteger valor = desempilhar(token);
                tabelaSimbolos.put(destino, valor);
                destino = null;
                break;
            }

            // Show
            case 3: {
                BigInteger valor = desempilhar(token);
                String texto = valor.toString(2);
                if (mostrar_decimal) {
                    texto = texto + "  (" + valor.toString(10) + " em decimal)";
                }
                saida.append(texto).append(System.lineSeparator());
                System.out.println(texto);
                break;
            }

            // soma. O segundo operando sai primeiro da pilha, por isso o b vem
            // antes do a. O mesmo vale para as outras operacoes
            case 4: {
                BigInteger b = desempilhar(token);
                BigInteger a = desempilhar(token);
                pilha.push(a.add(b));
                break;
            }

            // subtracao. A linguagem e sem sinal, entao negativo da erro
            case 5: {
                BigInteger b = desempilhar(token);
                BigInteger a = desempilhar(token);
                BigInteger r = a.subtract(b);
                if (r.signum() < 0) {
                    erro("resultado negativo (" + a.toString(2) + " - " + b.toString(2)
                            + "): a linguagem trabalha apenas com inteiros sem sinal", token);
                }
                pilha.push(r);
                break;
            }

            // multiplicacao
            case 6: {
                BigInteger b = desempilhar(token);
                BigInteger a = desempilhar(token);
                pilha.push(a.multiply(b));
                break;
            }

            // divisao inteira, porque a linguagem nao tem numero com virgula
            case 7: {
                BigInteger b = desempilhar(token);
                BigInteger a = desempilhar(token);
                if (b.signum() == 0) {
                    erro("divisao por zero", token);
                }
                if (exigir_resultado_exato && a.mod(b).signum() != 0) {
                    erro("divisao nao exata (" + a.toString(2) + " / " + b.toString(2)
                            + "): a linguagem so representa inteiros", token);
                }
                pilha.push(a.divide(b));
                break;
            }

            // exponenciacao. Fica associativa a direita por causa da gramatica:
            // <potencia> ::= <fator> exponenciacao <potencia> #8
            case 8: {
                BigInteger expoente = desempilhar(token);
                BigInteger base = desempilhar(token);
                pilha.push(potencia(base, expoente, token));
                break;
            }

            // numero: converte o texto de base 2 e empilha
            case 9: {
                try {
                    pilha.push(new BigInteger(token.getLexeme(), 2));
                } catch (NumberFormatException e) {
                    erro("numero binario invalido: " + token.getLexeme(), token);
                }
                break;
            }

            // variavel usada numa expressao
            case 10: {
                String nome = token.getLexeme();
                BigInteger valor = tabelaSimbolos.get(nome);
                if (valor == null) {
                    erro("variavel '" + nome + "' usada sem ter recebido valor", token);
                }
                pilha.push(valor);
                break;
            }

            // Log na base 2, que e a base que combina com a linguagem binaria.
            // bitLength() - 1 da o log2 arredondado para baixo, e bitCount() == 1
            // quer dizer que x e potencia de 2, ou seja, que o log e exato
            case 11: {
                BigInteger x = desempilhar(token);
                if (x.signum() <= 0) {
                    erro("Log nao esta definido para zero", token);
                }
                if (exigir_resultado_exato && x.bitCount() != 1) {
                    erro("Log de " + x.toString(2) + " nao eh exato na base 2: "
                            + "a linguagem so representa inteiros", token);
                }
                pilha.push(BigInteger.valueOf(x.bitLength() - 1L));
                break;
            }

            default:
                erro("acao semantica " + action + " nao implementada", token);
        }
    }

    private BigInteger potencia(BigInteger base, BigInteger expoente, Token token) throws SemanticError {
        if (expoente.signum() == 0) {
            return BigInteger.ONE;
        }
        if (expoente.bitLength() > 31) {
            erro("expoente grande demais para ser calculado", token);
        }
        int exp = expoente.intValue();
        long bitsEstimados = (long) base.bitLength() * exp;
        if (bitsEstimados > limite_bits_resultado) {
            erro("resultado da exponenciacao grande demais para ser calculado", token);
        }
        return base.pow(exp);
    }

    private BigInteger desempilhar(Token token) throws SemanticError {
        if (pilha.isEmpty()) {
            erro("expressao mal formada (pilha de valores vazia)", token);
        }
        return pilha.pop();
    }

    private void erro(String mensagem, Token token) throws SemanticError {
        if (token != null) {
            throw new SemanticError(mensagem, token.getPosition());
        }
        throw new SemanticError(mensagem);
    }

    public String getSaida() { return saida.toString(); }

    public Map<String, BigInteger> getTabelaSimbolos() { return tabelaSimbolos; }
}