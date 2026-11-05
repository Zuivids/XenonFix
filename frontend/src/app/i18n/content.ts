/** A titled block of text. */
export interface TextCard {
  title: string;
  text: string;
}

/** Part of a longer text page, such as the warranty terms. */
export interface DocumentSection {
  heading?: string;
  paragraphs?: string[];
  items?: string[];
  closing?: string;
}

/**
 * Long and structured texts. The labels and short texts are in the messages files. Every
 * language must have the same structure and the same number of items; a test checks that.
 */
export interface Content {
  homeServices: TextCard[];
  homeReasons: string[];
  aboutIntro: string[];
  aboutGoal: string[];
  aboutManager: string[];
  services: TextCard[];
  payment: DocumentSection[];
  delivery: DocumentSection[];
  warranty: DocumentSection[];
  privacy: DocumentSection[];
}
