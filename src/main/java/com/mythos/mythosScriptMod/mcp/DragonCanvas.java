package com.mythos.mythosScriptMod.mcp;

import com.google.gson.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** CPU-only 2D renderer. Accepts evaluated runtime values, never executes YAML or expressions. */
final class DragonCanvas {
    interface Resources { InputStream open(String path) throws Exception; }
    private final LinkedHashMap<String,BufferedImage> images=new LinkedHashMap<>(32,0.75f,true);
    private final Map<String,Font> fonts=new HashMap<>();
    private final Map<String,String> failedImages=new HashMap<>();
    private long retryImagesAt;
    private final Set<String> warnings=new LinkedHashSet<>();
    private long pixels;
    private long cacheUntil;
    // Owned by this renderer; callers must consume synchronously, not retain or flush it.
    private BufferedImage output;
    JsonArray warnings() { JsonArray result=new JsonArray(); for(String warning:warnings) result.add(warning); return result; }
    private void clearResources() { for(BufferedImage image:images.values())image.flush(); images.clear();fonts.clear();failedImages.clear();pixels=0; }
    void clear() { clearResources(); if(output!=null)output.flush();output=null;cacheUntil=0; }

    BufferedImage render(int width,int height,JsonArray nodes,Resources resources) {
        return render(width,height,nodes,resources,1);
    }

