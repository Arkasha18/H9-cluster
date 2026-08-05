package net.adminrunet.h9cluster.skins;

import net.adminrunet.h9cluster.ClusterRenderer;
import net.adminrunet.h9cluster.skins.classic.ClassicClusterView;
import net.adminrunet.h9cluster.skins.horizon.HorizonClusterView;
import net.adminrunet.h9cluster.skins.horizon.HorizonSettingsProvider;
import net.adminrunet.h9cluster.skins.simple.SimpleClusterView;
import net.adminrunet.h9cluster.skins.simple.SimpleSettingsProvider;
import net.adminrunet.h9cluster.skins.sport.SportClusterView;

import android.content.Context;
import android.view.View;

/**
 * The single inclusion point for selectable skins.
 *
 * <p>Each renderer and all of its design assets live in its own {@code skins/<id>}
 * folder. Adding or removing a skin requires changing this registry only; telemetry
 * collection and the other renderers remain independent.</p>
 */
public final class SkinRegistry {
    public static final String CLASSIC = "classic";
    public static final String HORIZON = "horizon";
    public static final String SIMPLE = "simple";
    public static final String SPORT = "sport";

    private interface RendererFactory {
        View create(Context context, SkinSettings settings);
    }

    public static final class Definition {
        public final String id;
        public final String title;
        public final String description;
        private final RendererFactory factory;
        private final SkinSettingsProvider settingsProvider;

        private Definition(
                String id,
                String title,
                String description,
                RendererFactory factory,
                SkinSettingsProvider settingsProvider) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.factory = factory;
            this.settingsProvider = settingsProvider;
        }

        public boolean hasSettings() {
            return settingsProvider != null;
        }

        public SkinSettings getDefaultSettings() {
            if (settingsProvider == null) {
                return SkinSettings.empty();
            }
            return safeSettings(settingsProvider.getDefaultSettings());
        }

        public SkinSettings normalizeSettings(SkinSettings settings) {
            if (settingsProvider == null) {
                return SkinSettings.empty();
            }
            SkinSettings candidate = settings == null
                    ? getDefaultSettings()
                    : settings;
            SkinSettings normalized = settingsProvider.normalize(candidate);
            return normalized == null ? getDefaultSettings() : normalized;
        }

        public View createSettingsEditor(
                Context context,
                SkinSettings settings,
                SkinSettingsProvider.Listener listener) {
            if (settingsProvider == null) {
                throw new IllegalStateException(
                        "Skin has no settings provider: " + id);
            }
            View editor = settingsProvider.createEditor(
                    context,
                    normalizeSettings(settings),
                    listener);
            if (editor == null) {
                throw new IllegalStateException(
                        "Skin settings provider returned no editor: " + id);
            }
            return editor;
        }

        private View createRenderer(
                Context context,
                SkinSettings settings) {
            return factory.create(context, normalizeSettings(settings));
        }
    }

    private static final Definition[] DEFINITIONS = {
        new Definition(
                CLASSIC,
                "Classic — утверждённый дизайн",
                "Финальный дизайн демо v8 с реальными показаниями автомобиля",
                new RendererFactory() {
                    @Override
                    public View create(
                            Context context,
                            SkinSettings settings) {
                        return new ClassicClusterView(context);
                    }
                },
                null),
        new Definition(
                SPORT,
                "Sport — спортивная тема",
                "Красные асимметричные шкалы и белые указатели скорости и оборотов",
                new RendererFactory() {
                    @Override
                    public View create(
                            Context context,
                            SkinSettings settings) {
                        return new SportClusterView(context);
                    }
                },
                null),
        new Definition(
                SIMPLE,
                "Simple — простая тема",
                "Компактные шкалы восьми цветов со свободными зонами штатных индикаторов",
                new RendererFactory() {
                    @Override
                    public View create(
                            Context context,
                            SkinSettings settings) {
                        return new SimpleClusterView(
                                context,
                                SimpleSettingsProvider.scaleColor(
                                        settings));
                    }
                },
                new SimpleSettingsProvider()),
        new Definition(
                HORIZON,
                "Horizon — базовый скин",
                "Исходный дизайн проекта с подключением к GWM Adapter Service",
                new RendererFactory() {
                    @Override
                    public View create(
                            Context context,
                            SkinSettings settings) {
                        return new HorizonClusterView(context, settings);
                    }
                },
                new HorizonSettingsProvider())
    };

    private SkinRegistry() {
    }

    public static Definition[] getDefinitions() {
        return DEFINITIONS.clone();
    }

    public static String getDefaultId() {
        return CLASSIC;
    }

    public static boolean isSupported(String id) {
        for (Definition definition : DEFINITIONS) {
            if (definition.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public static String normalize(String id) {
        return isSupported(id) ? id : getDefaultId();
    }

    public static Definition getDefinition(String id) {
        String normalizedId = normalize(id);
        for (Definition definition : DEFINITIONS) {
            if (definition.id.equals(normalizedId)) {
                return definition;
            }
        }
        throw new IllegalStateException("Default skin is not registered");
    }

    public static SkinSettings normalizeSettings(
            String id,
            SkinSettings settings) {
        return getDefinition(id).normalizeSettings(settings);
    }

    public static View createRenderer(Context context, String id) {
        return createRenderer(context, id, SkinSettings.empty());
    }

    public static View createRenderer(
            Context context,
            String id,
            SkinSettings settings) {
        Definition definition = getDefinition(id);
        View view = definition.createRenderer(context, settings);
        if (!(view instanceof ClusterRenderer)) {
            throw new IllegalStateException(
                    "Skin renderer must implement ClusterRenderer: " + definition.id);
        }
        return view;
    }

    private static SkinSettings safeSettings(SkinSettings settings) {
        return settings == null ? SkinSettings.empty() : settings;
    }
}
