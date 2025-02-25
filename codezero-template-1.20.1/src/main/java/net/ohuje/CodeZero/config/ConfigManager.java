package net.ohuje.CodeZero.config;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Менеджер конфигурации для мода CodeZero.
 */
public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "codezero_config.json";
    private static final Path CONFIG_DIR = Paths.get("config");




    // Настройки для мобов
    public static final Set<String> MOB_BLACKLIST = new HashSet<>();
    public static float BUILDING_CHANCE = 0.6f;
    public static int BLOCKS_MIN = 6;
    public static int BLOCKS_MAX = 12;


    public static float INFORMATOR_CHANCE = 0f;
    public static float INFORMATOR_BROADCAST_RADIUS = 16f;

    public static int HORDE_WAVE_COUNT = 3; // Количество волн за ночь



    // Новые параметры

    public static int JUDGMENT_WAVE_COUNT = 18;
    public static int MAX_JUDGMENT_MOBS = 350; // Максимальное количество мобов в судную ночь
    public static int HORDE_WAVE_MIN_MOBS = 1;   // Минимальное количество мобов в волне
    public static int HORDE_WAVE_MAX_MOBS = 3; // Максимальное количество мобов в волне
    public static int JUDGMENT_WAVE_MIN_MOBS = 2; // Минимальное количество мобов в волне (судная ночь)
    public static int JUDGMENT_WAVE_MAX_MOBS = 4; // Максимальное количество мобов в волне (судная ночь)
    public static int JUDGMENT_NIGHT_MIN = 8;
    public static int JUDGMENT_NIGHT_MAX = 15;
    public static int JUDGMENT_AGGRO_DISTANCE = 180;
    public static int HORDE_GROUP_INTERVAL = 0;     // Интервал между группами в секундах
    public static int HORDE_SPAWN_DISTANCE_MIN = 70; // Минимальная дистанция спавна от игрока
    public static int HORDE_SPAWN_DISTANCE_MAX = 120; // Максимальная дистанция спавна от игрока
    public static int HORDE_CAVE_SPAWN_DISTANCE_MIN = 40; // Минимальная дистанция спавна в пещере
    public static int HORDE_CAVE_SPAWN_DISTANCE_MAX = 60; // Максимальная дистанция спавна в пещере

    public static void loadConfig() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            Path filePath = CONFIG_DIR.resolve(CONFIG_FILE_NAME);

            if (!Files.exists(filePath)) {
                createDefaultConfig(filePath);
            }

            try (Reader reader = Files.newBufferedReader(filePath)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null) {
                    System.err.println("[CodeZero] Config file is empty or invalid JSON.");
                    return;
                }

                // mob_blacklist
                if (root.has("mob_blacklist")) {
                    JsonArray arr = root.getAsJsonArray("mob_blacklist");
                    for (JsonElement el : arr) {
                        MOB_BLACKLIST.add(el.getAsString());
                    }
                }

                // building_chance
                if (root.has("building_chance")) {
                    BUILDING_CHANCE = root.get("building_chance").getAsFloat();
                }
                // blocks_min
                if (root.has("blocks_min")) {
                    BLOCKS_MIN = root.get("blocks_min").getAsInt();
                }
                // blocks_max
                if (root.has("blocks_max")) {
                    BLOCKS_MAX = root.get("blocks_max").getAsInt();
                }
                // informator_chance
                if (root.has("informator_chance")) {
                    INFORMATOR_CHANCE = root.get("informator_chance").getAsFloat();
                }
                // informator_broadcast_radius
                if (root.has("informator_broadcast_radius")) {
                    INFORMATOR_BROADCAST_RADIUS = root.get("informator_broadcast_radius").getAsFloat();
                }

                // Новые параметры для механики орды
                if (root.has("judgment_wave_count")) {
                    JUDGMENT_WAVE_COUNT = root.get("judgment_wave_count").getAsInt();
                }

                if (root.has("judgment_aggro_distance")) {
                    JUDGMENT_AGGRO_DISTANCE = root.get("judgment_aggro_distance").getAsInt();
                }

                if (root.has("max_judgment_mobs")) {
                    MAX_JUDGMENT_MOBS = root.get("max_judgment_mobs").getAsInt();
                }

                if (root.has("horde_wave_min_mobs")) {
                    HORDE_WAVE_MIN_MOBS = root.get("horde_wave_min_mobs").getAsInt();
                }
                if (root.has("horde_wave_max_mobs")) {
                    HORDE_WAVE_MAX_MOBS = root.get("horde_wave_max_mobs").getAsInt();
                }
                if (root.has("judgment_wave_min_mobs")) {
                    JUDGMENT_WAVE_MIN_MOBS = root.get("judgment_wave_min_mobs").getAsInt();
                }
                if (root.has("judgment_wave_max_mobs")) {
                    JUDGMENT_WAVE_MAX_MOBS = root.get("judgment_wave_max_mobs").getAsInt();
                }
                if (root.has("judgment_night_min_interval")) {
                    JUDGMENT_NIGHT_MIN = root.get("judgment_night_min_interval").getAsInt();
                }
                if (root.has("judgment_night_max_interval")) {
                    JUDGMENT_NIGHT_MAX = root.get("judgment_night_max_interval").getAsInt();
                }
                if (root.has("horde_group_interval")) {
                    HORDE_GROUP_INTERVAL = root.get("horde_group_interval").getAsInt();
                }
                if (root.has("horde_spawn_distance_min")) {
                    HORDE_SPAWN_DISTANCE_MIN = root.get("horde_spawn_distance_min").getAsInt();
                }
                if (root.has("horde_spawn_distance_max")) {
                    HORDE_SPAWN_DISTANCE_MAX = root.get("horde_spawn_distance_max").getAsInt();
                }
                if (root.has("horde_cave_spawn_distance_min")) {
                    HORDE_CAVE_SPAWN_DISTANCE_MIN = root.get("horde_cave_spawn_distance_min").getAsInt();
                }
                if (root.has("horde_cave_spawn_distance_max")) {
                    HORDE_CAVE_SPAWN_DISTANCE_MAX = root.get("horde_cave_spawn_distance_max").getAsInt();
                }

                System.out.println("[CodeZero] Config loaded successfully!");
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[CodeZero] Failed to load config!");
        }
    }

    private static void createDefaultConfig(Path filePath) {
        JsonObject root = new JsonObject();

        // Настройки по умолчанию
        JsonArray blacklist = new JsonArray();
        blacklist.add("minecraft:creeper");
        blacklist.add("minecraft:enderman");
        blacklist.add("minecraft:ghast");
        blacklist.add("minecraft:zombified_piglin");
        blacklist.add("minecraft:piglin");
        blacklist.add("minecraft:skeleton");
        blacklist.add("minecraft:stray");
        blacklist.add("minecraft:wither_skeleton");
        blacklist.add("minecraft:skeleton_horse");
        blacklist.add("minecraft:spider");

        root.add("mob_blacklist", blacklist);
        root.addProperty("building_chance", 0.6f);
        root.addProperty("blocks_min", 6);
        root.addProperty("blocks_max", 12);

        // Новые настройки
        root.addProperty("judgment_aggro_distance", 180);
        root.addProperty("judgment_wave_count", 25);
        root.addProperty("max_judgment_mobs", 250);
        root.addProperty("horde_wave_min_mobs", 1);
        root.addProperty("horde_wave_max_mobs", 3);
        root.addProperty("judgment_wave_min_mobs", 3);
        root.addProperty("judgment_wave_max_mobs", 5);
        root.addProperty("judgment_night_min_interval", 8);
        root.addProperty("judgment_night_max_interval", 15);
        root.addProperty("horde_group_interval", 0);
        root.addProperty("horde_spawn_distance_min", 80);
        root.addProperty("horde_spawn_distance_max", 120);
        root.addProperty("horde_cave_spawn_distance_min", 40);
        root.addProperty("horde_cave_spawn_distance_max", 60);

        try (Writer writer = Files.newBufferedWriter(filePath)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("[CodeZero] Created default config file: " + filePath);
    }
}