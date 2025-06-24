package space.zeinab.demo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private String orderId;
    private String customerId;
    private Long orderAmount;
    private LocalDateTime orderDateTime;

    private String customerName;
    private String customerTier;
    private String customerEmail;

    public OrderEntity(String orderId, String customerId, long orderAmount, LocalDateTime orderDateTime) {
        this(orderId, customerId, orderAmount, orderDateTime, "", "", "");
    }
}