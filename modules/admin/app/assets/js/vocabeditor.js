"use strict";

/**
 * A data access object containing functions to vocabulary concepts.
 */
let DAO = {
  ajaxHeaders: {
    "ajax-ignore-csrf": true,
    "Content-Type": "application/json",
    "Accept": "application/json; charset=utf-8"
  },

  /**
   *
   * @param obj an object of URL parameters
   * @returns {string}
   */
  objToQueryString: function (obj) {
    let str = [];
    for (var p in obj)
      if (obj.hasOwnProperty(p)) {
        if (Array.isArray(obj[p])) {
          obj[p].forEach(v => {
            str.push(encodeURIComponent(p) + "=" + encodeURIComponent(v));
          });
        } else {
          str.push(encodeURIComponent(p) + "=" + encodeURIComponent(obj[p]));
        }
      }
    return str.join("&");
  },

  get: function (id) {
    return fetch(window.SERVICE.get(CONFIG.vocabId, id).url)
      .then(r => r.json());
  },

  getLangData: function () {
    return fetch(window.SERVICE.langs(CONFIG.vocabId).url)
      .then(r => r.json())
      .then(json => json.data.map(a => a[0]));
  },

  search: function (q, opts) {
    return fetch(window.SERVICE.search().url + "?" + this.objToQueryString({
      q: q,
      f: "holderId:" + CONFIG.vocabId,
      ex: opts.excludeId,
      page: opts.page || 1
    })).then(r => r.json()
      .then(data => data.items));
  },

  getConcepts: function (q, lang) {
    return fetch(window.SERVICE.list(CONFIG.vocabId).url + "?" + this.objToQueryString({
      q: q,
      lang: lang
    })).then(r => r.json())
      .then(data => data.data);
  },

  getChildren: function (id, lang) {
    return fetch(window.SERVICE.narrower(CONFIG.vocabId, id).url + "?" + this.objToQueryString({
      lang: lang
    })).then(r => r.json())
      .then(data => data.data);
  },

  getNextIdentifier: function() {
    return fetch(window.SERVICE.nextIdentifier(CONFIG.vocabId).url).then(r => r.json());
  },

  createItem: function (data) {
    let self = this;
    return SERVICE.createItem(CONFIG.vocabId).ajax({
      data: JSON.stringify(data),
      headers: self.ajaxHeaders
    });
  },

  updateItem: function (id, data) {
    let self = this;
    return SERVICE.updateItem(CONFIG.vocabId, id).ajax({
      data: JSON.stringify(data),
      headers: self.ajaxHeaders
    });
  },

  deleteItem: function (id) {
    let self = this;
    return SERVICE.deleteItem(CONFIG.vocabId, id).ajax({
      data: JSON.stringify(null),
      headers: self.ajaxHeaders
    });
  },

  setBroader: function (id, broaderIds) {
    let self = this;
    return SERVICE.broader(CONFIG.vocabId, id).ajax({
      data: JSON.stringify(broaderIds),
      headers: self.ajaxHeaders
    });
  },

  title: function (data, lang, fallback) {
    for (let i in data.descriptions) {
      if (data.descriptions.hasOwnProperty(i)) {
        let desc = data.descriptions[i];
        if (desc.languageCode === lang) {
          return desc.name;
        }
      }
    }
    return data.descriptions[0] ? data.descriptions[0].name : fallback;
  },

  sortByTitle: function (lang) {
    return (a, b) => {
      return this.title(a, lang, a.id)
        .localeCompare(this.title(b, lang, b.id));
    }
  }
};

Vue.component("vocab-editor-autocomplete-suggestion", {
  props: {selected: Boolean, item: Object,},
  template: `
    <div @click="$emit('selected', item)" class="vocab-editor-autocomplete-widget-suggestion">
        {{ item.name }} 
        <span v-if="selected"> *</span> 
    </div>
  `
});

