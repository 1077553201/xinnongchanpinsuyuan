package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class QualityFileSupportTest {
    private MockMultipartFile file(String name,String text){return new MockMultipartFile("attachment",name,"application/pdf",text.getBytes(StandardCharsets.UTF_8));}
    @Test void checksPdfSignatureEndAndNormalizesFilename(){QualityFileSupport.FileData result=QualityFileSupport.read(file("C:\\fakepath\\报告.PDF","%PDF-1.4\nQA sample\n%%EOF"));assertEquals("报告.PDF",result.getName());assertEquals("application/pdf",result.getType());assertEquals(64,result.getHash().length());}
    @Test void rejectsHtmlSvgTruncatedPdfAndWrongExtension(){for(MockMultipartFile f:new MockMultipartFile[]{file("bad.pdf","<html>not a report</html>"),file("bad.svg","<svg></svg>"),file("bad.pdf","%PDF-1.4\nmissing EOF"),file("bad.png","%PDF-1.4\nQA sample\n%%EOF")})assertThrows(IllegalArgumentException.class,()->QualityFileSupport.read(f));}
    @Test void enforcesEmptyAndSizeLimits(){assertThrows(IllegalArgumentException.class,()->QualityFileSupport.read(file("empty.pdf","")));assertThrows(IllegalArgumentException.class,()->QualityFileSupport.read(new MockMultipartFile("attachment","large.pdf","application/pdf",new byte[QualityFileSupport.MAX_BYTES+1])));}
    @Test void acceptsRealPngAndJpegByContentInsteadOfClaimedMime() throws Exception {for(String kind:new String[]{"png","jpg"}){ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),kind,out);QualityFileSupport.FileData file=QualityFileSupport.read(new MockMultipartFile("attachment","image."+kind,"text/html",out.toByteArray()));assertEquals(kind.equals("png")?"image/png":"image/jpeg",file.getType());}}
    @Test void rejectsMalformedPngAndDigestDependsOnActualBytes(){assertThrows(IllegalArgumentException.class,()->QualityFileSupport.read(new MockMultipartFile("attachment","bad.png","image/png",new byte[]{(byte)137,80,78,71,13,10,26,10})));assertNotEquals(QualityFileSupport.digest(new byte[]{1}),QualityFileSupport.digest(new byte[]{2}));}
}
