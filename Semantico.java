import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Analisador Semantico / Interpretador da linguagem binaria.
 *
 * Trabalho M2 - Linguagens Formais e Automatos - UNIVALI
 * Prof. Alex Luciano Roesler Rese
 *
 * O GALS gera esta classe VAZIA. Toda a interpretacao acontece aqui.
 *
 * ---------------------------------------------------------------------------
 * PONTO CENTRAL DA LINGUAGEM: tudo eh binario, do inicio ao fim.
 *
 *   - o literal "10" no codigo-fonte vale 2 (dois), e nao dez;
 *   - o lexico so aceita os digitos 0 e 1 (ver especificacao .gals);
 *   - o Show imprime o valor DE VOLTA EM BINARIO.
 *
 * Por isso a leitura eh feita com new BigInteger(lexema, 2) e a escrita com
 * valor.toString(2). Se qualquer um dos dois virar base 10, a linguagem deixa
 * de ser a linguagem pedida no enunciado.
 * ---------------------------------------------------------------------------
 *
 * Usa BigInteger: o enunciado fala em "inteiros sem sinal", sem limite de bits.
 * BigInteger modela isso exatamente, sem estouro de 32/64 bits.
 *
 * Mecanismo: pilha de valores + tabela de simbolos.
 */
public class Semantico implements Constants {

    /** Se true, o Show mostra tambem o valor em decimal entre parenteses (util para demonstrar). */
    public static final boolean MOSTRAR_DECIMAL = false;

    /**
     * Fechamento do conjunto de valores.
     *
     * O conjunto da linguagem sao os inteiros binarios SEM SINAL. Tres operacoes
     * podem produzir um resultado fora desse conjunto:
     *
     *   subtracao -> numero negativo      (SEMPRE erro, nao ha escolha: o valor
     *                                      nao eh representavel na linguagem)
     *   divisao   -> fracao               (escolha abaixo)
     *   Log       -> numero irracional    (escolha abaixo)
     *
     * false = divisao e Log truncam para a parte inteira (convencao usual de
     *         aritmetica inteira: 111 / 10 = 11)
     * true  = divisao e Log so aceitam resultado exato, senao erro semantico
     */
    public static final boolean EXIGIR_RESULTADO_EXATO = false;

    /** Limite de seguranca para a exponenciacao (evita travar a maquina com 10^1111111111). */
    private static final int LIMITE_BITS_RESULTADO = 1_000_000;

    private final Map<String, BigInteger> tabelaSimbolos = new LinkedHashMap<>();
    private final Deque<BigInteger> pilha = new ArrayDeque<>();
    private final StringBuilder saida = new StringBuilder();

    /** Nome da variavel do lado esquerdo da atribuicao em andamento. */
    private String destino = null;

    // =======================================================================
    // Metodo chamado pelo analisador sintatico gerado pelo GALS
    // =======================================================================
    public void executeAction(int action, Token token) throws SemanticError {
        switch (action) {

            // ---- #1 : <COMANDO> ::= id #1 "=" ...
            // Guarda o nome da variavel que vai receber o resultado.
            case 1:
                destino = token.getLexeme();
                break;

            // ---- #2 : ... <E> #2 ";"
            // Fim da atribuicao: tira o valor da expressao da pilha e grava.
            case 2: {
                BigInteger valor = desempilhar(token);
                tabelaSimbolos.put(destino, valor);
                destino = null;
                break;
            }

            // ---- #3 : Show "(" <E> ")" #3 ";"
            // AQUI ESTA O DETALHE: imprime em BINARIO, nao em decimal.
            case 3: {
                BigInteger valor = desempilhar(token);
                String texto = valor.toString(2);
                if (MOSTRAR_DECIMAL) {
                    texto = texto + "  (" + valor.toString(10) + " em decimal)";
                }
                saida.append(texto).append(System.lineSeparator());
                System.out.println(texto);
                break;
            }

            // ---- #4 : soma
            case 4: {
                BigInteger b = desempilhar(token);
                BigInteger a = desempilhar(token);
                pilha.push(a.add(b));
                break;
            }

            // ---- #5 : subtracao
            // A linguagem eh SEM SINAL: resultado negativo nao eh representavel.
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

            // ---- #6 : multiplicacao
            case 6: {
                BigInteger b = desempilhar(token);
                BigInteger a = desempilhar(token);
                pilha.push(a.multiply(b));
                break;
            }

            // ---- #7 : divisao (inteira, pois nao existe fracao na linguagem)
            case 7: {
                BigInteger b = desempilhar(token);
                BigInteger a = desempilhar(token);
                if (b.signum() == 0) {
                    erro("divisao por zero", token);
                }
                if (EXIGIR_RESULTADO_EXATO && a.mod(b).signum() != 0) {
                    erro("divisao nao exata (" + a.toString(2) + " / " + b.toString(2)
                            + "): a linguagem so representa inteiros", token);
                }
                pilha.push(a.divide(b));
                break;
            }

            // ---- #8 : exponenciacao
            // Acao colocada DEPOIS da chamada recursiva -> associativa a DIREITA.
            case 8: {
                BigInteger expoente = desempilhar(token);
                BigInteger base = desempilhar(token);
                pilha.push(potencia(base, expoente, token));
                break;
            }

            // ---- #9 : literal numerico
            // BASE 2. Trocar para base 10 aqui quebra toda a linguagem.
            case 9: {
                try {
                    pilha.push(new BigInteger(token.getLexeme(), 2));
                } catch (NumberFormatException e) {
                    erro("numero binario invalido: " + token.getLexeme(), token);
                }
                break;
            }

            // ---- #10 : uso de variavel em expressao
            case 10: {
                String nome = token.getLexeme();
                BigInteger valor = tabelaSimbolos.get(nome);
                if (valor == null) {
                    erro("variavel '" + nome + "' usada sem ter recebido valor", token);
                }
                pilha.push(valor);
                break;
            }

            // ---- #11 : Log
            // Base 2: eh a base natural de uma linguagem binaria e o resultado
            // continua sendo um inteiro sem sinal (posicao do bit mais significativo).
            case 11: {
                BigInteger x = desempilhar(token);
                if (x.signum() <= 0) {
                    erro("Log nao esta definido para zero", token);
                }
                // x.bitCount() == 1 significa que x eh potencia de 2, ou seja,
                // que o log na base 2 eh exato.
                if (EXIGIR_RESULTADO_EXATO && x.bitCount() != 1) {
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

    // =======================================================================
    // Apoio
    // =======================================================================

    private BigInteger potencia(BigInteger base, BigInteger expoente, Token token) throws SemanticError {
        if (expoente.signum() == 0) {
            return BigInteger.ONE;            // x^0 = 1
        }
        if (expoente.bitLength() > 31) {
            erro("expoente grande demais para ser calculado", token);
        }
        int exp = expoente.intValue();
        long bitsEstimados = (long) base.bitLength() * exp;
        if (bitsEstimados > LIMITE_BITS_RESULTADO) {
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

    // =======================================================================
    // Consulta pelo programa principal
    // =======================================================================

    public String getSaida() { return saida.toString(); }

    public Map<String, BigInteger> getTabelaSimbolos() { return tabelaSimbolos; }
}
