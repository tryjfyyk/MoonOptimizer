package ru.Moon.MoonOptimizer;

/**
 * Optimization presets, ordered from best visual quality to best performance.
 * QUALITY always restores the player's own settings.
 */
public enum MoonProfile {
    QUALITY("quality"),
    BALANCED("balanced"),
    PERFORMANCE("performance"),
    EXTREME("extreme");

    private final String id;

    MoonProfile(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "moonoptimizer.profile." + id;
    }

    public MoonProfile next() {
        MoonProfile[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static MoonProfile byId(String id) {
        if (id != null) {
            for (MoonProfile profile : values()) {
                if (profile.id.equalsIgnoreCase(id)) {
                    return profile;
                }
            }
        }

        return QUALITY;
    }
}
