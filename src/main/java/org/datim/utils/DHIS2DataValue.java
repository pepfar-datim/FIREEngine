package org.datim.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.lang3.StringUtils;
import org.datim.patientLevelMonitor.CategoryOption;
import org.datim.patientLevelMonitor.Configuration;
import org.datim.patientLevelMonitor.Data;
import org.datim.patientLevelMonitor.DataProcessingException;

public class DHIS2DataValue {

  private static Logger log = Logger.getLogger(DHIS2DataValue.class.getName());
  private String dataelement;
  private String period; 
  private String orgUnit;  
  private List <String> categoryOptionList;
  private String fundingMechanism;
  
  private int value;
  
  
  public DHIS2DataValue( String dataelement, String period, String orgUnit,String fundingMechanism, List<String> categoryOptionList, int value) {
    super();
    this.dataelement =  dataelement;
    this.period = period;
    this.orgUnit = orgUnit;
    this.value = value;
    this.categoryOptionList = categoryOptionList;
    this.fundingMechanism = fundingMechanism;
    
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
  public List<String> getcategoryOptionList(){
    return this.categoryOptionList;
  }
  public String getFundingMechanism(){
    return this.fundingMechanism;
  }
  public int getValue(){
    return this.value;
  }
  
  
  /*public static void writeToCSV(File f, HashMap<String, Object[]> dataToWrite){
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
  }*/

  public static void writeToADX(File file, List<DHIS2DataValue> data, Map<String, CategoryOption> mapOptionIDtoCategoryOptionObj) throws IOException, DataProcessingException{

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

        String pe, ou, ao, de, cc, value;
        String ou_prev = null;
        String pe_prev = null;
        List<String>categoryOptionList = null;
        boolean groupOpen = false;
        int i = 0;
        for (DHIS2DataValue dd: data) {
          ou = dd.getOrgUnit();
          ao = dd.getFundingMechanism();
          de = dd.getDataelement();        
          pe = dd.getPeriod();
         
          categoryOptionList = dd.getcategoryOptionList();
          value = dd.getValue() + "";

          if(!ou.equals(ou_prev)){
            if(groupOpen){
                writer.writeEndElement(); // close group
            }            
            ou_prev = ou;
            pe_prev = pe;           
            // open group
            groupOpen = true;
            writer.writeStartElement("group");
            writer.writeAttribute("orgUnit", ou);
            writer.writeAttribute("attributeOptionCombo", ao);
            writer.writeAttribute("period", pe);
          }else if (!pe.equals(pe_prev) ) {
            if(groupOpen){
              writer.writeEndElement(); // close group
            }            
            pe_prev = pe;            
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
          
          for (String s: categoryOptionList) {           
            String[] catOption = s.split(Data.delimiter2);            
            String cat = catOption[0];
            String optionID = catOption[1];            
            CategoryOption co = mapOptionIDtoCategoryOptionObj.get(optionID);           
            String optionCode = co.getCode(); //co.getCodeInXMLformat(); no need to convert to xml, it seems the system automatically converts it            
            writer.writeAttribute(cat, optionCode);
          }         
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
  
  
  /**
   * 
   * @param period such as 2019-05-03
   * @return
   * @throws DataProcessingException 
   */
  public static String getAdxPeriod(String period) throws DataProcessingException {   
    
    String pe = "";
    if (!period.equals("") && period.trim().length() == 10) {
      try {
        String year = period.substring(0, 4);
        String month = period.substring(5,7);
        String firstMonthInQuarter = getFirstMonthInQuarter(Integer.parseInt(month));
        pe = year + "-" + firstMonthInQuarter + "-01" + "/P3M";               
      }catch(NumberFormatException nfe) {
        String msg = "error when getAdxPeriod : " + period + " " + nfe.getMessage();
        log.warning(msg);   
        if (!Configuration.isParcialProcessingAllowed())
        {
          throw new DataProcessingException(msg);
        }
      }      
    }
    return pe;
    
  }
  
  private static String getFirstMonthInQuarter(int month) {
    if (month <= 3 && month >= 1) {
      return "01";
    }else if (month <= 6 && month >= 4) {
      return "04";
    }else if (month <= 9 && month >= 7) {
      return "07";
    }else if (month <= 12 && month >=10) {
      return "10";
    }
    return "";
  }
  
}
