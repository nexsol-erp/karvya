package com.karvya.store.application.admin;

import com.karvya.store.domain.model.CustomerOrder;
import com.karvya.store.domain.model.OrderItem;

import java.io.IOException;
import java.io.Writer;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes orders as CSV.
 *
 * <p>Two hazards are handled deliberately. Quotes inside a value are doubled
 * and the whole field wrapped, which is the actual CSV rule rather than the
 * comma-stripping people often reach for. And any value beginning with
 * {@code = + - @} is prefixed with an apostrophe, because a spreadsheet will
 * otherwise treat it as a formula - a customer named {@code =cmd|...} is a
 * genuine attack, not a curiosity.
 */
public class OrderCsvExporter {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private static final String[] HEADERS = {
            "Order number", "Placed at (UTC)", "Status", "Payment status", "Payment method",
            "Customer", "Phone", "Email", "Registered", "Address", "City", "State", "Postal code",
            "Items", "Currency", "Subtotal", "Delivery", "Total", "Products"
    };

    public void write(Writer writer, List<CustomerOrder> orders) throws IOException {
        writeRow(writer, (Object[]) HEADERS);

        for (CustomerOrder order : orders) {
            writeRow(writer,
                    order.getOrderNumber(),
                    TIMESTAMP.format(order.getPlacedAt()),
                    order.getStatus(),
                    order.getPaymentStatus(),
                    order.getPaymentMethodCode(),
                    order.getDeliveryName(),
                    order.getDeliveryPhone(),
                    order.getDeliveryEmail(),
                    order.getUser() != null ? "yes" : "guest",
                    joinAddress(order),
                    order.getCity(),
                    order.getState(),
                    order.getPostalCode(),
                    order.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
                    order.getCurrency(),
                    order.getSubtotal(),
                    order.getDeliveryCharge(),
                    order.getTotal(),
                    describeItems(order));
        }
        writer.flush();
    }

    private String joinAddress(CustomerOrder order) {
        return order.getAddressLine2() == null || order.getAddressLine2().isBlank()
                ? order.getAddressLine1()
                : order.getAddressLine1() + ", " + order.getAddressLine2();
    }

    private String describeItems(CustomerOrder order) {
        return order.getItems().stream()
                .map(item -> item.getProductSku() + " x" + item.getQuantity())
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }

    private void writeRow(Writer writer, Object... values) throws IOException {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(escape(values[i]));
        }
        // CRLF, which is what the CSV specification asks for and what
        // spreadsheet software on Windows expects
        row.append("\r\n");
        writer.write(row.toString());
    }

    private String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);

        // neutralise formula injection before quoting
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }

        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
