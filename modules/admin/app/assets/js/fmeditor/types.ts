/**
 * Field metadata data type definitions.
 */

export type Ok = {ok: true}

export type EntityType = "Country" | "RepositoryDescription" | "HistoricalAgentDescription" | "DocumentaryUnitDescription";
export type Usage = "mandatory" | "desirable";

export interface FieldMetadataInfo {
  name: string
  description?: string
  usage?: Usage
  category?: string
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

export type FieldMetadataCategory = Array<[string, string[]]>

export type FieldMetadataTemplates = Record<EntityType, FieldMetadataCategory[]>;
