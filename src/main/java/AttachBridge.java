import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.UUID;
import java.util.jar.JarFile;

/** Standalone transport: never loads Minecraft or agent implementation in this JVM. */
public final class AttachBridge {
    public static void main(String[] args) throws Exception {
        if ((args.length != 2 && args.length != 3) || !args[0].matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("Usage: AttachBridge <pid> <agent.jar> [agent-options]");
        }
        File jar = new File(args[1]).getCanonicalFile();
        String agent;
        try (JarFile file = new JarFile(jar)) {
            agent = file.getManifest().getMainAttributes().getValue("Agent-Class");
            if (agent == null) throw new IllegalArgumentException("JAR has no Agent-Class");
        }
        Class<?> vm = Class.forName("com.sun.tools.attach.VirtualMachine");
        Object target = vm.getMethod("attach", String.class).invoke(null, args[0]);
        String request = UUID.randomUUID().toString();
        boolean mythos = agent.equals("dev.mythos.inject.MythosAgent");
        boolean baritone = agent.equals("com.mythos.mythosScriptMod.shadowbaritone.launch.BaritoneAgent");
        String options = "request=" + request;
        if (mythos) options += ";jar=" + java.util.Base64.getEncoder().encodeToString(jar.getPath().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (args.length == 3 && !args[2].isEmpty()) {
            for (String option : args[2].split(";")) {
                if (option.startsWith("request=") || option.startsWith("jar="))
                    throw new IllegalArgumentException("request and jar are reserved transport options");
            }
            options += ";" + args[2];
        }
        try {
            try {
                vm.getMethod("loadAgent", String.class, String.class).invoke(target, jar.getPath(), options);
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                if (!(mythos || baritone) || !cause.getClass().getName().equals("com.sun.tools.attach.AgentLoadException")
                        || !"0".equals(cause.getMessage())) throw ex;
                // Some cross-JDK Attach providers report success as AgentLoadException(0).
                // Only an exact request receipt can validate this case below.
            }
            if (mythos || baritone) {
                String key = mythos ? "mythos.attach.receipt" : "baritone.attach.receipt";
                String receipt = "";
                for (int i = 0; i < 180; i++) {
                    Properties properties = (Properties) vm.getMethod("getSystemProperties").invoke(target);
                    receipt = properties.getProperty(key, "");
                    if (receipt.startsWith(request + ":") && !receipt.endsWith(":pending")) break;
                    Thread.sleep(250);
                }
                if (!(request + ":installed").equals(receipt))
                    throw new IllegalStateException("Agent did not confirm initialization: " + receipt);
                System.out.println(mythos ? "客户端初始化成功（事件模式；完整 Mixin 功能请使用 mods 安装）。"
                    : "1.16.5 Agent 安装已确认；请在游戏中检查功能初始化。 ");
            } else {
                System.out.println("JVM 已接受 Agent；该第三方 Agent 不提供 Mythos 初始化回执。");
            }
        } finally {
            vm.getMethod("detach").invoke(target);
        }
    }
}
