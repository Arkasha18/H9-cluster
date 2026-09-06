# Dashboard skins

Each skin owns its complete rendering code and design assets:

| Skin | Renderer | Assets |
| --- | --- | --- |
| ION AURORA | `ionaurora/IonAuroraClusterView.java` | `assets/dashboard/skins/ionaurora/` |
| Classic | `classic/ClassicClusterView.java` | `assets/dashboard/skins/classic/` |
| Horizon | `horizon/HorizonClusterView.java` | Asset-free Canvas renderer |
| Simple | `simple/SimpleClusterView.java` | Asset-free Canvas renderer |
| Sport | `sport/SportClusterView.java` | `assets/dashboard/skins/sport/` |
| Stock | none, the factory is `null` | Nothing is drawn |

All renderers receive the same immutable `ClusterState` through the shared
`ClusterRenderer` interface. Vehicle services, polling and decoding stay outside
the skin folders.

Real-device screenshots of all four renderers are available in the
[skin gallery](../../../../../../../../docs/SKINS_RU.md).

## Factory gear companion numeral

Classic and Sport match ION AURORA's automatic gear numeral: Rajdhani 44,
x=1000, baseline=63, with no lower GEAR card, border, selector letter or glow.
Only valid D ratios 1..8 are drawn. M1..M8, P/N/R, unknown selectors and invalid
ratios draw nothing, leaving the factory selector/manual caption unduplicated.
The original digit position and its narrow bounds are checked against the
actual ION AURORA renderer by native pixel tests.

## ION AURORA live layout

The mask-safe layout is rendered directly by `IonAuroraClusterView` and
`IonAuroraChrome`. Only the unchanged `map_black_gradient.png` and the approved
`cosmic_sides.png` provide bitmap backgrounds; the older `static_alpha_art.png`
and `static_color_art.png` are not used by this renderer.

The two cylindrical tapes frame the map at x=652 and x=1268 on the 1920×720 canvas.
Their screen-space fixed readouts share y=365 and use whole km/h and raw rpm (for example,
86 and 2400), with the same rounded value driving the tape and digital display.
The split index ends outside the numeric capsule, whose text is drawn last.
Tyre pressure and individual wheel speeds occupy the upper corner panels,
whose contours closely follow beneath the system-icon arcs. Instantaneous and
average consumption have their own left-hand instrument with Russian captions
and units (л/ч at <=1 km/h, otherwise л/100 км). The average uses the same
consumptionLitersPer100Km field as Classic/Sport, not the trip journal average.
Steering has a separate wheel pictogram and РУЛЬ caption, outside the three
temperature rows. Wi-Fi and wheel-speed outlier colors match Classic/Sport.
Side ribs move with the exact same projection as the tape; slow energy packets
add depth without changing the indicated value. Ambient phase wraps seamlessly
every 200 seconds to retain float precision after long vehicle uptimes.
All pixels with nonzero alpha in mask03 are protected from text, gauges,
panels, indices, and glow, except the explicitly requested automatic gear
numeral in the tight rectangle [982,28,1018,66). Like Simple, it is drawn at
x=1000, baseline=63: D shows only 1..8; M/P/N/R and invalid ratios show nothing.
Selector letters and driveMode are never drawn by ION AURORA.
The separately approved cosmic background may overlap system regions.
The mask is used by tests, never as a renderer clip or cutout.
Tests render the complete foreground without either bitmap background and
require zero alpha under the mask outside that numeric rectangle, including
fractional-alpha edges, plus at least one empty pixel between foreground and
mask03. Separate tests require a blank numeral for non-D states.
Both the Demo and transparent variants are checked, including idle, maximum,
and warning states. Selector letters and drive-mode indicators remain system-owned.

`Stock` is the one entry without a renderer factory. `SkinRegistry.hasRenderer`
reports it, `ClusterLauncher` then closes any open cluster window instead of
starting one, and the factory instrument panel of the vehicle stays visible.

## Optional per-skin settings

A skin may own an independent settings editor by implementing
`SkinSettingsProvider` inside its package. The shared application:

- shows the **Configure selected skin** button only for definitions that have a
  provider;
- keeps a separate unsaved draft while the user switches between skins;
- sends the draft to Display ID 2 for live preview;
- stores primitive values under the selected skin ID only after **Save**;
- restores the persisted renderer when the settings screen closes without
  saving.

Other skins never see disabled foreign controls. Their renderers and settings
remain independent.

Use stable lowercase keys and return a complete normalized snapshot from the
provider:

```java
public final class ExampleSettingsProvider
        implements SkinSettingsProvider {
    private static final String SHOW_CLOCK = "show_clock";

    @Override
    public SkinSettings getDefaultSettings() {
        return SkinSettings.builder()
                .putBoolean(SHOW_CLOCK, true)
                .build();
    }

    @Override
    public SkinSettings normalize(SkinSettings settings) {
        return SkinSettings.builder()
                .putBoolean(
                        SHOW_CLOCK,
                        settings.getBoolean(SHOW_CLOCK, true))
                .build();
    }

    @Override
    public View createEditor(
            Context context,
            SkinSettings initialSettings,
            Listener listener) {
        Switch editor = new Switch(context);
        editor.setChecked(initialSettings.getBoolean(SHOW_CLOCK, true));
        editor.setOnCheckedChangeListener((button, checked) ->
                listener.onSettingsChanged(
                        SkinSettings.builder()
                                .putBoolean(SHOW_CLOCK, checked)
                                .build()));
        return editor;
    }
}
```

Pass the provider only in that skin's `SkinRegistry.Definition`. Its renderer
factory receives the normalized `SkinSettings` snapshot and may pass it to the
skin view constructor. Built-in skins without options pass `null` and retain
their existing settings screen and rendering behavior.

To add a skin:

1. Create `skins/<id>/<Name>ClusterView.java`.
2. Put all of its bitmaps in `assets/dashboard/skins/<id>/`.
3. Optionally create a `SkinSettingsProvider` in the same package.
4. Add one `Definition` to `SkinRegistry`.

To remove a skin, delete its two folders and its `SkinRegistry` entry. Do not
modify another renderer to customize a new skin.
