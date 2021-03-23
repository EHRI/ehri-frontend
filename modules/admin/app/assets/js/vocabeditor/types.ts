

export interface ConceptDescription {
  id: string,
  name: string,
  languageCode: string,
  altLabel: string[],
  hiddenLabel: string[],
  scopeNote: string[],
}

export interface ConceptData {
  id: string,
  identifier: string,
  uri?: string,
  url?: string,
  longitude?: number,
  latitude?: number,
  seeAlso: string[],
  descriptions: ConceptDescription[],
}

export interface Concept {
  id: string,
  data: ConceptData,
  broaderTerms: Concept[],
}
