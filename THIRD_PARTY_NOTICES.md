# Third-party and data notices

The root [Apache-2.0 app-code license](LICENSE) and Ashva [NOTICE](NOTICE) do **not** relicense the following data or components. Preserve upstream notices and review transitive dependencies again before shipping a signed binary. No upstream endorsement is implied.

## Chess datasets (redistributed in source and reviewed APK assets)

| Material | License and credit | Source/version and changes |
| --- | --- | --- |
| content/raw/lichess-openings and its derived taxonomy pack | CC0-1.0; Lichess chess-openings contributors | [Pinned source](https://github.com/lichess-org/chess-openings/tree/c67912be581f0793dbaa776be5ccf111e01f88d9); names retained, legal replay, canonical SAN/UCI and normalized positions added |
| content/raw/lichess-broadcast-2020-04 and its derived game pack | CC-BY-SA-4.0; Lichess and original broadcast contributors | [Lichess broadcasts](https://database.lichess.org/#broadcasts), April 2020 snapshot; individual broadcast/game links retained; canonical SAN/UCI and metadata normalization; derived scores omit comments/clocks/evaluations/NAGs/alternate annotations; raw export retained |

Derived broadcast game data remains CC-BY-SA-4.0. When redistributing/adapting it, retain attribution, source and license links, identify changes and satisfy ShareAlike for adapted data. Do not label the data Apache-2.0 merely because it is bundled with the app.

Exact payload hashes, source/evidence URLs, processing revision and taxonomy dependency are in [sources.json](content/sources.json), [snapshot lock](content/snapshots.lock.json) and the manifests/ATTRIBUTION.txt under [content/packs](content/packs). The original taxonomy license is [COPYING.txt](content/raw/lichess-openings/COPYING.txt). License texts/links are under [LICENSES](LICENSES/README.md). No Masters collection, copyrighted historical commentary or external engine binary is bundled.

## Code/build/tooling

- JetBrains Kotlin, Kotlinx Serialization and Coroutines: Apache-2.0. [Kotlin license](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt), [serialization](https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt), [coroutines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt).
- AndroidX/Jetpack Compose, Material components/icons, Room, Navigation, Lifecycle and AndroidX SQLite adapters: Apache-2.0, with their upstream notices. [AndroidX source license](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/LICENSE.txt), [Material icons](https://github.com/google/material-design-icons/blob/master/LICENSE).
- Bundled SQLite native engine: SQLite public-domain dedication; the AndroidX wrapper is Apache-2.0. [SQLite copyright](https://www.sqlite.org/copyright.html).
- Gradle wrapper: Apache-2.0. [Gradle license](https://github.com/gradle/gradle/blob/master/LICENSE).
- Vendored Relay skill/CLI at .agents/skills/relay: its own [MIT license](.agents/skills/relay/LICENSE), Copyright 2026 Shishir Jha. The root license does not replace this file.
- Gitleaks and actionlint are developer tools, not shipped in the repository/APK. Downloads used for checks are pinned and verified. [Gitleaks MIT](https://github.com/gitleaks/gitleaks/blob/master/LICENSE), [actionlint MIT](https://github.com/rhysd/actionlint/blob/main/LICENSE.txt).
- GitHub Actions and emulator tooling are workflow dependencies, not app assets; their upstream licenses/terms apply. Exact action commits are pinned in the workflow.

Version pins live in [the catalog](gradle/libs.versions.toml). This is a source-repository notice, not a complete binary SBOM or legal certification. Obtain and retain dependency-specific NOTICE/license files when distributing compiled artifacts; never infer data rights from a library's code license.

## Visuals and teaching content

The existing knight launcher path and Compose UI are project assets, not copied Chess.com/Lichess logos or boards. Board pieces use Unicode/system glyphs; no external chess-piece image set or bundled proprietary font is present. Screenshots show this app on a synthetic isolated emulator. Introductory seed explanations are authored prototype content, not verified GM quotations or copyrighted historical annotations.
