---
name: wire-protocol
description: Reference for the OpenDisplay wire protocol (framing, handshake, message types) that this Android client must speak to interoperate with the existing Mac app, ported from Shared/Protocol.swift, iOS/PhoneReceiver.swift and Mac/MacSender.swift.
---

# Protocolo OpenDisplay (referência para o cliente Android)

Fonte: repositório upstream `peetzweg/opendisplay` (Swift), arquivos `Shared/Protocol.swift`,
`iOS/PhoneReceiver.swift`, `Mac/MacSender.swift`, `Mac/InputInjector.swift`. **O Mac não muda** —
esta skill existe pra qualquer trabalho na camada de rede/handshake do Android conferir contra o
comportamento real do peer, em vez de reinventar por memória.

## Regra: mudança nunca quebra o protocolo (vale também pra segurança)

Fonte da verdade é o `PROTOCOL.md` do upstream (`peetzweg/opendisplay`), não só esta skill. O Mac
não muda; qualquer divergência vira incompatibilidade silenciosa. Antes de alterar rede,
handshake, framing ou ciclo de sessão:

1. Ler a seção correspondente do `PROTOCOL.md` e checar os MUST/MUST NOT.
2. Hardening (rejeitar conexão, truncar/validar campo, limitar tamanho, descartar mensagem) não
   pode derrubar sessão legítima nem mudar o que vai no fio. Em dúvida, degradar em vez de
   rejeitar.
3. Normativos que mais batem com ajuste de segurança: tipo/campo desconhecido é ignorado (§6);
   payload de controle não parseável é ignorado, nunca fatal; conexão nova substitui a ativa (§1);
   silêncio > 5 s é morte e ping a cada 2 s (§8.2); `hello` sempre primeiro; demux JSON/vídeo
   (§4) intacto.
4. Se a divergência for deliberada, documentar em `RATIONALE.md`, testar contra o Mac real e
   avisar o dono antes do merge. Exemplo: #165 diverge do §1 (issue #168).

## Papéis

- O **Android (este projeto) escuta** um `ServerSocket` na porta **9000**.
- O **Mac conecta** — dial ativo, tanto por WiFi (resolvendo o serviço mDNS) quanto por USB
  (usbmuxd, que não existe no Android — ver a seção de USB no README).
- Depois de conectado, o **mesmo socket TCP carrega vídeo E controle**, nas duas direções.

## Descoberta WiFi

Anunciar via `NsdManager`:
- `serviceType = "_opensidecar._tcp"`
- `serviceName` = nome editável pelo usuário (default algo como "OpenDisplay"), análogo ao
  `serviceName` do iOS.
- TXT record com `id` (install id estável, UUID persistido) e `pv` (versão do protocolo, string).

## Framing (as duas direções, sempre)

```
[4 bytes tamanho, big-endian, UInt32][payload de N bytes]
```

Sem exceção — controle e vídeo usam o mesmo framing. Ler sempre: 4 bytes de tamanho, depois
exatamente esse tanto de payload, e decidir o que é o payload por conteúdo (ver heurística abaixo).

## Heurística: payload é JSON de controle ou vídeo Annex-B?

Aplicar nessa ordem exata (do `PhoneReceiver.swift`, função `handleAnnexB`):

```kotlin
val isControlJson = payload.size < 32_768 &&
    payload.isNotEmpty() && payload[0] == '{'.code.toByte() &&
    payload.none { it == 0.toByte() }
```

Motivo do `none { it == 0 }`: frames de vídeo também começam com um prefixo de telemetria JSON
(`{"cap":...,"snd":...}`) antes do primeiro NAL — mas sempre contêm bytes `0x00` (start codes
`00 00 00 01`), o que os desambigua de um JSON puro (que nunca tem NUL, mesmo com PNG em base64).

## Vídeo (Mac → Android)

- H.264 Annex-B, NALUs delimitados por start code de 4 bytes `00 00 00 01` (o sender só emite esse
  formato, nunca o de 3 bytes).
- Cada payload de vídeo pode ter um **prefixo de telemetria** antes do primeiro start code:
  `{"cap":<capturaMs>,"snd":<envioMs>}` — usar pra calcular latência ponta-a-ponta (precisa do
  offset de relógio via ping/pong, ver abaixo).
- NALU tipo 7 = SPS, tipo 8 = PPS, tipo 6 = SEI (ignorar), qualquer outro = slice de vídeo (VCL).
- Keyframes vêm com SPS+PPS embutidos antes do slice.
- Ao juntar SPS+PPS novos (ou na primeira vez), montar o formato/`MediaFormat` H.264 e (re)criar o
  decoder — uma mudança de SPS/PPS in-band significa que o Mac mudou de resolução (ex.: rotação do
  Android, que reenvia `hello` com dimensões trocadas e força o Mac a reconstruir o display
  virtual).
