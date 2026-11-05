package ru.Moon.MoonOptimizer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent mod settings, stored in config/moonoptimizer.json.
 */
public final class MoonSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("moonoptimizer.json");

    private static final int MIN_FPS_THRESHOLD = 15;
    private static final int MAX_FPS_THRESHOLD = 240;

    public String profile = MoonProfile.QUALITY.id();
    public boolean autoBoost = false;
    public int autoBoostFpsThreshold = 45;
    public boolean hudVisible = true;

    /**
     * Forces the vanilla GUI scale to 1 on startup. The custom main menu is
     * scaled independently of the GUI scale, so it keeps the same size either
     * way, while the in-game HUD gains more room.
     */
    public boolean autoGuiScale = true;

    public static MoonSettings load() {
        if (!Files.exists(PATH)) {
            MoonSettings defaults = new MoonSettings();
            defaults.save();
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(PATH)) {
            MoonSettings loaded = GSON.fromJson(reader, MoonSettings.class);
            return loaded == null ? new MoonSettings() : loaded.normalized();
        } catch (Exception exception) {
            MoonOptimizerClient.LOGGER.warn("Could not read {}", PATH, exception);
            return new MoonSettings();
        }
    }

    public MoonProfile profile() {
        return MoonProfile.byId(profile);
    }

    public void setProfile(MoonProfile newProfile) {
        profile = newProfile.id();
    }

    private MoonSettings normalized() {
        profile = MoonProfile.byId(profile).id();
        autoBoostFpsThreshold = Math.max(
                MIN_FPS_THRESHOLD,
                Math.min(MAX_FPS_THRESHOLD, autoBoostFpsThreshold)
        );
        return this;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());

            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception exception) {
            MoonOptimizerClient.LOGGER.warn("Could not write {}", PATH, exception);
        }
    }
}
