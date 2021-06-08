
import axios from "axios";

import {
  ConvertConfig,
  DataTransformation, DataTransformationInfo, FileInfo,
  FileToUpload,
  ImportConfig, ImportDataset, ImportDatasetInfo,
  JobMonitor,
  OaiPmhConfig, RepositoryDatasets,
  ResourceSyncConfig, ValidationResult
} from "./types";


/**
 * A data access object encapsulating the management API endpoints.
 */
export class DatasetManagerApi {
  service: any;
  repoId: string;

  static DONE_MSG: string = "Done";
  static ERR_MSG: string = "Error";

  constructor(service: object, repoId: string) {
    this.service = service;
    this.repoId = repoId;
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

  listFiles(ds: string, stage: string, prefix: string, after?: string): Promise<FileList> {
    return DatasetManagerApi.call(this.service.ImportFiles.listFiles(this.repoId, ds, stage, prefix, after));
  }

  getImportConfig(ds: string): Promise<ImportConfig | null> {
    return DatasetManagerApi.call(this.service.ImportConfigs.get(this.repoId, ds));
  }

  saveImportConfig(ds: string, config: ImportConfig): Promise<ImportConfig> {
    return DatasetManagerApi.call(this.service.ImportConfigs.save(this.repoId, ds), config);
  }

  deleteImportConfig(ds: string): Promise<void> {
    return DatasetManagerApi.call(this.service.ImportConfigs.delete(this.repoId, ds));
  }

  ingestFiles(ds: string, paths: string[], opts: ImportConfig, commit: boolean): Promise<JobMonitor> {
    let data = {
      config: opts,
      commit: commit,
      files: paths,
    };
    return DatasetManagerApi.call(this.service.ImportConfigs.ingestFiles(this.repoId, ds), data);
  }

  deleteFiles(ds: string, stage: string, paths: string[]): Promise<{deleted: number}> {
    return DatasetManagerApi.call(this.service.ImportFiles.deleteFiles(this.repoId, ds, stage), paths);
  }

  validateFiles(ds: string, stage: string, tagToPath: object): Promise<ValidationResult[]> {
    return DatasetManagerApi.call(this.service.ImportFiles.validateFiles(this.repoId, ds, stage), tagToPath);
  }

  info(ds: string, stage: string, key: string, versionId?: string): Promise<FileInfo> {
    return DatasetManagerApi.call(this.service.ImportFiles.info(this.repoId, ds, stage, key, versionId));
  }

  fileUrls(ds: string, stage: string, paths: string[]): Promise<object> {
    return DatasetManagerApi.call(this.service.ImportFiles.fileUrls(this.repoId, ds, stage), paths);
  }

  uploadHandle(ds: string, stage: string, fileSpec: FileToUpload): Promise<{presignedUrl: string}> {
    return DatasetManagerApi.call(this.service.ImportFiles.uploadHandle(this.repoId, ds, stage), fileSpec);
  }

  uploadFile(url: string, file: File, progressHandler: Function) {
    const CancelToken = axios.CancelToken;
    const source = CancelToken.source();
    const progressFunc = progressHandler || function () { return true };

    return axios.put(url, file, {
      onUploadProgress: function (evt): void {
        if (!progressFunc(evt)) {
          source.cancel();
        }
      },
      headers: {
        'Content-type': file.type,
        'Cache-Control': 120,
        'x-amz-meta-source': 'user',
      },
      cancelToken: source.token,
    }).then(r => r.status === 200)
      .catch(function (e): boolean {
        if (axios.isCancel(e)) {
          console.log('Request canceled', file.name);
          return false;
        } else {
          throw e;
        }
      });
  }

  sync(ds: string, config: ResourceSyncConfig): Promise<JobMonitor> {
    return DatasetManagerApi.call(this.service.ResourceSyncConfigs.sync(this.repoId, ds), config);
  }

  cancelSync(jobId: string): Promise<{ok: boolean}> {
    return DatasetManagerApi.call(this.service.ResourceSyncConfigs.cancelSync(this.repoId, jobId));
  }

  getSyncConfig(ds: string): Promise<ResourceSyncConfig | null> {
    return DatasetManagerApi.call(this.service.ResourceSyncConfigs.get(this.repoId, ds));
  }

  saveSyncConfig(ds: string, config: ResourceSyncConfig): Promise<ResourceSyncConfig> {
    return DatasetManagerApi.call(this.service.ResourceSyncConfigs.save(this.repoId, ds), config);
  }

  deleteSyncConfig(ds: string): Promise<void> {
    return DatasetManagerApi.call(this.service.ResourceSyncConfigs.delete(this.repoId, ds));
  }

  testSyncConfig(ds: string, config: ResourceSyncConfig): Promise<{ok: true}> {
    return DatasetManagerApi.call(this.service.ResourceSyncConfigs.test(this.repoId, ds), config);
  }

  getOaiPmhConfig(ds: string): Promise<OaiPmhConfig | null> {
    return DatasetManagerApi.call(this.service.OaiPmhConfigs.get(this.repoId, ds));
  }

  saveOaiPmhConfig(ds: string, config: OaiPmhConfig): Promise<OaiPmhConfig> {
    return DatasetManagerApi.call(this.service.OaiPmhConfigs.save(this.repoId, ds), config);
  }

  deleteOaiPmhConfig(ds: string): Promise<void> {
    return DatasetManagerApi.call(this.service.OaiPmhConfigs.delete(this.repoId, ds));
  }

  testOaiPmhConfig(ds: string, config: OaiPmhConfig): Promise<object> {
    return DatasetManagerApi.call(this.service.OaiPmhConfigs.test(this.repoId, ds), config);
  }

  harvest(ds: string, config: OaiPmhConfig, fromLast: boolean): Promise<JobMonitor> {
    return DatasetManagerApi.call(this.service.OaiPmhConfigs.harvest(this.repoId, ds, fromLast), config);
  }

  cancelHarvest(jobId: string): Promise<{ok: boolean}> {
    return DatasetManagerApi.call(this.service.OaiPmhConfigs.cancelHarvest(this.repoId, jobId));
  }

  convert(ds: string, key: string, config: ConvertConfig): Promise<JobMonitor> {
    return DatasetManagerApi.call(this.service.DataTransformations.convert(this.repoId, ds, key), config);
  }

  convertFileUrl(ds: string, stage: string, key: string) {
    return this.service.DataTransformations.convertFile(this.repoId, ds, stage, key).url;
  }

  cancelConvert(jobId: string): Promise<{ok: boolean}> {
    return DatasetManagerApi.call(this.service.DataTransformations.cancelConvert(this.repoId, jobId));
  }

  getConvertConfig(ds: string): Promise<[DataTransformation, object][]> {
    return DatasetManagerApi.call(this.service.DataTransformations.getConfig(this.repoId, ds));
  }

  saveConvertConfig(ds: string, dtIds: [string, object][]): Promise<{ok: true}> {
    return DatasetManagerApi.call(this.service.DataTransformations.saveConfig(this.repoId, ds), dtIds);
  }

  listDataTransformations(): Promise<DataTransformation[]> {
    return DatasetManagerApi.call(this.service.DataTransformations.list(this.repoId));
  }

  getDataTransformation(id: string): Promise<DataTransformation> {
    return DatasetManagerApi.call(this.service.DataTransformations.get(this.repoId, id));
  }

  createDataTransformation(generic: boolean, data: DataTransformationInfo): Promise<DataTransformation> {
    return DatasetManagerApi.call(this.service.DataTransformations.create(this.repoId, generic), data);
  }

  updateDataTransformation(id: string, generic: boolean, data: DataTransformationInfo): Promise<DataTransformation> {
    return DatasetManagerApi.call(this.service.DataTransformations.update(this.repoId, id, generic), data);
  }

  deleteDataTransformation(id: string): Promise<void> {
    return DatasetManagerApi.call(this.service.DataTransformations.delete(this.repoId, id));
  }

  listDatasets(): Promise<ImportDataset[]> {
    return DatasetManagerApi.call(this.service.ImportDatasets.list(this.repoId));
  }

  listAllDatasets(): Promise<RepositoryDatasets[]> {
    return DatasetManagerApi.call(this.service.ImportDatasets.listAll());
  }

  datasetStats(): Promise<Map<string, number>> {
    return DatasetManagerApi.call(this.service.ImportDatasets.stats(this.repoId));
  }

  createDataset(info: ImportDatasetInfo): Promise<ImportDataset> {
    return DatasetManagerApi.call(this.service.ImportDatasets.create(this.repoId), info);
  }

  updateDataset(ds: string, info: ImportDatasetInfo): Promise<ImportDataset> {
    return DatasetManagerApi.call(this.service.ImportDatasets.update(this.repoId, ds), info);
  }

  deleteDataset(ds: string): Promise<void> {
    return DatasetManagerApi.call(this.service.ImportDatasets.delete(this.repoId, ds));
  }
}
