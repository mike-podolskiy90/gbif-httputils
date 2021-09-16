/*
 * Copyright 2021 Global Biodiversity Information Facility (GBIF)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gbif.utils;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class for HTTP related functions.
 * <p/>
 * This class itself is thread safe. If you require thread safety please make sure to use a thread safe HTTP client as
 * the underlying client. The ones created here in the static builder methods or via the default constructor are.
 */
@SuppressWarnings("unused")
public class HttpUtil {

  static final String GBIF_NAME = "null".equals(HttpUtil.class.getPackage().getName())
      ? "org.gbif.utils"
      : HttpUtil.class.getPackage().getName().replace("gbif", "GBIF");
  static final String GBIF_VERSION = HttpUtil.class.getPackage().getImplementationVersion() == null
      ? "development"
      : HttpUtil.class.getPackage().getImplementationVersion();
  static final String JAVA_VERSION = System.getProperty("java.version");

  /**
   * An {@link org.apache.http.HttpResponse} wrapper exposing limited fields.
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

    public Header[] getAllHeaders() {
      return response.getAllHeaders();
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

    @Deprecated
    public HttpParams getParams() {
      return response.getParams();
    }

    public ProtocolVersion getProtocolVersion() {
      return response.getProtocolVersion();
    }

    public int getStatusCode() {
      return response.getStatusLine().getStatusCode();
    }

    public StatusLine getStatusLine() {
      return response.getStatusLine();
    }

    public HeaderIterator headerIterator() {
      return response.headerIterator();
    }

    public HeaderIterator headerIterator(String name) {
      return response.headerIterator(name);
    }

  }

  private static final Logger LOG = LoggerFactory.getLogger(HttpUtil.class);
  private final CloseableHttpClient client;

  public HttpUtil() {
    this.client = newMultithreadedClient(60_000, 250, 5);
  }

  public HttpUtil(CloseableHttpClient client) {
    this.client = client;
  }

  public static UsernamePasswordCredentials credentials(String username, String password) {
    return new UsernamePasswordCredentials(StringUtils.trimToEmpty(username), StringUtils.trimToEmpty(password));
  }

  /**
   * Creates a URL form encoded HTTP entity suitable for POST requests with a single given parameter
   * encoded in UTF-8.
   *
   * @param kvp the parameter map to encode
   */
  public static HttpEntity map2Entity(Map<String, String> kvp) {
    List<NameValuePair> formparams = new ArrayList<>(kvp.size());
    for (Map.Entry<String, String> entry : kvp.entrySet()) {
      formparams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
    }
    return new UrlEncodedFormEntity(formparams, StandardCharsets.UTF_8);
  }

  /**
   * Creates a URL form encoded HTTP entity suitable for POST requests with a single given parameter
   * encoded in UTF-8.
   *
   * @param key the parameter name
   * @param data the value to encode
   */
  public static HttpEntity map2Entity(String key, String data) {
    List<NameValuePair> formparams = new ArrayList<>(1);
    formparams.add(new BasicNameValuePair(key, data));
    return new UrlEncodedFormEntity(formparams, StandardCharsets.UTF_8);
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
    ConnectionConfig connectionConfig = ConnectionConfig.custom()
      .setCharset(StandardCharsets.UTF_8)
      .build();

    RequestConfig requestConfig = RequestConfig.custom()
      .setSocketTimeout(timeout)
      .setConnectTimeout(timeout)
      .setConnectionRequestTimeout(timeout)
      .build();

    SSLContext sslcontext = SSLContexts.createSystemDefault();

    Registry registry = RegistryBuilder.create()
      .register("http", PlainConnectionSocketFactory.INSTANCE)
      .register("https", new SSLConnectionSocketFactory(sslcontext))
      .build();

    PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(registry);
    connectionManager.setDefaultConnectionConfig(connectionConfig);

    RedirectStrategy redirectStrategy = new DefaultRedirectStrategy() {
      @Override
      public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        return super.isRedirected(request, response, context)
          || (response.getStatusLine().getStatusCode() == 308
          && isRedirectable(request.getRequestLine().getMethod()));
      }
    };

    final String userAgent = String.format("%s/%s (Java/%s; S-%d; +https://www.gbif.org/)",
      GBIF_NAME, GBIF_VERSION, JAVA_VERSION, timeout);

    return HttpClientBuilder.create()
      // Retain compressed content, e.g. a tar.gz archive we download
      .disableContentCompression()
      .setRedirectStrategy(redirectStrategy)
      .setDefaultRequestConfig(requestConfig)
      .setConnectionManager(connectionManager)
      .setUserAgent(userAgent)
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
    ConnectionConfig connectionConfig = ConnectionConfig.custom()
      .setCharset(StandardCharsets.UTF_8)
      .build();

