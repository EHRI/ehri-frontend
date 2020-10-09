"use strict";

/**
 * A data access object encapsulating the management API endpoints.
 */
let DAO = class {
  constructor(service, repoId, datasetId) {
    this.service = service;
    this.repoId = repoId;
    this.datasetId = datasetId;
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

  listFiles(stage, prefix, after) {
    return this.call(this.service.listFiles(this.repoId, this.datasetId, stage, prefix, after));
  }

  ingestFiles(stage, paths, tolerant, commit, logMessage) {
    let data = {
      logMessage: logMessage,
      tolerant: tolerant,
      commit: commit,
      files: paths
    };
    return this.call(this.service.ingestFiles(this.repoId, this.datasetId, stage), data);
  }

  ingestAll(stage, tolerant, commit, logMessage) {
    return this.call(this.service.ingestAll(this.repoId, this.datasetId, stage), {
      logMessage: logMessage,
      tolerant: tolerant,
      commit: commit,
      files: []
    });
  }

  deleteFiles(stage, paths) {
    return this.call(this.service.deleteFiles(this.repoId, this.datasetId, stage), paths);
  }

  deleteAll(stage) {
    return this.call(this.service.deleteAll(this.repoId, this.datasetId, stage))
      .then(data => data.ok || false);
  }

  validateFiles(stage, paths) {
    return this.call(this.service.validateFiles(this.repoId, this.datasetId, stage), paths);
  }

  fileUrls(stage, paths) {
    return this.call(this.service.fileUrls(this.repoId, this.datasetId, stage), paths);
  }

  uploadHandle(stage, fileSpec) {
    return this.call(this.service.uploadHandle(this.repoId, this.datasetId, stage), fileSpec);
  }

  uploadFile(url, file, progressHandler) {
    const CancelToken = axios.CancelToken;
    const source = CancelToken.source();

    return axios.put(url, file, {
      onUploadProgress: function (evt) {
        if (!progressHandler(evt)) {
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

  harvest(config, fromLast) {
    return this.call(this.service.harvestOaiPmh(this.repoId, this.datasetId, fromLast), config);
  }

  cancelHarvest(jobId) {
    return this.call(this.service.cancelOaiPmhHarvest(this.repoId, jobId));
  }

  getConfig() {
    return this.call(this.service.getOaiPmhConfig(this.repoId, this.datasetId));
  }

  saveConfig(config) {
    return this.call(this.service.saveOaiPmhConfig(this.repoId, this.datasetId), config);
  }

  deleteConfig() {
    return this.call(this.service.deleteOaiPmhConfig(this.repoId, this.datasetId));
  }

  testConfig(config) {
    return this.call(this.service.testOaiPmhConfig(this.repoId, this.datasetId), config);
  }

  convert(config) {
    return this.call(this.service.convert(this.repoId, this.datasetId), config);
  }

  convertFileUrl(stage, key) {
    return this.service.convertFile(this.repoId, this.datasetId, stage, key).url;
  }

  cancelConvert(jobId) {
    return this.call(this.service.cancelConvert(this.repoId, jobId));
  }

  getConvertConfig() {
    return this.call(this.service.getConvertConfig(this.repoId, this.datasetId));
  }

  saveConvertConfig(dtIds) {
    return this.call(this.service.saveConvertConfig(this.repoId, this.datasetId), dtIds);
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
};
