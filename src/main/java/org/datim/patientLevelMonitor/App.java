package org.datim.patientLevelMonitor;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FilenameUtils;

import ca.uhn.fhir.parser.DataFormatException;

import org.datim.patientLevelMonitor.MetadataMappings;
import org.datim.patientLevelMonitor.TerminologyServiceOCLImpl;
import org.datim.utils.HTTPUtilException;

public class App 
{
  private final static Logger log = Logger.getLogger("mainLog");
  private static String LOG_PATH;
  private static String ADX_PATH;
  private static FileHandler logFile;

  public static void main(String[] args){
    try {
      Options options = new Options();
      options.addOption("p", true, "location of properties file");
      options.addOption("q", true, "location of plm queue");
      CommandLineParser cmdParser = new DefaultParser();
      CommandLine cmd = cmdParser.parse(options, args);
      Configuration.loadConfiguration(cmd.getOptionValue("p"));
      
      String archiveLocation = Configuration.getArchivePath();
      boolean isParcialProcessingAllowed = Configuration.isParcialProcessingAllowed();
      String fileQueueLocation = cmd.getOptionValue("q");
      
      LOG_PATH = Configuration.getLogPath();

      File logPath = new File(LOG_PATH + File.separator + (new SimpleDateFormat("yyyy-MM-dd")).format(new Date()) + ".log");
      logPath.getParentFile().mkdirs();

      logFile = new FileHandler(logPath.getAbsolutePath(), true);

      logFile.setFormatter(new SimpleFormatter());
      log.addHandler(logFile);

      File folder = new File(fileQueueLocation);
      File[] files = folder.listFiles();
     
      // Process files in FIFO order
      Arrays.sort(files, new Comparator<File>() {
        public int compare(File f1, File f2){
          return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
        }
      });
     
      BundleParser parser = new BundleParser();
      if (Configuration.getInputBundleType() != null && Configuration.getInputBundleType().equalsIgnoreCase(BundleParser.BUNDLE_TYPE_QUESTIONNAIRE_RESPONSE)) {
        parser = new BundleParserQR();
      }
            
      Map<String, Integer> calculatedDataStoreAll = new HashMap<String, Integer>();
      
      try {
        log.info("Getting mappings");
        MetadataMappings mm = new MetadataMappings();
        mm.loadIndicatorMappings(new  TerminologyServiceOCLImpl(Configuration.getOclDomain(), Configuration.getDhisdomain(), Configuration.getOclVersion(), Configuration.getOCLUser(), Configuration.getUser())); 
        
        log.info("Validating PLM expressions from OCL");  
        mm.validateOptionsWithValidExpression(); 
        
        log.info("Total bundle files: " + files.length);
        log.info("inputBundleType: " + Configuration.getInputBundleType());
        for (File inputFile : files) {
         log.info("\n\nProcessing file: " + inputFile.getName());                 
         String extension = FilenameUtils.getExtension(inputFile.getPath());
         if (!extension.equals("json") && !extension.equals("xml")) {
           log.info("Not a json or xml file, skip:" + inputFile.getName());
           continue;
         }         
         try {
            Data data = parser.parse(inputFile, mm, Configuration.getSchematronValidatorLocation());
            log.info("START transformPatientData call");
            data.transformPatientData(mm, calculatedDataStoreAll);              
         } catch (DataProcessingException excep) {
            log.info("DataProcessing exception: " + excep.getMessage());
            if (!isParcialProcessingAllowed) {
              throw excep;
            }
         } catch (DataFormatException excep) {
            log.info("Data Format exception: " + excep.getMessage() + " File: " + inputFile.getName());
            if (!isParcialProcessingAllowed) {
              throw excep;
            }
         }  catch (RuntimeException excep) {
            log.info("Data Format exception: " + excep.getMessage());
            if (!isParcialProcessingAllowed) {
              throw excep;
            }
          }
        }
                
        //adx generation  
        log.info("Generating adx");
        ADX_PATH = Configuration.getAdxPath();
        String processID = (new SimpleDateFormat("yyyy-MM-dd_HHmmss")).format(new Date()) ;
        Data d = new Data(null);
        log.info("calculatedDataStoreAll size: " + calculatedDataStoreAll.size() );
        File outputAdx = d.transform(mm, ADX_PATH, processID, calculatedDataStoreAll);
       
        log.info("ADX file written out to: " + outputAdx.getAbsolutePath());
        
        //importing
        if (calculatedDataStoreAll.size() > 0 && Configuration.getImportIntoHMIS()) {          
          new ImportDataAsync(Configuration.getUser(), Configuration.getDhisdomain(), "application/adx+xml").process(outputAdx);
        }
        // archiving done only if both generation and importing are successful
        log.info("Archive files .. ." + Configuration.getArchiveFilesInQueue());
        if ( Configuration.getArchiveFilesInQueue()) {
         for (File inputFile : files) {
            boolean isArchivingSuccessfull = inputFile.renameTo(new File(archiveLocation + inputFile.getName()));
            if (isArchivingSuccessfull) {
              log.info("File archived: " + archiveLocation + inputFile.getName());
            }
          }
        }
        
      } catch (HTTPUtilException excep) {
        log.info("HTTP exception: " + excep.getMessage());
      } catch (DataProcessingException excep) {
        log.info("DataProcessing exception: " + excep.getMessage());
      } catch (DataFormatException excep) {
        log.info("Data Format exception: " + excep.getMessage());
      }
    } catch (IOException excep) {
      log.severe("Unable to load configuration file. " + excep);
    } catch (ParseException excep) {
      log.severe("Unable to parse command line arguments. " + excep);
    } catch (HTTPUtilException excep) {
      log.severe("HTTP exception: " + excep);
    } catch (Exception excep) {
      log.info("Exception: " + excep.getMessage());
    } 
  }

}
