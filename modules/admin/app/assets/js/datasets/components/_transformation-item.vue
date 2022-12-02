<script lang="ts">

export default {
  props: {
    item: Object,
    parameters: {
      type: Object,
      default: null
    },
    disabled: {
      type: Boolean,
      default: false
    },
    active: Boolean,
  },
}
</script>

<template>
  <div v-on:dblclick="$emit('edit')"
       v-bind:class="{'is-disabled': disabled}"
       class="list-group-item transformation-item list-group-item-action">
    <h4 class="transformation-item-name">
      {{ item.name }}
      <span class="transformation-item-comments" v-bind:title="item.comments">{{ item.comments }}</span>
    </h4>
    <div class="transformation-item-disable">
      <button v-if="active" v-bind:class="{
          'btn-warning': disabled,
          'btn-default': !disabled
        }" class="btn btn-sm" v-on:click="$emit('disable')">
        <i v-if="disabled" class="fa fa-fw fa-eye-slash"></i>
        <i v-else class="fa fa-fw fa-eye"></i>
      </button>
    </div>
    <div class="transformation-item-params">
      <button v-if="item.hasParams && parameters"
          v-bind:title="JSON.stringify(parameters, null, 2)"
          v-bind:class="{
          'btn-dark': Object.keys(parameters).length > 0,
          'btn-default': Object.keys(parameters).length === 0
        }" class="btn btn-sm" v-on:click="$emit('edit-params')">
        <i class="fa fa-fw fa-gears"></i>
      </button>
    </div>
    <button v-if="active" class="transformation-item-edit btn btn-sm btn-outline-danger" v-on:click="$emit('delete')">
      <i class="fa fa-fw fa-trash-o"></i>
    </button>
    <button v-else class="transformation-item-edit btn btn-sm btn-default" v-on:click="$emit('edit')">
      <i class="fa fa-fw fa-edit"></i>
    </button>

    <span class="transformation-item-meta">
      <span class="badge badge-pill" v-bind:class="'badge-' + item.bodyType">{{ item.bodyType }}</span>
      <span v-if="!item.repoId" class="badge badge-light">Generic</span>
    </span>
  </div>
</template>
