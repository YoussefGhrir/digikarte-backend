package ghrir.digikarte.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvoiceDto {

    private String id;
    private double amount;
    private String currency;
    private String status;   // PAID, PENDING, FAILED
    private String createdAt;
    private String paidAt;
    private String invoiceUrl;
}

