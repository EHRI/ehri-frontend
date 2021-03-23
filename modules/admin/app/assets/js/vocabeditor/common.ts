import {Concept} from "./types";
import {DateTime} from "luxon";


function conceptTitle (data: Concept, lang: string, fallback: string): string {
  for (let i in data.descriptions) {
    if (data.descriptions.hasOwnProperty(i)) {
      let desc = data.descriptions[i];
      if (desc.languageCode === lang) {
        return desc.name;
      }
    }
  }
  return data.descriptions[0] ? data.descriptions[0].name : fallback;
}

function formatTimestamp(s: string): string|null {
  let m = DateTime.fromISO(s);
  return m.isValid ? m.toRelative() : "";
}


function sortByTitle(lang: string) {
  return (a: Concept, b: Concept): number => {
    return conceptTitle(a, lang, a.id).localeCompare(conceptTitle(b, lang, b.id));
  }
}

export { conceptTitle, formatTimestamp, sortByTitle };
