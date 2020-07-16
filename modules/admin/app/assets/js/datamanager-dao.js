"use strict";

/**
 * A data access object encapsulating the management API endpoints.
 */
let DAO = {
  call: function (endpoint, data) {
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
  },

  listFiles: function (stage, prefix, after) {
    return this.call(SERVICE.listFiles(CONFIG.repositoryId, stage, prefix, after));
  },

  ingestFiles: function (stage, paths, tolerant, commit, logMessage) {
    let data = {
      logMessage: logMessage,
      tolerant: tolerant,
      commit: commit,
      files: paths
    };
    return this.call(SERVICE.ingestFiles(CONFIG.repositoryId, stage), data);
  },

  ingestAll: function (stage, tolerant, commit, logMessage) {
    return this.call(SERVICE.ingestAll(CONFIG.repositoryId, stage), {
      logMessage: logMessage,
      tolerant: tolerant,
      commit: commit,
      files: []
    });
  },

  deleteFiles: function (stage, paths) {
    return this.call(SERVICE.deleteFiles(CONFIG.repositoryId, stage), paths);
  },

  deleteAll: function (stage) {
    return this.call(SERVICE.deleteAll(CONFIG.repositoryId, stage)).then(data => data.ok || false);
  },

  validateFiles: function (stage, paths) {
    return this.call(SERVICE.validateFiles(CONFIG.repositoryId, stage), paths);
  },

  fileUrls: function (stage, paths) {
    return this.call(SERVICE.fileUrls(CONFIG.repositoryId, stage), paths);
  },

  uploadHandle: function (stage, fileSpec) {
    return this.call(SERVICE.uploadHandle(CONFIG.repositoryId, stage), fileSpec);
  },

  uploadFile: function (url, file, progressHandler) {
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
  },

  harvest: function (config) {
    return this.call(SERVICE.harvestOaiPmh(CONFIG.repositoryId), config);
  },

  cancelHarvest: function(jobId) {
    return this.call(SERVICE.cancelOaiPmhHarvest(CONFIG.repositoryId, jobId));
  },

  getConfig: function () {
    return this.call(SERVICE.getOaiPmhConfig(CONFIG.repositoryId));
  },

  saveConfig: function (config) {
    return this.call(SERVICE.saveOaiPmhConfig(CONFIG.repositoryId), config);
  },

  deleteConfig: function () {
    return this.call(SERVICE.deleteOaiPmhConfig(CONFIG.repositoryId));
  },

  testConfig: function (config) {
    return this.call(SERVICE.testOaiPmhConfig(CONFIG.repositoryId), config);
  },

  convert: function(config) {
    return this.call(SERVICE.convert(CONFIG.repositoryId), config);
  },

  convertFileUrl: function(stage, key) {
    return SERVICE.convertFile(CONFIG.repositoryId, stage, key).url;
  },

  cancelConvert: function(jobId) {
    return this.call(SERVICE.cancelConvert(CONFIG.repositoryId, jobId));
  },

  getConvertConfig: function () {
    return this.call(SERVICE.getConvertConfig(CONFIG.repositoryId));
  },

  saveConvertConfig: function (dtIds) {
    return this.call(SERVICE.saveConvertConfig(CONFIG.repositoryId), dtIds);
  },

  listDataTransformations: function() {
    return this.call(SERVICE.listDataTransformations(CONFIG.repositoryId));
  },

  getDataTransformation: function(id) {
    return this.call(SERVICE.getDataTransformation(CONFIG.repositoryId, id));
  },

  createDataTransformation: function(generic, data) {
    return this.call(SERVICE.createDataTransformation(CONFIG.repositoryId, generic), data);
  },

  updateDataTransformation: function(id, generic, data) {
    return this.call(SERVICE.updateDataTransformation(CONFIG.repositoryId, id, generic), data);
  },

  deleteDataTransformation: function(id) {
    return this.call(SERVICE.deleteDataTransformation(CONFIG.repositoryId, id));
  },
};
