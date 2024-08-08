<script lang="ts">

import EntityTypeMetadataApi from "../api";
import {EntityType, FieldMetadata, FieldMetadataTemplates} from "../types";
import ModalWindow from "../../datasets/components/_modal-window";
import MultiItem from "./_multi-item";
import ModalAlert from "../../datasets/components/_modal-alert.vue";

export default {
  components: {ModalAlert, ModalWindow, MultiItem},
  props: {
    api: Object as EntityTypeMetadataApi,
    item: Object as FieldMetadata,
    fieldMetadata: Object as Record<string, FieldMetadata[]>,
    templates: Object as FieldMetadataTemplates,
    editing: Boolean
  },
  data() {
    return {
      saving: false,
      deleting: false,
      confirmDelete: false,
      et: this.item.entityType ? this.item.entityType : "",
      id: this.item.id ? this.item.id : "",
      name: this.item.name ? this.item.name : "",
      description: this.item.description ? this.item.description : null,
      category: this.item.category ? this.item.category : null,
      usage: this.item.usage ? this.item.usage : null,
      defaultVal: this.item.defaultVal ? this.item.defaultVal : null,
      seeAlso: this.item.seeAlso ? this.item.seeAlso.slice() : [],
    };
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
          defaultVal: this.defaultVal,
          seeAlso: this.seeAlso,
        });
        this.$emit('saved');
      } catch (e) {
        this.$emit('error', "Error saving field metadata", e);
      } finally {
        this.saving = false;
      }
    },
    deleteField: async function (entityType: EntityType, id: string) {
      this.deleting = true;
      try {
        await this.api.deleteField(this.et, this.id);
        this.$emit('deleted');
      } catch (e) {
        this.$emit('error', "Error deleting field metadata", e);
      } finally {
        this.deleting = false;
        this.$emit('close');
      }
      this.$emit('delete-field-metadata', entityType, id);
    },
    categoryForId: function (entityType: EntityType, id: string): string | null {
      for (let [category, fields] of Object.entries(this.templates[entityType])) {
        if (fields.includes(id)) {
          return category === "_" ? null : category
        }
      }
      return null;
    },
    categoryName: function(entityType: EntityType, category: string): string {
      return category === '_' ? '' : this.$t(`dataModel.${entityType}.${category}`);
    },
    isUsed: function (entityType, id): boolean {
      return entityType in this.fieldMetadata
          ? this.fieldMetadata[entityType].some(f => f.id === id)
          : false;
    },
  },
  computed: {
    categories: function () {
      return this.templates[this.et];
    },
    i18nPrefix: function() {
      // hack where we lowercase the first letter of the entityType to
      // prefix the i18n keys
      return this.et.charAt(0).toLowerCase() + this.et.slice(1);
    },
    isValid: function() {
      return Boolean(this.et) && Boolean(this.id) && Boolean(this.name);
    }
  },
  watch: {
    et: function (value) {
      if (value !== this.item.entityType) {
        this.id = "";
        this.name = "";
        this.description = null;
        this.usage = null;
        this.defaultVal = null;
        this.seeAlso = [];
      }
    },
    id: function(value) {
      let tNameKey = `${this.i18nPrefix}.${value}`;
      let tDescKey = `${tNameKey}.description`;
      if (tNameKey !== this.$t(tNameKey)) {
        this.name = this.$t(tNameKey);
      }
      if (tDescKey !== this.$t(tDescKey)) {
        this.description = this.$t(tDescKey);
      }
      this.category = this.categoryForId(this.et, value);
    }
  },
}


</script>

<template>
    <modal-window v-bind:resizable="true" v-on:close="$emit('close')" v-on:keyup.esc="$emit('close')">
        <template v-slot:title>Field Metadata: {{ et }}</template>

        <fieldset id="field-metadata-editor-form" class="options-form">
            <div class="form-group">
                <label for="fm-id">Field ID</label>
                <select id="fm-id" v-model="id" class="form-control" v-bind:disabled="editing">
                    <template v-if="et" v-for="(fields, category) in templates[et]">
                        <option v-if="category === '_'" v-for="field in fields" v-bind:value="field">{{ field }}</option>
                        <optgroup v-else v-bind:label="categoryName(et, category)">
                            <option v-for="field in fields" v-bind:value="field" v-bind:disabled="isUsed(et, field)">{{ field }}</option>
                        </optgroup>
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
                        {{ usage === null ? 'Optional' : $t(`dataModel.field.usage.${usage}`) }}
                    </option>
                </select>
            </div>
            <div class="form-group">
                <label for="fm-default-val">Default Value</label>
                <input type="text" id="fm-default-val" v-model.trim="defaultVal" class="form-control"/>
            </div>
            <multi-item v-bind:label="'See Also'" v-model="seeAlso" v-bind:type="'url'" />
        </fieldset>
        <template v-slot:footer>
            <button tabindex="-1" v-if="editing" class="btn btn-danger" id="delete-metadata" v-on:click="confirmDelete = true">
                <i v-if="deleting" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
                <i v-else class="fa fa-fw fa-trash-o"></i>
                Delete Field Metadata
            </button>
            <button class="btn btn-primary" v-on:click="saveField" v-bind:disabled="!isValid">
                <i v-if="saving" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
                <i v-else class="fa fa-fw fa-save"></i>
                Save Field Metadata
            </button>
            <modal-alert v-if="confirmDelete"
                         v-bind:title="'Delete Field Metadata'"
                         v-bind:cls="'danger confirm-delete-field-metadata'"
                         v-bind:accept="'Delete'"
                         v-bind:cancel="'Cancel'"
                         v-on:accept="deleteField(et, id)"
                         v-on:close="confirmDelete = false">
                <p>Are you sure you want to delete this field?</p>
            </modal-alert>
        </template>
    </modal-window>
</template>
