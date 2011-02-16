package org.gbif.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;

/**
 * @author markus
 */
public class HttpUtilTest {
  @Test
  public void testConditionalGet() throws ParseException, IOException {
    DefaultHttpClient client = new DefaultHttpClient();
    HttpUtil util = new HttpUtil(client);
    Date last = HttpUtil.DATE_FORMAT_RFC2616.parse("Wed, 03 Aug 2009 22:37:31 GMT");
    Date current = HttpUtil.DATE_FORMAT_RFC2616.parse("Wed, 04 Aug 2010 8:14:57 GMT");

    File tmp = File.createTempFile("vocab", ".xml");
    URL url = new URL("http://rs.gbif.org/vocabulary/gbif/resource_type.xml");
    boolean downloaded = util.downloadIfChanged(url, last, tmp);
    assertTrue(downloaded);

    downloaded = util.downloadIfChanged(url, current, tmp);
    assertFalse(downloaded);
  }

  @Test
  public void testMultithreadedCommonsLogDependency() throws ParseException, IOException {
    HttpUtil util = new HttpUtil(HttpUtil.newMultithreadedClient());
  }

}
