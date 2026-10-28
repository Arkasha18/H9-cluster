# Third-party notices

The PolyForm Noncommercial License in the repository root applies only to
files for which `admin.ru.net` owns the necessary rights. The following
components remain under their respective licenses.

## Fonts

### Noto Sans (design tooling only)

- File: `tools/assets/fonts/NotoSans.ttf`; not included in Android assets
- Copyright: Copyright 2022 The Noto Project Authors
- Project: <https://github.com/notofonts/latin-greek-cyrillic>
- Source: Google Fonts `ofl/notosans/NotoSans[wdth,wght].ttf`, revision
  `8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5`
- SHA-256: `bfb7bb691513f12e734dc346c03a03f784912432d7e3fa8e56efcf906fe86b3d`
- License: SIL Open Font License 1.1
- License text: `third_party/licenses/NotoSans-OFL-1.1.txt`

The unchanged font supplies Cyrillic glyphs for the generated technical guide
on Windows, macOS and Linux. It does not change the application's typography.

### Inter Regular

- File: `app/src/main/assets/fonts/Inter-Regular.ttf`
- Copyright: Copyright (c) 2016 The Inter Project Authors
- Project: <https://github.com/rsms/inter>
- License: SIL Open Font License 1.1
- License text: `third_party/licenses/Inter-OFL-1.1.txt`

### Rajdhani Medium

- File: `app/src/main/assets/fonts/Rajdhani-Medium.ttf`
- Copyright: Copyright (c) 2014, Indian Type Foundry
- Project: <https://github.com/itfoundry/rajdhani>
- License: SIL Open Font License 1.1
- License text: `third_party/licenses/Rajdhani-OFL-1.1.txt`

## Gradle Wrapper

- File: `gradle/wrapper/gradle-wrapper.jar`
- Project: <https://github.com/gradle/gradle>
- License: Apache License 2.0
- License text: `third_party/licenses/Apache-2.0.txt`

## Native runtime libraries

The following ARM64 binary libraries were copied from the stock software of a
compatible Haval H9 head unit and are included solely to provide runtime
compatibility with its FDBus service:

- `app/src/main/jniLibs/arm64-v8a/libfdbus-jni.so`
- `app/src/main/jniLibs/arm64-v8a/libcommon-base.so`
- `app/src/main/jniLibs/arm64-v8a/libc++.so`
- `app/src/main/jniLibs/arm64-v8a/libutils.so`
- `app/src/main/jniLibs/arm64-v8a/libcutils.so`
- `app/src/main/jniLibs/arm64-v8a/libvndksupport.so`

These files are third-party components. They are not authored by
`admin.ru.net`, are not licensed under PolyForm, and no ownership claim is
made over them.

The names correspond to the upstream FDBus, Android Open Source Project and
LLVM Android runtime projects. Their upstream versions are generally
distributed under Apache License 2.0 or the applicable LLVM license. However,
the exact provenance, version, OEM modifications and redistribution terms of
these particular extracted binaries have not been independently verified.
Inclusion in this repository does not grant rights beyond those supplied by
their respective copyright holders.

The application catches native loading failures and falls back to the stock
Binder RPM source, so the rest of the application remains functional if a
redistributor removes these files.

## JSch

- Gradle dependency: `com.jcraft:jsch:0.1.55`
- Project: <https://www.jcraft.com/jsch/>
- License: BSD-style JSch license
- License text: `third_party/licenses/JSch-BSD.txt`

JSch is used only for the read-only connection to the TBOX shared-memory
snapshot that contains transmission temperature.

## Trademarks

Haval, Great Wall Motor and related marks are the property of their respective
owners. Their appearance in project descriptions indicates compatibility only
and does not imply affiliation, sponsorship or endorsement.
