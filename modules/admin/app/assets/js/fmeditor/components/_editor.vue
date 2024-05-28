<script lang="ts">
import FieldMetadataEditorApi from "../api";
import {FieldMetadata} from "../types";

export default {
  props: {
    api: Object as FieldMetadataEditorApi,
    fm: Object as FieldMetadata,
  },
  data() {
    return {
      saving: false,
      name: this.fm.name ? this.fm.name : "",
      description: this.fm.description ? this.fm.description : "",
      usage: this.fm.usage ? this.fm.usage : "",
      seeAlso: this.fm.seeAlso ? this.fm.seeAlso : "",
    }
  },
  methods: {
    save: async function () {
      this.saving = true;
      try {
        await this.api.create(this.fm.entityType, this.fm.id, {
          name: this.name,
          description: this.description,
          usage: this.usage,
          seeAlso: [this.seeAlso],
        });
        this.$emit('saved');
      } catch (e) {
        console.error(e);
      } finally {
        this.saving = false;
      }
    }
  }
}



</script>

<template>
  <tr id="field-metadata-editor-form" class="fm-editor-form">
    <td>
      <input type="text" v-model="name" class="form-control" readonly />
    </td>
    <td>
      <input type="text" v-model="description" class="form-control" />
    </td>
    <td>
      <input type="text" v-model="usage" class="form-control" />
    </td>
    <td>
      <input type="text" v-model="seeAlso" class="form-control" />
    </td>
    <td>
      <button class="btn btn-primary" v-on:click="save">Save</button>
    </td>
  </tr>
</template>

<style scoped>

</style>
