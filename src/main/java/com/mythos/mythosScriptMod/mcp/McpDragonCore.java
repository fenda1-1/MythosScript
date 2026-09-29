package com.mythos.mythosScriptMod.mcp;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import static com.mythos.mythosScriptMod.mcp.McpJson.*;

/** All state, reflection and original GUI calls stay on the Minecraft client thread. */
public final class McpDragonCore {
    private static final String PREFIX = "eos.moe.dragoncore.";
    /** Raw frame transport: "MYRAW1\n\0" + little-endian int width + int height + BGRA rows. */
    static final byte[] RAW_MAGIC = {'M','Y','R','A','W','1','\n','\0'};
    private static final DragonCanvas canvas = new DragonCanvas();
    private static java.nio.ByteBuffer rawBuffer;
    private static final Set<Integer> keys = new HashSet<>();
    private static final Set<Integer> buttons = new HashSet<>();
    private static Object world, screen;
    private static String token = "", error = "";
    private static long lease, serial;
    private static boolean supported, drawing, screenHook;
    private static String declaredVersion;
    private static int mouseX = -1, mouseY = -1, wheel, width, height;
    private static JsonArray nodes = new JsonArray();
    private static final Map<String, Object> components = new HashMap<>();
    private static final Map<String, Method> methods = new HashMap<>();
    private static final Map<String, Field> fields = new HashMap<>();
    private McpDragonCore() {}

    public static boolean active() { return supported && System.currentTimeMillis() < lease; }
    public static boolean keyDown(int code) { return keys.contains(code); }
    public static boolean buttonDown(int button) { return buttons.contains(button); }
    public static int mouseX() { return mouseX; }
    public static int mouseY() { return mouseY; }
    public static int wheel() { return wheel; }

    /** Also proves that the optional mixin actually applied before accepting any input. */
    public static boolean skipScreenDraw() {
        if (!active()) return false;
        if (drawing) { screenHook = true; return false; }
        return true;
    }

