# Dashboard skins

Each skin owns its complete rendering code and design assets:

| Skin | Renderer | Assets |
| --- | --- | --- |
| ION AURORA | `ionaurora/IonAuroraClusterView.java` | `assets/dashboard/skins/ionaurora/` |
| Classic | `classic/ClassicClusterView.java` | `assets/dashboard/skins/classic/` |
| Horizon | `horizon/HorizonClusterView.java` | Asset-free Canvas renderer |
| Simple | `simple/SimpleClusterView.java` | Asset-free Canvas renderer |
| Sport | `sport/SportClusterView.java` | `assets/dashboard/skins/sport/` |
| Sculpted Titanium | `sculptedtitanium/SculptedTitaniumClusterView.java` | `assets/dashboard/skins/sculptedtitanium/` |
| Stock | none, the factory is `null` | Nothing is drawn |

All renderers receive the same immutable `ClusterState` through the shared
`ClusterRenderer` interface. Vehicle services, polling and decoding stay outside
the skin folders.

For a new skin, follow the complete
[design workflow](../../../../../../../../docs/FORUM_DESIGN_PROMPTS_RU.md):
inventory all readouts, freeze their anchors before styling, and review both
the native render and the coloured technical-field overlay. The template now
separates strict lower-left reservations from factory fields that permit a
decorative underlay. This does not relax any existing skin's stricter tests.
Keep the original calibrated PNG intact; document and test any derived edge
treatment. Validate foreground alpha separately from the full composition.

## Sculpted Titanium

The titanium/glass/amber sketch uses a 1920×720 design canvas. Its sculpted
artwork is exported by `tools/assets/sculpted_titanium/export.mjs`; all captions
and values are now centred within the original housings by Canvas, following
the user's real-car review. No text is baked into the runtime PNG.
Canvas renders live values, horizontal speed/RPM scales,
temperature/voltage bars and the rotating steering-wheel glyph. The original
calibrated map PNG is copied byte-for-byte; the runtime composition softens its
top strip over y=40..79. The inner shoulders have a design-specific alpha fade.
The x=560..1359 corridor below y=80
stays free of skin artwork; full map transparency remains x=672..1095.

For this skin the user explicitly approved artwork **below** the factory
perimeter lamps, top-centre gear and lower-right drive-mode caption. The two
internal lower-left reservations remain completely clear with a one-pixel
margin. Tests apply mask03 only as a validator, never a renderer cutout.
Production renders only the automatic D ratio at (1000,63), leaving factory
selector/manual captions and drive mode to the car. Demo alone adds their
simulation and the 24 existing lamp-test glyphs above this skin.

Missing/invalid telemetry uses a dash; instant consumption switches to л/ч at
0–1 km/h; ATF becomes unavailable after 15 seconds. The displayed average uses
`consumptionLitersPer100Km`, independently of the trip-journal average.
The global `SpeedDisplayMode` from PR #82 applies to both the bar and digits;
consumption units still depend on the measured speed. Titanium and ION AURORA
animate the RPM gauge continuously, while their RPM digits use a shared
160 ms minimum update interval, 10 RPM rounding and 8 RPM hysteresis. These are
display filters only: raw telemetry and trip detection are unchanged.
The ION tape's small index capsule uses the same stabilized RPM as the large
number; only the surrounding tape geometry follows every animation frame.

## Factory-notification visibility and adapter refresh

The root view temporarily reveals the calibrated QNX notification rectangle.
`FactoryNotificationMonitor` recognises the TTS packet's action as well as its
warning ID: a dismiss with a nonzero ID restores the skin immediately. An
initial cached GET_DATA packet does not prove a card is still on screen, and
repeated copies cannot extend the fail-safe indefinitely. Door-state visibility
and the normal factory indicator reservations retain their existing behavior.

If speed callbacks are absent for one second or listener registration fails,
the production data source refreshes speed, gear, wheel speeds and door state
using read-only GET_DATA on a worker, at most once per 500 ms. A newer callback
wins over an older in-flight response; stop/disconnect invalidate pending work.
This refresh never replays cached TTS or fabricates new RPM/trip timestamps.
Invalid negative speed is not converted into a new zero-speed measurement.

An ION AURORA Demo preview and real-device screenshots of the four original
renderers are available in the
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
86 and 2400). Speed uses the same rounded value for tape and digits; the RPM
tape follows the animated reading while its digits update at a calmer cadence.
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
