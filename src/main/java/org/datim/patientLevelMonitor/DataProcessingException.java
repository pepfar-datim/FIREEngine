package org.datim.patientLevelMonitor;

public class DataProcessingException extends Exception {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  /**
   *
   */
  public DataProcessingException() {
    super();
  }

  /**
   * @param message
   * @param cause
   */
  public DataProcessingException(String message) {
    super(message);
  }

  /**
   *
   * @param cause
   */
  public DataProcessingException(Throwable cause) {
    super(cause);
  }
  /**
   * @param message
   * @param cause
   */
  public DataProcessingException(String message, Throwable cause) {
    super(message, cause);
  }
}
