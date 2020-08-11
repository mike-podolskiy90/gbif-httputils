/***************************************************************************
 * Copyright 2020 Global Biodiversity Information Facility Secretariat
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ***************************************************************************/
package org.gbif.utils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.impl.io.DefaultHttpRequestWriterFactory;
import org.apache.hc.core5.http.impl.io.DefaultHttpResponseParserFactory;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.LangUtils;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A utility class for HTTP related functions.
 * <p/>
 * This class itself is thread safe. If you require thread safety please make sure to use a thread safe HTTP client as
 * the underlying client. The ones created here in the static builder methods or via the default constructor are.
 */
public class HttpUtil {

  private static final String GBIF_VERSION = HttpUtil.class.getPackage().getImplementationVersion() == null
      ? "development"
      : HttpUtil.class.getPackage().getImplementationVersion();
  private static final String JAVA_VERSION = Runtime.class.getPackage().getImplementationVersion();

  /**
   * GBIF User Agent header, for use when not using the multithreaded client defined in this class.
   */
  public static final Header GBIF_USER_AGENT = new BasicHeader(HttpHeaders.USER_AGENT,
    String.format("GBIF-HttpClient/%s (Java/%s; S; +https://www.gbif.org/)", GBIF_VERSION, JAVA_VERSION));

  /**
   * An {@link org.apache.hc.core5.http.HttpResponse} wrapper exposing limited fields.
   */
  public static class Response {

    public String content;

    private final HttpResponse response;

    public Response(HttpResponse resp) {
      response = resp;
    }

    public boolean containsHeader(String name) {
      return response.containsHeader(name);
    }

    public Header[] getHeaders() {
      return response.getHeaders();
    }

    public Header getFirstHeader(String name) {
      return response.getFirstHeader(name);
    }

    public Header[] getHeaders(String name) {
      return response.getHeaders(name);
    }

    public Header getLastHeader(String name) {
      return response.getLastHeader(name);
    }

    public Locale getLocale() {
      return response.getLocale();
    }

    public ProtocolVersion getVersion() {
      return response.getVersion();
    }

    public int getCode() {
      return response.getCode();
    }

    public Iterator<Header> headerIterator() {
      return response.headerIterator();
    }

