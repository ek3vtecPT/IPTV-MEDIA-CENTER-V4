# IPTV Player Pro (Media3 ExoPlayer)

Ficheiros principais:
- `app/src/main/java/pt/iptvplayer/pro/MainActivity.kt` (todo o código da app num só ficheiro)
- `.github/workflows/build.yml` (compila o APK no GitHub Actions)

## Atualizar o repositório
1. Apaga do repositório o ficheiro antigo `.github/workflows/build-apk.yml` (senão correm duas compilações)
   e a pasta antiga `app/src/main/java/pt/iptvplayer/pro/` (ficheiros Home.kt, Catalog.kt, etc.).
2. Carrega todo o conteúdo desta pasta (mantendo as pastas).
3. Em **Actions** descarrega o APK em *Artifacts → IPTVPlayerPro-apk*.

Versões: AGP 8.5.2, Gradle 8.7, Kotlin 1.9.24, Compose Compiler 1.5.14, Media3 1.3.1, JDK 17.
Nota: nesta versão não existe gravação (o ExoPlayer não grava o fluxo).
