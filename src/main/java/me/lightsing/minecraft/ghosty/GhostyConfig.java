package me.lightsing.minecraft.ghosty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import me.lightsing.minecraft.ghosty.gson.Vec3Adapter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class GhostyConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(Ghosty.MOD_ID);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Vec3.class, new Vec3Adapter())
            .create();
    private static GhostyConfig INSTANCE;

    @Expose
    private TrackingParameters tracking = new TrackingParameters();
    @Expose
    private boolean shouldDespawn = false;
    @Expose
    private CacheSettings cache = new CacheSettings();

    public static GhostyConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    private static GhostyConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configPath = configDir.resolve("ghosty.json");
        GhostyConfig config = new GhostyConfig();
        boolean saveNeeded = false;

        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                config = GSON.fromJson(reader, GhostyConfig.class);
                if (config == null) {
                    LOGGER.warn("Config file empty, using defaults.");
                    config = new GhostyConfig();
                    saveNeeded = true;
                }
            } catch (IOException e) {
                LOGGER.error("Failed to read config, using defaults.", e);
                config = new GhostyConfig();
                saveNeeded = true;
            }
        } else {
            saveNeeded = true;
            LOGGER.info("Config file not found, creating default.");
        }

        if (saveNeeded) {
            save(config, configPath);
        }

        config.cacheValues();

        return config;
    }

    private static void save(GhostyConfig config, Path path) {
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    public TrackingParameters trackingParameters() {
        return tracking;
    }

    public boolean isShouldDespawn() {
        return shouldDespawn;
    }

    public CacheSettings cacheSettings() {
        return cache;
    }

    private void cacheValues() {
        this.tracking.cacheValues();
        this.cache.cacheValues();
    }

    public static class TrackingParameters {
        private static final double[] DEFAULT_RAYCAST_SAMPLE_X = {0.5, 0.1, 0.9, 0.1, 0.9, 0.5, 0.5, 0.5, 0.5};
        private static final double[] DEFAULT_RAYCAST_SAMPLE_Y = {0.5, 0.1, 0.1, 0.9, 0.9, 1.0, 0.0, 0.5, 0.5};
        private static final double[] DEFAULT_RAYCAST_SAMPLE_Z = {0.5, 0.1, 0.1, 0.9, 0.9, 0.5, 0.5, 0.1, 0.9};
        @Expose
        private RangeParameters range = new RangeParameters();
        @Expose
        private FovParameters fov = new FovParameters();
        @Expose
        private List<Vec3> rayCastSamples = new java.util.ArrayList<>();

        public TrackingParameters() {
            for (int i = 0; i < DEFAULT_RAYCAST_SAMPLE_X.length; i++) {
                rayCastSamples.add(new Vec3(
                        DEFAULT_RAYCAST_SAMPLE_X[i],
                        DEFAULT_RAYCAST_SAMPLE_Y[i],
                        DEFAULT_RAYCAST_SAMPLE_Z[i]
                ));
            }
        }

        public RangeParameters rangeParameters() {
            return range;
        }

        public FovParameters fovParameters() {
            return fov;
        }

        public List<Vec3> rayCastSamples() {
            return List.copyOf(rayCastSamples);
        }

        private void cacheValues() {
            this.range.cacheValues();
            this.fov.cacheValues();
            this.rayCastSamples = Collections.unmodifiableList(rayCastSamples);
        }

        public static class RangeParameters {
            @Expose
            private float bypassFovCheckMaxDistance = 6.0f;
            @Expose
            private float enterDistance = 8.0f;
            @Expose
            private float exitDistance = 12.0f;


            private transient float bypassFovDistanceSq;
            private transient float enterDistanceSq;
            private transient float exitDistanceSq;

            public float bypassFovDistanceSq() {
                return bypassFovDistanceSq;
            }

            public float enterDistanceSq() {
                return enterDistanceSq;
            }

            public float exitDistanceSq() {
                return exitDistanceSq;
            }

            private void cacheValues() {
                this.bypassFovDistanceSq = bypassFovCheckMaxDistance * bypassFovCheckMaxDistance;
                this.enterDistanceSq = enterDistance * enterDistance;
                this.exitDistanceSq = exitDistance * exitDistance;
            }
        }

        public static class FovParameters {
            @Expose
            private float enterAngle = 45.0f;
            @Expose
            private float exitAngle = 60.0f;

            private transient double enterAngleCos;
            private transient double exitAngleCos;

            public double enterAngleCos() {
                return enterAngleCos;
            }

            public double exitAngleCos() {
                return exitAngleCos;
            }

            private void cacheValues() {
                this.enterAngleCos = Math.cos(Math.toRadians(enterAngle));
                this.exitAngleCos = Math.cos(Math.toRadians(exitAngle));
            }
        }
    }

    public static class CacheSettings {
        @Expose
        private int forceRefreshNTicks = 5;
        @Expose
        private ByPlayer byPlayer = new ByPlayer();
        @Expose
        private ByTarget byTarget = new ByTarget();

        public int forceRefreshNTicks() {
            return forceRefreshNTicks;
        }

        public ByPlayer byPlayer() {
            return byPlayer;
        }

        public ByTarget byTarget() {
            return byTarget;
        }

        private void cacheValues() {
            this.byTarget.cacheValues();
        }

        public static class ByPlayer {
            @Expose
            private double yawChangedThreshold = 10.0;
            @Expose
            private double pitchChangedThreshold = 10.0;

            public double yawChangedThreshold() {
                return yawChangedThreshold;
            }

            public double pitchChangedThreshold() {
                return pitchChangedThreshold;
            }
        }

        public static class ByTarget {
            @Expose
            private double positionMovedThreshold = 1.0;

            private transient double positionMovedThresholdSq;

            public double positionMovedThresholdSq() {
                return positionMovedThresholdSq;
            }

            private void cacheValues() {
                this.positionMovedThresholdSq = positionMovedThreshold * positionMovedThreshold;
            }
        }
    }
}
