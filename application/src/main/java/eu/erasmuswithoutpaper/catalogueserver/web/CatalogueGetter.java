package eu.erasmuswithoutpaper.catalogueserver.web;

public interface CatalogueGetter<T extends CatalogueMetadata<T>> {
  CatalogueGetterResponse<T> fetchCatalogue(T metadata)
      throws CatalogueFetchException;

  class CatalogueFetchException extends Exception {
    public CatalogueFetchException(String message) {
      super(message);
    }
  }
}


