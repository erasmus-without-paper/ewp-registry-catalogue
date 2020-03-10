package eu.erasmuswithoutpaper.catalogueserver.web;

import java.io.StringReader;
import java.time.Instant;

import javax.xml.bind.JAXB;

public interface CatalogueMetadata<U extends CatalogueMetadata<U>> {
  /**
   * Creates CatalogueMetadataGitHub from its XML representation.
   *
   * @param input XML representation as a string.
   * @return XML Converted to CatalogueMetadataGitHub .
   * @throws CatalogueMetadataParseException if XML cannot be converted to CatalogueMetadataGitHub .
   */
  static <T extends CatalogueMetadata<T>> T fromXmlString(
      String input, Class<T> metadataClass) throws CatalogueMetadataParseException {
    try {
      return JAXB.unmarshal(new StringReader(input), metadataClass);
    } catch (RuntimeException e) {
      throw new CatalogueMetadataParseException();
    }
  }

  String toXmlString() throws CatalogueMetadataConversionException;

  Instant getModificationDate();

  Instant getLastFetchDate();

  String getHash();

  /**
   * Creates a copy of current metadata object with updated lastFetchDate.
   */
  U withUpdatedLastFetchDate(Instant newLastFetchDate);

  class CatalogueMetadataParseException extends Exception {
  }


  class CatalogueMetadataConversionException extends Exception {
    public CatalogueMetadataConversionException(Exception exception) {
      super(exception);
    }
  }
}