    BufferedImage render(int width,int height,JsonArray nodes,Resources resources,int requestedScale) {
        if(width<1 || height<1 || (long)width*height>4194304) throw new IllegalArgumentException("Invalid canvas size");
        int density=Math.max(1,Math.min(4,requestedScale));
        while((long)width*height*density*density>4194304)density--;
        warnings.clear();
        if(System.currentTimeMillis()>retryImagesAt){failedImages.clear();retryImagesAt=System.currentTimeMillis()+3000;}
        if(System.currentTimeMillis()>cacheUntil){clearResources();cacheUntil=System.currentTimeMillis()+30000;}
        if(output==null || output.getWidth()!=width*density || output.getHeight()!=height*density) {
            if(output!=null)output.flush();
            output=new BufferedImage(width*density,height*density,BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D g=output.createGraphics();
        try {
            g.scale(density,density);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setColor(new Color(17,24,32));g.fillRect(0,0,width,height);
            for(JsonElement element:nodes) {
                Graphics2D layer=(Graphics2D)g.create();
                try { draw(layer,element.getAsJsonObject(),resources); }
                catch(Exception e) { warn("组件 "+text(element.getAsJsonObject(),"name","")+"："+e.getMessage()); }
                finally { layer.dispose(); }
            }
            for(int i=nodes.size()-1;i>=0;i--) {
                JsonObject n=nodes.get(i).getAsJsonObject();
                if(flag(n,"hover",false) && n.has("tips") && n.getAsJsonArray("tips").size()>0) {tooltip(g,n,width,height);break;}
            }
        } finally {g.dispose();}
        return output;
    }

    private void draw(Graphics2D g,JsonObject n,Resources resources) throws Exception {
        double x=number(n,"XPos",0),y=number(n,"YPos",0),w=number(n,"Width",0),h=number(n,"Height",0);
        if(w<0 || h<0 || w>65536 || h>65536)return;
        String type=text(n,"type","");
        float alpha=(float)Math.max(0,Math.min(1,number(n,"alpha",1)));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alpha));
        double lw=number(n,"limitWidth",0),lh=number(n,"limitHeight",0);
        if(lw>0 && lh>0)g.clip(new Rectangle2D.Double(number(n,"limitX",0),number(n,"limitY",0),lw,lh));
        g.translate(x+number(n,"fx",0),y+number(n,"fy",0));
        double scale=number(n,"fscale",1);
        if(scale!=1)g.scale(scale,scale);
        g.rotate(Math.toRadians(number(n,"frotatez",0)),w/2,h/2);
        if(number(n,"frotatex",0)!=0 || number(n,"frotatey",0)!=0)warn("二维预览未还原三维旋转");
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if("texture".equals(type)) {
            String texture=text(n,"texture","");
            if(flag(n,"hover",false) && !text(n,"textureHovered","").isEmpty())texture=text(n,"textureHovered","");
            Color fill=color(texture,null);
            if(fill!=null) {g.setColor(fill);g.fill(new Rectangle2D.Double(0,0,w,h));}
            else if(!texture.isEmpty()) {
                try {
                    BufferedImage image=image(texture,resources);
                    double tw=number(n,"textureWidth",image.getWidth()),th=number(n,"textureHeight",image.getHeight());
                    if(tw<=0)tw=image.getWidth(); if(th<=0)th=image.getHeight();
                    int sx=(int)(number(n,"u",0)*image.getWidth()/tw),sy=(int)(number(n,"v",0)*image.getHeight()/th);
                    int sw=(int)(w*image.getWidth()/tw),sh=(int)(h*image.getHeight()/th);
                    double deviceScale=Math.hypot(g.getTransform().getScaleX(),g.getTransform().getShearY());
                    // Preserve pixel-art edges when enlarging; sample original texture detail when shrinking.
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,w*deviceScale>=sw && h*deviceScale>=sh
                        ?RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR:RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(image,0,0,(int)w,(int)h,sx,sy,sx+sw,sy+sh,null);
                } catch(Exception e) { placeholder(g,w,h,"图片缺失");warn(texture+"："+e.getMessage()); }
            }
            String content=text(n,"text","");
            if(!content.isEmpty()) {g.setColor(color(text(n,"color",""),Color.WHITE));g.setFont(font(n,resources));drawText(g,content,0,(float)(h/2+3));}
        } else if("label".equals(type)) {
            g.setFont(font(n,resources));g.setColor(color(text(n,"color",""),Color.WHITE));
            JsonArray lines=n.has("lines")?n.getAsJsonArray("lines"):new JsonArray();
            float dy=0;
            for(JsonElement line:lines) {
                String value=line.getAsString();
                float dx=flag(n,"center",false)?-textWidth(g,value)/2f:0;
                drawText(g,value,dx,dy+g.getFontMetrics().getAscent());
                dy+=g.getFont().getSize2D()+number(n,"lineSpace",0);
            }
        } else if("textbox".equals(type) || "textarea".equals(type)) {
            if(flag(n,"drawBackground",true)) {g.setColor(new Color(10,14,20));g.fill(new Rectangle2D.Double(0,0,w,h));g.setColor(flag(n,"focused",false)?new Color(130,225,190):Color.GRAY);g.draw(new Rectangle2D.Double(0,0,w,h));}
            g.clip(new Rectangle2D.Double(2,1,Math.max(0,w-4),Math.max(0,h-2)));
            g.setFont(font(n,resources));g.setColor(color(text(n,"color",""),Color.WHITE));
            float dy=g.getFontMetrics().getAscent()+2;
            for(String line:text(n,"text","").split("\n",-1)) {drawText(g,line,3,dy);dy+=g.getFontMetrics().getHeight();}
        } else if("slot".equals(type)) {
            placeholder(g,w,h,text(n,"itemName",text(n,"identifier","槽位"))+ (number(n,"count",0)>0?" ×"+text(n,"count",""):""));
            warn("槽位显示名称；物品三维模型与附魔效果尚未还原");
        } else if("entity".equals(type)) {placeholder(g,w,h,"实体预览");warn("实体与时装三维预览尚未还原");}
        else if(!"hit".equals(type)) {placeholder(g,w,h,type);warn("未支持的组件："+type);}
    }
    private Font font(JsonObject n,Resources resources) {
        String name=text(n,"font","");
        Font font=fonts.get(name);
        if(font==null && !name.isEmpty()) {
            if(name.endsWith(".ttf") || name.endsWith(".otf")) {
                try(InputStream stream=resources.open(name)) {font=Font.createFont(Font.TRUETYPE_FONT,stream);}
                catch(Exception e) {warn("字体 "+name+" 未加载，使用系统字体");}
            } else warn("字体别名 "+name+" 使用系统字体");
        }
        if(font==null)font=new Font("Dialog",Font.PLAIN,9);
        if(fonts.size()<32)fonts.put(name,font);
        return font.deriveFont((float)Math.max(1,Math.min(128,9*number(n,"scale",1))));
    }
    private BufferedImage image(String path,Resources resources) throws Exception {
        BufferedImage found=images.get(path);if(found!=null)return found;
        if(failedImages.containsKey(path))throw new IOException(failedImages.get(path));
        try(InputStream stream=resources.open(path);ImageInputStream input=ImageIO.createImageInputStream(stream)) {
            if(input==null)throw new IOException("无法读取图片");
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("不支持的图片格式");
            ImageReader reader=readers.next();
            try {
                reader.setInput(input,true,true);
                long size=(long)reader.getWidth(0)*reader.getHeight(0);
                if(size<1 || size>4194304)throw new IOException("图片超过 4MP 限制");
                found=reader.read(0);
                while(!images.isEmpty() && (pixels+size>8388608 || images.size()>=64)) {
                    String first=images.keySet().iterator().next();BufferedImage old=images.remove(first);pixels-=(long)old.getWidth()*old.getHeight();old.flush();
                }
                images.put(path,found);pixels+=size;return found;
            } finally {reader.dispose();}
        } catch(Exception e) {
            String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
            if(failedImages.size()<256)failedImages.put(path,message);
            throw new IOException(message,e);
        }
    }
    private void warn(String value) {if(warnings.size()<32)warnings.add(value);}
    private static void placeholder(Graphics2D g,double w,double h,String label) {
        g.setColor(new Color(65,49,67));g.fill(new Rectangle2D.Double(0,0,w,h));g.setColor(new Color(239,188,117));g.draw(new Rectangle2D.Double(0,0,w,h));
        g.clip(new Rectangle2D.Double(0,0,w,h));g.setFont(new Font("Dialog",Font.PLAIN,9));g.drawString(label,2,10);
    }
    private static void tooltip(Graphics2D g,JsonObject n,int width,int height) {
        g.setFont(new Font("Dialog",Font.PLAIN,10));
        JsonArray lines=n.getAsJsonArray("tips");int lineHeight=g.getFontMetrics().getHeight(),w=0;
        for(JsonElement line:lines)w=Math.max(w,(int)Math.ceil(textWidth(g,line.getAsString())));
        w=Math.min(width-4,w+12);int h=Math.min(height-4,lines.size()*lineHeight+8);
        int x=Math.max(2,Math.min(width-w-2,(int)number(n,"mouseX",0)+12));
        int y=Math.max(2,Math.min(height-h-2,(int)number(n,"mouseY",0)+12));
        Graphics2D layer=(Graphics2D)g.create();
        try {layer.setColor(new Color(20,8,30,240));layer.fillRect(x,y,w,h);layer.setColor(new Color(126,69,175));layer.drawRect(x,y,w,h);layer.clipRect(x+3,y+2,w-6,h-4);int dy=y+4+g.getFontMetrics().getAscent();
            for(JsonElement line:lines){layer.setColor(Color.WHITE);drawText(layer,line.getAsString(),x+6,dy);dy+=lineHeight;}
        } finally {layer.dispose();}
    }
    private static void drawText(Graphics2D g,String value,float x,float y) {
        styledText(g,value,x,y,true);
    }
    private static float textWidth(Graphics2D g,String value) {return styledText(g,value,0,0,false);}
    private static boolean formatAt(String value,int i) {
        return i+1<value.length() && (value.charAt(i)=='§' || value.charAt(i)=='&')
            && "0123456789abcdefklmnor".indexOf(Character.toLowerCase(value.charAt(i+1)))>=0;
    }
    private static float styledText(Graphics2D g,String value,float x,float y,boolean paint) {
        float start=x;
        Color original=g.getColor();Font font=g.getFont();StringBuilder run=new StringBuilder();
        boolean underline=false,strike=false;
        for(int i=0;i<=value.length();i++) {
            if(i==value.length() || formatAt(value,i)) {
                String text=run.toString();
                float advance=(float)g.getFont().getStringBounds(text,g.getFontRenderContext()).getWidth();
                if(paint && !text.isEmpty()) {
                    g.drawString(text,x,y);
                    if(underline)g.fill(new Rectangle2D.Float(x,y+1,advance,0.7f));
                    if(strike)g.fill(new Rectangle2D.Float(x,y-g.getFontMetrics().getAscent()/3f,advance,0.7f));
                }
                x+=advance;run.setLength(0);
                if(i==value.length())break;
                char code=Character.toLowerCase(value.charAt(++i));int index="0123456789abcdef".indexOf(code);
                if(index>=0){int bright=(index>>3&1)*85;int r=(index>>2&1)*170+bright,green=(index>>1&1)*170+bright,b=(index&1)*170+bright;if(index==6)r+=85;g.setColor(new Color(r,green,b,original.getAlpha()));g.setFont(font);underline=false;strike=false;}
                else if(code=='l')g.setFont(g.getFont().deriveFont(g.getFont().getStyle()|Font.BOLD));
                else if(code=='o')g.setFont(g.getFont().deriveFont(g.getFont().getStyle()|Font.ITALIC));
                else if(code=='n')underline=true;
                else if(code=='m')strike=true;
                else if(code=='r'){g.setColor(original);g.setFont(font);underline=false;strike=false;}
            } else run.append(value.charAt(i));
        }
        g.setColor(original);g.setFont(font);
        return x-start;
    }
    static String text(JsonObject n,String key,String fallback) {return n.has(key)&&n.get(key).isJsonPrimitive()?n.get(key).getAsString():fallback;}
    static double number(JsonObject n,String key,double fallback) {try {double value=Double.parseDouble(text(n,key,""));return Double.isFinite(value)?value:fallback;} catch(RuntimeException e){return fallback;}}
    static boolean flag(JsonObject n,String key,boolean fallback) {String s=text(n,key,"");return s.isEmpty()?fallback:!s.equals("0")&&!s.equalsIgnoreCase("false");}
    static Color color(String text,Color fallback) {
        try {
            if(text.startsWith("#"))return Color.decode(text);
            String[] values=text.split(",");if(values.length!=3 && values.length!=4)return fallback;
            int[] rgba={255,255,255,255};for(int i=0;i<values.length;i++)rgba[i]=Integer.parseInt(values[i].trim());
            return new Color(rgba[0],rgba[1],rgba[2],rgba[3]);
        } catch(RuntimeException e){return fallback;}
    }
}
