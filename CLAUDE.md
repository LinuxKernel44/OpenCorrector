# CLAUDE.md — Contexte du projet OpenCorrector

Ce fichier est destiné aux futures sessions Claude Code travaillant sur ce dépôt. Il capture
le contexte, les décisions techniques et les contraintes qui ne sont pas forcément évidentes
en lisant seulement le code.

## Résumé du projet

App Android **100% Java** (aucun Kotlin, aucun Compose) qui corrige/reformule du texte via un
LLM exécuté **entièrement en local** avec llama.cpp (JNI/CMake, pas de MediaPipe). Point
d'entrée : sélection de texte système → `Intent.ACTION_PROCESS_TEXT` → popup avec 3
suggestions (Corrigé / Plus formel / Plus concis) → remplacement direct du texte sélectionné.

Matériel cible principal : **Snapdragon 855** (CPU Kryo 485, GPU Adreno 640, pas de NPU
utilisable) → priorité absolue à l'inférence CPU efficace. Langues supportées : français et
anglais, détection automatique, la langue d'entrée doit être conservée en sortie.

## Contraintes non négociables (imposées par l'utilisateur)

- Java uniquement, aucun `.kt`, aucun Jetpack Compose — layouts XML classiques.
- llama.cpp pour l'inférence (pas d'alternative sans justification technique forte).
- Aucune donnée utilisateur envoyée sur le réseau, jamais — le réseau ne sert qu'au
  téléchargement initial du modèle (`INTERNET` permission scoped à `ModelDownloader`/`DownloadTask`).
- Aucune télémétrie/analytics.
- Modèles jamais embarqués dans l'APK — téléchargés au premier lancement, stockage privé.
- Vérification SHA-256 obligatoire après téléchargement, refus d'utiliser un fichier corrompu.
- Ne jamais tronquer silencieusement un texte trop long → chunking avec avertissement utilisateur.
- Ne jamais logger le contenu du texte utilisateur (ni côté Java, ni côté natif C++).

## Décisions techniques et pourquoi

- **`LlamaService` = foreground service pendant chargement/génération uniquement.**
  Demandé explicitement par l'utilisateur (2026-09-04) : "pendant que l'app sera en
  génération il faudra foreground service". Le service est un `Service` lié (bound) classique
  le reste du temps ; il appelle `startForeground()` juste avant `loadModel()`/`generate()` et
  `stopForeground(STOP_FOREGROUND_REMOVE)` dès que l'opération se termine (voir
  `LlamaService.java`). Type de service : `specialUse` avec le sous-type
  `on_device_llm_text_generation` (requis par Android 14+ pour tout foreground service qui ne
  rentre pas dans une catégorie standard).
- **Déchargement automatique après inactivité** (`InferenceConfig.unloadDelayMillis`, réglable
  dans les Paramètres, défaut 5 min) : géré par un `Handler.postDelayed` dans `LlamaService`,
  annulé/reprogrammé à chaque `onBind`/`onRebind`/`onUnbind` et à la fin de chaque
  chargement/génération. Ne dépend pas du cycle de vie Android seul, pour rester prévisible.
- **Sampling glouton (greedy)**, pas de température/top-p : un correcteur doit être fidèle et
  déterministe, pas créatif. Voir `llama_jni.cpp` (`llama_sampler_init_greedy()`).
- **Contexte reset avant chaque génération** (`llama_memory_clear`) : chaque appel
  (mode × chunk) est une requête indépendante, jamais la continuation d'une conversation — sans
  ce reset, le contexte KV grandirait sans borne sur les 3 modes × N chunks d'une même session.
- **Chunking par phrases** (`TextChunker`), jamais au milieu d'un mot ; si une phrase seule
  dépasse la limite, découpage de secours sur les espaces. Le comptage réel de tokens utilise
  le tokenizer natif (`LlamaNative.tokenCount` → `llama_tokenize`) une fois le modèle chargé ;
  avant chargement, `TextProcessor` utilise une heuristique (~4 caractères/token) uniquement
  pour afficher l'avertissement "texte long" instantanément.
- **Prompts système dans `res/raw/`**, jamais hardcodés en Java — 6 fichiers
  (`system_prompt_{fr,en}_{correction,formal,concise}.txt`), chargés et mis en cache par
  `PromptManager`. Format ChatML manuel (`<|im_start|>system...<|im_end|>` etc.) construit côté
  Java plutôt que via `llama_chat_apply_template` côté natif, pour garder le C++ simple.
- **ABI `arm64-v8a` uniquement** : cible réelle 64-bit only, réduit la taille de l'APK.
- **`BUILD_SHARED_LIBS=OFF`** pour llama.cpp : compilé en statique dans `libopencorrector.so`
  (un seul .so applicatif ; `libc++_shared.so`/`libomp.so` restent des dépendances du NDK
  auto-embarquées par le linker).
