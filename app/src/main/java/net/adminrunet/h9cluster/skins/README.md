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

To add a skin:

1. Create `skins/<id>/<Name>ClusterView.java`.
2. Put all of its bitmaps in `assets/dashboard/skins/<id>/`.
3. Add one `Definition` to `SkinRegistry`.

To remove a skin, delete its two folders and its `SkinRegistry` entry. Do not
modify another renderer to customize a new skin.
