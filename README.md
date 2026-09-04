# OpenCorrector

Application Android (Java, sans Kotlin, sans Jetpack Compose) de correction et reformulation
de texte par LLM **exécuté entièrement en local** via [llama.cpp](https://github.com/ggml-org/llama.cpp).

Sélectionnez du texte dans n'importe quelle application, choisissez **« Corriger avec
OpenCorrector »** dans le menu de sélection, et remplacez le texte sélectionné par une version
corrigée, plus formelle ou plus concise — sans qu'aucune donnée ne quitte l'appareil.

## Confidentialité

- Tout le traitement du texte a lieu sur l'appareil, via un modèle GGUF chargé en mémoire.
- Le réseau (permission `INTERNET`) n'est utilisé **que** pour télécharger le fichier modèle
  une seule fois, dans `download/ModelDownloader.java` — jamais pour l'inférence.
- Aucune télémétrie, aucun analytics, aucun appel réseau après le téléchargement du modèle.
- Le contenu du texte utilisateur n'est jamais écrit dans les logs (voir les commentaires de
  `LlamaEngine`/`llama_jni.cpp`).

## Modèles

| Variante | Modèle | Quantization | Taille | Source |
|----------|--------|--------------|--------|--------|
| Qualité (défaut) | Qwen2.5-1.5B-Instruct | Q4_K_M | ~1,04 Go | [Qwen/Qwen2.5-1.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF) |
| Rapide | Qwen2.5-0.5B-Instruct | Q4_K_M | ~469 Mo | [Qwen/Qwen2.5-0.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF) |

Les checksums SHA-256 exacts sont dans [`LlamaModel.java`](app/src/main/java/com/opencorrector/inference/LlamaModel.java)
et vérifiés par [`ChecksumVerifier`](app/src/main/java/com/opencorrector/download/ChecksumVerifier.java)
après chaque téléchargement. Les modèles ne sont **jamais** embarqués dans l'APK ; ils sont
stockés dans le stockage privé de l'application (`getFilesDir()/models`).

## Prérequis pour compiler

- JDK 17
- Android SDK avec **API 34**, **Build-Tools ≥ 34**, **NDK 27.1.12297006** et **CMake 3.22.1**
- Environ 2 Go d'espace disque libre pour la compilation native de llama.cpp

## Cloner et compiler

Le dépôt utilise un **sous-module git** pour vendorer llama.cpp — il faut le récupérer :

```bash
git clone --recurse-submodules https://github.com/LinuxKernel44/OpenCorrector.git
cd OpenCorrector
```

Si le dépôt est déjà cloné sans `--recurse-submodules` :

```bash
git submodule update --init --recursive
```

Créer `local.properties` à la racine (non versionné) pointant vers votre SDK Android :

```properties
sdk.dir=/chemin/vers/Android/Sdk
```

Puis compiler :

```bash
./gradlew :app:assembleDebug
```

L'APK signé debug se trouve dans `app/build/outputs/apk/debug/app-debug.apk`. Il embarque
`libopencorrector.so` (JNI + llama.cpp + ggml compilés statiquement pour `arm64-v8a`),
`libc++_shared.so` et `libomp.so` (runtime OpenMP utilisé par le backend CPU de ggml).

### Tests

```bash
./gradlew :app:testDebugUnitTest
```

Tests unitaires purs Java (détection de langue, découpage en chunks) — ne nécessitent ni
émulateur ni modèle téléchargé. Le jeu de tests linguistiques manuels (qualité de correction
réelle, à exécuter sur un appareil avec le modèle téléchargé) est dans
[`docs/LINGUISTIC_TEST_CASES.md`](docs/LINGUISTIC_TEST_CASES.md).

## Architecture

```
app/src/main/java/com/opencorrector/
├── MainActivity.java            écran principal (statut modèle, téléchargement, paramètres)
├── ProcessTextActivity.java     popup ACTION_PROCESS_TEXT (les 3 suggestions)
├── SettingsActivity.java        paramètres (threads, modèle, délai de déchargement, GPU)
├── SuggestionsAdapter.java      RecyclerView des 3 suggestions
├── inference/                   LlamaService (foreground pendant génération), LlamaEngine,
│                                 LlamaModel, InferenceConfig, EngineException
├── download/                    ModelDownloader, DownloadTask (reprise HTTP Range),
│                                 ChecksumVerifier (SHA-256)
├── prompt/                      PromptManager (charge res/raw/system_prompt_*.txt),
│                                 CorrectionMode
├── text/                        LanguageDetector (fr/en), TextChunker, TextProcessor
├── settings/                    AppPreferences
└── nativebridge/                LlamaNative (JNI)

app/src/main/cpp/
├── CMakeLists.txt               build llama.cpp en statique + libopencorrector.so
├── llama_jni.cpp                pont JNI (load/generate/cancel/unload)
└── third_party/llama.cpp/       sous-module git (épinglé sur le tag b10797)
```

Voir [`CLAUDE.md`](CLAUDE.md) pour le contexte détaillé des décisions techniques.

## Limitations connues

- **Vulkan/GPU** : l'architecture le permet (option CMake `OPENCORRECTOR_ENABLE_VULKAN`) mais
  c'est **désactivé par défaut** — non testé sur Snapdragon 855 dans cette session. L'app
  fonctionne intégralement en CPU.
- **Test matériel réel** : le build a été validé (compilation Java + JNI + llama.cpp réussie,
  tests unitaires passants) mais n'a pas été exécuté sur un Snapdragon 855 physique dans cette
  session — voir `docs/LINGUISTIC_TEST_CASES.md` pour le protocole de validation manuelle.
- **ABI** : uniquement `arm64-v8a` (voir `app/build.gradle`).

## Licence

Le code de l'application n'a pas de licence choisie ici — à définir par le propriétaire du
dépôt. llama.cpp est sous licence MIT. Les modèles Qwen2.5-Instruct-GGUF sont sous licence
Apache 2.0 (voir la page Hugging Face de chaque modèle).
