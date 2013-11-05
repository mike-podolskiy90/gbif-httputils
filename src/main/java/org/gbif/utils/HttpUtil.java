package org.gbif.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class for HTTP related functions.
 * <p/>
 * This class itself is thread safe. If you require thread safety please make sure to use a thread safe http client as
 * the underlying client. The ones created here in the static builder methods or via the default constructor are.
 */
public class HttpUtil {

  /**
   * An {@link HttpResonse} wrapper exposing limited fields.
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

  public static final String FORM_URL_ENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8";
  private static final Logger LOG = LoggerFactory.getLogger(HttpUtil.class);
  private static final String LAST_MODIFIED = "Last-Modified";
  private static final String MODIFIED_SINCE = "If-Modified-Since";
  private static final int HTTP_PORT = 80;
  private static final int HTTPS_PORT = 443;
  private static final String HTTP_PROTOCOL = "http";
  private static final String HTTPS_PROTOCOL = "https";
  private static final String UTF_8 = "UTF-8";
  private final DefaultHttpClient client;

  public HttpUtil(DefaultHttpClient client) {
    this.client = client;
  }

  public static UsernamePasswordCredentials credentials(String username, String password) {
    return new UsernamePasswordCredentials(StringUtils.trimToEmpty(username), StringUtils.trimToEmpty(password));
  }

  /**
   * Creates a url form encoded http entity suitable for POST requests with a single given parameter
   * encoded in utf8.
   * 
   * @param kvp the parameter map to encode
   */
  public static HttpEntity map2Entity(Map<String, String> kvp) {
    List<NameValuePair> formparams = new ArrayList<NameValuePair>(kvp.size());
    for (Map.Entry<String, String> entry : kvp.entrySet()) {
      formparams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
    }
    try {
      return new UrlEncodedFormEntity(formparams, UTF_8);
    } catch (UnsupportedEncodingException e) {
      LOG.error("Can't encode post entity with utf8", e);
    }
    return null;
  }

  /**
   * Creates a url form encoded http entity suitable for POST requests with a single given parameter
   * encoded in utf8.
   * 
   * @param key the parameter name
   * @param data the value to encode
   */
  public static HttpEntity map2Entity(String key, String data) {
    List<NameValuePair> formparams = new ArrayList<NameValuePair>(1);
    formparams.add(new BasicNameValuePair(key, data));
    try {
      return new UrlEncodedFormEntity(formparams, UTF_8);
    } catch (UnsupportedEncodingException e) {
      LOG.error("Can't encode post entity with utf8", e);
    }
    return null;
  }

  /**
   * This creates a new threadsafe, multithreaded http client with support for http and https.
   * Default http client values are partially overriden to use UTF8 as the default charset and an explicit timeout
   * is required for configuration.
   * 
   * @param timeout in milliseconds
   * @param maxConnections maximum allowed connections in total
   * @param maxPerRoute maximum allowed connections per route
   */
  public static DefaultHttpClient newMultithreadedClient(int timeout, int maxConnections, int maxPerRoute) {
    HttpParams params = new BasicHttpParams();
    params.setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET, UTF_8);
    HttpConnectionParams.setConnectionTimeout(params, timeout);
    HttpConnectionParams.setSoTimeout(params, timeout);
    params.setLongParameter(ClientPNames.CONN_MANAGER_TIMEOUT, timeout);

    SchemeRegistry schemeRegistry = new SchemeRegistry();
    schemeRegistry.register(new Scheme(HTTP_PROTOCOL, HTTP_PORT, PlainSocketFactory.getSocketFactory()));
    schemeRegistry.register(new Scheme(HTTPS_PROTOCOL, HTTPS_PORT, SSLSocketFactory.getSocketFactory()));

    PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager(schemeRegistry);
    connectionManager.setMaxTotal(maxConnections);
    connectionManager.setDefaultMaxPerRoute(maxPerRoute);
    return new DefaultHttpClient(connectionManager, params);
  }


  /**
   * Parses a RFC2616 compliant date string such as used in http headers.
   * 
   * @see <a href="http://tools.ietf.org/html/rfc2616#section-3.3">RFC 2616</a> specification.
   *      example:
   *      Wed, 21 Jul 2010 22:37:31 GMT
   * @param rfcDate RFC2616 compliant date string
   * @return the parsed date or null if it cannot be parsed
   */
  public static Date parseHeaderDate(String rfcDate) {
    try {
      if (rfcDate != null) {
        // as its not thread safe we create a new instance each time
        return new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).parse(rfcDate);
      }
    } catch (ParseException e) {
      LOG.debug("Can't parse RFC2616 date");
    }
    return null;
  }

  public static String responseAsString(HttpResponse response) {
    String content = null;
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      try {
        content = EntityUtils.toString(entity);
        EntityUtils.consume(entity);
      } catch (org.apache.http.ParseException e) {
        LOG.error("ParseException consuming http response into string", e);
      } catch (IOException e) {
        LOG.error("IOException consuming http response into string", e);
      }
    }
    return content;
  }

  /**
   * Creates a http entity suitable for POSTs that encodes a single string in utf8.
   * 
   * @param data to encode
   */
  public static HttpEntity stringEntity(String data) throws UnsupportedEncodingException {
    return new StringEntity(data, UTF_8);
  }

  /**
   * Whether a request has succedded, i.e.: 200 response code
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

      authContext.setAttribute(ClientContext.CREDS_PROVIDER, credsProvider);
    }

    return authContext;
  }

  /**
   * Executes a generic DELETE request.
   */
  public Response delete(String url, UsernamePasswordCredentials credentials) throws IOException, URISyntaxException {
    LOG.info("Http delete to {}", url);
    HttpDelete delete = new HttpDelete(url);
    HttpContext authContext = buildContext(url, credentials);
    // HttpGet get = new HttpGet(url);
    HttpResponse response = client.execute(delete, authContext);
    Response result = new Response(response);
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      result.content = EntityUtils.toString(entity);
      EntityUtils.consume(entity);
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
    HttpResponse response = client.execute(get);
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      // copy stream to local file
      FileUtils.forceMkdir(downloadTo.getParentFile());
      OutputStream fos = new FileOutputStream(downloadTo, false);
      try {
        entity.writeTo(fos);
      } finally {
        fos.close();
      }
    }

    LOG.debug("Successfully downloaded {} to {}", url, downloadTo.getAbsolutePath());
    return response.getStatusLine();
  }

  /**
   * @return body content if changed or null if unmodified since lastModified
   */
  public String downloadIfChanged(URL url, Date lastModified) throws IOException {
    Map<String, String> header = new HashMap<String, String>(1);
    header.put(MODIFIED_SINCE, DateFormatUtils.SMTP_DATETIME_FORMAT.format(lastModified));

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
   * Downloads a url to a file if its modified since the date given.
   * Updates the last modified file property to reflect the last servers modified http header.
   * 
   * @param downloadTo file to download to
   * @return true if changed or false if unmodified since lastModified
   */

  public boolean downloadIfChanged(URL url, Date lastModified, File downloadTo) throws IOException {
    StatusLine status = downloadIfModifiedSince(url, lastModified, downloadTo);
    return success(status);
  }

  /**
   * Downloads a url to a local file using conditional GET, i.e. only downloading the file again if it has been changed
   * since the last download.
   */
  public boolean downloadIfChanged(URL url, File downloadTo) throws IOException {
    StatusLine status = downloadIfModifiedSince(url, downloadTo);
    return success(status);
  }

  /**
   * Downloads a url to a file if its modified since the date given.
   * Updates the last modified file property to reflect the last servers modified http header.
   * 
   * @param downloadTo file to download to
   * @return true if changed or false if unmodified since lastModified
   */
  public StatusLine downloadIfModifiedSince(final URL url, final Date lastModified, final File downloadTo)
    throws IOException {

    HttpGet get = new HttpGet(url.toString());

    // prepare conditional GET request headers
    if (lastModified != null) {
      // DateFormatUtils is threadsafe
      get.addHeader(MODIFIED_SINCE, DateFormatUtils.SMTP_DATETIME_FORMAT.format(lastModified));
      LOG.debug("Conditional GET: {}", DateFormatUtils.SMTP_DATETIME_FORMAT.format(lastModified));
    }

    // execute
    HttpResponse response = client.execute(get);
    final StatusLine status = response.getStatusLine();
    if (status.getStatusCode() == HttpStatus.SC_NOT_MODIFIED) {
      LOG.debug("Content not modified since last request");
    } else {
      HttpEntity entity = response.getEntity();
      if (entity != null) {

        Date serverModified = null;
        Header modHeader = response.getFirstHeader(LAST_MODIFIED);
        if (modHeader != null) {
          serverModified = parseHeaderDate(modHeader.getValue());
        }

        // copy stream to local file
        FileUtils.forceMkdir(downloadTo.getParentFile());
        OutputStream fos = new FileOutputStream(downloadTo, false);
        try {
          entity.writeTo(fos);
        } finally {
          fos.close();
        }
        // update last modified of file with http header date from server
        if (serverModified != null) {
          downloadTo.setLastModified(serverModified.getTime());
        }
      }

      // close http connection
      EntityUtils.consume(entity);

      LOG.debug("Successfully downloaded {} to {}", url, downloadTo.getAbsolutePath());
    }

    return status;
  }

  /**
   * Downloads a url to a local file using conditional GET, i.e. only downloading the file again if it has been changed
   * on the filesystem since the last download.
   * 
   * @param url url to download
   * @param downloadTo file to download into and used to get the last modified date from
   */
  public StatusLine downloadIfModifiedSince(final URL url, final File downloadTo) throws IOException {
    Date lastModified = null;
    if (downloadTo.exists()) {
      lastModified = new Date(downloadTo.lastModified());
    }
    return downloadIfModifiedSince(url, lastModified, downloadTo);
  }

  public HttpResponse executeGetWithTimeout(HttpGet get, int timeout) throws ClientProtocolException, IOException {
    HttpParams httpParams = client.getParams();
    // keep old values to rest afterwards
    int ct = HttpConnectionParams.getConnectionTimeout(httpParams);
    int st = HttpConnectionParams.getSoTimeout(httpParams);

    HttpConnectionParams.setConnectionTimeout(httpParams, timeout);
    HttpConnectionParams.setSoTimeout(httpParams, timeout);

    HttpResponse response = null;
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
    // http header
    if (headers != null) {
      for (Map.Entry<String, String> header : headers.entrySet()) {
        get.addHeader(StringUtils.trimToEmpty(header.getKey()), StringUtils.trimToEmpty(header.getValue()));
      }
    }
    // authentication
    HttpContext authContext = buildContext(url, credentials);
    HttpResponse response = client.execute(get, authContext);

    Response result = new Response(response);
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      // Adding a default charset in case it is not found
      result.content = EntityUtils.toString(entity, UTF_8);
      EntityUtils.consume(entity);
    }
    return result;
  }

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
  public Response post(String uri, HttpEntity encodedEntity) throws IOException, URISyntaxException {
    return post(uri, null, null, null, encodedEntity);
  }

  public Response post(String uri, HttpParams params, Map<String, String> headers,
    UsernamePasswordCredentials credentials) throws IOException, URISyntaxException {
    return post(uri, params, headers, credentials, null);
  }

  public Response post(String uri, HttpParams params, Map<String, String> headers,
    UsernamePasswordCredentials credentials, HttpEntity encodedEntity) throws IOException, URISyntaxException {
    HttpPost post = new HttpPost(uri);
    post.setHeader(HTTP.CONTENT_TYPE, FORM_URL_ENCODED_CONTENT_TYPE);
    // if (params != null) {
    // post.setParams(params);
    // }
    if (encodedEntity != null) {
      post.setEntity(encodedEntity);
    }
    // authentication
    HttpContext authContext = buildContext(uri, credentials);
    HttpResponse response = client.execute(post, authContext);

    // response
    if (response != null) {
      Response result = new Response(response);
      HttpEntity entity = response.getEntity();
      if (entity != null) {
        result.content = EntityUtils.toString(entity);
        EntityUtils.consume(entity);
      }
      return result;
    }
    return null;
  }

  public boolean verifyHost(HttpHost host) {
    if (host != null) {
      try {
        HttpHead head = new HttpHead(host.toURI());
        client.execute(host, head);
        return true;
      } catch (Exception e) {
        e.printStackTrace();
        LOG.debug("Exception thrown", e);
      }
    }
    return false;
  }
}
