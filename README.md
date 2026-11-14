# Lusa IPTV (Android e Android TV)

Leitor IPTV em Kotlin + Jetpack Compose, com o motor **LibVLC**. Interface escura com destaque turquesa,
cartões grandes e gradientes (TV ao Vivo, Filmes, Séries).

> Não inclui canais, listas nem credenciais. Usa apenas fontes e conteúdos a que tens direito de aceder.

## Como obter o APK (sem instalar nada no PC)
1. No github.com, cria um repositório (pode ser privado) e carrega **todo o conteúdo desta pasta**, mantendo as pastas
   (Add file → Upload files). Se não conseguires arrastar a pasta escondida `.github`, usa **Create new file** com o nome
   `.github/workflows/build-apk.yml` e cola o conteúdo.
2. Abre o separador **Actions**: a compilação "Compilar APK" arranca sozinha (5 a 10 minutos).
3. Descarrega **Artifacts → IPTVPlayerPro-apk**, passa o `.apk` para o telemóvel/TV e instala.
4. Se falhar, abre o passo "Compilar" e copia as últimas linhas do erro.

## O que já está implementado
- **Fontes:** Xtream compatível, lista M3U/M3U8 por URL e ficheiro M3U (seletor de documentos do Android).
  Várias fontes, mudança da fonte ativa, atualização manual, eliminar fonte (apaga favoritos e histórico).
- **Segurança:** URL do servidor e palavra-passe cifrados com o Android Keystore (AES-GCM); erros sem expor credenciais;
  aviso e consentimento quando a fonte usa HTTP; "Eliminar todos os dados".
- **Início:** barra com logótipo, data/hora, pesquisa e definições; 3 cartões (desativados se a fonte não tiver o conteúdo);
  atalhos Favoritos e Histórico; rodapé com fonte, estado, última atualização e validade da conta (só quando o servidor a envia).
- **Catálogo:** categorias à esquerda (com contagens, pesquisa, Favoritos e Histórico) e grelha à direita; em ecrãs estreitos
  as categorias passam a uma linha de botões. Grelha/lista, ordenação, pesquisa, menu de contexto (premir prolongadamente).
  Canais com logótipo, filmes e séries com capas.
- **Séries:** temporadas e episódios. **Pesquisa global** em canais, filmes e séries.
- **Leitor:** ecrã inteiro, canal anterior/seguinte, pausa, barra de tempo (só em filmes/episódios; em direto aparece "AO VIVO"),
  faixa de áudio, legendas, proporção, religação automática (3 tentativas com atraso progressivo), ecrã sempre ligado.
- **Gravação** com submenu: qualidade, formato (MKV/MP4/TS) e pasta; usa uma só ligação ao servidor.
- **Android TV:** categoria LEANBACK, contorno e aumento de escala no item focado, comando (OK, setas, play/pausa, canal +/-).

## Ainda não implementado (fase seguinte)
Estas partes do documento de requisitos **não** estão feitas e não aparecem na app como se funcionassem:
- Guia TV / EPG (XMLTV) e programa atual/seguinte. Hoje mostra "Programação indisponível".
- Ecrã "TV ao Vivo" com 3 painéis (lista + pré-visualização + informação).
- Multi-ecrã, "Rever programas", controlo parental com PIN, perfis, Picture-in-Picture, bloqueio de toque,
  seleção de qualidade por variante, retomar posição e "continuar a ver".
- Arquitetura com Hilt, Room, Retrofit, DataStore e WorkManager, e Media3/ExoPlayer: o leitor continua em **LibVLC**
  porque é o que permite gravar com uma só ligação. Catálogos muito grandes são guardados em memória.
- Testes unitários e de interface, e Gradle Wrapper (a compilação usa `gradle/actions/setup-gradle`).
- Aviso: só a URL do servidor e a palavra-passe são cifradas; os favoritos e o histórico ficam em ficheiros privados da app.
