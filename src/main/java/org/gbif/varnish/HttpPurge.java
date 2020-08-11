package org.gbif.varnish;

import java.net.URI;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * The HTTP PURGE method is used by Varnish to flush its cache via HTTP.
 */
@Contract(threading = ThreadingBehavior.UNSAFE)
public class HttpPurge extends HttpUriRequestBase {

  public static final String METHOD_NAME = "PURGE";

  public HttpPurge(final URI uri) {
    super(METHOD_NAME, uri);
  }
}
