"use strict";

/**
 * A data access object containing functions to manage access points.
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

  /**
   * Search for items of the given type with the
   * given text contained in their name.
   *
   * @param {string} type the type string
   * @param {string} text the search string
   * @returns {Promise<Array.Object>} an array of matching item
   * objects, consisting of name, id, type fields
   */
  search: function (type, text) {
    let params = {
      limit: 10,
      q: text,
      st: CONFIG.typeFilters[type],
      f: CONFIG.holderIds.length ? ("holderId:(" + CONFIG.holderIds.join(" ") + ")") : ""
    };
    return fetch(SERVICE.filter().url + "?" + this.objToQueryString(params))
        .then(r => r.json())
        .then(data => data.items);
  },

  /**
   * Create a set of access points with the given type.
   *
   * @param {string} type the type string
   * @param {Object[]} items an array of objects describing
   * the access points to be created, consisting of name, targetId,
   * and (nullable) description fields
   * @returns {Promise<any>}
   */
  createAccessPoints: function (type, items) {
    let self = this;
    return new Promise(function (resolve, reject) {
      function saveItem(item, rest, cb) {
        SERVICE.createAccessPoint(CONFIG.id, CONFIG.descId).ajax({
          data: JSON.stringify({
            name: item.text,
            description: item.description,
            accessPointType: type,
            isA: "AccessPoint"
          }),
          headers: self.ajaxHeaders
        }).done(function (data) {
          if (item.targetId) {
            SERVICE.createLink(CONFIG.id, data.id).ajax({
              data: JSON.stringify({
                target: item.targetId,
                type: CONFIG.linkType,
                description: item.desccription
              }),
              headers: self.ajaxHeaders
            }).done(function (data) {
              if (rest.length) {
                saveItem(rest[0], rest.splice(1));
              } else {
                resolve();
              }
            })
          } else {
            if (rest.length) {
              saveItem(rest[0], rest.splice(1));
            } else {
              resolve();
            }
          }
        });
      }

      if (items.length) {
        saveItem(items[0], items.splice(1))
      } else {
        resolve();
      }
    });
  },

  /**
   * Delete an access point, and optionally a link.
   *
   * @param {string} accessPointId the access point ID
   * @param {?string} linkId the optional link ID
   * @returns {Promise<any>}
   */
  deleteAccessPoint: function (accessPointId, linkId) {
    let route = linkId
        ? SERVICE.deleteLinkAndAccessPoint(
            CONFIG.id, CONFIG.descId, accessPointId, linkId)
        : SERVICE.deleteAccessPoint(CONFIG.id, CONFIG.descId, accessPointId);
    return route.ajax();
  },

  /**
   * Fetch a list of access points for the configured description.
   *
   * @returns {Promise<Array.Object>}
   */
  getAccessPoints: function () {
    return SERVICE.getAccessPoints(CONFIG.id, CONFIG.descId).ajax()
        .then(json => {
          let types = [];
          json.forEach(item => {
            if (item["id"] === CONFIG.descId) {
              types = item["data"];
            }
          });
          return types;
        });
  }
};

Vue.component("access-point-autocomplete-suggestion", {
  props: {selected: Boolean, item: Object,},
  template: `
    <div @click="$emit('selected', item)" class="ap-editor-autocomplete-widget-suggestion">
        {{ item.name }} 
        <span class="badge suggestion-type">{{ item.type }}</span>
        <span v-if="selected"> *</span> 
    </div>
  `
});