    public Iterator<Header> headerIterator(String name) {
      return response.headerIterator(name);
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(HttpUtil.class);
  private final CloseableHttpClient client;

  public HttpUtil(CloseableHttpClient client) {
    this.client = client;
  }

  public static UsernamePasswordCredentials credentials(String username, String password) {
    return new UsernamePasswordCredentials(StringUtils.trimToEmpty(username), StringUtils.trimToEmpty(password).toCharArray());
  }

  /**
   * Creates a URL form encoded HTTP entity suitable for POST requests with a single given parameter
   * encoded in UTF-8.
   *
   * @param kvp the parameter map to encode
   */
  public static HttpEntity map2Entity(Map<String, String> kvp) {
    List<NameValuePair> formParams = new ArrayList<NameValuePair>(kvp.size());
    for (Map.Entry<String, String> entry : kvp.entrySet()) {
      formParams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
    }
    return new UrlEncodedFormEntity(formParams, StandardCharsets.UTF_8);
  }

  /**
   * Creates a URL form encoded HTTP entity suitable for POST requests with a single given parameter
   * encoded in UTF-8.
   *
   * @param key the parameter name
   * @param data the value to encode
   */
  public static HttpEntity map2Entity(String key, String data) {
    List<NameValuePair> formParam = new ArrayList<NameValuePair>(1);
    formParam.add(new BasicNameValuePair(key, data));
    return new UrlEncodedFormEntity(formParam, StandardCharsets.UTF_8);
  }

  /**
   * This creates a new threadsafe, singlethreaded HTTP client with support for HTTP and HTTPS.
   * <p>
   * Default HTTP client values are partially overridden to use UTF-8 as the default charset and an explicit timeout
   * is required for configuration.
   *
   * @param timeout in milliseconds
   */
  public static CloseableHttpClient newSinglethreadedClient(int timeout) {
    final CharCodingConfig connectionConfig = CharCodingConfig.custom()
      .setCharset(StandardCharsets.UTF_8)
      .build();

    final HttpConnectionFactory<ManagedHttpClientConnection> connFactory = new ManagedHttpClientConnectionFactory(
      Http1Config.DEFAULT, connectionConfig, new DefaultHttpRequestWriterFactory(), new DefaultHttpResponseParserFactory());

    final SocketConfig socketConfig = SocketConfig.custom()
      .setSoTimeout(Timeout.ofSeconds(timeout))
      .build();

    final RequestConfig requestConfig = RequestConfig.custom()
      .setConnectionRequestTimeout(Timeout.ofSeconds(timeout))
      .setConnectTimeout(Timeout.ofSeconds(timeout))
      .build();

    final SSLContext sslcontext = SSLContexts.createSystemDefault();
    final Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
      .register("http", PlainConnectionSocketFactory.INSTANCE)
      .register("https", new SSLConnectionSocketFactory(sslcontext))
      .build();

    final BasicHttpClientConnectionManager connManager = new BasicHttpClientConnectionManager(
      socketFactoryRegistry, connFactory);
    connManager.setSocketConfig(socketConfig);

    final String USER_AGENT = String.format("GBIF-HttpClient/%s (Java/%s; S-%d; +https://www.gbif.org/)",
      GBIF_VERSION, JAVA_VERSION, timeout);

    return HttpClients.custom()
      .setUserAgent(USER_AGENT)
      .setDefaultRequestConfig(requestConfig)
      .build();
  }

  /**
   * This creates a new threadsafe, multithreaded HTTP client with support for HTTP and HTTPS.
   * <p>
   * Default HTTP client values are partially overridden to use UTF-8 as the default charset and an explicit timeout
   * is required for configuration.
   *
   * @param timeout in milliseconds
   * @param maxConnections maximum allowed connections in total
   * @param maxPerRoute maximum allowed connections per route
   */
  public static CloseableHttpClient newMultithreadedClient(int timeout, int maxConnections, int maxPerRoute) {
    final CharCodingConfig connectionConfig = CharCodingConfig.custom()
      .setCharset(StandardCharsets.UTF_8)
      .build();

    final HttpConnectionFactory<ManagedHttpClientConnection> connFactory = new ManagedHttpClientConnectionFactory(
      Http1Config.DEFAULT, connectionConfig, new DefaultHttpRequestWriterFactory(), new DefaultHttpResponseParserFactory());

    final SocketConfig socketConfig = SocketConfig.custom()
      .setSoTimeout(Timeout.ofSeconds(timeout))
      .build();

    final RequestConfig requestConfig = RequestConfig.custom()
      .setConnectionRequestTimeout(Timeout.ofSeconds(timeout))
      .setConnectTimeout(Timeout.ofSeconds(timeout))
      .build();

    final SSLContext sslcontext = SSLContexts.createSystemDefault();
    final Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
      .register("http", PlainConnectionSocketFactory.INSTANCE)
      .register("https", new SSLConnectionSocketFactory(sslcontext))
      .build();

    final PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(
      socketFactoryRegistry, PoolConcurrencyPolicy.STRICT, PoolReusePolicy.LIFO, TimeValue.ofMinutes(5),
      null, null, connFactory);
    connManager.setDefaultSocketConfig(socketConfig);
    connManager.setMaxTotal(maxConnections);
    connManager.setDefaultMaxPerRoute(maxPerRoute);

    final String USER_AGENT = String.format("GBIF-HttpClient/%s (Java/%s; M-%d-%d-%d; +https://www.gbif.org/)",
      GBIF_VERSION, JAVA_VERSION, timeout, maxConnections, maxPerRoute);

    return HttpClients.custom()
      .setUserAgent(USER_AGENT)
      .setConnectionManager(connManager)
      .setDefaultRequestConfig(requestConfig)
      .build();
  }

  /**
   * Parses a RFC2616 compliant date string such as used in HTTP headers.
   *
   * @param rfcDate RFC2616 compliant date string
   * @return the parsed date or null if it cannot be parsed
   */
  public static Date parseHeaderDate(String rfcDate) {
    return DateUtils.parseDate(rfcDate);
  }

  public static String responseAsString(CloseableHttpResponse response) {
    String content = null;
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      try {
        content = EntityUtils.toString(entity);
        EntityUtils.consume(entity);
      } catch (org.apache.hc.core5.http.ParseException e) {
        LOG.error("ParseException consuming HTTP response into string", e);
      } catch (IOException e) {
        LOG.error("IOException consuming HTTP response into string", e);
      }
    }
    return content;
  }

  /**
   * Creates an HTTP entity suitable for POSTs that encodes a single string in UTF-8.
   *
   * @param data to encode
   */
  public static HttpEntity stringEntity(String data) throws UnsupportedEncodingException {
    return new StringEntity(data, StandardCharsets.UTF_8);
  }

  /**
   * Whether a request has succeeded, e.g. 200 response code
   */
  public static boolean success(Response resp) {
    return resp != null && success(resp.getCode());
  }

  public static boolean success(StatusLine status) {
    return status != null && status.getStatusCode() >= HttpStatus.SC_SUCCESS
      && status.getStatusCode() < HttpStatus.SC_REDIRECTION;
  }

  public static boolean success(int status) {
    return status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION;
  }

