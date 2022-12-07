<script lang="ts">

import AutocompleteInput from './_autocomplete-input';

export default {
  components: {AutocompleteInput},
  props: {
    type: String,
    api: Object,
    config: Object,
  },
  data: function () {
    return {
      adding: [],
      saving: false
    };
  },
  methods: {
    acceptItem: function (text, targetId) {
      this.adding.push({
        text: text,
        targetId: targetId,
        description: ""
      });
    },
    saveItems: async function () {
      this.saving = true;
      try {
        await this.api.createAccessPoints(this.config.id, this.config.did, this.type, this.adding);
        this.$emit('added');
      } finally {
        this.saving = false;
      }
    },
    setDescription: function (i, desc) {
      this.adding[i].description = desc;
    }
  },
}
</script>

<template>
  <div class="ap-editor-new-access-point">
    <button v-on:click="$emit('cancel-add')"
            type="button" class="close" aria-label="Close"><span aria-hidden="true">&times;</span></button>
    <h4>New Access Points</h4>
    <autocomplete-input
        v-bind:type="type"
        v-bind:disabled="saving"
        v-bind:api="api"
        v-on:item-accepted="acceptItem"
      />
    <ul class="ap-editor-pending-items" v-if="adding.length">
      <li v-for="(item, i) in adding" v-bind:key="i">
        <div class="control-elements input-group ap-editor-pending-item">
                  <span v-if="item.targetId" title="Item is linked"
                        class="input-group-prepend ap-editor-pending-item-link-note">
                    <i class="fa fa-link"></i>
                  </span>
          <span class="input-group-prepend input-group-text">
                    {{item.text}}
                  </span>
          <input class="form-control"
                 v-bind:disabled="saving"
                 v-model="item.description"
                 placeholder="Optional description..."/>
          <button title="Remove item" class="btn btn-danger input-group-append"
                  v-bind:disabled="saving"
                  v-on:click.prevent="adding.splice(i, 1)">
            <i class="fa fa-remove"></i>
          </button>
        </div>
      </li>
    </ul>
    <div class="ap-editor-new-access-point-controls">
      <button class="btn btn-danger"
              v-on:click="saveItems"
              v-bind:disabled="!adding.length || saving">Save</button>
      <button v-bind:disabled="saving"
              class="btn btn-default"
              v-on:click="$emit('cancel-add')">Cancel</button>
    </div>
  </div>
</template>
