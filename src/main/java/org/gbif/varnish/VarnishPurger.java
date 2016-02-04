package org.gbif.varnish;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import com.google.common.base.Joiner;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Varnish provides two main ways of invalidating its cache: PURGE and BAN.
 * PURGE truly frees the cache, but only works on individual resource URLs while BANs work with regular expressions
 * and can banRegex entire subresources from being served. BANs do not remove the object from the varnish memory though.
 *
 * @see <h ref="https://www.varnish-software.com/static/book/Cache_invalidation.html">Varnish Book</h>
 */
public class VarnishPurger {
  private static final Logger LOG = LoggerFactory.getLogger(VarnishPurger.class);
  private final CloseableHttpClient client;
  private final URI apiBaseUrl;
  private final String apiRoot;
  private static final Joiner PATH_JOINER = Joiner.on("/").skipNulls();
  private static final Joiner PIPE_JOINER = Joiner.on("|").skipNulls();

  public VarnishPurger(CloseableHttpClient client, URI apiBaseUrl) {
    this.client = client;
    this.apiBaseUrl = apiBaseUrl;
    apiRoot = apiBaseUrl.getPath();
  }

  /**
   * Concatenates keys into an OR'ed list suitable for regular expressions.
   * For example the 3 keys  345, 23 and 778 become: (345|23|778)
   */
  public static String anyKey(Set<?> keys) {
    if (keys.size() == 1) {
      return keys.iterator().next().toString();
    }
    return "(" + PIPE_JOINER.join(keys) + ")";
  }

  public static String path(String ... parts) {
    return PATH_JOINER.join(parts);
  }

  /**
   * @param path relative to the API base URL which gets prepended
   */
  public void purge(String path) {
    URI uri = URI.create(String.format("%s/%s", apiRoot, path));
    try {
      CloseableHttpResponse resp = client.execute(new HttpPurge(uri));
      resp.close();
    } catch (IOException e) {
      LOG.error("Failed to purge {}", uri, e);
    }
  }

  /**
   * @param regex ban regex relative to the API base URL which gets prepended
   */
  public void ban(String regex) {
    regex = String.format("%s/%s", apiRoot, regex);
    try {
      CloseableHttpResponse resp = client.execute(new HttpBan(apiBaseUrl, regex));
      resp.close();
    } catch (IOException e) {
      LOG.error("Failed to ban {}", regex, e);
    }
  }

}
