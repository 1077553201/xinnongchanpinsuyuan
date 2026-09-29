package org.nongchanpin.constants;

import java.lang.Exception;
import java.lang.RuntimeException;
import java.lang.String;

public class ContractConstants {
  public static String AgriTraceAbi;

  public static String AgriTraceBinary;

  public static String AgriTraceGmBinary;

  static {
    try {
      AgriTraceAbi = org.apache.commons.io.IOUtils.toString(Thread.currentThread().getContextClassLoader().getResource("abi/AgriTrace.abi"));
      AgriTraceBinary = org.apache.commons.io.IOUtils.toString(Thread.currentThread().getContextClassLoader().getResource("bin/ecc/AgriTrace.bin"));
      AgriTraceGmBinary = org.apache.commons.io.IOUtils.toString(Thread.currentThread().getContextClassLoader().getResource("bin/sm/AgriTrace.bin"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