- Todos os NALUs VCL de um mesmo payload (um "frame de wire") formam UM único frame decodificado —
  não decodificar slice por slice.

## Controle — mensagens Android → Mac

Todas como JSON, um objeto por payload, campo `type` sempre presente:

| type | campos | quando |
|---|---|---|
| `hello` | `pixelsWide`, `pixelsHigh`, `scale`, `device` (string livre, ex. `"Android"`), `id` (install id, UUID persistido), `pv` (versão do protocolo) | ao conectar, e de novo sempre que a orientação mudar (dimensões trocam) |
| `ping` | `t` (timestamp local em ms, `System.currentTimeMillis()`) | a cada 2s enquanto conectado |
| `touch` | `phase` (`"began"` \| `"moved"` \| `"ended"` \| `"cancelled"`), `x`, `y` (normalizados 0–1, origem top-left, espaço do vídeo), `t` (opcional: timestamp ajustado pelo offset de relógio, pra medir latência de input) | eventos de toque |
| `scroll` | `dx`, `dy` (pixels de vídeo, sinal de "natural scrolling") | gesto de 2 dedos |
| `kf` | — | quando o decoder perde sincronia (ex. entrou no meio de um GOP) e precisa de keyframe novo |
| `sleeping` | — | tela bloqueou — Mac derruba o display virtual e arma reconexão pro wake |
| `closing` | — | app sendo fechado de propósito — Mac encerra a sessão sem esperar wake |

## Controle — mensagens Mac → Android

| type | campos | o que fazer |
|---|---|---|
| `welcome` | `pv`, `min` | identifica a versão do Mac; se `pv < WireProtocol.minSupportedPeer`, avisar o usuário que o Mac está desatualizado |
| `updateRequired` | `target`, `store` (URL), `message` | Mac recusa a sessão até o Android atualizar — mostrar `message` e um link pra `store` |
| `pong` | `t` (echo do `ping`), `mt` (clock do Mac) | usar pra estimar offset de relógio (ver abaixo) |
| `cursor` | `x`, `y` (normalizados, ou ausentes quando `v=0`), `v` (1=visível, 0=escondido) | desenhar/esconder um cursor local sobreposto ao vídeo — não fica "queimado" no stream, é mais rápido assim |
| `cursorImg` | `png` (base64), `nw`, `nh` (tamanho normalizado contra o display), `ax`, `ay` (hotspot normalizado) | atualizar o sprite do cursor quando o formato muda (seta ↔ texto ↔ resize) |
| `stats` | (diagnóstico, enviado pelo Mac a cada ~5s, não requer ação) | opcional: logar |

## Handshake de versão

```kotlin
object WireProtocol {
    const val VERSION = 2
    const val MIN_SUPPORTED_PEER = 1
    const val ASSUMED_WHEN_ABSENT = 1   // peer sem campo pv = versão 1
}
```

Mandar `pv` em todo `hello`. Um Mac que não manda `pv` em `welcome` (instalação muito antiga) deve
ser tratado como protocolo 1 — nunca bloquear a conexão nesse caso, só o gate explícito de
`updateRequired`/`pv < minSupportedPeer` decide isso.

## Sincronia de relógio (NTP-style, pro HUD de latência)

Do `PhoneReceiver.swift`: manter as últimas ~15 amostras de `(rtt, offset)` de cada `pong`
recebido, `rtt = t2 - t1` (t1 = timestamp que mandamos no `ping`, t2 = agora), `offset = mt - (t1+t2)/2`.
O offset usado é o da amostra de **menor RTT** (menos assimétrica). Latência ponta-a-ponta de um
frame = `(agora + offset) - capturaMs` (do prefixo de telemetria do frame de vídeo).

Isso é só pro HUD de performance — não bloqueia o MVP funcional (vídeo + touch funcionam sem
isso), mas é barato de implementar e dá paridade com o app iOS.

## Injeção no lado Mac (pra entender o que o touch/scroll fazem do outro lado)

`Mac/InputInjector.swift`: `began` → mouse down, `moved` → drag (ou move se não tiver down),
`ended`/`cancelled` → mouse up. `scroll` vira `CGEvent` de scroll wheel em pixels. Não precisa
replicar nada disso no Android — só precisa mandar as mensagens certas.

**Mirror é view-only (upstream, não bug do client):** o `MacSender` só cria o `InputInjector` no
caminho de Extend, sempre preso ao display virtual (`inputInjector = InputInjector(displayID:
vd.displayID)`); no modo Mirror ele nunca é criado, então `touch`/`scroll` continuam sendo
mandados pelo Android mas caem em `inputInjector?...` nil e são ignorados em silêncio. Qualquer
pedido de "touch no Mirror" é feature request pro `peetzweg/opendisplay`, não algo corrigível aqui.
