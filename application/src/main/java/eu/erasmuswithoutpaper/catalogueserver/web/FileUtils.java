package eu.erasmuswithoutpaper.catalogueserver.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtils {
  private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);


  public static class ReadFileException extends Exception {

  }


  public static class WriteFileException extends Exception {

  }

  /**
   * Read a file from a directory.
   *
   * @param directory directory where the fire resides.
   * @param fileName  file name.
   * @return contents of the file or null if it cannot be read.
   */
  public static String readFile(String directory, String fileName) throws ReadFileException {
    Path path = Paths.get(directory, fileName);

    try {
      return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    } catch (NoSuchFileException e) {
      logger.info("Trying to read a file that doesn't exist: {}", path.toString());
      throw new ReadFileException();
    } catch (IOException e) {
      logger.error("Cannot read file.", e);
      throw new ReadFileException();
    }
  }

  /**
   * Write to a file in a directory.
   *
   * @param directory directory where the file resides.
   * @param fileName  file name.
   * @param content   data to write to the file.
   * @throws WriteFileException if file cannot be written.
   */
  public static void writeToFile(String directory, String fileName, byte[] content)
      throws WriteFileException {
    Path path = Paths.get(directory, fileName);
    try {
      Files.write(path, content);
    } catch (IOException e) {
      throw new WriteFileException();
    }
  }

}
