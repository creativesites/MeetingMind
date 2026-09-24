# Third-Party Notices & Model Licenses

MeetingMind incorporates the following open-source runtimes, models, and libraries. This list reflects what is actually integrated in the codebase today — not aspirational or planned components.

## 1. On-Device Speech Recognition (ASR)

- **NVIDIA Parakeet TDT 0.6B v3 (INT8)**
  - Source: NVIDIA / Hugging Face (`nvidia/parakeet-tdt-0.6b-v3`), converted to sherpa-onnx ONNX format
  - License: **CC-BY-4.0**
  - Usage: downloaded on demand at runtime (not bundled in the APK) and run entirely on-device via sherpa-onnx's `OfflineRecognizer` (`nemo_transducer` model type). Attribution required per CC-BY-4.0; no modification of the model weights is performed by this app.

## 2. On-Device Voice Activity Detection (VAD)

- **Silero VAD**
  - Source: Silero Team (`snakers4/silero-vad`)
  - License: **MIT License**
  - Attribution: Copyright (c) 2020-present Silero Team.
  - Usage: downloaded on demand at runtime and run entirely on-device via sherpa-onnx's `Vad` API (`SileroVadModelConfig`).

## 3. On-Device Speaker Diarization

- **pyannote/segmentation-3.0** (sherpa-onnx int8 export)
  - Source: `pyannote/segmentation-3.0` on Hugging Face, converted to ONNX by the sherpa-onnx project
  - License: **MIT License** (Copyright (c) 2022 CNRS)
  - Usage: downloaded on demand (extracted from the project's official `.tar.bz2` release asset) and run on-device via sherpa-onnx's `OfflineSpeakerDiarization` to find speaker-change boundaries.

- **3D-Speaker CAM++ (English, VoxCeleb-trained)**
  - Source: Alibaba DAMO Academy (`alibaba-damo-academy/3D-Speaker`), re-exported to ONNX by the sherpa-onnx project
  - License: **Apache License 2.0**
  - Usage: downloaded on demand and run on-device via sherpa-onnx's `OfflineSpeakerDiarization` to turn each diarization segment into a speaker embedding for clustering.

## 4. Language Intelligence & LLM

- **Qwen2.5-1.5B-Instruct** (litert-community `.task` build, q8 quantized)
  - Source: Qwen Team / Alibaba Cloud (`Qwen/Qwen2.5-1.5B-Instruct`), converted to the MediaPipe LiteRT `.task` format by the `litert-community` Hugging Face organization
  - License: **Apache License 2.0**
  - Usage: downloaded on demand (not bundled in the APK) and run entirely on-device via MediaPipe's `LlmInference` API to generate meeting summaries, decisions, action items, questions, and follow-ups from the real transcript only.

- **MediaPipe Tasks GenAI** (`com.google.mediapipe:tasks-genai`)
  - Source: Google (`google/mediapipe`)
  - License: **Apache License 2.0**
  - Usage: provides the `LlmInference` Android API and bundled native inference engine that runs the Qwen2.5 model on-device.

## 5. On-Device Inference Runtime

- **sherpa-onnx** (k2-fsa)
  - Source: `k2-fsa/sherpa-onnx`
  - License: **Apache License 2.0**
  - Usage: provides the `OfflineRecognizer`/`Vad`/`OfflineSpeakerDiarization` Android APIs and bundled ONNX Runtime native libraries (`libsherpa-onnx-jni.so`, `libonnxruntime.so`) that run the Parakeet ASR, Silero VAD, and speaker diarization models on-device. Consumed as a prebuilt AAR from the project's official GitHub Releases — no source modifications.

- **Apache Commons Compress**
  - Source: Apache Software Foundation
  - License: **Apache License 2.0**
  - Usage: extracts the one `.onnx` file needed from the official pyannote segmentation model's `.tar.bz2` release archive. Not used for any other purpose.

## 6. Android Libraries

- **AndroidX & Jetpack Compose**: Apache License 2.0
- **Kotlin & Kotlinx Coroutines**: Apache License 2.0
- **Room Persistence**: Apache License 2.0
- **Firebase Android SDK** (Auth, Firestore): Apache License 2.0
- **Coil**: Apache License 2.0
- **OkHttp**: Apache License 2.0


## Inter and Outfit fonts

`app/src/main/res/font/inter_*.ttf` (Inter, © The Inter Project Authors) and
`app/src/main/res/font/outfit_*.ttf` (Outfit, © The Outfit Project Authors), latin subsets from
Fontsource. Licensed under the SIL Open Font License 1.1: https://openfontlicense.org
Copies of Inter and Outfit are also in `app/src/main/assets/fonts/`, where the share-card renderer loads them.

## Lora and Playfair Display fonts

`app/src/main/assets/fonts/lora*.ttf` (Lora, © The Lora Project Authors) and
`app/src/main/assets/fonts/playfair.ttf` (Playfair Display, © The Playfair Project Authors), taken
from the google/fonts repository. Licensed under the SIL Open Font License 1.1: https://openfontlicense.org

## Morning and Evening (C. H. Spurgeon, 1865)

`app/src/main/assets/devotionals/spurgeon_morning_evening.json.gz` holds the text of Spurgeon's
*Morning and Evening: Daily Readings* (1865), which is in the public domain. The text was taken
from the Christian Classics Ethereal Library edition (ccel.org), keeping only the plain text of
each reading; none of CCEL's markup or editorial apparatus is included.

## Quotes

`app/src/main/assets/devotionals/quotes.json` contains short quotations from public-domain
writers and hymns, published before 1929 or by authors who died more than 70 years ago. Each
one is shown with its author and source.

## Built-in background photos

`app/src/main/assets/backgrounds/` contains 26 photos from Unsplash, used under the Unsplash
License (https://unsplash.com/license): free to use, including commercially, with no permission
needed. They were obtained through Lorem Picsum and were resized and re-encoded as WebP.

- Snowfield light: photo by Lukas Budimaier (https://unsplash.com/photos/6cY-FvMlmkQ)
- Cathedral: photo by Jeff Sheldon (https://unsplash.com/photos/u3gES0SUsnI)
- Mountain chapel: photo by Jeff Sheldon (https://unsplash.com/photos/4IPe3tnBKK0)
- Golden morning: photo by Fritz Bielmeier (https://unsplash.com/photos/ooJi3CJQRa8)
- Still sea at dawn: photo by Jonathan Bean (https://unsplash.com/photos/ywnnwzcdR5o)
- Above the clouds: photo by Sebastien Gabriel (https://unsplash.com/photos/2W5LoumSdfw)
- Orchard sunrise: photo by Philipp Reiner (https://unsplash.com/photos/qPJ6eRAMmCM)
- Forest light: photo by Mr. Marco (https://unsplash.com/photos/QP1dUyQ8WsI)
- Green forest: photo by Sven Schlager (https://unsplash.com/photos/xzjouTJASSA)
- Lone tree: photo by Jasper van der Meij (https://unsplash.com/photos/Xo3uIN_Q1Y0)
- Mountain lake: photo by Ales Krivec (https://unsplash.com/photos/DgtRKZOOE0w)
- Meadow tree: photo by Silvestri Matteo (https://unsplash.com/photos/6-C0VRsagUw)
- Misty sunrise: photo by Elaine Li (https://unsplash.com/photos/9jYj32TN9Ts)
- Quiet lake: photo by Dustin Scarpitti (https://unsplash.com/photos/RdF3apSExR0)
- Waterfall: photo by Jeff Sheldon (https://unsplash.com/photos/SdSc4sWVMRU)
- Green valley: photo by Andrew Coelho (https://unsplash.com/photos/VB-w_3dnyvI)
- Meadow path: photo by Drew Geraets (https://unsplash.com/photos/NtrxaEdbMXU)
- Dandelions: photo by Jason Long (https://unsplash.com/photos/FOeDIUwYiSw)
- Calm water: photo by Griffin Keller (https://unsplash.com/photos/7oS_26cb1Wo)
- Evening field: photo by Kenneth Thewissen (https://unsplash.com/photos/D76DklsG-5U)
- Pink sea: photo by Kelly Sikkema (https://unsplash.com/photos/X7dy114KWs4)
- Glacier lake: photo by Tanvi Malik (https://unsplash.com/photos/OeC1wIsKNpk)
- Golden hills: photo by David Marcu (https://unsplash.com/photos/GyALQFQ9cp4)
- Woodland path: photo by Sonja Langford (https://unsplash.com/photos/L_F8jAsRWtU)
- Sun in the grass: photo by Jake Givens (https://unsplash.com/photos/ocwmWiNAWGs)
- Sky reflection: photo by Susanne Feldt (https://unsplash.com/photos/SIoHky3TPeo)

## Free Use Bible API (bible.helloao.org)

Translations, audio narrations, commentaries and cross-references are fetched from the Free Use
Bible API by the AO Lab, which allows free use, including commercial use, without a key. Each
translation is shown with its own name and licence link.

- **Commentaries** (Matthew Henry, Jamieson-Fausset-Brown, Adam Clarke, John Gill, John Calvin,
  Keil & Delitzsch): public domain. Tyndale Open Study Notes are used under their open licence.
- **Cross-references**: the Open Bible Cross References dataset (openbible.info), licensed
  CC BY 4.0 and adapted by the Free Use Bible API.
- **Audio narrations and verse timings**: served by the Free Use Bible API and openbible.com
  for the Berean Standard Bible.
