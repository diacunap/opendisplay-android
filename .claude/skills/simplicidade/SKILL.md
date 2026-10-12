---
name: simplicidade
description: Regra permanente de estilo de código deste projeto — sempre gerar a solução mais simples possível antes de escrever qualquer código novo ou editar código existente. Use sempre, em toda tarefa de desenvolvimento neste repositório, não só quando pedido explicitamente.
---

# Simplicidade acima de tudo

Regra do projeto, sempre ativa — não é uma revisão opcional, é como o código deve nascer.

## Antes de escrever qualquer código

Pare e pergunte: **existe uma solução mais simples que resolve a mesma necessidade?** Só siga
para a alternativa mais complexa se a resposta for claramente não.

## Prioridades, nessa ordem

1. **Legibilidade** — código que se lê como prosa técnica direta.
2. **Manutenção** — fácil de mudar depois, sem precisar entender camadas extras primeiro.
3. **Clareza** — nomes que dizem o que a coisa é/faz, sem precisar de comentário pra decodificar.

## Evitar por padrão

- Abstrações genéricas pra um único caso de uso (interface/classe base pra um único
  implementador, generics onde um tipo concreto resolve).
- Padrões de projeto (Factory, Strategy, Observer manual, etc.) quando uma função ou uma
  classe simples já resolve.
- Otimizações prematuras — só otimizar quando há um problema de performance medido e real.
- Complexidade "por precaução" — não construir para requisitos hipotéticos futuros.

## Como aplicar

- Nomes claros e diretos (variável, função, classe) — se o nome já explica, não comenta.
- Métodos curtos, uma responsabilidade por função.
- Seguir o padrão que já existe no projeto (ver o README e o código vizinho) em vez de
  introduzir um estilo novo pra a mesma coisa.
- Só usar solução mais complexa quando a mais simples realmente não resolve — e, nesse caso,
  deixar claro (num comentário curto) *por que* a simples não bastou.

## Exemplo de aplicação neste projeto

`net/PhoneReceiver.kt` já segue isso: o cálculo de offset de relógio (`OffsetSample`) e a
métrica de performance (`PerfStats`) usam listas simples + funções privadas pequenas, não uma
classe de "collector" genérica ou um sistema de métricas plugável — não há necessidade disso
aqui, é um único receptor falando com um único peer.
