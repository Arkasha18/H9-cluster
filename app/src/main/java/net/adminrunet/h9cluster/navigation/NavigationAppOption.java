package net.adminrunet.h9cluster.navigation;

final class NavigationAppOption {
    final String component;
    final String title;

    NavigationAppOption(String component, String title) {
        this.component = NavigationSettings.normalizeComponent(component);
        this.title = title == null ? "" : title;
    }
}
