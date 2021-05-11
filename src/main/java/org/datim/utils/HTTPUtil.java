package org.datim.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HTTPUtil {

  private final static Logger log = Logger.getLogger("mainLog");

  /**
   * Check if url returns ok, as there is no body to send, GET method is used
   * 
   * @param path
   *          url
   * @param user
   *          user credentials
   * @return true if response is 200/OK, false otherwise
   */
  public static boolean isOk(String path, final User user){
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) (new URL(path).openConnection());
      if (user != null) {
        String userPassword = user.getUsername() + ":" + user.getPassword();
        String encoding = new String(new org.apache.commons.codec.binary.Base64().encode(userPassword.getBytes()));
        connection.setRequestProperty("Authorization", "Basic " + encoding);
      }
      connection.setRequestProperty("Accept-Charset", java.nio.charset.StandardCharsets.UTF_8.name());
      connection.setRequestProperty("Accept-Language", "en-us,en");
      connection.getHeaderFields();
      int c = connection.getResponseCode();
      if (c == HttpURLConnection.HTTP_OK)
        return true;
      if (c == HttpURLConnection.HTTP_UNAUTHORIZED)
        return false;

      InputStream inputStream = null;
      try {
        inputStream = connection.getInputStream();
      } catch (IOException exception) {
        inputStream = connection.getErrorStream();
      }
      String message = "";
      if (inputStream != null) {
        BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) {
          sb.append(line);
          sb.append('\r');
        }
        rd.close();
        message = sb.toString();
      }
      log.severe("Cannot check if credentials are OK. Response code was " + c + " : " + message);
      throw new RuntimeException("Cannot connect to the DHIS server, make sure it is configured correctly. Response code from " + path + " was " + c + ". Check log for additional details");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   *
   * @param path
   *          url to download from
   * @param user
   *          user credentials for BASIC authentication
   * @return json node representing the document
   */
  public static JsonNode getJson(String path, final User user)  throws HTTPUtilException{
    log.info("in getJson:" + path);
    InputStream in = null;
    HttpURLConnection connection = null;
    try {
      //String userPassword = user.getUsername() + ":" + user.getPassword();
      String userPassword = "" + ":" + "";
      String encoding = new String(new org.apache.commons.codec.binary.Base64().encode(userPassword.getBytes()));
      connection = (HttpURLConnection) (new URL(path).openConnection());
      connection.setRequestProperty("Authorization", "Basic " + encoding);
      connection.setRequestProperty("Accept-Charset", java.nio.charset.StandardCharsets.UTF_8.name());
      connection.setRequestProperty("Accept-Language", "en-us,en");
      connection.getHeaderFields();

      if (connection.getResponseCode() == 500) {
        throw new InternalServerException();
      }

      // TODO - need to deal with non 200 responses
      in = connection.getInputStream();
      
      return new ObjectMapper().readTree(in);

    } catch (IOException e) {
      log.info("error occurred:" + e.getMessage());
      if (e instanceof FileNotFoundException) {
        return null;
      }
      throw new RuntimeException(e);
    } catch (RuntimeException e) {
      throw e;
    } finally {
      if (in != null)
        try {
          in.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
    }
  }
  
  public static JsonNode getJsonFromZip(String path, final User user) throws HTTPUtilException{
    String charset = java.nio.charset.StandardCharsets.UTF_8.name();    
    Long t = new Date().getTime();
    InputStream in = null;
    HttpURLConnection connection = null;
    log.info("downloading zip. " + LocalDateTime.now());
    String tempFileName = "/tmp/plm_ocl_tmp" + LocalDateTime.now() + ".zip"; 
    
    //log.info("user: " + user + " path:" + path);
   
    File outputFile = new File(tempFileName);    
    try {
      
      connection = (HttpURLConnection)(new URL(path).openConnection());
      if(user != null && !user.getUsername().equals("")){
        String userPassword = user.getUsername() + ":" + user.getPassword();       
        String encoding = new String(new org.apache.commons.codec.binary.Base64().encode(userPassword.getBytes()));
        connection.setRequestProperty("Authorization", "Basic " + encoding);
      }
      connection.setRequestProperty("Accept-Charset", charset);
      connection.setRequestProperty("Accept-Language", "en-us,en");
      connection.getHeaderFields();

      in = connection.getInputStream();

      Files.copy(in, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      log.info("Downloaded " + path + " to " + outputFile.getAbsolutePath() + ". It took " + (new Date().getTime() - t) + " milliseconds");
      
      ZipFile zipFile = new ZipFile(outputFile);
      try {        
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();           
            System.out.printf("File: %s Size %d  Modified on %TD %n", "entryname: "+ entry.getName(), entry.getSize(), new Date(entry.getTime()));
            InputStream inputStream = null;
            try {
            inputStream = zipFile.getInputStream(entry);
            }catch(Exception ee) {
              log.warning("exception:" + ee.getMessage() + ee.getStackTrace());}            
            JsonNode jn  = new ObjectMapper().readTree(inputStream);                        
            return jn;            
        }       
      } finally {                  
          outputFile.deleteOnExit();
          zipFile.close();
      }        
      return null;
    } catch (IOException e) {
      if(connection != null){
        InputStream inputStream = null;
        try{
          inputStream = connection.getErrorStream();
          String message = "";
          if(inputStream != null){
            BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = rd.readLine()) != null) {
              sb.append(line);
              sb.append('\r');
            }
            rd.close();
            message = sb.toString();
          }
          log.log(Level.WARNING, "Failed to download " + path + " to " + outputFile.getAbsolutePath(), e.getMessage());
          //return new ResponseStatus("" + connection.getResponseCode(), message);
        } catch(IOException e2){
          log.warning(e2.getMessage());
        } finally {
          if(inputStream != null) try{ inputStream.close(); } catch(IOException e3){}
        }
      }
      throw new HTTPUtilException(e);
    } finally {
      if(in != null) try{ in.close(); } catch(IOException e){ e.printStackTrace(); }
    }
  }

  
 
  /**
   * Send xml content
   * 
   * @param path
   *          url to send to
   * @param xml
   *          xml content to send
   * @param method
   *          method to use (e.g. POST or PUT)
   * @param user
   *          user credentials for BASIC authentication
   * @return http response code
   */
  public static JsonNode sendJson(String path, String xml, String method, final User user){
	    HttpURLConnection connection = null;
	    InputStream inputStream = null;
	    try {

	      connection = (HttpURLConnection) (new URL(path).openConnection());
	      connection.setRequestProperty("Content-Type", "application/xml");
	      if(user != null) {
		      String userPassword = user.getUsername() + ":" + user.getPassword();
		      String encoding = new String(new org.apache.commons.codec.binary.Base64().encode(userPassword.getBytes())); 
		      connection.setRequestProperty("Authorization", "Basic " + encoding);
	      }
	      connection.setRequestProperty("Accept-Charset", java.nio.charset.StandardCharsets.UTF_8.name());
	      connection.setRequestProperty("Accept-Language", "en-us,en");
	      connection.setRequestMethod(method);
	      connection.setDoOutput(true);
	      if (!method.equals("DELETE")) {
	        OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream());
	        wr.write(xml);
	        wr.flush();
	      }
	      try {
	        inputStream = connection.getInputStream();
	      } catch(IOException exception) {
	        inputStream = connection.getErrorStream();
	      }
	      return new ObjectMapper().readTree(inputStream);

	    } catch(IOException e){
	      throw new RuntimeException(e);
	    }
	  } 
  
 
  public static class InternalServerException extends RuntimeException {

  }

  
}
