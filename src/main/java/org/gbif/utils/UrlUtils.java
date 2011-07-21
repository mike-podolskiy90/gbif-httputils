package org.gbif.utils;

import org.apache.commons.lang.StringUtils;

/**
 * Utilitiy class for dealing with URLs in addition to the methods
 * found in commonly used classes:
 *
 * @See java.net.URL
 * @See java.net.URI
 * @See java.net.URLEncoder
 * @See org.apache.http.client.utils.URLEncodedUtils
 */
public class UrlUtils {

  /*
    method to escape whitespace for complete Url strings as
    httpclient has removed the convenient org.apache.commons.httpclient.util.URIUtil class in its 4.x version

    Whitespace in a URL path is not allowed and should be replaced with %20.
    Creating a URI instance with whitespace results in a URISyntaxException

    The regular URL encoding is for parameters only, but not suited to escape the path, protocol or host part.
    If the url at hand can be atomised into these parts the URI class provides constructors that deal with escaping.

    If only a complete url string exists this method comes in handy to avoid the URISyntaxException.

   */
  public static String encodeURLWhitespace(final String s) {
    return StringUtils.trimToEmpty(s).replaceAll(" ", "%20");
  }

}
