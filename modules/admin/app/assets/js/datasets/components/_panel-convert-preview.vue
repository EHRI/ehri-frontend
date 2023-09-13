<script lang="ts">

import MixinPreviewPanel from './_mixin-preview-panel';

export default {
  mixins: [MixinPreviewPanel],
  props: {
    mappings: Array,
    trigger: String,
    api: Object,
  },
  methods: {
    validate: function () {
      // FIXME: not yet supported
    },
    load: function () {
      if (this.previewing === null) {
        return;
      }

      this.setLoading();
      this.worker.postMessage({
        type: 'convert-preview',
        url: this.api.convertFileUrl(this.datasetId, this.fileStage, this.previewing.key),
        max: this.config.maxPreviewSize,
        // FIXME: JSON needed here because Vue reactive proxies cannot be serialised to a worker
        // TODO: Find a nicer way to do this.
        mappings: JSON.parse(JSON.stringify(this.mappings)),
      });
    }
  },
  watch: {
    trigger: function () {
      this.load();
    },
    config: function (newConfig, oldConfig) {
      if (newConfig !== oldConfig && newConfig !== null) {
        console.log("Refresh convert preview...");
        this.load();
      }
    }
  }
}
</script>
