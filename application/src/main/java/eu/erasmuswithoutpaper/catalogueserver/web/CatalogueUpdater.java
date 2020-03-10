package eu.erasmuswithoutpaper.catalogueserver.web;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CatalogueUpdater<T extends CatalogueMetadata<T>> {
  private static final Logger logger = LoggerFactory.getLogger(CatalogueUpdater.class);
  private final CatalogueCopy<T> catalogueCopy;
  private final CatalogueGetter<T> catalogueGetter;
  private Instant nextAllowedFetchInstant;

  /**
   * Used to fetch new catalogue and update cached content.
   */
  public CatalogueUpdater(CatalogueCopy<T> catalogueCopy, CatalogueGetter<T> catalogueGetter) {
    this.catalogueCopy = catalogueCopy;
    this.catalogueGetter = catalogueGetter;
    this.nextAllowedFetchInstant = Instant.now();
  }

  /**
   * Fetches new Catalogue and updates cached contents.
   */
  public void updateCatalogue() {
    logger.info("Fetching new Catalogue.");

    if (Instant.now().isBefore(this.nextAllowedFetchInstant)) {
      logger.info("Catalogue not fetched - we have been rate limited until {}",
          this.nextAllowedFetchInstant);
      return;
    }

    CatalogueAndMetadata<T> catalogueAndMetadata = this.catalogueCopy.getCatalogueAndMetadata();
    T catalogueMetadata = null;
    if (catalogueAndMetadata != null) {
      catalogueMetadata = catalogueAndMetadata.getCatalogueMetadata();
    }

    try {
      CatalogueGetterResponse<T> response = this.catalogueGetter.fetchCatalogue(catalogueMetadata);
      switch (response.status) {
        case OK: {
          onOkResponse(response);
          break;
        }
        case NOT_MODIFIED: {
          onNotModifiedResponse();
          break;
        }
        case RATE_LIMITED: {
          onRateLimited(response);
          break;
        }
        case FORBIDDEN: {
          onForbidden();
          break;
        }
        default:
          throw new CatalogueGetter.CatalogueFetchException("Unknown response status.");
      }
    } catch (CatalogueGetter.CatalogueFetchException e) {
      logger.error("Cannot fetch catalogue.", e);
    }
  }

  private void onOkResponse(CatalogueGetterResponse<T> response) {
    this.catalogueCopy.onCatalogueFetchedWithChanges(response);
    logger.info("Catalogue fetched - changed.");
  }

  private void onNotModifiedResponse() {
    this.catalogueCopy.onCatalogueFetchedWithoutChanges();
    logger.info("Catalogue fetched - no changes.");
  }

  private void onRateLimited(CatalogueGetterResponse<T> response) {
    Instant retryAfter = response.retryAfter;
    logger.info("Catalogue not fetched - rate limited until {}.", retryAfter);
    this.nextAllowedFetchInstant = retryAfter;
  }

  private void onForbidden() {
    Instant retryAfter = Instant.now().plus(1, ChronoUnit.HOURS);
    logger.info("Catalogue not fetched - forbidden. Delaying next fetch until {}.", retryAfter);
    this.nextAllowedFetchInstant = retryAfter;
  }

  public CatalogueCopy<T> getCatalogueCopy() {
    return this.catalogueCopy;
  }
}
