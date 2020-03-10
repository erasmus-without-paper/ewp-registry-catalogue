package eu.erasmuswithoutpaper.catalogueserver.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

/**
 * Creates and verifies Metadata for contents of the Catalogue.
 */
public class MetadataVerifier {
  /**
   * Verifies if metadata object was generated for given catalogue content.
   */
  public static <T extends CatalogueMetadata<T>> boolean verifyMetadata(String catalogueContent,
      CatalogueMetadata<T> metadata) {
    return Objects.equals(metadata.getHash(), getHash(catalogueContent));
  }

  /**
   * Calculates hash of of given content.
   */
  public static String getHash(String content) {
    return calculateHash(content);
  }

  private static String calculateHash(String content) {
    MessageDigest digest = null;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 hash not available.");
    }
    byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
    byte[] hash = digest.digest(contentBytes);
    return Base64.getEncoder().encodeToString(hash);
  }

}