Vue.component("vocab-editor-autocomplete-input", {
  props: {excludeId: String, disabled: Boolean},
  data: function () {
    return {
      input: "",
      text: "",
      selectedIdx: -1,
      suggestions: [],
      loading: false,
      item: null,
    }
  },
  methods: {
    search: function () {
      this.input = this.text;
      if (this.text.trim().length === 0) {
        this.suggestions = [];
      } else {
        this.loading = true;
        DAO.search(this.text, {exclude: this.excludeId}).then(items => {
          this.loading = false;
          this.suggestions = items;
        });
      }
    },
    setItem: function (item) {
      this.item = item;
      this.text = item.name;
    },
    selectPrev: function () {
      this.selectedIdx = Math.max(-1, this.selectedIdx - 1);
      this.setItemFromSelection();
    },
    selectNext: function () {
      this.selectedIdx = Math.min(this.suggestions.length, this.selectedIdx + 1);
      this.setItemFromSelection();
    },
    setAndChooseItem: function (item) {
      this.setItem(item);
      this.accept();
    },
    setItemFromSelection: function () {
      let idx = this.selectedIdx,
        len = this.suggestions.length;
      if (idx > -1 && len > 0 && idx < len) {
        this.setItem(this.suggestions[idx]);
      } else if (idx === -1) {
        this.text = this.input;
        this.item = null;
      }
    },
    accept: function () {
      let text = this.item ? this.item.name : this.text,
        targetId = this.item ? this.item.id : null;
      this.$emit("item-accepted", text, targetId);
      this.text = "";
      this.input = "";
      this.cancelComplete();
    },
    cancelComplete: function () {
      this.suggestions = [];
      this.selectedIdx = -1;
      this.item = null;
    }
  },
  template: `
    <div class="vocab-editor-autocomplete-widget form-group">
        <div class="col-md-12">
            <input class="form-control input-sm" type="text" 
                placeholder="Add Broader Term..."
              v-bind:disabled="disabled"
              v-model.trim="text" 
              v-on:input="search"
              v-on:keydown.up="selectPrev"
              v-on:keydown.down="selectNext"
              v-on:keydown.enter="accept"
              v-on:keydown.esc="cancelComplete"/>
            <div class="dropdown-list" v-if="suggestions.length">
              <div class="vocab-editor-autocomplete-widget-suggestions">
                  <vocab-editor-autocomplete-suggestion
                      v-for="(suggestion, i) in suggestions"
                      v-bind:class="{selected: i == selectedIdx}"
                      v-bind:key="suggestion.id"
                      v-bind:item="suggestion"
                      v-bind:selected="i == selectedIdx"
                      v-on:selected="setAndChooseItem">
                  </vocab-editor-autocomplete-suggestion>
              </div>
            </div>
        </div>
    </div>
  `
});

Vue.component("concept-hierarchy", {
  props: { id: String, data: Array, lang: String },
  template: `
    <ul class="concept-hierarchy" v-if="data">
      <li v-for="parent in data">{{parent|conceptTitle(lang, id)}}
          <concept-hierarchy 
              v-bind:data="parent.broaderTerms || []"
              v-bind:lang="lang"
              v-bind:id="parent.id"/>
      </li>
    </ul>  
  `
});

Vue.component("concept-description-multi-item", {
  props: {
    label: String, data: Array, text: Boolean
  },
  data: function () {
    return {
      state: this.data,
      pending: null,
    };
  },
  watch: {
    data: function (newData, oldData) {
      this.state = newData;
    },
  },
  methods: {
    addPending: function () {
      if (this.pending) {
        this.state.push(this.pending);
        this.pending = null;
      }
    },
  },
  template: `
    <div class="form-group">
       <label class="col-md-2">{{label}}</label> 
       <div class="col-md-10">
         <div class="input-group" v-for="(item, i) in state">
           <textarea rows="1" v-if="text" class="form-control" v-model.trim="state[i]"></textarea>
           <input v-else class="form-control" v-model.trim="state[i]"/>
           <span class="input-group-addon" v-on:click="state.splice(i, 1)">
            <i class="fa fa-remove"></i>
           </span>
         </div>
         <div class="input-group">
           <textarea rows="1" v-if="text" class="form-control" placeholder="Add New..." 
                v-on:change="addPending" v-model.trim="pending"></textarea>
           <input v-else class="form-control" placeholder="Add New..." v-on:change="addPending" v-model.trim="pending"/>
           <span class="input-group-btn">
             <button class="btn" v-bind:disabled="pending === ''"
                v-bind:class="{'btn-success': pending}" v-on:click="addPending">
              <i class="fa fa-plus-circle"></i>
             </button>
           </span>
         </div>
       </div>
     </div>
  `
});

