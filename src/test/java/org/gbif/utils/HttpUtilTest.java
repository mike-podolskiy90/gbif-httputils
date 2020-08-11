package org.gbif.utils;

import org.apache.http.StatusLine;
import org.apache.http.client.utils.DateUtils;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpUtilTest {

  @Test
  public void testClientRedirect() throws ParseException, IOException {
    HttpUtil util = new HttpUtil(HttpUtil.newSinglethreadedClient(10_000));
    File tmp = File.createTempFile("httputils", "test");
    tmp.deleteOnExit();
    // a redirect to http://rs.gbif.org/extension/gbif/1.0/distribution.xml
    StatusLine status = util.download("http://rs.gbif.org/terms/1.0/Distribution", tmp);
    assertTrue(HttpUtil.success(status));

    // assert false on 404s
    status = util.download("https://rs.gbif.org/gbif-httputils-unit-test-404", tmp);
    assertFalse(HttpUtil.success(status));
    assertEquals(404, status.getStatusCode());

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
    HttpUtil util = new HttpUtil();
    // We know for sure it has changed since this date
    Date last = DateUtils.parseDate("Wed, 03 Aug 2009 22:37:31 GMT");
    File tmp = File.createTempFile("vocab", ".xml");
    URL url = new URL("http://rs.gbif.org/vocabulary/gbif/rank.xml");
    assertTrue(util.downloadIfChanged(url, last, tmp));

    // Verify that it does not download with a conditional get of 5 minutes ago, in case of clock skew.
    Date nearlyNow = new Date(System.currentTimeMillis() - 1000 * 60 * 5);
    assertFalse(util.downloadIfChanged(url, nearlyNow, tmp));
  }

  /**
   * Testing if the IPT DWCA file serving respects the is-modified-since.
   * Ignoring this test as its only a manual one to run to check the IPT!
   */
  @Test
  @Ignore
  public void testIptConditionalGet() throws ParseException, IOException {
    HttpUtil util = new HttpUtil();

    Date beforeChange = DateUtils.parseDate("Wed, 03 Aug 2009 22:37:31 GMT");
    Date afterChange = DateUtils.parseDate("Wed, 15 May 2019 14:47:25 GMT");

    File tmp = File.createTempFile("dwca", ".zip");
    URL url = new URL("https://cloud.gbif.org/bid/archive.do?r=nzcs_introduced_fauna_suriname");
    boolean downloaded = util.downloadIfChanged(url, beforeChange, tmp);
    assertTrue(downloaded);

    downloaded = util.downloadIfChanged(url, afterChange, tmp);
    assertFalse(downloaded);
  }

  @Test
  public void testMultithreadedCommonsLogDependency() throws Exception {
    HttpUtil util = new HttpUtil(HttpUtil.newMultithreadedClient(10_000, 10_000, 10));
    util.get("http://rs.gbif.org/vocabulary/gbif/rank.xml");
    util.get("https://rs.gbif.org/vocabulary/gbif/rank.xml");
  }
}
