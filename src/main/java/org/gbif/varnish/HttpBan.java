package org.gbif.varnish;

import java.net.URI;

import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.client.methods.HttpRequestBase;

/**
 * The HTTP BAN method is used by varnish to flush its cache via http.
 */
@Contract(threading = ThreadingBehavior.UNSAFE)
public class HttpBan extends HttpRequestBase {

  public static final String METHOD_NAME = "BAN";
  public static final String BAN_HEADER = "x-ban-url";

  public HttpBan(URI uri, String banRegex) {
    super();
    setURI(uri);
    setHeader(BAN_HEADER, banRegex);
  }

  @Override
  public String getMethod() {
    return METHOD_NAME;
  }

}
