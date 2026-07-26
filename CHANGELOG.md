# История изменений

Заметные изменения проекта перечисляются в этом файле. Версии следуют
[Semantic Versioning](https://semver.org/), когда это применимо.

## Unreleased

### Added

- воспроизводимое Docker-окружение с JDK 17 и Android SDK 35;
- GitHub Actions для debug-сборки и Android Lint;
- публикация build-образа в GitHub Container Registry;
- шаблоны Issues и Pull Request, правила участия и политика безопасности.

### Security

- проверка SHA-256 загружаемого Gradle;
- закрепление используемых GitHub Actions полными commit SHA;
- автоматические проверки изменений до слияния в `main`.

## 9.0.0 — 2026-07-26

### Added

- темы Classic и Horizon;
- вывод основных показателей автомобиля на дополнительный дисплей 1920×720;
- read-only интеграция со штатным GWM Adapter;
- высокочастотное чтение RPM через FDBus с Binder fallback;
- отдельные скорости четырёх колёс и момент маховика;
- первый production APK.
