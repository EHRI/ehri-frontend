import {
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
          seeAlso: ["seeAlso"],
          created: "2021-06-01"
        }
      ]
    };

  }

  list(): Promise<Record<string, EntityTypeMetadata>> {
    return Promise.resolve(this.etData);
  }

  get(entityType: string): Promise<EntityTypeMetadata | null> {
    return Promise.resolve(this.etData[entityType] || null);
  }

  save(entityType: string, data: EntityTypeMetadataInfo): Promise<EntityTypeMetadata> {
    return Promise.resolve({
      entityType,
      ...data
    } as EntityTypeMetadata);
  }

  delete(entityType: string): Promise<boolean> {
    return Promise.resolve(true);
  }

  listFields(entityType?: string): Promise<Record<string, FieldMetadata[]>> {
    return Promise.resolve(this.fData);
  }

  getField(entityType: string, id: string): Promise<FieldMetadata | null> {
    return Promise.resolve(this.fData[entityType].find(f => f.id === id) || null);
  }

  saveField(entityType: string, id: string, data: FieldMetadataInfo): Promise<FieldMetadata> {
    return Promise.resolve({
      entityType,
      id,
      ...data
    } as FieldMetadata);
  }

  deleteField(entityType: string, id: string): Promise<boolean> {
    // remove the item from fData:
    this.fData[entityType] = this.fData[entityType].filter(f => f.id !== id);
    return Promise.resolve(true);
  }

  templates(): Promise<FieldMetadataTemplates> {
    return Promise.resolve({
      Country: {
        "" : ["history"]
      }
    } as FieldMetadataTemplates);
  }
}
