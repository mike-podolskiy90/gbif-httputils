package org.gbif.varnish;

import java.io.IOException;
import java.net.URI;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Test VarnishPurger generated URI and Http headers
 */
public class VarnishPurgerTest {

  /**
   * Mock HttpClient to intercept the uri and headers without sending them.
   */
  private class MockCloseableHttpClientTest extends org.apache.http.impl.client.CloseableHttpClient {

    private String uri;
    private Header[] headers;

    @Override
    public HttpParams getParams() {
      return null;
    }

    @Override
    public ClientConnectionManager getConnectionManager() {
      return null;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    protected CloseableHttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context) throws IOException, ClientProtocolException {
      uri = request.getRequestLine().getUri();
      headers = request.getAllHeaders();

      //mock it
      CloseableHttpResponse response = mock(CloseableHttpResponse.class);
      return response;
    }

    public String getUri() {
      return uri;
    }

    public Header[] getHeaders() {
      return headers;
    }

    public String getFirstHeaderValue(String name) {
      for (Header header : headers) {
        if (name.equals(header.getName()))
          return header.getValue();
      }
      return null;
    }

  }

  @Test
  public void testBan() {
    MockCloseableHttpClientTest mockHttClient = new MockCloseableHttpClientTest();

    VarnishPurger purger = new VarnishPurger(mockHttClient, URI.create("http://api.gbif.org/v1/"));
    purger.ban("directory/*");

    assertEquals("/v1/directory/*", mockHttClient.getFirstHeaderValue(HttpBan.BAN_HEADER));

    //test no trailing slash
    purger = new VarnishPurger(mockHttClient, URI.create("http://api.gbif.org/v1"));
    purger.ban("directory/*");
  }
}
