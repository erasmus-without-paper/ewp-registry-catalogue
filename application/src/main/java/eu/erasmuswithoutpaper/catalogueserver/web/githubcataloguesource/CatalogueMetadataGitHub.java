package eu.erasmuswithoutpaper.catalogueserver.web.githubcataloguesource;

import java.io.StringWriter;
import java.time.Instant;

import javax.xml.bind.JAXB;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import eu.erasmuswithoutpaper.catalogueserver.web.CatalogueMetadata;

import io.github.threetenjaxb.core.InstantXmlAdapter;

@XmlRootElement(name = "catalogue-metadata")
@XmlAccessorType(XmlAccessType.NONE)
public class CatalogueMetadataGitHub implements CatalogueMetadata<CatalogueMetadataGitHub> {
  @XmlElement(name = "modification-date")
  @XmlJavaTypeAdapter(InstantXmlAdapter.class)
  private Instant modificationDate;

  @XmlElement(name = "last-fetch-date")
  @XmlJavaTypeAdapter(InstantXmlAdapter.class)
  private Instant lastFetchDate;

  @XmlElement(name = "hash")
  private String hash;

  @XmlElement(name = "github-etag")
  private String gitHubEtag;

  public CatalogueMetadataGitHub() {
  }

  /**
   * Stores basic information about local catalogue copy.
   * Objects of this type are serializable to XML and are stored with local catalogue copy.
   *
   * @param modificationDate modification date.
   * @param lastFetchDate    last fetch date.
   * @param hash             hash - used as etag.
   */
  public CatalogueMetadataGitHub(Instant modificationDate, Instant lastFetchDate, String hash,
      String gitHubEtag) {
    this.modificationDate = modificationDate;
    this.lastFetchDate = lastFetchDate;
    this.hash = hash;
    this.gitHubEtag = gitHubEtag;
  }

  /**
   * Dumps this CatalogueMetadata to XML string.
   *
   * @return XML string.
   * @throws CatalogueMetadataConversionException if this CatalogueMetadata cannot be dumped to XML.
   */
  public String toXmlString() throws CatalogueMetadataConversionException {
    try {
      StringWriter writer = new StringWriter();
      JAXB.marshal(this, writer);
      return writer.toString();
    } catch (RuntimeException e) {
      throw new CatalogueMetadataConversionException(e);
    }
  }

  public Instant getModificationDate() {
    return modificationDate;
  }

  public Instant getLastFetchDate() {
    return lastFetchDate;
  }

  public String getHash() {
    return hash;
  }

  @Override
  public CatalogueMetadataGitHub withUpdatedLastFetchDate(Instant newLastFetchDate) {
    return new CatalogueMetadataGitHub(modificationDate, newLastFetchDate, hash, gitHubEtag);
  }

  public String getGitHubEtag() {
    return gitHubEtag;
  }
}
