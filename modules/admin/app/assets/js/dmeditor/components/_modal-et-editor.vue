<script lang="ts">

import EntityTypeMetadataApi from "../api";
import {EntityTypeMetadata} from "../types";
import ModalWindow from "../../datasets/components/_modal-window";
import ModalAlert from "../../datasets/components/_modal-alert.vue";

export default {
  components: {ModalAlert, ModalWindow},
  props: {
    api: Object as EntityTypeMetadataApi,
    item: Object as EntityTypeMetadata,
    entityTypeMetadata: Object as Record<string, EntityTypeMetadata>
  },
  data() {
    return {
      saving: false,
      deleting: false,
      confirmDelete: false,
      name: this.item.name ? this.item.name : "",
      description: this.item.description ? this.item.description : ""
    }
  },
  methods: {
    save: async function () {
      this.saving = true;
      try {
        await this.api.save(this.item.entityType, {
          name: this.name,
          description: this.description,
        });
        this.$emit('saved');
      } catch (e) {
        this.$emit('error', "Error saving entity type metadata", e);
      } finally {
        this.saving = false;
      }
    },
    deleteEt: async function() {
      this.deleting = true;
      try {
        await this.api.delete(this.item.entityType);
        this.$emit('deleted');
      } catch (e) {
        this.$emit('error', "Error deleting entity type metadata", e);
      } finally {
        this.deleting = false;
        this.$emit('close');
      }
    }
  },
}


</script>

<template>
    <modal-window v-bind:resizable="true" v-on:close="$emit('close')" v-on:keyup.esc="$emit('close')">
        <template v-slot:title>Entity Type Metadata Editor</template>
        <fieldset id="entity-type-metadata-editor-form" class="options-form">
            <div class="form-group">
                <label for="et-name">Name</label>
                <input type="text" id="et-name" v-model.trim="name" class="form-control"/>
            </div>
            <div class="form-group">
                <label for="et-description">Description</label>
                <textarea rows="8" id="et-description" v-model.trim="description" class="form-control"></textarea>
            </div>
        </fieldset>
        <template v-slot:footer>
            <button v-if="item.created" class="btn btn-danger" id="delete-metadata" v-on:click="confirmDelete = true">
                <i v-if="saving" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
                <i v-else class="fa fa-fw fa-trash-o"></i>
                Delete Metadata
            </button>
            <button class="btn btn-primary" v-on:click="save">
                <i v-if="saving" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
                <i v-else class="fa fa-fw fa-save"></i>
                Save
            </button>
            <modal-alert v-if="confirmDelete"
                         v-bind:title="'Delete Entity Type Metadata'"
                         v-bind:cls="'danger confirm-delete-metadata'"
                         v-bind:accept="'Delete'"
                         v-bind:cancel="'Cancel'"
                         v-on:accept="deleteEt"
                         v-on:close="confirmDelete = false">
                <p>Are you sure you want to delete this entity type metadata?
                    All associated fields will be removed.</p>
            </modal-alert>
        </template>
    </modal-window>
</template>
