package com.vanatta.helene.supplies.database.manage;

import com.vanatta.helene.supplies.database.data.CountyDao;
import com.vanatta.helene.supplies.database.data.SiteType;
import com.vanatta.helene.supplies.database.export.update.SendSiteUpdate;
import com.vanatta.helene.supplies.database.manage.add.site.AddSiteDao;
import com.vanatta.helene.supplies.database.manage.add.site.AddSiteData;
import com.vanatta.helene.supplies.database.supplies.site.details.SiteDetailDao;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@Controller
@AllArgsConstructor
@Slf4j
public class ManageSiteController {

  static final String PATH_SELECT_SITE = "/manage/select-site";
  private final Jdbi jdbi;
  private final SendSiteUpdate sendSiteUpdate;

  @Builder
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class SiteSelection {
    Long id;
    String name;
  }

  /** User will be shown a page to select the site they want to manage. */
  @GetMapping(PATH_SELECT_SITE)
  ModelAndView showSelectSitePage() {
    return showSelectSitePage(jdbi);
  }

  public static ModelAndView showSelectSitePage(Jdbi jdbi) {
    Map<String, Object> pageParams = new HashMap<>();
    pageParams.put("sites", ManageSiteDao.fetchSiteList(jdbi));
    return new ModelAndView("manage/select-site", pageParams);
  }

  /**
   * After a site is selected, user selects which aspect they want to manage (eg: inventory, status)
   */
  @GetMapping("/manage/site-selected")
  ModelAndView showSiteSelectedPage(
      //      @CookieValue(value = "auth") String auth,
      @RequestParam String siteId) {

    String siteName = fetchSiteName(siteId);
    if (siteName == null) {
      return showSelectSitePage();
    }

    Map<String, String> pageParams = new HashMap<>();
    pageParams.put("siteName", siteName);
    pageParams.put("siteId", siteId);
    return new ModelAndView("manage/site-selected", pageParams);
  }

