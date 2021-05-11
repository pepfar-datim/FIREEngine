package org.datim.patientLevelMonitor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.datim.utils.User;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ImportDataAsync {
  private final static Logger log = Logger.getLogger("mainLog");
  private User user;
  private String serverPath;

  private String contentType = "application/adx+xml";

  public ImportDataAsync() {
    
  }
  
  public ImportDataAsync(User user, String serverPath, String contentType) {
    this.user = user;
    this.serverPath = serverPath;
    this.contentType = contentType;
  }
  public static void main(String[] args) throws Exception{
    new ImportDataAsync().process(new File("/Users/hchichaybelu/Documents/GitHub/PLM/src/PLMTransformationApp/adx/2019-06-03_162228.xml"));
  }

  public void processBatch(String path, String fileList, String url) throws Exception{
    Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(new FileReader(path + "/" + fileList));
    for (CSVRecord c : records) {
      String file = path + "/" + c.get(0);
      log.info(new Date().toString() + " Processing: " + file);
      long t = new Date().getTime();
      //process(file, url);
      log.info(new Date().toString() + " completed in " + ((new Date().getTime() - t) / 1000) + " seconds\n");
    }
  }

  public void process(File file) throws Exception{
    String transcationUID = post(file, serverPath + "api/dataValueSets?preheatCache=true&dryRun=false&async=true&idScheme=CODE&dataElementIdScheme=CODE&orgUnitIdScheme=UID&categoryOptionComboIdScheme=UID&categoryOptionIdScheme=CODE");
    do {
      TimeUnit.SECONDS.sleep(30); // wait 30 seconds between polls
      log.info(".");
    } while (!isDone(transcationUID));
    try (PrintWriter out = new PrintWriter(file + "_import.json")) {
      JsonNode n = getSummary(transcationUID);
      out.print('\n');
      out.println(n.toString());
      log.info(new Date().toString() + " " + n.toString());
    }
  }

  private String post(java.io.File file, String path){

    HttpURLConnection connection = null;
    OutputStream os = null;
    InputStream in = null;
    try {
      connection = (HttpURLConnection) (new URL(path).openConnection());
      connection.setRequestProperty("Content-Type", contentType);
      String userPassword = user.getUsername() + ":" + user.getPassword();
      String encoding = new String(new org.apache.commons.codec.binary.Base64().encode(userPassword.getBytes()));
      connection.setRequestProperty("Authorization", "Basic " + encoding);
      connection.setRequestProperty("Accept-Charset", java.nio.charset.StandardCharsets.UTF_8.name());
      connection.setRequestProperty("Accept-Language", "en-us,en");
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);

      os = connection.getOutputStream();

      FileInputStream is = new FileInputStream(file);
      int n;
      byte[] buffer = new byte[16384];
      while ((n = is.read(buffer)) > -1) {
        os.write(buffer, 0, n); // Don't allow any extra bytes to creep in, final write
      }
      os.flush();
      os.close();
      is.close();

      connection.getHeaderFields();

      int code = connection.getResponseCode();
      log.info(new Date().toString() + " POST to " + path);
      log.info(new Date().toString() + " response " + code);
      if (code == 200 || code == 202) {
        in = connection.getInputStream();
        JsonNode jn = new ObjectMapper().readTree(in);
        String relativeNotifierEndpoint = jn.get("response").get("relativeNotifierEndpoint").asText();
        // not the best, but have to parse "relativeNotifierEndpoint":"/api/system/tasks/DATAVALUE_IMPORT/Uj9xpJj0A6k" to get UID
        return relativeNotifierEndpoint.substring(relativeNotifierEndpoint.lastIndexOf('/') + 1);
      } else {
        // raise error
        throw new RuntimeException("Received " + code + " when 200 was expected");
      }
    } catch (IOException e) {
      if (in != null)
        try {
          in.close();
        } catch (IOException e2) {
        }
      if (os != null)
        try {
          os.close();
        } catch (IOException e2) {
        }
      throw new RuntimeException(e);
    }
  }

  private JsonNode getSummary(String uid){
    InputStream in = null;
    HttpURLConnection connection = null;
    try {
      String path = serverPath + "api/system/taskSummaries/DATAVALUE_IMPORT/" + uid;
      connection = (HttpURLConnection) (new URL(path).openConnection());
      String userPassword = user.getUsername() + ":" + user.getPassword();
      String encoding = new String(new org.apache.commons.codec.binary.Base64().encode(userPassword.getBytes()));
      connection.setRequestProperty("Authorization", "Basic " + encoding);
      connection.setRequestProperty("Accept-Charset", java.nio.charset.StandardCharsets.UTF_8.name());
      connection.setRequestProperty("Accept-Language", "en-us,en");
      connection.setRequestMethod("GET");
      in = connection.getInputStream();
      return new ObjectMapper().readTree(in);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean isDone(String uid){
    InputStream in = null;
    HttpURLConnection connection = null;
    try {
      String path = serverPath + "api/system/tasks/DATAVALUE_IMPORT/" + uid;
      connection = (HttpURLConnection) (new URL(path).openConnection());
      String userPassword = user.getUsername() + ":" + user.getPassword();
      String encoding = new String(new org.apache.commons.codec.binary.Base64().encode(userPassword.getBytes()));
      connection.setRequestProperty("Authorization", "Basic " + encoding);
      connection.setRequestProperty("Accept-Charset", java.nio.charset.StandardCharsets.UTF_8.name());
      connection.setRequestProperty("Accept-Language", "en-us,en");
      connection.setRequestMethod("GET");
      in = connection.getInputStream();
      JsonNode jn = new ObjectMapper().readTree(in);
      for (JsonNode n : jn) {
        if (n.has("completed") && n.get("completed").asBoolean())
          return true;
      }
      return false;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
