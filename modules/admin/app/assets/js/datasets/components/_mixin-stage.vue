<script lang="ts">
import {defineComponent, PropType} from 'vue';


import _debounce from 'lodash/debounce';
import _forIn from 'lodash/forIn';
import _fromPairs from 'lodash/fromPairs';
import _isEmpty from 'lodash/isEmpty';

import {FileMeta, ImportDataset} from '../types';
import {DatasetManagerApi} from "../api";

let initialStageState = function () {
  return {
    loaded: false,
    loadingMore: false,
    truncated: false,
    tab: 'preview',
    previewing: null as FileMeta | null,
    deleting: {} as Record<string, boolean>,
    downloading: {} as Record<string, boolean>,
    loadingInfo: {} as Record<string, boolean>,
    selected: {} as Record<string, FileMeta>,
    filter: {
      value: "",
      active: false
    },
    files: [] as FileMeta[],
    fileInfo: null,
  };
};

export default defineComponent({
  props: {
    dataset: Object as PropType<ImportDataset>,
    active: Boolean,
    api: DatasetManagerApi,
  },
  data: function () {
    return initialStageState();
  },
  computed: {
    selectedKeys: function (): string[] {
      return Object.keys(this.selected);
    },
    selectedTags: function (): object {
      return _fromPairs(Object.values(this.selected).map((f: FileMeta) => [f.eTag, f.key]));
    }
  },
  methods: {
    reset: function () {
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
    refresh: _debounce(function () {
      this.load();
    }, 500),
    load: async function () {
      try {
        let {files, truncated} = await this.api.listFiles(this.dataset.id, this.fileStage, this.filter.value);
        this.files = files;
        this.truncated = truncated;
      } catch (e) {
        this.showError(`Error retrieving file list for stage ${this.fileStage}: `, e);
      } finally {
        this.loaded = true;
      }
    },
    loadMore: async function () {
      this.loadingMore = true;
      let from = this.files.length > 0
          ? this.files[this.files.length - 1].key
          : null;
      try {
        let {files, truncated} = await this.api.listFiles(this.dataset.id, this.fileStage, this.filter.value, from);
        this.files.push.apply(this.files, files);
        this.truncated = truncated;
      } catch (e) {
        this.showError("Error listing files", e);
      } finally {
        this.loadingMore = false;
      }
    },
    loadConfig: function () {
      // Overridden
    },
    downloadFiles: function (keys: string[]) {
      keys.forEach(key => this.downloading[key] = true);
      this.api.fileUrls(this.dataset.id, this.fileStage, keys)
          .then(urls => {
            _forIn(urls, (url, fileName) => {
              window.open(url, '_blank');
              delete this.downloading[fileName];
            });
          })
          .catch(error => this.showError("Error fetching download URLs", error))
          .finally(() => this.downloading = {});
    },
    deleteFiles: function (keys: string[]) {
      if (_isEmpty(keys) || (this.previewing && keys.includes(this.previewing.key))) {
        this.previewing = null;
      }
      let dkeys = _isEmpty(keys) ? this.files.map(f => f.key) : keys;
      dkeys.forEach(key => this.deleting[key] = true);
      this.api.deleteFiles(this.dataset.id, this.fileStage, keys)
          .then(() => {
            dkeys.forEach(key => {
              delete this.deleting[key];
              delete this.selected[key];
            });
            this.refresh();
            this.$emit('updated')
          })
          .catch(error => this.showError("Error deleting files", error))
          .finally(() => this.deleting = {});
    },
    info: function (key: string) {
      this.loadingInfo[key] = true;
      return this.api.info(this.dataset.id, this.fileStage, key)
          .then(r => this.fileInfo = r)
          .catch(error => this.showError("Error fetching file info", error))
          .finally(() => this.loadingInfo = {});
    },
    selectItem: function (file: FileMeta) {
      this.selected[file.key] = file;
    },
    deselectItem: function (file: FileMeta) {
      delete this.selected[file.key];
    },
    deselect: function () {
      this.previewing = null;
    },
    toggleFile: function (file: FileMeta) {
      if (this.selected[file.key]) {
        this.deselectItem(file);
      } else {
        this.selectItem(file);
      }
    },
    toggleAll: function (toggle: boolean) {
      if (!toggle) {
        this.selected = {};
      } else {
        this.selected = _fromPairs(this.files.map(f => [f.key, f]));
      }
    },
    showError: function (msg: string, exp?: object) {
    }, // Overridden by inheritors
  },
  watch: {
    active: function (newValue) {
      if (newValue) {
        this.load();
      }
    },
    dataset: function () {
      this.reset();
      this.load();
      this.loadConfig();
    }
  },
  created: function () {
    this.load();
  }
});
</script>
