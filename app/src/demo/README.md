# Demo system-icon preview

Sculpted Titanium uses `TitaniumDemoSystemView` above its approved artwork.
This Demo-only layer includes the existing 24 lamp-test glyphs, the simulated
factory selector/manual caption and the lower-right drive mode. In D, the skin
supplies only the ratio and the simulation supplies only the letter. These
captions and lamps are absent from production; the vehicle owns them there.
Its lower-left internal technical reservations stay clear of skin artwork.

ION AURORA's demo adds a separate, non-interactive `DemoSystemIconsView` above
the skin. It shows 24 photo/video-derived symbols in a simultaneous lamp-test
composition. Selector letters and drive-mode captions are not imitated.
The skin itself shows only the actual ratio 1..8 in D; M/P/N/R remain blank.
The lamps do not represent real warnings or the demo vehicle's current faults.

Glyphs are sourced without modifying the original PNGs from
`system_icons_capture/photo_template/`. Individual crops are drawn within the
reserved regions of the current mask03. This is an approximate visual
simulation, not a pixel-exact reproduction of the vehicle's QNX overlay.

The renderer, image assets, and tests belong to the Demo source set. Debug and
Release provide no-op factories, and the Release contract test requires the
demo image assets to be absent. ION AURORA itself still paints nothing under
mask03 apart from the separately authorized cosmic atmosphere and tiny automatic
gear digit described in the main skins README.

### Sculpted Titanium: проверка двух экранов на Windows

Подготовленный сценарий `tools/start_titanium_demo.ps1` использует штатный
MultiDisplay в Android 10+; он не создаёт плавающий экран через
`overlay_display_devices`. Проверяются ровно два логических дисплея — 0 и 2,
размер второго 1920×720, плотность 240 dpi и флаг `OWN_CONTENT_ONLY`.
Без последнего флага основной экран может зеркалироваться на дополнительный.

```powershell
.\tools\start_titanium_demo.ps1 -SdkRoot 'D:\H9-Development\Android\Sdk' -AvdHome 'D:\H9-Development\Android\avd' -AvdName 'gwm-api30-arm-translation' -StartEmulator
```

Запуск ограничивает Android двумя ядрами, 2048 МБ гостевой RAM и частотой
30 Гц, работает без сохранения снимка и в режиме `-read-only`. Память процесса
эмулятора на Windows дополнительно включает графические буферы и накладные
расходы виртуальной машины. После настройки выбрать **Sculpted Titanium**
в приложении и проверить, что `PreviewActivity` находится на Display 2.

28.10.2026 сценарий создал Display 0 и Display 2 в Android 11; размер
1920×720, плотность 240 dpi и `OWN_CONTENT_ONLY` подтверждены через ADB.
Визуальная проверка нового скина не завершена: пользователь сообщил о
зависании, эмулятор закрыт, отсутствие его процессов проверено. `native-render.png`
в `artifacts/sculpted-titanium` получен тестом Android Canvas, а не снимком
работающего эмулятора. Поддержка MultiDisplay описана в
[документации AOSP](https://source.android.com/docs/setup/test/avd#add-multi-display-support),
команды консоли — в
[документации Android Emulator](https://developer.android.com/studio/run/emulator-console).
