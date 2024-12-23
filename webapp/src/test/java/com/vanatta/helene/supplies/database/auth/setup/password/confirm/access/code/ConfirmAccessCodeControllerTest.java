package com.vanatta.helene.supplies.database.auth.setup.password.confirm.access.code;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.auth.setup.password.Helper;
import com.vanatta.helene.supplies.database.auth.setup.password.send.access.code.SendAccessTokenDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfirmAccessCodeControllerTest {
  static final String jsonInput =
      """
      {"confirmCode":"557603","csrf":"1a24abcd-029f-49fe-8d76-af1c4b93bc28"}
      """;
  ConfirmAccessCodeController controller =
      new ConfirmAccessCodeController(TestConfiguration.jdbiTest, () -> "validation token");

  @BeforeEach
  void cleanDb() {
    Helper.setup();
  }

  @Test
  void canParseInput() {
    var request = ConfirmAccessCodeController.ConfirmAccessCodeRequest.parse(jsonInput);
    assertThat(request.getConfirmCode()).isEqualTo("557603");
    assertThat(request.getCsrf()).isEqualTo("1a24abcd-029f-49fe-8d76-af1c4b93bc28");
  }

  /**
   * Send a valid access token with CSRF. This should create a validation token in database and that
   * token will be returned to us. The test objct is set up to generate a hardcoded validation
   * token.
   */
  @Test
  void confirmAccessCode() {
    assertThat(
            Helper.accessTokenExists(
                "557603", "1a24abcd-029f-49fe-8d76-af1c4b93bc28", "validation token"))
        .isFalse();

    String number = "123___4444";
    Helper.withRegisteredNumber(number);
    SendAccessTokenDao.insertSmsPasscode(
        TestConfiguration.jdbiTest,
        SendAccessTokenDao.InsertAccessCodeParams.builder()
            .phoneNumber(number)
            .accessCode("557603")
            .csrfToken("1a24abcd-029f-49fe-8d76-af1c4b93bc28")
            .build());

    var response = controller.confirmAccessCode(jsonInput);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().getError()).isNull();
    assertThat(response.getBody().getValidationToken()).isEqualTo("validation token");
    assertThat(
            Helper.accessTokenExists(
                "557603", "1a24abcd-029f-49fe-8d76-af1c4b93bc28", "validation token"))
        .isTrue();
  }

  @Test
  void confirmAccessBadAccessToken() {
    String number = "123___4444";
    Helper.withRegisteredNumber(number);
    SendAccessTokenDao.insertSmsPasscode(
        TestConfiguration.jdbiTest,
        SendAccessTokenDao.InsertAccessCodeParams.builder()
            .phoneNumber(number)
            .accessCode("000000")
            .csrfToken("1a24abcd-029f-49fe-8d76-af1c4b93bc28")
            .build());

    var response = controller.confirmAccessCode(jsonInput);
    assertThat(response.getStatusCode().value()).isEqualTo(401);
    assertThat(response.getBody().getError()).isNotNull();
    assertThat(response.getBody().getValidationToken()).isNull();
  }

  @Test
  void confirmAccessBadCsrfToken() {
    String number = "123___4444";
    Helper.withRegisteredNumber(number);
    SendAccessTokenDao.insertSmsPasscode(
        TestConfiguration.jdbiTest,
        SendAccessTokenDao.InsertAccessCodeParams.builder()
            .phoneNumber(number)
            .accessCode("557603")
            .csrfToken("some other value")
            .build());

    var response = controller.confirmAccessCode(jsonInput);
    assertThat(response.getStatusCode().value()).isEqualTo(401);
    assertThat(response.getBody().getError()).isNotNull();
    assertThat(response.getBody().getValidationToken()).isNull();
  }
}
