<script lang="ts">
import {timeToRelative, humanFileSize} from "../common";

function emptyObj() {
  return {};
}

/**
 * Files table: this can be a fairly heavy component if there are lots of files
 * so it's rendered as a functional component with no state of its own.
 */
export default {
  props: {
    files: Array,
    fileStage: String,
    loadingMore: {
      type: Boolean,
      default: false,
    },
    loaded: {
      type: Boolean,
      default: false,
    },
    truncated: {
      type: Boolean,
      default: false
    },
    previewing: {
      type: Object,
      default: emptyObj,
    },
    validating: {
      type: Object,
      default: emptyObj,
    },
    validationResults: {
      type: Object,
      default: emptyObj,
    },
    selected: {
      type: Object,
      default: emptyObj,
    },
    deleting: {
      type: Object,
      default: emptyObj,
    },
    downloading: {
      type: Object,
      default: emptyObj,
    },
    loadingInfo: {
      type: Object,
      default: emptyObj,
    },
    filter: {
      type: String,
      default: ""
    },
    dropping: {
      type: Boolean,
      default: false,
    }
  },
  methods: {
    prettyDate: timeToRelative,
    humanFileSize,
    decodeURI,
  },
};
</script>

<template>
  <div v-bind:class="{'loading': !loaded, 'dropping': dropping}"
       v-on:keyup.down="$emit('select-next')"
       v-on:keyup.up="$emit('select-prev')"
       v-on:click.self.stop="$emit('deselect-all')"
       class="file-list-container">
    <table class="table table-bordered table-striped table-sm" v-if="files.length > 0">
      <thead>
      <tr>
        <th><input type="checkbox"
                   v-bind:id="fileStage + '-checkall'"
                   v-bind:indeterminate.prop="Object.keys(selected).length > 0 && Object.keys(selected).length < files.length"
                   v-on:change="e => $emit('toggle-all', e.target.checked)" /></th>
        <th>Name</th>
        <th>Last Modified</th>
        <th>Size</th>
        <th v-bind:colspan="(
            Number(loadingInfo !== null) +
            Number(validating !== null) +
            Number(downloading !== null) +
            Number(deleting !== null))"></th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="file in files"
          v-bind:key="file.key"
          v-on:click.stop="$emit('show-preview', file)"
          v-bind:class="{'active': previewing != null && previewing.key === file.key}">
        <td v-on:click.stop.prevent="$emit('toggle-file', file)">
          <input type="checkbox"
                 v-bind:checked="Boolean(selected[file.key])"
                 v-on:input.stop.prevent.self="$emit('toggle-file', file)"
                 v-on:click="$event.stopPropagation()">
        </td>
        <td>{{ decodeURI(file.key) }}</td>
        <td v-bind:title="file.lastModified">{{ prettyDate(file.lastModified) }}</td>
        <td>{{ humanFileSize(file.size) }}</td>

        <td v-if="loadingInfo !== null">
          <a href="#" v-on:click.prevent.stop="$emit('info', file.key)">
            <i class="fa fa-fw" v-bind:class="{
                'fa-circle-o-notch fa-spin': loadingInfo[file.key],
                'fa-info-circle': !loadingInfo[file.key]
              }"></i>
          </a>
        </td>
        <td v-if="validating !== null">
            <a href="#" v-on:click.prevent.stop="$emit('validate-files', {[file.eTag]: file.key})">
            <i v-if="validating[file.eTag]" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
            <i v-else-if="validationResults && validationResults[file.eTag]" class="fa fa-fw" v-bind:class="{
                    'fa-check text-success': validationResults[file.eTag].length === 0,
                    'fa-exclamation-circle text-danger': validationResults[file.eTag].length > 0
                    }">
            </i>
            <i v-else class="fa fa-fw fa-flag-o"></i>
          </a>
        </td>
        <td v-if="downloading !== null">
          <a href="#" title="" v-on:click.prevent.stop="$emit('download-files', [file.key])">
            <i class="fa fa-fw" v-bind:class="{
                'fa-circle-o-notch fa-spin': downloading[file.key],
                'fa-download': !downloading[file.key]
              }"></i>
          </a>
        </td>
        <td v-if="deleting !== null">
          <a href="#" v-on:click.prevent.stop="$emit('delete-files', [file.key])">
            <i class="fa fa-fw" v-bind:class="{
                'fa-circle-o-notch fa-spin': deleting[file.key],
                'fa-trash-o': !deleting[file.key]
              }"></i>
          </a>
        </td>
      </tr>
      </tbody>
    </table>
    <button class="btn btn-sm btn-default" v-if="truncated" v-on:click.prevent.stop="$emit('load-more')">
      Load more
      <i v-if="loadingMore" class="fa fa-fw fa-cog fa-spin"/>
      <i v-else class="fa fa-fw fa-caret-down"/>
    </button>
    <div class="panel-placeholder" v-else-if="loaded && filter && files.length === 0">
      No files found starting with &quot;<code>{{ filter }}</code>&quot;...
    </div>
    <div class="panel-placeholder" v-else-if="loaded && files.length === 0">
      There are no files here yet.
    </div>
    <div class="file-list-loading-indicator" v-show="!loaded">
      <i class="fa fa-3x fa-spinner fa-spin"></i>
    </div>
  </div>
</template>

