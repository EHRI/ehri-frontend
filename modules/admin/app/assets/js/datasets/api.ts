import axios from "axios";
import {apiCall} from "./common";

import {
  Cleanup,
  CleanupSummary,
  ConvertConfig,
  Coreference,
  DataTransformation,
  DataTransformationInfo,
  FileInfo,
  FileToUpload,
  HarvestConfig,
  ImportConfig,
  ImportDataset,
  ImportDatasetInfo,
  ImportLog,
  ImportLogSummary,
  JobMonitor,
  RepositoryDatasets,
  Snapshot,
  SnapshotInfo,
  ValidationResult
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

  harvest(ds: string, config: HarvestConfig, fromLast?: boolean): Promise<JobMonitor> {
    return apiCall(this.service.HarvestConfigs.harvest(this.repoId, ds, fromLast), config);
  }

  getHarvestConfig(ds: string): Promise<HarvestConfig | null> {
    return apiCall(this.service.HarvestConfigs.get(this.repoId, ds));
  }

  saveHarvestConfig(ds: string, config: HarvestConfig): Promise<HarvestConfig> {
    return apiCall(this.service.HarvestConfigs.save(this.repoId, ds), config);
  }

  deleteHarvestConfig(ds: string): Promise<void> {
    return apiCall(this.service.HarvestConfigs.delete(this.repoId, ds));
  }

  testHarvestConfig(ds: string, config: HarvestConfig): Promise<{ok: true}> {
    return apiCall(this.service.HarvestConfigs.test(this.repoId, ds), config);
  }

  cleanHarvestConfig(ds: string, config: HarvestConfig): Promise<string[]> {
    return apiCall(this.service.HarvestConfigs.clean(this.repoId, ds), config);
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

  listCleanups(snId: number): Promise<[number, string][]> {
    return apiCall(this.service.ImportLogs.listCleanups(this.repoId, snId));
  }

  getCleanup(snId: number, cleanupId: number): Promise<Cleanup> {
    return apiCall(this.service.ImportLogs.getCleanup(this.repoId, snId, cleanupId));
  }

  doCleanup(snId: number, confirm: object): Promise<CleanupSummary> {
    return apiCall(this.service.ImportLogs.doCleanup(this.repoId, snId), confirm);
  }

  getCoreferenceTable(): Promise<Coreference[]> {
    return apiCall(this.service.CoreferenceTables.getTable(this.repoId));
  }

  saveCoreferenceTable(coreferences: Coreference[]): Promise<{ok: true}> {
    return apiCall(this.service.CoreferenceTables.saveTable(this.repoId), coreferences);
  }

  ingestCoreferenceTable(): Promise<ImportLog> {
    return apiCall(this.service.CoreferenceTables.ingestTable(this.repoId));
  }

  logs(dsId?: string): Promise<ImportLogSummary[]> {
    return apiCall(this.service.ImportLogs.list(this.repoId, dsId));
  }
}
