---
name: run
description: Build, install and run the OpenDisplay Android client on an emulator or device, and tail its logs.
---

# Rodar o OpenDisplay Android

Este projeto é um app Android puro (Kotlin + Gradle + Compose). Não tem servidor, não tem modo
browser-driven — é "instala no device e conecta no Mac de verdade".

## Pré-requisitos

1. Android SDK instalado, com `ANDROID_HOME` setado ou `local.properties` na raiz apontando
   `sdk.dir=/caminho/para/Android/sdk`. Sem isso o Gradle falha na etapa `:app:compileDebugKotlin`
   por falta de `android.jar`.
2. Um device físico ou emulador rodando (`adb devices` deve listar pelo menos um).
3. Opcional, mas recomendado para teste real: o app Mac do
   [opendisplay](https://github.com/peetzweg/opendisplay) rodando na mesma rede WiFi (ver o README do repo).

## Build + instalar

```sh
cd <repo root>
./gradlew assembleDebug        # só compila
./gradlew installDebug         # compila + instala no device/emulador conectado
./gradlew uninstallDebug       # remove do device
```

Se `./gradlew` não existir ainda (wrapper não gerado), rodar primeiro:
```sh
gradle wrapper --gradle-version 9.4.1 --distribution-type bin
```

## Abrir e ver rodando

```sh
adb shell am start -n com.peetzweg.opensidecar.android/.MainActivity
```

## Logs

Toda a camada de rede/protocolo loga com a tag `OpenDisplay` (ver `Log.kt` — mesma convenção do
`Log.swift` do projeto original, um wrapper fino sobre o logger nativo). Para filtrar:

```sh
adb logcat -s OpenDisplay:*
```

Erros de decodificação de vídeo (`MediaCodec`) aparecem com a tag `OpenDisplay` também — não usar
`adb logcat *:E` como primeira tentativa, esconde os `Log.info` que geralmente explicam o estado
da conexão antes do erro.

## Testar o pipeline sem o Mac

Não existe "modo mock" no app — ele só faz sentido conectado a um sender real. Para testar o
handshake e a UI sem precisar montar o Mac todo, é possível escrever um script Python/Node
descartável que abre um socket TCP pro device (via `adb reverse` ou IP direto), manda um `hello`
fake e alguns frames Annex-B de um vídeo de teste. Preferir isso a modificar o app com atalhos de
debug permanentes.

## Erros comuns

| Sintoma | Causa provável |
|---|---|
| `SDK location not found` | falta `local.properties` / `ANDROID_HOME` |
| App abre mas fica em "Listening on :9000" pra sempre | Mac não está na mesma rede, ou não descobriu o serviço mDNS (checar `NsdManager` registrou sem erro no logcat) |
| Tela preta, "Connected" mas sem vídeo | keyframe SPS/PPS não chegou ainda, ou `MediaCodec` falhou ao configurar — checar logcat por `MediaCodec` |
| Touch não faz nada no Mac | Accessibility permission não concedida no lado Mac — isso é do app Mac, não do Android |
