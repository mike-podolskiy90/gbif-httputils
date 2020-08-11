package org.gbif.utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpUtilTest {

  @Test
  public void testClientRedirect() throws ParseException, IOException {
    HttpUtil util = new HttpUtil(HttpClientBuilder.create().build());
    File tmp = File.createTempFile("httputils", "test");
    tmp.deleteOnExit();
    // a redirect to http://rs.gbif.org/extension/gbif/1.0/distribution.xml
    int status = util.download("http://rs.gbif.org/terms/1.0/Distribution", tmp);
    assertTrue(HttpUtil.success(status));

    // assert false non 404s
    status = util.download("http://rs.gbif.org/hoppladi/hopplaho/hallodrian/tim/is/back.txt", tmp);
    assertFalse(HttpUtil.success(status));
    assertEquals(404, status);

    // HTTP 307
    status = util.download("https://httpstat.us/307", tmp);
    assertTrue(HttpUtil.success(status));

    // HTTP 308
    status = util.download("https://httpstat.us/308", tmp);
    assertTrue(HttpUtil.success(status));
  }

  /**
   * Tests a condition get against an apache http server within GBIF.
   * If the file being tested against has been changed in the last 24h this test is expected to fail!
   *
   * @see <a href="http://dev.gbif.org/issues/browse/GBIFCOM-77">GBIFCOM-77</a>
   */
  @Test
  public void testConditionalGet() throws ParseException, IOException {
    CloseableHttpClient client = HttpClientBuilder.create().build();
    HttpUtil util = new HttpUtil(client);
    // We know for sure it has changed since this date
    Date last = HttpUtil.parseHeaderDate("Wed, 03 Aug 2009 22:37:31 GMT");
    File tmp = File.createTempFile("vocab", ".xml");
    URL url = new URL("http://rs.gbif.org/vocabulary/gbif/rank.xml");
    assertTrue(util.downloadIfChanged(url, last, tmp));

    // Verify that it does not download with a conditional get of a five minutes ago, in case of
    // clock drift.
    Date nearlyNow = new Date(System.currentTimeMillis() - 5 * 60 * 1000);
    assertFalse(util.downloadIfChanged(url, nearlyNow, tmp));
  }

  /**
   * Testing if the IPT dwca file serving respects the If-Modified-Since.
   * Ignoring this test as its only a manual one to run to check the IPT!
   */
  @Test
  @Ignore
  public void testIptConditionalGet() throws ParseException, IOException {
    CloseableHttpClient client = HttpClientBuilder.create().build();
    HttpUtil util = new HttpUtil(client);
    Date longBefore = HttpUtil.parseHeaderDate("Wed, 03 Aug 2009 22:37:31 GMT");
    Date sinceChange = HttpUtil.parseHeaderDate("Wed, 15 May 2019 14:47:25 GMT");

    File tmp = File.createTempFile("dwca", ".zip");
    URL url = new URL("https://cloud.gbif.org/bid/archive?r=nzcs_introduced_fauna_suriname");
    boolean downloaded = util.downloadIfChanged(url, longBefore, tmp);
    assertTrue(downloaded);

    downloaded = util.downloadIfChanged(url, sinceChange, tmp);
    assertFalse(downloaded);
  }

  @Test
  public void testMultithreadedCommonsLogDependency() throws Exception {
    HttpUtil util = new HttpUtil(HttpUtil.newMultithreadedClient(10, 10, 10));
    util.get("http://rs.gbif.org/terms/1.0/Distribution");
  }
}
