/**
 * Entity type metadata data type definitions.
 */

export type EntityType = "Country" | "Repository" | "HistoricalAgent" | "DocumentaryUnit" | "CvocConcept" | "CvocVocabulary" | "AuthoritativeSet";
export type Usage = "mandatory" | "desirable";

export interface FieldMetadataInfo {
  name: string
  description?: string
  usage?: Usage
  category?: string
  defaultVal?: string
  seeAlso?: string[]
  created: string
  updated?: string
}

export interface FieldMetadata extends FieldMetadataInfo {
  entityType: EntityType
  id: string
}

export interface EntityTypeMetadataInfo {
  name: string
  description?: string
  created: string
  updated?: string
}

export interface EntityTypeMetadata extends EntityTypeMetadataInfo {
  entityType: EntityType
}


export type FieldMetadataTemplates = Record<EntityType, Record<string, string[]>>;