    public static JsonObject call(JsonObject p) throws Exception {
        String op = p.has("operation") ? p.get("operation").getAsString() : "inspect";
        if ("release".equals(op)) { reset(); return object("available", false, "reason", "已结束龙核观察"); }
        long started = System.nanoTime(), dispatched = started, composed;
        Minecraft mc = Minecraft.getMinecraft();
        ModContainer mod = Loader.instance().getIndexedModList().get("dragoncore");
        String version = version(mod);
        if (!"1.12.2-2.6.2.9".equals(version) && !"2.6.2.9".equals(version)) {
            reset(); return object("available", false, "reason", "需要 DragonCore 2.6.2.9", "version", version);
        }
        // 软件画布不读取 Framebuffer，真实渲染与低占用模式都能输出龙核界面。
        sync(mc);
        if (mc.world == null || mc.player == null) { reset(); return object("available", false, "reason", "尚未进入服务器"); }
        supported = true;
        if (!"inspect".equals(op)) {
            if (!active() || !token.equals(string(p, "expectedToken"))) throw new IllegalStateException("龙核界面已切换或观察已过期，请刷新后重试");
            if (mc.currentScreen == null || !mc.currentScreen.getClass().getName().equals(PREFIX + "sl"))
                throw new IllegalStateException("当前只有 HUD，没有可操作的龙核窗口");
            if (!error.isEmpty() || !screenHook) throw new IllegalStateException("龙核绘制桥接尚未就绪：" + error);
        }
        lease = System.currentTimeMillis() + 3000;
        if ("pointer".equals(op) || "scroll".equals(op)) {
            if (p.has("x")) mouseX = integer(p, "x", -1, -1, Math.max(1, width));
            if (p.has("y")) mouseY = integer(p, "y", -1, -1, Math.max(1, height));
        }
        if (!"inspect".equals(op)) {
            Object expectedScreen = mc.currentScreen;
            if (!"pointer".equals(op) || !p.has("phase") || !"move".equals(p.get("phase").getAsString())) modifiers(p);
            render(mc); // Native hit testing/cache before dispatch, never dispatch to a replacement GUI.
            if (mc.currentScreen != expectedScreen) throw new IllegalStateException("界面在更新期间切换，输入已取消");
            GuiScreen gui = mc.currentScreen;
            switch (op) {
                case "pointer": {
                    String phase = string(p, "phase");
                    int button = integer(p, "button", 0, 0, 2);
                    if ("press".equals(phase)) {
                        buttons.add(button);
                        invoke(gui, "func_73864_a", void.class, new Class<?>[]{int.class,int.class,int.class}, mouseX,mouseY,button);
                    } else if ("release".equals(phase)) {
                        try { invoke(gui, "func_146286_b", void.class, new Class<?>[]{int.class,int.class,int.class}, mouseX,mouseY,button); }
                        finally { buttons.remove(button); }
                    } else if ("move".equals(phase)) {
                        for (Integer held : new ArrayList<>(buttons))
                            invoke(gui,"func_146273_a",void.class,new Class<?>[]{int.class,int.class,int.class,long.class},mouseX,mouseY,held,50L);
                    } else throw new IllegalArgumentException("Unknown pointer phase");
                    break;
                }
                case "key": {
                    int key = integer(p,"keyCode",0,0,255);
                    String text = p.has("text") ? p.get("text").getAsString() : "";
                    invoke(gui,"func_73869_a",void.class,new Class<?>[]{char.class,int.class},text.isEmpty()?'\0':text.charAt(0),key);
                    break;
                }
                case "text": {
                    String text = string(p,"text");
                    if (text.length()>4096) throw new IllegalArgumentException("一次最多输入 4096 个字符");
                    Object focused = null;
                    for (Object component : components.values()) {
                        String name = component.getClass().getName();
                        if ((name.equals(PREFIX+"oj") && truth(value(component,"focused"),false))
                            || (name.equals(PREFIX+"ie") && Boolean.TRUE.equals(invoke(component,"isActive",boolean.class,new Class<?>[0])))) { focused=component; break; }
                    }
                    if (focused==null) throw new IllegalStateException("请先点击龙核文本框取得焦点");
                    if(focused.getClass().getName().equals(PREFIX+"oj")) invoke(focused,"writeText",void.class,new Class<?>[]{String.class},text);
                    else for(char c:text.replace("\r\n","\n").toCharArray())
                        invoke(focused,"keyTyped1",void.class,new Class<?>[]{char.class,int.class},c,c=='\n'?28:0);
                    break;
                }
                case "scroll":
                    wheel=integer(p,"wheel",0,-1200,1200);
                    try { invoke(gui,"runWheel",boolean.class,new Class<?>[0]); } finally { wheel=0; }
                    break;
                case "close": mc.displayGuiScreen(null); break;
                default: throw new IllegalArgumentException("Unknown DragonCore operation: "+op);
            }
            dispatched=System.nanoTime();
        }
        sync(mc);
        render(mc);
        JsonObject result = snapshot(mc);
        // Only `inspect` pays for rasterizing and exporting a frame, and a caller may decline the
        // frame entirely with export=false. Input then answers as soon as the native GUI has
        // processed it, so typing is never queued behind a full-canvas redraw.
        boolean export = bool(p,"export",true);
        result.addProperty("frames", export);
        if ("inspect".equals(op) && export && nodes.size()>0) {
            Path directory=mc.mcDataDir.toPath().resolve("console-preview/dragoncore");
            Files.createDirectories(directory);
            BufferedImage image=canvas.render(width,height,nodes,McpDragonCore::resource,integer(p,"density",2,1,4));
            result.addProperty("renderWidth",image.getWidth());
            result.addProperty("renderHeight",image.getHeight());
            String format=p.has("format")?string(p,"format"):"png";
            if(!"png".equals(format) && !"raw".equals(format))
                throw new IllegalArgumentException("format 只能是 png 或 raw");
            Path target;
            try {
                result.addProperty("format",format);
                // PNG remains lossless; reduce compression work for the larger live canvas.
                target="raw".equals(format)?writeRaw(directory,image):writePng(directory,image);
            }
            finally { /* The canvas owns and reuses its pixels until reset or resize. */ }
            result.addProperty("path",target.toAbsolutePath().toString());
            result.add("warnings",canvas.warnings());
        }
        composed=System.nanoTime();
        result.add("timings",object(
            "dispatchMs",millis(dispatched-started),
            "composeMs",millis(composed-dispatched),
            "totalMs",millis(composed-started)));
        return result;
    }

