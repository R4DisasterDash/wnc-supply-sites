package com.vanatta.helene.supplies.database.supplies;

import com.vanatta.helene.supplies.database.data.SiteType;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jdbi.v3.core.Jdbi;

public class SuppliesDao {

  @NoArgsConstructor
  @Data
  public static class SuppliesQueryResult {
    Long siteId;
    boolean acceptingDonations;
    boolean givingDonations;
    String site;
    String siteType;
    String county;
    String item;
    String itemStatus;
    LocalDate inventoryLastUpdated;
    LocalDate lastDeliveryDate;
  }

  public static List<SuppliesQueryResult> getSupplyResults(Jdbi jdbi, SiteSupplyRequest request) {
    StringBuilder query =
        new StringBuilder(
            """
      select
        s.id siteId,
        s.accepting_donations acceptingDonations,
        s.distributing_supplies givingDonations,
        s.name site,
        st.name siteType,
        c.name county,
        i.name item,
        ist.name itemStatus,
        s.inventory_last_updated inventoryLastUpdated,
        max(d.target_delivery_date) lastDeliveryDate
      from site s
      join site_type st on st.id = s.site_type_id
      join county c on c.id = s.county_id
      left join site_item si on si.site_id = s.id
      left join item i on i.id = si.item_id
      left join item_status ist on ist.id = si.item_status_id
      left join delivery d on d.to_site_id = s.id
      where s.active = true
         and (
           d.delivery_status is null or d.delivery_status = 'Delivery Completed')
      """);

    if (!request.getSites().isEmpty()) {
      query.append("and s.name in (<sites>)\n");
    }
    if (!request.getCounties().isEmpty()) {
      query.append("and c.name in (<counties>)\n");
    }
    if (!request.getItems().isEmpty()) {
      query.append("and i.name in (<items>)\n");
    }

    // if item status length is 3, then we are asking for all item status
    // but, if we do that, we filter out sites with no item status.
    // If all item statuses are requested, then we treat it as if none are requested.
    if (!request.getItemStatus().isEmpty()
        && request.getItemStatus().size() < SiteSupplyRequest.ITEM_STATUS_COUNT) {
      query.append("and ist.name in (<item_status>)\n");
    }

    if (!request.getSiteType().isEmpty()
        && request.getSiteType().size() < SiteType.values().length) {
      query.append("and st.name in (<site_type>)\n");
    }

    if (request.getAcceptingDonations() != request.getNotAcceptingDonations()) {
      query
          .append("and s.accepting_donations = ")
          .append(request.getAcceptingDonations())
          .append("\n");
    }

    if (!request.getIsAuthenticatedUser()) {
      query.append("and s.publicly_visible = true").append("\n");
    }

    query.append(
        """
      group by
        s.id,
        s.accepting_donations,
        s.distributing_supplies,
        s.name,
        st.name,
        c.name,
        i.name,
        ist.name,
        s.inventory_last_updated,
        ist.sort_order
      order by c.name, s.name, ist.sort_order, i.name
      """);

    List<String> decodedSites =
        request.getSites().stream().map(s -> s.replace("&amp;", "&")).toList();

    return jdbi.withHandle(
        handle -> {
          var queryBuilder = handle.createQuery(query.toString());
          if (!request.getSites().isEmpty()) {
            queryBuilder.bindList("sites", decodedSites);
          }
          if (!request.getCounties().isEmpty()) {
            queryBuilder.bindList("counties", request.getCounties());
          }
          if (!request.getItems().isEmpty()) {
            queryBuilder.bindList("items", request.getItems());
          }
          if (!request.getItemStatus().isEmpty()
              && request.getItemStatus().size() < SiteSupplyRequest.ITEM_STATUS_COUNT) {
            queryBuilder.bindList("item_status", request.getItemStatus());
          }

          if (!request.getSiteType().isEmpty()
              && request.getSiteType().size() < SiteType.values().length) {
            queryBuilder.bindList("site_type", request.getSiteType());
          }

          return queryBuilder.mapToBean(SuppliesQueryResult.class).list();
        });
  }
}
