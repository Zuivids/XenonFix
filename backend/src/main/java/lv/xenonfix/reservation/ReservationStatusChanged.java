package lv.xenonfix.reservation;

import lv.xenonfix.product.ProductLanguage;

/** Published when the customer should hear about a change, and handled once it is saved. */
record ReservationStatusChanged(String reference, ReservationStatus status, String productName, int quantity,
		String customerName, String customerEmail, ProductLanguage language) {
}
