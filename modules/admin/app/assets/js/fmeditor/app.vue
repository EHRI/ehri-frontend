<script lang="ts">

import FileMetadataEditorApi from "./api";
import ModalEditor from "./components/_modal-editor";

export default {
  components: {ModalEditor},
  props: {
    service: Object,
  },
  data() {
    return {
      api: new FileMetadataEditorApi(this.service),
      loading: true,
      fieldMetadata: [],
      templates: null,
      addNew: null,
      adding: false,
      addNewEntityType: null,
      addNewFieldName: null,
    }
  },
  methods: {
    reload: async function () {
      try {
        this.loading = true;
        this.fieldMetadata = await this.api.list();
        this.templates = await this.api.templates();
        console.log("Templates", this.templates);
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
    nameFor: function(entityType, id) {
      for (let [fid, name] of this.templates[entityType].fields) {
        if (fid === id) {
          return name;
        }
      }
      return null;
    },
    fieldMetaForTypeAndCategory: function (entityType, category) {
      let fields = [];
      for (let fm of this.fieldMetadata) {
        if (fm.entityType === entityType && fm.category === category) {
          fields.push(fm);
        }
      }
    }
  },
  watch: {
    addNewEntityType: function (value) {
      if (value === null) {
        this.addNewFieldName = null;
      }
    },
    addNewFieldName: function (value) {
      if (value === null) {
        this.addNew = null;
      } else {
        this.addNew = {entityType: this.addNewEntityType, id: value, name: this.nameFor(this.addNewEntityType, value)};
      }
    }
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
          <h3>{{entityType}}</h3>

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
                    <tr v-if="cat">
                        <td colspan="5" class="category">
                            <h4>{{ cat }}</h4>
                        </td>
                    </tr>

                    <template v-for="[et, fmeta] in fieldMetadata">
                        <template v-for="fm in fmeta" v-if="et === entityType">
                            <tr v-if="cat === fm.category">
                                <td>{{ fm.name }}</td>
                                <td>{{ fm.description }}</td>
                                <td>{{ fm.usage }}</td>
                                <td>
                                    <a v-for="sa in fm.seeAlso" v-bind:href="sa">{{ sa }}</a>
                                </td>
                                <td>
                                    <a v-on:click="addNew = fm" href="#">Update</a>
                                    /
                                    <a v-on:click="deleteFieldMetadata(fm.entityType, fm.id)" href="#">Delete</a>
                                </td>
                            </tr>
                        </template>
                    </template>
                </template>
              </tbody>
          </table>
          <button class="btn btn-default" v-on:click="addNewFieldMetadata(entityType)">Add new field metadata</button>

          <hr/>
      </div>
<!--      <div v-if="fieldMetadata.length === 0 && !loading">-->
<!--          <p>No field metadata found.</p>-->
<!--          <p><a href="#" v-on:click.prevent="addNew = {}">Add new field metadata</a></p>-->
<!--      </div>-->
      <modal-editor v-if="addNew !== null"
                    v-bind:api="api"
                    v-bind:fm="addNew"
                    v-bind:templates="templates"
                    v-on:saved="addNew = null; reload()"
                    v-on:close="addNew = null" />
  </div>
</template>

<style scoped>
  .category {
      background-color: lightgray;
      font-weight: bold;
  }
</style>
