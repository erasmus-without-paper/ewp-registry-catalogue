package eu.erasmuswithoutpaper.catalogueserver.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GitHubData {
  private static final Logger logger = LoggerFactory.getLogger(GitHubData.class);

  private final String gitHubUserName;
  private final String gitHubRepositoryName;
  private final String gitHubFilePath;
  private final String gitHubApiUrl;
  private final String gitHubAuthHeader;

  /**
   * Stores data used in communication with GitHub.
   */
  @Autowired
  public GitHubData(
      @Value("${app.git-hub-catalogue.user-name}") String gitHubUserName,
      @Value("${app.git-hub-catalogue.repo-name}") String gitHubRepositoryName,
      @Value("${app.git-hub-catalogue.file-path}") String gitHubFilePath,
      @Value("${app.git-hub-auth.user-name:#{null}}") String gitHubAuthUserName,
      @Value("${app.git-hub-auth.user-token:#{null}}") String gitHubAuthUserToken) {
    this.gitHubUserName = gitHubUserName;
    this.gitHubRepositoryName = gitHubRepositoryName;
    this.gitHubFilePath = gitHubFilePath;
    this.gitHubApiUrl = "api.github.com";
    this.gitHubAuthHeader = createUserAuthHeader(gitHubAuthUserName, gitHubAuthUserToken);
  }

  public String getGitHubUserName() {
    return gitHubUserName;
  }

  public String getGitHubRepositoryName() {
    return gitHubRepositoryName;
  }

  public String getGitHubFilePath() {
    return gitHubFilePath;
  }

  public String getGitHubApiUrl() {
    return gitHubApiUrl;
  }

  public String getGitHubAuthHeader() {
    return gitHubAuthHeader;
  }

  private String createUserAuthHeader(String gitHubAuthUserName, String gitHubAuthUserToken) {
    boolean userNameProvided = gitHubAuthUserName != null && !gitHubAuthUserName.isEmpty();
    boolean userTokenProvided = gitHubAuthUserToken != null && !gitHubAuthUserToken.isEmpty();

    if (userNameProvided && userTokenProvided) {
      logger.info("Using Authorization Token header to authorize GitHub API Calls, username: {}",
          gitHubAuthUserName);
      return createAuthorizationHeader(gitHubAuthUserToken);
    } else if (userNameProvided || userTokenProvided) {
      String message =
          "Invalid configuration. Please provide both GitHub User and Token for authorization, "
              + "or skip both of them for unauthorized usage.";
      logger.error(message);
      throw new RuntimeException(message);
    } else {
      logger.info(
          "GitHub username and token for authorization not provided. Continuing as anonymous user."
      );
      return null;
    }
  }

  private String createAuthorizationHeader(String token) {
    return "token " + token;
  }
}
