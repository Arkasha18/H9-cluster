# Выпуск новой версии

Коммиты и Pull Request в `main` никогда не создают production-релиз
автоматически. Обычный Android CI проверяет каждое изменение без доступа к
production-ключу. Подпись и публикация запускаются владельцем отдельно через
workflow `Production release`.

## 1. Выбор типа версии

Версия выбирается человеком до сборки. Сообщения коммитов и labels не меняют
версию автоматически.

| Тип | Когда использовать | Пример |
| --- | --- | --- |
| `patch` | hotfix ошибки или регрессии без новых функций | `9.5.2` → `9.5.3` |
| `minor` | новый совместимый функциональный релиз | `9.5.2` → `9.6.0` |
| `major` | несовместимое изменение или новая основная ветка продукта | `9.5.2` → `10.0.0` |

`versionName` всегда имеет числовой вид `X.Y.Z`. Production workflow не
публикует prerelease-суффиксы.

`versionCode` формируется как `YYYYMMDDNN` по московской дате. Первый выпуск
дня заканчивается на `01`, следующий — на `02`. Код записывается в Release PR и
не пересчитывается при повторном запуске сборки. Он обязан быть строго больше
кода APK из последнего опубликованного stable GitHub Release. Workflow сам
скачивает этот публичный APK, проверяет его подпись и извлекает `versionCode`.
Для локальной подготовки нужны авторизованный GitHub CLI `gh`,
`ANDROID_HOME`/`ANDROID_SDK_ROOT` и Android Build Tools `35.0.0`.

## 2. Подготовка обычного minor/major-релиза

1. В течение разработки заполняйте раздел `Unreleased` в `CHANGELOG.md`.
2. Обновите теги и на актуальном `main` рассчитайте будущую версию без
   изменения файлов:

   ```bash
   git fetch --force --tags origin
   python3 tools/release/release_tool.py prepare \
     --release-type minor
   ```

   Утилита через авторизованный `gh` находит последний опубликованный stable
   Release, скачивает его APK, проверяет production-сертификат и берёт
   `versionCode` именно из публичного файла. Поэтому minor/major корректно
   готовится и после off-main hotfix, metadata которого не переносилась в
   `main`.

3. Создайте показанную утилитой ветку, например `release/9.6.0`.
4. Примените рассчитанные `versionName`, `versionCode` и секцию changelog:

   ```bash
   python3 tools/release/release_tool.py prepare \
     --release-type minor \
     --apply
   ```

5. Закоммитьте только подготовку версии, отправьте Release PR в `main` и
   дождитесь обязательной проверки `Build and lint`.
6. После ревью слейте Release PR. Для `minor` и `major` production workflow
   принимает только точный текущий HEAD `main`.

Для major-релиза в обеих командах используйте `--release-type major`.
Утилита откажется работать при пустом `Unreleased`, грязном worktree,
неправильной ветке или немонотонном `versionCode`.

## 3. Подготовка hotfix

Hotfix всегда ответвляется от тега последнего опубликованного stable Release.
Это не позволяет случайно включить в patch незавершённые изменения из `main`.

```bash
git switch --create hotfix/9.5.3 v9.5.2
```

1. Внесите только исправление и его тесты.
2. Заполните `Unreleased`, закоммитьте исправление и changelog.
3. Выполните доверенную версию утилиты из актуального `origin/main`. Такой
   способ работает и для первого hotfix от старого тега, в котором самой
   утилиты ещё нет:

   ```bash
   set -o pipefail
   git fetch --force --tags origin \
     '+refs/heads/main:refs/remotes/origin/main'
   git show origin/main:tools/release/release_tool.py \
     | python3 - prepare --release-type patch --apply
   ```

4. Закоммитьте подготовку версии и отправьте ветку `hotfix/9.5.3`.
5. Откройте не-draft PR в `main` для обсуждения и CI, но не сливайте его до
   выпуска. Все review threads должны быть закрыты. Release gate требует
   успешный `pull_request`-run текущего доверенного `android-ci.yml`, связанный
   одновременно с точным SHA hotfix и текущим SHA `main`; PR также обязан быть
   mergeable. Сам preflight отдельно собирает и проверяет исходный hotfix SHA.
   Поэтому первый hotfix от старого тега не зависит от наличия нового
   `push: hotfix/**` trigger в этом теге.