Vue.component("concept-description-editor", {
  props: {
    data: Object,
    langData: Object,
    newForm: Boolean,
  },
  data: function () {
    return {
      state: this.data,
    }
  },
  watch: {
    data: function (newData, oldData) {
      this.state = newData;
    },
  },
  template: `
    <div class="vocab-editor-description-body">
        <div class="form-group" v-if="newForm">
            <label class="col-md-2">Language</label>
            <div class="col-md-10">
              <select class="form-control" v-model="state.languageCode">
                  <option value=""></option>
                  <option v-bind:value="key" v-for="(item, key) in langData">{{item}}</option>
              </select>
            </div>
        </div>
        <div class="form-group" v-if="newForm">
            <label class="col-md-2">Script Code</label>
            <div class="col-md-10">
               <input type="text" class="form-control" v-model.trim="state.identifier"/>
            </div>
        </div>
        <div class="form-group">
           <label class="col-md-2">Pref. Label</label> 
           <div class="col-md-10">
             <input class="form-control" v-model.trim="state.name"/>
           </div>
        </div>
        <concept-description-multi-item
            v-bind:label="'Alt. Labels'"
            v-bind:data="state.altLabels" />
        <concept-description-multi-item
            v-bind:label="'Hidden Labels'"
            v-bind:data="state.hiddenLabels" />
        <concept-description-multi-item
            v-bind:label="'Definition(s)'"
            v-bind:data="state.definition"/>
        <concept-description-multi-item
            v-bind:label="'Scope Note(s)'"
            v-bind:data="state.scopeNote"/>
    </div>
  `
});

Vue.component("concept-rel-editor", {
  props: {
    lang: String, id: String, data: Array, dirty: Boolean
  },
  data: function () {
    return {
      expand: true,
      state: this.data,
      loading: false,
      saving: false,
      saved: false,
      error: false,
    }
  },
  watch: {
    data: function (newData, oldData) {
      this.state = newData;
    }
  },
  methods: {
    save: function() {
      this.$emit("item-rels-saved", this.state);
    },
    addBroader: function (text, id) {
      this.loading = true;
      DAO.get(id)
        .then(item => this.state.push(item))
        .then(() => this.loading = false);
    },
    removeBroader: function (item) {
      this.state.splice(_.findIndex(this.state, c => c.id === item.id), 1);
    }
  },
  computed: {
    sorted: function () {
      return this.state.concat().sort(DAO.sortByTitle(this.lang));
    },
    expandable: function() {
      return this.state.filter(i => i.broaderTerms.length > 0).length > 0;
    }
  },
  template: `
    <div id="concept-editor-rels-tab" class="concept-editor-tab">
      <div class="concept-editor-broader-terms concept-editor-tab-form">
        <h4>Broader Terms
            <button v-bind:disabled="!expandable" class="btn btn-xs btn-default">
                <span v-if="!expand" v-on:click="expand = true">expand terms</span>
                <span v-if="expand" v-on:click="expand = false">collapse terms</span>
            </button> 
        </h4>
        <vocab-editor-autocomplete-input
          v-on:item-accepted="addBroader"
          v-bind:disabled="false" />
        <ul v-if="state.length > 0">
          <li v-for="broader in sorted">
            {{broader|conceptTitle(lang, id)}}
            <span title="Remove Broader Term" class="remove" v-on:click="removeBroader(broader)">
              <i class="fa fa-remove"></i>
            </span>
            <concept-hierarchy
                v-show="expand"
                v-bind:id="id"
                v-bind:data="broader.broaderTerms"
                v-bind:lang="lang"
                />
          </li>  
        </ul>
        <div class="concept-editor-broader-terms-empty" v-else>No broader terms</div>
      </div>
      <div class="concept-editor-tab-form-footer" v-bind:class="{disabled:saving || loading}">
          <button v-bind:disabled="!dirty || saving" class="btn btn-danger" v-on:click="save">
              Save Concept Relationships
              <span v-if="saving"><i class="fa fa-fw fa-circle-o-notch fa-spin"></i></span>
              <span v-else-if="!dirty && saved"><i class="fa fa-fw fa-check"></i></span>
              <span v-else><i class="fa fa-fw fa-save"></i></span>
          </button>
          <button v-bind:disabled="!dirty || saving" class="pull-right btn btn-default" v-on:click="$emit('item-rels-reset')">
              Reset
              <span><i class="fa fa-fw fa-undo"></i></span>
          </button>
      </div>
    </div>
  `
});