- **Vulkan désactivé par défaut** (`OPENCORRECTOR_ENABLE_VULKAN` OFF dans CMakeLists.txt).
  L'architecture le permet mais ce n'est ni compilé ni testé dans cette session — CPU only.
- **Un seul modèle/contexte natif à la fois** : pas de table de handles côté JNI, juste un
  pointeur `EngineContext*` casté en `jlong`. `LlamaEngine` sérialise tout sur un unique
  thread exécuteur car un `llama_context` n'est pas thread-safe.

## Modèles — valeurs exactes (vérifiées via l'API Hugging Face le 2026-09-04)

| Variante | Fichier | Taille (octets) | SHA-256 |
|----------|---------|------------------|---------|
| quality | `qwen2.5-1.5b-instruct-q4_k_m.gguf` | 1117320736 | `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e` |
| fast | `qwen2.5-0.5b-instruct-q4_k_m.gguf` | 491400032 | `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db` |

Si ces fichiers changent un jour sur Hugging Face (re-quantization), regénérer ces valeurs
via `Invoke-RestMethod https://huggingface.co/api/models/Qwen/<repo>/tree/main` (le champ
`lfs.oid` est le SHA-256 exact) et mettre à jour `LlamaModel.java` **et** ce tableau ensemble.

## API llama.cpp utilisée (sous-module épinglé sur le tag `b10797`)

L'API de llama.cpp change fréquemment. Cette session a vérifié l'API réelle du tag `b10797`
directement dans `include/llama.h` avant d'écrire `llama_jni.cpp` (ne pas supposer une API
d'une version antérieure/postérieure sans revérifier) :
`llama_backend_init`, `ggml_backend_load_all`, `llama_model_load_from_file`,
`llama_model_get_vocab`, `llama_init_from_model`, `llama_memory_clear`/`llama_get_memory`,
`llama_tokenize`, `llama_batch_get_one`, `llama_decode`, `llama_sampler_sample`,
`llama_vocab_is_eog`, `llama_token_to_piece`. Référence utilisée :
`app/src/main/cpp/third_party/llama.cpp/examples/simple-chat/simple-chat.cpp`.

Pour mettre à jour le sous-module vers un tag plus récent : lire le nouveau `include/llama.h`
et diff avec `llama_jni.cpp` avant de bouger quoi que ce soit — l'API a cassé plusieurs fois
dans l'historique de llama.cpp (ex: `llama_load_model_from_file` → `llama_model_load_from_file`,
`llama_free_model` → `llama_model_free`, tokenizer pris sur `llama_vocab*` et non plus sur
`llama_context*`).

## Environnement de build (poste de développement)

- Android SDK : `F:\AndroidSDK` (NDK 27.1.12297006, CMake 3.22.1 et 4.1.2 disponibles,
  build-tools 34–37, platforms 24–37 installées).
- `local.properties` (non versionné) pointe vers ce SDK.
- JDK 17 (Eclipse Temurin) utilisé pour Gradle.
- Gradle 8.7 via wrapper (`gradlew`/`gradlew.bat` + `gradle/wrapper/`), AGP 8.5.2.
- **Build entièrement validé dans cette session** : `./gradlew :app:assembleDebug` compile
  Java + JNI + llama.cpp (statique) avec succès en ~40 s (une fois le sous-module cloné),
  produit un APK debug de ~13 Mo avec `libopencorrector.so` (4,4 Mo). `./gradlew
  :app:testDebugUnitTest` : 15/15 tests passent (LanguageDetector, TextChunker, TextProcessor).
- **Non testé dans cette session** : exécution réelle sur un appareil/émulateur (pas de
  Snapdragon 855 ni d'AVD ARM64 disponible ici), téléchargement réel des ~1 Go de modèle,
  qualité de correction réelle (voir `docs/LINGUISTIC_TEST_CASES.md` pour le protocole).

## Dépôt GitHub

`https://github.com/LinuxKernel44/OpenCorrector` (public, appartient à l'utilisateur,
`gh auth status` confirme un compte `LinuxKernel44` déjà authentifié sur cette machine).

## Pistes non traitées / suite possible

- Support Vulkan réellement testé sur Adreno 640 (actuellement juste scaffoldé, OFF par défaut).
- Tests instrumentés (`androidTest`) qui installent réellement le modèle et vérifient
  bout-en-bout sur émulateur/device — actuellement seul `docs/LINGUISTIC_TEST_CASES.md`
  documente le protocole manuel.
- Gestion d'un AVD arm64 pour tester sans matériel physique (émulation lente, à évaluer).
- Éventuellement : bouton "ouvrir l'app" depuis la popup quand le modèle n'est pas téléchargé.
