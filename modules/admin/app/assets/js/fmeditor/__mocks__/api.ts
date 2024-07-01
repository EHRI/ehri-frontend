import {
  EntityTypeMetadata,
  EntityTypeMetadataInfo,
  FieldMetadata,
  FieldMetadataInfo,
  FieldMetadataTemplates,
} from "../types";

export default class EntityTypeMetadataApi {

  service: any;

  constructor(service: object) {
    this.service = service;
  }

  list(): Promise<Record<string, EntityTypeMetadata>> {
    return Promise.resolve({
      Country: {
        entityType: "Country",
        name: "Country",
        description: "Country description",
        created: "2021-06-01"
      }
    });
  }

  get(entityType: string): Promise<EntityTypeMetadata | null> {
    return this.list().then((data) => {
      return data[entityType];
    });
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
    return Promise.resolve({
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
    });
  }

  getField(entityType: string, id: string): Promise<FieldMetadata | null> {
    return this.listFields(entityType).then((data) => {
      return data[entityType].find((field) => field.id === id) || null;
    });
  }

  saveField(entityType: string, id: string, data: FieldMetadataInfo): Promise<FieldMetadata> {
    return Promise.resolve({
      entityType,
      id,
      ...data
    } as FieldMetadata);
  }

  deleteField(entityType: string, id: string): Promise<boolean> {
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
