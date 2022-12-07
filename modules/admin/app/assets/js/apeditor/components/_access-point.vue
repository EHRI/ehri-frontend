<script lang="ts">
export default {
  props: {
    accessPoint: Object,
    link: Object,
    target: Object,
    api: Object,
    config: Object,
  },
  data: function () {
    return {
      loading: false,
      confirm: false,
    };
  },
  computed: {
    targetUrl: function () {
      return this.target
          ? this.api.itemUrl(this.target.type, this.target.id)
          : null;
    }
  },
  methods: {
    confirmDelete: function () {
      this.confirm = true;
    },
    deleteAccessPoint: async function () {
      this.loading = true;
      try {
        if (this.link) {
          await this.api.deleteLinkAndAccessPoint(this.config.id, this.config.did, this.accessPoint.id, this.link.id)
        } else {
          await this.api.deleteAccessPoint(this.config.id, this.config.did, this.accessPoint.id);
        }
        this.$emit("deleted");
      } catch(e) {
        console.error(e);
      } finally {
        this.loading = false;
        this.confirm = false;
      }
    }
  },
}
</script>

<template>
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
</template>
