package ghrir.digikarte.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubscriptionDto {

    private String plan;          // "MONTHLY" | "SEMIANNUAL" | "YEARLY"
    private String status;        // "TRIALING", "ACTIVE", "EXPIRED", "CANCELLED"
    private String trialEnd;
    private String currentPeriodStart;
    private String currentPeriodEnd;
    private String nextPaymentAt;
    private boolean autoRenew;
    private String currency;
    private double amount;
}

