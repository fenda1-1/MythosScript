package com.mythos.mythosScriptMod.gui.modern.plugin;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mythos.mythosScriptMod.api.IMythosPlugin;
import com.mythos.mythosScriptMod.api.IPluginChatListener;
import com.mythos.mythosScriptMod.api.IPluginCommand;
import com.mythos.mythosScriptMod.api.IPluginConfig;
import com.mythos.mythosScriptMod.api.IPluginContext;
import com.mythos.mythosScriptMod.api.IPluginMcpTool;
import com.mythos.mythosScriptMod.api.IPluginTabFactory;
import com.mythos.mythosScriptMod.api.MythosApi;
import com.mythos.mythosScriptMod.mythosScriptMod;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ClientCommandHandler;

/**
 * Discovers plugin jars in {@code mods/} and brings them to life.
 *
 * <p>Loading runs in two passes. The first pass reads every manifest and builds
 * the dependency graph; the second instantiates plugins in topological order.
 * The split exists because a plugin that fails its API check must be reported
 * <em>before</em> anything of it runs — a half-loaded plugin that already
 * registered two commands is far harder to explain than a plugin that never
 * started.</p>
 *
 * <p>Each plugin gets its own {@link URLClassLoader} whose parent is the mod's own
 * loader. Parent delegation is what lets a plugin reference Minecraft and the
 * {@code api} package while its own classes stay isolated: two plugins may ship
 * different versions of the same helper library without colliding.</p>
 */
public final class PluginLoader {

    /** What happened to one installed plugin. */
    public enum State {
        /** Not a plugin after all, or a manifest that cannot be run. */
        SKIPPED("已跳过"),
        LOADED("已加载"),
        /** Loaded once, then switched off by the user. Registrations are released. */
        DISABLED("已禁用"),
        FAILED("加载失败");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** One tab a plugin asked for; kept so a re-enable can re-register it. */
    public static final class TabRegistration {
        public final String pluginId;
        public final String command;
        public final String title;
        public final IPluginTabFactory factory;

        TabRegistration(String pluginId, String command, String title, IPluginTabFactory factory) {
            this.pluginId = pluginId;
            this.command = command;
            this.title = title;
            this.factory = factory;
        }
    }

    /** A single tick callback; disabled for good the first time it throws. */
    private static final class TickCallback {
        final Runnable action;
        boolean alive = true;

        TickCallback(Runnable action) {
            this.action = action;
        }
    }

    /** Live state of one installed plugin. Mutated only under the class lock. */
    public static final class Runtime {
        public final String fileName;
        public final PluginCatalog.Plugin manifest;
        public final List<String> commands = new ArrayList<>();
        public final List<IPluginMcpTool> tools = new ArrayList<>();
        public final List<TabRegistration> tabs = new ArrayList<>();
        public final List<IPluginChatListener> chatListeners = new ArrayList<>();
        private final List<TickCallback> ticks = new ArrayList<>();
        private final List<IPluginCommand> commandBodies = new ArrayList<>();
        private final List<String> commandNames = new ArrayList<>();
        private State state = State.SKIPPED;
        private String message = "";
        private IMythosPlugin instance;
        private URLClassLoader loader;
        private IPluginConfig config = null;

        Runtime(String fileName, PluginCatalog.Plugin manifest) {
            this.fileName = fileName;
            this.manifest = manifest;
        }

        public String id() {
            return manifest.id;
        }

        public State state() {
            return state;
        }

        public String message() {
            return message;
        }

        public IMythosPlugin instance() {
            return instance;
        }

        public IPluginConfig config() {
            return config;
        }

        public int commandCount() {
            return commands.size();
        }

        public int activeTickCount() {
            int count = 0;
            for (TickCallback callback : ticks) {
                if (callback.alive) {
                    count++;
                }
            }
            return count;
        }

        public int toolCount() {
            return tools.size();
        }

        public int tabCount() {
            return tabs.size();
        }

        public int chatListenerCount() {
            return chatListeners.size();
        }

        /** True when the plugin finished {@code onLoad} and has not been switched off. */
        public boolean isRunning() {
            return state == State.LOADED;
        }
    }

    /** Registers everything during {@code onLoad}, and only then. */
    private static final class Context implements IPluginContext {
        private final Runtime runtime;
        private boolean closed;

        Context(Runtime runtime) {
            this.runtime = runtime;
        }

        void close() {
            closed = true;
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("插件只能在 onLoad 期间注册");
            }
        }

