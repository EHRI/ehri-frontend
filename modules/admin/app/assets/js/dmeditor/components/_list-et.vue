<script lang="ts">

import FileMetadataEditorApi from "../api";
import ModalFmEditor from "./_modal-fm-editor.vue";
import ModalEtEditor from "./_modal-et-editor.vue";
import {EntityType, EntityTypeMetadata, FieldMetadata, FieldMetadataTemplates} from "../types";
import ModalAlert from "../../datasets/components/_modal-alert";
import Markdown from './_markdown.vue';

export default {
  components: {ModalFmEditor, ModalEtEditor, ModalAlert, Markdown},
  props: {
    api: Object as FileMetadataEditorApi,
  },
  data() {
    return {
      loading: true,
      entityTypeMetadata: Object as Record<string, EntityTypeMetadata>,
      fieldMetadata: Object as Record<string, FieldMetadata[]>,
      templates: null as FieldMetadataTemplates | null,
      editET: null as EntityTypeMetadata | null,
      addFieldMeta: null as FieldMetadata | null,
      editFieldMeta: null as FieldMetadata | null,
      addNewEntityType: null as Record<string, string> | null,
    }
  },
  methods: {
    reload: async function () {
      try {
        this.loading = true;

        let et = await this.api.list();
        console.log(et);
        this.entityTypeMetadata = et;

        let fm = await this.api.listFields();
        console.log(fm);
        this.fieldMetadata = fm;

        this.templates = await this.api.templates();
      } catch (e) {
        this.$emit('error', "Error fetching data model info from API", e);
      } finally {
        this.loading = false;
      }
    },
    updateFieldMetadata: function (entityType, id) {
      this.$emit('update-field-metadata', entityType, id);
    },
    addNewFieldMetadata: function (entityType) {
      this.addFieldMeta = {entityType};
    },
    editFieldMetadata: function (fm: FieldMetadata) {
      this.editFieldMeta = fm;
    },
    addEntityTypeMetadata: function (entityType: EntityType) {
      this.editET = {
        entityType,
        name: this.$t(`contentTypes.${entityType}`),
        description: ''
      };
    },
    editEntityTypeMetadata: function (et: EntityTypeMetadata) {
      this.editET = et;
    },
    fieldMetadataFor: function (entityType: string, cat: string): FieldMetadata[] {
      let items = []
      let fieldMetaForType = this.fieldMetadata[entityType];
      if (fieldMetaForType) {
        for (let fm of fieldMetaForType) {
          if ((!fm.category && cat === '_') || fm.category === cat) {
            items.push(fm);
          }
        }
      }
      return items;
    },
    fieldRowId: function (entityType: string, id: string): string {
      return `fm-${entityType}-${id}`;
    },
  },
  computed: {
    missingEntityTypes: function () {
      if (this.templates === null || this.entityTypeMetadata === null) {
        return [];
      }
      return Object.keys(this.templates).filter(et => !(et in this.entityTypeMetadata));
    },
  },
  created: async function () {
    await this.reload();
  },
}
</script>

<template>
    <div id="entity-type-metadata-editor" class="fm-editor">
        <span v-if="loading && !fieldMetadata">Loading...</span>
        <div v-if="templates" class="fm-editor-list" v-for="(catFields, entityType) in templates">
            <template v-if="entityType in entityTypeMetadata">
                <h3>
                    {{ entityTypeMetadata[entityType].name ? entityTypeMetadata[entityType].name : entityType }}
                    <a v-if="entityTypeMetadata[entityType]"
                       v-on:click.prevent="editEntityTypeMetadata(entityTypeMetadata[entityType])" href="#">
                        <i class="fa fa-pencil"></i>
                    </a>
                </h3>
                <markdown v-if="entityTypeMetadata[entityType]"
                          v-bind:content="entityTypeMetadata[entityType].description"/>

                <table v-if="fieldMetadata" class="table table-bordered fm-list">
                    <thead>
                    <tr>
                        <th>Name</th>
                        <th>Usage</th>
                        <th>Description</th>
                        <th>See Also</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    <template v-for="(catFields, cat) in catFields">

                        <tr v-if="cat !== '_' && fieldMetadataFor(entityType, cat).length > 0"
                            v-bind:id="fieldRowId(entityType, cat)">
                            <td colspan="5" class="section">
                                <h4>{{ $t(`dataModel.${entityType}.${cat}`) }}</h4>
                            </td>
                        </tr>

                        <tr v-for="fm in fieldMetadataFor(entityType, cat)" v-bind:id="fieldRowId(entityType, fm.id)">
                            <td class="fm-name">{{ fm.name }}</td>
                            <td class="fm-usage" v-bind:class="fm.usage">
                                <div v-if="fm.usage" v-bind:class="'badge badge-'+ fm.usage">{{ $t(`dataModel.field.usage.${fm.usage}`) }}</div>
                                <div v-else class="badge badge-optional">{{ $t(`dataModel.field.usage.optional`) }}</div>
                            </td>
                            <td class="fm-description">
                                <markdown v-bind:content="fm.description"/>
                                <p v-if="fm.defaultVal"><strong>Default Value:</strong> <q>{{ fm.defaultVal }}</q></p>
                            </td>
                            <td class="fm-see-also">
                                <a v-for="sa in fm.seeAlso" v-bind:href="sa">{{ sa }}</a>
                            </td>
                            <td class="fm-actions">
                                <a class="fm-edit" v-on:click.prevent="editFieldMetadata(fm)" href="#">
                                    <i class="fa fa-pencil"></i>
                                </a>
                            </td>
                        </tr>
                    </template>
                    </tbody>
                </table>
                <button class="btn btn-default" v-bind:id="'fm-editor-add-field-' + entityType"
                        v-on:click.prevent="addNewFieldMetadata(entityType)">Add new field metadata
                </button>

                <hr/>
            </template>
        </div>
        <template v-if="missingEntityTypes.length > 0" v-for="et in missingEntityTypes">
            <button class="btn btn-default btn-lg" v-on:click.prevent="addEntityTypeMetadata(et)">Add metadata for {{ $t(`contentTypes.${et}`) }}</button>
            <hr/>
        </template>
        <modal-fm-editor v-if="addFieldMeta !== null"
                         v-bind:api="api"
                         v-bind:item="addFieldMeta"
                         v-bind:templates="templates"
                         v-bind:field-metadata="fieldMetadata"
                         v-bind:editing="false"
                         v-on:saved="addFieldMeta = null; reload()"
                         v-on:deleted="reload"
                         v-on:close="addFieldMeta = null"
                         v-on:error="(...args) => $emit('error', ...args)">
        </modal-fm-editor>
        <modal-fm-editor v-if="editFieldMeta !== null"
                         v-bind:api="api"
                         v-bind:item="editFieldMeta"
                         v-bind:templates="templates"
                         v-bind:field-metadata="fieldMetadata"
                         v-bind:editing="true"
                         v-on:saved="editFieldMeta = null; reload()"
                         v-on:deleted="reload"
                         v-on:close="editFieldMeta = null"
                         v-on:error="(...args) => $emit('error', ...args)">
        </modal-fm-editor>
        <modal-et-editor v-if="editET !== null"
                         v-bind:api="api"
                         v-bind:item="editET"
                         v-bind:entityTypeMetadata="entityTypeMetadata"
                         v-on:saved="editET = null; reload()"
                         v-on:deleted="reload"
                         v-on:close="editET = null"
                         v-on:error="(...args) => $emit('error', ...args)">
        </modal-et-editor>
    </div>
</template>
