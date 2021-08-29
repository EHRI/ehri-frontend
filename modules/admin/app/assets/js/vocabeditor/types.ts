
export interface ConfigType {
  vocabId: string,
  vocabName: string,
  title: string,
}

export interface ConceptDescription {
  id: string,
  name: string,
  languageCode: string,
  altLabel?: string[],
  hiddenLabel?: string[],
  scopeNote?: string[],
}

export interface Concept {
  id: string,
  identifier: string,
  uri?: string,
  url?: string,
  longitude?: number,
  latitude?: number,
  seeAlso?: string[],
  descriptions: ConceptDescription[],
  broaderTerms: Concept[],
}

export type ConceptRef = [string, string, number];

export type SearchRef = {
  id: string,
  name: string,
  did: string,
}

