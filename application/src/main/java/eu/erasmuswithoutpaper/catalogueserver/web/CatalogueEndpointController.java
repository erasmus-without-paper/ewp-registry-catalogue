package eu.erasmuswithoutpaper.catalogueserver.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

@RestController
public class CatalogueEndpointController<T extends CatalogueMetadata<T>> {
  private final CatalogueCopy<T> catalogueCopy;

  @Autowired
  public CatalogueEndpointController(CatalogueUpdater<T> catalogueUpdater) {
    this.catalogueCopy = catalogueUpdater.getCatalogueCopy();
  }

  /**
   * @return a HTTP response with the catalogue contents.
   */
  @RequestMapping("/catalogue-v1.xml")
  public ResponseEntity<String> getCatalogue(ServletWebRequest webRequest) {
    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.setCacheControl("max-age=300, must-revalidate");
    responseHeaders.setContentType(MediaType.APPLICATION_XML);
    responseHeaders.setExpires(System.currentTimeMillis() + 300000);

    CatalogueAndMetadata<T> catalogueAndMetadata = this.catalogueCopy.getCatalogueAndMetadata();

    if (catalogueAndMetadata == null || catalogueAndMetadata.getCatalogueMetadata() == null
        || catalogueAndMetadata.getCatalogueContents() == null) {
      throw new CatalogueNotAvailableException();
    }
    String etag = catalogueAndMetadata.getCatalogueMetadata().getHash();

    long modificationTimestampMillis =
        catalogueAndMetadata.getCatalogueMetadata().getModificationDate().getEpochSecond() * 1000;

    // Check 'If-None-Match' and 'If-Modified-Since' headers if present.
    // Additionally, this method attaches 'ETag' and 'Last-Modified' timestamps to webRequest,
    // they are used later in response.
    if (webRequest.checkNotModified(etag, modificationTimestampMillis)) {
      // Returning null results in HTTP 304 NOT MODIFIED response,
      // that status was automatically set in checkNotModified.
      return null;
    }

    return new ResponseEntity<>(catalogueAndMetadata.getCatalogueContents(), responseHeaders,
        HttpStatus.OK);
  }

  @ResponseStatus(code = HttpStatus.SERVICE_UNAVAILABLE,
      reason = "Local copy of the Catalogue is unavailable.")
  public static class CatalogueNotAvailableException extends RuntimeException {
  }
}
