package org.gbif.varnish;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.lang3.StringUtils;
import org.gbif.utils.HttpUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.Assert.assertEquals;

/**
 * Test VarnishPurger generated URI and HTTP headers
 */
public class VarnishPurgerTest {

  private static final String API_BASEURL = "http://localhost:13823/";

  private static HttpServer server;
  private static String method;
  private static URI requestUri;
  private static Headers headers;

  static class TestHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange t) throws IOException {
      method = t.getRequestMethod();
      requestUri = t.getRequestURI();
      headers = t.getRequestHeaders();
      t.sendResponseHeaders(204, -1);
    }
  }

  @BeforeClass
  public static void before() throws Exception {
    server = HttpServer.create(new InetSocketAddress(13823), 0);
    server.createContext("/", new TestHandler());
    server.setExecutor(null);
    server.start();
  }

  @AfterClass
  public static void after() {
    server.stop(0);
  }

  @Test
  public void testPurge() {
    VarnishPurger purger = new VarnishPurger(HttpUtil.newSinglethreadedClient(5), URI.create(API_BASEURL));
    purger.purge("occurrence/15");
    assertEquals("PURGE", method);
    assertEquals("/occurrence/15", requestUri.toString());

    purger.purge("/occurrence/15");
    assertEquals("/occurrence/15", requestUri.toString());
  }

  @Test
  public void testBan() {
    VarnishPurger purger = new VarnishPurger(HttpUtil.newSinglethreadedClient(5), URI.create(API_BASEURL));
    purger.ban("directory/*");

    assertEquals("BAN", method);
    assertEquals("/", requestUri.toString());
    assertEquals("/directory/*", headers.getFirst(HttpBan.BAN_HEADER));

    // test no trailing slash
    purger = new VarnishPurger(HttpUtil.newSinglethreadedClient(5), URI.create(StringUtils.removeEnd(API_BASEURL, "/")));
    purger.ban("/directory/*");
    assertEquals("/directory/*", headers.getFirst(HttpBan.BAN_HEADER));
  }
}
