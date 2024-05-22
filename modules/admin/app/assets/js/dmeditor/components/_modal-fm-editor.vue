<script lang="ts">

import EntityTypeMetadataApi from "../api";
import {FieldMetadata, FieldMetadataTemplates} from "../types";
import ModalWindow from "../../datasets/components/_modal-window";
import MultiItem from "./_multi-item";

export default {
  components: {ModalWindow, MultiItem},
  props: {
    api: Object as EntityTypeMetadataApi,
    item: Object as FieldMetadata,
    fieldMetadata: Object as Record<string, FieldMetadata[]>,
    templates: Object as FieldMetadataTemplates,
  },
  data() {
    return {
      saving: false,
      et: this.item.entityType ? this.item.entityType : "",
      id: this.item.id ? this.item.id : "",
      name: this.item.name ? this.item.name : "",
      description: this.item.description ? this.item.description : null,
      category: this.item.category ? this.item.category : null,
      usage: this.item.usage ? this.item.usage : null,
      seeAlso: this.item.seeAlso ? this.item.seeAlso.slice() : [],
    }
  },
  methods: {
    saveField: async function () {
      this.saving = true;
      try {
        await this.api.saveField(this.et, this.id, {
          name: this.name,
          description: this.description,
          usage: this.usage,
          category: this.category,
          seeAlso: this.seeAlso,
        });
        this.$emit('saved');
      } catch (e) {
        this.$emit('error', "Error saving field metadata", e);
      } finally {
        this.saving = false;
      }
    },
    categoryFor: function (entityType, id) {
      for (let [category, fields] of Object.entries(this.templates[entityType])) {
        if (fields.includes(id)) {
          return category;
        }
      }
      return null;
    },
    idsForCategory: function(entityType, category) {
        return category
            ? this.templates[entityType][category]
            : this.templates[entityType]["_"];
    },
    unusedIds: function (entityType) {
      let used = new Set();
      let items = this.fieldMetadata[entityType] as FieldMetadata[];
      for (let fm of items) {
        if (fm.entityType === entityType) {
          used.add(fm.id);
        }
      }

      let all = new Set();
      for (let [_, fields] of Object.entries(this.templates[entityType])) {
        for (let id in fields) {
          all.add(id);
        }
      }
      return Array.from(all).filter(id => !used.has(id));
    }
  },
  computed: {
    categories: function () {
      return this.templates[this.et];
    },
    hasCategories: function () {
      // If there's a single blank category, then there are no categories
      return !("_" in this.categories);
    }
  },
  watch: {
    et: function (value) {
      if (value !== this.item.entityType) {
        this.id = "";
        this.name = "";
        this.description = null;
        this.usage = null;
        this.seeAlso = [];
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
        <template v-slot:title>Field Metadata: {{ et }}</template>

        <fieldset id="field-metadata-editor-form" class="options-form">
            <div class="form-group" v-if="hasCategories">
                <label for="fm-category">Section</label>
                <select id="fm-category" v-model="category" class="form-control">
                    <option v-for="(_, category) in templates[et]">{{ category }}</option>
                </select>
            </div>

            <div class="form-group">
                <label for="fm-id">Field ID</label>
                <select id="fm-id" v-model="id" class="form-control"
                        v-bind:readonly="!Boolean(et) || (hasCategories && !Boolean(category))">
                    <option v-if="hasCategories && !Boolean(category)" value="">Select a section first...</option>
                    <option v-else-if="!id" value="">Select a field ID...</option>
                    
                    <template v-if="et" v-for="field in idsForCategory(et, category)">
                        <option v-bind:value="field">{{ field }}</option>
                    </template>
                </select>
            </div>

            <div class="form-group">
                <label for="fm-name">Name</label>
                <input type="text" id="fm-name" v-model.trim="name" class="form-control"/>
            </div>
            <div class="form-group">
                <label for="fm-description">Description</label>
                <textarea id="fm-description" v-model.trim="description" class="form-control"></textarea>
            </div>
            <div class="form-group">
                <label for="fm-usage">Usage</label>
                <select id="fm-usage" v-model="usage" class="form-control">
                    <option v-for="usage in [null, 'desirable', 'mandatory']" v-bind:value="usage">
                        {{ usage === null ? 'optional' : usage }}
                    </option>
                </select>
            </div>
            <multi-item v-bind:label="'See Also'" v-model="seeAlso" v-bind:type="'url'" />
        </fieldset>
        <template v-slot:footer>
            <button class="btn btn-primary" v-on:click="saveField">Save Field</button>
        </template>
    </modal-window>
</template>

<style scoped>

</style>
