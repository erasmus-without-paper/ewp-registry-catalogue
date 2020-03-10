package eu.erasmuswithoutpaper.catalogueserver.web;

import javax.validation.constraints.NotNull;

/**
 * In memory copy of the Catalogue contents and metadata, they are always read and updated together.
 * Moreover, storing them in a single immutable object makes accessing them thread safe.
 */
public class CatalogueAndMetadata<T extends CatalogueMetadata<T>> {
  private final T catalogueMetadata;
  private final String catalogueContents;

  CatalogueAndMetadata(@NotNull T catalogueMetadata, String catalogueContents) {
    this.catalogueMetadata = catalogueMetadata;
    this.catalogueContents = catalogueContents;
  }

  public T getCatalogueMetadata() {
    return catalogueMetadata;
  }

  public String getCatalogueContents() {
    return catalogueContents;
  }
}
