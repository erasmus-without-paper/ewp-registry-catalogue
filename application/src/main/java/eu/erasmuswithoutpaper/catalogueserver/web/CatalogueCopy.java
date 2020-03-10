package eu.erasmuswithoutpaper.catalogueserver.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class manages in-memory and on-filesystem copies of the Catalogue and its metadata.
 * It reads the catalogue from file at the beginning and saves it to file after an update.
 */
public class CatalogueCopy<T extends CatalogueMetadata<T>> {
  private static final Logger logger = LoggerFactory.getLogger(CatalogueCopy.class);

  private final String catalogueMetadataFilename;
  private final String catalogueCopyFilename;
  private final String catalogueDirectory;
  private final Class<T> metadataClass;
  private CatalogueAndMetadata<T> catalogueAndMetadata;

  /**
   * Create a CatalogueCopy object and read Catalogue contents and metadata from local files.
   */
  public CatalogueCopy(Class<T> metadataClass) {
    this.catalogueDirectory = "/cache";
    this.catalogueCopyFilename = "catalogue-v1.xml";
    this.catalogueMetadataFilename = "catalogue-v1-metadata.xml";
    this.metadataClass = metadataClass;

    this.catalogueAndMetadata = this.readCatalogueAndMetadataFromFilesystem();
  }

  private CatalogueAndMetadata<T> readCatalogueAndMetadataFromFilesystem() {
    String catalogueContent = readCatalogueContent();
    if (catalogueContent == null) {
      // Local copy of the Catalogue is not present.
      return null;
    }

    T catalogueMetadata = readCatalogueMetadata();
    if (catalogueMetadata == null) {
      // Catalogue Metadata file is missing, even if there is a local copy of the catalogue we
      // won't be able to verify it's contents or tell how old it is. Local copy is ignored.
      return null;
    }

    if (!MetadataVerifier.verifyMetadata(catalogueContent, catalogueMetadata)) {
      // We are unable to verify the contents of the catalogue file, e.g. hash stored in metadata
      // doesn't match. Local copy is ignored.
      return null;
    }

    return new CatalogueAndMetadata<T>(catalogueMetadata, catalogueContent);
  }

  public CatalogueAndMetadata<T> getCatalogueAndMetadata() {
    return this.catalogueAndMetadata;
  }

  /**
   * Called when fetched catalogue is different than current version.
   *
   * @param response CatalogueGetterResponse containing the new catalogue.
   */
  public void onCatalogueFetchedWithChanges(CatalogueGetterResponse<T> response) {
    String newContent = response.content;
    T newMetadata = response.metadata;

    this.setCatalogueAndMetadata(newMetadata, newContent);
  }

  private void setCatalogueAndMetadata(T newMetadata, String newContent) {
    boolean metadataSaved = saveMetadataToFile(newMetadata);
    boolean catalogueSaved = saveCatalogueToFile(newContent);
    if (!metadataSaved || !catalogueSaved) {
      throw new RuntimeException("Couldn't perform a write to metadata or catalogue cache file. "
          + "Check your configuration. Is directory with those files writeable?");
    }

    this.catalogueAndMetadata = new CatalogueAndMetadata<T>(newMetadata, newContent);
  }

  /**
   * Called when fetched catalogue is identical to current version.
   */
  public void onCatalogueFetchedWithoutChanges() {
    T newMetadata =
        this.catalogueAndMetadata.getCatalogueMetadata().withUpdatedLastFetchDate(Instant.now());
    this.setCatalogueMetadata(newMetadata);
  }

  private T readCatalogueMetadata() {
    String metadataFileContents;
    try {
      metadataFileContents =
          FileUtils.readFile(this.catalogueDirectory, this.catalogueMetadataFilename);
    } catch (FileUtils.ReadFileException e) {
      return null;
    }
    try {
      return CatalogueMetadata.fromXmlString(metadataFileContents, this.metadataClass);
    } catch (CatalogueMetadata.CatalogueMetadataParseException e) {
      String errorFormat = "Catalogue Metadata file read from {}/{} was malformed. "
          + "It will be ignored and overridden on next Catalogue fetch.";
      logger.error(errorFormat, this.catalogueDirectory, this.catalogueMetadataFilename, e);
      return null;
    }
  }

  private String readCatalogueContent() {
    try {
      return FileUtils.readFile(this.catalogueDirectory, this.catalogueCopyFilename);
    } catch (FileUtils.ReadFileException e) {
      return null;
    }
  }

  private boolean saveCatalogueToFile(String content) {
    try {
      FileUtils.writeToFile(this.catalogueDirectory, this.catalogueCopyFilename,
          content.getBytes(StandardCharsets.UTF_8));
    } catch (FileUtils.WriteFileException e) {
      logger.error("Cannot write catalogue to file.", e);
      return false;
    }
    return true;
  }

  private boolean saveMetadataToFile(T catalogueMetadata) {
    String xmlCatalogueMetadata = null;
    try {
      xmlCatalogueMetadata = catalogueMetadata.toXmlString();
    } catch (CatalogueMetadata.CatalogueMetadataConversionException e) {
      logger.error("Cannot convert CatalogueMetadata to XML.", e);
      return false;
    }

    try {
      FileUtils.writeToFile(this.catalogueDirectory, this.catalogueMetadataFilename,
          xmlCatalogueMetadata.getBytes(StandardCharsets.UTF_8));
    } catch (FileUtils.WriteFileException e) {
      logger.error("Cannot write catalogue metadata to file.", e);
      return false;
    }
    return true;
  }

  private void setCatalogueMetadata(T catalogueMetadata) {
    if (this.saveMetadataToFile(catalogueMetadata)) {
      this.catalogueAndMetadata = new CatalogueAndMetadata<T>(catalogueMetadata,
          this.catalogueAndMetadata.getCatalogueContents());
    }
  }
}
