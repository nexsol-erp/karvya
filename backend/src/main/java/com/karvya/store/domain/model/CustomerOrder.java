package com.karvya.store.domain.model;

import com.karvya.store.domain.ConflictException;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A placed order.
 *
 * <p>The delivery details are copied onto the order rather than referenced
 * from a saved address, so editing or deleting that address later cannot
 * change where a past order went. The same reasoning governs
 * {@link OrderItem}: an order is a record of what was agreed, not a live view
 * of the catalogue.
 *
 * <p>Status transitions go through {@link #transitionTo} and
 * {@link #transitionPaymentTo}, which refuse illegal moves and append to the
 * history. Nothing sets the enum fields directly.
 */
@Entity
@Table(name = "customer_order")
public class CustomerOrder extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 32)
    private String orderNumber;

    /** Null for a guest order. The delivery snapshot stands on its own. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status = OrderStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 24)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payment_method_code", nullable = false, length = 48)
    private String paymentMethodCode;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "delivery_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    // ---- immutable delivery snapshot --------------------------------------

    @Column(name = "delivery_name", nullable = false, length = 160)
    private String deliveryName;

    @Column(name = "delivery_phone", nullable = false, length = 32)
    private String deliveryPhone;

    @Column(name = "delivery_email", length = 255)
    private String deliveryEmail;

    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 120)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 24)
    private String postalCode;

    @Column(name = "delivery_notes", columnDefinition = "text")
    private String deliveryNotes;

    @Column(name = "customer_comments", columnDefinition = "text")
    private String customerComments;

    // ---- internal ---------------------------------------------------------

    @Column(name = "internal_notes", columnDefinition = "text")
    private String internalNotes;

    /** Hash of the token that lets a guest view their own confirmation. */
    @Column(name = "access_token_hash", nullable = false, length = 128)
    private String accessTokenHash;

    /** Set the once stock is returned, which is what makes cancelling idempotent. */
    @Column(name = "stock_restored_at")
    private Instant stockRestoredAt;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    /**
     * Batched rather than fetch-joined. Hibernate refuses to fetch two list
     * collections in one query - MultipleBagFetchException - so items win the
     * join and the timeline arrives in a second, batched query.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC, id ASC")
    @BatchSize(size = 32)
    private List<OrderStatusHistory> history = new ArrayList<>();

    protected CustomerOrder() {
    }

    public static CustomerOrder open(String orderNumber, String accessTokenHash,
                                     String paymentMethodCode, String currency) {
        CustomerOrder order = new CustomerOrder();
        order.orderNumber = orderNumber;
        order.accessTokenHash = accessTokenHash;
        order.paymentMethodCode = paymentMethodCode;
        order.currency = currency;
        order.placedAt = Instant.now();
        return order;
    }

    // ---- construction -----------------------------------------------------

    public void setCustomer(AppUser user) {
        this.user = user;
    }

    public void setDelivery(String name, String phone, String email,
                            String line1, String line2, String city, String state,
                            String postalCode, String deliveryNotes, String customerComments) {
        this.deliveryName = name;
        this.deliveryPhone = phone;
        this.deliveryEmail = email;
        this.addressLine1 = line1;
        this.addressLine2 = line2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.deliveryNotes = deliveryNotes;
        this.customerComments = customerComments;
    }

    /** Adds a line, snapshotting the product as it stands right now. */
    public OrderItem addLine(Product product, int quantity) {
        OrderItem item = OrderItem.snapshotOf(this, product, quantity);
        items.add(item);
        return item;
    }

    public void setTotals(BigDecimal subtotal, BigDecimal deliveryCharge) {
        this.subtotal = subtotal;
        this.deliveryCharge = deliveryCharge;
        this.total = subtotal.add(deliveryCharge);
    }

    /** Records the opening status, so the timeline starts at placement. */
    public void recordPlacement(String actor) {
        history.add(OrderStatusHistory.record(
                this, OrderStatusHistory.FIELD_STATUS, null, status.name(), "Order placed", actor));
    }

    // ---- transitions ------------------------------------------------------

    public void transitionTo(OrderStatus next, String note, String actor) {
        if (next == status) {
            throw new ConflictException("status-unchanged",
                    "The order is already " + humanise(status.name()) + ".");
        }
        if (!status.canTransitionTo(next)) {
            throw new ConflictException("illegal-status-transition",
                    "An order that is " + humanise(status.name())
                            + " cannot become " + humanise(next.name()) + ".");
        }
        OrderStatus previous = status;
        status = next;
        history.add(OrderStatusHistory.record(
                this, OrderStatusHistory.FIELD_STATUS, previous.name(), next.name(), note, actor));
    }

    public void transitionPaymentTo(PaymentStatus next, String note, String actor) {
        if (next == paymentStatus) {
            throw new ConflictException("payment-status-unchanged",
                    "Payment is already " + humanise(paymentStatus.name()) + ".");
        }
        if (!paymentStatus.canTransitionTo(next)) {
            throw new ConflictException("illegal-payment-transition",
                    "Payment that is " + humanise(paymentStatus.name())
                            + " cannot become " + humanise(next.name()) + ".");
        }
        PaymentStatus previous = paymentStatus;
        paymentStatus = next;
        history.add(OrderStatusHistory.record(this, OrderStatusHistory.FIELD_PAYMENT_STATUS,
                previous.name(), next.name(), note, actor));
    }

    /**
     * Claims the right to return this order's stock, exactly once.
     *
     * <p>Returns true only on the first call; a second cancellation, a retried
     * request, or two administrators clicking at the same moment all get false
     * and must not touch stock. The caller holds a row lock, so the check and
     * the set cannot interleave.
     */
    public boolean claimStockRestoration() {
        if (stockRestoredAt != null) {
            return false;
        }
        stockRestoredAt = Instant.now();
        return true;
    }

    public void appendInternalNote(String note, String actor) {
        String stamped = Instant.now() + " - " + actor + ": " + note;
        internalNotes = (internalNotes == null || internalNotes.isBlank())
                ? stamped
                : internalNotes + "\n" + stamped;
    }

    private static String humanise(String enumName) {
        return enumName.toLowerCase().replace('_', ' ');
    }

    // ---- accessors --------------------------------------------------------

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public AppUser getUser() { return user; }
    public OrderStatus getStatus() { return status; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public String getPaymentMethodCode() { return paymentMethodCode; }
    public String getCurrency() { return currency; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDeliveryCharge() { return deliveryCharge; }
    public BigDecimal getTotal() { return total; }
    public String getDeliveryName() { return deliveryName; }
    public String getDeliveryPhone() { return deliveryPhone; }
    public String getDeliveryEmail() { return deliveryEmail; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getDeliveryNotes() { return deliveryNotes; }
    public String getCustomerComments() { return customerComments; }
    public String getInternalNotes() { return internalNotes; }
    public String getAccessTokenHash() { return accessTokenHash; }
    public Instant getStockRestoredAt() { return stockRestoredAt; }
    public Instant getPlacedAt() { return placedAt; }
    public long getVersion() { return version; }
    public List<OrderItem> getItems() { return items; }
    public List<OrderStatusHistory> getHistory() { return history; }
}
