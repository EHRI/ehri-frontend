import {
  EntityType,
  EntityTypeMetadata,
  EntityTypeMetadataInfo,
  FieldMetadata,
  FieldMetadataInfo,
  FieldMetadataTemplates,
} from "../types";

export default class EntityTypeMetadataApi {

  service = {};
  etData = {};
  fData = {};

  constructor(service: object) {
    this.service = service;
    this.etData = {
      Country: {
        entityType: "Country",
        name: "Country",
        description: "Country description",
        created: "2021-06-01"
      }
    };

    this.fData = {
      Country: [
        {
          entityType: "Country",
          id: "history",
          name: "History",
          description: "Test",
          usage: "mandatory",
          defaultVal: null,
          seeAlso: ["seeAlso"],
          created: "2021-06-01"
        }
      ]
    };

  }

  list(): Promise<Record<EntityType, EntityTypeMetadata>> {
    return Promise.resolve(this.etData);
  }

  get(entityType: EntityType): Promise<EntityTypeMetadata | null> {
    return Promise.resolve(this.etData[entityType] || null);
  }

  save(entityType: EntityType, data: EntityTypeMetadataInfo): Promise<EntityTypeMetadata> {
    return Promise.resolve({
      entityType,
      ...data
    } as EntityTypeMetadata);
  }

  delete(entityType: EntityType): Promise<boolean> {
    return Promise.resolve(true);
  }

  listFields(entityType?: EntityType): Promise<Record<EntityType, FieldMetadata[]>> {
    return Promise.resolve(this.fData);
  }

  getField(entityType: EntityType, id: string): Promise<FieldMetadata | null> {
    return Promise.resolve(this.fData[entityType].find(f => f.id === id) || null);
  }

  saveField(entityType: EntityType, id: string, data: FieldMetadataInfo): Promise<FieldMetadata> {
    return Promise.resolve({
      entityType,
      id,
      ...data
    } as FieldMetadata);
  }

  deleteField(entityType: EntityType, id: string): Promise<boolean> {
    // remove the item from fData:
    this.fData[entityType] = this.fData[entityType].filter(f => f.id !== id);
    return Promise.resolve(true);
  }

  templates(): Promise<FieldMetadataTemplates> {
    return Promise.resolve({
      Country: {
        "_" : ["history"]
      }
    } as FieldMetadataTemplates);
  }
}
