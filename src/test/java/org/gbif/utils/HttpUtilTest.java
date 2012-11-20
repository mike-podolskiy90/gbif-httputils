package org.gbif.utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;

import org.apache.http.StatusLine;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpUtilTest {

  /**
   * Tests a condition get against an apache http server within GBIF.
   * If the file being tested against has been changed in the last 24h this test is expected to fail!
   * @see <a href="http://dev.gbif.org/issues/browse/GBIFCOM-77">GBIFCOM-77</a>
   */
  @Test
  public void testConditionalGet() throws ParseException, IOException {
    DefaultHttpClient client = new DefaultHttpClient();
    HttpUtil util = new HttpUtil(client);
    // We know for sure it has changed since this date
    Date last = HttpUtil.parseHeaderDate("Wed, 03 Aug 2009 22:37:31 GMT");
    File tmp = File.createTempFile("vocab", ".xml");
    URL url = new URL("http://rs.gbif.org/vocabulary/gbif/rank.xml");
    assertTrue(util.downloadIfChanged(url, last, tmp));
    
    // Verify that it does not download with a conditional get of a day before "now"
    // we dont use now because the apache server on the other side might have a slightly different clock and timezone
    // configured that might lead to a mismatch.
    //  If the rank file was modified within the last 24h this test could fail though !!!
    Date nearlyNow = new Date(System.currentTimeMillis() - 24*1000*60*60);
    assertFalse(util.downloadIfChanged(url, nearlyNow, tmp));
  }

  /**
   * Testing if the IPT dwca file serving respects the is-modified-since.
   * Ignoring this test as its only a manual one to run to check the IPT!
   */
  @Test
  @Ignore
  public void testIptConditionalGet() throws ParseException, IOException {
    DefaultHttpClient client = new DefaultHttpClient();
    HttpUtil util = new HttpUtil(client);
    // 2010-08-30
    Date last = HttpUtil.parseHeaderDate("Wed, 03 Aug 2009 22:37:31 GMT");
    Date current = HttpUtil.parseHeaderDate("Sat, 04 June 2011 8:14:57 GMT");
    //current = new Date();

    File tmp = File.createTempFile("dwca", ".zip");
    URL url = new URL("http://ipt.gbif.org/archive.do?r=masswildlifetim");
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
    StatusLine status = util.download("http://rs.gbif.org/terms/1.0/Distribution", tmp);
    assertTrue(HttpUtil.success(status));

    // assert false non 404s
    status = util.download("http://rs.gbif.org/hoppladi/hopplaho/hallodrian/tim/is/back.txt", tmp);
    assertFalse(HttpUtil.success(status));
    assertEquals(404, status.getStatusCode());

  }

}
