package org.nongchanpin.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import lombok.Getter;
import org.nongchanpin.util.*;
import org.springframework.web.multipart.MultipartFile;

public final class QualityFileSupport {
    public static final int MAX_BYTES=5*1024*1024;
    private QualityFileSupport() {}
    @Getter public static class FileData {
        private byte[] content;
        private String name,type,hash;
    }
    public static FileData read(MultipartFile file) {
        if(file==null||file.isEmpty()||file.getSize()>MAX_BYTES)throw new IllegalArgumentException("请上传 1 字节至 5 MB 的 PDF、PNG 或 JPG 报告附件。");
        FileData data=new FileData();
        try{data.content=file.getBytes();}catch(IOException e){throw new IllegalArgumentException("无法读取附件，请重新选择文件。");}
        if(data.content.length==0||data.content.length>MAX_BYTES)throw new IllegalArgumentException("附件大小超过限制。");
        byte[] b=data.content;
        if(b.length>=12&&new String(b,0,5,StandardCharsets.ISO_8859_1).equals("%PDF-")&&new String(b,Math.max(0,b.length-1024),Math.min(1024,b.length),StandardCharsets.ISO_8859_1).contains("%%EOF"))data.type="application/pdf";
        else if(b.length>=8&&Arrays.equals(Arrays.copyOf(b,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))data.type="image/png";
        else if(b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)data.type="image/jpeg";
        else throw new IllegalArgumentException("附件格式无法确认，请使用 PDF、PNG 或 JPG，不接受网页或 SVG。");
        String name=Optional.ofNullable(file.getOriginalFilename()).orElse("").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","").trim();
        if(name.isEmpty()||name.length()>128)throw new IllegalArgumentException("附件文件名须为 1–128 个字符。");
        String lower=name.toLowerCase(Locale.ROOT);
        if(!(data.type.equals("application/pdf")&&lower.endsWith(".pdf")||data.type.equals("image/png")&&lower.endsWith(".png")||data.type.equals("image/jpeg")&&(lower.endsWith(".jpg")||lower.endsWith(".jpeg"))))throw new IllegalArgumentException("附件扩展名与内容格式不一致。");
        if(data.type.startsWith("image/")){
            try(MemoryCacheImageInputStream input=new MemoryCacheImageInputStream(new ByteArrayInputStream(b))){
                Iterator<ImageReader> readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException();ImageReader reader=readers.next();
                try{reader.setInput(input);int width=reader.getWidth(0),height=reader.getHeight(0);if(width<1||height<1||(long)width*height>16000000)throw new IOException();}finally{reader.dispose();}
            }catch(IOException|RuntimeException e){throw new IllegalArgumentException("图片无法读取或像素过大，请提供不超过 1600 万像素的报告图片。");}
        }
        data.name=name;data.hash=digest(b);return data;
    }
    public static String digest(byte[] content){try{return HashUtils.withoutPrefix(Bytes32Utils.toHex(MessageDigest.getInstance("SHA-256").digest(content)));}catch(Exception e){throw new IllegalStateException("无法计算附件摘要。");}}
}
