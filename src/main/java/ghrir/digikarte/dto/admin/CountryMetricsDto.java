package ghrir.digikarte.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CountryMetricsDto {
    private String country;
    private int usersCount;
    private long menusCount;

    private int activeSubscriptions;
    private int trialingSubscriptions;
    private int expiredSubscriptions;
    private int cancelledSubscriptions;

    /**
     * (activeSubscriptions + trialingSubscriptions) / usersCount
     */
    private double subscriptionRate;

    // utile pour afficher “revenu par pays” si on l'implémente ensuite
    private List<String> _future;
}

