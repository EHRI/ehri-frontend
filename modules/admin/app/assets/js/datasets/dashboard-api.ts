import {RepositoryDatasets} from "./types";
import {apiCall} from "./common";


/**
 * A data access object encapsulating the management API endpoints.
 */
export class DashboardApi {
  service: any;

  constructor(service: object) {
    this.service = service;
  }

  managerUrl(repoId: string, ds?: string): string {
    return this.service.datasets.ImportDatasets.manager(repoId, ds).url;
  }

  listAllDatasets(): Promise<RepositoryDatasets[]> {
    return apiCall(this.service.datasets.ImportDatasets.listAll());
  }
}
