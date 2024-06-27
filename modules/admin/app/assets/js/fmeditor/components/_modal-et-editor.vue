<script lang="ts">
import FieldMetadataEditorApi from "../api";
import {EntityTypeMetadata, FieldMetadata, FieldMetadataTemplates} from "../types";
import ModalWindow from "./_modal-window";
import MultiItem from "./_multi-item";

export default {
  components: {ModalWindow},
  props: {
    api: Object as FieldMetadataEditorApi,
    item: Object as EntityTypeMetadata,
    entityTypeMetadata: Object as Record<string, EntityTypeMetadata>
  },
  data() {
    return {
      saving: false,
      name: this.item.name ? this.item.name : "",
      description: this.item.description ? this.item.description : ""
    }
  },
  methods: {
    save: async function () {
      this.saving = true;
      try {
        await this.api.saveET(this.item.entityType, {
          name: this.name,
          description: this.description,
        });
        this.$emit('saved');
      } catch (e) {
        console.error(e);
      } finally {
        this.saving = false;
      }
    },
  },
  created() {
    console.log("Entity Type Metadata Editor", this.templates);

  }
}


</script>

<template>
    <modal-window v-bind:resizable="true" v-on:close="$emit('close')">
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
            <button class="btn btn-primary" v-on:click="save">Save</button>
        </template>
    </modal-window>
</template>

<style scoped>

</style>
