# Canonical design-template assets

`h9_system_icon_forbidden_mask_1920x720.png` is the canonical system-icon
reserved-area overlay v2.4 used by `build_forum_design_template.py`.

The file is the original 1920×720 RGBA reference supplied by the vehicle owner.
Its public filename is intentionally unchanged so existing documentation and
4PDA links keep working.

- opaque or partially transparent black: reserved for system icons;
- fully transparent: this overlay imposes no layout restriction.

Open it as a temporary top layer while arranging a new skin. Do not scale,
move, bake it into the final artwork, or use it as a clip mask for an already
drawn design.
