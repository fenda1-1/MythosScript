package com.zszl.zszlScriptMod.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.nio.file.attribute.DosFileAttributeView;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AHKExecutor {

    public static final Logger LOGGER = LogManager.getLogger(zszlScriptMod.class);

    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    private static final Path AHK_DIR = createCustomTempDir();

    private static Path createCustomTempDir() {
        try {
            Path baseDir = Paths.get(ModConfig.CONFIG_DIR);
            Path targetDir = baseDir.resolve("AutoHotKey");

            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
                setFileHidden(targetDir);
            }

            return targetDir;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create hidden AHK directory", e);
        }
    }

    private static void setFileHidden(Path path) throws IOException {
        if (System.getProperty("os.name").startsWith("Windows")) {
            DosFileAttributeView attrs = Files.getFileAttributeView(path, DosFileAttributeView.class);
            if (attrs != null) {
                attrs.setHidden(true);
            }
        }
    }

    // *** 核心修改: 在静态初始化块中调用新的检查与更新方法 ***
    // *** 核心修改: 在静态初始化块中调用新的检查与更新方法 ***
    static {
        try {
            ensureLatestAHKResource("AutoHotKey/AutoHotkey.exe");
            ensureLatestAHKResource("AutoHotKey/KeyPress.ahk");
            ensureLatestAHKResource("AutoHotKey/MouseClick.ahk");
            ensureLatestAHKResource("AutoHotKey/FindAndReturn.ahk");
            // --- 新增代码行 ---
            ensureLatestAHKResource("AutoHotKey/GuiClick.ahk");
            // --- 新增结束 ---
        } catch (Exception e) {
            LOGGER.error("Fatal error occurred while extracting or updating AHK resources", e);
        }
    }

    public static void executeAHKScript(String scriptName, String... params) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        Path ahkExe = AHK_DIR.resolve("AutoHotkey.exe");
        Path scriptPath = AHK_DIR.resolve(scriptName);

        List<String> command = new ArrayList<>();
        command.add(ahkExe.toAbsolutePath().toString());
        command.add(scriptPath.toAbsolutePath().toString());
        command.addAll(Arrays.asList(params));

        pb.command(command);
        if (ModConfig.isDebugFlagEnabled(DebugModule.AHK_EXECUTION)) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.player != null) {
                String fullCommand = String.join(" ", command);
                fullCommand = fullCommand.substring(fullCommand.indexOf("AutoHotkey.exe"));
                mc.player.sendMessage(new TextComponentString("§d[调试] §7[AHK] " + fullCommand));
            }
        }

        pb.start();
    }

    public static void executeTargetedAHKScript(String scriptName, String... params) throws Exception {
        List<String> finalParams = new ArrayList<>();
        String processId = getCurrentProcessId();
        if (!processId.isEmpty()) {
            finalParams.add(processId);
        }
        if (params != null && params.length > 0) {
            finalParams.addAll(Arrays.asList(params));
        }
        executeAHKScript(scriptName, finalParams.toArray(new String[0]));
    }

    public static String getCurrentProcessId() {
        try {
            String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
            if (runtimeName == null) {
                return "";
            }
            int atIndex = runtimeName.indexOf('@');
            String pid = atIndex >= 0 ? runtimeName.substring(0, atIndex) : runtimeName;
            return pid.matches("\\d+") ? pid : "";
        } catch (Exception e) {
            LOGGER.warn("无法获取当前 Java 进程 PID，将回退到通用窗口匹配", e);
            return "";
        }
    }

    /**
     * 在Minecraft窗口中查找文本并返回布尔结果 (不再需要窗口名)
     * 
     * @param searchText 要搜索的文本数据
     * @param err1       文字的黑点容错百分率（0.1=10%）
     * @param err0       背景的白点容错百分率（0.1=10%）
     * @param X1         查找区域左上角X坐标
     * @param Y1         查找区域左上角Y坐标
     * @param X2         查找区域右下角X坐标
     * @param Y2         查找区域右下角Y坐标
     * @return 是否找到文本
     */
    public static boolean FindTextInWindow(String searchText, double err1, double err0, String X1, String Y1, String X2,
            String Y2) {
        try {
            Path ahkExe = AHK_DIR.resolve("AutoHotkey.exe");
            Path scriptPath = AHK_DIR.resolve("FindAndReturn.ahk");

            // 构建参数列表，注意不再传递窗口名
            List<String> command = new ArrayList<>();
            command.add(ahkExe.toAbsolutePath().toString());
            command.add(scriptPath.toAbsolutePath().toString());
            String processId = getCurrentProcessId();
            if (!processId.isEmpty()) {
                command.add(processId);
            }
            command.add(searchText); // 搜索文本
            command.add(Double.toString(err1)); // err1容错率
            command.add(Double.toString(err0)); // err0容错率
            command.add(X1); // X1
            command.add(Y1); // Y1
            command.add(X2); // X2
            command.add(Y2); // Y2

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            // 读取输出结果
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            // 使用临时文件来获取结果，比读取stdout更可靠
            process.waitFor(10, TimeUnit.SECONDS);
            Path resultFile = AHK_DIR.resolve("FindTextResult.txt");
            if (Files.exists(resultFile)) {
                String result = new String(Files.readAllBytes(resultFile));
                Files.deleteIfExists(resultFile); // 清理
                return "true".equalsIgnoreCase(result.trim());
            }

            return false;
        } catch (Exception e) {
            LOGGER.error("Error occurred while searching text", e);
            return false;
        }
    }

    /**
     * *** 新增辅助方法: 计算流的MD5校验和 ***
     * 
     * @param is 输入流
     * @return 文件的MD5哈希值
     * @throws Exception 如果计算失败
     */
    private static String getMD5Checksum(InputStream is) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        // 使用 DigestInputStream 可以在读取文件内容的同时计算哈希值
        try (DigestInputStream dis = new DigestInputStream(is, md)) {
            // 读取流的全部内容，但不需要存储它，因为dis会自动更新md
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
                // 空循环体，仅为了读取
            }
        }
        byte[] digest = md.digest();
        return toUpperHex(digest);
    }

    private static String toUpperHex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            result[i * 2] = HEX_CHARS[value >>> 4];
            result[i * 2 + 1] = HEX_CHARS[value & 0x0F];
        }
        return new String(result);
    }

    /**
     * *** 核心修改: 替换旧的 extractResourceIfNotExists 方法 ***
     * 确保磁盘上的AHK资源文件与Mod内置的最新版本一致。
     * 如果文件不存在，或与内置版本不同（通过MD5校验），则进行提取或覆盖。
     *
     * @param resourceName 资源在JAR包内的路径 (例如 "AutoHotKey/KeyPress.ahk")
     * @return 最终在磁盘上的文件路径
     * @throws IOException 如果资源读取或文件写入失败
     */
    private static Path ensureLatestAHKResource(String resourceName) throws Exception {
        // 从资源名中解析出纯文件名
        String simpleName = resourceName.substring(resourceName.lastIndexOf("/") + 1);
        Path targetPath = AHK_DIR.resolve(simpleName);

        try (InputStream resourceStream = AHKExecutor.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (resourceStream == null) {
                throw new FileNotFoundException("在 classpath 中找不到资源: " + resourceName);
            }

            // 读取整个资源到内存，以便多次使用（一次用于计算哈希，一次用于写入文件）
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = resourceStream.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            byte[] resourceBytes = baos.toByteArray();

            String internalChecksum = getMD5Checksum(new ByteArrayInputStream(resourceBytes));
            String externalChecksum = "";

            if (Files.exists(targetPath)) {
                try (InputStream externalStream = new FileInputStream(targetPath.toFile())) {
                    externalChecksum = getMD5Checksum(externalStream);
                }
            }

            // 比较校验和，如果不一致或外部文件不存在，则更新
            if (!internalChecksum.equals(externalChecksum)) {
                if (!Files.exists(targetPath)) {
                    LOGGER.info("AHK 资源 '{}' 不存在, 正在提取新版本...", simpleName);
                } else {
                    LOGGER.warn("AHK 资源 '{}' 的校验和不匹配 (内置: {}, 本地: {}), 将其更新至最新版本。",
                            simpleName, internalChecksum, externalChecksum);
                }

                // 使用内存中的字节数组写入/覆盖文件
                Files.copy(new ByteArrayInputStream(resourceBytes), targetPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("'{}' 已成功提取/更新。", simpleName);
            } else {
                // 如果校验和一致，则什么都不做
                // LOGGER.info("AHK 资源 '{}' 已是最新版本，无需操作。", simpleName); // 这条日志可以取消注释用于调试
            }
        }
        return targetPath;
    }
}