6. Production workflow принимает patch только с точного HEAD ветки
   `hotfix/X.Y.Z`, требует ровно следующий patch после последнего stable-тега,
   запрещает merge-коммиты и изменения release/build control plane.
7. После публикации перенесите исправление, тесты и уже опубликованную секцию
   changelog отдельным PR в `main`. Не переносите из hotfix значения Gradle
   `versionName`/`versionCode`: `main` может законно оставаться на более старой
   metadata до подготовки следующего minor/major.

## 4. Проверка кандидата без публикации

Откройте `Actions` → `Production release` → `Run workflow` и обязательно
оставьте ветку запуска `main`.

Заполните:

- `operation`: `verify`;
- `release_type`: `patch`, `minor` или `major`;
- `version`: версия без `v`;
- `commit_sha`: полный 40-символьный SHA;
- `confirmation`: пусто.

Workflow проверит владельца запуска, точный HEAD исходной ветки, последний
успешный `Build and lint` для подходящего `push` или `pull_request` run,
связанный PR без открытых review threads, SemVer, `versionCode`, changelog и
изменения от предыдущего публичного stable Release. Затем выполняются theme smoke, тесты
Debug/Demo/Release, lint всех вариантов, Demo secret hygiene, package name,
ABI и проверка того, что Release APK ещё не подписан.

Никакие теги, Releases или GitHub Secrets в режиме `verify` не изменяются.

## 5. Подпись и публикация

Повторно запустите `Production release` с теми же типом, версией и SHA:

- `operation`: `publish`;
- `confirmation`: точная строка `release vX.Y.Z`.

Последовательность закрыта при любой ошибке:

1. Полный preflight повторяется без production signing key.
2. Из того же точного SHA собирается unsigned production APK с
   `H9_TBOX_PASSWORD`.
3. Проверяется, что TBOX-материал соответствует защищённому build input, а
   открытая строка пароля отсутствует в DEX.
4. Unsigned APK передаётся отдельному job через Actions artifact.
5. GitHub Environment `release` останавливает job до личного подтверждения
   `Arkasha18`.
6. Signing job не делает checkout и не запускает Gradle, Docker или код
   кандидата. Он выполняет только доверенную копию release gate, переданную
   control job через неизменяемый Actions artifact.
7. Keystore восстанавливается только в `$RUNNER_TEMP`, APK сначала проходит
   `zipalign`, затем подписывается `apksigner`.
8. Проверяются единственный signer, закреплённый SHA-256 сертификата,
   `net.adminrunet.h9cluster`, versionName/versionCode, `arm64-v8a`,
   отсутствие debug-флага и точное имя:

   ```text
   H9_Cluster_vX.Y.Z_adminrunet_release.apk
   ```

9. Создаётся аннотированный Git-тег `vX.Y.Z`. Это не GPG/SSH-подпись тега:
   Android APK подписывается отдельным production-ключом.
10. GitHub Release создаётся как draft, получает APK и `SHA256SUMS`, затем оба
    asset скачиваются обратно и сравниваются побайтно. Поле Release
    `targetCommitish` остаётся `main`, чтобы штатный `GITHUB_TOKEN` мог
    обслуживать off-main hotfix; фактическую привязку к кандидату задаёт и
    доказывает уже проверенный аннотированный тег.
11. Непосредственно перед публикацией ещё раз проверяются HEAD исходной ветки,
    предыдущий stable Release, PR, последний CI run, review threads и commit
    аннотированного тега.
12. Draft публикуется последним действием и становится Immutable Release. Уже
    публичные assets скачиваются в новый каталог и снова сравниваются с
    локальными файлами и `SHA256SUMS`.

