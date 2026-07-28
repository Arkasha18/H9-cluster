# Dashboard skins

Each skin owns its complete rendering code and design assets:

| Skin | Renderer | Assets |
| --- | --- | --- |
| Classic | `classic/ClassicClusterView.java` | `assets/dashboard/skins/classic/` |
| Horizon | `horizon/HorizonClusterView.java` | Asset-free Canvas renderer |
| Sport | `sport/SportClusterView.java` | `assets/dashboard/skins/sport/` |

All renderers receive the same immutable `ClusterState` through the shared
`ClusterRenderer` interface. Vehicle services, polling and decoding stay outside
the skin folders.

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
