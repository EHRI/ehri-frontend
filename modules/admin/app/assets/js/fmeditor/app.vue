<script lang="ts">

import FileMetadataEditorApi from "./api";
import ModalFmEditor from "./components/_modal-fm-editor.vue";
import ModalEtEditor from "./components/_modal-et-editor.vue";
import {EntityType, EntityTypeMetadata, FieldMetadata} from "./types";
import ModalAlert from "../datasets/components/_modal-alert";

export default {
  components: {ModalFmEditor, ModalEtEditor, ModalAlert},
  props: {
    service: Object,
  },
  data() {
    return {
      api: new FileMetadataEditorApi(this.service),
      loading: true,
      entityTypeMetadata: Object as Record<string, EntityTypeMetadata>,
      fieldMetadata: Object as Record<string, FieldMetadata[]>,
      templates: null,
      editET: null as EntityTypeMetadata | null,
      addNew: null as FieldMetadata | null,
      addNewEntityType: null as Record<string, string> | null,
      confirmDelete: null as {entityType: EntityType, id: string} | null,
      deleting: null as {entityType: EntityType, id: string} | null,
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
        console.error(e);
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
        console.error(e);
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
            console.log("FM", fm, cat, fm.category, fm.category === cat)
            items.push(fm);
          }
        }
      }
      return items;
    },
  },
  created: function () {
    this.reload();
  },
}
</script>

<template>
    <div id="field-metadata-editor" class="fm-editor">
        <span v-if="loading">Loading...</span>
        <div class="fm-editor-list" v-for="(catFields, entityType) in templates">
            <h3>
                {{ entityTypeMetadata[entityType] ? entityTypeMetadata[entityType].name : entityType }}
                <a v-if="entityTypeMetadata[entityType]" v-on:click.prevent="editET = entityTypeMetadata[entityType]" href="#">
                    <i class="fa fa-pencil"></i>
                </a>
            </h3>
            <p v-if="entityTypeMetadata[entityType]">{{ entityTypeMetadata[entityType].description }}</p>

            <table v-if="fieldMetadata" class="table table-bordered fm-list">
                <thead>
                <tr>
                    <th>Name</th>
                    <th>Description</th>
                    <th>Usage</th>
                    <th>See Also</th>
                    <th></th>
                </tr>
                </thead>
                <tbody>
                <template v-for="([cat, fields], idx) in catFields">
                    <tr v-if="cat && fieldMetadataFor(entityType, cat).length > 0" v-bind:id="'fm-' + entityType + '-' + cat">
                        <td colspan="5" class="category">
                            <h4>{{ cat }}</h4>
                        </td>
                    </tr>

                    <tr v-for="fm in fieldMetadataFor(entityType, cat)" v-bind:id="'fm-' + entityType + '-' + fm.id">
                        <td class="fm-name">{{ fm.name }}</td>
                        <td class="fm-description">{{ fm.description }}</td>
                        <td class="fm-usage">{{ fm.usage }}</td>
                        <td class="fm-see-also">
                            <a v-for="sa in fm.seeAlso" v-bind:href="sa">{{ sa }}</a>
                        </td>
                        <td class="fm-actions">
                            <a v-on:click="editFieldMetadata(fm)" href="#">
                                <i class="fa fa-pencil"></i>
                            </a>
                            <a v-on:click="confirmDelete = {entityType: fm.entityType, id: fm.id}" href="#">
                                <i v-if="deleting && deleting.entityType === fm.entityType && deleting.id === fm.id" class="fa fa-spinner fa-spin"></i>
                                <i v-else class="fa fa-trash"></i>
                            </a>
                        </td>
                    </tr>
                </template>
                </tbody>
            </table>
            <button class="btn btn-default" v-on:click="addNewFieldMetadata(entityType)">Add new field metadata</button>

            <hr/>
        </div>
        <modal-fm-editor v-if="addNew !== null"
                      v-bind:api="api"
                      v-bind:item="addNew"
                      v-bind:templates="templates"
                      v-bind:field-metadata="fieldMetadata"
                      v-on:saved="addNew = null; reload()"
                      v-on:close="addNew = null"/>
        <modal-et-editor v-if="editET !== null"
                        v-bind:api="api"
                        v-bind:item="editET"
                        v-bind:entityTypeMetadata="entityTypeMetadata"
                        v-on:saved="editET = null; reload()"
                        v-on:close="editET = null"/>
        <modal-alert v-if="confirmDelete !== null"
                     title="Delete Field Metadata"
                     cls="danger"
                     accept="Delete"
                     cancel="Cancel"
                     v-on:accept="deleteFieldMetadata(confirmDelete.entityType, confirmDelete.id); confirmDelete = null"
                     v-on:close="confirmDelete = null">
            <p>Are you sure you want to delete this field?</p>
        </modal-alert>
    </div>
</template>

<style scoped>
.category {
    background-color: #dfdfdf;
    font-weight: bold;
}

.fm-list td.fm-actions {
    width: 5rem;
}

.fm-see-also a + a {
    margin-left: .5rem;
}

.fm-list td:last-child a {
    margin-left: .5rem;
}
</style>
