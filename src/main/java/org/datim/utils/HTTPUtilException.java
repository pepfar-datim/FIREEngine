package org.datim.utils;

public class HTTPUtilException  extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     *
     */
    public HTTPUtilException() {
      super();
    }

    /**
     * @param message
     * @param cause
     */
    public HTTPUtilException(String message) {
      super(message);
    }

    /**
     *
     * @param cause
     */
    public HTTPUtilException(Throwable cause) {
      super(cause);
    }
    /**
     * @param message
     * @param cause
     */
    public HTTPUtilException(String message, Throwable cause) {
      super(message, cause);
    }
  }