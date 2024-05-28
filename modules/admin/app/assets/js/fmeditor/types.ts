/**
 * Field metadata data type definitions.
 */

export type Ok = {ok: true}

export type EntityType = "RepositoryDescription" | "HistoricalAgentDescription" | "DocumentaryUnitDescription";
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

export interface FieldMetadataTemplateInfo {
  [key: string]: string
}

export interface FieldMetadataEntityTypeTemplate {
  fields: FieldMetadataTemplateInfo,
  sections: FieldMetadataTemplateInfo
}

export type FieldMetadataTemplates = Record<EntityType, FieldMetadataEntityTypeTemplate>;
