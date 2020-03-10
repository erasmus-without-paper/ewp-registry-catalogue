package eu.erasmuswithoutpaper.catalogueserver.web;

import java.time.Instant;

public class CatalogueGetterResponse<T extends CatalogueMetadata<T>> {

  public final Status status;
  public final String content;
  public final Instant retryAfter;
  public final T metadata;

  /**
   * Describes a catalogue fetched by {@link CatalogueGetter}.
   *
   * @param content  contents of fetched catalogue.
   * @param metadata metadata of fetched catalogue.
   */
  private CatalogueGetterResponse(Status status, String content, Instant retryAfter, T metadata) {
    this.status = status;
    this.content = content;
    this.metadata = metadata;
    this.retryAfter = retryAfter;
  }

  public static <T extends CatalogueMetadata<T>> CatalogueGetterResponse<T> createOk(String content,
      T metadata) {
    return new CatalogueGetterResponse<T>(Status.OK, content, null, metadata);
  }

  public static <T extends CatalogueMetadata<T>> CatalogueGetterResponse<T> createNotModified() {
    return new CatalogueGetterResponse<T>(Status.NOT_MODIFIED, null, null, null);
  }

  public static <T extends CatalogueMetadata<T>> CatalogueGetterResponse<T> createRateLimited(
      Instant retryAfter) {
    return new CatalogueGetterResponse<T>(Status.RATE_LIMITED, null, retryAfter, null);
  }

  public static <T extends CatalogueMetadata<T>> CatalogueGetterResponse<T> createForbidden() {
    return new CatalogueGetterResponse<T>(Status.FORBIDDEN, null, null, null);
  }

  public enum Status {
    OK,
    NOT_MODIFIED,
    RATE_LIMITED,
    FORBIDDEN
  }

}
