package eu.erasmuswithoutpaper.catalogueserver.configuration;

import java.util.List;

import eu.erasmuswithoutpaper.catalogueserver.web.CatalogueCopy;
import eu.erasmuswithoutpaper.catalogueserver.web.CatalogueGetter;
import eu.erasmuswithoutpaper.catalogueserver.web.CatalogueUpdater;
import eu.erasmuswithoutpaper.catalogueserver.web.GitHubData;
import eu.erasmuswithoutpaper.catalogueserver.web.githubcataloguesource.CatalogueGetterGitHub;
import eu.erasmuswithoutpaper.catalogueserver.web.githubcataloguesource.CatalogueMetadataGitHub;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * Spring beans to be used when running an actual application server.
 */
@Profile({ "production", "development" })
@Configuration
public class ProductionConfiguration {
  public ProductionConfiguration() {
    setSystemProperties();
  }

  private void setSystemProperties() {
    // Allow manual setting of "Content-Length" header in requests
    allowSettingRestrictedHeaders();
    // Some of the partners require AIA extension to be used to verify theirs certificate chain.
    enableAuthorityInformationAccessCertificateExtension();
  }

  private void enableAuthorityInformationAccessCertificateExtension() {
    System.setProperty("com.sun.security.enableAIAcaIssuers", "true");
  }

  private void allowSettingRestrictedHeaders() {
    System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
  }

  /**
   * Get {@link ThreadPoolTaskExecutor} to be used in production environment.
   *
   * <p>
   * When running the actual application server, a {@link ThreadPoolTaskExecutor} will be used for
   * handling async runnables.
   * </p>
   *
   * @return {@link ThreadPoolTaskExecutor} instance.
   */
  @Bean
  public TaskExecutor getTaskExecutor() {
    return new ThreadPoolTaskExecutor();
  }

  /**
   * This enables Spring's default conversion filters to be applied to Spring's {@link Value}
   * annotations, e.g. comma-separated list of property values will be properly mapped to
   * {@link List}s.
   *
   * @return {@link DefaultConversionService}
   */
  @Bean
  public ConversionService conversionService() {
    return new DefaultConversionService();
  }

  @Bean
  public CloseableHttpClient closeableHttpClient() {
    return HttpClients.createDefault();
  }

  /**
   * Constructs CatalogueUpdater implementation to be used by the application.
   */
  @Bean
  public CatalogueUpdater<CatalogueMetadataGitHub> catalogueGetterGitHub(
      CloseableHttpClient httpClient, GitHubData gitHubData) {
    CatalogueGetter<CatalogueMetadataGitHub> getter =
        new CatalogueGetterGitHub(httpClient, gitHubData);
    CatalogueCopy<CatalogueMetadataGitHub> copy =
        new CatalogueCopy<>(CatalogueMetadataGitHub.class);
    return new CatalogueUpdater<>(copy, getter);
  }

}
