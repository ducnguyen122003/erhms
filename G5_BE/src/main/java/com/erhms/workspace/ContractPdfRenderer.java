package com.erhms.workspace;

import java.io.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.docx4j.Docx4J;
import org.docx4j.fonts.*;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;

/** Fills the supplied Word template, then renders its original paragraphs/tables as PDF. */
@Component
public class ContractPdfRenderer {
    private static final String W="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final Pattern TOKEN=Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9]*)}");
    private static final Set<String> MONEY=Set.of("grossTuition","discountAmount","tuitionDue","depositDue","otherChargesDue","initialTotal","equipmentDepositTotal","actualDepositReceived","agreedDepositDeduction","disputedDepositAmount","immediateDepositRefund");
    private static final List<String> DATES=List.of("agreementDate","centerLicenseIssuedOn","studentDateOfBirth","startsOn","endsOn","latestOpeningOn","paymentDueOn","effectiveOn","regulationIssuedOn","regulationProvidedOn","otherChargesDueOn","equipmentReturnDueOn","equipmentReturnedOn","depositRefundDueOn");
    private final byte[] template;
    private static boolean fontsReady;

    public ContractPdfRenderer() throws IOException {
        try(var input=new ClassPathResource("contracts/training-contract-template.docx").getInputStream()){template=input.readAllBytes();}
    }

    public byte[] pdf(Map<String,Object> values) {
        try {
            var document=WordprocessingMLPackage.load(new ByteArrayInputStream(filledDocx(values)));
            document.setFontMapper(fontMapper());
            var output=new ByteArrayOutputStream();
            Docx4J.toPDF(document,output);
            return output.toByteArray();
        } catch(Exception failure) {
            throw new IllegalStateException("Không thể tạo PDF hợp đồng từ mẫu đã cung cấp.",failure);
        }
    }

    public byte[] filledDocx(Map<String,Object> values) throws Exception {
        Map<String,String> replacements=formatted(values);
        var output=new ByteArrayOutputStream();
        try(var input=new ZipInputStream(new ByteArrayInputStream(template));var zip=new ZipOutputStream(output)){
            for(ZipEntry entry;(entry=input.getNextEntry())!=null;){
                byte[] content=input.readAllBytes();
                if(entry.getName().equals("word/document.xml"))content=fillXml(content,replacements);
                zip.putNextEntry(new ZipEntry(entry.getName()));zip.write(content);zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static synchronized Mapper fontMapper() throws Exception {
        // These redistributable fonts ship with docx4j, so Linux exports do not depend on Windows fonts.
        if(!fontsReady){
            PhysicalFonts.setRegex("(?i).*(tinos|arimo|carlito|caladea|cousine|notosanssymbols).*");
            PhysicalFonts.discoverJarFonts();fontsReady=true;
        }
        var mapper=new BestMatchingMapper();
        for(var entry:Map.of("Times New Roman","Tinos","Cambria","Caladea","Calibri","Carlito","Arial","Arimo","Courier New","Cousine").entrySet()){
            var font=PhysicalFonts.get(entry.getValue());if(font!=null)mapper.put(entry.getKey(),font);
        }
        var symbols=ContractPdfRenderer.class.getClassLoader().getResource("fonts-symbol/Noto_Sans_Symbols_2/NotoSansSymbols2-Regular.ttf");
        if(symbols!=null){
            var faces=PhysicalFonts.getPhysicalFont("Noto Sans Symbols 2",symbols.toURI());
            if(!faces.isEmpty()){
                var face=faces.get(0);PhysicalFonts.put(face.getName(),face);
                mapper.put(face.getName(),face);mapper.put("Segoe UI Symbol",face);mapper.put("Noto Sans Symbols 2",face);
            }
        }
        return mapper;
    }

    private static byte[] fillXml(byte[] xml,Map<String,String> values) throws Exception {
        var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
        var document=factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        var textNodes=document.getElementsByTagNameNS(W,"t");
        // Copy first: multiline replacements add text nodes to the same document.
        List<Element> text=new ArrayList<>();for(int i=0;i<textNodes.getLength();i++)text.add((Element)textNodes.item(i));
        for(var element:text){
            String before=element.getTextContent();Matcher matcher=TOKEN.matcher(before);StringBuilder result=new StringBuilder();
            while(matcher.find())matcher.appendReplacement(result,Matcher.quoteReplacement(values.getOrDefault(matcher.group(1),"—")));
            matcher.appendTail(result);
            setText(element,result.toString());
        }
        var transformerFactory=TransformerFactory.newInstance();transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
        var transformer=transformerFactory.newTransformer();transformer.setOutputProperty(OutputKeys.ENCODING,"UTF-8");
        var output=new ByteArrayOutputStream();transformer.transform(new DOMSource(document),new StreamResult(output));return output.toByteArray();
    }

    private static void setText(Element element,String value){
        String[] lines=value.replace("\r\n","\n").replace('\r','\n').split("\n",-1);
        element.setTextContent(lines[0]);element.setAttributeNS(XMLConstants.XML_NS_URI,"xml:space","preserve");
        Node cursor=element;
        for(int i=1;i<lines.length;i++){
            Element lineBreak=element.getOwnerDocument().createElementNS(W,"w:br");
            cursor.getParentNode().insertBefore(lineBreak,cursor.getNextSibling());
            Element next=element.getOwnerDocument().createElementNS(W,"w:t");next.setAttributeNS(XMLConstants.XML_NS_URI,"xml:space","preserve");next.setTextContent(lines[i]);
            lineBreak.getParentNode().insertBefore(next,lineBreak.getNextSibling());cursor=next;
        }
    }

    private static Map<String,String> formatted(Map<String,Object> raw){
        Map<String,String> values=new HashMap<>();
        var money=NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));money.setMaximumFractionDigits(2);
        raw.forEach((key,value)->{
            String text=value==null?"":value instanceof BigDecimal number?number.toPlainString():String.valueOf(value);
            if(MONEY.contains(key)||key.matches("fee[1-9]SeparateAmount|asset[1-3](RecordedValue|DepositAmount)")){
                try{text=money.format(new BigDecimal(text));}catch(NumberFormatException ignored){text="0";}
            }
            values.put(key,text.isBlank()?"—":text);
        });
        for(String key:DATES){
            LocalDate date=null;try{date=LocalDate.parse(Objects.toString(raw.get(key),""));}catch(Exception ignored){}
            values.put(key+"Day",date==null?"—":String.format(Locale.ROOT,"%02d",date.getDayOfMonth()));
            values.put(key+"Month",date==null?"—":String.format(Locale.ROOT,"%02d",date.getMonthValue()));
            values.put(key+"Year",date==null?"—":Integer.toString(date.getYear()));
        }
        String handedOver=Objects.toString(raw.get("equipmentHandedOverOn"),"");
        try{handedOver=LocalDate.parse(handedOver).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));}catch(Exception ignored){}
        String place=Objects.toString(raw.get("equipmentHandoverLocation"),"");
        values.put("equipmentHandoverDetails",handedOver.isBlank()&&place.isBlank()?"Chưa bàn giao":String.join(" — ",List.of(handedOver,place).stream().filter(v->!v.isBlank()).toList()));
        String scope=Objects.toString(raw.get("equipmentUseScope"),"").toLowerCase(Locale.ROOT);
        values.put("equipmentUseAtClass",scope.contains("lớp")||scope.equals("class")?"☑":"☐");
        values.put("equipmentUseTakeHome",scope.contains("mang về")||scope.equals("take_home")?"☑":"☐");
        values.put("equipmentUseOther",scope.contains("khác")||scope.equals("other")?"☑":"☐");
        if(!Boolean.TRUE.equals(raw.get("hasGuardian")))for(String key:List.of("guardianName","guardianIdentityNumber","guardianAddress","guardianPhone","guardianRelationshipAndAuthority"))values.put(key,"Không áp dụng");
        for(var entry:Map.of("additionalTerms","Thỏa thuận bổ sung: ").entrySet()){
            String content=Objects.toString(raw.get(entry.getKey()),"").strip();values.put(entry.getKey(),content.isEmpty()?"":entry.getValue()+content);
        }
        return values;
    }
}