Vue.component("concept-data-editor", {
  props: {
    lang: String,
    id: String,
    create: Boolean,
    data: Object,
    dirty: Boolean,
    loading: Boolean,
    saving: Boolean,
    saved: Boolean,
    error: Boolean,
    errors: Object,
    langData: Object,
  },
  data: function () {
    return {
      state: this.data,
      newDesc: false,
      pendingDesc: this.descTemplate(),
      currentDescIdx: -1,
    }
  },
  watch: {
    data: function (newData, oldData) {
      this.state = newData;
      this.error = false;
    }
  },
  methods: {
    save: function () {
      this.$emit('item-data-saved', this.state);
    },
    descTemplate: function () {
      return {
        isA: "CvocConceptDescription",
        languageCode: null,
        identifier: null,
        name: "",
        altLabels: [],
        hiddenLabels: [],
        definition: [],
        scopeNote: [],
        creationProcess: "MANUAL",
        maintenanceEvents: [],
        unknownProperties: [],
        accessPoints: []
      }
    },
    descName: function (desc) {
      return [
        LocaleData.languageCodeToName(desc.languageCode),
        desc.identifier,
        "-",
        desc.name
      ].filter(v => v).join(" ");
    },
    descIdent: function (desc) {
      return [
        desc.languageCode,
        desc.identifier,
      ].filter(v => v).join("-");
    },
    addDesc: function () {
      let data = JSON.parse(JSON.stringify(this.pendingDesc));
      this.state.descriptions.push(data);
      this.newDesc = false;
      this.pendingDesc = this.descTemplate();
    },
    cancelDesc: function () {
      this.newDesc = false;
      this.pendingDesc = this.descTemplate();
    },
    deleteDesc: function (desc) {
      let idx = _.findIndex(this.state.descriptions, d =>
        this.descIdent(d) === this.descIdent(desc));
      this.state.descriptions.splice(idx, 1);
    },
  },
  computed: {
    invalid: function () {
      // TODO: More validation???
      return !this.data.identifier;
    },
    sortedDescriptions: function () {
      return this.state.descriptions.concat().sort((a, b) => {
        return a.languageCode.localeCompare(b.languageCode);
      })
    },
    descriptionIds: function () {
      return this.state.descriptions.map(this.descIdent);
    },
    pendingHasUniqueId: function () {
      return !this.descriptionIds.includes(this.descIdent(this.pendingDesc));
    },
    pendingValid: function () {
      return this.pendingDesc.languageCode && this.pendingDesc.name && this.pendingHasUniqueId;
    },
  },
  template: `
    <div id="concept-editor-data-tab" class="concept-editor-tab" v-bind:class="{error: error}">
      <div class="concept-editor-tab-form">
        <div class="concept-editor-data-form">
          <div class="form-group" v-bind:class="{'has-error': errors.identifier}">
             <label class="col-md-2">Identifier</label> 
             <div class="col-md-10">
               <input type="url" class="form-control" v-model.trim="state.identifier"/>
               <span v-if="errors.identifier" v-for="e in errors.identifier" class="help-block">{{ e }}</span>
             </div>
          </div>
          <div class="form-group" v-bind:class="{'has-error': errors.uri}">
             <label class="col-md-2">URI</label> 
             <div class="col-md-10">
               <input type="url" class="form-control" v-model.trim="state.uri"/>
               <span v-if="errors.uri" v-for="e in errors.uri" class="help-block">{{ e }}</span>
             </div>
          </div>
          <div class="form-group" v-bind:class="{'has-error': errors.url}">
             <label class="col-md-2">URL</label> 
             <div class="col-md-10">
               <input type="url" class="form-control" v-model.trim="state.url"/>
               <span v-if="errors.url" v-for="e in errors.url" class="help-block">{{ e }}</span>
             </div>
          </div>
          <div id="concept-editor-descriptions">
              <header>
                <div class="concept-editor-description-controls pull-right">
                  <button v-if="!newDesc" class="btn btn-default" v-on:click="newDesc = true">
                      <i class="fa fa-plus-circle"></i>
                  </button>
                  <button v-if="newDesc" class="btn btn-success" v-bind:disabled="!pendingValid" v-on:click="addDesc">
                      <i class="fa fa-check"></i>
                  </button><button v-if="newDesc" class="btn btn-default" v-on:click="cancelDesc">
                      <i class="fa fa-remove"></i>
                  </button>
                </div>
                <h4>Descriptions</h4>
              </header>
              <div class="concept-editor-new-description-form" v-if="newDesc">
                  <div v-show="!pendingHasUniqueId" class="alert alert-warning">
                      New descriptions must have a unique lang/script combination.
                  </div> 
                  <concept-description-editor
                    v-bind:idx="-1"
                    v-bind:data="pendingDesc"
                    v-bind:langData="langData"
                    v-bind:newForm="true" />
                  <div class="form-group">
                      <div class="col-md-12">
                          <button class="btn btn-success" v-bind:disabled="!pendingValid" v-on:click="addDesc">
                            Add Description
                            <i class="fa fa-check"></i>
                          </button>
                          <button class="btn btn-default" v-on:click="cancelDesc">
                            Cancel
                            <i class="fa fa-remove"></i>
                          </button>
                      </div>
                  </div>  
              </div>
              <div class="list-group concept-editor-description-tabs" v-if="!newDesc && state.descriptions.length">
                <div v-for="(description, i) in sortedDescriptions">
                  <a href="#" class="list-group-item list-group-item-action" 
                          v-bind:class="{active: currentDescIdx === i}" 
                          v-on:click="currentDescIdx = i">
                      {{descName(description)}}
                  </a>
                  <div class="concept-editor-description-tab clearfix" v-if="i == currentDescIdx">
                    <concept-description-editor 
                        v-bind:idx="i"
                        v-bind:key="description.id"
                        v-bind:data="description"
                        v-bind:langData="langData" />
                     <button v-if="i == currentDescIdx" 
                          class="btn btn-xs btn-danger pull-right" v-on:click="deleteDesc(description)">
                      Delete Description
                      <i class="fa fa-remove"></i>
                      </button>   
                  </div>  
                  </div>
              </div>
              <p class="alert alert-info" v-else-if="!newDesc">
                  No descriptions yet. <a href="#" v-on:click.prevent="newDesc = true">Create one...</a>
              </p>
          </div>
        </div>
      </div>
      <div class="concept-editor-tab-form-footer" v-bind:class="{disabled:saving || loading}">
          <button v-bind:disabled="!dirty || saving || invalid || newDesc" class="btn btn-danger" v-on:click="save">
              Save Concept
              <span v-if="saving"><i class="fa fa-fw fa-circle-o-notch fa-spin"></i></span>
              <span v-else-if="!dirty && saved"><i class="fa fa-fw fa-check"></i></span>
              <span v-else><i class="fa fa-fw fa-save"></i></span>
          </button>
          <button v-if="!create" v-bind:disabled="!dirty || saving" class="pull-right btn btn-default" v-on:click="$emit('item-data-reset'); newDesc = false">
              Reset
              <span><i class="fa fa-fw fa-undo"></i></span>
          </button>
          <button v-if="create" class="pull-right btn btn-default" v-on:click="$emit('cancel-create')" title="Delete draft">
            <i class="fa fa-trash"></i>
          </button>
      </div>
    </div>
  `
});

