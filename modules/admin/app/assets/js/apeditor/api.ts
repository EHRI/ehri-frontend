import axios from "axios";
import {
  ConfigType,
  Link,
  AccessPoint,
  FilterResult,
  FilterHit,
  AccessPointType,
  ItemAccessPoints,
  Ok,
  AccessPointTypeData,
  CreationData
} from "./types";

export default class AccessPointEditorApi {

  service: any;
  config: ConfigType;

  constructor(service: object, config: ConfigType) {
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

  search(type: AccessPointType, text: string, limit: number = 10): Promise<FilterHit[]> {
    let params = {
      limit: limit,
      q: text,
      st: this.config.typeFilters[type],
      f: this.config.holderIds.length ? ("holderId:(" + this.config.holderIds.join(" ") + ")") : ""
    };
    return AccessPointEditorApi.call<FilterResult>(this.service.filter(), {}, params)
        .then(r => r.items)
  }

  async createAccessPoints(id: string, descId: string, type: AccessPointType, data: CreationData[]): Promise<void> {
    for (let item of data) {
      let ap = await this.createAccessPoint(id, descId, {
        name: item.text,
        description: item.description,
        accessPointType: type,
        isA: "AccessPoint"
      });
      if (item.targetId) {
        await this.createLink(id, ap.id, {
          target: item.targetId,
          type: this.config.linkType,
          description: item.description
        });
      }
    }
  }

  createLink(id: string, apId: string, data: object): Promise<Link> {
    return AccessPointEditorApi.call<Link>(this.service.createLink(id, apId), data);
  }

  createAccessPoint(id: string, did: string, data: object): Promise<AccessPoint> {
    return AccessPointEditorApi.call<AccessPoint>(this.service.createAccessPoint(id, did), data);
  }

  itemUrl(targetType: string, id: string): string {
    return this.service.getItem(targetType, id).url;
  }

  deleteAccessPoint(id: string, did: string, apId: string): Promise<Ok> {
    return AccessPointEditorApi.call<Ok>(this.service.deleteAccessPoint(id, did, apId));
  }

  deleteLinkAndAccessPoint(id: string, did: string, apId: string, linkId: string): Promise<Ok> {
    return AccessPointEditorApi.call<Ok>(this.service.deleteLinkAndAccessPoint(id, did, apId, linkId));
  }

  getAccessPoints(id: string, did: string): Promise<AccessPointTypeData[]> {
    function filterData(itemData: ItemAccessPoints[]): AccessPointTypeData[] {
      return itemData
          // FIXME: this indexing...
          .filter(d => d.id == did)[0].data;
    }

    return AccessPointEditorApi.call<ItemAccessPoints[]>(this.service.getAccessPoints(id, did))
        // This endpoint returns links for all descriptions,
        // so filter the data for our current description
        .then(filterData);
  }
}