    private static long millis(long nanos) { return nanos/1000000L; }

    private static Path writePng(Path directory,BufferedImage image) throws java.io.IOException {
        Path temporary=directory.resolve("frame.png.tmp"), target=directory.resolve("frame.png");
        javax.imageio.ImageWriter writer=ImageIO.getImageWritersByFormatName("png").next();
        try {
            try(javax.imageio.stream.ImageOutputStream stream=ImageIO.createImageOutputStream(temporary.toFile())) {
                writer.setOutput(stream);
                javax.imageio.ImageWriteParam options=writer.getDefaultWriteParam();
                if(options.canWriteCompressed()) {
                    options.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                    options.setCompressionQuality(0.85f);
                }
                writer.write(null,new javax.imageio.IIOImage(image,null,null),options);
            }
        } finally {writer.dispose();}
        return Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);
    }

    /** Lossless transfer without deflate: one bulk copy of the ARGB pixels in BGRA byte order. */
    private static Path writeRaw(Path directory,BufferedImage image) throws java.io.IOException {
        Path temporary=directory.resolve("frame.raw.tmp"), target=directory.resolve("frame.raw");
        if(image.getType()!=BufferedImage.TYPE_INT_ARGB || !(image.getRaster().getDataBuffer() instanceof java.awt.image.DataBufferInt))
            throw new IllegalStateException("龙核画布不是 ARGB 像素格式");
        int[] pixels=((java.awt.image.DataBufferInt)image.getRaster().getDataBuffer()).getData();
        if(rawBuffer==null)rawBuffer=java.nio.ByteBuffer.allocateDirect(65536).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        java.nio.ByteBuffer header=rawBuffer;
        header.clear();
        header.put(RAW_MAGIC).putInt(image.getWidth()).putInt(image.getHeight()).flip();
        try(java.nio.channels.FileChannel channel=java.nio.channels.FileChannel.open(temporary,
                StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)) {
            while(header.hasRemaining())channel.write(header);
            for(int offset=0;offset<pixels.length;) {
                rawBuffer.clear();
                int count=Math.min(rawBuffer.capacity()/4,pixels.length-offset);
                rawBuffer.asIntBuffer().put(pixels,offset,count);
                rawBuffer.limit(count*4);
                while(rawBuffer.hasRemaining())channel.write(rawBuffer);
                offset+=count;
            }
        }
        return Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);
    }

    public static void tick() {
        if (!active()) { if (lease!=0) reset(); return; }
        Minecraft mc=Minecraft.getMinecraft();
        try { sync(mc); if (mc.world!=null) render(mc); else reset(); }
        catch (Exception | LinkageError e) { error=e.toString(); lease=0; keys.clear(); buttons.clear(); }
    }

    private static void render(Minecraft mc) throws Exception {
        ScaledResolution size=new ScaledResolution(mc);
        width=size.getScaledWidth(); height=size.getScaledHeight();
        if (width<=0 || height<=0 || width>4096 || height>4096 || (long)width*height>4194304)
            throw new IllegalStateException("龙核画布超出 4MP 限制");
        nodes=new JsonArray(); components.clear(); screenHook=false; error="";
        field(type("xg"),"h",ScaledResolution.class).set(null,size);
        field(type("yk"),"j",int.class).setInt(null,mouseX);
        field(type("yk"),"c",int.class).setInt(null,mouseY);
        drawing=true;
        try {
            Object hud=field(type("xg"),"z",Map.class).get(null);
            for (Object entry : new ArrayList<>(((Map<?,?>)hud).values())) ((GuiScreen)entry).drawScreen(mouseX,mouseY,0);
            if (mc.currentScreen!=null && mc.currentScreen.getClass().getName().equals(PREFIX+"sl"))
                mc.currentScreen.drawScreen(mouseX,mouseY,0);
        } finally { drawing=false; }
        serial++;
        if (!screenHook && (mc.currentScreen!=null && mc.currentScreen.getClass().getName().equals(PREFIX+"sl")))
            error="龙核 Mixin 未生效，请更新 Mod 后重启实例";
    }

    public static boolean captureComponent(Object c) {
        if (!drawing || !active()) return false;
        if (nodes.size()>=2048) { error="组件超过 2048 个，已截断"; return true; }
        try {
            JsonObject n=new JsonObject();
            for (String key : new String[]{"name","type","text","texture","textureHovered","font","color","alpha","scale",
                    "u","v","textureWidth","textureHeight","center","shadow","lineSpace","focused","enable","drawBackground",
                    "limitX","limitY","limitWidth","limitHeight","fscale","frotatex","frotatey","frotatez","fx","fy","identifier"}) {
                Object v=value(c,key); if (v instanceof String || v instanceof Number || v instanceof Boolean) n.addProperty(key,String.valueOf(v));
            }
            for (String axis : new String[]{"XPos","YPos","Width","Height"})
                n.addProperty(axis,((Number)invoke(c,"get"+axis,double.class,new Class<?>[0])).doubleValue());
            n.addProperty("hover",truth(value(c,"hover"),false));
            n.addProperty("mouseX",mouseX); n.addProperty("mouseY",mouseY);
            String path=String.valueOf(invoke(c,"getManager",null,new Class<?>[0]).hashCode())+"/"+nodes.size();
            n.addProperty("path",path);
            components.put(path,c);
            if (c.getClass().getName().equals(PREFIX+"wd")) {
                // Match LabelComponent.render: j() is inherited from the scalar parser
                // and has no runtime on a ListMoLangParser.
                Object texts=field(c.getClass(),"ALLATORIxDEMO",List.class).get(c);
                if(texts==null) texts=type("kn").getMethod("m",type("wd")).invoke(null,c);
                JsonArray lines=new JsonArray(); for(Object line:(List<?>)texts) lines.add(String.valueOf(line)); n.add("lines",lines);
            }
            Object tips=invoke(c,"getTipStringTexts",List.class,new Class<?>[0]);
            JsonArray tooltip=new JsonArray(); for(Object tip:(List<?>)tips) tooltip.add(String.valueOf(tip)); n.add("tips",tooltip);
            if(c.getClass().getName().equals(PREFIX+"kf")) {
                Object manager=field(type("rf"),"x",type("rf")).get(null);
                net.minecraft.item.ItemStack item=(net.minecraft.item.ItemStack)invoke(manager,"ALLATORIxDEMO",net.minecraft.item.ItemStack.class,
                    new Class<?>[]{String.class},String.valueOf(value(c,"identifier")));
                if(item!=null && !item.isEmpty()) {
                    n.addProperty("itemName",item.getDisplayName()); n.addProperty("count",item.getCount());
                    n.addProperty("itemId",String.valueOf(item.getItem().getRegistryName()));
                    if(item.hasTagCompound())n.addProperty("nbt",item.getTagCompound().toString());
                    for(String line:item.getTooltip(Minecraft.getMinecraft().player,net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL))tooltip.add(line);
                }
            }
            nodes.add(n);
        } catch (Exception | LinkageError e) { error="组件读取失败："+c.getClass().getSimpleName()+": "+e; }
        return true;
    }

    private static JsonObject snapshot(Minecraft mc) {
        return object("available",true,"mode","dragoncore_software","version","2.6.2.9","token",token,"revision",serial,
                "width",width,"height",height,"interactive",screenHook && error.isEmpty() && mc.currentScreen!=null
                    && mc.currentScreen.getClass().getName().equals(PREFIX+"sl"),
                "components",nodes,"reason",error.isEmpty()?(nodes.size()==0?"等待服务器打开龙核界面或 HUD":""):error,
                "timestampMs",System.currentTimeMillis());
    }
    private static void sync(Minecraft mc) {
        ScaledResolution size=new ScaledResolution(mc);
        if(world!=mc.world || screen!=mc.currentScreen || width!=size.getScaledWidth() || height!=size.getScaledHeight()) {
            if(world!=mc.world) canvas.clear();
            world=mc.world; screen=mc.currentScreen; token=UUID.randomUUID().toString();
            keys.clear(); buttons.clear(); mouseX=-1; mouseY=-1;
            width=size.getScaledWidth(); height=size.getScaledHeight();
        }
    }
    private static void reset() {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc!=null && screen!=null && screen==mc.currentScreen) {
            for(Integer button:new ArrayList<>(buttons)) {
                try {invoke(screen,"func_146286_b",void.class,new Class<?>[]{int.class,int.class,int.class},mouseX,mouseY,button);}
                catch(Exception ignored) { /* The lease still expires if an original close handler fails. */ }
            }
        }
        lease=0; drawing=false; supported=false; token=""; keys.clear(); buttons.clear(); components.clear(); nodes=new JsonArray(); canvas.clear();rawBuffer=null;
    }
    private static boolean stub() {
        String configured=System.getProperty("mythosscript.headless.stub");
        if(configured!=null) return Boolean.parseBoolean(configured);
        try { Class.forName("io.github.headlesshq.headlessmc.lwjgl.api.RedirectionApi",false,McpDragonCore.class.getClassLoader()); return true; }
        catch(ClassNotFoundException e) { return false; }
    }
    private static String version(ModContainer mod) throws Exception {
        if(mod==null)return "";
        if(declaredVersion!=null)return declaredVersion;
        // This binary's @Mod reports 2.0.0; its actual release is in mcmod.info.
        declaredVersion=mod.getVersion();
        if(mod.getSource()!=null && mod.getSource().isFile()) {
            try(java.util.zip.ZipFile jar=new java.util.zip.ZipFile(mod.getSource())) {
                java.util.zip.ZipEntry entry=jar.getEntry("mcmod.info");
                if(entry!=null && entry.getSize()>=0 && entry.getSize()<65536)
                    try(java.io.Reader reader=new java.io.InputStreamReader(jar.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)) {
                        for(JsonElement value:new JsonParser().parse(reader).getAsJsonArray()) {
                            JsonObject item=value.getAsJsonObject();
                            if(item.has("modid") && "dragoncore".equals(item.get("modid").getAsString()))declaredVersion=item.get("version").getAsString();
                        }
                    }
            }
        }
        return declaredVersion;
    }
    private static void modifiers(JsonObject p) {
        keys.clear();
        if(p.has("shift") && p.get("shift").getAsBoolean()) keys.add(42);
        if(p.has("control") && p.get("control").getAsBoolean()) keys.add(29);
        if(p.has("alt") && p.get("alt").getAsBoolean()) keys.add(56);
    }
    static boolean truth(Object value, boolean fallback) { return value==null || "".equals(value)?fallback:!"0".equals(String.valueOf(value)) && !"false".equalsIgnoreCase(String.valueOf(value)); }
    private static Object value(Object c,String key) throws Exception { return invoke(c,"getValue",Object.class,new Class<?>[]{String.class},key); }
    private static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(PREFIX+name); }
    private static Field field(Class<?> type,String name,Class<?> result) throws Exception {
        String key=type.getName()+"/"+name+":"+result.getName();
        Field f=fields.get(key); if(f!=null)return f;
        for(Class<?> t=type;t!=null;t=t.getSuperclass()) for(Field candidate:t.getDeclaredFields())
            if(candidate.getName().equals(name) && result.isAssignableFrom(candidate.getType())) {candidate.setAccessible(true); fields.put(key,candidate);return candidate;}
        throw new NoSuchFieldException(key);
    }
    private static Object invoke(Object target,String name,Class<?> result,Class<?>[] args,Object... values) throws Exception {
        String key=target.getClass().getName()+"/"+name+Arrays.toString(args)+":"+result;
        Method m=methods.get(key);
        if(m==null) {
            for(Method candidate:target.getClass().getMethods()) if(candidate.getName().equals(name)
                && Arrays.equals(candidate.getParameterTypes(),args) && (result==null || result.isAssignableFrom(candidate.getReturnType()))) {m=candidate;break;}
            if(m==null)throw new NoSuchMethodException(key);
            m.setAccessible(true); methods.put(key,m);
        }
        try { return m.invoke(target,values); }
        catch(InvocationTargetException e) { throw new IllegalStateException("DragonCore "+name+": "+e.getCause(),e.getCause()); }
    }
    private static InputStream resource(String path) throws Exception {
        if(path.startsWith("dragoncore:"))path=path.substring("dragoncore:".length());
        if(path.contains("..") || path.startsWith("/") || path.contains(":") || path.contains("\\")) throw new IllegalArgumentException("Invalid DragonCore resource");
        // Use the same resource manager as DragonCore's CustomTexture. It routes to both
        // FolderResourcePack (.data) and ZipResourcePack, retaining their normal decoding.
        Exception failure;
        try {
            final net.minecraft.client.resources.IResource resource=Minecraft.getMinecraft().getResourceManager()
                .getResource(new net.minecraft.util.ResourceLocation("dragoncore",path));
            // Materialize while the manager's wrappers are still open, so failures
            // in reading/closing also get the local DragonCore pack fallback.
            try(net.minecraft.client.resources.IResource owned=resource) {
                return copyResource(owned.getInputStream());
            }
        } catch(Exception e) { failure=e; }
        java.io.File root=(java.io.File)field(type("za"),"n",java.io.File.class).get(null);
        if(root!=null && new java.io.File(root,path+".data").isFile()
                && field(type("pz"),"c",byte[].class).get(null)==null)
            throw new java.io.IOException("本地 .data 已存在，等待服务器下发龙核资源解码信息");
        Object folder=type("vv").getConstructor().newInstance();
        try(InputStream in=(InputStream)invoke(folder,"func_110591_a",InputStream.class,new Class<?>[]{String.class},"assets/dragoncore/"+path)) {
            return copyResource(in);
        } catch(Exception e) {
            String detail=root!=null && new java.io.File(root,path+".data").isFile()
                ?"本地 .data 已存在，但龙核未能解码；请检查资源与当前服务器是否匹配"
                :"龙核资源包和本地目录均未能读取该资源";
            java.io.IOException explained=new java.io.IOException(detail+" ["+e.getClass().getSimpleName()+"]",e);
            explained.addSuppressed(failure); throw explained;
        }
    }
    private static InputStream copyResource(InputStream in) throws java.io.IOException {
        if(in==null)throw new java.io.IOException("龙核解码返回空数据");
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] buffer=new byte[8192];int count;
        while((count=in.read(buffer))!=-1) {
            if(out.size()+count>16777216)throw new java.io.IOException("资源超过 16MB 限制");
            out.write(buffer,0,count);
        }
        return new java.io.ByteArrayInputStream(out.toByteArray());
    }
}
