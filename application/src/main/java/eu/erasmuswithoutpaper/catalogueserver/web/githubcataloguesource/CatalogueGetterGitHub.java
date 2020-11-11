package eu.erasmuswithoutpaper.catalogueserver.web.githubcataloguesource;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import eu.erasmuswithoutpaper.catalogueserver.web.CatalogueGetter;
import eu.erasmuswithoutpaper.catalogueserver.web.CatalogueGetterResponse;
import eu.erasmuswithoutpaper.catalogueserver.web.GitHubData;
import eu.erasmuswithoutpaper.catalogueserver.web.MetadataVerifier;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CatalogueGetterGitHub implements CatalogueGetter<CatalogueMetadataGitHub> {
  private static final Logger logger = LoggerFactory.getLogger(CatalogueGetter.class);
  private final CloseableHttpClient httpClient;
  private final GitHubData gitHubData;

  /**
   * Creates a catalogue getter.
   */
  public CatalogueGetterGitHub(CloseableHttpClient httpClient, GitHubData gitHubData) {
    this.httpClient = httpClient;
    this.gitHubData = gitHubData;
  }

  /**
   * Fetches a catalogue from remote host. Sends etag in 'If-None-Match' header to save bandwidth.
   *
   * @param metadata CatalogueMetadata with etag to send in a 'If-None-Match' header.
   * @return CatalogueGetterResponse describing the response.
   * @throws CatalogueFetchExceptionGitHub on connection error.
   */
  public CatalogueGetterResponse<CatalogueMetadataGitHub> fetchCatalogue(
      CatalogueMetadataGitHub metadata)
      throws CatalogueFetchExceptionGitHub {
    String etag = null;
    if (metadata != null) {
      etag = metadata.getGitHubEtag();
    }
    HttpGet request = getCatalogueFileRequest(etag);

    try (CloseableHttpResponse response = this.httpClient.execute(request)) {
      logger.info("Response status line {}", response.getStatusLine());
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        String newEtag = getEtag(response);
        String content = getContent(response);
        Instant modificationDate = fetchModificationDate();
        return CatalogueGetterResponse.createOk(
            content,
            new CatalogueMetadataGitHub(
                modificationDate,
                Instant.now(),
                MetadataVerifier.getHash(content),
                newEtag
            )
        );
      } else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_MODIFIED) {
        return CatalogueGetterResponse.createNotModified();
      } else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_FORBIDDEN) {
        Instant retryAfterSeconds = getNextAllowedCallInstant(response);
        String retryAfterMessage = getRetryAfterMessage(retryAfterSeconds);

        logger.error(
            "FORBIDDEN 403 status code received.\n{}\n" +
                    "This might mean that GitHub has cut us out. {}.",
            EntityUtils.toString(response.getEntity()),
            retryAfterMessage
        );

        if (retryAfterSeconds != null) {
          return CatalogueGetterResponse.createRateLimited(retryAfterSeconds);
        } else {
          return CatalogueGetterResponse.createForbidden();
        }
      } else {
        logger.error("Unexpected response code while fetching new Catalogue: {}",
            response.getStatusLine().getStatusCode());
        throw new CatalogueFetchExceptionGitHub(request.getURI().toASCIIString(),
            response.getStatusLine().getStatusCode());
      }
    } catch (IOException e) {
      String url = request.getURI().toASCIIString();
      logger.error("Exception while fetching new Catalogue from {}", url, e);
      throw new CatalogueFetchExceptionGitHub(url);
    }
  }

  private String createUriErrorMessage(String name, String path, String query) {
    return String.format("Cannot create an URL for fetching catalogue %s. "
            + "This is either configuration problem or programming error. "
            + "Following parts were used to create the URL:%n"
            + "GitHub user name: %s%n"
            + "GitHub repository name: %s%n"
            + "GitHub file path: %s%n"
            + "Requested endpoint: %s%n"
            + "Query params: %s%n",
        name,
        this.gitHubData.getGitHubUserName(),
        this.gitHubData.getGitHubRepositoryName(),
        this.gitHubData.getGitHubFilePath(),
        path,
        query);
  }

  private String getCatalogueFileUrl() {
    String path = String
        .format("/%s/%s/master/%s", this.gitHubData.getGitHubUserName(),
            this.gitHubData.getGitHubRepositoryName(), this.gitHubData.getGitHubFilePath());
    try {
      URI uri = new URI("https", this.gitHubData.getGitHubRawUrl(), path, null, null);
      return uri.toASCIIString();
    } catch (URISyntaxException e) {
      String error = createUriErrorMessage("contents", path, null);
      logger.error(error, e);
      throw new IllegalArgumentException();
    }
  }

  private HttpGet createGetRequest(String url) {
    HttpGet request = new HttpGet(url);
    if (this.gitHubData.getGitHubAuthHeader() != null) {
      request.setHeader(HttpHeaders.AUTHORIZATION, this.gitHubData.getGitHubAuthHeader());
    }
    return request;
  }

  private HttpGet getCatalogueFileRequest(String etag) {
    HttpGet request = createGetRequest(this.getCatalogueFileUrl());

    if (etag != null) {
      request.addHeader("If-None-Match", etag);
    }

    return request;
  }

  private String getMetadataUrl() {
    String path = String.format(
        "/repos/%s/%s/commits", this.gitHubData.getGitHubUserName(),
        this.gitHubData.getGitHubRepositoryName()
    );
    String query = String.format("path=%s&page=1&per_page=1", this.gitHubData.getGitHubFilePath());
    try {
      URI uri = new URI("https", this.gitHubData.getGitHubApiUrl(), path, query, null);
      return uri.toASCIIString();
    } catch (URISyntaxException e) {
      String error = createUriErrorMessage("metadata", path, query);
      logger.error(error, e);
      throw new IllegalArgumentException();
    }
  }

  private HttpGet getMetadataRequest() {
    HttpGet request = createGetRequest(this.getMetadataUrl());
    request.addHeader("Accept", "application/vnd.github.v3+json");
    return request;
  }

  private String getContent(CloseableHttpResponse response) throws IOException {
    HttpEntity entity = response.getEntity();
    return EntityUtils.toString(entity);
  }

  private String getEtag(CloseableHttpResponse response) {
    Header etag = response.getFirstHeader("ETag");
    return etag.getValue();
  }

  private String getRetryAfterMessage(Instant retryAfterSeconds) {
    String retryAfterMessage;
    if (retryAfterSeconds == null) {
      retryAfterMessage = "No Retry-After nor X-RateLimit-Reset provided.";
    } else {
      retryAfterMessage = String.format("Allowed to retry after: %s", retryAfterSeconds);
    }
    return retryAfterMessage;
  }

  private Integer parseIntegerHeader(CloseableHttpResponse response, String headerName) {
    Header header = response.getFirstHeader(headerName);
    if (header == null) {
      return null;
    }
    try {
      return Integer.valueOf(header.getValue());
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private Instant getNextAllowedCallInstant(CloseableHttpResponse response) {
    // If we have received 403 Forbidden it means that we have been rate limited - we have issued
    // too much non-conditional requests to GitHub API. Headers in response inform us when can we
    // issue another request without getting 403 Forbidden response again.
    // GitHub gives 60 non-conditional API calls per hour for unauthenticated users and
    // 5000 non-conditional API calls per hour for authenticated users.
    // A API Call is considered conditional if it contains 'If-*' headers and received
    // 304 Not Modified status, those calls do not decrease available API Call count.
    // We are using conditional calls thus our limit is decreased only when the Catalogue
    // is modified and it is very unlikely that we will hit that limit.
    // We might however be temporarily blocked for other reasons, e.g. concurrent calls from the
    // same IP, if we share IP address with another application that used GitHub API.
    // X-RateLimit-Remaining contains a number of calls that we can issue before hitting the limit.
    // If we have reached limit of API Calls from this IP/from this Client ID, then
    // X-RateLimit-Reset indicates when our limit will be reset back to the default value.
    // Retry-After header will contain number of seconds we have to wait before sending another
    // request, it is used when we've been rate limited for different reason.
    final Integer retryAfterSeconds = parseIntegerHeader(response, "Retry-After");
    final Integer rateLimitReset = parseIntegerHeader(response, "X-RateLimit-Reset");
    final Integer remainingCalls = parseIntegerHeader(response, "X-RateLimit-Remaining");
    logger.info("Rate-Limit related headers: Retry-After: {}, X-RateLimit-Reset: {}, "
        + "X-RateLimit-Remaining: {}", retryAfterSeconds, rateLimitReset, remainingCalls);
    if (retryAfterSeconds != null) {
      return Instant.now().plus(retryAfterSeconds + 1, ChronoUnit.SECONDS);
    } else if (rateLimitReset != null) {
      // It happens that, after we have been rate limited, GitHub returns 403 Forbidden for few
      // seconds after resetting calls limit at 'X-RateLimit-Reset' time.
      // The X-RateLimit-Remaining and X-RateLimit-Reset values, however, are given new values
      // exactly at 'X-RateLimit-Remaining' time.
      // Therefore, if we have received 403 Forbidden, but still have some calls left,
      // we will wait for a minute instead of waiting until 'X-RateLimit-Reset'.
      if (remainingCalls != null && remainingCalls > 0) {
        return Instant.now().plus(30, ChronoUnit.SECONDS);
      }
      // Additionally, if we have really been rate limited, we will issue next call 30 seconds after
      // 'X-RateLimit-Reset', to lower the chance of getting 403 just after X-RateLimit-Reset.
      return Instant.ofEpochSecond(rateLimitReset).plus(30, ChronoUnit.SECONDS);
    } else {
      return null;
    }
  }

  private Instant fetchModificationDate() throws CatalogueFetchExceptionGitHub {
    HttpGet request = getMetadataRequest();
    try (CloseableHttpResponse response = this.httpClient.execute(request)) {
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        HttpEntity entity = response.getEntity();
        String content = EntityUtils.toString(entity);
        JSONArray responseArray = new JSONArray(content);
        String date = responseArray
            .getJSONObject(0)
            .getJSONObject("commit")
            .getJSONObject("author")
            .getString("date");
        return ZonedDateTime.parse(date, DateTimeFormatter.ISO_DATE_TIME).toInstant();
      } else {
        logger.error("Unexpected response code while fetching new Catalogue metadata: {}",
            response.getStatusLine().getStatusCode());
        throw new CatalogueFetchExceptionGitHub(request.getURI().toASCIIString(),
            response.getStatusLine().getStatusCode());
      }
    } catch (IOException | JSONException e) {
      logger.error("Exception while fetching new Catalogue metadata from {}",
          request.getURI().toASCIIString(), e);
      throw new CatalogueFetchExceptionGitHub(request.getURI().toASCIIString());
    }
  }

  static class CatalogueFetchExceptionGitHub extends CatalogueFetchException {
    public CatalogueFetchExceptionGitHub(String url) {
      super("Error while fetching the Catalogue from " + url);
    }

    public CatalogueFetchExceptionGitHub(String url, int statusCode) {
      super("Error while fetching the Catalogue from " + url + ". Status code: " + statusCode);
    }
  }
}
