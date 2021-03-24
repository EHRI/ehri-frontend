import {Concept, ConceptRef, SearchRef} from "../types";

let testConcept1 = {
  id: "c1",
  identifier: "c1",
  descriptions: [{
    id: "cd1",
    languageCode: "eng",
    name: "Test",
  }],
  broaderTerms: []
};

let concepts = [testConcept1];

export default class VocabEditorApi {
  constructor(service: object, repoId: string) {
    console.log("VocabEditorApi mock constructor called");
  }

  get(id: string): Promise<Concept> {
    return Promise.resolve(concepts.filter(c => c.id === id)[0])
  }

  getLangData(): Promise<string[]> {
    return Promise.resolve(["eng", "fra"])
  }

  search(q: string, opts: {exclude?: string, page?: number}): Promise<SearchRef[]> {
    return Promise.resolve([{
      id: testConcept1.id,
      did: testConcept1.descriptions[0].id,
      name: testConcept1.descriptions[0].name
    }]);
  }

  getConcepts(q: string, lang: string): Promise<ConceptRef[]> {
    return Promise.resolve([[testConcept1.id, testConcept1.descriptions[0].name, 0]])
  }

  getChildren(id: string, lang: string): Promise<ConceptRef[]> {
    return Promise.resolve([]);
  }

  getNextIdentifier(): Promise<string> {
    return Promise.resolve("1001")
  }

  createItem(data: Concept): Promise<Concept> {
    return Promise.resolve(data)
  }

  updateItem(id: string, data: Concept): Promise<Concept> {
    return Promise.resolve(data);
  }

  deleteItem(id: string): Promise<void> {
    return Promise.resolve();
  }

  setBroader(id: string, broaderIds: string[]): Promise<Concept> {
    return Promise.resolve(concepts.filter(c => c.id === id)[0]);
  }
}
