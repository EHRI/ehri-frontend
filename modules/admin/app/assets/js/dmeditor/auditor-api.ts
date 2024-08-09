import {apiCall} from "../datasets/common";
import {EntityType, EntityTypeMetadata, FieldMetadataTemplates} from "./types";

export interface Service {
  templates(): {url: string, method: any};
  list(): {url: string, method: any};
  getItemType(et: EntityType, id: string): {url: string, method: any};
  runAudit(): {url: string, method: any};
  cancel(jobId: string): {url: string, method: any};
}

export interface JobInfo {
  jobId: string;
  cancelUrl: string;
  url: string;
}

/**
 * A data access object encapsulating the management API endpoints.
 */
export class AuditorApi {
  service: Service;

  constructor(service: Service) {
    this.service = service;
  }

  /**
   * Return a list of entity types for which we have audit metadata.
   */
  async types(): Promise<string[]> {
    let list = await apiCall<Record<EntityType, EntityTypeMetadata>>(this.service.list());
    let templates = await apiCall<FieldMetadataTemplates>(this.service.templates());
    return Object.keys(templates).filter(et => list[et] && Object.keys(list[et]).length > 0);
  }

  /**
   * Return the admin URL for a specific entity type.
   * @param et the entity type
   * @param id the entity ID
   */
  urlFor(et: EntityType, id: string): string {
    return this.service.getItemType(et, id).url;
  }

  /**
   * Run an audit job.
   * @param data the audit job data
   */
  runAudit(data: object): Promise<JobInfo> {
    return apiCall(this.service.runAudit(), data);
  }

  /**
   * Cancel an audit job.
   * @param jobId the job ID
   */
  cancelAudit(jobId: string): Promise<{ok: boolean}> {
    return apiCall(this.service.cancel(jobId));
  }
}
