package net.adminrunet.h9cluster;

/** Stable order, grouping and user-facing names for dashboard visibility controls. */
final class SettingsCatalog {
    enum Group {
        MAIN,
        TOP,
        BOTTOM
    }

    static final class Option {
        final BlockVisibility.Block block;
        final Group group;
        final String label;

        Option(BlockVisibility.Block block, Group group, String label) {
            this.block = block;
            this.group = group;
            this.label = label;
        }
    }

    private static final Option[] OPTIONS = {
            new Option(BlockVisibility.Block.SPEEDOMETER, Group.MAIN, "Спидометр"),
            new Option(BlockVisibility.Block.TACHOMETER, Group.MAIN, "Тахометр"),
            new Option(BlockVisibility.Block.FUEL_AND_RANGE, Group.MAIN, "Топливо и запас хода"),
            new Option(BlockVisibility.Block.ENGINE_TEMPERATURE, Group.MAIN, "Температура двигателя"),
            new Option(BlockVisibility.Block.WHEEL_SPEEDS, Group.TOP, "Скорости колёс"),
            new Option(BlockVisibility.Block.ENGINE_TORQUE, Group.TOP, "Крутящий момент"),
            new Option(BlockVisibility.Block.CLOCK, Group.TOP, "Часы"),
            new Option(BlockVisibility.Block.STEERING_ANGLE, Group.TOP, "Угол руля"),
            new Option(BlockVisibility.Block.OUTSIDE_TEMPERATURE, Group.TOP, "Наружная температура"),
            new Option(BlockVisibility.Block.ATF_TEMPERATURE, Group.TOP, "Температура масла АКПП"),
            new Option(BlockVisibility.Block.CURRENT_GEAR, Group.TOP, "Текущая передача"),
            new Option(BlockVisibility.Block.ODOMETERS, Group.BOTTOM, "Пробег"),
            new Option(BlockVisibility.Block.FUEL_CONSUMPTION, Group.BOTTOM, "Расход топлива"),
            new Option(BlockVisibility.Block.TYRE_PRESSURE, Group.BOTTOM, "Давление в шинах"),
            new Option(BlockVisibility.Block.BATTERY_VOLTAGE, Group.BOTTOM, "Напряжение аккумулятора")
    };

    private SettingsCatalog() {
    }

    static Option[] options() {
        return OPTIONS.clone();
    }
}
