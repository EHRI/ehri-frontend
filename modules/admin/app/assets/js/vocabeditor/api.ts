import axios from "axios";
import {Concept, ConceptRef, SearchRef} from "./types";

export default class VocabEditorApi {

  service: any;
  vocabId: string;

  constructor(service: object, vocabId: string) {
    this.service = service;
    this.vocabId = vocabId;
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

  get(id: string): Promise<Concept> {
    return VocabEditorApi.call(this.service.get(this.vocabId, id));
  }

  getLangData(): Promise<string[]> {
    return VocabEditorApi.call<{data: string[][]}>(this.service.langs(this.vocabId))
        .then(data => data.data.map((a: string[]) => a[0]));
  }

  search(q: string, opts: {exclude?: string, page?: number}): Promise<SearchRef[]> {
    return VocabEditorApi.call<SearchRef[]>(this.service.search(this.vocabId), {},  {
        q: q,
        ex: opts.exclude,
        page: opts.page || 1
      });
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
