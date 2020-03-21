"use strict";


/**
 * A data access object containing functions to vocabulary concepts.
 */
let DAO = {
  ajaxHeaders: {
    "ajax-ignore-csrf": true,
    "Content-Type": "application/json",
    "Accept": "application/json; charset=utf-8"
  },

  /**
   *
   * @param obj an object of URL parameters
   * @returns {string}
   */
  objToQueryString: function (obj) {
    let str = [];
    for (var p in obj)
      if (obj.hasOwnProperty(p)) {
        if (Array.isArray(obj[p])) {
          obj[p].forEach(v => {
            str.push(encodeURIComponent(p) + "=" + encodeURIComponent(v));
          });
        } else {
          str.push(encodeURIComponent(p) + "=" + encodeURIComponent(obj[p]));
        }
      }
    return str.join("&");
  },

  listFiles: function (prefix) {
    return fetch(SERVICE.listFiles(CONFIG.repositoryId, prefix).url)
      .then(r => r.json());
  },

  deleteFiles: function (paths) {
    return SERVICE.deleteFiles(CONFIG.repositoryId).ajax({
      data: JSON.stringify(paths),
      headers: this.ajaxHeaders
    });
  },

  uploadHandle: function(fileSpec) {
    return SERVICE.uploadHandle(CONFIG.repositoryId).ajax({
      data: JSON.stringify(fileSpec),
      headers: this.ajaxHeaders
    });
  }
};

var app = new Vue({
  el: '#data-manager',
  data: function () {
    return {
      loaded: false,
      files: [],
      deleting: [],
      dropping: false,
      uploading: [],
      cancelled: [],
    }
  },
  watch: {
  },
  methods: {
    // EJohn's pretty date:
    // Takes an ISO time and returns a string representing how
    // long ago the date represents.
    // https://johnresig.com/blog/javascript-pretty-date/
    prettyDate: function (time) {
      var date = new Date((time || "").replace(/-/g, "/").replace(/[TZ]/g, " ")),
        diff = (((new Date()).getTime() - date.getTime()) / 1000),
        day_diff = Math.floor(diff / 86400);

      if (isNaN(day_diff) || day_diff < 0 || day_diff >= 31)
        return;

      return day_diff === 0 && (
        diff < 60 && "just now" ||
        diff < 120 && "1 minute ago" ||
        diff < 3600 && Math.floor(diff / 60) + " minutes ago" ||
        diff < 7200 && "1 hour ago" ||
        diff < 86400 && Math.floor(diff / 3600) + " hours ago") ||
        day_diff === 1 && "Yesterday" ||
        day_diff < 7 && day_diff + " days ago" ||
        day_diff < 31 && Math.ceil(day_diff / 7) + " weeks ago";
    },

    refresh: function () {
      DAO.listFiles("").then(files => {
        this.files = files;
        this.loaded = true;
      });
    },
    deleteFile: function(key) {
      this.deleting.push(key);
      DAO.deleteFiles([key]).then(deleted => {
        this.deleting = _.difference(this.deleting, deleted);
        this.refresh();
      })
    },
    cancelUpload: function(fileSpec) {
      if (!this.isCancelled(fileSpec)) {
        this.cancelled.push(fileSpec.name);
      }
    },
    isCancelled: function(fileSpec) {
      return this.cancelled.includes(fileSpec.name);
    },
    setUploadProgress: function(fileSpec, done, total) {
      let i = _.findIndex(this.uploading, s => s.spec.name === fileSpec.name);
      if (i > -1) {
        if (total === done) {
          this.uploading.splice(i, 1);
        } else {
          this.uploading[i].done = done;
          this.uploading[i].total = total;
        }
      }
    },

    isDeleting: function(key) {
      return _.includes(this.deleting, key);
    },
    dragOver: function(event) {
      this.dropping = true;
    },
    dragLeave: function(event) {
      this.dropping = false;
    },
    uploadFile: function(file) {
      this.uploading.push({
        spec: file,
        done: 0,
        total: file.size,
      });

      DAO.uploadHandle({
        name: file.name,
        type: file.type,
        size: file.size
      }).then(data => {
        this.setUploadProgress(file, 0, file.size);
        let xhr = new XMLHttpRequest();
        xhr.overrideMimeType(file.type);

        xhr.upload.addEventListener("progress", evt => {
          if (evt.lengthComputable) {
            this.setUploadProgress(file, evt.loaded, evt.total);
          }
          if (this.isCancelled(file)) {
            xhr.abort();
            this.uploading.splice(_.findIndex(this.uploading, f => f.spec.name === file.name), 1);
            this.cancelled.splice(this.cancelled.indexOf(file.name), 1);
          }
        });
        xhr.addEventListener("load", evt => {
          this.setUploadProgress(file, evt.loaded, evt.total);
          this.refresh();
        });
        xhr.addEventListener("error", evt => {

        });

        xhr.open("PUT", data.presignedUrl);
        xhr.setRequestHeader("Content-Type", file.type);
        xhr.send(file);
      });
    },
    uploadFiles: function(event) {
      this.dragLeave(event);
      for (let i = 0; i < event.dataTransfer.files.length; i++) {
        this.uploadFile(event.dataTransfer.files[i]);
      }
    },
  },
  computed: {
  },
  created: function () {
    this.refresh();
  },
  template: `
    <div id="data-manager-container">
      <table class="table table-bordered table-striped table-sm" v-if="files.length > 0">
        <thead>
        <tr>
          <td>Name</td>
          <td>Last Modified</td>
          <td>Size</td>
          <td></td>
        </tr>
        </thead>
        <tbody>
        <tr v-for="file in files">
          <td>{{file.key}}</td>
          <td>{{prettyDate(file.lastModified)}}</td>
          <td>{{file.size}}</td>
          <td>
            <a href="#" v-on:click="deleteFile(file.key)">
              Delete
              <i v-if="isDeleting(file.key)" class="fa fa-circle-o-notch fa-fw fa-spin"></i>
            </a>
          </td>
        </tr>
        </tbody>
      </table>
      <div class="admin-help-notice" v-else-if="loaded">
        There are no files yet.
      </div>
      <div id="drop-target"
           v-bind:class="{dropping: dropping}"
           v-on:dragover.prevent="dragOver"
           v-on:dragleave.prevent="dragLeave"
           v-on:drop.prevent="uploadFiles"
      >
        Drop files here...
      </div>
      <div id="upload-progress" v-if="uploading.length > 0">
        <div v-for="job in uploading" class="progress-container">
          <div class="progress">
            <div class="progress-bar progress-bar-striped progress-bar-animated" 
                 role="progressbar"
                 v-bind:aria-valuemax="job.total" 
                 v-bind:aria-valuemin="0" 
                 v-bind:aria-valuenow="job.done"
                  v-bind:style="'width: ' + (job.done/job.total * 100) + '%'">
              {{job.spec.name}}
            </div>
          </div>
          <button class="cancel-button" v-bind:disabled="isCancelled(job.spec)" v-on:click.prevent="cancelUpload(job.spec)">
              <i class="fa fa-fw" 
                 v-bind:class="{'fa-circle-o-notch fa-spin': isCancelled(job.spec), 'fa-times-circle': !isCancelled(job.spec)}"/>
          </button>
        </div>
      </div>
    </div>
  `
});
