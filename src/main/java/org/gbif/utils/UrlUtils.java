package org.gbif.utils;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

import org.apache.commons.lang.StringUtils;

public class UrlUtils {

  public static String encodeURLWhitespace(final String s) {
    return StringUtils.trimToEmpty(s).replaceAll(" ", "%20");
  }

}
