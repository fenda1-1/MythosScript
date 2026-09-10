// 文件路径: src/main/java/com/keycommand2/zszlScriptMod/utils/TextureManagerHelper.java
package com.zszl.zszlScriptMod.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.IIORegistry;
import javax.imageio.stream.ImageInputStream;

import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.config.ChatOptimizationConfig.ImageQuality;
import com.zszl.zszlScriptMod.system.ProfileManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class TextureManagerHelper {
    private static final String BUILTIN_SCHEME = "builtin:";

    private static final Map<String, ResourceLocation> textureCache = new ConcurrentHashMap<>();
    private static final Map<String, int[]> textureSizeCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastAccessAt = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Void>> loadingTasks = new ConcurrentHashMap<>();
    private static final Map<String, Long> failedAt = new ConcurrentHashMap<>();
    private static final long RETRY_COOLDOWN_MS = 10_000L;
    private static final long IDLE_EVICT_MS = 120_000L;
    private static final int MAX_CACHE_ENTRIES = 12;
    private static final long EVICT_CHECK_INTERVAL_MS = 5_000L;
    private static volatile long lastEvictCheckAt = 0L;

    private static final ExecutorService IO_POOL = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "zszl-texture-loader");
            t.setDaemon(true);
            return t;
        }
    });

    private static volatile boolean imageIOReady = false;

    private static Path getDiskCacheDir() {
        return ProfileManager.getCurrentProfileDir().resolve("theme_image_cache");
    }

    public static Path getThemeImageCacheDir() {
        Path dir = getDiskCacheDir();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("Failed to create theme image cache dir: {}", dir, e);
        }
        return dir;
    }

    public static String canonicalizeImagePath(String path) {
        String source = normalizeInputPath(path);
        if (source.isEmpty() || isBuiltinResourcePath(source) || !isHttpUrl(source)) {
            return source;
        }
        Path cached = getCachedFilePathForUrl(source);
        if (cached != null && Files.exists(cached)) {
            return cached.toAbsolutePath().toString();
        }
        return source;
    }

    /**
     * 根据提供的文件路径和质量设置获取一个 ResourceLocation。
     * 它会处理图片的加载、缩放、缓存和卸载。
     *
     * @param path    图片文件的绝对路径。
     * @param quality 图片质量/缩放设置。
     * @return 对应的 ResourceLocation，如果路径无效或加载失败则返回 null。
     */
    public static ResourceLocation getResourceLocationForPath(String path, ImageQuality quality) {
        ensureImageIOPlugins();
        String normalizedInput = normalizeInputPath(path);
        if (normalizedInput.isEmpty()) {
            return null;
        }
        String source = canonicalizeImagePath(normalizedInput);

        // 使用路径和质量作为复合键，因为不同质量设置会生成不同的纹理
        String cacheKey = source + "::" + quality.name();

        ResourceLocation cached = textureCache.get(cacheKey);
        if (cached != null) {
            lastAccessAt.put(cacheKey, System.currentTimeMillis());
            maybeEvictCache(false);
            return cached;
        }

        if (isInRetryCooldown(cacheKey)) {
            return null;
        }

        loadingTasks.computeIfAbsent(cacheKey, k -> startAsyncLoad(k, source, quality));
        maybeEvictCache(false);
        return null;
    }

    public static void prefetch(String path, ImageQuality quality) {
        getResourceLocationForPath(path, quality);
    }

    private static String normalizeInputPath(String path) {
        if (path == null) {
            return "";
        }
        String s = path.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private static CompletableFuture<Void> startAsyncLoad(String cacheKey, String source, ImageQuality quality) {
        return CompletableFuture.runAsync(() -> {
            try {
                BufferedImage originalImage = loadImage(source);
                if (originalImage == null) {
                    failedAt.put(cacheKey, System.currentTimeMillis());
                    zszlScriptMod.LOGGER.warn("Background image load failed: {}", source);
                    return;
                }

                BufferedImage processedImage = processImage(originalImage, quality);
                Minecraft mc = Minecraft.getMinecraft();
                if (mc == null) {
                    failedAt.put(cacheKey, System.currentTimeMillis());
                    return;
                }

                mc.addScheduledTask(() -> {
                    try {
                        DynamicTexture dynamicTexture = new DynamicTexture(processedImage);
                        String texName = Integer.toHexString(source.hashCode());
                        ResourceLocation location = mc.getTextureManager().getDynamicTextureLocation(
                                "chat_bg/" + texName + "_" + quality.name().toLowerCase(), dynamicTexture);
                        textureCache.put(cacheKey, location);
                        lastAccessAt.put(cacheKey, System.currentTimeMillis());
                        textureSizeCache.put(cacheKey,
                            new int[] { Math.max(1, processedImage.getWidth()), Math.max(1, processedImage.getHeight()) });
                        failedAt.remove(cacheKey);
                        maybeEvictCache(true);
                    } catch (Exception e) {
                        failedAt.put(cacheKey, System.currentTimeMillis());
                        zszlScriptMod.LOGGER.error("GPU texture upload failed: {}", source, e);
                    }
                });
            } catch (Exception e) {
                failedAt.put(cacheKey, System.currentTimeMillis());
                zszlScriptMod.LOGGER.error("Error while async loading background image source: {}", source, e);
            }
        }, IO_POOL).whenComplete((v, ex) -> loadingTasks.remove(cacheKey));
    }

    private static boolean isInRetryCooldown(String cacheKey) {
        Long lastFail = failedAt.get(cacheKey);
        if (lastFail == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastFail) < RETRY_COOLDOWN_MS;
    }

    private static BufferedImage loadImage(String source) throws Exception {
        ensureImageIOPlugins();
        if (isBuiltinResourcePath(source)) {
            return readBuiltinResourceImage(source);
        }
        if (isHttpUrl(source)) {
            Path cached = getCachedFilePathForUrl(source);
            if (cached != null && Files.exists(cached) && Files.isReadable(cached)) {
                BufferedImage local = readImageFromFile(cached.toFile());
                if (local != null) {
                    return local;
                }
            }

            URL url = new URL(source);
            URLConnection connection = HttpsCompat.openConnection(url);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(12000);
            applyCommonRequestHeaders(connection, url);
            if (connection instanceof HttpURLConnection) {
                HttpURLConnection http = (HttpURLConnection) connection;
                http.setInstanceFollowRedirects(true);
                http.setRequestMethod("GET");
            }

            try (InputStream in = connection.getInputStream()) {
                byte[] bytes = readAllBytes(in);
                if (bytes.length == 0) {
                    return null;
                }
                cacheDownloadedBytes(source, bytes);
                return decodeImageBytes(bytes);
            }
        }

        File file = new File(source);
        if (!file.exists() || !file.canRead()) {
            zszlScriptMod.LOGGER.warn("Background image file does not exist or is unreadable: {}", source);
            return null;
        }
        return readImageFromFile(file);
    }

    private static BufferedImage readBuiltinResourceImage(String source) throws Exception {
        String resourcePath = toBuiltinResourcePath(source);
        if (resourcePath.isEmpty()) {
            return null;
        }
        String classpathResource = "assets/zszl_script/" + resourcePath;
        try (InputStream in = TextureManagerHelper.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                zszlScriptMod.LOGGER.warn("Built-in theme image resource not found: {}", classpathResource);
                return null;
            }
            byte[] bytes = readAllBytes(in);
            if (bytes.length == 0) {
                zszlScriptMod.LOGGER.warn("Built-in theme image resource is empty: {}", classpathResource);
                return null;
            }
            return decodeImageBytes(bytes);
        }
    }

    private static void applyCommonRequestHeaders(URLConnection connection, URL url) {
        if (connection == null) {
            return;
        }

        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Pragma", "no-cache");

        if (url == null) {
            return;
        }

        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("haowallpaper.com") || host.endsWith(".haowallpaper.com")) {
            // 该站点对图片链路做了防盗链校验，缺少 Referer 时会直接返回 403。
            connection.setRequestProperty("Referer", "https://haowallpaper.com/homeView");
            connection.setRequestProperty("Origin", "https://haowallpaper.com");
        }
    }

    private static BufferedImage readImageFromFile(File file) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (img != null) {
                return img;
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            BufferedImage decoded = decodeImageBytes(bytes);
            if (decoded == null) {
                zszlScriptMod.LOGGER.warn("Image decode failed for file: {} (head={})",
                        file.getAbsolutePath(), toHexHead(bytes, 16));
            }
            return decoded;
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("Image decode exception for file: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    private static BufferedImage decodeImageBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null) {
                return img;
            }
        } catch (Exception ignore) {
        }

        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) {
                return null;
            }
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            while (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis, true, true);
                    BufferedImage img = reader.read(0);
                    if (img != null) {
                        return img;
                    }
                } catch (Exception ignore) {
                } finally {
                    try {
                        reader.dispose();
                    } catch (Exception ignore) {
                    }
                    try {
                        iis.seek(0);
                    } catch (Exception ignore) {
                    }
                }
            }
        } catch (Exception ignore) {
        }

        // 最后兜底：直接实例化 WebP Reader SPI 读取（绕过 ImageIO provider 发现链）
        BufferedImage webp1 = decodeWithExplicitWebpSpi(bytes, "com.luciad.imageio.webp.WebPImageReaderSpi");
        if (webp1 != null) {
            return webp1;
        }
        BufferedImage webp2 = decodeWithExplicitWebpSpi(bytes, "com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi");
        if (webp2 != null) {
            return webp2;
        }

        return null;
    }

    private static BufferedImage decodeWithExplicitWebpSpi(byte[] bytes, String spiClassName) {
        try {
            Class<?> c = Class.forName(spiClassName);
            Object spiObj = c.newInstance();
            if (!(spiObj instanceof ImageReaderSpi)) {
                return null;
            }
            ImageReaderSpi spi = (ImageReaderSpi) spiObj;
            ImageReader reader = spi.createReaderInstance();
            try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                if (iis == null) {
                    return null;
                }
                reader.setInput(iis, true, true);
                return reader.read(0);
            } finally {
                try {
                    reader.dispose();
                } catch (Exception ignore) {
                }
            }
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static synchronized void ensureImageIOPlugins() {
        if (imageIOReady) {
            return;
        }
        try {
            // 注意：不要调用 ImageIO.scanForPlugins()。
            // 某些运行环境会因为类路径中存在残缺的 ImageIO provider（例如缺失 WebP writer）
            // 触发 ServiceConfigurationError，导致 WEBP 初始化失败。
            boolean ok1 = registerWebpReaderSpi("com.luciad.imageio.webp.WebPImageReaderSpi");
            boolean ok2 = registerWebpReaderSpi("com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi");
            zszlScriptMod.LOGGER.info("WEBP reader registration status: luciad={}, twelvemonkeys={}", ok1, ok2);
        } catch (Throwable e) {
            zszlScriptMod.LOGGER.warn("ImageIO plugin init failed", e);
        } finally {
            imageIOReady = true;
        }
    }

    private static boolean registerWebpReaderSpi(String className) {
        try {
            Class<?> spiCls = Class.forName(className);
            Object spi = spiCls.newInstance();
            IIORegistry.getDefaultInstance().registerServiceProvider(spi);
            zszlScriptMod.LOGGER.info("Registered WEBP ImageReader SPI: {}", className);
            return true;
        } catch (Throwable ignore) {
            // 忽略：尝试下一个实现
            return false;
        }
    }

    private static String toHexHead(byte[] bytes, int max) {
        if (bytes == null || bytes.length == 0) {
            return "empty";
        }
        int n = Math.min(max, bytes.length);
        StringBuilder sb = new StringBuilder(n * 3);
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02X", bytes[i] & 0xFF));
            if (i < n - 1) {
                sb.append('-');
            }
        }
        return sb.toString();
    }

    private static boolean isHttpUrl(String source) {
        String s = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("http://") || s.startsWith("https://");
    }

    private static boolean isBuiltinResourcePath(String source) {
        String s = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        return s.startsWith(BUILTIN_SCHEME);
    }

    private static String toBuiltinResourcePath(String source) {
        String normalized = source == null ? "" : source.trim();
        if (!isBuiltinResourcePath(normalized)) {
            return "";
        }
        String resourcePath = normalized.substring(BUILTIN_SCHEME.length()).trim();
        while (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }
        return resourcePath;
    }

    private static byte[] readAllBytes(InputStream in) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static Path getCachedFilePathForUrl(String source) {
        if (!isHttpUrl(source)) {
            return null;
        }
        try {
            String digest = sha1(source);
            return getDiskCacheDir().resolve(digest + ".img");
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("Failed to build cache file path for url: {}", source, e);
            return null;
        }
    }

    private static void cacheDownloadedBytes(String source, byte[] bytes) {
        try {
            Path file = getCachedFilePathForUrl(source);
            if (file == null) {
                return;
            }
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("Failed to persist downloaded image cache: {}", source, e);
        }
    }

    private static String sha1(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] dig = md.digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : dig) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 根据指定的质量对 BufferedImage 进行缩放和插值处理。
     */
    private static BufferedImage processImage(BufferedImage original, ImageQuality quality) {
        if (quality == ImageQuality.ORIGINAL) {
            return original; // 原始尺寸，不处理
        }

        int targetWidth;
        Object interpolation;

        switch (quality) {
            case HIGH:
                targetWidth = 1920;
                interpolation = RenderingHints.VALUE_INTERPOLATION_BILINEAR;
                break;
            case MEDIUM:
                targetWidth = 854;
                interpolation = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
                break;
            case LOW:
                targetWidth = 426;
                interpolation = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
                break;
            default:
                return original;
        }

        // 如果原图比目标宽度还小，则不放大
        if (original.getWidth() <= targetWidth) {
            return original;
        }

        double aspectRatio = (double) original.getHeight() / original.getWidth();
        int targetHeight = (int) (targetWidth * aspectRatio);

        Image resultingImage = original.getScaledInstance(targetWidth, targetHeight,
                quality == ImageQuality.HIGH ? Image.SCALE_SMOOTH : Image.SCALE_REPLICATE);
        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = outputImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        g2d.drawImage(resultingImage, 0, 0, null);
        g2d.dispose();

        return outputImage;
    }

    /**
     * !! 核心修改：卸载指定路径的所有质量版本的纹理 !!
     * 
     * @param path 要卸载的图片文件的路径。
     */
    public static void unloadTexture(String path) {
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        String source = path.trim();
        for (ImageQuality quality : ImageQuality.values()) {
            String cacheKey = source + "::" + quality.name();
            ResourceLocation location = textureCache.remove(cacheKey);
            if (location != null) {
                Minecraft.getMinecraft().getTextureManager().deleteTexture(location);
                zszlScriptMod.LOGGER.info("Unloaded texture from GPU memory: {}", location);
            }
            lastAccessAt.remove(cacheKey);
            textureSizeCache.remove(cacheKey);
            failedAt.remove(cacheKey);
        }
    }

    public static int[] getTextureSizeForPath(String path, ImageQuality quality) {
        String normalizedInput = normalizeInputPath(path);
        if (normalizedInput.isEmpty()) {
            return null;
        }
        String source = canonicalizeImagePath(normalizedInput);
        String cacheKey = source + "::" + quality.name();
        int[] size = textureSizeCache.get(cacheKey);
        if (size == null || size.length < 2) {
            return null;
        }
        return new int[] { Math.max(1, size[0]), Math.max(1, size[1]) };
    }

    /**
     * !! 核心修改：清空所有缓存的纹理并从显存中卸载 !!
     */
    public static void clearCache() {
        if (textureCache.isEmpty()) {
            return;
        }
        for (ResourceLocation location : textureCache.values()) {
            if (location != null) {
                // 使用主线程任务来安全地删除纹理
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    Minecraft.getMinecraft().getTextureManager().deleteTexture(location);
                });
            }
        }
        textureCache.clear();
        lastAccessAt.clear();
        textureSizeCache.clear();
        failedAt.clear();
        loadingTasks.clear();
        zszlScriptMod.LOGGER.info("Cleared all chat background caches and unloaded textures from GPU memory");
    }

    private static void maybeEvictCache(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && (now - lastEvictCheckAt) < EVICT_CHECK_INTERVAL_MS) {
            return;
        }
        lastEvictCheckAt = now;

        if (textureCache.isEmpty()) {
            return;
        }

        List<String> keysToEvict = new ArrayList<>();

        // 1) 先按空闲时间淘汰
        for (Map.Entry<String, ResourceLocation> entry : textureCache.entrySet()) {
            String key = entry.getKey();
            long last = lastAccessAt.getOrDefault(key, 0L);
            if ((now - last) > IDLE_EVICT_MS) {
                keysToEvict.add(key);
            }
        }

        // 2) 再按容量淘汰最久未访问项
        int expectedSize = textureCache.size() - keysToEvict.size();
        if (expectedSize > MAX_CACHE_ENTRIES) {
            int overflow = expectedSize - MAX_CACHE_ENTRIES;
            List<String> candidates = new ArrayList<>(textureCache.keySet());
            candidates.removeAll(keysToEvict);
            candidates.sort(Comparator.comparingLong(k -> lastAccessAt.getOrDefault(k, 0L)));
            for (int i = 0; i < overflow && i < candidates.size(); i++) {
                keysToEvict.add(candidates.get(i));
            }
        }

        if (keysToEvict.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        for (String key : keysToEvict) {
            ResourceLocation location = textureCache.remove(key);
            lastAccessAt.remove(key);
            textureSizeCache.remove(key);
            failedAt.remove(key);
            if (location != null && mc != null && mc.getTextureManager() != null) {
                mc.addScheduledTask(() -> mc.getTextureManager().deleteTexture(location));
            }
        }
    }
}
