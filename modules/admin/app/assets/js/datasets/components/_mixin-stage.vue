<script lang="ts">


import _debounce from 'lodash/debounce';
import _forIn from 'lodash/forIn';
import _fromPairs from 'lodash/fromPairs';
import _isEmpty from 'lodash/isEmpty';

import {FileMeta} from '../types';
import {DatasetManagerApi} from "../api";


let initialStageState = function(): object {
  return {
    loaded: false,
    loadingMore: false,
    truncated: false,
    tab: 'preview',
    previewing: null,
    deleting: {},
    downloading: {},
    loadingInfo: {},
    selected: {},
    filter: {
      value: "",
      active: false
    },
    files: [],
    log: [],
    fileInfo: null,
  };
};

export default {
  props: {
    datasetId: String,
    active: Boolean,
    api: DatasetManagerApi,
  },
  data: function(): object {
    return initialStageState();
  },
  computed: {
    selectedKeys: function (): string[] {
      return Object.keys(this.selected);
    },
    selectedTags: function(): object {
      return _fromPairs(Object.values(this.selected).map((f: FileMeta) => [f.eTag, f.key]));
    }
  },
  methods: {
    reset: function() {
      Object.assign(this.$data, initialStageState());
    },
    clearFilter: function (): Promise<void> {
      this.filter.value = "";
      return this.refresh();
    },
    filterFiles: function (): Promise<void> {
      let func = () => {
        this.filter.active = true;
        return this.load().then(r => {
          this.filter.active = false;
          return r;
        });
      };
      return _debounce(func, 300)();
    },
    refresh: _debounce(function() {
      // this.load();
    }, 500),
    load: async function () {
      try {
        let {files, truncated} = await this.api.listFiles(this.datasetId, this.fileStage, this.filter.value);
        this.files = files;
        this.truncated = truncated;
      } catch (e) {
        this.showError(`Error retrieving file list for stage ${this.fileStage}: `, e);
      } finally {
        this.loaded = true;
      }
    },
    loadMore: async function() {
      this.loadingMore = true;
      let from = this.files.length > 0
        ? this.files[this.files.length - 1].key
        : null;
      try {
        let {files, truncated} = await this.api.listFiles(this.datasetId, this.fileStage, this.filter.value, from);
        this.files.push.apply(this.files, files);
        this.truncated = truncated;
      } catch (e) {
        this.showError("Error listing files", e);
      } finally {
        this.loadingMore = false;
      }
    },
    loadConfig: function() {
      // Overridden
    },
    downloadFiles: function(keys) {
      keys.forEach(key => this.$set(this.downloading, key, true));
      this.api.fileUrls(this.datasetId, this.fileStage, keys)
        .then(urls => {
          _forIn(urls, (url, fileName) => {
            window.open(url, '_blank');
            this.$delete(this.downloading, fileName);
          });
        })
        .catch(error => this.showError("Error fetching download URLs", error))
        .finally(() => this.downloading = {});
    },
    deleteFiles: function (keys) {
      if (_isEmpty(keys) || keys.includes(this.previewing)) {
        this.previewing = null;
      }
      let dkeys = _isEmpty(keys) ? this.files.map(f => f.key) : keys;
      dkeys.forEach(key => this.$set(this.deleting, key, true));
      this.api.deleteFiles(this.datasetId, this.fileStage, keys)
        .then(() => {
          dkeys.forEach(key => {
            this.$delete(this.deleting, key);
            this.$delete(this.selected, key);
          });
          this.refresh();
          this.$emit('updated')
        })
        .catch(error => this.showError("Error deleting files", error))
        .finally(() => this.deleting = {});
    },
    info: function(key) {
      this.$set(this.loadingInfo, key, true);
      return this.api.info(this.datasetId, this.fileStage, key)
        .then(r => this.fileInfo = r)
        .catch(error => this.showError("Error fetching file info", error))
        .finally(() => this.loadingInfo = {});
    },
    selectItem: function(file) {
      this.$set(this.selected, file.key, file);
    },
    deselectItem: function(file) {
      this.$delete(this.selected, file.key);
    },
    deselect: function() {
      this.previewing = null;
    },
    toggleFile: function(file) {
      if (this.selected[file.key]) {
        this.deselectItem(file);
      } else {
        this.selectItem(file);
      }
    },
    toggleAll: function() {
      if (this.selectedKeys.length === this.files.length) {
        this.selected = {};
      } else {
        this.selected = _fromPairs(this.files.map(f => [f.key, f]));
      }
    },
    showError: function(msg: string, exp?: object) {}, // Overridden by inheritors
  },
  watch: {
    active: function(newValue) {
      if (newValue) {
        this.load();
      }
    },
    datasetId: function() {
      this.reset();
      this.load();
      this.loadConfig();
    }
  },
  created: function() {
    this.load();
  }
}
</script>
