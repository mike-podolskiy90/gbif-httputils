package org.gbif.varnish;

import java.net.URI;

import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.client.methods.HttpRequestBase;

/**
 * The HTTP PURGE method is used by varnish to flush its cache via http.
 */
@Contract(threading = ThreadingBehavior.UNSAFE)
public class HttpPurge extends HttpRequestBase {

  public static final String METHOD_NAME = "PURGE";


  public HttpPurge(final URI uri) {
    super();
    setURI(uri);
  }

  @Override
  public String getMethod() {
    return METHOD_NAME;
  }

}