Vue.component("concept-editor", {
  props: {
    lang: String,
    id: String,
    data: Object,
    dirtyData: Boolean,
    dirtyRels: Boolean,
    langData: Object,
  },
  data: function () {
    return {
      tab: 'rels',
      loading: false,
      saving: false,
      saved: false,
      error: false,
      errors: {},
      deleting: false,
      deleted: false,
    };
  },
  watch: {
    id: function (newId, oldId) {
      if (this.deleted && (newId !== oldId)) {
        this.deleted = false;
        this.tab = 'rels';
      }
    }
  },
  methods: {
    updateItem: function (data) {
      this.loading = true;
      this.saving = true;
      DAO.updateItem(this.id, data)
        .then(item => this.$emit('item-data-saved', item))
        .then(() => {
          this.saved = true;
          this.error = false;
          this.saving = false;
          this.loading = false;
        }).fail(err => {
        this.error = true;
        this.saving = false;
        this.loading = false;
        if (err.status === 400 && err.responseJSON) {
          this.errors = err.responseJSON;
        }
      });
    },
    updateRels: function (broader) {
      this.loading = true;
      this.saving = true;
      DAO.setBroader(this.id, broader.map(c => c.id))
        .then(item => this.$emit('item-rels-saved', item))
        .then(() => {
          this.saved = true;
          this.error = false;
          this.saving = false;
          this.loading = false;
        }).fail(err => {
        this.error = true;
        this.saving = false;
        this.loading = false;
      });
    },
    deletedItem: function (data) {
      this.deleted = true;
      this.deleting = false;
      this.$emit("item-deleted", data);
    }
  },
  filters: {
    formatTimestamp: function(s) {
      let m = moment(s);
      return m.isValid() ? m.fromNow() : "";
    }
  },
  template: `
    <div id="concept-editor" class="form-horizontal">
        <h3 id="concept-editor-item-title">{{data|conceptTitle(lang, id)}} ({{data.identifier}})</h3>
        <small class="concept-editor-concept-meta" v-if="data.event.user">
          Last updated by {{ data.event.user.name }} {{ data.event.timestamp|formatTimestamp }}.  
        </small>
        <div id="concept-editor-item-deleted" v-if="deleted">
            <p class="alert alert-danger">Item deleted</p>
        </div>
        <div id="concept-editor-body" v-else>
          <ul id="concept-editor-nav-tabs" class="nav nav-tabs">
              <li class="nav-item">
                <a class="nav-link" v-bind:class="{active: tab === 'rels'}" href="#" v-on:click.prevent="tab = 'rels'">Relationships</a>
              </li>
              <li class="nav-item">
                <a class="nav-link" v-bind:class="{active: tab === 'data', 'error': error}" href="#" v-on:click.prevent="tab = 'data'">
                  Data
                  <i v-if="error" class="fa fa-exclamation-circle"></i>
                </a>
              </li>
              <li class="nav-item delete-tab">
                <a class="nav-link" v-bind:class="{active: tab == 'delete'}" href="#" v-on:click.prevent="tab = 'delete'">Delete</a>
              </li>
          </ul>
          <concept-rel-editor
              v-if="tab === 'rels'"
              v-bind:lang="lang"
              v-bind:id="id"
              v-bind:data="data.broaderTerms"
              v-bind:langData="langData"
              v-bind:dirty="dirtyRels"
              v-on:item-rels-saved="updateRels"
              v-on:item-rels-reset="$emit('item-rels-reset')"
                  />
          <concept-data-editor
              v-else-if="tab === 'data'"
              v-bind:lang="lang"
              v-bind:id="id"
              v-bind:create="false"
              v-bind:data="data"
              v-bind:langData="langData"
              v-bind:dirty="dirtyData"
              v-bind:loading="loading"
              v-bind:saving="saving"
              v-bind:saved="saved"
              v-bind:error="error"
              v-bind:errors="errors"
              v-on:item-data-saved="updateItem"
              v-on:item-data-reset="$emit('item-data-reset')" 
                  />
          <concept-deleter 
              v-if="tab === 'delete'"
              v-bind:id="id"
              v-bind:data="data"
              v-bind:lang="lang"
              v-on:delete-cancel="deleting = false"
              v-on:deleted-item="deletedItem"
                  />
        </div>
    </div>
  `
});

