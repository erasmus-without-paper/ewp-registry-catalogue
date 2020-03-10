package eu.erasmuswithoutpaper.catalogueserver.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ErrorController;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles how errors are displayed.
 */
@Controller
public class ApplicationErrorController implements ErrorController {
  private static final Logger logger = LoggerFactory.getLogger(ApplicationErrorController.class);
  private final ResourceLoader resLoader;

  @Autowired
  @SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC_ANON")
  public ApplicationErrorController(ResourceLoader resLoader) {
    this.resLoader = resLoader;
  }

  /**
   * Handle a server error.
   *
   * @param request request which has caused the error.
   * @return a HTTP 500 response (with EWP error XML).
   */
  @RequestMapping("/error")
  public ResponseEntity<String> error(HttpServletRequest request) {
    String errorMessage = (String) request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
    String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
    Throwable exception = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
    logger.error(
        "Exception while processing request to {}: {}", requestUri, errorMessage, exception
    );
    HttpHeaders headers = new HttpHeaders();
    String xml;
    try {
      xml = IOUtils
          .toString(this.resLoader.getResource("classpath:default-500.xml").getInputStream(),
              StandardCharsets.UTF_8);
      headers.setContentType(MediaType.APPLICATION_XML);
    } catch (IOException e) {
      xml = "Internal Server Error";
      headers.setContentType(MediaType.TEXT_PLAIN);
    }
    return new ResponseEntity<String>(xml, headers, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * Handle the "not found" error.
   *
   * @return a HTTP 404 response (with EWP error XML).
   */
  @RequestMapping("*")
  public ResponseEntity<String> get404() {
    String xml;
    try {
      xml = IOUtils
          .toString(this.resLoader.getResource("classpath:default-404.xml").getInputStream(),
              StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_XML);
    return new ResponseEntity<String>(xml, headers, HttpStatus.NOT_FOUND);
  }

  @Override
  public String getErrorPath() {
    return "/error";
  }
}