    RequestConfig requestConfig = RequestConfig.custom()
      .setSocketTimeout(timeout)
      .setConnectTimeout(timeout)
      .setConnectionRequestTimeout(timeout)
      .build();

    SSLContext sslcontext = SSLContexts.createSystemDefault();

    Registry registry = RegistryBuilder.create()
      .register("http", PlainConnectionSocketFactory.INSTANCE)
      .register("https", new SSLConnectionSocketFactory(sslcontext))
      .build();

    PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(registry);
    connectionManager.setMaxTotal(maxConnections);
    connectionManager.setDefaultMaxPerRoute(maxPerRoute);
    connectionManager.setDefaultConnectionConfig(connectionConfig);

    RedirectStrategy redirectStrategy = new DefaultRedirectStrategy() {
      @Override
      public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        return super.isRedirected(request, response, context)
          || (response.getStatusLine().getStatusCode() == 308
          && isRedirectable(request.getRequestLine().getMethod()));
      }
    };

    final String userAgent = String.format("%s/%s (Java/%s; M-%d-%d-%d; +https://www.gbif.org/)",
      GBIF_NAME, GBIF_VERSION, JAVA_VERSION, timeout, maxConnections, maxPerRoute);

    return HttpClientBuilder.create()
      // Retain compressed content, e.g. a tar.gz archive we download
      .disableContentCompression()
      .setRedirectStrategy(redirectStrategy)
      .setDefaultRequestConfig(requestConfig)
      .setConnectionManager(connectionManager)
      .setUserAgent(userAgent)
      .build();
  }

  public static String responseAsString(HttpResponse response) {
    String content = null;
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      try {
        content = EntityUtils.toString(entity);
        EntityUtils.consume(entity);
      } catch (org.apache.http.ParseException e) {
        LOG.error("ParseException consuming HTTP response into string", e);
      } catch (IOException e) {
        LOG.error("IOException consuming HTTP response into string", e);
      }
    }
    return content;
  }

  /**
   * Creates a HTTP entity suitable for POSTs that encodes a single string in UTF-8.
   *
   * @param data to encode
   */
  public static HttpEntity stringEntity(String data) throws UnsupportedEncodingException {
    return new StringEntity(data, StandardCharsets.UTF_8);
  }

  /**
   * Whether a request has succeeded,  e.g. 200 response code
   */
  public static boolean success(Response resp) {
    return resp != null && success(resp.getStatusLine());
  }

  public static boolean success(StatusLine status) {
    return status != null && status.getStatusCode() >= 200 && status.getStatusCode() < 300;
  }

  private HttpContext buildContext(String uri, UsernamePasswordCredentials credentials) throws URISyntaxException {
    HttpContext authContext = new BasicHttpContext();
    if (credentials != null) {
      URI authUri = new URI(uri);
      AuthScope scope = new AuthScope(authUri.getHost(), AuthScope.ANY_PORT, AuthScope.ANY_REALM);

      CredentialsProvider credsProvider = new BasicCredentialsProvider();
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
    } finally {
      closeQuietly(response);
    }
    return result;
  }

  /**
   * Downloads something via HTTP GET to the provided file.
   */
  public StatusLine download(String url, File downloadTo) throws IOException {
    return download(new URL(url), downloadTo);
  }

  public StatusLine download(URI url, File downloadTo) throws IOException {
    return download(url.toURL(), downloadTo);
  }

  public String download(URL url) throws IOException {
    try {
      Response resp = get(url.toString());
      return resp.content;
    } catch (URISyntaxException e) {
      LOG.error("Invalid URL provided: {}", url, e);
    }
    return null;
  }

  public StatusLine download(URL url, File downloadTo) throws IOException {
    HttpGet get = new HttpGet(url.toString());

    // execute
    CloseableHttpResponse response = client.execute(get);
    final StatusLine status = response.getStatusLine();
    try {
      // write to file only when download succeeds
      if (success(status)) {
        saveToFile(response, downloadTo);
        LOG.debug("Successfully downloaded {} to {}", url, downloadTo.getAbsolutePath());
      } else {
        LOG.error("Downloading {} to {} failed!: {}", url, downloadTo.getAbsolutePath(), status.getStatusCode());
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
      if (resp.getStatusCode() == HttpStatus.SC_NOT_MODIFIED) {
        LOG.debug("Content not modified since last request");
      }
      return resp.content;
    } catch (URISyntaxException e) {
      LOG.error("Invalid URL provided: {}", url, e);
    }
    return null;
  }

  /**
   * Downloads a URL to a file if its modified since the date given.
   * Updates the last modified file property to reflect the server's last-modified HTTP header.
   *
   * @param downloadTo file to download to
   * @return true if changed or false if unmodified since lastModified
   */

  public boolean downloadIfChanged(URL url, Date lastModified, File downloadTo) throws IOException {
    StatusLine status = downloadIfModifiedSince(url, lastModified, downloadTo);
    return success(status);
  }

  /**
   * Downloads a URL to a local file using conditional GET, i.e. only downloading the file again if it has been changed
   * since the last download.
   */
  public boolean downloadIfChanged(URL url, File downloadTo) throws IOException {
    StatusLine status = downloadIfModifiedSince(url, downloadTo);
    return success(status);
  }

  /**
   * Downloads a URL to a file if its modified since the date given.
   * Updates the last modified file property to reflect the server's last-modified HTTP header.
   *
   * @param downloadTo file to download to
   * @return true if changed or false if unmodified since lastModified
   */
  public StatusLine downloadIfModifiedSince(final URL url, final Date lastModified, final File downloadTo) throws IOException {

    HttpGet get = new HttpGet(url.toString());

    // prepare conditional GET request headers
    if (lastModified != null) {
      // DateFormatUtils is threadsafe
      get.addHeader(HttpHeaders.IF_MODIFIED_SINCE, DateUtils.formatDate(lastModified));
      LOG.debug("Conditional GET: {}", DateUtils.formatDate(lastModified));
    }

    // execute
    CloseableHttpResponse response = client.execute(get);
    final StatusLine status = response.getStatusLine();
    try {
      if (status.getStatusCode() == HttpStatus.SC_NOT_MODIFIED) {
        LOG.debug("Content not modified since last request");

      } else if (success(status)) {
        // write to file only when download succeeds
        saveToFile(response, downloadTo);
        LOG.debug("Successfully downloaded {} to {}", url, downloadTo.getAbsolutePath());

      } else {
        LOG.error("Downloading {} to {} failed!: {}", url, downloadTo.getAbsolutePath(), status.getStatusCode());
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
        serverModified = DateUtils.parseDate(modHeader.getValue());
      }

      // copy stream to local file
      FileUtils.forceMkdir(downloadTo.getParentFile());
      if (downloadTo.isFile()) {
        downloadTo.delete();
      }
      try (OutputStream fos = new FileOutputStream(downloadTo, false)) {
        entity.writeTo(fos);
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
  public StatusLine downloadIfModifiedSince(final URL url, final File downloadTo) throws IOException {
    Date lastModified = null;
    if (downloadTo.exists()) {
      lastModified = new Date(downloadTo.lastModified());
    }
    return downloadIfModifiedSince(url, lastModified, downloadTo);
  }

  public CloseableHttpResponse executeGetWithTimeout(HttpGet get, int timeout) throws IOException {
    HttpParams httpParams = client.getParams();
    // keep old values to rest afterwards
    int ct = HttpConnectionParams.getConnectionTimeout(httpParams);
    int st = HttpConnectionParams.getSoTimeout(httpParams);

    HttpConnectionParams.setConnectionTimeout(httpParams, timeout);
    HttpConnectionParams.setSoTimeout(httpParams, timeout);

    CloseableHttpResponse response = null;
    try {
      response = client.execute(get);
    } finally {
      // rest to previous values
      HttpConnectionParams.setConnectionTimeout(httpParams, ct);
      HttpConnectionParams.setSoTimeout(httpParams, st);
    }

    return response;
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
    } finally {
      closeQuietly(response);
    }

    return result;
  }

  @Deprecated
  public HttpParams params(Map<String, Object> params) {
    HttpParams p = new BasicHttpParams();
    for (Map.Entry<String, Object> param : params.entrySet()) {
      p.setParameter(param.getKey(), param.getValue());
    }
    return p;
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
    HttpResponse response = client.execute(post, authContext);

    // response
    if (response != null) {
      Response result = new Response(response);
      HttpEntity respEntity = response.getEntity();
      if (respEntity != null) {
        result.content = EntityUtils.toString(respEntity);
        EntityUtils.consume(respEntity);
      }
      return result;
    }
    return null;
  }

  @Deprecated
  public Response post(String uri, HttpParams params, Map<String, String> headers,
    UsernamePasswordCredentials credentials) throws IOException, URISyntaxException {
    return post(uri, params, headers, credentials, null);
  }


  @Deprecated
  public Response post(String uri, HttpParams params, Map<String, String> headers,
    UsernamePasswordCredentials credentials, HttpEntity encodedEntity) throws IOException, URISyntaxException {
    return post(uri, headers, credentials, encodedEntity);
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
        LOG.debug("Failed to close HTTP response", e);
      }
    }
  }
}
