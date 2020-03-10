package eu.erasmuswithoutpaper.catalogueserver.configuration;

import eu.erasmuswithoutpaper.catalogueserver.web.CatalogueUpdater;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * This component manages tasks which are scheduled to be run periodically in production
 * environment.
 */
@Profile({ "production", "development" })
@Component
@SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC_ANON")
public class ProductionScheduledTasks {

  private final CatalogueUpdater catalogueUpdater;

  @Autowired
  public ProductionScheduledTasks(CatalogueUpdater catalogueUpdater) {
    this.catalogueUpdater = catalogueUpdater;
  }

  /**
   * Update local catalogue copy.
   */
  @Scheduled(initialDelay = 0, fixedRate = 1000 * 10)
  public void fetchNewCatalogue() {
    this.catalogueUpdater.updateCatalogue();
  }
}
