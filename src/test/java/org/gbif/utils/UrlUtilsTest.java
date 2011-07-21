package org.gbif.utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class UrlUtilsTest {

  @Test
  public void testWhitespace() throws Exception {
    assertEquals("http://ecat-dev.gbif.org/repository/zoological%20names.zip",UrlUtils.encodeURLWhitespace("http://ecat-dev.gbif.org/repository/zoological names.zip"));
    assertEquals("http://ecat-dev.gbif.org/repository/zoological%20names.zip", UrlUtils.encodeURLWhitespace("http://ecat-dev.gbif.org/repository/zoological%20names.zip"));
    assertEquals("http://ecat-dev.gbif.org/repository/zoological%20names.do?arg=hello%20kitty", UrlUtils.encodeURLWhitespace("http://ecat-dev.gbif.org/repository/zoological names.do?arg=hello kitty  "));

    // this is the reason to have this method
    boolean syntaxException;
    try {
      URI url = new URI("http://ecat-dev.gbif.org/repository/zoological names.zip");
      syntaxException = false;
    } catch (URISyntaxException e) {
      syntaxException = true;
    }
    assertTrue(syntaxException);

    try {
      URI url = new URI(UrlUtils.encodeURLWhitespace("http://ecat-dev.gbif.org/repository/zoological names.zip"));
      syntaxException = false;
    } catch (URISyntaxException e) {
      syntaxException = true;
    }
    assertFalse(syntaxException);
  }

}
