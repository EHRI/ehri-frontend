<script lang="ts">
import FieldMetadataEditorApi from "../api";
import {FieldMetadata} from "../types";
import ModalWindow from "./_modal-window";

export default {
  components: {ModalWindow},
  props: {
    api: Object as FieldMetadataEditorApi,
    fm: Object as FieldMetadata,
    templates: Object,
  },
  data() {
    return {
      saving: false,
      et: this.fm.entityType ? this.fm.entityType : "",
      id: this.fm.id ? this.fm.id : "",
      name: this.fm.name ? this.fm.name : "",
      description: this.fm.description ? this.fm.description : "",
      category: this.fm.category ? this.fm.category : "",
      usage: this.fm.usage ? this.fm.usage : "",
      seeAlso: this.fm.seeAlso ? this.fm.seeAlso : "",
    }
  },
  methods: {
    save: async function () {
      this.saving = true;
      try {
        await this.api.save(this.et, this.id, {
          name: this.name,
          description: this.description,
          usage: this.usage,
          category: this.category,
          seeAlso: [this.seeAlso],
        });
        this.$emit('saved');
      } catch (e) {
        console.error(e);
      } finally {
        this.saving = false;
      }
    },
    categoryFor: function (entityType, id) {
      for (let [category, fields] in this.templates[entityType]) {
        if (fields.includes(id)) {
          return category;
        }
      }
      return null;
    }
  },
  computed: {
    categories: function () {
      return this.templates[this.et];
    },
    hasCategories: function () {
      // If there's a single blank category, then there are no categories
      return !(this.categories.length === 1 && this.categories[0][0] === "");
    }
  },
  watch: {
    et: function (value) {
      if (value !== this.fm.entityType) {
        this.id = "";
        this.name = "";
        this.description = "";
        this.usage = "";
        this.seeAlso = "";
      }
    },
  },
  created() {
    console.log("Field Metadata Editor", this.templates);

  }
}


</script>

<template>
  <modal-window v-bind:resizable="true" v-on:close="$emit('close')">
    <template v-slot:title>Field Metadata Editor</template>
    <fieldset id="field-metadata-editor-form" class="options-form">
      <div class="form-group">
        <label for="fm-entityType">Entity Type</label>
        <select id="fm-entityType" v-model="et" class="form-control">
          <option v-for="et in Object.keys(templates)">{{ et }}</option>
        </select>
      </div>

      <div class="form-group" v-if="hasCategories">
          <label for="fm-category">Section</label>
          <select id="fm-category" v-model="category" class="form-control">
            <option v-for="[category, _] in templates[et]">{{ category }}</option>
          </select>
      </div>

      <div class="form-group">
        <label for="fm-id">Field ID</label>
        <select id="fm-id" v-model="id" class="form-control" v-bind:readonly="!Boolean(et) || (hasCategories && !Boolean(category))">
            <template v-for="([cat, fields], idx) in templates[et]">
              <option v-if="category === cat " v-for="field in fields" v-bind:value="field">{{ field }}</option>
            </template>
        </select>
      </div>

      <div class="form-group">
        <label for="fm-name">Name</label>
        <input type="text" id="fm-name" v-model="name" class="form-control"/>
      </div>
      <div class="form-group">
        <label for="fm-description">Description</label>
        <textarea id="fm-description" v-model="description" class="form-control"></textarea>
      </div>
      <div class="form-group">
        <label for="fm-usage">Usage</label>
        <input type="text" id="fm-usage" v-model="usage" class="form-control"/>
      </div>
      <div class="form-group">
        <label for="fm-seeAlso">See Also</label>
        <input type="text" id="fm-seeAlso" v-model="seeAlso" class="form-control"/>
      </div>
    </fieldset>
    <template v-slot:footer>
      <div class="form-group">
        <button class="btn btn-primary" v-on:click="save">Save</button>
      </div>
    </template>
  </modal-window>
</template>

<style scoped>

</style>
