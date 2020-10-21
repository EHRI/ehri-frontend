"use strict";

/**
 * A data access object encapsulating the management API endpoints.
 */
let DAO = class {
  constructor(service, repoId, datasetId) {
    this.service = service;
    this.repoId = repoId;
  }

  call(endpoint, data) {
    return axios.request({
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

  listFiles(ds, stage, prefix, after) {
    return this.call(this.service.listFiles(this.repoId, ds, stage, prefix, after));
  }

  ingestFiles(ds, stage, paths, opts) {
    let data = {
      files: paths,
      ...opts
    };
    return this.call(this.service.ingestFiles(this.repoId, ds, stage), data);
  }

  ingestAll(ds, stage, opts) {
    return this.call(this.service.ingestAll(this.repoId, ds, stage), {
      files: [],
      ...opts
    });
  }

  deleteFiles(ds, stage, paths) {
    return this.call(this.service.deleteFiles(this.repoId, ds, stage), paths);
  }

  deleteAll(ds, stage) {
    return this.call(this.service.deleteAll(this.repoId, ds, stage))
      .then(data => data.ok || false);
  }

  validateAll(ds, stage) {
    return this.call(this.service.validateAll(this.repoId, ds, stage));
  }

  validateFiles(ds, stage, tagToPath) {
    return this.call(this.service.validateFiles(this.repoId, ds, stage), tagToPath);
  }

  fileUrls(ds, stage, paths) {
    return this.call(this.service.fileUrls(this.repoId, ds, stage), paths);
  }

  uploadHandle(ds, stage, fileSpec) {
    return this.call(this.service.uploadHandle(this.repoId, ds, stage), fileSpec);
  }

  uploadFile(url, file, progressHandler) {
    const CancelToken = axios.CancelToken;
    const source = CancelToken.source();
    const progressFunc = progressHandler || function () { return true };

    return axios.put(url, file, {
      onUploadProgress: function (evt) {
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
      .catch(function (e) {
        if (axios.isCancel(e)) {
          console.log('Request canceled', file.name);
          return false;
        } else {
          throw e;
        }
      });
  }

  harvest(ds, config, fromLast) {
    return this.call(this.service.harvestOaiPmh(this.repoId, ds, fromLast), config);
  }

  cancelHarvest(jobId) {
    return this.call(this.service.cancelOaiPmhHarvest(this.repoId, jobId));
  }

  getConfig(ds) {
    return this.call(this.service.getOaiPmhConfig(this.repoId, ds));
  }

  saveConfig(ds, config) {
    return this.call(this.service.saveOaiPmhConfig(this.repoId, ds), config);
  }

  deleteConfig(ds) {
    return this.call(this.service.deleteOaiPmhConfig(this.repoId, ds));
  }

  testConfig(ds, config) {
    return this.call(this.service.testOaiPmhConfig(this.repoId, ds), config);
  }

  convert(ds, config) {
    return this.call(this.service.convert(this.repoId, ds), config);
  }

  convertFileUrl(ds, stage, key) {
    return this.service.convertFile(this.repoId, ds, stage, key).url;
  }

  cancelConvert(jobId) {
    return this.call(this.service.cancelConvert(this.repoId, jobId));
  }

  getConvertConfig(ds) {
    return this.call(this.service.getConvertConfig(this.repoId, ds));
  }

  saveConvertConfig(ds, dtIds) {
    return this.call(this.service.saveConvertConfig(this.repoId, ds), dtIds);
  }

  listDataTransformations() {
    return this.call(this.service.listDataTransformations(this.repoId));
  }

  getDataTransformation(id) {
    return this.call(this.service.getDataTransformation(this.repoId, id));
  }

  createDataTransformation(generic, data) {
    return this.call(this.service.createDataTransformation(this.repoId, generic), data);
  }

  updateDataTransformation(id, generic, data) {
    return this.call(this.service.updateDataTransformation(this.repoId, id, generic), data);
  }

  deleteDataTransformation(id) {
    return this.call(this.service.deleteDataTransformation(this.repoId, id));
  }

  listDatasets() {
    return this.call(this.service.listDatasets(this.repoId));
  }

  datasetStats() {
    return this.call(this.service.datasetStats(this.repoId));
  }

  createDataset(info) {
    return this.call(this.service.createDataset(this.repoId), info);
  }

  updateDataset(ds, info) {
    return this.call(this.service.updateDataset(this.repoId, ds), info);
  }

  deleteDataset(ds) {
    return this.call(this.service.deleteDataset(this.repoId, ds));
  }
};