Vue.component("concept-creator", {
  props: {
    lang: String, langData: Object, data: Object,
  },
  data: function () {
    return {
      loading: false,
      saving: false,
      saved: false,
      error: false,
      errors: {},
      state: this.data,
    }
  },
  methods: {
    createItem: function (toSave) {
      this.loading = true;
      this.saving = true;
      DAO.createItem(toSave)
        .then(item => this.$emit('item-data-saved', item))
        .then(() => {
          this.saved = true;
          this.error = false;
          this.saving = false;
          this.loading = false;
        }).fail(err => {
        this.error = true;
        this.saving = false;
        this.loading = false;
        if (err.status === 400 && err.responseJSON) {
          this.errors = err.responseJSON;
        }
      });
    },
  },
  template: `
    <div id="concept-creator" class="form-horizontal">
        <h3 id="concept-creator-title">New Concept</h3>
        <concept-data-editor
            v-bind:lang="lang"
            v-bind:id="null"
            v-bind:create="true"
            v-bind:data="state"
            v-bind:langData="langData"
            v-bind:dirty="true"
            v-bind:loading="loading"
            v-bind:saving="saving"
            v-bind:saved="saved"
            v-bind:error="error"
            v-bind:errors="errors"
            v-on:item-data-saved="createItem"
            v-on:cancel-create="$emit('cancel-create')"
                />
    </div>
  `
});

Vue.component("concept-deleter", {
  props: {
    lang: String,
    id: String,
    data: Object,
  },
  data: function () {
    return {
      loading: false,
      error: false,
      iAmSure: false,
    }
  },
  methods: {
    deleteItem: function () {
      if (this.iAmSure) {
        this.loading = true;
        DAO.deleteItem(this.id).then(() => {
          this.loading = false;
          this.iAmSure = false;
          this.$emit("deleted-item", this.data);
        }).fail(() => {
          this.error = true;
          this.loading = false;
        });
      }
    }
  },
  template: `
    <div id="concept-delete-tab" class="concept-editor-tab" v-bind:class="{error: error}">
      <div class="concept-editor-tab-form">
          Are you sure you want to delete this item?
          <br/>
          <label><input type="checkbox" v-model="iAmSure">Yes, I am sure</input></label>
      </div>
      
      <div class="concept-editor-tab-form-footer" v-bind:class="{disabled:loading}">
        <button type="button" class="btn btn-danger" v-bind:disabled="loading || !iAmSure" v-on:click="deleteItem">
            Delete
            <span v-if="loading"><i class="fa fa-fw fa-circle-o-notch fa-spin"></i></span>
            <span v-else><i class="fa fa-fw fa-remove"></i></span>
        </button>
      </div>
    </div><!-- /.modal -->
  `
});