  private HttpContext buildContext(String uri, UsernamePasswordCredentials credentials) throws URISyntaxException {
    HttpContext authContext = new BasicHttpContext();
    if (credentials != null) {
      URI authUri = new URI(uri);
      AuthScope scope = new AuthScope(authUri.getHost(), -1);

      BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
      credsProvider.setCredentials(scope, credentials);

      authContext.setAttribute(HttpClientContext.CREDS_PROVIDER, credsProvider);
    }

    return authContext;
  }

  /**
   * Executes a generic DELETE request.
   */
  public Response delete(String url, UsernamePasswordCredentials credentials) throws IOException, URISyntaxException {
    LOG.info("HTTP DELETE to {}", url);
    HttpDelete delete = new HttpDelete(url);
    HttpContext authContext = buildContext(url, credentials);

    CloseableHttpResponse response = client.execute(delete, authContext);
    Response result = new Response(response);
    try {
      HttpEntity entity = response.getEntity();
      if (entity != null) {
        result.content = EntityUtils.toString(entity);
        EntityUtils.consume(entity);
      }
    } catch (ParseException pe) {
      throw new IOException(pe);
    } finally {
      closeQuietly(response);
    }
    return result;
  }

  /**
   * Downloads something via HTTP GET to the provided file.
   */
  public int download(String url, File downloadTo) throws IOException {
    return download(new URL(url), downloadTo);
  }

  public int download(URI url, File downloadTo) throws IOException {
    return download(url.toURL(), downloadTo);
  }

  public String download(URL url) throws IOException, org.apache.hc.core5.http.ParseException {
    try {
      Response resp = get(url.toString());
      return resp.content;
    } catch (URISyntaxException e) {
      LOG.error("Invalid URL provided: {}", url, e);
    }
    return null;
  }

  public int download(URL url, File downloadTo) throws IOException {
    HttpGet get = new HttpGet(url.toString());

    // execute
    CloseableHttpResponse response = client.execute(get);
    final int status = response.getCode();
    try {
      // write to file only when download succeeds
      if (success(status)) {
        saveToFile(response, downloadTo);
        LOG.debug("Successfully downloaded {} to {}", url, downloadTo.getAbsolutePath());
      } else {
        LOG.error("Downloading {} to {} failed!: {}", url, downloadTo.getAbsolutePath(), status);
      }
    } finally {
      closeQuietly(response);
    }
    return status;
  }

  /**
   * @return body content if changed or null if unmodified since lastModified
   */
  public String downloadIfChanged(URL url, Date lastModified) throws IOException {
    Map<String, String> header = new HashMap<String, String>(1);
    header.put(HttpHeaders.IF_MODIFIED_SINCE, DateUtils.formatDate(lastModified));

    try {
      Response resp = get(url.toString(), header, null);
      if (resp.getCode() == HttpStatus.SC_NOT_MODIFIED) {
        LOG.debug("Content not modified since last request");
      }
      return resp.content;
    } catch (URISyntaxException e) {
      LOG.error("Invalid URL provided: {}", url, e);
    }
    return null;
  }

  /**
   * Downloads a URL to a file if it has been modified since the date given.
   *
   * Updates the last modified file property to reflect the server's last modified HTTP header.
   *
   * @param downloadTo file to download to
   * @return true if changed or false if unmodified since lastModified
   */
  public boolean downloadIfChanged(URL url, Date lastModified, File downloadTo) throws IOException {
    int status = downloadIfModifiedSince(url, lastModified, downloadTo);
    return success(status);
  }

  /**
   * Downloads a URL to a local file using conditional GET, i.e. only downloading the file again if it has been changed
   * since the last download.
   */
  public boolean downloadIfChanged(URL url, File downloadTo) throws IOException {
    int status = downloadIfModifiedSince(url, downloadTo);
    return success(status);
  }

  /**
   * Downloads a URL to a file if it was modified since the date given.
   * Updates the last modified file property to reflect the server's last-modified HTTP header.
   *
   * @param downloadTo file to download to
   * @return true if changed or false if unmodified since lastModified
   */
  public int downloadIfModifiedSince(final URL url, final Date lastModified, final File downloadTo) throws IOException {

    HttpGet get = new HttpGet(url.toString());

    // prepare conditional GET request headers
    if (lastModified != null) {
      // DateFormatUtils is threadsafe
      get.addHeader(HttpHeaders.IF_MODIFIED_SINCE, DateUtils.formatDate(lastModified));
      LOG.debug("Conditional GET: {}", DateUtils.formatDate(lastModified));
    }

    // execute
    CloseableHttpResponse response = client.execute(get);
    final int status = response.getCode();
    try {
      if (status == HttpStatus.SC_NOT_MODIFIED) {
        LOG.debug("Content not modified since last request");

      } else if (success(status)) {
        // write to file only when download succeeds
        saveToFile(response, downloadTo);
        LOG.debug("Successfully downloaded {} to {}", url, downloadTo.getAbsolutePath());

      } else {
        LOG.error("Downloading {} to {} failed!: {}", url, downloadTo.getAbsolutePath(), status);
      }

    } finally {
      closeQuietly(response);
    }
    return status;
  }

