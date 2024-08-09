<script lang="ts">

import MixinTaskLog from "../datasets/components/_mixin-tasklog";
import PanelLogWindow from "../datasets/components/_panel-log-window";
import {EntityType} from "./types";
import FilterControl from "../datasets/components/_filter-control.vue";
import {AuditorApi, Service} from "./auditor-api";


export default {
  components: {FilterControl},
  props: {
    service: Object as Service,
    config: Object,
  },
  data: function () {
    return {
      types: [],
      running: false,
      idPrefix: '',
      mandatoryOnly: true,
      entityType: 'Repository' as EntityType,
      filter: {value: '', active: false},
      api: new AuditorApi(this.service),
      messages: [],
      error: null,
      doneMessage: null,
      infoMessage: null,
      jobId: null,
    }
  },
  methods: {
    runAudit: async function () {
      this.messages = [];
      this.doneMessage = null;
      this.infoMessage = null;
      this.error = null;

      this.running = true;
      try {
        let self = this;
        let {url, jobId} = await this.api.runAudit({
          idPrefix: this.idPrefix,
          entityType: this.entityType,
          mandatoryOnly: this.mandatoryOnly
        });
        console.log("Monitor: ", url, jobId);
        self.jobId = jobId;
        let websocket = new WebSocket(url);
        websocket.onopen = function () {
          console.debug("Websocket open")
        };
        websocket.onerror = function (e) {
          self.setError("Failed to run audit", e);
          self.running = false;
        };
        websocket.onmessage = function (e) {

          try {
            let msg = JSON.parse(e.data);
            let done = msg.startsWith("Done");
            let err = msg.startsWith("Error");
            let info = msg.startsWith("Info");
            if (done || err) {
              if (done) {
                self.doneMessage = msg;
              } else if (err) {
                self.error = msg;
              }
              self.running = false;
              websocket.close();
            } else if (info) {
              self.infoMessage = msg;
            } else {
              try {
                let payload = JSON.parse(msg);
                self.messages.push(...payload);
              } catch (exc) {
                console.error("Failed to parse message payload", msg, exc);
              }
            }
          } catch (exc) {
            console.error("Failed to parse websocket message", e.data, exc);
            return;
          }
        };
        websocket.onclose = function (e) {
          console.debug("Websocket close", e.reason, e.code)
          self.running = false;
        }

      } catch (exc) {
        this.setError("Failed to run audit", exc);
        this.running = false;
      } finally {
      }
    },
    cancelAudit: async function() {
      try {
        let res = await this.api.cancelAudit(this.jobId);
        console.log("Cancel audit", this.jobId, res);
      } catch (exc) {
        this.setError("Failed to cancel audit", exc);
      }
    },
    setError: function (err: string, exc?: Error) {
      this.error = err + (exc ? (": " + exc.message) : "");
    },
  },

  async created () {
    this.types = await this.api.types();
  }
}

</script>

<template>
    <div class="app-content-inner">
        <div v-if="error" id="app-error-notice" class="alert alert-danger alert-dismissable">
            <span class="close" v-on:click="error = null">&times;</span>
            {{ error }}
        </div>
        <h1>{{ $t(`dataModel.audit`)}}</h1>
        <div class="auditor-options">
            <fieldset class="options-form">
                <div class="form-group">
                    <label for="opt_entityType">{{ $t(`dataModel.audit.entityType`) }}</label>
                    <select v-model="entityType" class="form-control">
                        <option v-for="t in types" v-bind:value="t">{{ $t(`contentTypes.${t}`) }}</option>
                    </select>
                </div>
                <div class="form-group">
                    <label for="opt_filter">{{ $t(`dataModel.audit.idPrefix`) }}</label>
                    <input v-model="idPrefix" id="opt_filter" class="form-control" type="text" v-bind:placeholder="$t(`dataModel.audit.idPrefix.description`)"/>
                </div>
                <div class="form-group form-check">
                    <input v-model="mandatoryOnly" id="opt_mandatoryOnly" class="form-check-input" type="checkbox"/>
                    <label for="opt_mandatoryOnly" class="form-check-label">{{ $t(`dataModel.audit.mandatoryOnly`) }}</label>
                </div>
            </fieldset>
            <button v-if="running" class="btn btn-sm btn-default" v-on:click.prevent="cancelAudit">
                <i class="fa fa-fw fa-circle-o-notch fa-spin"></i> Cancel Audit
            </button>
            <button v-else class="btn btn-sm btn-default" v-on:click.prevent="runAudit">
                <i class="fa fa-fw fa-play"></i> {{ $t(`dataModel.audit.runAudit`) }}
            </button>
        </div>

        <div id="auditor-suggestions-container">
            <table v-if="messages.length > 0" class="auditor-suggestions table table-striped table-bordered">
                <tr>
                    <th>{{ $t(`dataModel.audit.fields.entity`) }}</th>
                    <th>{{ $t(`dataModel.audit.fields.mandatory`) }}</th>
                    <th>{{ $t(`dataModel.audit.fields.desirable`) }}</th>
                </tr>
                <tr v-for="item in messages">
                    <td><a v-bind:href="api.urlFor(entityType, item.id)" target="_blank">{{ item.id }}</a></td>
                    <td>
                        <details v-if="item.mandatory.length > 0">
                            <summary>Fields: {{ item.mandatory.length }}</summary>
                            <ul>
                                <li v-for="msg in item.mandatory">{{ msg }}</li>
                            </ul>
                        </details>
                    </td>
                    <td>
                        <details v-if="item.desirable.length > 0">
                            <summary>Fields: {{ item.desirable.length }}</summary>
                            <ul>
                                <li v-for="msg in item.desirable">{{ msg }}</li>
                            </ul>
                        </details>
                    </td>
                </tr>
            </table>
        </div>
        <div class="alert alert-success" v-if="doneMessage">
            {{ doneMessage }}
        </div>
        <div class="alert alert-info" v-else-if="infoMessage">
            {{ infoMessage }}
        </div>
    </div>
</template>