Vue.component("access-point-autocomplete-input", {
  props: {type: String, disabled: Boolean},
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
        DAO.search(this.type, this.text).then(items => {
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
    <div class="ap-editor-autocomplete-widget form-group">
        <label class="control-label">Related name:</label>
        <div class="control-elements">
          <div class="input-group">
            <input class="form-control" type="text" 
              v-bind:disabled="disabled"
              v-model.trim="text" 
              v-on:input="search"
              v-on:keydown.up="selectPrev"
              v-on:keydown.down="selectNext"
              v-on:keydown.enter="accept"
              v-on:keydown.esc="cancelComplete"/>
              <span class="input-group-append">
                <button title="Create a new text-only access point" 
                      class="btn btn-success" 
                      v-bind:disabled="text.trim().length === 0"
                      v-on:click="accept">
                  <i class="fa fa-plus-circle"></i>
                </button>
              </span>
             </div>
            <div class="dropdown-list" v-if="suggestions.length">
              <div class="ap-editor-autocomplete-widget-suggestions">
                  <access-point-autocomplete-suggestion
                      v-for="(suggestion, i) in suggestions"
                      v-bind:class="{selected: i === selectedIdx}"
                      v-bind:key="suggestion.id"
                      v-bind:item="suggestion"
                      v-bind:selected="i === selectedIdx"
                      v-on:selected="setAndChooseItem">
                  </access-point-autocomplete-suggestion>
              </div>
            </div>
        </div>
    </div>
  `
});

Vue.component("access-point-add-form", {
  props: {type: String},
  data: function () {
    return {
      adding: [],
      saving: false
    };
  },
  methods: {
    acceptItem: function (text, targetId) {
      this.adding.push({
        text: text,
        targetId: targetId,
        description: ""
      });
    },
    saveItems: function () {
      this.saving = true;
      DAO.createAccessPoints(this.type, this.adding)
          .then(_ => {
            this.saving = false;
            this.$emit('added')
          });
    },
    setDescription: function (i, desc) {
      this.adding[i].description = desc;
    }
  },
  template: `
    <div class="ap-editor-new-access-point">
      <button v-on:click="$emit('cancel-add')" 
        type="button" class="close" aria-label="Close"><span aria-hidden="true">&times;</span></button>
      <h4>New Access Points</h4>
      <access-point-autocomplete-input 
            v-bind:type="type"
            v-bind:disabled="saving"
            v-on:item-accepted="acceptItem">            
        </access-point-autocomplete-input>
      <ul class="ap-editor-pending-items" v-if="adding.length">
        <li v-for="(item, i) in adding"
            v-bind:key="i">
              <div class="control-elements input-group ap-editor-pending-item">
                  <span v-if="item.targetId" title="Item is linked"
                    class="input-group-prepend ap-editor-pending-item-link-note">
                    <i class="fa fa-link"></i>
                  </span>
                  <span class="input-group-prepend input-group-text">
                    {{item.text}}
                  </span>
                  <input class="form-control" 
                    v-bind:disabled="saving" 
                    v-model="item.description" 
                    placeholder="Optional description..."/>
                  <button title="Remove item" class="btn btn-danger input-group-append" 
                        v-bind:disabled="saving"
                        v-on:click.prevent="adding.splice(i, 1)">
                    <i class="fa fa-remove"></i>  
                  </button>
              </div>
        </li>
      </ul>
      <div class="ap-editor-new-access-point-controls">
        <button class="btn btn-danger" 
            v-on:click="saveItems" 
            v-bind:disabled="!adding.length || saving">Save</button>
        <button v-bind:disabled="saving"
            class="btn btn-default" 
            v-on:click="$emit('cancel-add')">Cancel</button> 
      </div>
    </div>
  `
});

Vue.component("access-point", {
  props: {accessPoint: Object, link: Object, target: Object},
  data: function () {
    return {
      loading: false,
      confirm: false,
    };
  },
  computed: {
    targetUrl: function () {
      return SERVICE.getItem(this.target.type, this.target.id).url;
    }
  },
  methods: {
    confirmDelete: function () {
      this.confirm = true;
    },
    deleteAccessPoint: function () {
      this.loading = true;
      DAO.deleteAccessPoint(this.accessPoint.id, this.link ? this.link.id : null).then(_ => {
        this.loading = false;
        this.confirm = false;
        this.$emit("deleted");
      });
    }
  },
  template: `
        <li class="ap-editor-access-point">
            <a v-if="link" v-bind:href="targetUrl">{{accessPoint.name}}</a>
            <span v-else>{{accessPoint.name}}</span>
            <span class="controls">
              <span v-if="!confirm" title="Remove Access Point" 
                class="ap-editor-remove-access-point fa fa-remove" 
                v-on:click="confirm = true">
              </span>
              <span v-else class="remove-confirm" v-on:blur="confirm = false">
                  <button data-apply="confirmation" class="btn btn-xs btn-danger" 
                    v-on:click.prevent="deleteAccessPoint" 
                    v-bind:disabled="loading">
                      <i class="fa fa-check"></i>
                      Delete
                  </button>
                  <button data-dismiss="confirmation" class="btn btn-xs btn-default" 
                    v-on:click.prevent="confirm = false" 
                    v-bind:disabled="loading">Cancel</button>
              </span>
            </span>
            <span v-if="loading" class="loading-spinner">Deleting...</span>
            <p class="ap-editor-access-point-description" v-if="accessPoint.description">
                {{ accessPoint.description }}
            </p>
        </li>
    `
});

Vue.component("access-point-type", {
  props: {type: String, accessPoints: Array},
  data: function () {
    return {
      adding: false
    };
  },
  computed: {
    title: function () {
      return LABELS[this.type];
    }
  },
  methods: {
    added: function () {
      this.adding = false;
      this.$emit('added');
    }
  },
  template: `
        <li class="ap-editor-type">
          <h3>{{ title }}</h3>
          <ul class="ap-editor-access-points">
              <access-point 
                  v-for="ap in accessPoints" 
                  v-bind:key="ap.accessPoint.id"
                  v-bind:access-point="ap.accessPoint"
                  v-bind:link="ap.link"
                  v-bind:target="ap.target"
                  v-on:deleted="$emit('deleted')">
              </access-point>
          </ul>
          <a v-if="!adding" class="ap-editor-add-toggle" v-on:click.prevent="adding = !adding">
            <i class="glyphicon" v-bind:class="adding ? 'glyphicon-minus-sign' : 'glyphicon-plus-sign'"></i>
            Add New
          </a href="#">
          <access-point-add-form v-if="adding" 
            v-bind:type="type" 
            v-on:added="added"
            v-on:cancel-add="adding = false">            
          </access-point-add-form>
          <hr/>
        </li>
    `
});

var app = new Vue({
  el: '#ap-editor',
  data: {
    loading: true,
    types: []
  },
  methods: {
    reload: function () {
      this.loading = true;
      DAO.getAccessPoints().then(types => {
        this.loading = false;
        this.types = types
      });
    }
  },
  created: function () {
    this.reload();
  },
  template: `
    <div id="access-point-editor" class="ap-editor">
      <span v-if="loading">Loading...</span>
      <ul class="ap-editor-types">
          <access-point-type
            v-for="type in types"
            v-bind:key="type.type"
            v-bind:type="type.type"
            v-on:deleted="reload"
            v-on:added="reload"
            v-bind:access-points="type.data">
          </access-point-type>
      </ul>
    </div>
  `
});
