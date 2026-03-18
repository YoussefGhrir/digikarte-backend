package ghrir.digikarte.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminMetricsDto {
    private int totalUsers;

    private int activeSubscriptions;
    private int trialingSubscriptions;
    private int expiredSubscriptions;
    private int cancelledSubscriptions;

    private double subscriptionActiveRate;

    private double revenuePaid;
    private String revenueCurrency;

    private List<CountryMetricsDto> byCountry;
}

