import axios from "axios";
import {ConceptData} from "./types";


export default class VocabEditorApi {

  service: any;
  vocabId: string;

  constructor(service: object, vocabId: string) {
    this.service = service;
    this.vocabId = vocabId;
  }

  private ajaxHeaders: object = {
    "ajax-ignore-csrf": true,
    "Content-Type": "application/json",
    "Accept": "application/json; charset=utf-8",
    "X-Requested-With": "XMLHttpRequest",
  }

  private static call<T>(endpoint: {url: string, method: any}, data?: object): Promise<T> {
    return axios.request<T>({
      url: endpoint.url,
      method: endpoint.method,
      data: data,
      headers: {
        "ajax-ignore-csrf": true,
        "Content-Type": "application/json",
        "Accept": "application/json; charset=utf-8",
        "X-Requested-With": "XMLHttpRequest",
      },
      withCredentials: true,
    }).then(r => r.data);
  }

  /**
   *
   * @param obj an object of URL parameters
   * @returns {string}
   */
  private objToQueryString(obj: any): string {
    let str = [];
    for (let p in obj)
      if (obj.hasOwnProperty(p)) {
        if (Array.isArray(obj[p])) {
          obj[p].forEach((v: any) => {
            str.push(encodeURIComponent(p) + "=" + encodeURIComponent(v));
          });
        } else {
          str.push(encodeURIComponent(p) + "=" + encodeURIComponent(obj[p]));
        }
      }
    return str.join("&");
  }

  get(id: string) {
    return fetch(this.service.get(this.vocabId, id).url)
        .then(r => r.json());
  }

  getLangData() {
    return fetch(this.service.langs(this.vocabId).url)
        .then(r => r.json())
        .then(json => json.data.map((a: string[]) => a[0]));
  }

  search(q: string, opts: any) {
    return fetch(this.service.search().url + "?" + this.objToQueryString({
      q: q,
      f: "holderId:" + this.vocabId,
      ex: opts['excludeId'],
      page: opts['page'] || 1
    })).then(r => r.json()
        .then(data => data.items));
  }

  getConcepts(q: string, lang: string) {
    return fetch(this.service.list(this.vocabId).url + "?" + this.objToQueryString({
      q: q,
      lang: lang
    })).then(r => r.json())
        .then(data => data.data);
  }

  getChildren(id: string, lang: string) {
    return fetch(this.service.narrower(this.vocabId, id).url + "?" + this.objToQueryString({
      lang: lang
    })).then(r => r.json())
        .then(data => data.data);
  }

  getNextIdentifier(): Promise<string> {
    return fetch(this.service.nextIdentifier(this.vocabId).url).then(r => r.json());
  }

  createItem(data: object) {
    let self = this;
    return this.service.createItem(this.vocabId).ajax({
      data: JSON.stringify(data),
      headers: self.ajaxHeaders
    });
  }

  updateItem(id: string, data: object) {
    let self = this;
    return this.service.updateItem(this.vocabId, id).ajax({
      data: JSON.stringify(data),
      headers: self.ajaxHeaders
    });
  }

  deleteItem(id: string) {
    let self = this;
    return this.service.deleteItem(this.vocabId, id).ajax({
      data: JSON.stringify(null),
      headers: self.ajaxHeaders
    });
  }

  setBroader(id: string, broaderIds: string[]) {
    let self = this;
    return this.service.broader(this.vocabId, id).ajax({
      data: JSON.stringify(broaderIds),
      headers: self.ajaxHeaders
    });
  }

  title(data: ConceptData, lang: string, fallback: string): string {
    for (let i in data.descriptions) {
      if (data.descriptions.hasOwnProperty(i)) {
        let desc = data.descriptions[i];
        if (desc.languageCode === lang) {
          return desc.name;
        }
      }
    }
    return data.descriptions[0] ? data.descriptions[0].name : fallback;
  }

  sortByTitle(lang: string) {
    return (a: ConceptData, b: ConceptData) => {
      return this.title(a, lang, a.id)
          .localeCompare(this.title(b, lang, b.id));
    }
  }
}
