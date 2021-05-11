package org.datim.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.lang3.StringUtils;

public class HMISDataValue {

  private static Logger log = Logger.getLogger(HMISDataValue.class.getName());
  private String dataelement;
  private String period; 
  private String orgUnit;
  private String categoryOptionCombo;
  private String attributeOptionCombo;
  // FIXME need to support string type
  private String value;
  private String comment;
  
  public HMISDataValue( String dataelement, String period, String orgUnit, String categoryOptionCombo,String attributeOptionCombo, String value, String comment) {
    super();
    this.dataelement =  dataelement;
    this.period = period;
    this.orgUnit = orgUnit;
    this.value = value;
    this.categoryOptionCombo = categoryOptionCombo;
    this.attributeOptionCombo = attributeOptionCombo;
    this.comment = comment;
  }
  
  public String getDataelement(){
    return this.dataelement;
  }
  
  public String getPeriod(){
    return this.period;
  }
  public String getOrgUnit(){
    return this.orgUnit;
  }
  public String getCategoryOptionCombo(){
    return this.categoryOptionCombo;
  }
  public String getAttributeOptionCombo(){
    return this.attributeOptionCombo;
  }
  public String getValue(){
    return this.value;
  }
  public String getComment(){
    return this.comment;
  }
  

  public static void writeToCSV(File f, HashMap<String, Object[]> dataToWrite){
    String[] headers = {"dataelement",  "period", "orgUnit",  "categoryOptionCombo", "attributeOptionCombo", "value", "comment"};

    if(dataToWrite.size() == 0)
      return;

    FileWriter fileWriter = null;
    CSVPrinter printer = null;
    try{
      CSVFormat csvFormat = CSVFormat.DEFAULT.withHeader(headers).withRecordSeparator("\n").withQuoteMode(QuoteMode.ALL);
      fileWriter = new FileWriter(f);
      printer = new CSVPrinter(fileWriter, csvFormat);
      for(String c : dataToWrite.keySet()){
        printer.printRecord(dataToWrite.get(c));
      }
      fileWriter.flush();
    } catch(IOException e){
      e.printStackTrace();
    } finally {
      try {
        if(fileWriter != null){
          fileWriter.flush();
          fileWriter.close();
        }
        if(printer != null) printer.close();
      } catch (IOException e) {
        log.info("Error while flushing/closing fileWriter/csvPrinter: " + e.getMessage());
      }
    }
  }

  public static void writeToADX(File file, List<HMISDataValue> data) throws IOException{

    XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
    String encoding = "UTF-8";
    XMLStreamWriter writer = null;

    try (FileOutputStream os = new FileOutputStream(file)){

        writer = outputFactory.createXMLStreamWriter(os, encoding);

        writer.writeStartDocument(encoding,"1.0");
        writer.writeStartElement("adx");
        writer.writeDefaultNamespace("urn:ihe:qrph:adx:2015");
        writer.writeNamespace("xsi","http://www.w3.org/2001/XMLSchema-instance");
        writer.writeAttribute("http://www.w3.org/2001/XMLSchema-instance", "schemaLocation", "urn:ihe:qrph:adx:2015 ../schema/adx_loose.xsd");

        /*
         * xs:dateTime - uses ISO 8601 Time zone
         * http://www.w3schools.com/Xml/schema_dtypes_date.asp
         * http://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
         */
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        writer.writeAttribute("exported", sdf.format(new java.util.Date()));

        String pe, ou, ao, de, cc, value, comment;
        String ou_prev = null;
        boolean groupOpen = false;

        for (HMISDataValue dd: data) {
          ou = dd.getOrgUnit();
          ao = dd.getAttributeOptionCombo();
          de = dd.getDataelement();
          pe = getAdxPeriod(dd.getPeriod());
          // FIXME adx needs SDMX period format...
          //pe = "2016-10-01/P1Y";
          cc = dd.getCategoryOptionCombo();
          comment = dd.getComment();
          value = dd.getValue() +"";

          if(!(ou.equals(ou_prev) )){
            if(groupOpen){
                writer.writeEndElement(); // close group
            }
            ou_prev = ou;
            // open group
            groupOpen = true;
            writer.writeStartElement("group");
            writer.writeAttribute("orgUnit", ou);
            writer.writeAttribute("attributeOptionCombo", ao);
            writer.writeAttribute("period", pe);
          }
          writer.writeEmptyElement("dataValue");
          writer.writeAttribute("dataElement", de);
          if(StringUtils.isNumeric(value)){
          writer.writeAttribute("value", value);
          }else{
            writer.writeAttribute("value", "");
            writer.writeStartElement("annotation");
            writer.writeCharacters(value);
            writer.writeEndElement();
          }
          // TODO proper adx approach is to use categories and category options, instead of category option combo
          writer.writeAttribute("categoryOptionCombo", cc);
          writer.writeAttribute("comment", comment);
        }
        if(groupOpen){
          writer.writeEndElement(); // close group
        }

        writer.writeEndElement();
        writer.flush();
    } catch(XMLStreamException e){
        throw new RuntimeException(e);
    }  catch(IOException e){
        throw new RuntimeException(e);
    } finally {
        if(writer != null){
            try{writer.close();} catch(XMLStreamException e){}
        }
    }
  }
  public static String getAdxPeriod(String period){ 
    return period.substring(0, 4) + "-10-01/P1Y";
  }
}