  /** Returns null if ID is not valid or DNE. */
  private String fetchSiteName(String siteId) {
    if (siteId == null || siteId.isBlank()) {
      return null;
    }

    try {
      long id = Long.parseLong(siteId);
      return ManageSiteDao.fetchSiteName(jdbi, id);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Fetches data for the manage site page */
  @GetMapping("/manage/contact")
  ModelAndView showSiteContactPage(String siteId) {
    Map<String, Object> pageParams = new HashMap<>();

    String siteName = fetchSiteName(siteId);
    if (siteName == null) {
      return showSelectSitePage();
    }

    SiteDetailDao.SiteDetailData data = SiteDetailDao.lookupSiteById(jdbi, Long.parseLong(siteId));
    if(data == null) {
      return new ModelAndView("redirect:" + PATH_SELECT_SITE);
    }

    pageParams.put("siteId", siteId);
    pageParams.put("siteName", data.getSiteName());
    pageParams.put("address", data.getAddress());
    pageParams.put("city", Optional.ofNullable(data.getCity()).orElse(""));
    pageParams.put("state", Optional.ofNullable(data.getState()).orElse(""));
    pageParams.put("website", Optional.ofNullable(data.getWebsite()).orElse(""));
    pageParams.put("facebook", Optional.ofNullable(data.getFacebook()).orElse(""));
    pageParams.put("siteContactName", Optional.ofNullable(data.getContactName()).orElse(""));
    pageParams.put("siteContactEmail", Optional.ofNullable(data.getContactEmail()).orElse(""));
    pageParams.put("siteContactNumber", Optional.ofNullable(data.getContactNumber()).orElse(""));

    // fetch all counties, mark the county of this site as "selected"
    var countyListing =
        CountyDao.fetchFullCountyList(jdbi).stream()
            .map(
                county ->
                    CountyListing.builder()
                        .name(county)
                        .selected(county.equals(data.getCounty()) ? "selected" : "")
                        .build())
            .sorted(Comparator.comparing(CountyListing::getName))
            .toList();
    pageParams.put("countyList", countyListing);
    return new ModelAndView("manage/contact", pageParams);
  }

  @Builder
  @Data
  public static class CountyListing {
    String name;

    /** Should either be blank, or "selected" */
    String selected;
  }

  /** Info update for a site, eg: site-rename, site contact info changed. */
  @PostMapping("/manage/update-site")
  @ResponseBody
  ResponseEntity<?> updateSiteData(@RequestBody Map<String, String> params) {
    log.info("Update site data request received: {}", params);

    String siteId = params.get("siteId");
    String field = params.get("field");
    String newValue = params.get("newValue");

    if (newValue != null) {
      newValue = newValue.trim();
    }

    if (fetchSiteName(siteId) == null) {
      log.warn("invalid site id: {}, request: {}", siteId, params);
      return ResponseEntity.badRequest().body("Invalid site id");
    }

    var siteField = ManageSiteDao.SiteField.lookupField(field);
    ManageSiteDao.updateSiteField(jdbi, Long.parseLong(siteId), siteField, newValue);
    log.info("Site updated: {}", params);
    sendSiteUpdate.sendFullUpdate(Long.parseLong(siteId));

    return ResponseEntity.ok().body("Updated");
  }

  /** Displays the 'manage-status' page. */
  @GetMapping("/manage/status")
  ModelAndView showManageStatusPage(String siteId) {
    String siteName = fetchSiteName(siteId);
    if (siteName == null) {
      return showSelectSitePage();
    }

    Map<String, String> pageParams = new HashMap<>();
    pageParams.put("siteName", siteName);
    pageParams.put("siteId", siteId);

    ManageSiteDao.SiteStatus siteStatus =
        ManageSiteDao.fetchSiteStatus(jdbi, Long.parseLong(siteId));
    pageParams.put("siteActive", siteStatus.isActive() ? "checked" : "");
    pageParams.put("siteNotActive", siteStatus.isActive() ? "" : "checked");

    pageParams.put("siteAcceptingDonations", siteStatus.isAcceptingDonations() ? "checked" : "");
    pageParams.put("siteNotAcceptingDonations", siteStatus.isAcceptingDonations() ? "" : "checked");

    pageParams.put(
        "distributionSiteChecked",
        siteStatus.getSiteTypeEnum() == ManageSiteDao.SiteType.DISTRIBUTION_SITE ? "checked" : "");
    pageParams.put(
        "supplyHubChecked",
        siteStatus.getSiteTypeEnum() == ManageSiteDao.SiteType.SUPPLY_HUB ? "checked" : "");

    return new ModelAndView("manage/status", pageParams);
  }

  /** REST endpoint to toggle the status of sites (active/accepting donations). */
  @PostMapping("/manage/update-status")
  @ResponseBody
  ResponseEntity<?> updateStatus(@RequestBody Map<String, String> params) {
    log.info("Update site status request received: {}", params);

    String siteId = params.get("siteId");
    String statusFlag = params.get("statusFlag");
    String newValue = params.get("newValue");

    String siteName = fetchSiteName(siteId);
    if (siteName == null) {
      return ResponseEntity.badRequest().body("Invalid site id: " + siteId);
    }
    if (statusFlag == null
        || !(statusFlag.equals("active")
            || statusFlag.equals("acceptingDonations")
            || statusFlag.equals("distSite"))) {
      return ResponseEntity.badRequest().body("Invalid status flag: " + statusFlag);
    }

    if (newValue == null
        || !(newValue.equalsIgnoreCase("true") || newValue.equalsIgnoreCase("false"))) {
      return ResponseEntity.badRequest().body("Invalid new value: " + newValue);
    }

    if (statusFlag.equalsIgnoreCase("active")) {
      log.info("Updating site: {}, active = {}", siteName, newValue);
      ManageSiteDao.updateSiteActiveFlag(
          jdbi, Long.parseLong(siteId), Boolean.parseBoolean(newValue));
    } else if (statusFlag.equalsIgnoreCase("acceptingDonations")) {
      log.info("Updating site: {}, accepting donations = {}", siteName, newValue);
      ManageSiteDao.updateSiteAcceptingDonationsFlag(
          jdbi, Long.parseLong(siteId), Boolean.parseBoolean(newValue));
    } else {
      var siteType =
          Boolean.parseBoolean(newValue)
              ? ManageSiteDao.SiteType.DISTRIBUTION_SITE
              : ManageSiteDao.SiteType.SUPPLY_HUB;
      log.info("Updating site: {}, site type: {}", siteName, siteType);
      ManageSiteDao.updateSiteType(jdbi, Long.parseLong(siteId), siteType);
    }
    sendSiteUpdate.sendFullUpdate(Long.parseLong(siteId));
    return ResponseEntity.ok().body("Updated");
  }

  /** Shows the form for adding a brand new site */
  @GetMapping("/manage/new-site/add-site")
  ModelAndView showAddNewSiteForm() {
    log.info("new site");
    Map<String, Object> model = new HashMap<>();
    model.put("countyList", CountyDao.fetchFullCountyList(jdbi));
    return new ModelAndView("manage/new-site/add-site", model);
  }

  /** REST endpoint to create a new site */
  @PostMapping("/manage/add-site")
  @ResponseBody
  ResponseEntity<?> postNewSite(@RequestBody Map<String, String> params) {
    log.info("Received add new site data: {}", params);
    var addSiteData =
        AddSiteData.builder()
            .contactNumber(params.get("contactNumber"))
            .website(params.get("website"))
            .siteType(SiteType.parseSiteType(params.get("siteType")))
            .siteName(params.get("siteName"))
            .streetAddress(params.get("streetAddress"))
            .city(params.get("city"))
            .county(params.get("county"))
            .state(params.get("state"))
            .build();
    if (addSiteData.isMissingRequiredData()) {
      log.warn(
          "Add new site data is missing required data. Add new site data received: {}",
          addSiteData);
      // front end should be enforcing required data, error messaging back to user here is
      // pretty minimal.
      return ResponseEntity.badRequest().body("Failed, missing required data.");
    }
    try {
      long newSiteId = AddSiteDao.addSite(jdbi, addSiteData);
      sendSiteUpdate.sendFullUpdate(newSiteId);
      return ResponseEntity.ok(
          "{\"result\": \"success\", \"editSiteInventoryUrl\": \"/manage/inventory?siteId="
              + newSiteId
              + "\"}");
    } catch (AddSiteDao.DuplicateSiteException e) {
      return ResponseEntity.badRequest()
          .body("{\"result\": \"fail\", \"error\": \"site name already exists\"}");
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest()
          .body(String.format("{\"result\": \"fail\", \"error\": \"%s\"}", e.getMessage()));
    }
  }
}