        @Override
        public String pluginId() {
            return runtime.id();
        }

        @Override
        public String pluginVersion() {
            return runtime.manifest.version;
        }

        @Override
        public void info(String message) {
            mythosScriptMod.LOGGER.info("[plugin:{}] {}", runtime.id(), message);
        }

        @Override
        public void warn(String message, Throwable error) {
            if (error == null) {
                mythosScriptMod.LOGGER.warn("[plugin:{}] {}", runtime.id(), message);
            } else {
                mythosScriptMod.LOGGER.warn("[plugin:{}] {}", runtime.id(), message, error);
            }
        }

        @Override
        public void registerCommand(String name, IPluginCommand command) {
            ensureOpen();
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("命令名不能为空");
            }
            if (command == null) {
                throw new IllegalArgumentException("命令体不能为空");
            }
            String clean = name.trim();
            if (isCommandTaken(clean, runtime.id())) {
                throw new IllegalArgumentException("命令 /" + clean + " 已被占用");
            }
            ClientCommandHandler.instance.registerCommand(new PluginCommand(clean, command, runtime));
            runtime.commands.add(clean);
            runtime.commandNames.add(clean);
            runtime.commandBodies.add(command);
        }

        @Override
        public void registerTick(Runnable callback) {
            ensureOpen();
            if (callback == null) {
                throw new IllegalArgumentException("tick 回调不能为空");
            }
            runtime.ticks.add(new TickCallback(callback));
        }

        @Override
        public void registerMcpTool(IPluginMcpTool tool) {
            ensureOpen();
            if (tool == null) {
                throw new IllegalArgumentException("工具对象不能为空");
            }
            String name = tool.name();
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("工具名不能为空");
            }
            String clean = name.trim();
            synchronized (PluginLoader.class) {
                if (TOOL_OWNER.containsKey(clean.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("MCP 工具 " + clean + " 已被占用");
                }
                runtime.tools.add(tool);
                TOOL_OWNER.put(clean.toLowerCase(Locale.ROOT), runtime);
            }
        }

        @Override
        public void registerTab(String command, String title, IPluginTabFactory factory) {
            ensureOpen();
            if (command == null || command.trim().isEmpty()) {
                throw new IllegalArgumentException("标签页路由名不能为空");
            }
            if (factory == null) {
                throw new IllegalArgumentException("标签页工厂不能为空");
            }
            String clean = command.trim();
            synchronized (PluginLoader.class) {
                if (TABS.containsKey(clean)) {
                    throw new IllegalArgumentException("标签页路由 " + clean + " 已被占用");
                }
                TabRegistration registration = new TabRegistration(
                        runtime.id(), clean, title == null || title.trim().isEmpty() ? clean : title, factory);
                runtime.tabs.add(registration);
                TABS.put(clean, registration);
            }
        }

        @Override
        public void registerChatListener(IPluginChatListener listener) {
            ensureOpen();
            if (listener == null) {
                throw new IllegalArgumentException("聊天监听器不能为空");
            }
            runtime.chatListeners.add(listener);
        }

