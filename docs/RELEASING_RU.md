# Выпуск новой версии

Production-ключ хранится только локально. Не добавляйте `.p12`, пароли или
Gradle properties с секретами в GitHub, GitHub Actions либо Docker-образ.

## 1. Подготовка

1. Обновите `versionCode` и `versionName` в `app/build.gradle.kts`.
2. Перенесите изменения из `Unreleased` в новую версию `CHANGELOG.md`.
3. Дождитесь успешной обязательной проверки `Build and lint` в Pull Request.

## 2. Локальная release-сборка

Настройте параметры подписи в пользовательском
`~/.gradle/gradle.properties`, как описано в `README.md`, затем выполните:

```bash
./gradlew clean assembleRelease
```

Проверьте подпись и сохраните SHA-256:

```bash
apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/*.apk
shasum -a 256 app/build/outputs/apk/release/*.apk
```

## 3. Публикация

1. Создайте подписанный тег `vX.Y.Z` на проверенном commit из `main`.
2. Создайте GitHub Release с выдержкой из `CHANGELOG.md`.
3. Приложите только подписанный APK.
4. Укажите SHA-256 APK, совместимость и предупреждение о безопасности.

Docker-образ из GHCR предназначен для воспроизводимой debug-сборки и не
содержит production-ключ.
