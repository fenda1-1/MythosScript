package com.mythos.mythosScriptMod.mcp;

import com.google.gson.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

public class DragonCanvasTest {
    private static JsonArray lines(String text) {JsonArray result=new JsonArray();result.add(text);return result;}
    @Test public void highDensityUsesOriginalTextureDetailAndKeepsLogicalCoordinates() throws Exception {
        BufferedImage source=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<8;y++)for(int x=0;x<8;x++)source.setRGB(x,y,(x%2==0?Color.RED:Color.BLUE).getRGB());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(source,"png",bytes);
        JsonObject n=node("texture",2,3,2,2);n.addProperty("texture","stripes.png");
        n.addProperty("textureWidth",2);n.addProperty("textureHeight",2);
        JsonArray nodes=new JsonArray();nodes.add(n);
        BufferedImage image=new DragonCanvas().render(20,20,nodes,p->new ByteArrayInputStream(bytes.toByteArray()),4);
        assertEquals(80,image.getWidth());assertEquals(80,image.getHeight());
        for(int x=0;x<8;x++)assertEquals(source.getRGB(x,3),image.getRGB(8+x,15));
        BufferedImage bounded=new DragonCanvas().render(1024,1024,new JsonArray(),null,4);
        assertEquals(2048,bounded.getWidth());
        assertTrue((long)bounded.getWidth()*bounded.getHeight()<=4194304);
    }

    @Test public void ampersandAndSectionColorsStylesAndResetRenderIdentically() throws Exception {
        String value="&c红色 &a绿色 &9Blue &l粗体 &n下划线 &m删除 &r重置 &q原样";
        JsonObject n=node("label",5,5,0,0);n.add("lines",lines(value));
        JsonArray nodes=new JsonArray();nodes.add(n);
        DragonCanvas canvas=new DragonCanvas();
        BufferedImage amp=canvas.render(400,30,nodes,null,4);
        int[] expected=amp.getRGB(0,0,amp.getWidth(),amp.getHeight(),null,0,amp.getWidth());
        n.add("lines",lines(value.replaceAll("&([0-9a-fklmnor])","§$1")));
        BufferedImage section=canvas.render(400,30,nodes,null,4);
        int red=0,green=0;
        for(int y=0;y<amp.getHeight();y++)for(int x=0;x<amp.getWidth();x++) {
            assertEquals(expected[y*amp.getWidth()+x],section.getRGB(x,y));
            Color pixel=new Color(amp.getRGB(x,y));
            if(pixel.getRed()>180 && pixel.getGreen()<120)red++;
            if(pixel.getGreen()>180 && pixel.getRed()<120)green++;
        }
        assertTrue(red>20);assertTrue(green>20);
        String output=System.getProperty("dragoncanvas.evidence");
        if(output!=null)ImageIO.write(amp,"png",new File(output,"formatted-text-4x.png"));
    }

    private static JsonObject node(String type,double x,double y,double w,double h) {
        return McpJson.object("type",type,"XPos",x,"YPos",y,"Width",w,"Height",h);
    }
    @Test public void reusedCanvasClearsOldPixelsAndReleasesOnResizeAndClose() {
        DragonCanvas canvas=new DragonCanvas();JsonArray nodes=new JsonArray();
        JsonObject n=node("texture",0,0,20,20);n.addProperty("texture","255,0,0,255");nodes.add(n);
        BufferedImage first=canvas.render(20,20,nodes,null,2);
        assertEquals(Color.RED.getRGB(),first.getRGB(1,1));
        BufferedImage next=canvas.render(20,20,new JsonArray(),null,2);
        assertSame(first,next);assertEquals(new Color(17,24,32).getRGB(),next.getRGB(1,1));
        BufferedImage resized=canvas.render(30,20,new JsonArray(),null,2);
        assertNotSame(next,resized);assertEquals(60,resized.getWidth());
        canvas.clear();assertNotSame(resized,canvas.render(30,20,new JsonArray(),null,2));
    }

    @Test public void rawExportPreservesPixelsAcrossChunkBoundariesAndRepeatedSizes() throws Exception {
        java.lang.reflect.Method write=McpDragonCore.class.getDeclaredMethod("writeRaw",java.nio.file.Path.class,BufferedImage.class);
        write.setAccessible(true);
        java.nio.file.Path directory=java.nio.file.Files.createTempDirectory("dragon-raw-test");
        try {
            for(int width:new int[]{257,3,512}) {
                BufferedImage image=new BufferedImage(width,129,BufferedImage.TYPE_INT_ARGB);
                int[] pixels=((java.awt.image.DataBufferInt)image.getRaster().getDataBuffer()).getData();
                for(int i=0;i<pixels.length;i++)pixels[i]=i*7919;
                java.nio.file.Path path=(java.nio.file.Path)write.invoke(null,directory,image);
                byte[] bytes=java.nio.file.Files.readAllBytes(path);
                assertEquals(16+pixels.length*4,bytes.length);
                java.nio.ByteBuffer buffer=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                byte[] magic=new byte[8];buffer.get(magic);assertArrayEquals(McpDragonCore.RAW_MAGIC,magic);
                assertEquals(width,buffer.getInt());assertEquals(129,buffer.getInt());
                for(int pixel:pixels)assertEquals(pixel,buffer.getInt());
            }
        } finally {java.nio.file.Files.deleteIfExists(directory.resolve("frame.raw"));java.nio.file.Files.deleteIfExists(directory.resolve("frame.raw.tmp"));java.nio.file.Files.delete(directory);}
    }
    @Test public void hoverTextureClippingAndLayerOrderUseActualPixels() {
        JsonArray nodes=new JsonArray();
        JsonObject first=node("texture",2,3,20,20); first.addProperty("texture","255,0,0,255");nodes.add(first);
        JsonObject top=node("texture",8,8,20,20);top.addProperty("texture","0,255,0,255");top.addProperty("textureHovered","0,0,255,255");top.addProperty("hover",true);
        top.addProperty("limitX",10);top.addProperty("limitY",10);top.addProperty("limitWidth",3);top.addProperty("limitHeight",3);nodes.add(top);
        BufferedImage result=new DragonCanvas().render(40,40,nodes,path->{throw new AssertionError("Color requested a texture");});
        assertEquals(Color.RED.getRGB(),result.getRGB(9,9));
        assertEquals(Color.BLUE.getRGB(),result.getRGB(11,11));
        assertEquals(Color.RED.getRGB(),result.getRGB(14,14));
        assertNotEquals(Color.RED.getRGB(),result.getRGB(0,0));
    }
    @Test public void textureBytesAreDecodedAndReusedWithoutOpenGL() throws Exception {
        BufferedImage source=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);source.setRGB(0,0,Color.MAGENTA.getRGB());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(source,"png",bytes);
        JsonObject n=node("texture",0,0,2,2);n.addProperty("texture","gui/test.png");JsonArray nodes=new JsonArray();nodes.add(n);
        int[] reads={0};DragonCanvas renderer=new DragonCanvas();
        DragonCanvas.Resources resources=path->{reads[0]++;return new ByteArrayInputStream(bytes.toByteArray());};
        assertEquals(Color.MAGENTA.getRGB(),renderer.render(4,4,nodes,resources).getRGB(0,0));
        renderer.render(4,4,nodes,resources);assertEquals(1,reads[0]);
        renderer.clear();renderer.render(4,4,nodes,resources);assertEquals(2,reads[0]);
    }
    @Test public void missingResourcesAndUnsupportedEntitiesAreVisibleAndReported() {
        JsonArray nodes=new JsonArray();JsonObject n=node("texture",0,0,20,20);n.addProperty("texture","missing.png");nodes.add(n);nodes.add(node("entity",22,0,20,20));
        DragonCanvas renderer=new DragonCanvas();renderer.render(48,24,nodes,path->{throw new FileNotFoundException(path);});
        assertEquals(2,renderer.warnings().size());
    }
    @Test public void failedResourcesDoNotDecodeOnEveryFrameAndCanRecover() throws Exception {
        JsonArray nodes=new JsonArray();JsonObject n=node("texture",0,0,2,2);n.addProperty("texture","pending.png");nodes.add(n);
        DragonCanvas renderer=new DragonCanvas();int[] reads={0};
        DragonCanvas.Resources missing=path->{reads[0]++;throw new IOException("等待资源");};
        renderer.render(4,4,nodes,missing);renderer.render(4,4,nodes,missing);
        assertEquals(1,reads[0]);assertTrue(renderer.warnings().toString().contains("等待资源"));
        java.lang.reflect.Field retry=DragonCanvas.class.getDeclaredField("retryImagesAt");retry.setAccessible(true);retry.setLong(renderer,0);
        BufferedImage source=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);source.setRGB(0,0,Color.GREEN.getRGB());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(source,"png",bytes);
        assertEquals(Color.GREEN.getRGB(),renderer.render(4,4,nodes,path->new ByteArrayInputStream(bytes.toByteArray())).getRGB(0,0));
        assertEquals(0,renderer.warnings().size());
    }
    @Test public void malformedDimensionsAndColorsCannotEscapeBudgets() {
        JsonObject n=McpJson.object("x","NaN","y","Infinity");
        assertEquals(7,DragonCanvas.number(n,"x",7),0);assertEquals(9,DragonCanvas.number(n,"y",9),0);
        assertEquals(Color.WHITE,DragonCanvas.color("300,-1,2",Color.WHITE));
        try {new DragonCanvas().render(65536,65536,new JsonArray(),null);fail();}catch(IllegalArgumentException expected){}
    }
}
