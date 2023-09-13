<script lang="ts">


import {DatasetManagerApi} from "../api";
import _startsWith from 'lodash/startsWith';
import {Terminal} from "xterm";
import termopts from "../termopts";


let initialLogState = function (): object {
  return {
    log: new Terminal(termopts),
    jobId: null,
    cancelling: false,
    overwrite: false,
    logDeleteLinePrefix: "Ingesting..."
  };
};

export default {
  props: {
    api: DatasetManagerApi,
    config: Object,
  },
  data: function (): object {
    return initialLogState();
  },
  methods: {
    reset: function () {
      this.log.clear();
    },

    println: function (...msg: string[]) {
      console.debug(...msg);
      let line = msg.join(' ');
      if (this.overwrite) {
        this.log.write("\x1b[F");
      }
      this.log.writeln(line);
      this.overwrite = _startsWith(line, this.logDeleteLinePrefix);
    },

    monitor: async function (url: string, jobId: string, onMsg: (s: string) => any = function () {
    }, clear: boolean = false) {
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
      })).catch(e => {
        throw e
      });
    },

    cancelJob: async function () {
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
