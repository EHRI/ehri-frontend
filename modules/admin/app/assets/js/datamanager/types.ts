/**
 * These types mirror those consumed and produced by the server.
 */

export type ConfigType = {
  repoId: String;
  versions: boolean;
  input: string;
  output: string;
  config: string;
  previewLoader: string;
  defaultTab: string;
  monitorUrl: (jobId: string) => string;
  maxPreviewSize: number;
}

export interface OaiPmhConfig {
  url: string,
  format: string,
  set?: string,
}

export interface ResourceSyncConfig {
  url: string,
  filter?: string,
}

export interface ImportConfig {
  allowUpdates: boolean,
  tolerant: boolean,
  properties?: string,
  defaultLang?: string,
  logMessage: string,
  comments?: string,
}

export interface TransformationList {
  mappings: string[],
  force: boolean,
}

export interface ConvertSpec {
  mappings: [string, string][],
  force: boolean,
}

export type ConvertConfig = TransformationList | ConvertSpec;

export interface JobMonitor {
  jobId: string,
  url: string,
}

export type TransformationType = 'xquery' | 'xslt';

export interface DataTransformationInfo {
  id: string,
  name: string,
  bodyType: TransformationType,
  body: string,
  comments: string,
  hasParams: boolean,
}

export interface DataTransformation extends DataTransformationInfo{
  id: string,
  repoId?: string,
  created: string,
}

export type ImportDatasetSrc = 'oaipmh' | 'rs' | 'upload';

export interface ImportDatasetInfo {
  id: string,
  name: string,
  src: ImportDatasetSrc,
  fonds?: string,
  sync: boolean,
  notes?: string,
}

export interface ImportDataset extends ImportDatasetInfo {
  repoId: string,
  created: string,
}

export interface RepositoryDatasets {
  repoId: String,
  name: String,
  sets: ImportDataset[]
}


export interface FileMeta {
  classifier: string,
  key: string,
  lastModified: string,
  size: number,
  eTag?: string,
  contentType?: string,
  versionId?: string,
}

export interface FileList {
  files: FileMeta[],
  truncated: boolean,
}

export interface FileToUpload {
  name: string,
  type: string,
  size: number,
}

export interface FileInfo {
  meta: FileMeta,
  user: object,
  presignedUrl: string,
  versions: FileList,
}

export interface XmlValidationError {
  line: number,
  pos: number,
  error: string,
}

export interface ValidationResult {
  key: string,
  eTag?: string,
  errors: XmlValidationError[],
}
