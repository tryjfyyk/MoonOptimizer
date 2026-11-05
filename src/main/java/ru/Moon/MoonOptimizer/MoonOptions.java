package ru.Moon.MoonOptimizer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.server.level.ParticleStatus;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Applies optimization presets on top of the player's original video settings.
 * The original values are captured once and restored when the profile is set
 * back to QUALITY or when the game shuts down.
 *
 * <p>Only individual options are touched. Vanilla switches the graphics preset
 * to CUSTOM by itself when one of them changes, so the preset is never written
 * directly (doing so would re-apply a whole bundle of settings).
 *
 * <p>Vanilla writes options.txt whenever the player presses "Done" in the video
 * settings screen, even while a profile is active. To survive that, the original
 * values are also persisted to config/moonoptimizer-baseline.json and reused on
 * the next launch.
 */
public final class MoonOptions {
    private static final Path STORE_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("moonoptimizer-baseline.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Snapshot baseline;
    private static Snapshot lastApplied;

    private MoonOptions() {
    }

    public static void apply(Minecraft client, MoonProfile profile) {
        Options options = client.options;
        Snapshot current = read(options);

        if (baseline == null) {
            if (!loadStore()) {
                if (profile == MoonProfile.QUALITY) {
                    // Nothing was ever changed, so there is nothing to restore.
                    return;
                }

                baseline = current;
                saveStore();
            } else if (lastApplied != null && !lastApplied.equals(current)) {
                // The player edited settings while the mod was not running.
                // Only the fields they actually changed become the new baseline.
                baseline = Snapshot.merge(baseline, lastApplied, current);
                saveStore();
            }
        } else if (lastApplied != null && !lastApplied.equals(current)) {
            // The player changed something manually while a profile was active.
            // Only the edited fields are adopted as the baseline; everything
            // else keeps the original value instead of the boosted one.
            baseline = Snapshot.merge(baseline, lastApplied, current);
            saveStore();
        }

        Snapshot target = target(baseline, profile);
        write(options, target);
        lastApplied = read(options);
        saveStore();

        MoonOptimizerClient.LOGGER.info(
                "Applied profile {}: rd={} sd={} ed={} clouds={} mipmaps={} filtering={} vsync={} fpsLimit={}",
                profile.id(),
                target.renderDistance(),
                target.simulationDistance(),
                target.entityDistanceScaling(),
                target.clouds(),
                target.mipmapLevels(),
                target.textureFiltering(),
                target.vsync(),
                target.framerateLimit()
        );

        if (!current.equals(target)) {
            rebuildChunks(client);
        }
    }

    public static void restore(Minecraft client) {
        if (baseline == null && !loadStore()) {
            return;
        }

        Snapshot current = read(client.options);

        if (lastApplied != null && !lastApplied.equals(current)) {
            baseline = Snapshot.merge(baseline, lastApplied, current);
        }

        write(client.options, baseline);

        if (!current.equals(baseline)) {
            rebuildChunks(client);
        }

        baseline = null;
        lastApplied = null;
        deleteStore();
    }

    private static Snapshot target(Snapshot base, MoonProfile profile) {
        int renderDistance = base.renderDistance();
        int simulationDistance = base.simulationDistance();
        double entityDistance = base.entityDistanceScaling();
        int biomeBlend = base.biomeBlendRadius();
        CloudStatus clouds = base.clouds();
        int cloudRange = base.cloudRange();
        int weatherRadius = base.weatherRadius();
        ParticleStatus particles = base.particles();
        boolean entityShadows = base.entityShadows();
        boolean ambientOcclusion = base.ambientOcclusion();
        boolean cutoutLeaves = base.cutoutLeaves();
        boolean improvedTransparency = base.improvedTransparency();
        boolean vignette = base.vignette();
        int mipmaps = base.mipmapLevels();
        TextureFilteringMethod textureFiltering = base.textureFiltering();
        double chunkFadeIn = base.chunkSectionFadeInTime();
        PrioritizeChunkUpdates chunkUpdates = base.prioritizeChunkUpdates();
        boolean vsync = base.vsync();
        int framerateLimit = base.framerateLimit();

        switch (profile) {
            case BALANCED -> {
                renderDistance = Math.min(renderDistance, 12);
                simulationDistance = Math.min(simulationDistance, 8);
                entityDistance = Math.min(entityDistance, 0.75);
                biomeBlend = Math.min(biomeBlend, 2);
                weatherRadius = Math.min(weatherRadius, 8);
                cloudRange = Math.min(cloudRange, 96);
                chunkUpdates = PrioritizeChunkUpdates.NEARBY;

                if (clouds == CloudStatus.FANCY) {
                    clouds = CloudStatus.FAST;
                }

                if (particles == ParticleStatus.ALL) {
                    particles = ParticleStatus.DECREASED;
                }
            }
            case PERFORMANCE -> {
                renderDistance = Math.min(renderDistance, 8);
                simulationDistance = Math.min(simulationDistance, 5);
                entityDistance = Math.min(entityDistance, 0.5);
                biomeBlend = 0;
                clouds = CloudStatus.OFF;
                cloudRange = 32;
                weatherRadius = 4;
                particles = ParticleStatus.MINIMAL;
                entityShadows = false;
                ambientOcclusion = false;
                cutoutLeaves = false;
                improvedTransparency = false;
                vignette = false;
                mipmaps = Math.min(mipmaps, 2);
                textureFiltering = TextureFilteringMethod.NONE;
                chunkFadeIn = 0.0;
                chunkUpdates = PrioritizeChunkUpdates.NEARBY;
                vsync = false;
            }
            case EXTREME -> {
                renderDistance = Math.min(renderDistance, 5);
                simulationDistance = Math.min(simulationDistance, 5);
                entityDistance = 0.5;
                biomeBlend = 0;
                clouds = CloudStatus.OFF;
                cloudRange = 2;
                weatherRadius = 3;
                particles = ParticleStatus.MINIMAL;
                entityShadows = false;
                ambientOcclusion = false;
                cutoutLeaves = false;
                improvedTransparency = false;
                vignette = false;
                mipmaps = 0;
                textureFiltering = TextureFilteringMethod.NONE;
                chunkFadeIn = 0.0;
                chunkUpdates = PrioritizeChunkUpdates.NEARBY;
                vsync = false;
                framerateLimit = Options.UNLIMITED_FRAMERATE_CUTOFF;
            }
            case QUALITY -> {
                // baseline is used as-is
            }
        }

        return new Snapshot(
                renderDistance,
                simulationDistance,
                entityDistance,
                biomeBlend,
                clouds,
                cloudRange,
                weatherRadius,
                particles,
                entityShadows,
                ambientOcclusion,
                cutoutLeaves,
                improvedTransparency,
                vignette,
                mipmaps,
                textureFiltering,
                chunkFadeIn,
                chunkUpdates,
                vsync,
                framerateLimit
        );
    }

    private static void rebuildChunks(Minecraft client) {
        if (client.level != null) {
            client.levelRenderer.allChanged();
        }
    }

    private static Snapshot read(Options options) {
        return new Snapshot(
                options.renderDistance().get(),
                options.simulationDistance().get(),
                options.entityDistanceScaling().get(),
                options.biomeBlendRadius().get(),
                options.cloudStatus().get(),
                options.cloudRange().get(),
                options.weatherRadius().get(),
                options.particles().get(),
                options.entityShadows().get(),
                options.ambientOcclusion().get(),
                options.cutoutLeaves().get(),
                options.improvedTransparency().get(),
                options.vignette().get(),
                options.mipmapLevels().get(),
                options.textureFiltering().get(),
                options.chunkSectionFadeInTime().get(),
                options.prioritizeChunkUpdates().get(),
                options.enableVsync().get(),
                options.framerateLimit().get()
        );
    }

    private static void write(Options options, Snapshot snapshot) {
        options.renderDistance().set(snapshot.renderDistance());
        options.simulationDistance().set(snapshot.simulationDistance());
        options.entityDistanceScaling().set(snapshot.entityDistanceScaling());
        options.biomeBlendRadius().set(snapshot.biomeBlendRadius());
        options.cloudStatus().set(snapshot.clouds());
        options.cloudRange().set(snapshot.cloudRange());
        options.weatherRadius().set(snapshot.weatherRadius());
        options.particles().set(snapshot.particles());
        options.entityShadows().set(snapshot.entityShadows());
        options.ambientOcclusion().set(snapshot.ambientOcclusion());
        options.cutoutLeaves().set(snapshot.cutoutLeaves());
        options.improvedTransparency().set(snapshot.improvedTransparency());
        options.vignette().set(snapshot.vignette());
        options.mipmapLevels().set(snapshot.mipmapLevels());
        options.textureFiltering().set(snapshot.textureFiltering());
        options.chunkSectionFadeInTime().set(snapshot.chunkSectionFadeInTime());
        options.prioritizeChunkUpdates().set(snapshot.prioritizeChunkUpdates());
        options.enableVsync().set(snapshot.vsync());
        options.framerateLimit().set(snapshot.framerateLimit());
    }

    private record Snapshot(
            int renderDistance,
            int simulationDistance,
            double entityDistanceScaling,
            int biomeBlendRadius,
            CloudStatus clouds,
            int cloudRange,
            int weatherRadius,
            ParticleStatus particles,
            boolean entityShadows,
            boolean ambientOcclusion,
            boolean cutoutLeaves,
            boolean improvedTransparency,
            boolean vignette,
            int mipmapLevels,
            TextureFilteringMethod textureFiltering,
            double chunkSectionFadeInTime,
            PrioritizeChunkUpdates prioritizeChunkUpdates,
            boolean vsync,
            int framerateLimit
    ) {
        /**
         * Keeps the original baseline for every option the player did not touch,
         * while adopting the values they edited while a profile was active.
         */
        static Snapshot merge(Snapshot base, Snapshot applied, Snapshot current) {
            return new Snapshot(
                    current.renderDistance() != applied.renderDistance()
                            ? current.renderDistance() : base.renderDistance(),
                    current.simulationDistance() != applied.simulationDistance()
                            ? current.simulationDistance() : base.simulationDistance(),
                    current.entityDistanceScaling() != applied.entityDistanceScaling()
                            ? current.entityDistanceScaling() : base.entityDistanceScaling(),
                    current.biomeBlendRadius() != applied.biomeBlendRadius()
                            ? current.biomeBlendRadius() : base.biomeBlendRadius(),
                    current.clouds() != applied.clouds()
                            ? current.clouds() : base.clouds(),
                    current.cloudRange() != applied.cloudRange()
                            ? current.cloudRange() : base.cloudRange(),
                    current.weatherRadius() != applied.weatherRadius()
                            ? current.weatherRadius() : base.weatherRadius(),
                    current.particles() != applied.particles()
                            ? current.particles() : base.particles(),
                    current.entityShadows() != applied.entityShadows()
                            ? current.entityShadows() : base.entityShadows(),
                    current.ambientOcclusion() != applied.ambientOcclusion()
                            ? current.ambientOcclusion() : base.ambientOcclusion(),
                    current.cutoutLeaves() != applied.cutoutLeaves()
                            ? current.cutoutLeaves() : base.cutoutLeaves(),
                    current.improvedTransparency() != applied.improvedTransparency()
                            ? current.improvedTransparency() : base.improvedTransparency(),
                    current.vignette() != applied.vignette()
                            ? current.vignette() : base.vignette(),
                    current.mipmapLevels() != applied.mipmapLevels()
                            ? current.mipmapLevels() : base.mipmapLevels(),
                    current.textureFiltering() != applied.textureFiltering()
                            ? current.textureFiltering() : base.textureFiltering(),
                    current.chunkSectionFadeInTime() != applied.chunkSectionFadeInTime()
                            ? current.chunkSectionFadeInTime() : base.chunkSectionFadeInTime(),
                    current.prioritizeChunkUpdates() != applied.prioritizeChunkUpdates()
                            ? current.prioritizeChunkUpdates() : base.prioritizeChunkUpdates(),
                    current.vsync() != applied.vsync()
                            ? current.vsync() : base.vsync(),
                    current.framerateLimit() != applied.framerateLimit()
                            ? current.framerateLimit() : base.framerateLimit()
            );
        }
    }

    private static final class StoredState {
        private SnapshotDto baseline;
        private SnapshotDto lastApplied;
    }

    private static final class SnapshotDto {
        private int renderDistance;
        private int simulationDistance;
        private double entityDistanceScaling;
        private int biomeBlendRadius;
        private String clouds;
        private int cloudRange;
        private int weatherRadius;
        private String particles;
        private boolean entityShadows;
        private boolean ambientOcclusion;
        private boolean cutoutLeaves;
        private boolean improvedTransparency;
        private boolean vignette;
        private int mipmapLevels;
        private String textureFiltering;
        private double chunkSectionFadeInTime;
        private String prioritizeChunkUpdates;
        private boolean vsync;
        private int framerateLimit;
    }

    private static boolean loadStore() {
        if (!Files.exists(STORE_PATH)) {
            return false;
        }

        try (Reader reader = Files.newBufferedReader(STORE_PATH)) {
            StoredState state = GSON.fromJson(reader, StoredState.class);

            if (state == null || state.baseline == null) {
                return false;
            }

            baseline = fromDto(state.baseline);
            lastApplied = state.lastApplied == null ? null : fromDto(state.lastApplied);
            return true;
        } catch (Exception exception) {
            MoonOptimizerClient.LOGGER.warn("Could not read {}", STORE_PATH, exception);
            return false;
        }
    }

    private static void saveStore() {
        if (baseline == null) {
            return;
        }

        StoredState state = new StoredState();
        state.baseline = toDto(baseline);
        state.lastApplied = lastApplied == null ? null : toDto(lastApplied);

        try {
            Files.createDirectories(STORE_PATH.getParent());

            try (Writer writer = Files.newBufferedWriter(STORE_PATH)) {
                GSON.toJson(state, writer);
            }
        } catch (Exception exception) {
            MoonOptimizerClient.LOGGER.warn("Could not write {}", STORE_PATH, exception);
        }
    }

    private static void deleteStore() {
        try {
            Files.deleteIfExists(STORE_PATH);
        } catch (Exception exception) {
            MoonOptimizerClient.LOGGER.warn("Could not delete {}", STORE_PATH, exception);
        }
    }

    private static SnapshotDto toDto(Snapshot snapshot) {
        SnapshotDto dto = new SnapshotDto();
        dto.renderDistance = snapshot.renderDistance();
        dto.simulationDistance = snapshot.simulationDistance();
        dto.entityDistanceScaling = snapshot.entityDistanceScaling();
        dto.biomeBlendRadius = snapshot.biomeBlendRadius();
        dto.clouds = snapshot.clouds().name();
        dto.cloudRange = snapshot.cloudRange();
        dto.weatherRadius = snapshot.weatherRadius();
        dto.particles = snapshot.particles().name();
        dto.entityShadows = snapshot.entityShadows();
        dto.ambientOcclusion = snapshot.ambientOcclusion();
        dto.cutoutLeaves = snapshot.cutoutLeaves();
        dto.improvedTransparency = snapshot.improvedTransparency();
        dto.vignette = snapshot.vignette();
        dto.mipmapLevels = snapshot.mipmapLevels();
        dto.textureFiltering = snapshot.textureFiltering().name();
        dto.chunkSectionFadeInTime = snapshot.chunkSectionFadeInTime();
        dto.prioritizeChunkUpdates = snapshot.prioritizeChunkUpdates().name();
        dto.vsync = snapshot.vsync();
        dto.framerateLimit = snapshot.framerateLimit();
        return dto;
    }

    private static Snapshot fromDto(SnapshotDto dto) {
        return new Snapshot(
                dto.renderDistance,
                dto.simulationDistance,
                dto.entityDistanceScaling,
                dto.biomeBlendRadius,
                enumOrDefault(CloudStatus.class, dto.clouds, CloudStatus.FANCY),
                dto.cloudRange,
                dto.weatherRadius,
                enumOrDefault(ParticleStatus.class, dto.particles, ParticleStatus.ALL),
                dto.entityShadows,
                dto.ambientOcclusion,
                dto.cutoutLeaves,
                dto.improvedTransparency,
                dto.vignette,
                dto.mipmapLevels,
                enumOrDefault(TextureFilteringMethod.class, dto.textureFiltering, TextureFilteringMethod.RGSS),
                dto.chunkSectionFadeInTime,
                enumOrDefault(PrioritizeChunkUpdates.class, dto.prioritizeChunkUpdates, PrioritizeChunkUpdates.PLAYER_AFFECTED),
                dto.vsync,
                dto.framerateLimit
        );
    }

    private static <E extends Enum<E>> E enumOrDefault(Class<E> type, String name, E fallback) {
        if (name == null) {
            return fallback;
        }

        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
