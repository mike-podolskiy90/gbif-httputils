package org.gbif.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.http.StatusLine;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;

/**
 * @author markus
 */
public class HttpUtilTest {
  private Logger log = LoggerFactory.getLogger(this.getClass());

  @Test
  public void testConditionalGet() throws ParseException, IOException {
    DefaultHttpClient client = new DefaultHttpClient();
    HttpUtil util = new HttpUtil(client);
    Date last = HttpUtil.DATE_FORMAT_RFC2616.parse("Wed, 03 Aug 2009 22:37:31 GMT");
    Date current = HttpUtil.DATE_FORMAT_RFC2616.parse("Sat, 04 June 2011 8:14:57 GMT");

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

  @Test
  public void testClientRedirect() throws ParseException, IOException {
    HttpUtil util = new HttpUtil(new DefaultHttpClient());
    File tmp = File.createTempFile("httputils", "test");
    tmp.deleteOnExit();
    // a redirect to http://rs.gbif.org/extension/gbif/1.0/distribution.xml
    StatusLine status = util.download("http://rs.gbif.org/terms/1.0/Distribution",tmp);
    assertTrue(HttpUtil.success(status));

    // assert false non 404s
    status = util.download("http://rs.gbif.org/hoppladi/hopplaho/hallodrian/tim/is/back.txt", tmp);
    assertFalse(HttpUtil.success(status));
    assertEquals(404, status.getStatusCode());

  }

}
