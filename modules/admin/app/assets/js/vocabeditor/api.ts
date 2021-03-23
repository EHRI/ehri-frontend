import axios from "axios";
import {Concept} from "./types";

type ConceptRef = [string, string, number];

type SearchRef = {
  id: string,
  name: string,
  did: string,
}

export default class VocabEditorApi {

  service: any;
  vocabId: string;

  constructor(service: object, vocabId: string) {
    this.service = service;
    this.vocabId = vocabId;
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

  get(id: string): Promise<Concept> {
    return VocabEditorApi.call(this.service.get(this.vocabId, id));
  }

  getLangData(): Promise<string[]> {
    return VocabEditorApi.call<{data: string[][]}>(this.service.langs(this.vocabId))
        .then(data => data.data.map((a: string[]) => a[0]));
  }

  search(q: string, opts: any): Promise<SearchRef[]> {
    return axios.get<{items: SearchRef[]}>(this.service.search().url, { params: {
      q: q,
        f: "holderId:" + this.vocabId,
        ex: opts['excludeId'],
        page: opts['page'] || 1
      }}).then(r => r.data).then(data => data.items)
  }

  getConcepts(q: string, lang: string): Promise<ConceptRef[]> {
    return VocabEditorApi.call<{data: ConceptRef[]}>(this.service.list(this.vocabId, q, lang))
        .then(data => data.data);
  }

  getChildren(id: string, lang: string): Promise<ConceptRef[]> {
    return VocabEditorApi.call<{data: ConceptRef[]}>(this.service.narrower(this.vocabId, id, lang))
        .then(data => data.data);
  }

  getNextIdentifier(): Promise<string> {
    return VocabEditorApi.call(this.service.nextIdentifier(this.vocabId));
  }

  createItem(data: Concept): Promise<Concept> {
    return VocabEditorApi.call(this.service.createItem(this.vocabId), data);
  }

  updateItem(id: string, data: Concept): Promise<Concept> {
    return VocabEditorApi.call(this.service.updateItem(this.vocabId, id), data);
  }

  deleteItem(id: string): Promise<void> {
    return VocabEditorApi.call(this.service.deleteItem(this.vocabId, id));
  }

  setBroader(id: string, broaderIds: string[]): Promise<Concept> {
    return VocabEditorApi.call(this.service.broader(this.vocabId, id), broaderIds);
  }
}
