
import axios from "axios";
import {apiCall} from "./common";

import {
  Cleanup, CleanupSummary,
  ConvertConfig, Coreference,
  DataTransformation, DataTransformationInfo, FileInfo,
  FileToUpload,
  ImportConfig, ImportDataset, ImportDatasetInfo, ImportLog, ImportLogSummary,
  JobMonitor,
  OaiPmhConfig, RepositoryDatasets,
  ResourceSyncConfig, Snapshot, SnapshotInfo, ValidationResult
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

  cancel(jobId: string): Promise<{ok: boolean}> {
    return apiCall(this.service.LongRunningJobs.cancel(this.repoId, jobId));
  }

  listFiles(ds: string, stage: string, prefix: string, after?: string): Promise<FileList> {
    return apiCall(this.service.ImportFiles.listFiles(this.repoId, ds, stage, prefix, after));
  }

  getImportConfig(ds: string): Promise<ImportConfig | null> {
    return apiCall(this.service.ImportConfigs.get(this.repoId, ds));
  }

  saveImportConfig(ds: string, config: ImportConfig): Promise<ImportConfig> {
    return apiCall(this.service.ImportConfigs.save(this.repoId, ds), config);
  }

  deleteImportConfig(ds: string): Promise<void> {
    return apiCall(this.service.ImportConfigs.delete(this.repoId, ds));
  }

  ingestFiles(ds: string, paths: string[], opts: ImportConfig, commit: boolean): Promise<JobMonitor> {
    let data = {
      config: opts,
      commit: commit,
      files: paths,
    };
    return apiCall(this.service.ImportConfigs.ingestFiles(this.repoId, ds), data);
  }

  deleteFiles(ds: string, stage: string, paths: string[]): Promise<{deleted: number}> {
    return apiCall(this.service.ImportFiles.deleteFiles(this.repoId, ds, stage), paths);
  }

  validateFiles(ds: string, stage: string, tagToPath: object): Promise<ValidationResult[]> {
    return apiCall(this.service.ImportFiles.validateFiles(this.repoId, ds, stage), tagToPath);
  }

  info(ds: string, stage: string, key: string, versionId?: string): Promise<FileInfo> {
    return apiCall(this.service.ImportFiles.info(this.repoId, ds, stage, key, versionId));
  }

  fileUrls(ds: string, stage: string, paths: string[]): Promise<object> {
    return apiCall(this.service.ImportFiles.fileUrls(this.repoId, ds, stage), paths);
  }

  uploadHandle(ds: string, stage: string, fileSpec: FileToUpload): Promise<{presignedUrl: string}> {
    return apiCall(this.service.ImportFiles.uploadHandle(this.repoId, ds, stage), fileSpec);
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
    return apiCall(this.service.ResourceSyncConfigs.sync(this.repoId, ds), config);
  }

  getSyncConfig(ds: string): Promise<ResourceSyncConfig | null> {
    return apiCall(this.service.ResourceSyncConfigs.get(this.repoId, ds));
  }

  saveSyncConfig(ds: string, config: ResourceSyncConfig): Promise<ResourceSyncConfig> {
    return apiCall(this.service.ResourceSyncConfigs.save(this.repoId, ds), config);
  }

  deleteSyncConfig(ds: string): Promise<void> {
    return apiCall(this.service.ResourceSyncConfigs.delete(this.repoId, ds));
  }

  testSyncConfig(ds: string, config: ResourceSyncConfig): Promise<{ok: true}> {
    return apiCall(this.service.ResourceSyncConfigs.test(this.repoId, ds), config);
  }

  cleanSyncConfig(ds: string, config: ResourceSyncConfig): Promise<string[]> {
    return apiCall(this.service.ResourceSyncConfigs.clean(this.repoId, ds), config);
  }

  getOaiPmhConfig(ds: string): Promise<OaiPmhConfig | null> {
    return apiCall(this.service.OaiPmhConfigs.get(this.repoId, ds));
  }

  saveOaiPmhConfig(ds: string, config: OaiPmhConfig): Promise<OaiPmhConfig> {
    return apiCall(this.service.OaiPmhConfigs.save(this.repoId, ds), config);
  }

  deleteOaiPmhConfig(ds: string): Promise<void> {
    return apiCall(this.service.OaiPmhConfigs.delete(this.repoId, ds));
  }

  testOaiPmhConfig(ds: string, config: OaiPmhConfig): Promise<object> {
    return apiCall(this.service.OaiPmhConfigs.test(this.repoId, ds), config);
  }

  harvest(ds: string, config: OaiPmhConfig, fromLast: boolean): Promise<JobMonitor> {
    return apiCall(this.service.OaiPmhConfigs.harvest(this.repoId, ds, fromLast), config);
  }

  convert(ds: string, key: string|null, config: ConvertConfig): Promise<JobMonitor> {
    return apiCall(this.service.ConvertConfigs.convert(this.repoId, ds, key), config);
  }

  convertFileUrl(ds: string, stage: string, key: string) {
    return this.service.ConvertConfigs.convertFile(this.repoId, ds, stage, key).url;
  }

  getConvertConfig(ds: string): Promise<[string, object][]> {
    return apiCall(this.service.ConvertConfigs.get(this.repoId, ds));
  }

  saveConvertConfig(ds: string, dtIds: [string, object][]): Promise<{ok: true}> {
    return apiCall(this.service.ConvertConfigs.save(this.repoId, ds), dtIds);
  }

  listDataTransformations(): Promise<DataTransformation[]> {
    return apiCall(this.service.DataTransformations.list(this.repoId));
  }

  getDataTransformation(id: string): Promise<DataTransformation> {
    return apiCall(this.service.DataTransformations.get(this.repoId, id));
  }

  createDataTransformation(generic: boolean, data: DataTransformationInfo): Promise<DataTransformation> {
    return apiCall(this.service.DataTransformations.create(this.repoId, generic), data);
  }

  updateDataTransformation(id: string, generic: boolean, data: DataTransformationInfo): Promise<DataTransformation> {
    return apiCall(this.service.DataTransformations.update(this.repoId, id, generic), data);
  }

  deleteDataTransformation(id: string): Promise<void> {
    return apiCall(this.service.DataTransformations.delete(this.repoId, id));
  }

  listDatasets(): Promise<ImportDataset[]> {
    return apiCall(this.service.ImportDatasets.list(this.repoId));
  }

  listAllDatasets(): Promise<RepositoryDatasets[]> {
    return apiCall(this.service.ImportDatasets.listAll());
  }

  datasetStats(): Promise<Map<string, number>> {
    return apiCall(this.service.ImportDatasets.stats(this.repoId));
  }

  createDataset(info: ImportDatasetInfo): Promise<ImportDataset> {
    return apiCall(this.service.ImportDatasets.create(this.repoId), info);
  }

  updateDataset(ds: string, info: ImportDatasetInfo): Promise<ImportDataset> {
    return apiCall(this.service.ImportDatasets.update(this.repoId, ds), info);
  }

  importDatasets(ds: string, info: ImportDatasetInfo[]): Promise<{ok: true}> {
    return apiCall(this.service.ImportDatasets.batch(this.repoId), info);
  }

  deleteDataset(ds: string): Promise<void> {
    return apiCall(this.service.ImportDatasets.delete(this.repoId, ds));
  }

  datasetErrors(ds: string): Promise<void> {
    return apiCall(this.service.ImportDatasets.errors(this.repoId, ds));
  }

  listSnapshots(): Promise<SnapshotInfo[]> {
    return apiCall(this.service.ImportLogs.listSnapshots(this.repoId));
  }

  takeSnapshot(info: SnapshotInfo): Promise<Snapshot> {
    return apiCall(this.service.ImportLogs.takeSnapshot(this.repoId), info);
  }

  diffSnapshot(snId: number): Promise<[string, string][]> {
    return apiCall(this.service.ImportLogs.diffSnapshot(this.repoId, snId));
  }

  cleanup(snId: number): Promise<Cleanup> {
    return apiCall(this.service.ImportLogs.cleanup(this.repoId, snId));
  }

  doCleanup(snId: number, confirm: object): Promise<CleanupSummary> {
    return apiCall(this.service.ImportLogs.doCleanup(this.repoId, snId), confirm);
  }

  getCoreferenceTable(): Promise<Coreference[]> {
    return apiCall(this.service.CoreferenceTables.getTable(this.repoId));
  }

  saveCoreferenceTable(): Promise<{ok: true}> {
    return apiCall(this.service.CoreferenceTables.saveTable(this.repoId));
  }

  ingestCoreferenceTable(): Promise<ImportLog> {
    return apiCall(this.service.CoreferenceTables.ingestTable(this.repoId));
  }

  logs(dsId?: string): Promise<ImportLogSummary[]> {
    return apiCall(this.service.ImportLogs.list(this.repoId, dsId));
  }
}