        @Override
        public IPluginConfig config() {
            if (runtime.config == null) {
                runtime.config = new PluginConfigStore(runtime.id());
            }
            return runtime.config;
        }
    }

    /** Bridges a plugin's command body to Minecraft's client command dispatcher. */
    private static final class PluginCommand extends CommandBase {
        private final String name;
        private final IPluginCommand body;
        private final Runtime runtime;

        PluginCommand(String name, IPluginCommand body, Runtime runtime) {
            this.name = name;
            this.body = body;
            this.runtime = runtime;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getUsage(ICommandSender sender) {
            String usage = body.usage();
            return usage == null || usage.trim().isEmpty() ? "/" + name : usage;
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
                throws CommandException {
            if (!runtime.isRunning()) {
                throw new CommandException("插件 " + runtime.id() + " 已禁用");
            }
            try {
                body.execute(args == null ? new String[0] : args);
            } catch (Throwable broken) {
                // CommandException is the only way the dispatcher reports a failure to
                // the player; anything else escapes into the chat handler silently.
                throw new CommandException("插件 " + runtime.id() + " 执行失败: " + describe(broken));
            }
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                String[] args, BlockPos targetPos) {
            try {
                List<String> options = body.complete(args == null ? new String[0] : args);
                return options == null ? Collections.<String>emptyList() : options;
            } catch (Throwable broken) {
                return Collections.emptyList();
            }
        }
    }

    private static final Map<String, Runtime> RUNTIMES = new LinkedHashMap<>();
    private static final Map<String, Runtime> BY_ID = new LinkedHashMap<>();
    /** Lower-case tool name to owner, so {@code mythos_*} names can never be shadowed. */
    private static final Map<String, Runtime> TOOL_OWNER = new LinkedHashMap<>();
    private static final Map<String, TabRegistration> TABS = new LinkedHashMap<>();

    /**
     * Incremented by every {@link #reload()}; an open main screen watches it to pull in
     * tabs whose plugin loaded after that screen was constructed.
     */
    private static volatile int generation;
    private static volatile List<Runtime> snapshot = Collections.emptyList();
    private static volatile String lastError = "";
    private static boolean loaded;

    private PluginLoader() {
    }

    /**
     * Loads every plugin exactly once.
     *
     * <p>Deliberately idempotent rather than retryable: a plugin that fails to load
     * is not retried behind the user's back, because a partially initialised plugin
     * (half its commands registered) would be worse than a failed one.</p>
     */
    public static synchronized void loadAll() {
        reload();
    }

    /**
     * Re-scans {@code mods/} and loads plugins that have not been seen before.
     *
     * <p>Only new files are loaded. A jar that was already attempted stays in
     * {@link #RUNTIMES} forever, because a {@link URLClassLoader} cannot be
     * unloaded: loading the same jar twice would produce two distinct copies of
     * each class and register every command twice. Replacing an installed plugin
     * therefore still needs a restart — that is a JVM constraint, not a shortcut.
     * {@link #reloadPlugin(String)} re-runs the plugin <em>object</em>, which is
     * enough to reset plugin state, but it cannot pick up a changed jar.</p>
     *
     * <p>This is what the centre's refresh button calls, so dropping a new jar into
     * {@code mods/} and pressing refresh brings it to life without a restart.</p>
     */
    public static synchronized void reload() {
        generation++;
        File mods = PluginCatalog.modsDirectory();
        if (mods == null) {
            // mcDataDir is not available yet; leave the flag down so a later call succeeds.
            lastError = "客户端数据目录尚未就绪";
            return;
        }
        loaded = true;
        lastError = "";

        List<PluginCatalog.Plugin> manifests = PluginCatalog.rescan();

        // Pass one: register every manifest that has not been attempted yet, so the
        // dependency graph can see plugins whose files came first in directory order.
        List<Runtime> pending = new ArrayList<>();
        for (PluginCatalog.Plugin manifest : manifests) {
            String key = fileNameKey(manifest.fileName);
            if (RUNTIMES.containsKey(key)) {
                continue;
            }
            Runtime runtime = new Runtime(manifest.fileName, manifest);
            RUNTIMES.put(key, runtime);
            BY_ID.put(manifest.id.toLowerCase(Locale.ROOT), runtime);
            pending.add(runtime);
        }
        if (pending.isEmpty()) {
            snapshot = Collections.unmodifiableList(new ArrayList<>(RUNTIMES.values()));
            return;
        }

        // Pass two: dependency order, then instantiate.
        List<Runtime> ordered = sortByDependencies(pending);
        int failed = 0;
        for (Runtime runtime : ordered) {
            if (runtime.state == State.SKIPPED) {
                loadOne(runtime, new File(mods, runtime.fileName));
                if (runtime.state == State.FAILED) {
                    failed++;
                }
            } else {
                failed++;
            }
        }
        snapshot = Collections.unmodifiableList(new ArrayList<>(RUNTIMES.values()));

        mythosScriptMod.LOGGER.info("插件加载完成: 新增 {} 个 (失败 {} 个), 清单共 {} 个",
                ordered.size(), failed, manifests.size());
    }

    /**
     * Orders pending runtimes so that every {@code requires} entry is loaded first.
     *
     * <p>A missing dependency or a cycle marks the affected plugin {@code SKIPPED}
     * with an explanatory message instead of throwing: one broken plugin must not
     * stop the others from loading.</p>
     */
    private static List<Runtime> sortByDependencies(List<Runtime> pending) {
        Map<String, Runtime> pool = new HashMap<>();
        for (Runtime runtime : pending) {
            pool.put(runtime.id().toLowerCase(Locale.ROOT), runtime);
        }
        List<Runtime> ordered = new ArrayList<>();
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> done = new HashSet<>();
        for (Runtime runtime : pending) {
            visit(runtime, pool, visiting, done, ordered);
        }
        return ordered;
    }

    private static void visit(Runtime runtime, Map<String, Runtime> pool, Set<String> visiting,
            Set<String> done, List<Runtime> ordered) {
        String id = runtime.id().toLowerCase(Locale.ROOT);
        if (done.contains(id) || runtime.state != State.SKIPPED) {
            return;
        }
        if (!visiting.add(id)) {
            runtime.state = State.FAILED;
            runtime.message = "依赖成环: " + id;
            done.add(id);
            return;
        }
        for (String dependency : runtime.manifest.requires) {
            String key = dependency.toLowerCase(Locale.ROOT);
            Runtime owner = pool.get(key);
            if (owner == null) {
                owner = BY_ID.get(key);
            }
            if (owner == null) {
                runtime.state = State.FAILED;
                runtime.message = "缺少依赖插件: " + dependency;
                break;
            }
            if (owner.state == State.FAILED) {
                runtime.state = State.FAILED;
                runtime.message = "依赖插件加载失败: " + dependency;
                break;
            }
            visit(owner, pool, visiting, done, ordered);
            if (owner.state == State.FAILED && runtime.state == State.SKIPPED) {
                runtime.state = State.FAILED;
                runtime.message = "依赖插件加载失败: " + dependency;
                break;
            }
        }
        visiting.remove(id);
        done.add(id);
        ordered.add(runtime);
    }

    private static void loadOne(Runtime runtime, File jar) {
        if (runtime.manifest.apiVersion != MythosApi.VERSION) {
            runtime.state = State.FAILED;
            runtime.message = "需要 API 版本 " + runtime.manifest.apiVersion
                    + "，当前为 " + MythosApi.VERSION + "（插件需按新版 API 重新编译）";
            return;
        }
        String entrypoint = runtime.manifest.entrypoint;
        if (entrypoint == null || entrypoint.trim().isEmpty()) {
            runtime.state = State.SKIPPED;
            runtime.message = "清单未声明 entrypoint";
            return;
        }
        if (!jar.isFile()) {
            runtime.state = State.FAILED;
            runtime.message = "找不到 jar: " + jar.getName();
            return;
        }
        Context context = new Context(runtime);
        try {
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{jar.toURI().toURL()}, IMythosPlugin.class.getClassLoader());
            runtime.loader = loader;
            Class<?> type = Class.forName(entrypoint.trim(), true, loader);
            if (!IMythosPlugin.class.isAssignableFrom(type)) {
                runtime.state = State.FAILED;
                runtime.message = entrypoint + " 没有实现 IMythosPlugin";
                return;
            }
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            runtime.instance = (IMythosPlugin) constructor.newInstance();
            runtime.instance.onLoad(context);
            runtime.state = State.LOADED;
            runtime.message = "";
            mythosScriptMod.LOGGER.info("[plugin:{}] 加载成功, 命令 {} 条, MCP 工具 {} 个, 标签页 {} 个, tick 回调 {} 个",
                    runtime.id(), runtime.commands.size(), runtime.tools.size(),
                    runtime.tabs.size(), runtime.ticks.size());
        } catch (Throwable broken) {
            runtime.state = State.FAILED;
            runtime.message = describe(broken);
            mythosScriptMod.LOGGER.warn("[plugin:{}] 加载失败", runtime.id(), broken);
        } finally {
            // Registration after onLoad returns is a programming error, not a feature.
            context.close();
        }
    }

    /**
     * Switches a plugin off and releases everything it registered.
     *
     * <p>The class loader stays alive because the JVM cannot unload it; only the
     * registrations are undone. That is enough for the user-visible meaning of
     * “off”: no commands, no tools, no tabs, no tick callbacks, no chat hooks.</p>
     */
    public static synchronized boolean disable(String pluginId) {
        Runtime runtime = runtimeById(pluginId);
        if (runtime == null || runtime.state != State.LOADED) {
            return false;
        }
        try {
            if (runtime.instance != null) {
                runtime.instance.onUnload();
            }
        } catch (Throwable broken) {
            mythosScriptMod.LOGGER.warn("[plugin:{}] onUnload 抛异常（忽略并继续卸载）", runtime.id(), broken);
        }
        releaseRegistrations(runtime);
        runtime.state = State.DISABLED;
        runtime.message = "已由用户禁用";
        snapshot = Collections.unmodifiableList(new ArrayList<>(RUNTIMES.values()));
        mythosScriptMod.LOGGER.info("[plugin:{}] 已禁用", runtime.id());
        return true;
    }

    /** Re-instantiates a disabled (or failed) plugin object and calls {@code onLoad} again. */
    public static synchronized boolean enable(String pluginId) {
        Runtime runtime = runtimeById(pluginId);
        if (runtime == null || (runtime.state != State.DISABLED && runtime.state != State.FAILED)) {
            return false;
        }
        File mods = PluginCatalog.modsDirectory();
        if (mods == null) {
            return false;
        }
        runtime.state = State.SKIPPED;
        runtime.message = "";
        loadOne(runtime, new File(mods, runtime.fileName));
        snapshot = Collections.unmodifiableList(new ArrayList<>(RUNTIMES.values()));
        return runtime.state == State.LOADED;
    }

    /**
     * Re-runs one plugin object: unload, release, instantiate again, load.
     *
     * <p>Same jar, same class loader — this resets plugin state, it does not pick up
     * new code. Replacing a jar still needs a client restart.</p>
     */
    public static synchronized boolean reloadPlugin(String pluginId) {
        Runtime runtime = runtimeById(pluginId);
        if (runtime == null) {
            return false;
        }
        if (runtime.state == State.LOADED) {
            try {
                if (runtime.instance != null) {
                    runtime.instance.onUnload();
                }
            } catch (Throwable broken) {
                mythosScriptMod.LOGGER.warn("[plugin:{}] onUnload 抛异常（忽略并继续重载）", runtime.id(), broken);
            }
            releaseRegistrations(runtime);
        }
        File mods = PluginCatalog.modsDirectory();
        if (mods == null) {
            return false;
        }
        runtime.state = State.SKIPPED;
        runtime.message = "";
        loadOne(runtime, new File(mods, runtime.fileName));
        snapshot = Collections.unmodifiableList(new ArrayList<>(RUNTIMES.values()));
        return runtime.state == State.LOADED;
    }

    private static void releaseRegistrations(Runtime runtime) {
        for (String name : runtime.commandNames) {
            try {
                ICommand existing = ClientCommandHandler.instance.getCommands().get(name);
                if (existing instanceof PluginCommand) {
                    ClientCommandHandler.instance.getCommands().remove(name);
                }
            } catch (Throwable ignored) {
                // The dispatcher is rebuilt on world join; a failed removal only
                // costs a stale entry that execute() rejects by plugin state.
            }
        }
        runtime.commandNames.clear();
        runtime.commands.clear();
        runtime.commandBodies.clear();
        runtime.ticks.clear();
        for (IPluginMcpTool tool : runtime.tools) {
            TOOL_OWNER.remove(tool.name().toLowerCase(Locale.ROOT));
        }
        runtime.tools.clear();
        for (TabRegistration tab : runtime.tabs) {
            TABS.remove(tab.command);
        }
        runtime.tabs.clear();
        runtime.chatListeners.clear();
    }

    /** Runs every live plugin tick callback once; called from the client tick. */
    public static void tick() {
        List<Runtime> runtimes = snapshot;
        for (Runtime runtime : runtimes) {
            if (runtime.state != State.LOADED) {
                continue;
            }
            for (TickCallback callback : runtime.ticks) {
                if (!callback.alive) {
                    continue;
                }
                try {
                    callback.action.run();
                } catch (Throwable broken) {
                    // One bad callback is killed for good instead of spamming every tick.
                    callback.alive = false;
                    runtime.message = "tick 回调异常，已禁用: " + describe(broken);
                    mythosScriptMod.LOGGER.warn("[plugin:{}] tick 回调异常，已禁用该回调",
                            runtime.id(), broken);
                }
            }
        }
    }

    /** Delivers one chat line to every running plugin's listeners. */
    public static void onChatMessage(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        List<Runtime> runtimes = snapshot;
        for (Runtime runtime : runtimes) {
            if (runtime.state != State.LOADED || runtime.chatListeners.isEmpty()) {
                continue;
            }
            List<IPluginChatListener> dead = null;
            for (IPluginChatListener listener : runtime.chatListeners) {
                try {
                    listener.onChatMessage(text);
                } catch (Throwable broken) {
                    if (dead == null) {
                        dead = new ArrayList<>();
                    }
                    dead.add(listener);
                    mythosScriptMod.LOGGER.warn("[plugin:{}] 聊天监听器异常，已移除", runtime.id(), broken);
                }
            }
            if (dead != null) {
                runtime.chatListeners.removeAll(dead);
            }
        }
    }

    /** Appends every running plugin's tools to the MCP {@code tools/list} payload. */
    public static void appendTools(JsonArray tools) {
        List<Runtime> runtimes = snapshot;
        for (Runtime runtime : runtimes) {
            if (runtime.state != State.LOADED) {
                continue;
            }
            for (IPluginMcpTool tool : runtime.tools) {
                tools.add(McpToolDescriptor.build(tool, runtime.id()));
            }
        }
    }

    /** Looks a running plugin's tool up by name; {@code null} when nobody owns it. */
    public static IPluginMcpTool findTool(String name) {
        if (name == null) {
            return null;
        }
        Runtime owner = TOOL_OWNER.get(name.toLowerCase(Locale.ROOT));
        if (owner == null || owner.state != State.LOADED) {
            return null;
        }
        for (IPluginMcpTool tool : owner.tools) {
            if (name.equalsIgnoreCase(tool.name())) {
                return tool;
            }
        }
        return null;
    }

    /** Used by an open screen to detect that tabs may have appeared since it was built. */
    public static int generation() {
        return generation;
    }

    /** Every tab contributed by a running plugin, in load order. */
    public static List<TabRegistration> tabs() {
        List<TabRegistration> result = new ArrayList<>();
        for (Runtime runtime : snapshot) {
            if (runtime.state == State.LOADED) {
                result.addAll(runtime.tabs);
            }
        }
        return result;
    }

    /** Live state for the plugin centre, in the order the manifests were scanned. */
    public static List<Runtime> runtimes() {
        return snapshot;
    }

    /**
     * Looks a runtime up by file name.
     *
     * <p>File name rather than id: two jars may declare the same id (a copy-paste
     * mistake), and the centre lists them by file, so the file is what must be
     * matched to show the right status on the right card.</p>
     */
    public static Runtime runtimeFor(String fileName) {
        if (fileName == null) {
            return null;
        }
        return RUNTIMES.get(fileNameKey(fileName));
    }

    public static Runtime runtimeById(String pluginId) {
        if (pluginId == null) {
            return null;
        }
        return BY_ID.get(pluginId.toLowerCase(Locale.ROOT));
    }

    public static String lastError() {
        return lastError;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    private static String fileNameKey(String fileName) {
        return fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
    }

    private static boolean isCommandTaken(String name, String ownerId) {
        Map<String, ICommand> existing = ClientCommandHandler.instance.getCommands();
        if (existing == null) {
            return false;
        }
        for (ICommand command : existing.values()) {
            if (command == null) {
                continue;
            }
            if (command instanceof PluginCommand
                    && ((PluginCommand) command).runtime.id().equalsIgnoreCase(ownerId)) {
                // A reload of the same plugin must not collide with its own leftovers.
                continue;
            }
            if (name.equalsIgnoreCase(command.getName())) {
                return true;
            }
            List<String> aliases = command.getAliases();
            if (aliases == null) {
                continue;
            }
            for (String alias : aliases) {
                if (name.equalsIgnoreCase(alias)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String describe(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        String type = error.getClass().getSimpleName();
        return message == null || message.trim().isEmpty() ? type : type + ": " + message;
    }

    /** Builds the MCP schema for one plugin tool; kept out of the hot path by the caller. */
    static final class McpToolDescriptor {
        static JsonObject build(IPluginMcpTool tool, String pluginId) {
            JsonObject schema = new JsonObject();
            schema.addProperty("name", tool.name());
            String description = tool.description();
            schema.addProperty("description", description == null ? "" : description);
            JsonObject properties = new JsonObject();
            String parameters = tool.parameters();
            if (parameters != null && !parameters.isEmpty()) {
                for (String field : parameters.split(",")) {
                    String[] pair = field.split(":");
                    if (pair.length != 2) {
                        continue;
                    }
                    JsonObject property = new JsonObject();
                    if (!"any".equals(pair[1])) {
                        property.addProperty("type", pair[1]);
                    }
                    if ("array".equals(pair[1])) {
                        property.add("items", new JsonObject());
                    }
                    properties.add(pair[0], property);
                }
            }
            JsonObject input = new JsonObject();
            input.addProperty("type", "object");
            input.add("properties", properties);
            JsonArray required = new JsonArray();
            String requiredNames = tool.required();
            if (requiredNames != null && !requiredNames.isEmpty()) {
                for (String name : requiredNames.split(",")) {
                    required.add(name);
                }
            }
            input.add("required", required);
            schema.add("inputSchema", input);
            schema.addProperty("plugin", pluginId);
            return schema;
        }
    }
}