Vue.component("concept-list-item", {
  props: {id: String, lang: String, name: String, childCount: Number, selectedId: String, eventBus: Vue, isSearch: Boolean},
  data: function () {
    return {
      loading: false,
      open: false,
      children: []
    };
  },
  watch: {
    lang: function (newLang, oldLang) {
      if (this.open) {
        this.refresh();
      }
    },
  },
  methods: {
    refresh: function () {
      this.$emit('refresh');
      return DAO.getChildren(this.id, this.lang)
        .then(c => {
          this.children = c;
          if (c.length === 0) {
            this.open = false;
          }
        });
    },
    showList: function () {
      if (this.childCount) {
        this.open = true;
        this.loading = true;
        this.refresh().then(() => this.loading = false);
      }
    },
    hideList: function () {
      this.open = false;
    },
    forwardEdit: function (id) {
      this.$emit('edit-item', id);
    }
  },
  created: function () {
    this.eventBus.$on('refresh-children', ids => {
      if (ids.includes(this.id)) {
        this.refresh();
      }
    })
  },
  template: `
    <li class="vocab-editor-concept" v-bind:class="{'is-search': isSearch, 'active': selectedId === id}">
        <div class="vocab-editor-concept-heading">
          <span class="vocab-editor-open-narrower" 
              v-if="childCount > 0 && !open && !loading" 
              v-on:click="showList()"><i class="fa fa-angle-right fa-fw"></i></span>
          <span class="vocab-editor-close-narrower" 
              v-if="open && !loading" 
              v-on:click="hideList()"><i class="fa fa-angle-down fa-fw"></i></span>
          <span class="vocab-editor-loading-narrower" 
              v-if="loading"><i class="fa fa-circle-o-notch fa-pulse fa-fw"></i></span>
            <span v-if="childCount == 0" class="fa fa-fw"></span>
            <span class="vocab-editor-concept-title"
                v-on:click="showList(); $emit('edit-item', id)">
                {{name}}
                <i v-if="selectedId === id" class="fa fa-asterisk"></i>
            </span>
        </div>
        <ul class="vocab-editor-concept-list" v-if="children.length > 0 && open">
            <concept-list-item v-for="child in children"
                v-bind:key="child[0]"
                v-bind:id="child[0]"              
                v-bind:lang="lang"
                v-bind:name="child[1]"
                v-bind:childCount="child[2]"
                v-bind:selectedId="selectedId"
                v-bind:eventBus="eventBus"
                v-bind:isSearch="false"
                v-on:refresh="refresh"
                v-on:edit-item="forwardEdit"/>
        </ul>
    </li>
  `
});

Vue.filter("conceptTitle", DAO.title);