  private void saveToFile(CloseableHttpResponse response, File downloadTo) throws IOException {
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      Date serverModified = null;
      Header modHeader = response.getFirstHeader(HttpHeaders.LAST_MODIFIED);
      if (modHeader != null) {
        serverModified = parseHeaderDate(modHeader.getValue());
      }

      // copy stream to local file
      FileUtils.forceMkdir(downloadTo.getParentFile());
      if (downloadTo.isFile()) {
        downloadTo.delete();
      }
      OutputStream fos = new FileOutputStream(downloadTo, false);
      try {
        entity.writeTo(fos);
      } finally {
        fos.close();
      }
      // update last modified of file with HTTP header date from server
      if (serverModified != null) {
        downloadTo.setLastModified(serverModified.getTime());
      }
    }
  }

  /**
   * Downloads a URL to a local file using conditional GET, i.e. only downloading the file again if it has been changed
   * on the filesystem since the last download.
   *
   * @param url URL to download
   * @param downloadTo file to download into and used to get the last modified date from
   */
  public int downloadIfModifiedSince(final URL url, final File downloadTo) throws IOException {
    Date lastModified = null;
    if (downloadTo.exists()) {
      lastModified = new Date(downloadTo.lastModified());
    }
    return downloadIfModifiedSince(url, lastModified, downloadTo);
  }

  /**
   * @throws IOException in case of a problem or the connection was aborted
   */
  public Response get(String url) throws IOException, URISyntaxException {
    return get(url, null, null);
  }

  public Response get(String url, Map<String, String> headers, UsernamePasswordCredentials credentials)
    throws IOException, URISyntaxException {
    HttpGet get = new HttpGet(url);
    // HTTP header
    if (headers != null) {
      for (Map.Entry<String, String> header : headers.entrySet()) {
        get.addHeader(StringUtils.trimToEmpty(header.getKey()), StringUtils.trimToEmpty(header.getValue()));
      }
    }
    // authentication
    HttpContext authContext = buildContext(url, credentials);
    CloseableHttpResponse response = client.execute(get, authContext);
    Response result = new Response(response);
    try {
      HttpEntity entity = response.getEntity();
      if (entity != null) {
        // Adding a default charset in case it is not found
        result.content = EntityUtils.toString(entity, StandardCharsets.UTF_8);
      }
    } catch (ParseException pe) {
      throw new IOException(pe);
    } finally {
      closeQuietly(response);
    }

    return result;
  }

  /**
   * Executes a generic POST request.
   */
  public Response post(String uri, HttpEntity requestEntity) throws IOException, URISyntaxException {
    return post(uri, null, null, requestEntity);
  }

  public Response post(String uri, UsernamePasswordCredentials credentials, HttpEntity requestEntity)
    throws IOException, URISyntaxException {
    return post(uri, null, credentials, requestEntity);
  }

  public Response post(String uri, Map<String, String> headers, UsernamePasswordCredentials credentials)
    throws IOException, URISyntaxException {
    return post(uri, headers, credentials, null);
  }

  public Response post(String uri, Map<String, String> headers, UsernamePasswordCredentials credentials,
    HttpEntity requestEntity) throws IOException, URISyntaxException {
    HttpPost post = new HttpPost(uri);
    if (headers != null) {
      for (Map.Entry<String, String> header : headers.entrySet()) {
        post.addHeader(StringUtils.trimToEmpty(header.getKey()), StringUtils.trimToEmpty(header.getValue()));
      }
    }
    if (requestEntity != null) {
      post.setEntity(requestEntity);
    }
    // authentication
    HttpContext authContext = buildContext(uri, credentials);
    CloseableHttpResponse response = client.execute(post, authContext);

    // response
    if (response != null) {
      Response result = new Response(response);
      try {
        HttpEntity entity = response.getEntity();
        if (entity != null) {
          result.content = EntityUtils.toString(entity);
          EntityUtils.consume(entity);
        }
      } catch (ParseException pe) {
        throw new IOException(pe);
      } finally {
        closeQuietly(response);
      }
      return result;
    }
    return null;
  }

  public boolean verifyHost(HttpHost host) {
    if (host != null) {
      CloseableHttpResponse resp = null;
      try {
        HttpHead head = new HttpHead(host.toURI());
        resp = client.execute(host, head);
        return true;

      } catch (Exception e) {
        LOG.debug("Exception thrown", e);

      } finally {
        closeQuietly(resp);
      }
    }
    return false;
  }

  private void closeQuietly(CloseableHttpResponse resp) {
    if (resp != null) {
      try {
        resp.close();
      } catch (IOException e) {
        LOG.debug("Failed to close http response", e);
      }
    }
  }
}
