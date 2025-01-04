package com.vanatta.helene.supplies.database.driver;

import com.vanatta.helene.supplies.database.auth.LoggedInAdvice;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.ModelAndView;

@Controller
@Slf4j
@AllArgsConstructor
public class DriverController {

  private final Jdbi jdbi;

  enum PageParams {
    location,
    licensePlates,
    availability,
    comments,
    active,
    ;
  }

  @GetMapping("/driver/portal")
  ModelAndView showDriverPortal(@ModelAttribute(LoggedInAdvice.USER_PHONE) String userPhone) {
    Driver driver =
        Optional.ofNullable(userPhone)
            .flatMap(phone -> DriverDao.lookupByPhone(jdbi, phone))
            .orElse(null);
    if (driver == null) {
      log.warn("DriverController could not find driver with phone number: {}", userPhone);
      return new ModelAndView("redirect:/");
    }

    Map<String, Object> params = new HashMap<>();
    params.put(PageParams.location.name(), Optional.ofNullable(driver.getLocation()).orElse(""));
    params.put(
        PageParams.licensePlates.name(), Optional.ofNullable(driver.getLicensePlates()).orElse(""));
    params.put(
        PageParams.availability.name(), Optional.ofNullable(driver.getAvailability()).orElse(""));
    params.put(PageParams.comments.name(), Optional.ofNullable(driver.getComments()).orElse(""));
    params.put(PageParams.active.name(), driver.isActive());
    return new ModelAndView("driver/portal", params);
  }

  @PostMapping("/driver/update")
  ResponseEntity<String> updateDriver(
      @ModelAttribute(LoggedInAdvice.USER_PHONE) String userPhone,
      @RequestBody Map<String, String> update) {
    log.info("Driver updated data, params received: {}", update);
    var updatedDriverData =
        DriverDao.lookupByPhone(jdbi, userPhone)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Invalid driver, not found in database. Unable to update: " + userPhone))
            .toBuilder()
            .location(update.get(PageParams.location.name()).trim())
            .licensePlates(update.get(PageParams.licensePlates.name()).trim())
            .availability(update.get(PageParams.availability.name()).trim())
            .comments(update.get(PageParams.comments.name()).trim())
            .build();

    DriverDao.upsert(jdbi, updatedDriverData);

    return ResponseEntity.ok().build();
  }
}
