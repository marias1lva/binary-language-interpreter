# Interpretador de linguagem binária com GALS

Trabalho M2 — Linguagens Formais e Autômatos
Universidade do Vale do Itajaí · Prof. Alex Luciano Roesler Rese

Implementação de uma gramática livre de contexto e de um interpretador para uma pequena linguagem de programação em que **todos os valores são números binários inteiros sem sinal**. O analisador léxico e o sintático são gerados pelo [Web GALS](https://lia-univali.github.io/Web-GALS/); o analisador semântico, que é o interpretador propriamente dito, foi escrito em Java.

```
A = 10;
B = 11;
B = 111 + A * B;
Show ( B );
```

```
1101
```

Os literais estão em base 2: `10` vale 2, `11` vale 3 e `111` vale 7. A expressão resulta em 7 + (2 × 3) = 13, exibido como `1101`.

## A linguagem

| Recurso | Sintaxe |
|---|---|
| Atribuição | `A = <expressão>;` |
| Exibição | `Show ( <expressão> );` |
| Soma, subtração | `+` `-` |
| Multiplicação, divisão | `*` `/` |
| Exponenciação | `^` |
| Logaritmo (base 2) | `Log ( <expressão> )` |
| Agrupamento | `( )` |

Os valores são inteiros binários sem sinal, sem limite de bits. Variáveis são sequências de letras e não precisam de declaração prévia, mas precisam ter recebido valor antes de serem usadas.

## Estrutura do projeto

| Arquivo | Origem |
|---|---|
| `LFATrabM2.gals`, `LFATrabM2.vgls` | Especificação salva do Web GALS |
| `Semantico.java` | Analisador semântico — **implementação autoral** |
| `Main.java` | Programa principal — **implementação autoral** |
| `Lexico.java`, `Sintatico.java`, `Token.java`, `Constants.java`, `ScannerConstants.java`, `ParserConstants.java`, `AnalysisError.java`, `LexicalError.java`, `SyntacticError.java`, `SemanticError.java` | Gerados pelo GALS |
| `exemplo.txt`, `testes.txt`, `erro-*.txt` | Casos de teste |

O GALS gera o `Semantico.java` com o método `executeAction` vazio. Toda a interpretação — tabela de símbolos, pilha de valores, aritmética e verificação de erros — foi implementada nesse arquivo.

## Como executar

```
chcp 65001
javac -d bin *.java
java -cp bin Main exemplo.txt
```

O `chcp 65001` ajusta a página de código do terminal do Windows para UTF-8, necessário para que as mensagens de erro acentuadas sejam exibidas corretamente.

O programa aceita o caminho de um arquivo-fonte como argumento. Sem argumento, executa o exemplo do enunciado.

## Especificação léxica

```
 : [\ \n\t\r\s]+

show: "Show"
log: "Log"

igual: "="
fim: ";"
abrePar: "("
fechaPar: ")"
soma: "+"
subtracao: "-"
multiplicacao: "*"
divisao: "/"
exponenciacao: "^"

variavel: [a-zA-Z]+
numero: [0-1]+
```

Duas decisões merecem destaque:

**`numero: [0-1]+`** restringe o alfabeto numérico aos dígitos 0 e 1. Um programa contendo `A = 2;` é rejeitado com erro léxico, porque `2` não é símbolo da linguagem. Aceitar `[0-9]+` faria o analisador reconhecer sentenças fora da linguagem especificada.

**`show` e `log` declarados antes de `variavel`.** Ambos também casam com `[a-zA-Z]+`; o GALS resolve o conflito pela ordem de declaração. Como estão escritos como literais entre aspas, o casamento é exato e sensível a maiúsculas — `Show` é palavra reservada, `show` não.

## Gramática

Símbolo inicial: `<programa>`. Classe do analisador: SLR(1).

```
<programa> ::= <programa> <comando> | <comando>;

<comando> ::= variavel #1 igual <expressao> fim #2
            | show abrePar <expressao> fechaPar fim #3;

<expressao> ::= <expressao> soma <termo> #4
              | <expressao> subtracao <termo> #5
              | <termo>;

<termo> ::= <termo> multiplicacao <potencia> #6
          | <termo> divisao <potencia> #7
          | <potencia>;

<potencia> ::= <fator> exponenciacao <potencia> #8
             | <fator>;

<fator> ::= numero #9
          | variavel #10
          | abrePar <expressao> fechaPar
          | log abrePar <expressao> fechaPar #11;
```

### Precedência

A precedência é codificada na própria estrutura da gramática, em quatro níveis:

| Nível | Não-terminal | Operadores |
|---|---|---|
| menor | `<expressao>` | soma, subtração |
| | `<termo>` | multiplicação, divisão |
| | `<potencia>` | exponenciação |
| maior | `<fator>` | número, variável, `( )`, `Log` |

Como a multiplicação pertence a um nível mais interno que a soma, `111 + A * B` é derivado como `111 + (A * B)`. Isso é observável na árvore de derivação: o `<termo>` que contém a multiplicação é filho do `<expressao>` que contém a soma, e consequentemente a ação `#6` é executada antes da `#4`.

### Associatividade

`<expressao>` e `<termo>` são **recursivos à esquerda**, o que torna soma, subtração, multiplicação e divisão associativos à esquerda:

```
1000 - 10 - 1  →  (1000 - 10) - 1  =  101
```

A distinção importa apenas para os operadores não associativos. Com `+` e `*` o resultado é o mesmo em qualquer agrupamento, mas com `-` e `/` a escolha errada produz valores incorretos — no exemplo acima, uma gramática recursiva à direita daria `1000 - (10 - 1)` = `111`.

`<potencia>` é **recursivo à direita**, por ser a exponenciação o único operador da linguagem com essa associatividade:

```
10 ^ 11 ^ 10  →  10 ^ (11 ^ 10)  =  2⁹  =  1000000000
```

### Ações semânticas

| Ação | Responsabilidade |
|---|---|
| `#1` | registra o identificador que receberá o valor |
| `#2` | desempilha o resultado e grava na tabela de símbolos |
| `#3` | desempilha e exibe o valor em base 2 |
| `#4` | soma |
| `#5` | subtração, com verificação de resultado negativo |
| `#6` | multiplicação |
| `#7` | divisão inteira, com verificação de divisor nulo |
| `#8` | exponenciação |
| `#9` | empilha o literal numérico, convertido da base 2 |
| `#10` | empilha o valor de uma variável, verificando se foi inicializada |
| `#11` | logaritmo na base 2, com verificação de operando nulo |

O analisador sintático gerado pelo GALS repassa a `executeAction` o último token consumido. Por isso `#1`, `#9` e `#10` conseguem ler o lexema relevante diretamente do token; as demais ações operam apenas sobre a pilha de valores.

## Fechamento do conjunto de valores

O enunciado define os valores da linguagem como inteiros binários **sem sinal**, ou seja, o conjunto dos naturais. Três das seis operações exigidas podem produzir resultado fora desse conjunto, e o interpretador trata cada uma explicitamente:

| Operação | Sai do conjunto quando | Tratamento |
|---|---|---|
| subtração | o resultado é negativo | erro semântico |
| divisão | o divisor é zero | erro semântico |
| divisão | o resultado é fracionário | truncamento para a parte inteira |
| logaritmo | o operando é zero | erro semântico |
| logaritmo | o resultado é irracional | truncamento para a parte inteira |

Os valores são armazenados em `BigInteger`, e não em `int`. Além de eliminar o estouro de 32 bits, isso evita que um resultado negativo seja silenciosamente reinterpretado em complemento de dois: em Java, `Integer.toBinaryString(-1)` produz `11111111111111111111111111111111`, um valor de 32 bits que não pertence à linguagem. Com a verificação da ação `#5`, o programa acusa o erro em vez de exibir esse valor.

A constante `EXIGIR_RESULTADO_EXATO`, no topo do `Semantico.java`, altera a política de truncamento: quando ativada, divisão e logaritmo com resultado não exato passam a ser erro semântico em vez de serem truncados.

## Tratamento de erros

Os três tipos de erro são reportados com linha e coluna:

```
Erro lexico: Caractere não esperado (linha 1, coluna 5)
Erro sintatico: esperado ';' (linha 2, coluna 8)
Erro semantico: resultado negativo (1 - 10): a linguagem trabalha apenas com inteiros sem sinal (linha 2, coluna 9)
```

## Testes

`java -cp bin Main testes.txt`

| Programa | Saída | Verifica |
|---|---|---|
| `B = 111 + A * B;` com A=10, B=11 | `1101` | precedência de `*` sobre `+` |
| `Show ( 1000 - 10 - 1 );` | `101` | associatividade à esquerda da subtração |
| `Show ( 10000 / 100 / 10 );` | `10` | associatividade à esquerda da divisão |
| `Show ( 10 ^ 11 ^ 10 );` | `1000000000` | associatividade à direita da exponenciação |
| `Show ( 10 * 11 ^ 10 );` | `10010` | precedência de `^` sobre `*` |
| `Show ( ( 111 + 10 ) * 11 );` | `11011` | agrupamento por parênteses |
| `Show ( Log ( 10000 ) );` | `100` | logaritmo base 2 |
| `Show ( 111 / 10 );` | `11` | divisão inteira |

Casos de rejeição, cada um em seu arquivo:

| Arquivo | Programa | Erro esperado |
|---|---|---|
| `erro-lexico.txt` | `A = 2;` | léxico — dígito fora do alfabeto |
| `erro-sem-sinal.txt` | `A = 1; B = 1 - 10;` | semântico — resultado negativo |
| `erro-divisao-zero.txt` | `Show ( 1 / 0 );` | semântico — divisão por zero |
| `erro-variavel-sem-valor.txt` | `Show ( Z );` | semântico — variável não inicializada |

## Decisões que vale confirmar com o professor

1. **Base do `Log`.** O enunciado não especifica. Adotei **base 2**, por ser a única em que o logaritmo de um número binário é naturalmente um inteiro sem sinal — `Log(X)` corresponde à posição do bit mais significativo. Para trocar, basta alterar a ação `#11` (e, no caso de uma forma `Log(base, valor)`, acrescentar uma produção à gramática).
2. **`Show` aceita expressão.** O enunciado menciona "exibição dos valores de variáveis". A produção adotada é `show abrePar <expressao> fechaPar`, que aceita variável e também expressão — um superconjunto do exigido. Para restringir ao enunciado literal, trocar `<expressao>` por `variavel` nessa produção e fazer a ação `#3` ler a variável diretamente da tabela de símbolos.
3. **Identificadores apenas com letras** (`variavel: [a-zA-Z]+`). Dígitos não foram permitidos em identificadores porque os únicos dígitos da linguagem são 0 e 1, o que tornaria nomes como `B1` ambíguos na leitura. Para permitir, usar `variavel: [a-zA-Z][a-zA-Z0-1]*`.