var app = new Vue({
  el: '#vocab-editor',
  data: function () {
    return {
      loading: true,
      loadingForm: false,
      lang: "eng",
      langs: [],
      langData: __languageData || {},
      q: "",
      concepts: [],
      creating: false,
      createBuffer: null,
      editing: null,
      editBuffer: null,
      eventBus: new Vue(),
      isSearch: false,
    }
  },
  watch: {
    lang: function (newLang, oldLang) {
      this.reload();
    },
  },
  methods: {
    conceptTemplate: function () {
      return DAO.getNextIdentifier().then(ident => {
        return {
          isA: "CvocConcept",
          identifier: ident,
          seeAlso: [],
          descriptions: [],
          accessibleTo: [],
          meta: {}
        }
      });
    },
    reload: function (item) {
      this.loading = true;
      DAO.getConcepts(this.q, this.lang).then(concepts => {
        this.loading = false;
        this.concepts = concepts;
        this.isSearch = this.q.trim() !== ""
      });
    },
    refreshItem: function (item) {
      this.reload();
      this.eventBus.$emit('refresh-children', [item.id]);
    },
    refreshUpdatedItem: function (item) {
      this.edit(item);
      this.refreshItem(item);
    },
    refreshSavedItem: function (item) {
      this.createBuffer = null;
      this.refreshUpdatedItem(item);
    },
    refreshReparentedItem: function (item) {
      // refresh broader terms that are
      let inBoth = _.intersection(item.broaderTerms.map(c => c.id), this.editing.broaderTerms.map(c => c.id));
      let all = _.union(item.broaderTerms.map(c => c.id), this.editing.broaderTerms.map(c => c.id));
      this.eventBus.$emit('refresh-children', _.difference(all, inBoth));
      this.edit(item);
      this.reload();
    },
    showNewConceptForm: function () {
      if (this.createBuffer === null) {
        this.loadingForm = true;
        this.conceptTemplate().then(temp => {
          this.loadingForm = false;
          this.createBuffer = temp;
          this.creating = true;
          this.editing = null;
          this.editBuffer = null;
        });
      } else {
        this.creating = true;
        this.editing = null;
        this.editBuffer = null;
      }
    },
    cancelCreate: function() {
      this.creating = false;
      this.createBuffer = null;
    },
    loadItem: function (id) {
      this.loading = true;
      DAO.get(id).then(item => {
        this.loading = false;
        this.edit(item);
      });
    },
    edit: function (item) {
      this.creating = false;
      this.editing = item;
      this.resetData();
      this.resetRels();
    },
    resetData: function () {
      let bt = this.editBuffer && this.editing ? this.editBuffer.broaderTerms : null;
      this.editBuffer = JSON.parse(JSON.stringify(this.editing));
      if (bt) {
        this.editBuffer.broaderTerms = bt;
      }
    },
    resetRels: function () {
      if (this.editBuffer && this.editing) {
        this.editBuffer.broaderTerms = JSON.parse(JSON.stringify(this.editing.broaderTerms));
      }
    },
  },
  computed: {
    dirtyData: function () {
      return JSON.stringify(_.omit(this.editing, ["broaderTerms"]))
        !== JSON.stringify(_.omit(this.editBuffer, ["broaderTerms"]));
    },
    dirtyRels: function () {
      return JSON.stringify(_.pick(this.editing, ["broaderTerms"]))
        !== JSON.stringify(_.pick(this.editBuffer, ["broaderTerms"]));

    }
  },
  created: function () {
    DAO.getLangData().then(langs => this.langs = langs)
      .then(() => this.reload());
  },
  template: `
    <div id="vocab-editor-container">
        <div id="vocab-editor-listnav">
          <div class="vocab-editor-controls form-inline">
            <div class="input-group">
              <span class="input-group-prepend">
                <select class="btn btn-secondary" v-model="lang">
                    <option v-bind:value="l" v-for="l in langs">{{langData[l]}}</option>
                </select>
              </span>
              <input class="form-control" v-model.trim="q" v-on:change="reload" placeholder="Search..."/>
              <div class="input-group-append">
                <button class="btn btn-secondary" v-on:click="reload"
                        v-bind:disabled="q === ''">
                    <i v-if="loading" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
                    <i v-else class="fa fa-fw fa-search"></i>
                </button>
              </div>
            </div>
          </div>
          <div class="vocab-editor-list">
            <ul class="vocab-editor-top-concepts vocab-editor-concept-list" v-if="concepts.length">
              <concept-list-item v-for="concept in concepts" 
                  v-bind:lang="lang"
                  v-bind:key="concept[0]"
                  v-bind:id="concept[0]"
                  v-bind:name="concept[1]"
                  v-bind:childCount="concept[2]"
                  v-bind:selectedId="editing ? editing.id : null"
                  v-bind:eventBus="eventBus"
                  v-bind:isSearch="isSearch"
                  v-on:edit-item="loadItem" />
            </ul>
            <p class="text-muted" v-else-if="loading">Loading data...</p>
            <p class="text-muted" v-else>No items found</p>
          </div>
          <div class="vocab-editor-listnav-footer">
            <button class="btn btn-success" 
                v-bind:disabled="creating" 
                v-on:click="showNewConceptForm">
                    New Concept
                <i v-if="loadingForm" class="fa fa-circle-o-notch fa-fw fa-spin"></i>
                <i v-else-if="createBuffer !== null" class="fa fa-asterisk"></i>
            </button>
          </div>
        </div>
        <div id="vocab-editor-editpanel">
          <concept-editor v-if="editing != null"
              v-bind:lang="lang"
              v-bind:id="editing.id"
              v-bind:data="editBuffer"
              v-bind:dirtyData="dirtyData"
              v-bind:dirtyRels="dirtyRels"
              v-bind:langData="langData"
              v-on:item-data-reset="resetData"
              v-on:item-rels-reset="resetRels"
              v-on:item-data-saved="refreshUpdatedItem"
              v-on:item-rels-saved="refreshReparentedItem"
              v-on:item-deleted="refreshItem" />
          <concept-creator v-else-if="creating"
              v-bind:lang="lang"
              v-bind:langData="langData"
              v-bind:data="createBuffer"
              v-on:item-data-saved="refreshSavedItem"
              v-on:cancel-create="cancelCreate" />
           <div v-else id="vocab-editor-load-note">
              <h2>Click on an item left to edit...</h2>
           </div>
        </div>
    </div>
  `
});
