package org.gbif.varnish;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Varnish provides two main ways of invalidating its cache: PURGE and BAN.
 * PURGE truly frees the cache, but only works on individual resource URLs while BANs work with regular expressions
 * and can banRegex entire subresources from being served. BANs do not remove the object from the Varnish memory though.
 *
 * @see <h ref="https://www.varnish-software.com/static/book/Cache_invalidation.html">Varnish Book</h>
 */
public class VarnishPurger {
  private static final Logger LOG = LoggerFactory.getLogger(VarnishPurger.class);
  private final CloseableHttpClient client;

  private final URI apiBaseUrl;
  private final String apiBaseUrlStr;

  //path represents the part after the authority (domain and extension)
  private final String apiPath;

  public VarnishPurger(CloseableHttpClient client, URI apiBaseUrl) {
    Args.check(apiBaseUrl.isAbsolute(), "apiBaseUrl must be absolute");

    this.client = client;
    this.apiBaseUrl = apiBaseUrl;
    this.apiBaseUrlStr = StringUtils.removeEnd(apiBaseUrl.toString(), "/");

    //make sure there is not trailing slash on apiRoot
    this.apiPath = StringUtils.removeEnd(apiBaseUrl.getPath(), "/");
  }

  /**
   * Concatenates keys into an OR'ed list suitable for regular expressions.
   * For example the 3 keys  345, 23 and 778 become: (345|23|778)
   */
  public static String anyKey(Set<?> keys) {
    if (keys.size() == 1) {
      return keys.iterator().next().toString();
    }
    return "(" + String.join("|", keys.stream().map(Object::toString).collect(Collectors.toSet())) + ")";
  }

  public static String path(String ... parts) {
    return String.join("/", parts);
  }

  /**
   * Send a PURGE request to Varnish for a specific path.
   * @param path relative to the API base URL (apiBaseUrl)
   */
  public void purge(String path) {
    Args.notNull(path, "path can not be null");
    URI uri = URI.create(String.format("%s/%s", apiBaseUrlStr, StringUtils.removeStart(path, "/")));
    try {
      CloseableHttpResponse resp = client.execute(new HttpPurge(uri));
      resp.close();
    } catch (IOException e) {
      LOG.error("Failed to purge {}", uri, e);
    }
  }

  /**
   * Send a BAN request to Varnish using a regex.
   * @param regex regex representing the path(s) relative to the API base URL (apiBaseUrl)
   */
  public void ban(String regex) {
    Args.notNull(regex, "regex can not be null");
    regex = String.format("%s/%s", apiPath, StringUtils.removeStart(regex, "/"));
    try {
      CloseableHttpResponse resp = client.execute(new HttpBan(apiBaseUrl, regex));
      resp.close();
    } catch (IOException e) {
      LOG.error("Failed to ban {}", regex, e);
    }
  }

}
