package com.mythos.mythosScriptMod.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mythos.mythosScriptMod.mythosScriptMod;
import com.mythos.mythosScriptMod.config.ModConfig;

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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProfileManager {

    private static final Path BASE_DIR = Paths.get(ModConfig.CONFIG_DIR);
    private static final Path PROFILES_DIR = BASE_DIR.resolve("profiles");
    private static final Path MAPPING_FILE = BASE_DIR.resolve("profile_mapping.json");
    public static final String DEFAULT_PROFILE_NAME = "Default";
    private static final String SESSION_DIR_NAME = "_session";
    private static final long MAX_SEED_BYTES = 1024L * 1024L;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static String activeProfileName = DEFAULT_PROFILE_NAME;
    private static Map<String, String> profileMappings = new ConcurrentHashMap<>();
    /** 显式指定的配置 owner（null 表示按当前线程的 Nexus 会话解析）。 */
    private static final ThreadLocal<String> CONFIG_OWNER = new ThreadLocal<>();
    /**
     * 各模块首次为某个会话读盘建配置对象时共用这一把锁：读盘中会访问别的模块的 cfg()，
     * 每个模块各锁各的话，两个 bot 线程交叉加载会互相等待（死锁）。
     */
    public static final Object CONFIG_LOAD_LOCK = new Object();

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
                mythosScriptMod.LOGGER.warn("Profile '{}' for user '{}' does not exist; falling back to default profile.",
                        activeProfileName, currentUser);
                activeProfileName = DEFAULT_PROFILE_NAME;
                setActiveProfileForCurrentUser(DEFAULT_PROFILE_NAME);
            }

            mythosScriptMod.LOGGER.info("Profile system initialized. Current user: '{}', active profile: '{}'",
                    currentUser,
                    activeProfileName);
        } catch (IOException e) {
            mythosScriptMod.LOGGER.error("Failed to initialize profile system", e);
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
            mythosScriptMod.LOGGER.error("Failed to create profile directory: {}", profileDir, e);
        }
        return profileDir;
    }

    /**
     * Per-session-owner runtime config dir under the active profile —
     * {@code profiles/<profile>/_runtime/<owner>/} where {@code owner} is the
     * Nexus session key ({@code ""} resolves to {@code _default}). Handler
     * enabled-state, activeRule and other live flags that must not leak
     * across bots go here; shared numeric/visual config stays in the
     * profile dir itself.
     */
    public static Path getCurrentRuntimeProfileDir() {
        String safe = sessionDirName(currentConfigOwner());
        Path dir = getProfileDir(getActiveProfileName()).resolve("_runtime").resolve(safe);
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            mythosScriptMod.LOGGER.error("Failed to create runtime profile directory: {}", dir, e);
        }
        return dir;
    }

    public static String getActiveProfileName() {
        return activeProfileName;
    }

    /**
     * 按会话隔离的配置目录 —— 除序列配置外的所有配置参数都读写这里。
     * 人类会话（无 Nexus 上下文，owner 为空）沿用 profile 目录本身，因此单开一个
     * 客户端时文件位置与旧版本完全一致；bot 会话落到
     * {@code profiles/<profile>/_session/<owner>/}，各自独立、互不覆盖。
     */
    public static Path getCurrentConfigDir() {
        String owner = currentConfigOwner();
        if (owner.trim().isEmpty()) {
            return getCurrentProfileDir();
        }
        return getConfigDirFor(owner);
    }

    /** 指定会话的配置目录，不存在则创建。 */
    public static Path getConfigDirFor(String owner) {
        Path dir = getProfileDir(activeProfileName).resolve(SESSION_DIR_NAME).resolve(sessionDirName(owner));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            mythosScriptMod.LOGGER.error("Failed to create session config directory: {}", dir, e);
        }
        return dir;
    }

    /**
     * 当前会话下的配置文件路径，供各 handler 的 getConfigFile() 使用。
     * 首次访问某个会话时，用 profile 目录里的同名文件做基线拷贝：老配置成为每个
     * 会话的起点，之后各写各的。序列/UI 等共享配置不走这里。
     */
    public static Path currentConfigFile(String fileName) {
        String owner = currentConfigOwner();
        if (owner.trim().isEmpty()) {
            return getCurrentProfileDir().resolve(fileName);
        }
        Path target = getConfigDirFor(owner).resolve(fileName);
        seedSessionConfigFile(target);
        return target;
    }

    /**
     * 首次访问某会话时用 profile 目录的同名文件做基线拷贝：老配置成为该会话的起点，
     * 之后各写各的。只在文件确实缺失时才拷贝，避免覆盖该会话自己存过的值。
     */
    private static void seedSessionConfigFile(Path target) {
        if (Files.exists(target)) {
            return;
        }
        Path source = getCurrentProfileDir().resolve(target.getFileName().toString());
        if (!Files.isRegularFile(source)) {
            return;
        }
        try {
            if (Files.size(source) > MAX_SEED_BYTES) {
                mythosScriptMod.LOGGER.warn("Session config baseline too large, skipped: {}", source);
                return;
            }
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            mythosScriptMod.LOGGER.warn("Failed to seed session config file: {}", target, e);
        }
    }

    /**
     * 在指定会话的配置目录里执行一段逻辑。挂载切换后需要在客户端线程重载目标会话的
     * 配置时用（此刻线程上下文里的 owner 还是切换前的那个）。
     */
    public static <T> T withConfigOwner(String owner, Supplier<T> body) {
        String previous = CONFIG_OWNER.get();
        CONFIG_OWNER.set(owner == null ? "" : owner);
        try {
            return body.get();
        } finally {
            if (previous == null) {
                CONFIG_OWNER.remove();
            } else {
                CONFIG_OWNER.set(previous);
            }
        }
    }

    public static void withConfigOwner(String owner, Runnable body) {
        withConfigOwner(owner, () -> {
            body.run();
            return null;
        });
    }

    public static String currentConfigOwner() {
        String override = CONFIG_OWNER.get();
        if (override != null) {
            return override;
        }
        String owner = com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner();
        return owner == null ? "" : owner;
    }

    private static String sessionDirName(String owner) {
        String trimmed = owner == null ? "" : owner.trim();
        if (trimmed.isEmpty()) {
            return "_default";
        }
        return trimmed.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    /**
     * 切换当前用户的激活配置，并重新加载所有设置。
     * 
     * @param newProfileName The name of the profile to activate.
     */
    public static void setActiveProfile(String newProfileName) {
        if (!profileExists(newProfileName)) {
            mythosScriptMod.LOGGER.error("Attempted to switch to a non-existent profile: {}", newProfileName);
            return;
        }
        activeProfileName = newProfileName;
        setActiveProfileForCurrentUser(newProfileName);
        mythosScriptMod.LOGGER.info("Switched active profile to: {}", newProfileName);

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
            mythosScriptMod.LOGGER.error("Failed to create new profile '{}'", profileName, e);
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
            mythosScriptMod.LOGGER.error("Failed to delete profile '{}'", profileName, e);
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
            mythosScriptMod.LOGGER.error("Failed to list all profiles", e);
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
            mythosScriptMod.LOGGER.error("Failed to load user-profile mapping file", e);
            profileMappings = new ConcurrentHashMap<>();
        }
    }

    private static void saveMappings() {
        try (BufferedWriter writer = Files.newBufferedWriter(MAPPING_FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(profileMappings, writer);
        } catch (IOException e) {
            mythosScriptMod.LOGGER.error("Failed to save user-profile mapping file", e);
        }
    }
}
