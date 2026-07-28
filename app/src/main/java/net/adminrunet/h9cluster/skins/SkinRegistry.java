package net.adminrunet.h9cluster.skins;

import net.adminrunet.h9cluster.ClusterRenderer;
import net.adminrunet.h9cluster.skins.classic.ClassicClusterView;
import net.adminrunet.h9cluster.skins.horizon.HorizonClusterView;
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
    public static final String SPORT = "sport";

    private interface RendererFactory {
        View create(Context context);
    }

    public static final class Definition {
        public final String id;
        public final String title;
        public final String description;
        private final RendererFactory factory;

        private Definition(
                String id,
                String title,
                String description,
                RendererFactory factory) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.factory = factory;
        }
    }

    private static final Definition[] DEFINITIONS = {
        new Definition(
                CLASSIC,
                "Classic — утверждённый дизайн",
                "Финальный дизайн демо v8 с реальными показаниями автомобиля",
                new RendererFactory() {
                    @Override
                    public View create(Context context) {
                        return new ClassicClusterView(context);
                    }
                }),
        new Definition(
                SPORT,
                "Sport — спортивная тема",
                "Красные асимметричные шкалы и белые указатели скорости и оборотов",
                new RendererFactory() {
                    @Override
                    public View create(Context context) {
                        return new SportClusterView(context);
                    }
                }),
        new Definition(
                HORIZON,
                "Horizon — базовый скин",
                "Исходный дизайн проекта с подключением к GWM Adapter Service",
                new RendererFactory() {
                    @Override
                    public View create(Context context) {
                        return new HorizonClusterView(context);
                    }
                })
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

    public static View createRenderer(Context context, String id) {
        String normalizedId = normalize(id);
        for (Definition definition : DEFINITIONS) {
            if (definition.id.equals(normalizedId)) {
                View view = definition.factory.create(context);
                if (!(view instanceof ClusterRenderer)) {
                    throw new IllegalStateException(
                            "Skin renderer must implement ClusterRenderer: " + definition.id);
                }
                return view;
            }
        }
        throw new IllegalStateException("Default skin is not registered");
    }
}
