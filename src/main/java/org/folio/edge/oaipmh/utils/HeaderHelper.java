package org.folio.edge.oaipmh.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HeaderHelper {
  private static HeaderHelper headerHelper = new HeaderHelper();

  public static HeaderHelper getInstance() {
    return headerHelper;
  }

  private HeaderHelper() {
  }

  public List<String> parseHeaders(List<String> headers) {
    List<String> headersList = headers.stream()
      .map(val -> val.split(",|;"))
      .flatMap(val -> Arrays.stream(val))
      .collect(Collectors.toList());
    return headersList.stream()
      .map(String::trim)
      .collect(Collectors.toList());
  }
}
