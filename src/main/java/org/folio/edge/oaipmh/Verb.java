package org.folio.edge.oaipmh;

import static com.google.common.collect.ImmutableSet.of;
import static java.util.Collections.EMPTY_SET;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static org.folio.edge.core.Constants.PARAM_API_KEY;
import static org.folio.edge.core.Constants.PATH_API_KEY;
import static org.folio.edge.oaipmh.utils.Constants.FROM;
import static org.folio.edge.oaipmh.utils.Constants.IDENTIFIER;
import static org.folio.edge.oaipmh.utils.Constants.METADATA_PREFIX;
import static org.folio.edge.oaipmh.utils.Constants.RESUMPTION_TOKEN;
import static org.folio.edge.oaipmh.utils.Constants.SET;
import static org.folio.edge.oaipmh.utils.Constants.UNTIL;
import static org.folio.edge.oaipmh.utils.Constants.VERB;
import static org.openarchives.oai._2.OAIPMHerrorcodeType.BAD_ARGUMENT;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.openarchives.oai._2.OAIPMHerrorType;

import io.vertx.ext.web.RoutingContext;

/**
 * Enum that represents OAI-PMH verbs with associated http parameters and validation logic.
 */
public enum Verb {
  GET_RECORD("GetRecord", of(IDENTIFIER, METADATA_PREFIX), EMPTY_SET, null),
  IDENTIFY("Identify", EMPTY_SET, EMPTY_SET, null),
  LIST_IDENTIFIERS("ListIdentifiers", of(METADATA_PREFIX), of(FROM, UNTIL, SET), RESUMPTION_TOKEN),
  LIST_METADATA_FORMATS("ListMetadataFormats", EMPTY_SET, of(IDENTIFIER), null),
  LIST_RECORDS("ListRecords", of(METADATA_PREFIX), of(FROM, UNTIL, SET), RESUMPTION_TOKEN),
  LIST_SETS("ListSets", EMPTY_SET, EMPTY_SET, RESUMPTION_TOKEN);


  /** String name of the verb. */
  private String name;
  /** Required http parameters associated with the verb. */
  private Set<String> requiredParams;
  /** Optional http parameters associated with the verb. */
  private Set<String> optionalParams;
  /** Exclusive (i.e. must be the only one if provided) http parameter associated with the verb. */
  private String exclusiveParam;
  /** All possible parameters associated with the verb. */
  private Set<String> allParams;

  /** ISO Date and Time with UTC offset. */
  private static final DateTimeFormatter ISO_UTC_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
  /** Parameters which must be excluded from validation if present in http request. */
  private final Set<String> excludeParams = of(VERB, PARAM_API_KEY, PATH_API_KEY);

  private static final Map<String, Verb> CONSTANTS = new HashMap<>();
  static {
    for (Verb v : values())
      CONSTANTS.put(v.name, v);
  }


  Verb(String name, Set<String> requiredParams, Set<String> optionalParams, String exclusiveParam) {
    this.name = name;
    this.requiredParams = requiredParams;
    this.optionalParams = optionalParams;
    this.exclusiveParam = exclusiveParam;

    allParams = concat(concat(requiredParams.stream(), optionalParams.stream()), excludeParams.stream())
      .collect(toSet());
    allParams.add(exclusiveParam);
  }

  /**
   * Checks if given {@link RoutingContext}'s http parameters are valid for current verb.
   *
   * @param ctx {@link RoutingContext} to validate
   * @return list of {@link OAIPMHerrorType} errors if any or empty list otherwise
   */
  public List<OAIPMHerrorType> validate(RoutingContext ctx) {
    List<OAIPMHerrorType> errors = new ArrayList<>();

    validateIllegalParams(ctx, errors);
    validateExclusiveParam(ctx, errors);

    if (ctx.request().getParam(FROM) != null) {
      validateDatestamp(FROM, ctx.request().getParam(FROM), errors);
    }
    if (ctx.request().getParam(UNTIL) != null) {
      validateDatestamp(UNTIL, ctx.request().getParam(UNTIL), errors);
    }

    return errors;
  }

  public static Verb fromName(String name) {
    return CONSTANTS.get(name);
  }

  public Set<String> getRequiredParams() {
    return requiredParams;
  }

  public Set<String> getOptionalParams() {
    return optionalParams;
  }

  public String getExclusiveParam() {
    return exclusiveParam;
  }

  @Override
  public String toString() {
    return this.name;
  }


  private void validateIllegalParams(RoutingContext ctx, List<OAIPMHerrorType> errors) {
    ctx.request().params().entries().stream()
      .map(Entry::getKey)
      .filter(param -> !allParams.contains(param))
      .filter(param -> !excludeParams.contains(param))
      .forEach(param -> errors.add(new OAIPMHerrorType()
        .withCode(BAD_ARGUMENT)
        .withValue("Verb '" + name + "', illegal argument: " + param)));
  }

  private void validateExclusiveParam(RoutingContext ctx, List<OAIPMHerrorType> errors) {
    if (exclusiveParam != null && ctx.request().getParam(exclusiveParam) != null) {
      ctx.request().params().entries().stream()
        .map(Entry::getKey)
        .filter(p -> !excludeParams.contains(p))
        .filter(p -> !exclusiveParam.equals(p))
        .findAny()
        .ifPresent(param -> errors.add(new OAIPMHerrorType()
          .withCode(BAD_ARGUMENT)
          .withValue("Verb '" + name + "', argument '" + exclusiveParam +
            "' is exclusive, no others maybe specified with it.")));
    }
  }

  private void validateDatestamp(String paramName, String paramValue, List<OAIPMHerrorType> errors) {
    try {
      LocalDateTime.parse(paramValue, ISO_UTC_DATE_TIME);
    } catch (DateTimeParseException e) {
      errors.add(new OAIPMHerrorType()
        .withCode(BAD_ARGUMENT)
        .withValue("Bad datestamp format for '" + paramName + "' argument."));
    }
  }
}
