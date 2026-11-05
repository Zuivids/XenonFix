package lv.xenonfix.reservation;

public enum ReservationStatus {

	/** Made by a customer and waiting for an admin. The stock is already taken. */
	PENDING,

	CONFIRMED,

	CANCELLED,

	COMPLETED

}
