package com.zszl.zszlScriptMod.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.config.ModConfig;

import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProfileManager {

    private static final Path BASE_DIR = Paths.get(ModConfig.CONFIG_DIR);
    private static final Path PROFILES_DIR = BASE_DIR.resolve("profiles");
    private static final Path MAPPING_FILE = BASE_DIR.resolve("profile_mapping.json");
    public static final String DEFAULT_PROFILE_NAME = "Default";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static String activeProfileName = DEFAULT_PROFILE_NAME;
    private static Map<String, String> profileMappings = new ConcurrentHashMap<>();

    /**
     * 在Mod启动时初始化Profile系统。
     */
    public static void initialize() {
        try {
            Files.createDirectories(PROFILES_DIR);
            ensureDefaultProfileExists();
            loadMappings();

            String currentUser = Minecraft.getMinecraft().getSession().getUsername().toLowerCase();
            activeProfileName = profileMappings.getOrDefault(currentUser, DEFAULT_PROFILE_NAME);

            // 确保用户的配置存在，如果不存在则回退到默认
            if (!profileExists(activeProfileName)) {
                zszlScriptMod.LOGGER.warn("Profile '{}' for user '{}' does not exist; falling back to default profile.",
                        activeProfileName, currentUser);
                activeProfileName = DEFAULT_PROFILE_NAME;
                setActiveProfileForCurrentUser(DEFAULT_PROFILE_NAME);
            }

            zszlScriptMod.LOGGER.info("Profile system initialized. Current user: '{}', active profile: '{}'",
                    currentUser,
                    activeProfileName);
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to initialize profile system", e);
        }
    }

    /**
     * 获取当前激活的配置方案的目录路径。
     * 这是所有Handler读写配置文件的基础。
     * 
     * @return Path to the current profile directory.
     */
    public static Path getCurrentProfileDir() {
        return getProfileDir(activeProfileName);
    }

    public static Path getProfileDir(String profileName) {
        String safeName = (profileName == null || profileName.trim().isEmpty())
                ? DEFAULT_PROFILE_NAME
                : profileName.trim();
        Path profileDir = PROFILES_DIR.resolve(safeName);
        try {
            if (!Files.exists(profileDir)) {
                Files.createDirectories(profileDir);
            }
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to create profile directory: {}", profileDir, e);
        }
        return profileDir;
    }

    public static String getActiveProfileName() {
        return activeProfileName;
    }

    /**
     * 切换当前用户的激活配置，并重新加载所有设置。
     * 
     * @param newProfileName The name of the profile to activate.
     */
    public static void setActiveProfile(String newProfileName) {
        if (!profileExists(newProfileName)) {
            zszlScriptMod.LOGGER.error("Attempted to switch to a non-existent profile: {}", newProfileName);
            return;
        }
        activeProfileName = newProfileName;
        setActiveProfileForCurrentUser(newProfileName);
        zszlScriptMod.LOGGER.info("Switched active profile to: {}", newProfileName);

        // 关键步骤：切换后立即重新加载所有配置
        ModConfig.loadAllConfigs();
    }

    /**
     * 创建一个新的配置方案。
     * 
     * @param profileName The name for the new profile.
     * @return true if successful, false otherwise.
     */
    public static boolean createProfile(String profileName) {
        if (profileName == null || profileName.trim().isEmpty() || profileExists(profileName)) {
            return false;
        }
        try {
            Path newProfileDir = PROFILES_DIR.resolve(profileName);
            Files.createDirectories(newProfileDir);
            // 将默认配置中的所有文件复制到新配置中
            try (Stream<Path> stream = Files.walk(PROFILES_DIR.resolve(DEFAULT_PROFILE_NAME), 1)) {
                stream.filter(path -> !Files.isDirectory(path)).forEach(source -> {
                    try {
                        Files.copy(source, newProfileDir.resolve(source.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to copy default profile file: " + source, e);
                    }
                });
            }
            return true;
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("Failed to create new profile '{}'", profileName, e);
            return false;
        }
    }

    /**
     * 删除一个配置方案。
     * 
     * @param profileName The name of the profile to delete.
     * @return true if successful, false otherwise.
     */
    public static boolean deleteProfile(String profileName) {
        if (DEFAULT_PROFILE_NAME.equals(profileName) || !profileExists(profileName)) {
            return false;
        }
        try {
            Path profileDir = PROFILES_DIR.resolve(profileName);
            try (Stream<Path> walk = Files.walk(profileDir)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
            }

            // 将使用此配置的所有用户重置为默认配置
            profileMappings.entrySet().removeIf(entry -> entry.getValue().equals(profileName));
            saveMappings();

            // 如果当前激活的是被删除的配置，则切换回默认
            if (activeProfileName.equals(profileName)) {
                setActiveProfile(DEFAULT_PROFILE_NAME);
            }
            return true;
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to delete profile '{}'", profileName, e);
            return false;
        }
    }

    public static List<String> getAllProfileNames() {
        try (Stream<Path> stream = Files.list(PROFILES_DIR)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to list all profiles", e);
            return java.util.Collections.singletonList(DEFAULT_PROFILE_NAME);
        }
    }

    private static boolean profileExists(String profileName) {
        return Files.isDirectory(PROFILES_DIR.resolve(profileName));
    }

    private static void ensureDefaultProfileExists() throws IOException {
        Path defaultDir = PROFILES_DIR.resolve(DEFAULT_PROFILE_NAME);
        if (!Files.exists(defaultDir)) {
            Files.createDirectories(defaultDir);
        }
    }

    private static void setActiveProfileForCurrentUser(String profileName) {
        String currentUser = Minecraft.getMinecraft().getSession().getUsername().toLowerCase();
        profileMappings.put(currentUser, profileName);
        saveMappings();
    }

    private static void loadMappings() {
        if (!Files.exists(MAPPING_FILE)) {
            profileMappings = new ConcurrentHashMap<>();
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(MAPPING_FILE, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<ConcurrentHashMap<String, String>>() {
            }.getType();
            profileMappings = GSON.fromJson(reader, type);
            if (profileMappings == null) {
                profileMappings = new ConcurrentHashMap<>();
            }
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to load user-profile mapping file", e);
            profileMappings = new ConcurrentHashMap<>();
        }
    }

    private static void saveMappings() {
        try (BufferedWriter writer = Files.newBufferedWriter(MAPPING_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(profileMappings, writer);
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("Failed to save user-profile mapping file", e);
        }
    }
}
