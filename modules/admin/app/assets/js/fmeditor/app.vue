<script lang="ts">

import FileMetadataEditorApi from "./api";
import ModalFmEditor from "./components/_modal-fm-editor.vue";
import ModalEtEditor from "./components/_modal-et-editor.vue";
import {EntityTypeMetadata, FieldMetadata} from "./types";

export default {
  components: {ModalFmEditor, ModalEtEditor},
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
      editET: null,
      addNew: null,
      addNewEntityType: null,
    }
  },
  methods: {
    reload: async function () {
      try {
        this.loading = true;

        let et = await this.api.listET();
        console.log(et);
        this.entityTypeMetadata = et;

        let fm = await this.api.list();
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
    deleteFieldMetadata: function (entityType, id) {
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
                            <a v-on:click="deleteFieldMetadata(fm.entityType, fm.id)" href="#">
                                <i class="fa fa-trash"></i>
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
