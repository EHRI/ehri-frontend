/**
 * Field metadata data type definitions.
 */

export type Ok = {ok: true}

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
  entityType: string
  id: string
}
