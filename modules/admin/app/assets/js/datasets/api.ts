import axios from "axios";
import {apiCall} from "./common";

import {
  Cleanup,
  CleanupSummary,
  ConvertConfig, CopyResult,
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
  private static SOURCE_META = 'user';

  constructor(service: object, repoId: string) {
    this.service = service;
    this.repoId = repoId;
  }

  cancel(jobId: string): Promise<{ok: boolean}> {
    return apiCall(this.service.admin.Tasks.cancel(jobId));
  }

  listFiles(ds: string, stage: string, prefix: string, after?: string): Promise<FileList> {
    return apiCall(this.service.datasets.ImportFiles.listFiles(this.repoId, ds, stage, prefix, after));
  }

  getImportConfig(ds: string): Promise<ImportConfig | null> {
    return apiCall(this.service.datasets.ImportConfigs.get(this.repoId, ds));
  }

  saveImportConfig(ds: string, config: ImportConfig): Promise<ImportConfig> {
    return apiCall(this.service.datasets.ImportConfigs.save(this.repoId, ds), config);
  }

  deleteImportConfig(ds: string): Promise<void> {
    return apiCall(this.service.datasets.ImportConfigs.delete(this.repoId, ds));
  }

  ingestFiles(ds: string, paths: string[], opts: ImportConfig, commit: boolean): Promise<JobMonitor> {
    let data = {
      config: opts,
      commit: commit,
      files: paths,
    };
    return apiCall(this.service.datasets.ImportConfigs.ingestFiles(this.repoId, ds), data);
  }

  deleteFiles(ds: string, stage: string, paths: string[]): Promise<{deleted: number}> {
    return apiCall(this.service.datasets.ImportFiles.deleteFiles(this.repoId, ds, stage), paths);
  }

  validateFiles(ds: string, stage: string, tagToPath: object): Promise<ValidationResult[]> {
    return apiCall(this.service.datasets.ImportFiles.validateFiles(this.repoId, ds, stage), tagToPath);
  }

  info(ds: string, stage: string, key: string, versionId?: string): Promise<FileInfo> {
    return apiCall(this.service.datasets.ImportFiles.info(this.repoId, ds, stage, key, versionId));
  }

  fileUrls(ds: string, stage: string, paths: string[]): Promise<object> {
    return apiCall(this.service.datasets.ImportFiles.fileUrls(this.repoId, ds, stage), paths);
  }

  copyFile(ds: string, stage: string, key: string, toDs: string, toName?: string, versionId?: string): Promise<CopyResult> {
    return apiCall(this.service.datasets.ImportFiles.copyFile(this.repoId, ds, stage, key, toDs, toName, versionId));
  }

  uploadHandle(ds: string, stage: string, fileSpec: FileToUpload): Promise<{presignedUrl: string}> {
    // NB: Because we're adding the Amazon-specific headers in the `uploadFile` method,
    // we need to make sure the 'source' metadata field is included in the upload handle request.
    let meta = fileSpec.meta || {};
    meta['source'] = DatasetManagerApi.SOURCE_META;
    fileSpec.meta = meta;
    return apiCall(this.service.datasets.ImportFiles.uploadHandle(this.repoId, ds, stage), fileSpec);
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
        'x-amz-meta-source': DatasetManagerApi.SOURCE_META,
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
    return apiCall(this.service.datasets.HarvestConfigs.harvest(this.repoId, ds, fromLast), config);
  }

  getHarvestConfig(ds: string): Promise<HarvestConfig | null> {
    return apiCall(this.service.datasets.HarvestConfigs.get(this.repoId, ds));
  }

  saveHarvestConfig(ds: string, config: HarvestConfig): Promise<HarvestConfig> {
    return apiCall(this.service.datasets.HarvestConfigs.save(this.repoId, ds), config);
  }

  deleteHarvestConfig(ds: string): Promise<void> {
    return apiCall(this.service.datasets.HarvestConfigs.delete(this.repoId, ds));
  }

  testHarvestConfig(ds: string, config: HarvestConfig): Promise<{ok: true}> {
    return apiCall(this.service.datasets.HarvestConfigs.test(this.repoId, ds), config);
  }

  cleanHarvestConfig(ds: string, config: HarvestConfig): Promise<string[]> {
    return apiCall(this.service.datasets.HarvestConfigs.clean(this.repoId, ds), config);
  }

  convert(ds: string, key: string|null, config: ConvertConfig): Promise<JobMonitor> {
    return apiCall(this.service.datasets.ConvertConfigs.convert(this.repoId, ds, key), config);
  }

  convertFileUrl(ds: string, stage: string, key: string) {
    return this.service.datasets.ConvertConfigs.convertFile(this.repoId, ds, stage, key).url;
  }

  getConvertConfig(ds: string): Promise<[string, object][]> {
    return apiCall(this.service.datasets.ConvertConfigs.get(this.repoId, ds));
  }

  saveConvertConfig(ds: string, dtIds: [string, object][]): Promise<{ok: true}> {
    return apiCall(this.service.datasets.ConvertConfigs.save(this.repoId, ds), dtIds);
  }

  listDataTransformations(): Promise<DataTransformation[]> {
    return apiCall(this.service.datasets.DataTransformations.list(this.repoId));
  }

  getDataTransformation(id: string): Promise<DataTransformation> {
    return apiCall(this.service.datasets.DataTransformations.get(this.repoId, id));
  }

  createDataTransformation(generic: boolean, data: DataTransformationInfo): Promise<DataTransformation> {
    return apiCall(this.service.datasets.DataTransformations.create(this.repoId, generic), data);
  }

  updateDataTransformation(id: string, generic: boolean, data: DataTransformationInfo): Promise<DataTransformation> {
    return apiCall(this.service.datasets.DataTransformations.update(this.repoId, id, generic), data);
  }

  deleteDataTransformation(id: string): Promise<void> {
    return apiCall(this.service.datasets.DataTransformations.delete(this.repoId, id));
  }

  listDatasets(): Promise<ImportDataset[]> {
    return apiCall(this.service.datasets.ImportDatasets.list(this.repoId));
  }

  listAllDatasets(): Promise<RepositoryDatasets[]> {
    return apiCall(this.service.datasets.ImportDatasets.listAll());
  }

  datasetStats(): Promise<Map<string, number>> {
    return apiCall(this.service.datasets.ImportDatasets.stats(this.repoId));
  }

  createDataset(info: ImportDatasetInfo): Promise<ImportDataset> {
    return apiCall(this.service.datasets.ImportDatasets.create(this.repoId), info);
  }

  updateDataset(ds: string, info: ImportDatasetInfo): Promise<ImportDataset> {
    return apiCall(this.service.datasets.ImportDatasets.update(this.repoId, ds), info);
  }

  importDatasets(ds: string, info: ImportDatasetInfo[]): Promise<{ok: true}> {
    return apiCall(this.service.datasets.ImportDatasets.batch(this.repoId), info);
  }

  fileCount(ds: string): Promise<number> {
    return apiCall(this.service.datasets.ImportDatasets.fileCount(this.repoId, ds));
  }

  deleteDataset(ds: string): Promise<void> {
    return apiCall(this.service.datasets.ImportDatasets.delete(this.repoId, ds));
  }

  datasetErrors(ds: string): Promise<void> {
    return apiCall(this.service.datasets.ImportDatasets.errors(this.repoId, ds));
  }

  listSnapshots(): Promise<SnapshotInfo[]> {
    return apiCall(this.service.datasets.ImportLogs.listSnapshots(this.repoId));
  }

  takeSnapshot(info: SnapshotInfo): Promise<Snapshot> {
    return apiCall(this.service.datasets.ImportLogs.takeSnapshot(this.repoId), info);
  }

  diffSnapshot(snId: number): Promise<[string, string][]> {
    return apiCall(this.service.datasets.ImportLogs.diffSnapshot(this.repoId, snId));
  }

  cleanup(snId: number): Promise<Cleanup> {
    return apiCall(this.service.datasets.ImportLogs.cleanup(this.repoId, snId));
  }

  listCleanups(snId: number): Promise<[number, string][]> {
    return apiCall(this.service.datasets.ImportLogs.listCleanups(this.repoId, snId));
  }

  getCleanup(snId: number, cleanupId: number): Promise<Cleanup> {
    return apiCall(this.service.datasets.ImportLogs.getCleanup(this.repoId, snId, cleanupId));
  }

  doCleanup(snId: number, confirm: object): Promise<CleanupSummary> {
    return apiCall(this.service.datasets.ImportLogs.doCleanup(this.repoId, snId), confirm);
  }

  doCleanupAsync(snId: number, confirm: object): Promise<JobMonitor> {
    return apiCall(this.service.datasets.ImportLogs.doCleanupAsync(this.repoId, snId), confirm);
  }

  getCoreferences(): Promise<Coreference[]> {
    return apiCall(this.service.datasets.CoreferenceTables.getTable(this.repoId));
  }

  importCoreferences(coreferences: Coreference[]): Promise<{imported: number}> {
    return apiCall(this.service.datasets.CoreferenceTables.importTable(this.repoId), coreferences);
  }

  extractCoreferences(): Promise<{imported: number}> {
    return apiCall(this.service.datasets.CoreferenceTables.extractTable(this.repoId));
  }

  applyCoreferences(): Promise<ImportLog> {
    return apiCall(this.service.datasets.CoreferenceTables.applyTable(this.repoId));
  }

  deleteCoreferences(coreferences: Coreference[]): Promise<{deleted: number}> {
    return apiCall(this.service.datasets.CoreferenceTables.deleteTable(this.repoId), coreferences);
  }

  logs(dsId?: string): Promise<ImportLogSummary[]> {
    return apiCall(this.service.datasets.ImportLogs.list(this.repoId, dsId));
  }
}
