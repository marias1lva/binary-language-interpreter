import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * Trabalho M2 - Linguagens Formais e Automatos - UNIVALI
 * Prof. Alex Luciano Roesler Rese
 *
 * Analisador semantico. O GALS gera essa classe vazia, entao foi aqui
 * que fizemos o interpretador.
 *
 * Todos os numeros da linguagem sao binarios: os literais sao lidos em
 * base 2 e o Show tambem mostra o resultado em base 2.
 *
 * Usamos BigInteger para nao ter problema de estouro como teria com int ou long.
 *
 * O interpretador usa uma pilha de valores para calcular as expressoes e
 * uma tabela de simbolos para guardar as variaveis.
 */
public class Semantico implements Constants {

    // se mudar para true, o Show mostra tambem o valor em decimal (ajuda nos testes)
    public static final boolean MOSTRAR_DECIMAL = false;

    // false: divisao e Log descartam a parte fracionaria (ex: 111 / 10 = 11)
    // true: divisao e Log com resultado nao exato dao erro semantico
    public static final boolean EXIGIR_RESULTADO_EXATO = false;

    // limite da exponenciacao, para o programa nao travar com uma conta muito grande
    private static final int LIMITE_BITS_RESULTADO = 1_000_000;

    // tabela de simbolos: nome da variavel -> valor
    private final Map<String, BigInteger> tabelaSimbolos = new LinkedHashMap<>();
    // pilha usada para calcular as expressoes
    private final Deque<BigInteger> pilha = new ArrayDeque<>();
    // guarda tudo que o Show imprimiu
    private final StringBuilder saida = new StringBuilder();

    // variavel que vai receber o valor na atribuicao atual
    private String destino = null;

    // chamado pelo sintatico do GALS toda vez que chega numa acao semantica (#1 a #11)
    public void executeAction(int action, Token token) throws SemanticError {
        switch (action) {

            // #1 - guarda o nome da variavel que vai receber o valor (o A em "A = ...")
            case 1:
                destino = token.getLexeme();
                break;

            // #2 - fim da atribuicao (depois do ;): tira o resultado da pilha e salva na variavel
            // se a variavel ja existia, o valor antigo e substituido (reatribuicao)
            case 2: {
                BigInteger valor = desempilhar(token);
                tabelaSimbolos.put(destino, valor);
                destino = null;
                break;
            }

            // #3 - Show (depois do ;): tira o resultado da pilha e imprime em binario
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

            // #4 - soma
            // o segundo operando sai primeiro da pilha, por isso o b vem antes do a
            // (o mesmo vale para as outras operacoes)
            case 4: {
                BigInteger b = desempilhar(token);
                BigInteger a = desempilhar(token);
                pilha.push(a.add(b));
                break;
            }

            // #5 - subtracao
            // a linguagem e sem sinal, entao resultado negativo da erro
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

            // #6 - multiplicacao
            case 6: {
                BigInteger b = desempilhar(token);
                BigInteger a = desempilhar(token);
                pilha.push(a.multiply(b));
                break;
            }

            // #7 - divisao inteira (a linguagem nao tem numero com virgula)
            // divisao por zero da erro
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

            // #8 - exponenciacao
            // fica associativa a direita por causa da gramatica:
            // <potencia> ::= <fator> exponenciacao <potencia> #8
            case 8: {
                BigInteger expoente = desempilhar(token);
                BigInteger base = desempilhar(token);
                pilha.push(potencia(base, expoente, token));
                break;
            }

            // #9 - numero: converte o texto de base 2 para BigInteger e empilha
            case 9: {
                try {
                    pilha.push(new BigInteger(token.getLexeme(), 2));
                } catch (NumberFormatException e) {
                    erro("numero binario invalido: " + token.getLexeme(), token);
                }
                break;
            }

            // #10 - variavel usada numa expressao: busca o valor na tabela e empilha
            // se a variavel nunca recebeu valor, da erro
            case 10: {
                String nome = token.getLexeme();
                BigInteger valor = tabelaSimbolos.get(nome);
                if (valor == null) {
                    erro("variavel '" + nome + "' usada sem ter recebido valor", token);
                }
                pilha.push(valor);
                break;
            }

            // #11 - Log na base 2 (escolhemos base 2 porque a linguagem e binaria)
            // quando nao e exato, fica so a parte inteira. Log de zero da erro
            case 11: {
                BigInteger x = desempilhar(token);
                if (x.signum() <= 0) {
                    erro("Log nao esta definido para zero", token);
                }
                // bitCount() == 1 quer dizer que x e potencia de 2, ai o log e exato
                if (EXIGIR_RESULTADO_EXATO && x.bitCount() != 1) {
                    erro("Log de " + x.toString(2) + " nao eh exato na base 2: "
                            + "a linguagem so representa inteiros", token);
                }
                // bitLength() - 1 da o log2 de x arredondado para baixo
                pilha.push(BigInteger.valueOf(x.bitLength() - 1L));
                break;
            }

            default:
                erro("acao semantica " + action + " nao implementada", token);
        }
    }

    // calcula base ^ expoente, respeitando o limite de seguranca
    private BigInteger potencia(BigInteger base, BigInteger expoente, Token token) throws SemanticError {
        if (expoente.signum() == 0) {
            return BigInteger.ONE;            // x^0 = 1
        }
        if (expoente.bitLength() > 31) {
            erro("expoente grande demais para ser calculado", token);
        }
        int exp = expoente.intValue();
        // estimativa de quantos bits o resultado vai ter
        long bitsEstimados = (long) base.bitLength() * exp;
        if (bitsEstimados > LIMITE_BITS_RESULTADO) {
            erro("resultado da exponenciacao grande demais para ser calculado", token);
        }
        return base.pow(exp);
    }

    // tira um valor da pilha (se estiver vazia, algo deu errado na expressao)
    private BigInteger desempilhar(Token token) throws SemanticError {
        if (pilha.isEmpty()) {
            erro("expressao mal formada (pilha de valores vazia)", token);
        }
        return pilha.pop();
    }

    // lanca o erro semantico com a posicao do token, para o Main mostrar linha e coluna
    private void erro(String mensagem, Token token) throws SemanticError {
        if (token != null) {
            throw new SemanticError(mensagem, token.getPosition());
        }
        throw new SemanticError(mensagem);
    }

    // usados pelo Main
    public String getSaida() { return saida.toString(); }

    public Map<String, BigInteger> getTabelaSimbolos() { return tabelaSimbolos; }
}
