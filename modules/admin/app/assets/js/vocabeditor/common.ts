import {ConceptData} from "./types";
import {DateTime} from "luxon";


function conceptTitle (data: ConceptData, lang: string, fallback: string): string {
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


export { conceptTitle, formatTimestamp };
