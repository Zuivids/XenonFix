package lv.xenonfix.product;

public enum ProductStatus {

	/** Being prepared; customers do not see it. */
	DRAFT,

	/** Customers see it. */
	PUBLISHED,

	/** No longer sold. Kept rather than deleted, because reservations will refer to it. */
	ARCHIVED

}
