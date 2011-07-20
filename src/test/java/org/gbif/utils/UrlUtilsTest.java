package org.gbif.utils;

import java.io.IOException;
import java.text.ParseException;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class UrlUtilsTest {

  @Test
  public void testWhitespace() {
    assertEquals("http://ecat-dev.gbif.org/repository/zoological%20names.zip",UrlUtils.encodeURLWhitespace("http://ecat-dev.gbif.org/repository/zoological names.zip"));
    assertEquals("http://ecat-dev.gbif.org/repository/zoological%20names.zip", UrlUtils.encodeURLWhitespace("http://ecat-dev.gbif.org/repository/zoological%20names.zip"));
    assertEquals("http://ecat-dev.gbif.org/repository/zoological%20names.do?arg=hello%20kitty", UrlUtils.encodeURLWhitespace("http://ecat-dev.gbif.org/repository/zoological names.do?arg=hello kitty  "));
  }

}
