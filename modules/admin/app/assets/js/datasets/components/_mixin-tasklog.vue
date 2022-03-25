<script lang="ts">


import {DatasetManagerApi} from "../api";
import _startsWith from 'lodash-es/startsWith';
import _last from 'lodash-es/last';
import Vue from 'vue';


let initialLogState = function(): object {
  return {
    log: [],
    jobId: null,
    cancelling: false,
  };
};

export default {
  props: {
    api: DatasetManagerApi,
    config: Object,
  },
  data: function(): object {
    return initialLogState();
  },
  methods: {
    reset: function() {
      this.log.length = 0;
    },

    println: function(...msg: string[]) {
      console.debug(...msg);
      let line = msg.join(' ');
      // FIXME: hack workaround for ingest manager progress...
      let progPrefix = "Ingesting..."
      if (this.log && _startsWith(_last(this.log), progPrefix) && _startsWith(line, progPrefix)) {
        this.log.splice(this.log.length - 1, 1, line);
      } else {
        this.log.push(line);
      }

      // Cull the list back to 1000 items every
      // time we exceed a threshold
      // FIXME: this is crap
      if (this.log.length >= 2000) {
        // this.log.shift();
        this.log.splice(0, 1000);
      }
    },

    monitor: async function(url: string, jobId: string, onMsg: (s: string) => any = function () {}) {
      this.jobId = jobId;
      return await new Promise(((resolve) => {
        let worker = new Worker(this.config.previewLoader);
        worker.onmessage = msg => {
          if (msg.data.error) {
            this.println(msg.data.error);
          } else if (msg.data.msg) {
            this.println(msg.data.msg);
            onMsg(msg.data.msg);
          }
          if (msg.data.done || msg.data.error) {
            worker.terminate();
            this.jobId = null;
            resolve();
          }
        };
        worker.postMessage({
          type: 'websocket',
          url: url,
          DONE: DatasetManagerApi.DONE_MSG,
          ERR: DatasetManagerApi.ERR_MSG
        });
      })).catch(e => {throw e});
    },

    cancelJob: async function() {
      if (this.jobId) {
        this.cancelling = true;
        try {
          await this.api.cancel(this.jobId);
          this.jobId = null;
        } finally {
          this.cancelling = false;
        }
      }
    }
  },
}
</script>
