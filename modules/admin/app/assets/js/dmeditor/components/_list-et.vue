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
      addNew: null as FieldMetadata | null,
      addNewEntityType: null as Record<string, string> | null,
      confirmDelete: null as { entityType: EntityType, id: string } | null,
      deleting: null as { entityType: EntityType, id: string } | null,
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

        let td = await this.api.templates();
        console.log("Templates", td);
        this.templates = td;
      } catch (e) {
        this.$emit('error', "Error fetching data model info from API", e);
      } finally {
        this.loading = false;
      }
    },
    addNewFieldMetadata: function (entityType) {
      this.addNew = {entityType};
    },
    updateFieldMetadata: function (entityType, id) {
      this.$emit('update-field-metadata', entityType, id);
    },
    deleteFieldMetadata: async function (entityType: EntityType, id: string) {
      this.deleting = {entityType, id};
      try {
        await this.api.deleteField(entityType, id);
        await this.reload();
      } catch (e) {
        this.$emit('error', "Error deleting field metadata", e);
      } finally {
        this.deleting = null;
      }
      this.$emit('delete-field-metadata', entityType, id);
    },
    editFieldMetadata: function (fm: FieldMetadata) {
      this.addNew = fm;
    },
    fieldMetadataFor: function (entityType: string, cat: string) {
      let items = []
      let fieldMetaForType = this.fieldMetadata[entityType];
      if (fieldMetaForType) {
        for (let fm of fieldMetaForType) {
          if (!fm.category || fm.category === cat) {
            items.push(fm);
          }
        }
      }
      return items;
    },
    fieldRowId: function (entityType: string, id: string) {
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
                       v-on:click.prevent="editET = entityTypeMetadata[entityType]" href="#">
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
                                <h4>{{ cat }}</h4>
                            </td>
                        </tr>

                        <tr v-for="fm in fieldMetadataFor(entityType, cat)" v-bind:id="fieldRowId(entityType, fm.id)">
                            <td class="fm-name">{{ fm.name }}</td>
                            <td class="fm-usage" v-bind:class="fm.usage">
                                <div v-if="fm.usage" v-bind:class="'badge badge-'+ fm.usage">{{ fm.usage }}</div>
                                <div v-else class="badge badge-optional">optional</div>
                            </td>
                            <td class="fm-description">
                                <markdown v-bind:content="fm.description"/>
                            </td>
                            <td class="fm-see-also">
                                <a v-for="sa in fm.seeAlso" v-bind:href="sa">{{ sa }}</a>
                            </td>
                            <td class="fm-actions">
                                <a class="fm-edit" v-on:click="editFieldMetadata(fm)" href="#">
                                    <i class="fa fa-pencil"></i>
                                </a>
                                <a class="fm-delete" v-on:click="confirmDelete = {entityType: fm.entityType, id: fm.id}"
                                   href="#">
                                    <i v-if="deleting && deleting.entityType === fm.entityType && deleting.id === fm.id"
                                       class="fa fa-spinner fa-spin"></i>
                                    <i v-else class="fa fa-trash"></i>
                                </a>
                            </td>
                        </tr>
                    </template>
                    </tbody>
                </table>
                <button class="btn btn-default" v-bind:id="'fm-editor-add-field-' + entityType"
                        v-on:click="addNewFieldMetadata(entityType)">Add new field metadata
                </button>

                <hr/>
            </template>
        </div>
        <template v-if="missingEntityTypes.length > 0" v-for="et in missingEntityTypes">
            <button class="btn btn-default btn-lg" v-on:click="editET = {entityType: et, name: '', description: ''}">Add metadata for {{ et }}</button>
            <hr/>
        </template>
        <modal-fm-editor v-if="addNew !== null"
                         v-bind:api="api"
                         v-bind:item="addNew"
                         v-bind:templates="templates"
                         v-bind:field-metadata="fieldMetadata"
                         v-on:saved="addNew = null; reload()"
                         v-on:close="addNew = null"
                         v-on:error="(...args) => $emit('error', ...args)">
        </modal-fm-editor>
        <modal-et-editor v-if="editET !== null"
                         v-bind:api="api"
                         v-bind:item="editET"
                         v-bind:entityTypeMetadata="entityTypeMetadata"
                         v-on:saved="editET = null; reload()"
                         v-on:close="editET = null"
                         v-on:error="(...args) => $emit('error', ...args)">
        </modal-et-editor>
        <modal-alert v-if="confirmDelete !== null"
                     v-bind:title="'Delete Field Metadata'"
                     v-bind:cls="'danger confirm-delete-field-metadata'"
                     v-bind:accept="'Delete'"
                     v-bind:cancel="'Cancel'"
                     v-on:accept="deleteFieldMetadata(confirmDelete.entityType, confirmDelete.id); confirmDelete = null"
                     v-on:close="confirmDelete = null">
            <p>Are you sure you want to delete this field?</p>
        </modal-alert>
    </div>
</template>

<style scoped>
.section {
    background-color: #dfdfdf;
    font-weight: bold;
}

.fm-list td.fm-actions {
    width: 5rem;
}

.fm-see-also a + a {
    display: inline-block;
}

.fm-usage {
    white-space: nowrap;

}

.fm-list td:last-child a {
    margin-left: .5rem;
}

.badge-mandatory {
    background-color: #fba8a6;
}

.badge-desirable {
    background-color: #f8dab1;
}

.badge-optional {
    background-color: #f5f5f5;
}
</style>
