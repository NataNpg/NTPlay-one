# NTPlayone

Player IPTV para Android TV, feito para listas grandes e para o controle remoto.

![Android](https://img.shields.io/badge/Android-TV-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)

## O que faz

- **Listas Xtream Codes** (servidor, usuário e senha) e **links M3U/M3U8**.
- **Pareamento por QR code**: um servidor HTTP embutido na TV (porta 8899) recebe a lista enviada pelo celular, sem digitar nada com o controle.
- **TV ao vivo, filmes e séries** com navegação por categorias, busca, favoritos e "continuar assistindo".
- **Reprodução com ExoPlayer/Media3**, com reconexão automática e suporte a HLS.
- **Feito para controle remoto**: navegação por D-pad, painel lateral de canais e botão voltar em camadas.

## Requisitos

- Android 6.0 (API 23) ou superior.
- Android TV / Google TV com suporte a `leanback` (também instala em celular e tablet).

## Como instalar na TV

1. Gere o APK (veja abaixo) ou use o APK já compilado.
2. Transfira para a TV por pendrive, app **Downloader** ou `adb install`.
3. Abra o NTPlayone e adicione sua lista por Xtream, por link M3U ou escaneando o QR code com o celular.

> Para o pareamento por QR code, o celular e a TV precisam estar na mesma rede Wi-Fi.

## Compilando

```bash
# JDK 17+ e Android SDK instalados
./gradlew :app:assembleRelease
```

O APK sai em `app/build/outputs/apk/release/`.

### Assinatura

As credenciais da keystore **não ficam no repositório**. Crie um `local.properties` na raiz do projeto:

```properties
sdk.dir=/caminho/para/Android/Sdk
signing.storeFile=keystore/ntplayone.jks
signing.storePassword=SUA_SENHA
signing.keyAlias=SEU_ALIAS
signing.keyPassword=SUA_SENHA
```

Sem esses valores o build gera um APK de release sem assinatura.

## Estrutura

```
app/src/main/java/com/ntplayone/tv/
├── MainActivity.kt     # navegação e BackHandler global
├── AppModel.kt         # estado, listas, favoritos e progresso
├── HomeScreen.kt       # tela inicial, hero e contato
├── Screens.kt          # setup, formulários Xtream/M3U e configurações
├── PlayerScreen.kt     # player, controles e painel de canais
├── Components.kt       # botões, cartões, QR e componentes de foco
└── Util.kt             # QR code, rede e helpers
```

## Solicite sua lista

Para solicitar sua lista, entre em contato pelo número **(42) 99804-0495** — ou escaneie o QR code exibido na tela inicial do aplicativo.

WhatsApp: https://wa.me/5542998040495
