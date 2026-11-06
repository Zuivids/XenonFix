/** Part of a longer text page, such as the warranty terms. */
export interface DocumentSection {
  heading?: string;
  paragraphs?: string[];
  items?: string[];
  closing?: string;
}

/**
 * The legal texts. The labels and short texts are in the messages files; the texts of the home,
 * about, services, works and contact pages are edited in the admin panel (see pages/page-api.ts).
 * Every language must have the same structure and the same number of items; a test checks that.
 */
export interface Content {
  payment: DocumentSection[];
  delivery: DocumentSection[];
  warranty: DocumentSection[];
  privacy: DocumentSection[];
}
