package com.erhms.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.*;
import static org.junit.jupiter.api.Assertions.*;

class ContractPdfRendererTest {
    static final String W="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    ContractTemplateData templates(){return new ContractTemplateData(new WorkflowData(null),new ObjectMapper());}
    Map<String,Object> values(){var templates=templates();return templates.values(ContractTemplateFixtures.record(ContractTemplateFixtures.completeDetails(templates)),null);}

    static byte[] documentXml(byte[] docx)throws Exception{
        try(var zip=new ZipInputStream(new ByteArrayInputStream(docx))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())if(entry.getName().equals("word/document.xml"))return zip.readAllBytes();}
        throw new AssertionError("Missing Word document XML");
    }
    static Document xml(byte[] bytes)throws Exception{var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));}
    static String text(Node node){var elements=((Element)node).getElementsByTagNameNS(W,"t");var result=new StringBuilder();for(int i=0;i<elements.getLength();i++)result.append(elements.item(i).getTextContent());return result.toString();}
    static String normalized(String text){return Normalizer.normalize(text,Normalizer.Form.NFC).replaceAll("(?U)\\s+","");}

    @Test void fillingPreservesTablesSectionsAndEscapesUserTextWithoutInjectingWordMarkup()throws Exception{
        var renderer=new ContractPdfRenderer();var data=values();data.put("studentName","Nguyễn Anh Đức & <Công ty>");
        var filled=renderer.filledDocx(data);byte[] raw=documentXml(filled);Document document=xml(raw);
        assertTrue(document.getDocumentElement().getTextContent().contains("Nguyễn Anh Đức & <Công ty>"));
        assertEquals(1,document.getElementsByTagNameNS(W,"tbl").getLength());
        assertEquals(1,document.getElementsByTagNameNS(W,"sectPr").getLength());
        assertFalse(new String(raw,StandardCharsets.UTF_8).contains("${"));
        assertTrue(document.getElementsByTagNameNS(W,"br").getLength()>0,"Multiline fields retain line breaks");
        assertEquals("11906",((Element)document.getElementsByTagNameNS(W,"pgSz").item(0)).getAttributeNS(W,"w"));
    }

    @Test void pdfRetainsTheRevisedClausesPrefilledInformationVietnameseAndPageNumbering()throws Exception{
        var templates=templates();var data=values();data.put("additionalTerms","Ưu tiên lịch học buổi tối theo thỏa thuận.");data.put("additionalPaymentSchedule","Nội dung lịch thanh toán cũ không được xuất.");templates.requireComplete(data);
        byte[] bytes=new ContractPdfRenderer().pdf(data);assertTrue(bytes.length>20000);
        assertEquals("%PDF-",new String(bytes,0,5,StandardCharsets.US_ASCII));
        try(var document=Loader.loadPDF(bytes)){
            String extracted=new PDFTextStripper().getText(document),full=normalized(extracted);
            assertTrue(full.contains(normalized("Nguyễn Anh Đức")));assertTrue(full.contains(normalized(EnrollmentCourses.NAMES.get(0))));
            assertTrue(extracted.contains("13.500.000"));assertTrue(extracted.contains("500.000"));
            assertTrue(full.contains(normalized("Ưu tiên lịch học buổi tối theo thỏa thuận.")));assertFalse(full.contains(normalized("Nội dung lịch thanh toán cũ không được xuất.")));
            assertTrue(full.contains(normalized("Mười bốn triệu đồng")));assertFalse(full.contains("PHỤLỤC03TIẾPTHEO"));
            assertFalse(extracted.contains("${"));assertTrue(document.getNumberOfPages()>=4);
            try(var source=new ClassPathResource("contracts/BK_VTCOMS_Hop_dong_dao_tao_nghe.docx").getInputStream()){
                var body=(Element)xml(documentXml(source.readAllBytes())).getElementsByTagNameNS(W,"body").item(0);
                int index=0;
                for(Node child=body.getFirstChild();child!=null;child=child.getNextSibling()){
                    if(!(child instanceof Element element))continue;
                    if(element.getLocalName().equals("p")){
                        String clause=text(element);
                        if(index!=3&&!clause.isBlank()&&!clause.matches("(?s).*\\.{2,}.*"))assertTrue(full.contains(normalized(clause)),"Original clause missing from PDF: "+clause);
                    }
                    index++;
                }
            }
            assertEquals(595.3,document.getPage(0).getMediaBox().getWidth(),1);
            assertEquals(841.9,document.getPage(0).getMediaBox().getHeight(),1);
            var last=new PDFTextStripper();last.setStartPage(document.getNumberOfPages());last.setEndPage(document.getNumberOfPages());
            assertTrue(normalized(last.getText(document)).contains("Trang"+document.getNumberOfPages()+"/"+document.getNumberOfPages()),"Footer updates PAGE and NUMPAGES");
            var artifacts=Path.of("target","contract-preview");Files.createDirectories(artifacts);Files.write(artifacts.resolve("hop-dong-da-dien.pdf"),bytes);
            var render=new PDFRenderer(document);
            for(int page:new int[]{0,1,document.getNumberOfPages()-1})ImageIO.write(render.renderImageWithDPI(page,100),"png",artifacts.resolve("page-"+(page+1)+".png").toFile());
        }
    }
}
