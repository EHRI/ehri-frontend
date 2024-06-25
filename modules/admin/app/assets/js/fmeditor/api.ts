import axios from "axios";
import {
  FieldMetadata,
  FieldMetadataInfo, FieldMetadataTemplates,
} from "./types";

export default class FieldMetadataEditorApi {

  service: any;

  constructor(service: object) {
    this.service = service;
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

  list(entityType?: string): Promise<Record<string, FieldMetadata[]>> {
    let params = entityType ? {entityType} : {};
    return FieldMetadataEditorApi.call<Record<string, FieldMetadata[]>>(this.service.list(), {}, params)
  }

  get(entityType: string, id: string): Promise<FieldMetadata | null> {
    return FieldMetadataEditorApi.call<FieldMetadata>(this.service.get(entityType, id));
  }

  save(entityType: string, id: string, data: FieldMetadataInfo): Promise<FieldMetadata> {
    return FieldMetadataEditorApi.call<FieldMetadata>(this.service.save(entityType, id), data);
  }

  delete(entityType: string, id: string): Promise<boolean> {
    return FieldMetadataEditorApi.call<boolean>(this.service.delete(entityType, id));
  }

  templates(): Promise<FieldMetadataTemplates> {
    return FieldMetadataEditorApi.call<object>(this.service.templates());
  }
}
