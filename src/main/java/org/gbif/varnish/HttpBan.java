package org.gbif.varnish;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

import java.net.URI;

/**
 * The HTTP BAN method is used by Varnish to flush its cache via HTTP.
 */
@Contract(threading = ThreadingBehavior.UNSAFE)
public class HttpBan extends HttpUriRequestBase {

  public static final String METHOD_NAME = "BAN";
  public static final String BAN_HEADER = "x-ban-url";

  public HttpBan(URI uri, String banRegex) {
    super(METHOD_NAME, uri);
    setHeader(BAN_HEADER, banRegex);
  }

  @Override
  public String getMethod() {
    return METHOD_NAME;
  }
}
