package lv.xenonfix.reservation;

import java.math.BigDecimal;

import lv.xenonfix.product.ProductLanguage;

/** Published when a reservation is made, and handled once it is safely saved. */
record ReservationCreated(String reference, String productName, String latvianProductName, String partNumber,
		BigDecimal unitPrice, int quantity, String customerName, String customerPhone, String customerEmail,
		String comment, ProductLanguage language) {

	BigDecimal total() {
		return this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
	}

}