Если исходная ветка сдвинулась во время ожидания approval, публикация
останавливается. Нельзя переносить существующий тег или заменять asset
опубликованного релиза.

## 6. Доступ и GitHub Secrets

Production workflow дополнительно проверяет одновременно `github.actor` и
`github.triggering_actor`. Оба обязаны быть `Arkasha18`, поэтому чужой запуск
или rerun завершается до production-сборки.

Environment `release` содержит только signing secrets:

- secret `H9_RELEASE_KEYSTORE_B64`;
- secret `H9_RELEASE_STORE_PASSWORD`;
- secret `H9_RELEASE_KEY_ALIAS`;
- secret `H9_RELEASE_KEY_PASSWORD`.

Repository Variables содержат публичные контрольные значения:

- `H9_RELEASE_CERT_SHA256` — SHA-256 production-сертификата APK;
- `H9_IMMUTABLE_RELEASES_ENABLED=true` — подтверждение, что настройка
  Immutable Releases включена и проверена владельцем.

Required reviewer Environment — только `Arkasha18`; self-review разрешён,
поскольку владелец одновременно запускает и подтверждает релиз. Environment
доступен только workflow, запущенному из защищённого `main`.

Для тегов `v*` действует active ruleset без bypass-акторов: update и deletion
запрещены всем, поэтому ни владелец, ни workflow не может перенести или
удалить уже созданный release-тег. Creation не ограничивается ruleset:
встроенный GitHub Actions App нельзя добавить bypass-актором в ruleset личного
репозитория. Вместо внешнего PAT/App creation защищена правами репозитория:
единственный write/admin collaborator — `Arkasha18`, а `contents: write` имеет только
signing job после Environment approval. Добавление другого write-collaborator
требует повторного аудита этого инварианта.
Настройка репозитория Immutable Releases должна оставаться включённой. Это
ручной административный инвариант владельца: обычный `GITHUB_TOKEN` не может
строго прочитать эту настройку до публикации. Поэтому workflow сначала
проверяет repository variable, а после публикации — реальное поле `immutable`
ответа GitHub API. Менять настройку может только администратор `Arkasha18`.

Keystore Base64 — это кодировка, а не шифрование. Ключ и пароли запрещено
помещать в репозиторий, Docker image, cache, Actions artifact или логи.
Отдельная офлайн-резервная копия production-ключа обязательна.

`H9_TBOX_PASSWORD` остаётся отдельным Repository Secret. Он доступен только
production build step и не передаётся signing job. Маскирование в APK защищает
от простого поиска строки, но не превращает публичный APK в криптографическое
хранилище.

## 7. Повтор после сбоя

- До создания тега workflow можно запустить заново с теми же входными данными.
- Если тег уже создан, он обязан указывать на тот же SHA и быть
  аннотированным.
- После частичного сбоя на шаге публикации используйте **Re-run failed jobs**:
  он повторно использует уже собранный unsigned artifact. Новый запуск заново
  генерирует случайную TBOX-маску, поэтому его APK закономерно отличается и
  fail-closed проверка не позволит подменить asset существующего draft.
- Существующий draft продолжается только при точном совпадении title, notes,
  target commit, APK и `SHA256SUMS`. Если исходный run больше недоступен, draft
  и оставшийся unpublished-тег нужно отдельно проверить до нового запуска.
  Удаление тега потребует осознанно временно отключить immutable-tag ruleset,
  удалить только проверенный unpublished-тег и сразу вернуть ruleset. Workflow
  ничего такого автоматически не удаляет.
- Опубликованный Immutable Release считается завершённым и никогда не
  перезаписывается. Любое исправление выпускается новой patch-версией.

## 8. Локальная аварийная проверка

Локальные signing properties поддерживаются для контрольной сборки и
восстановления процесса, но не заменяют GitHub Environment:

```bash
./gradlew clean assembleRelease
apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/*.apk
shasum -a 256 app/build/outputs/apk/release/*.apk
```

Перед ручной публикацией всё равно обязательны те же проверки сертификата,
metadata, SHA-256 и повторное скачивание публичного asset.
