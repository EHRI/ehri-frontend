import axios from "axios";
import {
  EntityType,
  EntityTypeMetadata, EntityTypeMetadataInfo,
  FieldMetadata,
  FieldMetadataInfo, FieldMetadataTemplates,
} from "./types";

export default class EntityTypeMetadataApi {

  service: any;
  config: object;

  constructor(service: object, config: object) {
    this.service = service;
    this.config = config;
  }

  private static call<T>(endpoint: {url: string, method: any}, data?: object, params?: object): Promise<T> {
    return axios.request<T>({
      url: endpoint.url,
      method: endpoint.method,
      data,
      params,
      headers: {
        "ajax-ignore-csrf": true,
        "Content-Type": "application/json",
        "Accept": "application/json; charset=utf-8",
        "X-Requested-With": "XMLHttpRequest",
      },
      withCredentials: true,
    }).then(r => r.data);
  }

  list(): Promise<Record<EntityType, EntityTypeMetadata>> {
    return EntityTypeMetadataApi.call<Record<EntityType, EntityTypeMetadata>>(this.service.list(), {});
  }

  get(entityType: EntityType): Promise<Record<EntityType, EntityTypeMetadata>> {
    return EntityTypeMetadataApi.call<Record<EntityType, EntityTypeMetadata>>(this.service.list(), {}, {entityType});
  }

  save(entityType: EntityType, data: EntityTypeMetadataInfo): Promise<EntityTypeMetadata> {
    return EntityTypeMetadataApi.call<EntityTypeMetadata>(this.service.save(entityType), data);
  }

  delete(entityType: EntityType): Promise<boolean> {
    return EntityTypeMetadataApi.call<boolean>(this.service.delete(entityType));
  }

  listFields(entityType?: EntityType): Promise<Record<EntityType, FieldMetadata[]>> {
    let params = entityType ? {entityType} : {};
    return EntityTypeMetadataApi.call<Record<EntityType, FieldMetadata[]>>(this.service.listFields(), {}, params)
  }

  getField(entityType: EntityType, id: string): Promise<FieldMetadata | null> {
    return EntityTypeMetadataApi.call<FieldMetadata>(this.service.getField(entityType, id));
  }

  saveField(entityType: EntityType, id: string, data: FieldMetadataInfo): Promise<FieldMetadata> {
    return EntityTypeMetadataApi.call<FieldMetadata>(this.service.saveField(entityType, id), data);
  }

  deleteField(entityType: EntityType, id: string): Promise<boolean> {
    return EntityTypeMetadataApi.call<boolean>(this.service.deleteField(entityType, id));
  }

  templates(): Promise<FieldMetadataTemplates> {
    return EntityTypeMetadataApi.call<object>(this.service.templates());
  }
}
